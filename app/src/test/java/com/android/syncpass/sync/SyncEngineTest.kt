package com.android.syncpass.sync

import com.android.syncpass.core.network.ApiResponse
import com.android.syncpass.core.network.AsistenteDto
import com.android.syncpass.core.network.CatalogoDto
import com.android.syncpass.core.network.EventoDto
import com.android.syncpass.core.network.ProyectoDto
import com.android.syncpass.core.network.ResultadoSyncDto
import com.android.syncpass.data.local.AsistenciaEntity
import com.android.syncpass.data.local.EncuestaEntity
import com.android.syncpass.data.local.EstadoSync
import com.android.syncpass.testing.FakeAsistenciaDao
import com.android.syncpass.testing.FakeCatalogoDao
import com.android.syncpass.testing.FakeEncuestaDao
import com.android.syncpass.testing.FakeSyncApi
import com.android.syncpass.testing.FakeSyncPrefs
import com.android.syncpass.testing.RelojFijo
import com.android.syncpass.testing.respuestaLote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant

/** La lógica de sincronización contra un servidor y una base en memoria. */
class SyncEngineTest {

    private val sub = "sub-monitor"
    private lateinit var api: FakeSyncApi
    private lateinit var catalogo: FakeCatalogoDao
    private lateinit var asistencias: FakeAsistenciaDao
    private lateinit var encuestas: FakeEncuestaDao
    private lateinit var prefs: FakeSyncPrefs
    private lateinit var reloj: RelojFijo

    @Before
    fun preparar() {
        api = FakeSyncApi()
        catalogo = FakeCatalogoDao()
        asistencias = FakeAsistenciaDao()
        encuestas = FakeEncuestaDao()
        prefs = FakeSyncPrefs()
        reloj = RelojFijo()
    }

    private fun motor(subActual: suspend () -> String? = { sub }) =
        SyncEngine(api, catalogo, asistencias, encuestas, prefs, subActual, reloj)

    private fun asistencia(
        uuid: String,
        asistente: Long = uuid.hashCode().toLong().coerceAtLeast(1),
        usuario: String = sub,
        estado: EstadoSync = EstadoSync.PENDIENTE,
        marcado: Long = 1_793_199_851_000L,
        lat: Double? = null,
        lon: Double? = null,
        motivo: String? = null,
    ) = AsistenciaEntity(uuid, eventoId = 1, asistenteId = asistente, marcadoEnMillis = marcado, latitud = lat,
        longitud = lon, usuarioSub = usuario, estadoSync = estado, motivoFallo = motivo)

    private fun encuesta(uuid: String, asistenciaUuid: String, usuario: String = sub) = EncuestaEntity(
        uuid, asistenciaUuid, satisfaccion = 5, utilidad = 4, comentarios = "Muy útil",
        enviadaEnMillis = 1_793_203_451_000L, usuarioSub = usuario,
    )

    private fun guardar(vararg lista: AsistenciaEntity) = lista.forEach { asistencias.filas[it.uuid] = it }
    private fun guardar(vararg lista: EncuestaEntity) = lista.forEach { encuestas.filas[it.uuid] = it }

    private fun res(
        uuid: String?,
        estado: String,
        indice: Int = 0,
        motivo: String? = null,
        id: Long? = null,
        validacion: String? = null,
        distancia: Int? = null,
        detalle: String? = null,
    ) = ResultadoSyncDto(indice, uuid, estado, motivo, id, validacion, distancia, detalle)

    private fun sincronizar(motor: SyncEngine = motor()) = runBlocking { motor.sincronizar() }

    private fun httpError(codigo: Int) = HttpException(Response.error<Any>(codigo, "".toResponseBody()))

    // ------------------------------------------------------------------ sesión

    @Test
    fun `sin sesion no toca la red`() {
        guardar(asistencia("a1"))

        val resultado = sincronizar(motor(subActual = { null }))

        assertEquals(ResultadoSync.SinSesion, resultado)
        assertTrue(api.llamadas.isEmpty())
    }

