package com.android.syncpass.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.syncpass.data.local.AppDatabase
import com.android.syncpass.data.local.MIGRACION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migración de Room v1 -> v2 (altas de asistentes en terreno). Es una prueba INSTRUMENTADA: necesita un teléfono o
 * emulador (`./gradlew :app:connectedDebugAndroidTest`). Crea una base v1 con datos reales de la app, aplica la
 * migración y deja que Room valide que el esquema resultante es exactamente el de app/schemas/.../2.json.
 */
@RunWith(AndroidJUnit4::class)
class MigracionRoomTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migra1a2_conservaLosDatosYAgregaLasColumnasNuevas() {
        helper.createDatabase(NOMBRE, 1).apply {
            execSQL("INSERT INTO asistentes (id, codigo, tipo, estadoInscripcion) VALUES (340, 'DEMO-001', 'VECINO', 'INSCRITO')")
            execSQL(
                "INSERT INTO asistencias (uuid, eventoId, asistenteId, marcadoEnMillis, latitud, longitud, usuarioSub, " +
                    "estadoSync, motivoFallo, servidorId, estadoValidacion, distanciaMetros) " +
                    "VALUES ('a-1', 12, 340, 1000, NULL, NULL, 'sub-1', 'PENDIENTE', NULL, NULL, NULL, NULL)",
            )
            close()
        }

        // validateDroppedTables = true: Room compara el esquema migrado con 2.json y falla si difiere en algo.
        val db = helper.runMigrationsAndValidate(NOMBRE, 2, true, MIGRACION_1_2)

        db.query("SELECT codigo, uuid, estadoSync, motivoFallo FROM asistentes WHERE id = 340").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("DEMO-001", c.getString(0))
            assertTrue("uuid queda nulo en los asistentes que ya existían", c.isNull(1))
            assertEquals("SINCRONIZADO", c.getString(2))
            assertTrue(c.isNull(3))
        }
        db.query("SELECT estadoSync FROM asistencias WHERE uuid = 'a-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("la marcación pendiente no se toca", "PENDIENTE", c.getString(0))
        }
    }

    @Test
    fun migra1a2_permiteVariosAsistentesSinUuidPeroNoUuidRepetidos() {
        helper.createDatabase(NOMBRE, 1).close()
        val db = helper.runMigrationsAndValidate(NOMBRE, 2, true, MIGRACION_1_2)

        db.execSQL("INSERT INTO asistentes (id, codigo, tipo, estadoInscripcion, uuid, estadoSync) VALUES (1, 'A', 'VECINO', 'INSCRITO', NULL, 'SINCRONIZADO')")
        db.execSQL("INSERT INTO asistentes (id, codigo, tipo, estadoInscripcion, uuid, estadoSync) VALUES (2, 'B', 'VECINO', 'INSCRITO', NULL, 'SINCRONIZADO')")
        db.execSQL("INSERT INTO asistentes (id, codigo, tipo, estadoInscripcion, uuid, estadoSync) VALUES (-1, 'N1', 'VECINO', 'SIN_INSCRIPCION', 'u-1', 'PENDIENTE')")
        val repetido = runCatching {
            db.execSQL("INSERT INTO asistentes (id, codigo, tipo, estadoInscripcion, uuid, estadoSync) VALUES (-2, 'N2', 'VECINO', 'SIN_INSCRIPCION', 'u-1', 'PENDIENTE')")
        }
        assertTrue("un uuid repetido debe violar el índice único", repetido.isFailure)
    }

    private companion object {
        const val NOMBRE = "migracion-test"
    }
}
