package com.android.syncpass.sync

import com.android.syncpass.core.network.ApiResponse
import com.android.syncpass.core.network.AsistenteSyncDto
import com.android.syncpass.core.network.ResultadoAsistenteDto
import com.android.syncpass.core.network.ResultadoSyncDto
import com.android.syncpass.data.local.AsistenciaEntity
import com.android.syncpass.data.local.AsistenteEntity
import com.android.syncpass.data.local.EstadoSync
import com.android.syncpass.testing.FakeAsistenciaDao
import com.android.syncpass.testing.FakeCatalogoDao
import com.android.syncpass.testing.FakeEncuestaDao
import com.android.syncpass.testing.FakeSyncApi
import com.android.syncpass.testing.FakeSyncPrefs
import com.android.syncpass.testing.RelojFijo
import com.android.syncpass.testing.respuestaAsistentes
import com.android.syncpass.testing.respuestaLote
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Sincronización del alta de asistentes en terreno (caso §3.1): el asistente provisional (id negativo) se sube,
 * se reemplaza por el real y arrastra sus asistencias.
 */
class AltaEnTerrenoSyncTest {

    private val sub = "sub-monitor"
    private lateinit var api: FakeSyncApi
    private lateinit var catalogo: FakeCatalogoDao
    private lateinit var asistencias: FakeAsistenciaDao

    @Before
    fun preparar() {
        api = FakeSyncApi()
        catalogo = FakeCatalogoDao()
        asistencias = FakeAsistenciaDao()
    }

    private fun motor() =
        SyncEngine(api, catalogo, asistencias, FakeEncuestaDao(), FakeSyncPrefs(), { sub }, RelojFijo())

    private fun sincronizar() = runBlocking { motor().sincronizar() }

    /** Un alta hecha sin conexión: asistente provisional y su marcación pendiente. */
    private fun altaEnTerreno(id: Long = -1, uuid: String = "uuid-alta-1", tipo: String = "VECINO", evento: Long = 1) {
        catalogo.asistentes[id] = AsistenteEntity(
            id, "NUEVO-AB12CD", tipo, "SIN_INSCRIPCION", uuid = uuid, estadoSync = EstadoSync.PENDIENTE,
        )
        asistencias.filas["asis-$uuid"] = AsistenciaEntity(
            "asis-$uuid", evento, id, 1_793_199_851_000L, null, null, sub,
        )
    }

    private fun aceptado(uuid: String, id: Long, codigo: String, indice: Int = 0) =
        ResultadoAsistenteDto(indice, uuid, "ACEPTADO", null, id, codigo, null)

    @Test
    fun `el orden es catalogo, asistentes nuevos, asistencias y encuestas`() {
        altaEnTerreno()
        api.alSubirAsistentes = { respuestaAsistentes(listOf(aceptado("uuid-alta-1", 700, "TER-AAAAAAAAAAAA"))) }
        api.alSubirAsistencias = { respuestaLote(listOf(ResultadoSyncDto(0, "asis-uuid-alta-1", "ACEPTADO", null, 900))) }

        sincronizar()

        assertEquals(listOf("catalogo", "asistentes", "asistencias"), api.llamadas)
    }

    @Test
    fun `sin altas pendientes no se llama al endpoint de asistentes`() {
        sincronizar()

        assertEquals(listOf("catalogo"), api.llamadas)
    }

    @Test
    fun `sube solo el uuid y el tipo - ni codigo ni inscripcion los decide el telefono`() {
        altaEnTerreno(tipo = "DOCENTE")
        api.alSubirAsistentes = { respuestaAsistentes(emptyList()) }

        sincronizar()

        assertEquals(listOf(AsistenteSyncDto("uuid-alta-1", "DOCENTE")), api.lotesAsistentes.single().registros)
    }

    @Test
    fun `el servidor acepta - se reemplaza el provisional por el real y la asistencia se sube con el id real`() {
        altaEnTerreno()
        api.alSubirAsistentes = { respuestaAsistentes(listOf(aceptado("uuid-alta-1", 700, "TER-9F1C7E62AAAA"))) }
        api.alSubirAsistencias = { respuestaLote(listOf(ResultadoSyncDto(0, "asis-uuid-alta-1", "ACEPTADO", null, 900))) }

        val resultado = sincronizar() as ResultadoSync.Completa

        // El provisional desapareció y el real lo reemplaza, con el código que asignó el servidor.
        assertNull(catalogo.asistentes[-1])
        val real = catalogo.asistentes.getValue(700)
        assertEquals("TER-9F1C7E62AAAA", real.codigo)
        assertEquals("VECINO", real.tipo)
        assertEquals("SIN_INSCRIPCION", real.estadoInscripcion)
        assertEquals(EstadoSync.SINCRONIZADO, real.estadoSync)
        assertFalse(real.esProvisional)
        // La asistencia viajó con el id REAL (si hubiera ido con -1 el servidor la rechazaría).
        assertEquals(700L, api.lotesAsistencias.single().registros.single().asistenteId)
        assertEquals(EstadoSync.SINCRONIZADO, asistencias.filas.getValue("asis-uuid-alta-1").estadoSync)
        assertEquals(700L, asistencias.filas.getValue("asis-uuid-alta-1").asistenteId)
        assertEquals(1, resultado.resumen.asistentesNuevos.sincronizados)
        // El alta no se cuenta dos veces en el aviso: viaja con su asistencia.
        assertEquals(1, resultado.resumen.totalSincronizados)
    }

