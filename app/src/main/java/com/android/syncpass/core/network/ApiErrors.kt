package com.android.syncpass.core.network

import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

/** Traduce los fallos de red y del backend a mensajes claros, en lenguaje simple (caso §3.3). */
object ApiErrors {

    private val gson = Gson()

    fun describir(error: Throwable): String = when (error) {
        is HttpException -> describirHttp(error)
        is IOException ->
            "Sin conexión con el servidor. Lo que registres se guarda en el teléfono y se enviará después."
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Ocurrió un error inesperado."
    }

    /** true si el servidor respondió con ese código HTTP. */
    fun esHttp(error: Throwable, codigo: Int): Boolean = error is HttpException && error.code() == codigo

    /** Cuerpo de error del backend ({error, mensaje, status, detalles}), o null si no es ese formato. */
    fun cuerpo(error: HttpException): ErrorDto? = runCatching {
        error.response()?.errorBody()?.string()?.let { gson.fromJson(it, ErrorDto::class.java) }
    }.getOrNull()

    private fun describirHttp(error: HttpException): String {
        val dto = cuerpo(error)
        val mensaje = dto?.mensaje?.takeIf { it.isNotBlank() }
        return when (error.code()) {
            401 -> mensaje ?: "Credenciales inválidas o sesión vencida."
            403 -> when (dto?.error) {
                "USER_NOT_CONFIRMED" ->
                    "Tu cuenta todavía no está confirmada. Pide a un administrador que la confirme."
                "CHALLENGE_NOT_SUPPORTED" ->
                    "Tu cuenta tiene una contraseña temporal y debe cambiarse antes de usar la app."
                else -> mensaje ?: "No tienes permiso para esta acción."
            }
            429 -> "Demasiados intentos seguidos. Espera un momento e inténtalo de nuevo."
            in 500..599 -> "El servidor no está disponible en este momento. Inténtalo más tarde."
            else -> mensaje ?: "No se pudo completar la operación (código ${error.code()})."
        }
    }
}