    // ------------------------------------------------------------------ catálogo

    @Test
    fun `la primera descarga pide todo el catalogo y las siguientes solo lo nuevo`() {
        api.alCatalogo = { ApiResponse(true, CatalogoDto(servidorEn = "2026-10-28T15:00:00Z")) }
        sincronizar()

        api.alCatalogo = { ApiResponse(true, CatalogoDto(servidorEn = "2026-10-28T16:00:00Z")) }
        sincronizar()

        assertEquals(listOf(null, "2026-10-28T15:00:00Z"), api.desdeRecibidos)
        assertEquals("2026-10-28T16:00:00Z", prefs.catalogoEn)
    }

    @Test
    fun `guarda el catalogo y descarta las filas incompletas`() {
        api.alCatalogo = {
            ApiResponse(
                true,
                CatalogoDto(
                    servidorEn = "2026-10-28T15:00:00Z",
                    proyectos = listOf(
                        ProyectoDto(1, "Alfabetización digital", null, "tecnologia", "UV-12", "2026-08-01", null, "EN_CURSO"),
                        ProyectoDto(id = 2), // incompleto
                    ),
                    eventos = listOf(
                        EventoDto(10, 1, "Taller", "2026-11-05T15:00:00Z", 30, "Sede", -33.5, -70.6, null),
                        EventoDto(id = 11, nombre = "sin proyecto"),
                    ),
                    asistentes = listOf(
                        AsistenteDto(100, "ASI-0001", "VECINO", "INSCRITO"),
                        AsistenteDto(id = 101),
                    ),
                ),
            )
        }

        val resultado = sincronizar() as ResultadoSync.Completa

        assertEquals(setOf(1L), catalogo.proyectos.keys)
        assertEquals(setOf(10L), catalogo.eventos.keys)
        assertEquals("un evento sin radio usa el de por defecto", 200, catalogo.eventos[10]?.radioMetros)
        assertEquals(setOf(100L), catalogo.asistentes.keys)
        assertEquals(ResumenCatalogo(1, 1, 1), resultado.resumen.catalogo)
    }

    @Test
    fun `si la descarga falla no se avanza la marca de catalogo`() {
        prefs.catalogoEn = "2026-10-01T00:00:00Z"
        api.alCatalogo = { throw IOException("sin red") }

        val resultado = sincronizar()

        assertTrue(resultado is ResultadoSync.ErrorRed)
        assertEquals("2026-10-01T00:00:00Z", prefs.catalogoEn)
    }

    @Test
    fun `un catalogo con novedades da otra oportunidad a lo que fallo por referencia desconocida`() {
        guardar(
            asistencia("a1", motivo = "EVENTO_NO_ENCONTRADO", estado = EstadoSync.FALLIDO),
            asistencia("a2", motivo = "ASISTENTE_NO_ENCONTRADO", estado = EstadoSync.FALLIDO),
            asistencia("a3", motivo = "DATOS_INVALIDOS: latitud", estado = EstadoSync.FALLIDO),
        )
        api.alCatalogo = {
            ApiResponse(true, CatalogoDto("2026-10-28T15:00:00Z", eventos = listOf(
                EventoDto(1, 1, "Taller", "2026-11-05T15:00:00Z", 30, "Sede", null, null, 200),
            )))
        }
        api.alSubirAsistencias = { respuestaLote(emptyList()) }

        sincronizar()

        // a1 y a2 volvieron a PENDIENTE y se enviaron; a3 (datos inválidos) no se reintenta jamás.
        assertEquals(listOf("a1", "a2"), api.lotesAsistencias.single().registros.map { it.uuid }.sorted())
        assertEquals(EstadoSync.FALLIDO, asistencias.filas["a3"]?.estadoSync)
    }

    @Test
    fun `un catalogo sin novedades no reabre los fallidos`() {
        guardar(asistencia("a1", motivo = "EVENTO_NO_ENCONTRADO", estado = EstadoSync.FALLIDO))

        sincronizar()

        assertEquals(EstadoSync.FALLIDO, asistencias.filas["a1"]?.estadoSync)
        assertTrue(api.lotesAsistencias.isEmpty())
    }

