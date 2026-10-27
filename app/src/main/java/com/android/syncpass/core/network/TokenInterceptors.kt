package com.android.syncpass.core.network

import com.android.syncpass.core.auth.TokenStorage
import com.android.syncpass.core.auth.Tokens
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import java.io.IOException

/** Rutas que se llaman SIN token: iniciar sesión, renovarlo y registrarse. */
internal object RutasPublicas {
    fun esPublica(ruta: String): Boolean =
        ruta.endsWith("/api/auth/login") ||
            ruta.endsWith("/api/auth/refresh") ||
            ruta.endsWith("/api/auth/registro")
}

/**
 * Añade `Authorization: Bearer <accessToken>` a cada petición. Ningún código de la app pasa el token a mano.
 *
 * La lectura es bloqueante porque Interceptor.intercept no es suspend; DataStore mantiene el valor en
 * memoria, así que solo la primera lectura toca el disco.
 */
class TokenInterceptor(private val storage: TokenStorage) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (RutasPublicas.esPublica(request.url.encodedPath)) return chain.proceed(request)

        val token = runBlocking { storage.tokens()?.accessToken }
        if (token.isNullOrBlank()) return chain.proceed(request)

        return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
    }
}

/**
 * Ante un 401 renueva el token con POST /api/auth/refresh y repite la petición original una vez.
 *
 * - Dos hilos con 401 a la vez no renuevan dos veces: el segundo comprueba si el token ya cambió.
 * - Si el servidor RECHAZA el refresh token (400, 401 o 403) la sesión terminó: se borran los tokens y la
 *   interfaz vuelve al login.
 * - Si no hay red o el servidor falla (5xx) se CONSERVA la sesión: el usuario no debe perderla por estar sin
 *   cobertura, que es justo el escenario del caso.
 *
 * [refreshApi] debe ser una API SIN este autenticador, o un 401 en el refresh provocaría un bucle.
 */
class TokenAuthenticator(
    private val storage: TokenStorage,
    private val refreshApi: AuthApi,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (RutasPublicas.esPublica(response.request.url.encodedPath)) return null
        // Un segundo 401 tras haber renovado significa que el problema no es el token caducado.
        if (generateSequence(response) { it.priorResponse }.count() >= 2) return null

        return synchronized(lock) { renovar(response.request) }
    }

    private fun renovar(original: Request): Request? {
        val usado = original.header("Authorization")?.removePrefix("Bearer ")
        val actuales = runBlocking { storage.tokens() } ?: return null

        // Otro hilo ya renovó mientras éste esperaba el bloqueo: basta con repetir con el token nuevo.
        if (actuales.accessToken != usado) return conToken(original, actuales.accessToken)

        val refresh = actuales.refreshToken
        if (refresh.isNullOrBlank()) {
            runBlocking { storage.limpiar() }
            return null
        }

        val email = runBlocking { storage.email() }
        val respuesta = try {
            runBlocking { refreshApi.refresh(RefreshRequest(refresh, email)) }
        } catch (e: HttpException) {
            if (e.code() in CODIGOS_REFRESH_RECHAZADO) runBlocking { storage.limpiar() }
            return null
        } catch (e: IOException) {
            return null
        }

        val nuevoAcceso = respuesta.data?.accessToken
        if (!respuesta.success || nuevoAcceso.isNullOrBlank()) {
            runBlocking { storage.limpiar() }
            return null
        }

        runBlocking {
            storage.guardar(
                Tokens(
                    accessToken = nuevoAcceso,
                    idToken = respuesta.data?.idToken ?: actuales.idToken,
                    // Cognito no rota el refresh token en este flujo; el backend reenvía el que recibió.
                    refreshToken = respuesta.data?.refreshToken ?: refresh,
                ),
                email,
            )
        }
        return conToken(original, nuevoAcceso)
    }

    private fun conToken(request: Request, token: String): Request =
        request.newBuilder().header("Authorization", "Bearer $token").build()

    private companion object {
        val CODIGOS_REFRESH_RECHAZADO = setOf(400, 401, 403)
    }
}
