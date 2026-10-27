package com.android.syncpass.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Rutas relativas, sin barra inicial: API_BASE_URL termina en "/".
 *
 * Todas son `suspend` y devuelven el cuerpo. Un estado HTTP que no sea 2xx lanza HttpException, y los
 * fallos de red lanzan IOException; ApiErrors los traduce a mensajes para el usuario.
 */

/** Sin autenticación: se usa antes de tener tokens y para renovarlos. */
interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<TokensDto>

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): ApiResponse<TokensDto>
}

/** Requiere el token en la cabecera; lo añade TokenInterceptor. */
interface SesionApi {
    @POST("api/auth/logout")
    suspend fun logout(): ApiResponse<Any>
}

/** Sincronización offline-first y quórum. El token lo añade TokenInterceptor; nunca se pasa a mano. */
interface SyncPassApi {

    /** [desde] es el servidorEn de la última descarga; null descarga todo el catálogo. */
    @GET("api/sync/catalogo")
    suspend fun catalogo(@Query("desde") desde: String?): ApiResponse<CatalogoDto>

    @POST("api/sync/asistencias")
    suspend fun subirAsistencias(@Body request: SincronizarAsistenciasRequest): ApiResponse<LoteSyncDto>

    @POST("api/sync/encuestas")
    suspend fun subirEncuestas(@Body request: SincronizarEncuestasRequest): ApiResponse<LoteSyncDto>

    /** Solo COORDINADOR; otro rol recibe 403. */
    @GET("api/eventos/{id}/resumen")
    suspend fun resumenEvento(@Path("id") id: Long): ApiResponse<ResumenEventoDto>
}
