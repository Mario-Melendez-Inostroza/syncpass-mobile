package com.android.syncpass.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Catálogo descargado del servidor. @Upsert (insertar o actualizar en el sitio) y NO REPLACE: REPLACE borra
 * la fila y la inserta de nuevo, y con relaciones en cascada se llevaría por delante registros ya hechos.
 */
@Dao
interface CatalogoDao {
    @Upsert suspend fun upsertProyectos(proyectos: List<ProyectoEntity>)

    @Upsert suspend fun upsertEventos(eventos: List<EventoEntity>)

    @Upsert suspend fun upsertAsistentes(asistentes: List<AsistenteEntity>)

    @Query(
        "SELECT e.*, p.titulo AS proyectoTitulo FROM eventos e " +
            "LEFT JOIN proyectos p ON p.id = e.proyectoId ORDER BY e.fechaHoraProgramada",
    )
    fun observarEventos(): Flow<List<EventoConProyecto>>

    @Query("SELECT * FROM eventos WHERE id = :id")
    fun observarEvento(id: Long): Flow<EventoEntity?>

    @Query("SELECT * FROM eventos WHERE id = :id")
    suspend fun evento(id: Long): EventoEntity?

    @Query("SELECT * FROM asistentes ORDER BY codigo")
    fun observarAsistentes(): Flow<List<AsistenteEntity>>

    @Query("SELECT * FROM asistentes WHERE id = :id")
    suspend fun asistente(id: Long): AsistenteEntity?

    // --- altas en terreno (asistentes con id provisional negativo) ---

    /** Falla (SQLiteConstraintException) si el código o el uuid ya existen. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarAsistente(asistente: AsistenteEntity)

    /** El próximo id provisional: -1 si no hay ninguno, y uno menos que el menor existente en otro caso. */
    @Query("SELECT COALESCE(MIN(id), 0) - 1 FROM asistentes WHERE id < 0")
    suspend fun siguienteIdProvisional(): Long

    /** Las altas que esperan subir, en el orden en que se hicieron (el primer provisional es -1). */
    @Query("SELECT * FROM asistentes WHERE id < 0 AND estadoSync = 'PENDIENTE' ORDER BY id DESC")
    suspend fun asistentesPendientes(): List<AsistenteEntity>

    @Query("UPDATE asistentes SET estadoSync = 'FALLIDO', motivoFallo = :motivo WHERE id = :id")
    suspend fun marcarAsistenteFallido(id: Long, motivo: String)

    @Query("DELETE FROM asistentes WHERE id = :id")
    suspend fun eliminarAsistente(id: Long)
}

@Dao
interface AsistenciaDao {

    /** Falla (SQLiteConstraintException) si ese asistente ya está marcado en ese evento. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(asistencia: AsistenciaEntity)

    @Query("SELECT * FROM asistencias WHERE eventoId = :eventoId AND asistenteId = :asistenteId LIMIT 1")
    suspend fun buscar(eventoId: Long, asistenteId: Long): AsistenciaEntity?

    @Query("SELECT * FROM asistencias WHERE uuid = :uuid")
    suspend fun porUuid(uuid: String): AsistenciaEntity?

    @Query(
        "SELECT a.*, s.codigo AS asistenteCodigo, " +
            "EXISTS(SELECT 1 FROM encuestas e WHERE e.asistenciaUuid = a.uuid) AS tieneEncuesta FROM asistencias a " +
            "JOIN asistentes s ON s.id = a.asistenteId " +
            "WHERE a.eventoId = :eventoId ORDER BY a.marcadoEnMillis DESC",
    )
    fun observarPorEvento(eventoId: Long): Flow<List<AsistenciaConAsistente>>

    /**
     * Solo los del usuario indicado, en el orden en que se marcaron. Se excluyen las de un asistente todavía
     * provisional (id negativo): el servidor no lo conoce, y subirlas daría ASISTENTE_NO_ENCONTRADO. Esperan a que
     * su alta se confirme y [reasignarAsistente] les ponga el id real.
     */
    @Query(
        "SELECT * FROM asistencias WHERE estadoSync = 'PENDIENTE' AND usuarioSub = :sub AND asistenteId > 0 " +
            "ORDER BY marcadoEnMillis",
    )
    suspend fun pendientes(sub: String): List<AsistenciaEntity>