    @Test
    fun `varias altas se reemplazan cada una por su id real y no se mezclan`() {
        altaEnTerreno(id = -1, uuid = "u-1")
        altaEnTerreno(id = -2, uuid = "u-2", tipo = "ESTUDIANTE")
        api.alSubirAsistentes = {
            respuestaAsistentes(
                listOf(aceptado("u-1", 701, "TER-111111111111", 0), aceptado("u-2", 702, "TER-222222222222", 1)),
            )
        }
        api.alSubirAsistencias = { respuestaLote(emptyList()) }

        sincronizar()

        assertEquals(setOf(701L, 702L), catalogo.asistentes.keys)
        assertEquals(701L, asistencias.filas.getValue("asis-u-1").asistenteId)
        assertEquals(702L, asistencias.filas.getValue("asis-u-2").asistenteId)
        assertEquals("ESTUDIANTE", catalogo.asistentes.getValue(702).tipo)
    }

    @Test
    fun `una asistencia de un asistente aun provisional NO se sube`() {
        altaEnTerreno()
        // El servidor no responde al alta (sin red): las asistencias no deben salir con un id negativo.
        api.alSubirAsistentes = { throw IOException("sin red") }

        val resultado = sincronizar()

        assertTrue(resultado is ResultadoSync.ErrorRed)
        assertFalse("asistencias" in api.llamadas)
        assertEquals(EstadoSync.PENDIENTE, asistencias.filas.getValue("asis-uuid-alta-1").estadoSync)
        assertEquals(-1L, asistencias.filas.getValue("asis-uuid-alta-1").asistenteId)
        assertTrue("el alta sigue pendiente para el próximo intento", catalogo.asistentes.getValue(-1).esProvisional)
    }

    @Test
    fun `si se pierde la respuesta del alta, el reenvio devuelve el mismo id y todo se resuelve`() {
        altaEnTerreno()
        api.alSubirAsistentes = { throw IOException("se cortó la conexión") }
        sincronizar()

        // El servidor SÍ lo había guardado: reenviar el mismo uuid responde DUPLICADO con el id ya asignado.
        api.alSubirAsistentes = {
            respuestaAsistentes(
                listOf(ResultadoAsistenteDto(0, "uuid-alta-1", "DUPLICADO", "UUID_YA_REGISTRADO", 700, "TER-9F1C7E62AAAA", null)),
            )
        }
        api.alSubirAsistencias = { respuestaLote(listOf(ResultadoSyncDto(0, "asis-uuid-alta-1", "ACEPTADO", null, 900))) }
        sincronizar()

        assertNull(catalogo.asistentes[-1])
        assertEquals("TER-9F1C7E62AAAA", catalogo.asistentes.getValue(700).codigo)
        assertEquals(EstadoSync.SINCRONIZADO, asistencias.filas.getValue("asis-uuid-alta-1").estadoSync)
        assertEquals(1, asistencias.filas.size)
    }

    @Test
    fun `si el catalogo ya trajo al asistente real antes del reemplazo no se duplica ni choca con el indice unico`() {
        altaEnTerreno()
        // Respuesta perdida + catálogo posterior: ya existe la fila real (sin uuid) con el mismo código que dará el alta.
        catalogo.asistentes[700] = AsistenteEntity(700, "TER-9F1C7E62AAAA", "VECINO", "SIN_INSCRIPCION")
        api.alSubirAsistentes = { respuestaAsistentes(listOf(aceptado("uuid-alta-1", 700, "TER-9F1C7E62AAAA"))) }
        api.alSubirAsistencias = { respuestaLote(emptyList()) }

        sincronizar()

        assertEquals(setOf(700L), catalogo.asistentes.keys)
        assertEquals(700L, asistencias.filas.getValue("asis-uuid-alta-1").asistenteId)
    }

    @Test
    fun `un alta rechazada queda como fallida con su motivo y sus asistencias esperan sin subirse`() {
        altaEnTerreno()
        api.alSubirAsistentes = {
            respuestaAsistentes(
                listOf(ResultadoAsistenteDto(0, "uuid-alta-1", "RECHAZADO", "DATOS_INVALIDOS", null, null, "tipo")),
            )
        }

        val resultado = sincronizar() as ResultadoSync.Completa

        val asistente = catalogo.asistentes.getValue(-1)
        assertEquals(EstadoSync.FALLIDO, asistente.estadoSync)
        assertEquals("DATOS_INVALIDOS: tipo", asistente.motivoFallo)
        assertEquals(1, resultado.resumen.asistentesNuevos.fallidos)
        assertFalse("asistencias" in api.llamadas)
        assertEquals(EstadoSync.PENDIENTE, asistencias.filas.getValue("asis-uuid-alta-1").estadoSync)
        // Un alta fallida ya no se reintenta sola en cada ciclo.
        api.llamadas.clear()
        sincronizar()
        assertFalse("asistentes" in api.llamadas)
    }

    @Test
    fun `una respuesta sin id o sin codigo no reemplaza nada y se reintenta`() {
        altaEnTerreno()
        api.alSubirAsistentes = { respuestaAsistentes(listOf(ResultadoAsistenteDto(0, "uuid-alta-1", "ACEPTADO", null, null, null, null))) }

        val resultado = sincronizar() as ResultadoSync.Completa

        assertTrue(catalogo.asistentes.getValue(-1).esProvisional)
        assertEquals(EstadoSync.PENDIENTE, catalogo.asistentes.getValue(-1).estadoSync)
        assertEquals(0, resultado.resumen.asistentesNuevos.sincronizados)
        assertEquals(1, resultado.resumen.asistentesNuevos.pendientes)
    }

    @Test
    fun `una respuesta mal formada del servidor es un error de servidor y no toca la base`() {
        altaEnTerreno()
        api.alSubirAsistentes = { ApiResponse(success = false, data = null) }

        val resultado = sincronizar()

        assertEquals(ResultadoSync.ErrorServidor(null), resultado)
        assertTrue(catalogo.asistentes.getValue(-1).esProvisional)
    }
}
