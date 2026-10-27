package com.android.syncpass.core

import java.util.UUID

/** Reloj inyectable: las pruebas fijan la hora en lugar de depender del reloj real. */
fun interface Reloj {
    fun ahoraMillis(): Long

    companion object {
        val Sistema = Reloj { System.currentTimeMillis() }
    }
}

/** El uuid de cada registro lo genera el teléfono: es la clave de idempotencia frente al servidor. */
fun interface GeneradorUuid {
    fun nuevo(): String

    companion object {
        val Aleatorio = GeneradorUuid { UUID.randomUUID().toString() }
    }
}

/** Ubicación referencial de una marcación (caso §3.3). */
data class Coordenadas(val latitud: Double, val longitud: Double)

/** Fuente de ubicación. null si no hay permiso, no hay señal o tardó demasiado: nunca bloquea la marcación. */
interface UbicacionProvider {
    suspend fun actual(): Coordenadas?
}

/** Avisos visuales inmediatos (caso §3.3), p. ej. "sincronización completa". */
interface Notificador {
    fun avisar(titulo: String, texto: String)
}
