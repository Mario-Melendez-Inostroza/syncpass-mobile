package com.android.syncpass.ui

import com.android.syncpass.core.Coordenadas
import com.android.syncpass.core.UbicacionProvider
import com.android.syncpass.core.auth.AuthRepository
import com.android.syncpass.core.auth.Tokens
import com.android.syncpass.core.network.ApiResponse
import com.android.syncpass.core.network.NetworkFactory
import com.android.syncpass.core.network.ResumenEventoDto
import com.android.syncpass.data.RegistroRepository
import com.android.syncpass.data.local.AsistenteEntity
import com.android.syncpass.data.local.EventoEntity
import com.android.syncpass.sync.SyncScheduler
import com.android.syncpass.ui.asistencia.MarcarViewModel
import com.android.syncpass.ui.coordinador.ResumenViewModel
import com.android.syncpass.ui.login.LoginViewModel
import com.android.syncpass.ui.sesion.EstadoSesion
import com.android.syncpass.ui.sesion.SesionViewModel
import com.android.syncpass.ui.sync.motivoLegible
import com.android.syncpass.testing.FakeAsistenciaDao
import com.android.syncpass.testing.FakeCatalogoDao
import com.android.syncpass.testing.FakeEncuestaDao
import com.android.syncpass.testing.FakeSyncApi
import com.android.syncpass.testing.FakeTokenStorage
import com.android.syncpass.testing.MainDispatcherRule
import com.android.syncpass.testing.RelojFijo
import com.android.syncpass.testing.UuidSecuencial
import com.android.syncpass.testing.jwtDePrueba
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

private class SchedulerFalso : SyncScheduler {
    var pedidos = 0
    override fun solicitarAhora() {
        pedidos++
    }

    override fun programarPeriodica() = Unit
}

private class UbicacionFalsa(var coordenadas: Coordenadas? = Coordenadas(-33.5122, -70.6001)) : UbicacionProvider {
    override suspend fun actual() = coordenadas
}

/** Lógica de pantalla: lo que ve el usuario tras cada acción, sin emulador. */
class ViewModelsTest {

    @get:Rule
    val principal = MainDispatcherRule()

    private val sub = "sub-monitor"

    // ------------------------------------------------------------------ marcación de asistencia

    private lateinit var catalogo: FakeCatalogoDao
    private lateinit var asistencias: FakeAsistenciaDao
    private lateinit var scheduler: SchedulerFalso
    private lateinit var ubicacion: UbicacionFalsa
    private lateinit var reloj: RelojFijo

    @Before
    fun preparar() {
        catalogo = FakeCatalogoDao().apply {
            eventos[1] = EventoEntity(1, 1, "Taller", "2026-11-05T15:00:00Z", 30, "Sede", -33.5, -70.6, 200)
            asistentes[340] = AsistenteEntity(340, "ASI-0001", "VECINO", "INSCRITO")
            asistentes[341] = AsistenteEntity(341, "ASI-0002", "ESTUDIANTE", "INSCRITO")
        }
        asistencias = FakeAsistenciaDao()
        scheduler = SchedulerFalso()
        ubicacion = UbicacionFalsa()
        reloj = RelojFijo()
    }

    private fun marcarVm(): MarcarViewModel = MarcarViewModel(
        eventoId = 1,
        sub = sub,
        registro = RegistroRepository(catalogo, asistencias, FakeEncuestaDao(), reloj, UuidSecuencial()),
        catalogoDao = catalogo,
        asistenciaDao = asistencias,
        ubicacion = ubicacion,
        scheduler = scheduler,
        reloj = reloj,
    )

    @Test
    fun `un QR valido registra la asistencia, avisa y pide sincronizar`() {
        val vm = marcarVm()

        vm.alLeerQr("ASISTENTE_340")

        val aviso = vm.ultimo.value!!
        assertFalse(aviso.esError)
        assertTrue(aviso.texto, aviso.texto.contains("ASI-0001") || aviso.texto.contains("#340"))
        assertNotNull("ofrece abrir la encuesta", aviso.asistenciaUuid)
        assertEquals(1, asistencias.filas.size)
        assertEquals(-33.5122, asistencias.filas.values.single().latitud!!, 0.0)
        assertEquals(1, scheduler.pedidos)
        assertEquals(1, vm.sesionMarcadas.value)
    }

    @Test
    fun `el mismo QR leido varias veces seguidas se registra una sola vez`() {
        val vm = marcarVm()

        vm.alLeerQr("ASISTENTE_340")
        vm.alLeerQr("ASISTENTE_340")
        vm.alLeerQr("ASISTENTE_340")

        assertEquals(1, asistencias.filas.size)
        assertEquals(1, scheduler.pedidos)
        assertFalse(vm.ultimo.value!!.esError)
    }