    /** Cambia el id provisional de un asistente por el real en todas sus asistencias. */
    @Query("UPDATE asistencias SET asistenteId = :real WHERE asistenteId = :provisional")
    suspend fun reasignarAsistente(provisional: Long, real: Long)

    @Query(
        "UPDATE asistencias SET estadoSync = 'SINCRONIZADO', motivoFallo = NULL, servidorId = :servidorId, " +
            "estadoValidacion = :estadoValidacion, distanciaMetros = :distanciaMetros WHERE uuid = :uuid",
    )
    suspend fun marcarSincronizada(uuid: String, servidorId: Long?, estadoValidacion: String?, distanciaMetros: Int?)

    @Query("UPDATE asistencias SET estadoSync = 'FALLIDO', motivoFallo = :motivo WHERE uuid = :uuid")
    suspend fun marcarFallida(uuid: String, motivo: String)

    @Query("UPDATE asistencias SET estadoSync = 'PENDIENTE', motivoFallo = NULL WHERE uuid = :uuid AND estadoSync = 'FALLIDO'")
    suspend fun reintentar(uuid: String)

    /**
     * Tras bajar un catálogo con novedades: lo que falló porque el evento o el asistente no existían puede
     * resolverse ahora. Devuelve cuántas filas volvieron a PENDIENTE.
     */
    @Query(
        "UPDATE asistencias SET estadoSync = 'PENDIENTE', motivoFallo = NULL WHERE estadoSync = 'FALLIDO' " +
            "AND motivoFallo IN ('EVENTO_NO_ENCONTRADO', 'ASISTENTE_NO_ENCONTRADO')",
    )
    suspend fun reintentarPorReferencias(): Int

    @Query("SELECT COUNT(*) FROM asistencias WHERE estadoSync = :estado AND usuarioSub = :sub")
    fun contar(estado: EstadoSync, sub: String): Flow<Int>

    @Query("SELECT * FROM asistencias WHERE estadoSync = 'FALLIDO' AND usuarioSub = :sub ORDER BY marcadoEnMillis")
    fun observarFallidas(sub: String): Flow<List<AsistenciaEntity>>
}

@Dao
interface EncuestaDao {

    /** Falla si esa asistencia ya tiene encuesta. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(encuesta: EncuestaEntity)

    @Query("SELECT * FROM encuestas WHERE asistenciaUuid = :asistenciaUuid LIMIT 1")
    suspend fun porAsistencia(asistenciaUuid: String): EncuestaEntity?

    @Query("SELECT * FROM encuestas WHERE estadoSync = 'PENDIENTE' AND usuarioSub = :sub ORDER BY enviadaEnMillis")
    suspend fun pendientes(sub: String): List<EncuestaEntity>

    @Query(
        "UPDATE encuestas SET estadoSync = 'SINCRONIZADO', motivoFallo = NULL, servidorId = :servidorId WHERE uuid = :uuid",
    )
    suspend fun marcarSincronizada(uuid: String, servidorId: Long?)

    @Query("UPDATE encuestas SET estadoSync = 'FALLIDO', motivoFallo = :motivo WHERE uuid = :uuid")
    suspend fun marcarFallida(uuid: String, motivo: String)

    @Query("UPDATE encuestas SET estadoSync = 'PENDIENTE', motivoFallo = NULL WHERE uuid = :uuid AND estadoSync = 'FALLIDO'")
    suspend fun reintentar(uuid: String)

    @Query("SELECT COUNT(*) FROM encuestas WHERE estadoSync = :estado AND usuarioSub = :sub")
    fun contar(estado: EstadoSync, sub: String): Flow<Int>

    @Query("SELECT * FROM encuestas WHERE estadoSync = 'FALLIDO' AND usuarioSub = :sub ORDER BY enviadaEnMillis")
    fun observarFallidas(sub: String): Flow<List<EncuestaEntity>>
}
