package com.android.syncpass.data

import com.android.syncpass.core.Coordenadas
import com.android.syncpass.data.local.AsistenteEntity
import com.android.syncpass.data.local.EstadoSync
import com.android.syncpass.data.local.EventoEntity
import com.android.syncpass.testing.FakeAsistenciaDao
import com.android.syncpass.testing.FakeCatalogoDao
import com.android.syncpass.testing.FakeEncuestaDao
import com.android.syncpass.testing.RelojFijo
import com.android.syncpass.testing.UuidSecuencial
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegistroRepositoryTest {

    private val sub = "sub-monitor"
    private lateinit var catalogo: FakeCatalogoDao
    private lateinit var asistencias: FakeAsistenciaDao
    private lateinit var encuestas: FakeEncuestaDao
    private lateinit var reloj: RelojFijo
    private lateinit var repo: RegistroRepository

    @Before
    fun preparar() {
        catalogo = FakeCatalogoDao().apply {
            eventos[1] = EventoEntity(1, 1, "Taller", "2026-11-05T15:00:00Z", 30, "Sede", -33.5, -70.6, 200)
            asistentes[340] = AsistenteEntity(340, "ASI-0001", "VECINO", "INSCRITO")
            asistentes[341] = AsistenteEntity(341, "ASI-0002", "ESTUDIANTE", "INSCRITO")
        }
        asistencias = FakeAsistenciaDao()
        encuestas = FakeEncuestaDao()
        reloj = RelojFijo()
        repo = RegistroRepository(catalogo, asistencias, encuestas, reloj, UuidSecuencial())
    }

    private fun marcar(asistente: Long = 340, evento: Long = 1, coordenadas: Coordenadas? = null) =
        runBlocking { repo.marcarAsistencia(evento, asistente, sub, coordenadas) }

    // ------------------------------------------------------------------ asistencia

    @Test
    fun `una marcacion nueva queda PENDIENTE con la hora del dispositivo y quien la registro`() {
        reloj.ahora = 1_793_199_851_000L

        val resultado = marcar(coordenadas = Coordenadas(-33.5122, -70.6001)) as ResultadoMarcacion.Registrada

        val a = resultado.asistencia
        assertEquals("uuid-1", a.uuid)
        assertEquals(1L, a.eventoId)
        assertEquals(340L, a.asistenteId)
        assertEquals(1_793_199_851_000L, a.marcadoEnMillis)
        assertEquals(-33.5122, a.latitud!!, 0.0)
        assertEquals(-70.6001, a.longitud!!, 0.0)
        assertEquals(sub, a.usuarioSub)
        assertEquals(EstadoSync.PENDIENTE, a.estadoSync)
        assertEquals(a, asistencias.filas["uuid-1"])
    }

    @Test
    fun `sin ubicacion la marcacion se guarda igual`() {
        val resultado = marcar(coordenadas = null) as ResultadoMarcacion.Registrada

        assertNull(resultado.asistencia.latitud)
        assertNull(resultado.asistencia.longitud)
        assertEquals(1, asistencias.filas.size)
    }

    @Test
    fun `marcar dos veces al mismo asistente en el mismo evento no crea otra fila`() {
        val primera = marcar() as ResultadoMarcacion.Registrada

        val segunda = marcar()

        assertTrue(segunda is ResultadoMarcacion.YaMarcada)
        assertEquals(primera.asistencia, (segunda as ResultadoMarcacion.YaMarcada).asistencia)
        assertEquals(1, asistencias.filas.size)
    }

    @Test
    fun `dos asistentes distintos se marcan por separado`() {
        marcar(340)
        marcar(341)

        assertEquals(2, asistencias.filas.size)
        assertEquals(setOf("uuid-1", "uuid-2"), asistencias.filas.keys)
    }

    @Test
    fun `un evento o asistente que no esta en el catalogo no se marca`() {
        assertEquals(ResultadoMarcacion.EventoDesconocido, marcar(evento = 99))
        assertEquals(ResultadoMarcacion.AsistenteDesconocido, marcar(asistente = 999))
        assertTrue(asistencias.filas.isEmpty())
    }

    // ------------------------------------------------------------------ encuesta

    private fun conAsistencia(): String = (marcar() as ResultadoMarcacion.Registrada).asistencia.uuid

    private fun encuesta(uuid: String, satisfaccion: Int = 5, utilidad: Int = 4, comentarios: String? = "Muy útil") =
        runBlocking { repo.registrarEncuesta(uuid, satisfaccion, utilidad, comentarios, sub) }

    @Test
    fun `una encuesta valida queda PENDIENTE y colgada de su asistencia`() {
        val asistenciaUuid = conAsistencia()
        reloj.ahora = 1_793_203_451_000L

        val e = (encuesta(asistenciaUuid) as ResultadoEncuesta.Registrada).encuesta

        assertEquals(asistenciaUuid, e.asistenciaUuid)
        assertEquals(5, e.satisfaccion)
        assertEquals(4, e.utilidad)
        assertEquals("Muy útil", e.comentarios)
        assertEquals(1_793_203_451_000L, e.enviadaEnMillis)
        assertEquals(sub, e.usuarioSub)
        assertEquals(EstadoSync.PENDIENTE, e.estadoSync)
    }

    @Test
    fun `la escala es de 1 a 5`() {
        val asistenciaUuid = conAsistencia()

        assertTrue(encuesta(asistenciaUuid, satisfaccion = 0) is ResultadoEncuesta.Invalida)
        assertTrue(encuesta(asistenciaUuid, satisfaccion = 6) is ResultadoEncuesta.Invalida)
        assertTrue(encuesta(asistenciaUuid, utilidad = 0) is ResultadoEncuesta.Invalida)
        assertTrue(encuesta(asistenciaUuid, utilidad = 6) is ResultadoEncuesta.Invalida)
        assertTrue(encuestas.filas.isEmpty())

        assertTrue(encuesta(asistenciaUuid, satisfaccion = 1, utilidad = 5) is ResultadoEncuesta.Registrada)
    }

    @Test
    fun `no se puede valorar una asistencia que no existe`() {
        assertEquals(ResultadoEncuesta.AsistenciaDesconocida, encuesta("inexistente"))
    }

    @Test
    fun `una asistencia solo admite una encuesta`() {
        val asistenciaUuid = conAsistencia()
        encuesta(asistenciaUuid)

        assertEquals(ResultadoEncuesta.YaRespondida, encuesta(asistenciaUuid, satisfaccion = 1))
        assertEquals(1, encuestas.filas.size)
        assertEquals("no se pisa la respuesta original", 5, encuestas.filas.values.single().satisfaccion)
    }

    @Test
    fun `los comentarios se limpian y se acotan`() {
        val asistenciaUuid = conAsistencia()
        val e = (encuesta(asistenciaUuid, comentarios = "   Genial   ") as ResultadoEncuesta.Registrada).encuesta
        assertEquals("Genial", e.comentarios)

        val otra = (marcar(341) as ResultadoMarcacion.Registrada).asistencia.uuid
        val vacia = (encuesta(otra, comentarios = "   ") as ResultadoEncuesta.Registrada).encuesta
        assertNull("un comentario en blanco se guarda como ausente", vacia.comentarios)
    }

    @Test
    fun `un comentario demasiado largo se recorta al limite del servidor`() {
        val asistenciaUuid = conAsistencia()

        val e = (encuesta(asistenciaUuid, comentarios = "x".repeat(2500)) as ResultadoEncuesta.Registrada).encuesta

        assertEquals(RegistroRepository.MAX_COMENTARIO, e.comentarios!!.length)
    }
}
