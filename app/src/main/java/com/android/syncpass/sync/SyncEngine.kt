package com.android.syncpass.sync

import com.android.syncpass.core.Reloj
import com.android.syncpass.core.network.ResultadoSyncDto
import com.android.syncpass.core.network.SincronizarAsistenciasRequest
import com.android.syncpass.core.network.SincronizarEncuestasRequest
import com.android.syncpass.core.network.SyncPassApi
import com.android.syncpass.data.aDto
import com.android.syncpass.data.aEntidad
import com.android.syncpass.data.local.AsistenciaDao
import com.android.syncpass.data.local.AsistenciaEntity
import com.android.syncpass.data.local.CatalogoDao
import com.android.syncpass.data.local.EncuestaDao
import com.android.syncpass.data.local.EncuestaEntity
import com.android.syncpass.data.local.EstadoSync
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException

/** Cuántos registros de cada tipo se mandan por petición. El servidor admite hasta 500. */
const val TAMANO_LOTE = 100

data class ResumenCatalogo(val proyectos: Int, val eventos: Int, val asistentes: Int) {
    val hayNovedades: Boolean get() = proyectos + eventos + asistentes > 0
}

/**
 * @property sincronizados registros que el servidor ya tiene (lo aceptó, o ya existía).
 * @property fallidos rechazados definitivamente; quedan visibles con su motivo.
 * @property pendientes siguen esperando (p. ej. una encuesta cuya asistencia aún no llega).
 */
data class ResumenLote(val sincronizados: Int, val fallidos: Int, val pendientes: Int)

data class ResumenSync(val catalogo: ResumenCatalogo, val asistencias: ResumenLote, val encuestas: ResumenLote) {
    val totalSincronizados: Int get() = asistencias.sincronizados + encuestas.sincronizados
}

sealed interface ResultadoSync {
    data class Completa(val resumen: ResumenSync) : ResultadoSync

    /** No hay sesión (nunca inició o el servidor rechazó la renovación). No se reintenta hasta el próximo login. */
    data object SinSesion : ResultadoSync

    /** Sin conexión o sin alcanzar el servidor. Se reintenta cuando vuelva la red. */
    data class ErrorRed(val causa: Throwable) : ResultadoSync

    /** El servidor respondió con un error o con algo que la app no entiende. */
    data class ErrorServidor(val codigo: Int?) : ResultadoSync
}

private class RespuestaInvalidaException : Exception("El servidor respondió con un formato inesperado")

/**
 * Sincronización bidireccional (caso §3.4): baja el catálogo y sube, en lotes, las asistencias y encuestas
 * acumuladas sin conexión.
 *
 * Principios:
 *  - IDEMPOTENTE: cada registro lleva el uuid generado en el teléfono, así que reenviar es seguro. Si la
 *    conexión se corta a mitad, lo no confirmado sigue PENDIENTE y se reenvía; el servidor responde
 *    DUPLICADO a lo que ya recibió.
 *  - ACEPTADO y DUPLICADO significan lo mismo para la app: el servidor ya lo tiene.
 *  - Un registro rechazado no frena al resto del lote.
 *  - Solo se envían los registros del usuario con sesión: el servidor atribuye lo que recibe al dueño del
 *    token, y subir lo de otro usuario falsearía quién registró.
 *  - Las asistencias se suben ANTES que las encuestas, porque cada encuesta cuelga de una asistencia.
 *  - Nunca hay dos sincronizaciones a la vez.
 */
