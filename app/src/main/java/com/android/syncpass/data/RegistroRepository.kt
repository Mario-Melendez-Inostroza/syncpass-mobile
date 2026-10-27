package com.android.syncpass.data

import android.database.sqlite.SQLiteConstraintException
import com.android.syncpass.core.Coordenadas
import com.android.syncpass.core.GeneradorUuid
import com.android.syncpass.core.Reloj
import com.android.syncpass.data.local.AsistenciaDao
import com.android.syncpass.data.local.AsistenciaEntity
import com.android.syncpass.data.local.CatalogoDao
import com.android.syncpass.data.local.EncuestaDao
import com.android.syncpass.data.local.EncuestaEntity

sealed interface ResultadoMarcacion {
    data class Registrada(val asistencia: AsistenciaEntity) : ResultadoMarcacion

    /** Ese asistente ya estaba marcado en este evento (una marcación por persona y evento). */
    data class YaMarcada(val asistencia: AsistenciaEntity) : ResultadoMarcacion

    data object EventoDesconocido : ResultadoMarcacion

    /** El código no está en el catálogo descargado: puede que falte sincronizar. */
    data object AsistenteDesconocido : ResultadoMarcacion
}

sealed interface ResultadoEncuesta {
    data class Registrada(val encuesta: EncuestaEntity) : ResultadoEncuesta

    data object AsistenciaDesconocida : ResultadoEncuesta

    data object YaRespondida : ResultadoEncuesta

    data class Invalida(val motivo: String) : ResultadoEncuesta
}

/**
 * Registros hechos en terreno. Todo se guarda en el teléfono primero (offline-first): nada aquí necesita red.
 * Quien llama pide después una sincronización (SyncScheduler.solicitarAhora) para que viaje cuando se pueda.
 */
class RegistroRepository(
    private val catalogoDao: CatalogoDao,
    private val asistenciaDao: AsistenciaDao,
    private val encuestaDao: EncuestaDao,
    private val reloj: Reloj,
    private val uuid: GeneradorUuid,
) {

    /**
     * @param usuarioSub `sub` de Cognito de quien registra. Se guarda con el registro para subirlo solo con
     *                   su sesión.
     * @param coordenadas ubicación referencial; null si no se pudo obtener (la marcación se guarda igual).
     */
    suspend fun marcarAsistencia(
        eventoId: Long,
        asistenteId: Long,
        usuarioSub: String,
        coordenadas: Coordenadas?,
    ): ResultadoMarcacion {
        catalogoDao.evento(eventoId) ?: return ResultadoMarcacion.EventoDesconocido
        catalogoDao.asistente(asistenteId) ?: return ResultadoMarcacion.AsistenteDesconocido
        asistenciaDao.buscar(eventoId, asistenteId)?.let { return ResultadoMarcacion.YaMarcada(it) }

        val nueva = AsistenciaEntity(
            uuid = uuid.nuevo(),
            eventoId = eventoId,
            asistenteId = asistenteId,
            marcadoEnMillis = reloj.ahoraMillis(),
            latitud = coordenadas?.latitud,
            longitud = coordenadas?.longitud,
            usuarioSub = usuarioSub,
        )
        try {
            asistenciaDao.insertar(nueva)
        } catch (e: SQLiteConstraintException) {
            // Dos toques casi simultáneos: el otro ganó. No es un error para el usuario.
            asistenciaDao.buscar(eventoId, asistenteId)?.let { return ResultadoMarcacion.YaMarcada(it) }
            throw e
        }
        return ResultadoMarcacion.Registrada(nueva)
    }

    /** Escala de 1 a 5 para satisfacción y utilidad (caso §3.2). */
    suspend fun registrarEncuesta(
        asistenciaUuid: String,
        satisfaccion: Int,
        utilidad: Int,
        comentarios: String?,
        usuarioSub: String,
    ): ResultadoEncuesta {
        if (satisfaccion !in ESCALA) return ResultadoEncuesta.Invalida("La satisfacción debe estar entre 1 y 5")
        if (utilidad !in ESCALA) return ResultadoEncuesta.Invalida("La utilidad debe estar entre 1 y 5")

        asistenciaDao.porUuid(asistenciaUuid) ?: return ResultadoEncuesta.AsistenciaDesconocida
        if (encuestaDao.porAsistencia(asistenciaUuid) != null) return ResultadoEncuesta.YaRespondida

        val nueva = EncuestaEntity(
            uuid = uuid.nuevo(),
            asistenciaUuid = asistenciaUuid,
            satisfaccion = satisfaccion,
            utilidad = utilidad,
            comentarios = comentarios?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_COMENTARIO),
            enviadaEnMillis = reloj.ahoraMillis(),
            usuarioSub = usuarioSub,
        )
        try {
            encuestaDao.insertar(nueva)
        } catch (e: SQLiteConstraintException) {
            return ResultadoEncuesta.YaRespondida
        }
        return ResultadoEncuesta.Registrada(nueva)
    }

    companion object {
        val ESCALA = 1..5

        /** Igual que el límite del backend. */
        const val MAX_COMENTARIO = 1000
    }
}
