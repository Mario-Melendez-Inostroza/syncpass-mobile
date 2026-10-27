package com.android.syncpass.core.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

/** Grupos de Cognito que reconoce la app. Vienen del caso (§3.1), no de la app de ejemplo del profesor. */
object Roles {
    /** Docente / Coordinador(a) de Proyecto: ve el quórum y los indicadores. */
    const val COORDINADOR = "COORDINADOR"

    /** Estudiante Voluntario / Monitor: registra asistencia y encuestas en terreno. */
    const val MONITOR = "MONITOR"
}

data class UserInfo(
    val sub: String?,
    val email: String?,
    val nombre: String?,
    val grupos: List<String>,
) {
    val esCoordinador: Boolean get() = grupos.any { it.equals(Roles.COORDINADOR, ignoreCase = true) }
    val esMonitor: Boolean get() = grupos.any { it.equals(Roles.MONITOR, ignoreCase = true) }

    /** Solo se admite a quien pertenece a un grupo del caso. */
    val tieneRolValido: Boolean get() = esCoordinador || esMonitor
}

/**
 * Lee los claims de un JWT SIN verificar la firma.
 *
 * Es correcto para mostrar el nombre o decidir qué pantallas ver, pero NO es seguridad: el servidor valida
 * la firma de cada petición y es quien autoriza (p. ej. /api/eventos/{id}/resumen exige COORDINADOR aunque la
 * app mostrara el botón).
 */
object JwtClaims {

    fun payload(jwt: String?): JsonObject? {
        val partes = jwt?.split('.') ?: return null
        if (partes.size != 3) return null
        return try {
            val bytes = Base64.getUrlDecoder().decode(partes[1])
            JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonObject
        } catch (e: Exception) {
            null
        }
    }

    /** Usuario según el id token (el access token de Cognito no trae email ni name). */
    fun usuario(idToken: String?): UserInfo? {
        val p = payload(idToken) ?: return null
        val grupos = runCatching {
            p.getAsJsonArray("cognito:groups")?.map { it.asString } ?: emptyList()
        }.getOrDefault(emptyList())
        return UserInfo(
            sub = p.texto("sub"),
            email = p.texto("email"),
            nombre = p.texto("name"),
            grupos = grupos,
        )
    }

    /** Instante de expiración (claim exp) en milisegundos, o null si no se puede leer. */
    fun expiracionMillis(jwt: String?): Long? =
        runCatching { payload(jwt)?.get("exp")?.asLong?.times(1000) }.getOrNull()

    private fun JsonObject.texto(clave: String): String? =
        runCatching { get(clave)?.takeUnless { it.isJsonNull }?.asString }.getOrNull()
}