    // ------------------------------------------------------------------ subida de asistencias

    @Test
    fun `ACEPTADO y DUPLICADO dejan la asistencia SINCRONIZADA`() {
        guardar(asistencia("a1"), asistencia("a2"))
        api.alSubirAsistencias = {
            respuestaLote(
                listOf(
                    res("a1", "ACEPTADO", indice = 0, id = 881, validacion = "VALIDA", distancia = 37),
                    res("a2", "DUPLICADO", indice = 1, motivo = "UUID_YA_REGISTRADO", id = 455),
                ),
            )
        }

        val resultado = sincronizar() as ResultadoSync.Completa

        val a1 = asistencias.filas.getValue("a1")
        assertEquals(EstadoSync.SINCRONIZADO, a1.estadoSync)
        assertEquals(881L, a1.servidorId)
        assertEquals("VALIDA", a1.estadoValidacion)
        assertEquals(37, a1.distanciaMetros)
        assertEquals(EstadoSync.SINCRONIZADO, asistencias.filas.getValue("a2").estadoSync)
        assertEquals(ResumenLote(sincronizados = 2, fallidos = 0, pendientes = 0), resultado.resumen.asistencias)
    }

    @Test
    fun `un rechazo definitivo deja la asistencia FALLIDA con su motivo y detalle`() {
        guardar(asistencia("a1"))
        api.alSubirAsistencias = {
            respuestaLote(listOf(res("a1", "RECHAZADO", motivo = "DATOS_INVALIDOS", detalle = "latitud")))
        }

        sincronizar()

        val a1 = asistencias.filas.getValue("a1")
        assertEquals(EstadoSync.FALLIDO, a1.estadoSync)
        assertEquals("DATOS_INVALIDOS: latitud", a1.motivoFallo)
    }

    @Test
    fun `un lote con exitos y fallos parciales actualiza cada registro por separado`() {
        guardar(asistencia("ok1"), asistencia("dup"), asistencia("sin-evento"), asistencia("ok2"))
        api.alSubirAsistencias = {
            respuestaLote(
                listOf(
                    res("ok1", "ACEPTADO", 0, id = 1),
                    res("dup", "DUPLICADO", 1, motivo = "ASISTENTE_YA_MARCADO", id = 2),
                    res("sin-evento", "RECHAZADO", 2, motivo = "EVENTO_NO_ENCONTRADO"),
                    res("ok2", "ACEPTADO", 3, id = 4),
                ),
            )
        }

        val resultado = sincronizar() as ResultadoSync.Completa

        assertEquals(EstadoSync.SINCRONIZADO, asistencias.filas.getValue("ok1").estadoSync)
        assertEquals(EstadoSync.SINCRONIZADO, asistencias.filas.getValue("dup").estadoSync)
        assertEquals(EstadoSync.FALLIDO, asistencias.filas.getValue("sin-evento").estadoSync)
        assertEquals("EVENTO_NO_ENCONTRADO", asistencias.filas.getValue("sin-evento").motivoFallo)
        assertEquals(EstadoSync.SINCRONIZADO, asistencias.filas.getValue("ok2").estadoSync)
        assertEquals(3, resultado.resumen.asistencias.sincronizados)
        assertEquals(1, resultado.resumen.asistencias.fallidos)
    }

    @Test
    fun `solo se envian los registros del usuario con sesion`() {
        guardar(asistencia("mia"), asistencia("de-otro", usuario = "sub-otra-persona"))
        api.alSubirAsistencias = { respuestaLote(listOf(res("mia", "ACEPTADO", 0, id = 1))) }

        sincronizar()

        assertEquals(listOf("mia"), api.lotesAsistencias.single().registros.map { it.uuid })
        assertEquals(
            "lo de otro usuario espera a que ese usuario inicie sesión",
            EstadoSync.PENDIENTE,
            asistencias.filas.getValue("de-otro").estadoSync,
        )
    }

