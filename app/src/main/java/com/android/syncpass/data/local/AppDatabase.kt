package com.android.syncpass.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun deEstado(estado: EstadoSync): String = estado.name

    @TypeConverter
    fun aEstado(valor: String): EstadoSync = EstadoSync.valueOf(valor)
}

/**
 * Base local (SQLite vía Room): catálogo descargado y registros hechos sin conexión.
 *
 * Versiones:
 *  - 1: catálogo y registros de asistencia y encuestas.
 *  - 2: asistentes dados de alta en terreno (columnas uuid, estadoSync y motivoFallo en `asistentes`).
 *
 * El esquema se exporta a app/schemas (exportSchema = true) y cada cambio lleva su migración en
 * [MIGRACION_1_2] y siguientes: sin ella Room se niega a abrir una base instalada con la versión anterior.
 */
@Database(
    entities = [
        ProyectoEntity::class,
        EventoEntity::class,
        AsistenteEntity::class,
        AsistenciaEntity::class,
        EncuestaEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogoDao(): CatalogoDao
    abstract fun asistenciaDao(): AsistenciaDao
    abstract fun encuestaDao(): EncuestaDao

    companion object {
        fun crear(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "syncpass.db")
                .addMigrations(MIGRACION_1_2)
                .build()
    }
}
