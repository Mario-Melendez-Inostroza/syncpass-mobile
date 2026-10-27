package com.android.syncpass.data

import com.android.syncpass.core.Coordenadas
import com.android.syncpass.data.local.AsistenciaEntity
import com.android.syncpass.data.local.AsistenteEntity
import com.android.syncpass.data.local.EstadoSync
import com.android.syncpass.data.local.EventoEntity
import com.android.syncpass.testing.FakeAsistenciaDao
import com.android.syncpass.testing.FakeCatalogoDao
import com.android.syncpass.testing.FakeEncuestaDao
import com.android.syncpass.testing.RelojFijo
import com.android.syncpass.testing.TransaccionConReversion
import com.android.syncpass.testing.UuidSecuencial
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Alta de asistentes en terreno (caso §3.1): quien llega sin inscripción se registra y se marca en un solo paso. */
class AltaEnTerrenoRepositoryTest {

    private val sub = "sub-monitor"
    private lateinit var catalogo: FakeCatalogoDao
    private lateinit var asistencias: FakeAsistenciaDao
    private lateinit var reloj: RelojFijo
    private lateinit var repo: RegistroRepository

    @Before
    fun preparar() {
        catalogo = FakeCatalogoDao().apply {
            eventos[1] = EventoEntity(1, 1, "Taller", "2026-11-05T15:00:00Z", 30, "Sede", -33.5, -70.6, 200)
            asistentes[340] = AsistenteEntity(340, "ASI-0001", "VECINO", "INSCRITO")
        }
        asistencias = FakeAsistenciaDao()
        reloj = RelojFijo()
        repo = RegistroRepository(catalogo, asistencias, FakeEncuestaDao(), reloj, UuidSecuencial())
    }

    private fun altaYMarcar(tipo: String = "VECINO", coordenadas: Coordenadas? = null, evento: Long = 1) =
        runBlocking { repo.altaYMarcar(evento, tipo, sub, coordenadas) }

    @Test
    fun `crea al asistente provisional y su marcacion en un solo paso`() {
        val resultado = altaYMarcar("ESTUDIANTE", Coordenadas(-33.5122, -70.6001))

        assertTrue(resultado is ResultadoMarcacion.Registrada)
        val asistente = catalogo.asistentes.getValue(-1)
        assertEquals("ESTUDIANTE", asistente.tipo)
        assertEquals("SIN_INSCRIPCION", asistente.estadoInscripcion)
        assertEquals(EstadoSync.PENDIENTE, asistente.estadoSync)
        assertEquals("uuid-1", asistente.uuid)
        assertTrue("es provisional hasta que el servidor confirme", asistente.esProvisional)

        val asistencia = asistencias.filas.values.single()
        assertEquals("uuid-2", asistencia.uuid)
        assertEquals(-1L, asistencia.asistenteId)
        assertEquals(1L, asistencia.eventoId)
        assertEquals(EstadoSync.PENDIENTE, asistencia.estadoSync)
        assertEquals(sub, asistencia.usuarioSub)
        assertEquals(reloj.ahoraMillis(), asistencia.marcadoEnMillis)
        assertEquals(-33.5122, asistencia.latitud!!, 0.0)
    }

    @Test
    fun `el codigo provisional es legible y no contiene datos personales`() {
        // Con un uuid de verdad (el generador de pruebas "uuid-1" es demasiado corto para dar 6 caracteres).
        val real = object : com.android.syncpass.core.GeneradorUuid {
            override fun nuevo() = "9f1c7e62-aaaa-4bbb-8ccc-0123456789ab"
        }
        val otro = RegistroRepository(catalogo, asistencias, FakeEncuestaDao(), reloj, real)

        runBlocking { otro.altaYMarcar(1, "VECINO", sub, null) }

        val codigo = catalogo.asistentes.getValue(-1).codigo
        assertEquals("NUEVO-9F1C7E", codigo)
        assertEquals(codigo, codigo.uppercase())
    }

    @Test
    fun `sin ubicacion tambien registra - el GPS nunca bloquea la marcacion`() {
        altaYMarcar(coordenadas = null)

        val asistencia = asistencias.filas.values.single()
        assertEquals(null, asistencia.latitud)
        assertEquals(null, asistencia.longitud)
    }

    @Test
    fun `cada alta recibe un id provisional propio y distinto`() {
        altaYMarcar()
        altaYMarcar("DOCENTE")
        altaYMarcar("VECINO")

        assertEquals(listOf(-3L, -2L, -1L), catalogo.asistentes.keys.filter { it < 0 }.sorted())
        assertEquals(3, asistencias.filas.size)
        assertEquals(3, asistencias.filas.values.map { it.asistenteId }.toSet().size)
    }

    @Test
    fun `los asistentes del catalogo no se tocan`() {
        altaYMarcar()

        val delCatalogo = catalogo.asistentes.getValue(340)
        assertEquals("ASI-0001", delCatalogo.codigo)
        assertEquals(EstadoSync.SINCRONIZADO, delCatalogo.estadoSync)
        assertFalse(delCatalogo.esProvisional)
    }

    @Test
    fun `un evento que no esta en el catalogo no crea nada`() {
        val resultado = altaYMarcar(evento = 999)

        assertEquals(ResultadoMarcacion.EventoDesconocido, resultado)
        assertEquals(1, catalogo.asistentes.size)
        assertTrue(asistencias.filas.isEmpty())
    }

    @Test
    fun `un tipo que el servidor no acepta es un error de programacion, no un registro silencioso`() {
        try {
            altaYMarcar("MARCIANO")
            fail("debía rechazar el tipo")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("MARCIANO"))
        }
        assertEquals(1, catalogo.asistentes.size)
        assertTrue(asistencias.filas.isEmpty())
    }

    @Test
    fun `si falla la marcacion el asistente tampoco queda - las dos escrituras van juntas o ninguna`() {
        // Una marcación previa de OTRO asistente en el mismo evento no estorba; forzamos el fallo con un uuid repetido.
        asistencias.filas["uuid-2"] = AsistenciaEntity("uuid-2", 1, 340, 1L, null, null, sub)
        val atomico = RegistroRepository(
            catalogo, asistencias, FakeEncuestaDao(), reloj, UuidSecuencial(),
            TransaccionConReversion(catalogo, asistencias),
        )

        try {
            runBlocking { atomico.altaYMarcar(1, "VECINO", sub, null) }
            fail("la marcación con uuid repetido debía fallar")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // esperado
        }

        assertEquals("no queda un asistente huérfano", setOf(340L), catalogo.asistentes.keys)
        assertEquals(1, asistencias.filas.size)
    }

    @Test
    fun `las tres opciones de tipo son las que acepta el servidor`() {
        assertEquals(listOf("ESTUDIANTE", "VECINO", "DOCENTE"), RegistroRepository.TIPOS_ASISTENTE)
    }
}
