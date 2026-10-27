package com.android.syncpass.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Estado de un registro que debe llegar al servidor (caso §3.4: "banderas de sincronización").
 *
 *  - PENDIENTE: guardado en el teléfono, todavía sin confirmar por el servidor.
 *  - SINCRONIZADO: el servidor ya lo tiene (lo aceptó, o ya existía).
 *  - FALLIDO: el servidor lo rechazó y reintentarlo igual no sirve; se muestra con su motivo.
 */
enum class EstadoSync { PENDIENTE, SINCRONIZADO, FALLIDO }

// ------------------------------------------------------------------ catálogo (viene del servidor)
//
// Los id son los del servidor. No hay claves foráneas entre estas tablas ni hacia las asistencias: el
// catálogo se actualiza con @Upsert, y una relación con borrado en cascada podría arrastrar registros ya
// hechos en terreno que aún no se enviaron.

@Entity(tableName = "proyectos")
data class ProyectoEntity(
    @PrimaryKey val id: Long,
    val titulo: String,
    val descripcion: String?,
    val areaTematica: String,
    val unidadVecinal: String,
    val fechaInicio: String,
    val fechaTermino: String?,
    val estado: String,
)

@Entity(tableName = "eventos", indices = [Index("proyectoId")])
data class EventoEntity(
    @PrimaryKey val id: Long,
    val proyectoId: Long,
    val nombre: String,
    /** ISO-8601 en UTC. */
    val fechaHoraProgramada: String,
    val cuposMaximos: Int?,
    val recinto: String?,
    val latitud: Double?,
    val longitud: Double?,
    val radioMetros: Int,
)

/**
 * Sin datos personales: código sintético y tipo (caso §3.2 y §5.1).
 *
 * Los asistentes del catálogo traen el id del servidor (positivo). El que da de alta el monitor en terreno
 * (caso §3.1: "anota a quienes llegan sin inscripción previa") nace aquí, sin conexión, con un id PROVISIONAL
 * NEGATIVO y su propio [uuid]. Al sincronizar, el servidor responde con el id y el código reales y esta fila se
 * reemplaza por la definitiva (SyncEngine). Un id negativo significa "todavía no existe en el servidor".
 */
@Entity(
    tableName = "asistentes",
    indices = [Index(value = ["codigo"], unique = true), Index(value = ["uuid"], unique = true)],
)
data class AsistenteEntity(
    @PrimaryKey val id: Long,
    val codigo: String,
    val tipo: String,
    val estadoInscripcion: String,
    /** Clave de idempotencia del alta en terreno. Nulo en los que vienen del catálogo. */
    val uuid: String? = null,
    /** SINCRONIZADO para los del servidor; PENDIENTE mientras un alta en terreno espera subir. */
    val estadoSync: EstadoSync = EstadoSync.SINCRONIZADO,
    val motivoFallo: String? = null,
) {
    /** true mientras el asistente solo existe en este teléfono. */
    val esProvisional: Boolean get() = id < 0
}

// ------------------------------------------------------------------ registros hechos en terreno

/**
 * Una marcación de asistencia. La clave es el [uuid] que genera el teléfono: es lo que permite reenviar el
 * mismo registro sin duplicarlo en el servidor.
 *
 * El índice único (eventoId, asistenteId) refleja la regla del servidor (una marcación por asistente y evento),
 * así que la app avisa de inmediato que ya está marcado en lugar de descubrirlo al sincronizar.
 */
@Entity(
    tableName = "asistencias",
    indices = [
        Index(value = ["eventoId", "asistenteId"], unique = true),
        Index("estadoSync"),
        Index("usuarioSub"),
    ],
)
data class AsistenciaEntity(
    @PrimaryKey val uuid: String,
    val eventoId: Long,
    val asistenteId: Long,
    /** Reloj del dispositivo al marcar (caso §3.2: marca de tiempo exacta). */
    val marcadoEnMillis: Long,
    /** Ubicación referencial (caso §3.3). Nula si no se pudo obtener; nunca bloquea la marcación. */
    val latitud: Double?,
    val longitud: Double?,
    /** `sub` de Cognito del usuario que registró. Solo se sincroniza con la sesión de ese mismo usuario. */
    val usuarioSub: String,
    val estadoSync: EstadoSync = EstadoSync.PENDIENTE,
    val motivoFallo: String? = null,
    /** Id de la fila en el servidor, cuando ya se sincronizó. */
    val servidorId: Long? = null,
    /** Lo calcula el servidor: VALIDA, FUERA_DE_RADIO o SIN_UBICACION. */
    val estadoValidacion: String? = null,
    val distanciaMetros: Int? = null,
)

/** Encuesta de valoración de una asistencia (caso §3.2): satisfacción y utilidad de 1 a 5, y comentarios. */
@Entity(
    tableName = "encuestas",
    indices = [
        Index(value = ["asistenciaUuid"], unique = true),
        Index("estadoSync"),
        Index("usuarioSub"),
    ],
)
data class EncuestaEntity(
    @PrimaryKey val uuid: String,
    val asistenciaUuid: String,
    val satisfaccion: Int,
    val utilidad: Int,
    val comentarios: String?,
    val enviadaEnMillis: Long,
    val usuarioSub: String,
    val estadoSync: EstadoSync = EstadoSync.PENDIENTE,
    val motivoFallo: String? = null,
    val servidorId: Long? = null,
)

// ------------------------------------------------------------------ consultas con uniones

data class EventoConProyecto(
    @Embedded val evento: EventoEntity,
    val proyectoTitulo: String?,
)

data class AsistenciaConAsistente(
    @Embedded val asistencia: AsistenciaEntity,
    val asistenteCodigo: String,
    /** true si esa asistencia ya tiene encuesta (para ofrecer "Responder encuesta" solo cuando falta). */
    val tieneEncuesta: Boolean,
)