    @Test
    fun `lo ya sincronizado o fallido no se reenvia`() {
        guardar(
            asistencia("ya", estado = EstadoSync.SINCRONIZADO),
            asistencia("mal", estado = EstadoSync.FALLIDO, motivo = "DATOS_INVALIDOS"),
        )

        sincronizar()

        assertTrue(api.lotesAsistencias.isEmpty())
    }

    @Test
    fun `lo enviado respeta el contrato del servidor`() {
        guardar(asistencia("a1", asistente = 340, marcado = 1_793_199_851_123L, lat = -33.5122, lon = -70.6001))
        api.alSubirAsistencias = { respuestaLote(listOf(res("a1", "ACEPTADO", 0, id = 1))) }

        sincronizar()

        val enviado = api.lotesAsistencias.single().registros.single()
        assertEquals("a1", enviado.uuid)
        assertEquals(1L, enviado.eventoId)
        assertEquals(340L, enviado.asistenteId)
        assertEquals(-33.5122, enviado.latitud!!, 0.0)
        assertEquals(-70.6001, enviado.longitud!!, 0.0)
        // La hora real de la marcación viaja en ISO-8601 UTC, no la de la subida.
        assertEquals(Instant.ofEpochMilli(1_793_199_851_123L).toString(), enviado.marcadoEn)
        assertTrue(enviado.marcadoEn.endsWith("Z"))
    }

    @Test
    fun `sin ubicacion no se mandan coordenadas`() {
        guardar(asistencia("a1"))
        api.alSubirAsistencias = { respuestaLote(listOf(res("a1", "ACEPTADO", 0, id = 1))) }

        sincronizar()

        val enviado = api.lotesAsistencias.single().registros.single()
        assertNull(enviado.latitud)
        assertNull(enviado.longitud)
    }

    @Test
    fun `se parten los lotes grandes en grupos de 100`() {
        (1..250).forEach { guardar(asistencia("a$it", asistente = it.toLong())) }
        api.alSubirAsistencias = { req ->
            respuestaLote(req.registros.mapIndexed { i, r -> res(r.uuid, "ACEPTADO", i, id = i.toLong()) })
        }

        sincronizar()

        assertEquals(listOf(100, 100, 50), api.lotesAsistencias.map { it.registros.size })
        assertTrue(asistencias.filas.values.all { it.estadoSync == EstadoSync.SINCRONIZADO })
    }

    @Test
    fun `une el resultado con su registro por posicion si el uuid no viene`() {
        guardar(asistencia("a1"))
        api.alSubirAsistencias = {
            respuestaLote(listOf(res(uuid = null, estado = "RECHAZADO", indice = 0, motivo = "DATOS_INVALIDOS", detalle = "uuid")))
        }

        sincronizar()

        assertEquals(EstadoSync.FALLIDO, asistencias.filas.getValue("a1").estadoSync)
    }

    @Test
    fun `un estado desconocido deja el registro pendiente para el proximo ciclo`() {
        guardar(asistencia("a1"))
        api.alSubirAsistencias = { respuestaLote(listOf(res("a1", "ESTADO_NUEVO", 0))) }

        val resultado = sincronizar() as ResultadoSync.Completa

        assertEquals(EstadoSync.PENDIENTE, asistencias.filas.getValue("a1").estadoSync)
        assertEquals(1, resultado.resumen.asistencias.pendientes)
    }

    // ------------------------------------------------------------------ reintentos y cortes de red