    @Test
    fun `pasado el antirrebote el mismo QR avisa que ya estaba marcado`() {
        val vm = marcarVm()
        vm.alLeerQr("ASISTENTE_340")

        reloj.ahora += 10_000
        vm.alLeerQr("ASISTENTE_340")

        assertEquals(1, asistencias.filas.size)
        assertTrue(vm.ultimo.value!!.esError)
        assertTrue(vm.ultimo.value!!.texto.contains("ya estaba marcado"))
    }

    @Test
    fun `asistentes distintos seguidos se registran los dos`() {
        val vm = marcarVm()

        vm.alLeerQr("ASISTENTE_340")
        vm.alLeerQr("ASISTENTE_341")

        assertEquals(2, asistencias.filas.size)
        assertEquals(2, vm.sesionMarcadas.value)
    }

    @Test
    fun `un QR que no es de SyncPass se rechaza sin registrar nada`() {
        val vm = marcarVm()

        vm.alLeerQr("https://ejemplo.cl/otra-cosa")

        assertTrue(vm.ultimo.value!!.esError)
        assertTrue(asistencias.filas.isEmpty())
        assertEquals(0, scheduler.pedidos)
    }

    @Test
    fun `sin ubicacion la asistencia se registra igual`() {
        ubicacion.coordenadas = null
        val vm = marcarVm()

        vm.alLeerQr("ASISTENTE_340")

        assertFalse(vm.ultimo.value!!.esError)
        assertNull(asistencias.filas.values.single().latitud)
    }

    @Test
    fun `un asistente que no esta en el catalogo pide sincronizar`() {
        val vm = marcarVm()

        vm.alLeerQr("ASISTENTE_999")

        assertTrue(vm.ultimo.value!!.esError)
        assertTrue(vm.ultimo.value!!.texto.contains("catálogo"))
        assertTrue(asistencias.filas.isEmpty())
    }

    @Test
    fun `elegir de la lista registra igual que el QR`() {
        val vm = marcarVm()

        vm.marcar(341)

        assertEquals(341L, asistencias.filas.values.single().asistenteId)
        assertEquals(1, scheduler.pedidos)
    }

    @Test
    fun `la lista indica quienes ya estan marcados en el evento`() {
        val vm = marcarVm()
        // El flujo es perezoso: se suscribe para que se calcule.
        val suscripcion = CoroutineScope(Dispatchers.Unconfined).launch { vm.asistentes.collect { } }
        vm.marcar(340)

        val lista = vm.asistentes.value

        assertEquals(setOf(340L), lista.filter { it.marcado }.map { it.asistente.id }.toSet())
        assertEquals(setOf(341L), lista.filter { !it.marcado }.map { it.asistente.id }.toSet())
        suscripcion.cancel()
    }


    // ------------------------------------------------------------------ inicio de sesión

    private lateinit var servidor: MockWebServer

    private fun abrirServidor() {
        servidor = MockWebServer().apply { start() }
    }

    @After
    fun cerrarServidor() {
        if (::servidor.isInitialized) servidor.shutdown()
    }

    private fun autenticacion(storage: FakeTokenStorage): AuthRepository {
        val red = NetworkFactory(servidor.url("/").toString(), storage, depurar = false)
        return AuthRepository(red.authApi, red.sesionApi, storage)
    }

    private fun <T> esperar(bloque: suspend () -> T): T = runBlocking { withTimeout(10_000) { bloque() } }

    @Test
    fun `iniciar sesion con campos vacios no llama al servidor`() {
        abrirServidor()
        val vm = LoginViewModel(autenticacion(FakeTokenStorage()))

        vm.iniciarSesion()

        assertNotNull(vm.ui.value.error)
        assertEquals(0, servidor.requestCount)
    }

    @Test
    fun `un correo sin arroba se rechaza antes de llamar al servidor`() {
        abrirServidor()
        val vm = LoginViewModel(autenticacion(FakeTokenStorage()))
        vm.onEmail("no-es-un-correo")
        vm.onPassword("Clave-de-prueba-1!")

        vm.iniciarSesion()

        assertEquals(0, servidor.requestCount)
        assertNotNull(vm.ui.value.error)
    }