class SyncEngine(
    private val api: SyncPassApi,
    private val catalogoDao: CatalogoDao,
    private val asistenciaDao: AsistenciaDao,
    private val encuestaDao: EncuestaDao,
    private val prefs: SyncPrefs,
    private val subActual: suspend () -> String?,
    private val reloj: Reloj,
) {
    private val candado = Mutex()

    suspend fun sincronizar(): ResultadoSync = candado.withLock { ejecutar() }

    private suspend fun ejecutar(): ResultadoSync {
        val sub = subActual() ?: return ResultadoSync.SinSesion
        return try {
            val catalogo = bajarCatalogo()
            val asistencias = subirAsistencias(sub)
            val encuestas = subirEncuestas(sub)
            prefs.registrarSincronizacion(reloj.ahoraMillis())
            ResultadoSync.Completa(ResumenSync(catalogo, asistencias, encuestas))
        } catch (e: HttpException) {
            if (e.code() == 401) {
                // Si la sesión sigue ahí, el 401 vino de no poder renovar el token por falta de red.
                if (subActual() == null) ResultadoSync.SinSesion else ResultadoSync.ErrorRed(e)
            } else {
                ResultadoSync.ErrorServidor(e.code())
            }
        } catch (e: IOException) {
            ResultadoSync.ErrorRed(e)
        } catch (e: RespuestaInvalidaException) {
            ResultadoSync.ErrorServidor(null)
        }
    }

    // ------------------------------------------------------------------ catálogo

    private suspend fun bajarCatalogo(): ResumenCatalogo {
        val respuesta = api.catalogo(prefs.ultimoCatalogoEn())
        val datos = respuesta.data
        if (!respuesta.success || datos == null) throw RespuestaInvalidaException()

        // Una fila que llegue incompleta se descarta en vez de romper toda la descarga.
        val proyectos = datos.proyectos.orEmpty().mapNotNull { it.aEntidad() }
        val eventos = datos.eventos.orEmpty().mapNotNull { it.aEntidad() }
        val asistentes = datos.asistentes.orEmpty().mapNotNull { it.aEntidad() }

        catalogoDao.upsertProyectos(proyectos)
        catalogoDao.upsertEventos(eventos)
        catalogoDao.upsertAsistentes(asistentes)
        // La marca se guarda DESPUÉS de escribir: si algo falla antes, la próxima vez se vuelve a pedir.
        datos.servidorEn?.let { prefs.guardarCatalogoEn(it) }

        if (eventos.isNotEmpty() || asistentes.isNotEmpty()) {
            // Lo que falló por un evento o asistente desconocido puede resolverse con el catálogo nuevo.
            asistenciaDao.reintentarPorReferencias()
        }
        return ResumenCatalogo(proyectos.size, eventos.size, asistentes.size)
    }

    // ------------------------------------------------------------------ asistencias

    private suspend fun subirAsistencias(sub: String): ResumenLote {
        var sincronizados = 0
        var fallidos = 0
        val pendientes = asistenciaDao.pendientes(sub)

        for (lote in pendientes.chunked(TAMANO_LOTE)) {
            val respuesta = api.subirAsistencias(SincronizarAsistenciasRequest(lote.map(AsistenciaEntity::aDto)))
            val resultados = respuesta.data?.resultados
            if (!respuesta.success || resultados == null) throw RespuestaInvalidaException()

            for (resultado in resultados) {
                val registro = encontrar(lote, resultado, AsistenciaEntity::uuid) ?: continue
                when (resultado.estado) {
                    ESTADO_ACEPTADO, ESTADO_DUPLICADO -> {
                        asistenciaDao.marcarSincronizada(
                            registro.uuid,
                            resultado.id,
                            resultado.estadoValidacion,
                            resultado.distanciaMetros,
                        )
                        sincronizados++
                    }
                    ESTADO_RECHAZADO -> {
                        asistenciaDao.marcarFallida(registro.uuid, motivoDe(resultado))
                        fallidos++
                    }
                    // Un estado que la app no conoce: se deja PENDIENTE y se reintenta en el próximo ciclo.
                }
            }
        }
        return ResumenLote(sincronizados, fallidos, pendientes = pendientes.size - sincronizados - fallidos)
    }

    // ------------------------------------------------------------------ encuestas

    private suspend fun subirEncuestas(sub: String): ResumenLote {
        var sincronizados = 0
        var fallidos = 0
        val pendientes = encuestaDao.pendientes(sub)

        for (lote in pendientes.chunked(TAMANO_LOTE)) {
            val respuesta = api.subirEncuestas(SincronizarEncuestasRequest(lote.map(EncuestaEntity::aDto)))
            val resultados = respuesta.data?.resultados
            if (!respuesta.success || resultados == null) throw RespuestaInvalidaException()

            for (resultado in resultados) {
                val registro = encontrar(lote, resultado, EncuestaEntity::uuid) ?: continue
                when (resultado.estado) {
                    ESTADO_ACEPTADO, ESTADO_DUPLICADO -> {
                        encuestaDao.marcarSincronizada(registro.uuid, resultado.id)
                        sincronizados++
                    }
                    ESTADO_RECHAZADO -> {
                        if (resultado.motivo == MOTIVO_ASISTENCIA_NO_ENCONTRADA && asistenciaAunPuedeLlegar(registro)) {
                            // Transitorio: la encuesta llegó antes que su asistencia. Se queda PENDIENTE.
                            continue
                        }
                        encuestaDao.marcarFallida(registro.uuid, motivoDe(resultado))
                        fallidos++
                    }
                }
            }
        }
        return ResumenLote(sincronizados, fallidos, pendientes = pendientes.size - sincronizados - fallidos)
    }

    /** Si la asistencia ya está marcada como FALLIDA nunca llegará, y esperar no sirve de nada. */
    private suspend fun asistenciaAunPuedeLlegar(encuesta: EncuestaEntity): Boolean =
        asistenciaDao.porUuid(encuesta.asistenciaUuid)?.estadoSync != EstadoSync.FALLIDO

    // ------------------------------------------------------------------ utilidades

    /**
     * Une un resultado con el registro que se envió. Primero por uuid, que es lo fiable, y si el resultado
     * no lo trae (venía inválido) por su posición en el lote.
     */
    private fun <T> encontrar(lote: List<T>, resultado: ResultadoSyncDto, uuid: (T) -> String): T? =
        resultado.uuid?.let { u -> lote.firstOrNull { uuid(it) == u } }
            ?: resultado.indice?.let { lote.getOrNull(it) }

    private fun motivoDe(resultado: ResultadoSyncDto): String {
        val motivo = resultado.motivo ?: ESTADO_RECHAZADO
        return resultado.detalle?.takeIf { it.isNotBlank() }?.let { "$motivo: $it" } ?: motivo
    }

    private companion object {
        const val ESTADO_ACEPTADO = "ACEPTADO"
        const val ESTADO_DUPLICADO = "DUPLICADO"
        const val ESTADO_RECHAZADO = "RECHAZADO"
        const val MOTIVO_ASISTENCIA_NO_ENCONTRADA = "ASISTENCIA_NO_ENCONTRADA"
    }
}