    @Test
    fun `si se corta la red todo sigue pendiente y el reintento no duplica`() {
        guardar(asistencia("a1"), asistencia("a2"))
        api.alSubirAsistencias = { throw IOException("se cortó la conexión") }

        val primero = sincronizar()
        assertTrue(primero is ResultadoSync.ErrorRed)
        assertTrue(asistencias.filas.values.all { it.estadoSync == EstadoSync.PENDIENTE })

        // Vuelve la red. Se reenvían los MISMOS uuid, y el servidor responde DUPLICADO a lo que ya recibió.
        api.alSubirAsistencias = { req ->
            respuestaLote(req.registros.mapIndexed { i, r -> res(r.uuid, "DUPLICADO", i, motivo = "UUID_YA_REGISTRADO", id = i + 1L) })
        }
        val segundo = sincronizar() as ResultadoSync.Completa

        assertEquals(api.lotesAsistencias[0].registros.map { it.uuid }, api.lotesAsistencias[1].registros.map { it.uuid })
        assertEquals(2, segundo.resumen.asistencias.sincronizados)
        assertTrue(asistencias.filas.values.all { it.estadoSync == EstadoSync.SINCRONIZADO })
    }

    @Test
    fun `si la red cae a mitad solo queda pendiente lo no confirmado`() {
        (1..150).forEach { guardar(asistencia("a$it", asistente = it.toLong())) }
        var llamada = 0
        api.alSubirAsistencias = { req ->
            if (++llamada == 2) throw IOException("sin cobertura")
            respuestaLote(req.registros.mapIndexed { i, r -> res(r.uuid, "ACEPTADO", i, id = i.toLong()) })
        }

        val resultado = sincronizar()

        assertTrue(resultado is ResultadoSync.ErrorRed)
        assertEquals(100, asistencias.filas.values.count { it.estadoSync == EstadoSync.SINCRONIZADO })
        assertEquals(50, asistencias.filas.values.count { it.estadoSync == EstadoSync.PENDIENTE })
    }

    @Test
    fun `una respuesta sin exito no marca nada`() {
        guardar(asistencia("a1"))
        api.alSubirAsistencias = { ApiResponse(success = false, message = "error") }

        val resultado = sincronizar()

        assertEquals(ResultadoSync.ErrorServidor(null), resultado)
        assertEquals(EstadoSync.PENDIENTE, asistencias.filas.getValue("a1").estadoSync)
    }

    @Test
    fun `un error 500 se informa y no se pierde nada`() {
        guardar(asistencia("a1"))
        api.alSubirAsistencias = { throw httpError(500) }

        val resultado = sincronizar()

        assertEquals(ResultadoSync.ErrorServidor(500), resultado)
        assertEquals(EstadoSync.PENDIENTE, asistencias.filas.getValue("a1").estadoSync)
    }

    @Test
    fun `un 401 con la sesion viva es falta de red al renovar, no fin de sesion`() {
        api.alCatalogo = { throw httpError(401) }

        assertTrue(sincronizar() is ResultadoSync.ErrorRed)
    }

    @Test
    fun `un 401 con la sesion ya cerrada es SinSesion`() {
        var consultas = 0
        api.alCatalogo = { throw httpError(401) }

        val resultado = sincronizar(motor(subActual = { if (consultas++ == 0) sub else null }))

        assertEquals(ResultadoSync.SinSesion, resultado)
    }

    // ------------------------------------------------------------------ encuestas

    @Test
    fun `las asistencias se suben antes que las encuestas`() {
        guardar(asistencia("a1"))
        guardar(encuesta("e1", "a1"))
        api.alSubirAsistencias = { respuestaLote(listOf(res("a1", "ACEPTADO", 0, id = 1))) }
        api.alSubirEncuestas = { respuestaLote(listOf(res("e1", "ACEPTADO", 0, id = 1))) }

        sincronizar()

        assertEquals(listOf("catalogo", "asistencias", "encuestas"), api.llamadas)
        assertEquals(EstadoSync.SINCRONIZADO, encuestas.filas.getValue("e1").estadoSync)
    }