    @Test
    fun `un login correcto limpia la contrasena y deja la sesion guardada`() {
        abrirServidor()
        val storage = FakeTokenStorage()
        servidor.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                """{"success":true,"data":{"accessToken":"a","idToken":"${jwtDePrueba()}","refreshToken":"r"}}""",
            ),
        )
        val vm = LoginViewModel(autenticacion(storage))
        vm.onEmail("monitor.demo@example.com")
        vm.onPassword("Clave-de-prueba-1!")

        vm.iniciarSesion()
        esperar { vm.ui.first { !it.cargando && it.password.isEmpty() } }

        assertNull(vm.ui.value.error)
        assertNotNull(runBlocking { storage.tokens() })
    }

    @Test
    fun `un login rechazado muestra el motivo y conserva el correo`() {
        abrirServidor()
        servidor.enqueue(
            MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json")
                .setBody("""{"error":"UNAUTHORIZED","mensaje":"Credenciales inválidas","status":401}"""),
        )
        val vm = LoginViewModel(autenticacion(FakeTokenStorage()))
        vm.onEmail("monitor.demo@example.com")
        vm.onPassword("mala")

        vm.iniciarSesion()
        esperar { vm.ui.first { !it.cargando && it.error != null } }

        assertEquals("Credenciales inválidas", vm.ui.value.error)
        assertEquals("monitor.demo@example.com", vm.ui.value.email)
    }

    @Test
    fun `escribir despues de un error lo borra`() {
        abrirServidor()
        val vm = LoginViewModel(autenticacion(FakeTokenStorage()))
        vm.iniciarSesion() // campos vacíos -> error
        assertNotNull(vm.ui.value.error)

        vm.onEmail("a")

        assertNull(vm.ui.value.error)
    }

    // ------------------------------------------------------------------ sesión

    @Test
    fun `sin tokens la sesion es SinSesion y con tokens validos es Activa con su rol`() {
        abrirServidor()
        val sinSesion = SesionViewModel(autenticacion(FakeTokenStorage()))
        assertEquals(EstadoSesion.SinSesion, sinSesion.estado.value)

        val conSesion = SesionViewModel(
            autenticacion(FakeTokenStorage(Tokens("a", jwtDePrueba(grupos = listOf("COORDINADOR")), "r"))),
        )
        val estado = conSesion.estado.value as EstadoSesion.Activa
        assertTrue(estado.usuario.esCoordinador)
    }

    @Test
    fun `tokens de un usuario sin grupo valido no abren la app`() {
        abrirServidor()
        val vm = SesionViewModel(
            autenticacion(FakeTokenStorage(Tokens("a", jwtDePrueba(grupos = listOf("USER")), "r"))),
        )

        assertEquals(EstadoSesion.SinSesion, vm.estado.value)
    }

    @Test
    fun `cuando los tokens desaparecen la sesion vuelve a SinSesion`() {
        abrirServidor()
        val storage = FakeTokenStorage(Tokens("a", jwtDePrueba(), "r"))
        val vm = SesionViewModel(autenticacion(storage))
        assertTrue(vm.estado.value is EstadoSesion.Activa)

        runBlocking { storage.limpiar() }

        assertEquals(EstadoSesion.SinSesion, vm.estado.value)
    }

    // ------------------------------------------------------------------ quórum del coordinador

    @Test
    fun `el quorum se carga desde el servidor`() {
        val api = FakeSyncApi().apply {
            alResumen = { ApiResponse(true, ResumenEventoDto(eventoId = it, nombre = "Taller", totalMarcados = 18, validas = 16)) }
        }

        val vm = ResumenViewModel(12, api)

        assertFalse(vm.ui.value.cargando)
        assertEquals(18L, vm.ui.value.resumen?.totalMarcados)
        assertNull(vm.ui.value.error)
    }

    @Test
    fun `un 403 explica que falta el grupo COORDINADOR`() {
        val api = FakeSyncApi().apply {
            alResumen = { throw HttpException(Response.error<Any>(403, "".toResponseBody())) }
        }

        val vm = ResumenViewModel(12, api)

        assertNull(vm.ui.value.resumen)
        assertTrue(vm.ui.value.error!!.contains("COORDINADOR"))
    }

    @Test
    fun `sin conexion el quorum avisa y se puede reintentar`() {
        val api = FakeSyncApi().apply { alResumen = { throw IOException("sin red") } }
        val vm = ResumenViewModel(12, api)
        assertTrue(vm.ui.value.error!!.contains("Sin conexión"))

        api.alResumen = { ApiResponse(true, ResumenEventoDto(eventoId = it, totalMarcados = 3)) }
        vm.cargar()

        assertNull(vm.ui.value.error)
        assertEquals(3L, vm.ui.value.resumen?.totalMarcados)
    }

    // ------------------------------------------------------------------ textos

    @Test
    fun `los motivos de rechazo se explican en lenguaje simple`() {
        assertTrue(motivoLegible("EVENTO_NO_ENCONTRADO").contains("evento"))
        assertTrue(motivoLegible("ASISTENTE_NO_ENCONTRADO").contains("asistente"))
        assertTrue(motivoLegible("DATOS_INVALIDOS: latitud").contains("latitud"))
        assertTrue(motivoLegible("ASISTENCIA_NO_ENCONTRADA").contains("asistencia"))
        assertEquals("Rechazado por el servidor.", motivoLegible(null))
    }
}
