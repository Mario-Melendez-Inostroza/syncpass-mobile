package com.android.syncpass.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * Ejecuta varias escrituras como una sola: o quedan todas o no queda ninguna.
 *
 * Existe para poder probar con fakes sin una base real: en producción es [RoomTransaccion] y en las pruebas
 * unitarias, [EnLinea], que simplemente ejecuta el bloque.
 */
interface Transaccion {
    suspend fun <T> ejecutar(bloque: suspend () -> T): T

    /** Sin transacción real: el bloque corre tal cual. Solo para pruebas con fakes en memoria. */
    data object EnLinea : Transaccion {
        override suspend fun <T> ejecutar(bloque: suspend () -> T): T = bloque()
    }
}

class RoomTransaccion(private val db: RoomDatabase) : Transaccion {
    override suspend fun <T> ejecutar(bloque: suspend () -> T): T = db.withTransaction { bloque() }
}
