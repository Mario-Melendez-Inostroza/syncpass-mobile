package com.android.syncpass.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
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

    /** Crea una cuenta de asistente (grupo ASISTENTE, fijado por el servidor). */
    @POST("api/auth/registro-asistente")
    suspend fun registrarAsistente(@Body request: RegistroAsistenteRequest): ApiResponse<MiPerfilDto>

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): ApiResponse<TokensDto>
}

/** Requiere el token en la cabecera; lo añade TokenInterceptor. */
interface SesionApi {
    @POST("api/auth/logout")
    suspend fun logout(): ApiResponse<Any>
}

/** La cuenta del asistente. Solo el grupo ASISTENTE; el servidor responde 403 a cualquier otro. */
interface MiCuentaApi {
    @GET("api/mi/perfil")
    suspend fun perfil(): ApiResponse<MiPerfilDto>

    @GET("api/mi/talleres")
    suspend fun talleres(): ApiResponse<List<TallerDto>>

    /** 201 al inscribirse; 409 con el motivo si ya está inscrito o no hay cupos. */
    @POST("api/mi/talleres/{id}/inscripcion")
    suspend fun inscribir(@Path("id") eventoId: Long): ApiResponse<Any>

    @DELETE("api/mi/talleres/{id}/inscripcion")
    suspend fun cancelar(@Path("id") eventoId: Long): ApiResponse<Any>
}

/**
 * Mantenedores del coordinador (proyectos, eventos y asistentes): crear, editar y eliminar. Solo el grupo
 * COORDINADOR; el servidor responde 403 a cualquier otro y 409 con el motivo si no se puede eliminar.
 * Las listas de proyectos y eventos salen de [SyncPassApi.catalogo].
 */
interface GestionApi {
    @POST("api/proyectos")
    suspend fun crearProyecto(@Body request: ProyectoRequest): ApiResponse<ProyectoDto>

    @PUT("api/proyectos/{id}")
    suspend fun editarProyecto(@Path("id") id: Long, @Body request: ProyectoRequest): ApiResponse<ProyectoDto>

    @DELETE("api/proyectos/{id}")
    suspend fun eliminarProyecto(@Path("id") id: Long): ApiResponse<Any>

    @POST("api/eventos")
    suspend fun crearEvento(@Body request: EventoRequest): ApiResponse<EventoDto>

    @PUT("api/eventos/{id}")
    suspend fun editarEvento(@Path("id") id: Long, @Body request: EventoRequest): ApiResponse<EventoDto>

    @DELETE("api/eventos/{id}")
    suspend fun eliminarEvento(@Path("id") id: Long): ApiResponse<Any>

    @GET("api/asistentes")
    suspend fun asistentes(): ApiResponse<List<AsistenteFilaDto>>

    @POST("api/asistentes")
    suspend fun crearAsistentes(@Body request: AsistenteAltaRequest): ApiResponse<List<AsistenteFilaDto>>

    @PUT("api/asistentes/{id}")
    suspend fun editarAsistente(@Path("id") id: Long, @Body request: AsistenteEdicionRequest): ApiResponse<AsistenteFilaDto>

    @DELETE("api/asistentes/{id}")
    suspend fun eliminarAsistente(@Path("id") id: Long): ApiResponse<Any>
}

/** Sincronización offline-first y quórum. El token lo añade TokenInterceptor; nunca se pasa a mano. */
interface SyncPassApi {

    /** [desde] es el servidorEn de la última descarga; null descarga todo el catálogo. */
    @GET("api/sync/catalogo")
    suspend fun catalogo(@Query("desde") desde: String?): ApiResponse<CatalogoDto>

    /** Altas de asistentes llegados sin inscripción. Se suben ANTES que las asistencias que los usan. */
    @POST("api/sync/asistentes")
    suspend fun subirAsistentes(@Body request: SincronizarAsistentesRequest): ApiResponse<LoteAsistentesDto>

    @POST("api/sync/asistencias")
    suspend fun subirAsistencias(@Body request: SincronizarAsistenciasRequest): ApiResponse<LoteSyncDto>

    @POST("api/sync/encuestas")
    suspend fun subirEncuestas(@Body request: SincronizarEncuestasRequest): ApiResponse<LoteSyncDto>

    /** Solo COORDINADOR; otro rol recibe 403. */
    @GET("api/eventos/{id}/resumen")
    suspend fun resumenEvento(@Path("id") id: Long): ApiResponse<ResumenEventoDto>
}
