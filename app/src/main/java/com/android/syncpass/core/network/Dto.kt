package com.android.syncpass.core.network

/**
 * Contrato con el backend (fs2-api). Los nombres de campo deben coincidir EXACTAMENTE con el JSON del
 * servidor: Gson enlaza por nombre de propiedad y aquí no se usa @SerializedName.
 *
 * Los campos de respuesta son nulables y con valor por defecto a propósito: Gson crea las instancias sin
 * pasar por el constructor, y un campo ausente en el JSON quedaría null aunque el tipo diga lo contrario.
 * Que sean nulables obliga a decidir qué hacer en ese caso.
 */

/** Envoltorio de todas las respuestas correctas: {success, data, message, timestamp}. */
data class ApiResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    val message: String? = null,
)

/** Cuerpo de los errores del backend: {error, mensaje, status, detalles}. */
data class ErrorDto(
    val error: String? = null,
    val mensaje: String? = null,
    val status: Int? = null,
    val detalles: Map<String, String>? = null,
)

// ------------------------------------------------------------------ autenticación

data class LoginRequest(val email: String, val password: String)

/** [email] solo es obligatorio si el App Client de Cognito tiene client secret; con client público se ignora. */
data class RefreshRequest(val refreshToken: String, val email: String? = null)

data class TokensDto(
    val accessToken: String? = null,
    val idToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Int? = null,
)

// ------------------------------------------------------------------ catálogo

data class CatalogoDto(
    val servidorEn: String? = null,
    val proyectos: List<ProyectoDto>? = null,
    val eventos: List<EventoDto>? = null,
    val asistentes: List<AsistenteDto>? = null,
)

data class ProyectoDto(
    val id: Long? = null,
    val titulo: String? = null,
    val descripcion: String? = null,
    val areaTematica: String? = null,
    val unidadVecinal: String? = null,
    val fechaInicio: String? = null,
    val fechaTermino: String? = null,
    val estado: String? = null,
    val actualizadoEn: String? = null,
)

data class EventoDto(
    val id: Long? = null,
    val proyectoId: Long? = null,
    val nombre: String? = null,
    val fechaHoraProgramada: String? = null,
    val cuposMaximos: Int? = null,
    val recinto: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val radioMetros: Int? = null,
    val actualizadoEn: String? = null,
)

/** Sin datos personales: solo el código sintético y el tipo de participante (caso §3.2 y §5.1). */
data class AsistenteDto(
    val id: Long? = null,
    val codigo: String? = null,
    val tipo: String? = null,
    val estadoInscripcion: String? = null,
    val actualizadoEn: String? = null,
)

// ------------------------------------------------------------------ sincronización por lotes

/** Una marcación pendiente. Lo que se envía; no hay campo del usuario que registra: sale del token. */
data class AsistenciaSyncDto(
    val uuid: String,
    val eventoId: Long,
    val asistenteId: Long,
    /** ISO-8601 en UTC, hora del reloj del dispositivo en el momento de marcar. */
    val marcadoEn: String,
    val latitud: Double? = null,
    val longitud: Double? = null,
)

data class SincronizarAsistenciasRequest(val registros: List<AsistenciaSyncDto>)

/**
 * Un asistente dado de alta en terreno (caso §3.1). Solo el uuid del teléfono y el tipo: el código sintético y la
 * condición de "sin inscripción" los decide el servidor. Sin datos personales (caso §5.1).
 */
data class AsistenteSyncDto(
    val uuid: String,
    /** ESTUDIANTE, VECINO o DOCENTE. */
    val tipo: String,
)

data class SincronizarAsistentesRequest(val registros: List<AsistenteSyncDto>)

data class LoteAsistentesDto(
    val aceptados: Int? = null,
    val duplicados: Int? = null,
    val rechazados: Int? = null,
    val resultados: List<ResultadoAsistenteDto>? = null,
)

/** A diferencia de [ResultadoSyncDto], trae el id y el código REALES que el servidor asignó. */
data class ResultadoAsistenteDto(
    val indice: Int? = null,
    val uuid: String? = null,
    /** ACEPTADO, DUPLICADO o RECHAZADO. */
    val estado: String? = null,
    val motivo: String? = null,
    val id: Long? = null,
    val codigo: String? = null,
    val detalle: String? = null,
)

data class EncuestaSyncDto(
    val uuid: String,
    val asistenciaUuid: String,
    val satisfaccion: Int,
    val utilidad: Int,
    val comentarios: String? = null,
    val enviadaEn: String,
)

data class SincronizarEncuestasRequest(val registros: List<EncuestaSyncDto>)

data class LoteSyncDto(
    val aceptados: Int? = null,
    val duplicados: Int? = null,
    val rechazados: Int? = null,
    val resultados: List<ResultadoSyncDto>? = null,
)

data class ResultadoSyncDto(
    val indice: Int? = null,
    val uuid: String? = null,
    /** ACEPTADO, DUPLICADO o RECHAZADO. */
    val estado: String? = null,
    val motivo: String? = null,
    val id: Long? = null,
    val estadoValidacion: String? = null,
    val distanciaMetros: Int? = null,
    val detalle: String? = null,
)

// ------------------------------------------------------------------ quórum

data class ResumenEventoDto(
    val eventoId: Long? = null,
    val nombre: String? = null,
    val cuposMaximos: Int? = null,
    val totalMarcados: Long? = null,
    val validas: Long? = null,
    val fueraDeRadio: Long? = null,
    val sinUbicacion: Long? = null,
    val encuestasRecibidas: Long? = null,
    val satisfaccionPromedio: Double? = null,
    val utilidadPromedio: Double? = null,
    val primeraMarcacion: String? = null,
    val ultimaMarcacion: String? = null,
)