    @Test
    fun `la encuesta viaja con su escala y la hora del dispositivo`() {
        guardar(asistencia("a1", estado = EstadoSync.SINCRONIZADO))
        guardar(encuesta("e1", "a1"))
        api.alSubirEncuestas = { respuestaLote(listOf(res("e1", "ACEPTADO", 0, id = 1))) }

        sincronizar()

        val enviada = api.lotesEncuestas.single().registros.single()
        assertEquals("e1", enviada.uuid)
        assertEquals("a1", enviada.asistenciaUuid)
        assertEquals(5, enviada.satisfaccion)
        assertEquals(4, enviada.utilidad)
        assertEquals("Muy útil", enviada.comentarios)
        assertEquals(Instant.ofEpochMilli(1_793_203_451_000L).toString(), enviada.enviadaEn)
    }

    @Test
    fun `una encuesta que llega antes que su asistencia se queda PENDIENTE`() {
        guardar(asistencia("a1"))
        guardar(encuesta("e1", "a1"))
        // La asistencia sigue pendiente (el servidor no la devolvió), y por eso la encuesta es rechazada.
        api.alSubirAsistencias = { respuestaLote(emptyList()) }
        api.alSubirEncuestas = { respuestaLote(listOf(res("e1", "RECHAZADO", 0, motivo = "ASISTENCIA_NO_ENCONTRADA"))) }

        val resultado = sincronizar() as ResultadoSync.Completa

        assertEquals(EstadoSync.PENDIENTE, encuestas.filas.getValue("e1").estadoSync)
        assertEquals(1, resultado.resumen.encuestas.pendientes)
    }

    @Test
    fun `una encuesta cuya asistencia fallo definitivamente tambien falla`() {
        guardar(asistencia("a1", estado = EstadoSync.FALLIDO, motivo = "DATOS_INVALIDOS"))
        guardar(encuesta("e1", "a1"))
        api.alSubirEncuestas = { respuestaLote(listOf(res("e1", "RECHAZADO", 0, motivo = "ASISTENCIA_NO_ENCONTRADA"))) }

        sincronizar()

        assertEquals(EstadoSync.FALLIDO, encuestas.filas.getValue("e1").estadoSync)
        assertEquals("ASISTENCIA_NO_ENCONTRADA", encuestas.filas.getValue("e1").motivoFallo)
    }

    @Test
    fun `encuesta ya registrada se da por sincronizada`() {
        guardar(asistencia("a1", estado = EstadoSync.SINCRONIZADO))
        guardar(encuesta("e1", "a1"))
        api.alSubirEncuestas = { respuestaLote(listOf(res("e1", "DUPLICADO", 0, motivo = "ENCUESTA_YA_REGISTRADA", id = 9))) }

        sincronizar()

        assertEquals(EstadoSync.SINCRONIZADO, encuestas.filas.getValue("e1").estadoSync)
        assertEquals(9L, encuestas.filas.getValue("e1").servidorId)
    }

    @Test
    fun `una encuesta con datos invalidos falla sin frenar las demas`() {
        guardar(asistencia("a1", estado = EstadoSync.SINCRONIZADO), asistencia("a2", estado = EstadoSync.SINCRONIZADO))
        guardar(encuesta("e1", "a1"), encuesta("e2", "a2"))
        api.alSubirEncuestas = {
            respuestaLote(
                listOf(
                    res("e1", "RECHAZADO", 0, motivo = "DATOS_INVALIDOS", detalle = "satisfaccion"),
                    res("e2", "ACEPTADO", 1, id = 2),
                ),
            )
        }

        sincronizar()

        assertEquals(EstadoSync.FALLIDO, encuestas.filas.getValue("e1").estadoSync)
        assertEquals(EstadoSync.SINCRONIZADO, encuestas.filas.getValue("e2").estadoSync)
    }

    // ------------------------------------------------------------------ marca de sincronización

    @Test
    fun `una sincronizacion completa registra cuando termino`() {
        reloj.ahora = 1_793_300_000_000L

        sincronizar()

        assertEquals(1_793_300_000_000L, runBlocking { prefs.ultimaSincronizacion().first() })
    }

    @Test
    fun `una sincronizacion fallida no se registra como completada`() {
        api.alCatalogo = { throw IOException("sin red") }

        sincronizar()

        assertNull(runBlocking { prefs.ultimaSincronizacion().first() })
    }
}
