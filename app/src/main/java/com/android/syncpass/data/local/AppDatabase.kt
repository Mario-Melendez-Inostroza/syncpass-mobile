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
 * Es la versión 1 de una app nueva, así que no hay migraciones. El esquema se exporta a app/schemas
 * (exportSchema = true) para poder escribir y probar migraciones cuando cambie.
 */
@Database(
    entities = [
        ProyectoEntity::class,
        EventoEntity::class,
        AsistenteEntity::class,
        AsistenciaEntity::class,
        EncuestaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogoDao(): CatalogoDao
    abstract fun asistenciaDao(): AsistenciaDao
    abstract fun encuestaDao(): EncuestaDao

    companion object {
        fun crear(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "syncpass.db").build()
    }
}
