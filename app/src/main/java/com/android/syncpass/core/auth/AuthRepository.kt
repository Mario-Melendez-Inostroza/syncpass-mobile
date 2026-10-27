package com.android.syncpass.core.auth

import com.android.syncpass.core.network.ApiErrors
import com.android.syncpass.core.network.AuthApi
import com.android.syncpass.core.network.LoginRequest
import com.android.syncpass.core.network.RegistroAsistenteRequest
import com.android.syncpass.core.network.SesionApi
import kotlinx.coroutines.flow.Flow

/** Error que se muestra tal cual en pantalla. */
class AuthException(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)

class AuthRepository(
    private val authApi: AuthApi,
    private val sesionApi: SesionApi,
    private val storage: TokenStorage,
) {

    /** true mientras haya una sesión; la interfaz vuelve al login cuando pasa a false. */
    val sesionActiva: Flow<Boolean> get() = storage.sesionActiva()

    /**
     * Inicia sesión contra el backend (que a su vez habla con Cognito; la app nunca contacta a Cognito).
     *
     * Solo se admite a quien pertenece a un grupo del caso (COORDINADOR o MONITOR). Un usuario sin grupo se
     * rechaza con una explicación en lugar de dejarlo entrar con un rol inventado.
     */
    suspend fun iniciarSesion(email: String, password: String): Result<UserInfo> {
        val correo = email.trim()
        val respuesta = try {
            authApi.login(LoginRequest(correo, password))
        } catch (e: Exception) {
            return Result.failure(AuthException(ApiErrors.describir(e), e))
        }

        val datos = respuesta.data
        val acceso = datos?.accessToken
        val identidad = datos?.idToken
        if (!respuesta.success || acceso.isNullOrBlank() || identidad.isNullOrBlank()) {
            return Result.failure(AuthException(respuesta.message ?: "La respuesta del servidor está incompleta."))
        }

        val usuario = JwtClaims.usuario(identidad)
        if (usuario == null || !usuario.tieneRolValido) {
            return Result.failure(
                AuthException(
                    "Tu usuario no tiene un rol asignado (COORDINADOR, MONITOR o ASISTENTE). " +
                        "Pide a un administrador que lo agregue a un grupo.",
                ),
            )
        }

        storage.guardar(Tokens(acceso, identidad, datos?.refreshToken), correo)
        return Result.success(usuario)
    }

    /**
     * Crea una cuenta de asistente y entra con ella. El servidor la deja confirmada y en el grupo ASISTENTE; no guarda
     * nombre ni correo en su base, solo un código sintético (caso §5.1).
     */
    suspend fun registrarAsistente(email: String, password: String, tipo: String): Result<UserInfo> {
        val correo = email.trim()
        try {
            val respuesta = authApi.registrarAsistente(RegistroAsistenteRequest(correo, password, tipo))
            if (!respuesta.success) return Result.failure(AuthException(respuesta.message ?: "No se pudo crear la cuenta."))
        } catch (e: Exception) {
            val mensaje = (e as? retrofit2.HttpException)?.let { ApiErrors.cuerpo(it)?.mensaje } ?: ApiErrors.describir(e)
            return Result.failure(AuthException(mensaje, e))
        }
        return iniciarSesion(correo, password)
    }

    /** Usuario de la sesión guardada, o null si no hay sesión (o su token ya no se puede leer). */
    suspend fun usuarioActual(): UserInfo? =
        storage.tokens()?.idToken?.let(JwtClaims::usuario)?.takeIf { it.tieneRolValido }

    /**
     * Cierra la sesión: avisa al servidor (para revocar el refresh token) y borra los tokens.
     * Si no hay red, igualmente se cierra en el teléfono. NO borra los registros pendientes de sincronizar.
     */
    suspend fun cerrarSesion() {
        runCatching { sesionApi.logout() }
        storage.limpiar()
    }
}
