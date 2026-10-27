package com.android.syncpass.ui

import com.android.syncpass.core.Coordenadas
import com.android.syncpass.core.UbicacionProvider
import com.android.syncpass.data.RegistroRepository
import com.android.syncpass.data.local.AsistenteEntity
import com.android.syncpass.data.local.EstadoSync
import com.android.syncpass.data.local.EventoEntity
import com.android.syncpass.sync.SyncScheduler
import com.android.syncpass.testing.FakeAsistenciaDao
import com.android.syncpass.testing.FakeCatalogoDao
import com.android.syncpass.testing.FakeEncuestaDao
import com.android.syncpass.testing.MainDispatcherRule
import com.android.syncpass.testing.RelojFijo
import com.android.syncpass.testing.UuidSecuencial
import com.android.syncpass.ui.asistencia.MarcarViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private class PlanificadorFalso : SyncScheduler {
    var pedidos = 0

    override fun solicitarAhora() {
        pedidos++
    }

    override fun programarPeriodica() = Unit
}

private class UbicacionDePrueba(var coordenadas: Coordenadas? = Coordenadas(-33.5122, -70.6001)) : UbicacionProvider {
    override suspend fun actual() = coordenadas
}

/** Lo que ve el monitor al dar de alta a alguien sin inscripción previa (caso §3.1). */
class AltaEnTerrenoViewModelTest {

    @get:Rule
    val principal = MainDispatcherRule()

    private val sub = "sub-monitor"
    private lateinit var catalogo: FakeCatalogoDao
    private lateinit var asistencias: FakeAsistenciaDao
    private lateinit var planificador: PlanificadorFalso
    private lateinit var ubicacion: UbicacionDePrueba

    @Before
    fun preparar() {
        catalogo = FakeCatalogoDao().apply {
            eventos[1] = EventoEntity(1, 1, "Taller", "2026-11-05T15:00:00Z", 30, "Sede", -33.5, -70.6, 200)
            asistentes[340] = AsistenteEntity(340, "ASI-0001", "VECINO", "INSCRITO")
        }
        asistencias = FakeAsistenciaDao()
        planificador = PlanificadorFalso()
        ubicacion = UbicacionDePrueba()
    }

    private fun vm(evento: Long = 1) = MarcarViewModel(
        eventoId = evento,
        sub = sub,
        registro = RegistroRepository(catalogo, asistencias, FakeEncuestaDao(), RelojFijo(), UuidSecuencial()),
        catalogoDao = catalogo,
        asistenciaDao = asistencias,
        ubicacion = ubicacion,
        scheduler = planificador,
        reloj = RelojFijo(),
    )

    @Test
    fun `dar de alta registra, avisa, ofrece la encuesta y pide sincronizar`() {
        val vm = vm()

        vm.altaYMarcar("VECINO")

        val aviso = vm.ultimo.value!!
        assertFalse(aviso.esError)
        assertTrue(aviso.texto, aviso.texto.contains("Persona nueva registrada y marcada"))
        assertTrue("avisa que viaja cuando haya conexión", aviso.texto.contains("conexión"))
        assertNotNull("se puede abrir su encuesta de inmediato", aviso.asistenciaUuid)
        assertEquals(1, asistencias.filas.size)
        assertEquals(1, planificador.pedidos)
        assertEquals(1, vm.sesionMarcadas.value)
    }

    @Test
    fun `el asistente nuevo aparece en la lista de ese evento como ya marcado`() {
        val vm = vm()

        vm.altaYMarcar("ESTUDIANTE")

        // Se reabre la pantalla (nuevo ViewModel): el fake del catálogo toma una foto al crearse. Con tiempo límite para no colgar.
        val lista = runBlocking { withTimeout(5_000) { vm().asistentes.first { it.size == 2 } } }
        val nuevo = lista.single { it.asistente.esProvisional }
        assertTrue("queda marcado: no se puede marcar dos veces", nuevo.marcado)
        assertEquals(EstadoSync.PENDIENTE, nuevo.asistente.estadoSync)
        assertEquals("SIN_INSCRIPCION", nuevo.asistente.estadoInscripcion)
        assertFalse("el del catálogo sigue sin marcar", lista.single { it.asistente.id == 340L }.marcado)
    }

    @Test
    fun `sin ubicacion registra igual`() {
        ubicacion.coordenadas = null
        val vm = vm()

        vm.altaYMarcar("DOCENTE")

        assertFalse(vm.ultimo.value!!.esError)
        assertEquals(null, asistencias.filas.values.single().latitud)
    }

    @Test
    fun `con la ubicacion disponible la guarda en la marcacion`() {
        val vm = vm()

        vm.altaYMarcar("VECINO")

        assertEquals(-33.5122, asistencias.filas.values.single().latitud!!, 0.0)
    }

    @Test
    fun `un evento que ya no esta en el catalogo avisa y no crea nada`() {
        val vm = vm(evento = 999)

        vm.altaYMarcar("VECINO")

        assertTrue(vm.ultimo.value!!.esError)
        assertEquals(1, catalogo.asistentes.size)
        assertTrue(asistencias.filas.isEmpty())
        assertEquals(0, planificador.pedidos)
    }

    @Test
    fun `dos altas seguidas crean dos personas distintas`() {
        val vm = vm()

        vm.altaYMarcar("VECINO")
        vm.altaYMarcar("VECINO")

        assertEquals(2, asistencias.filas.size)
        assertEquals(setOf(-1L, -2L), catalogo.asistentes.keys.filter { it < 0 }.toSet())
        assertEquals(2, vm.sesionMarcadas.value)
    }
}
