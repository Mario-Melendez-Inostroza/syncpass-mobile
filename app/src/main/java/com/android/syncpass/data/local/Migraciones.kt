package com.android.syncpass.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: altas de asistentes en terreno (caso §3.1).
 *
 * Solo agrega columnas y un índice: no recrea tablas ni toca filas, así que los asistentes del catálogo y las
 * marcaciones pendientes sobreviven. Los asistentes que ya había quedan como SINCRONIZADO, que es lo que son.
 *
 * El esquema esperado está en app/schemas/.../2.json; MigrationTest (androidTest) comprueba que esta migración
 * produce exactamente ese esquema.
 */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE asistentes ADD COLUMN uuid TEXT")
        db.execSQL("ALTER TABLE asistentes ADD COLUMN estadoSync TEXT NOT NULL DEFAULT 'SINCRONIZADO'")
        db.execSQL("ALTER TABLE asistentes ADD COLUMN motivoFallo TEXT")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_asistentes_uuid ON asistentes (uuid)")
    }
}
