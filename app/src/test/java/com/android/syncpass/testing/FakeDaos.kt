package com.android.syncpass.testing

import android.database.sqlite.SQLiteConstraintException
import com.android.syncpass.core.GeneradorUuid
import com.android.syncpass.core.Reloj
import com.android.syncpass.core.network.ApiResponse
import com.android.syncpass.core.network.CatalogoDto
import com.android.syncpass.core.network.LoteSyncDto
import com.android.syncpass.core.network.ResumenEventoDto
import com.android.syncpass.core.network.SincronizarAsistenciasRequest
import com.android.syncpass.core.network.SincronizarEncuestasRequest
import com.android.syncpass.core.network.SyncPassApi
import com.android.syncpass.data.local.AsistenciaConAsistente
import com.android.syncpass.data.local.AsistenciaDao
import com.android.syncpass.data.local.AsistenciaEntity
import com.android.syncpass.data.local.AsistenteEntity
import com.android.syncpass.data.local.CatalogoDao
import com.android.syncpass.data.local.EncuestaDao
import com.android.syncpass.data.local.EncuestaEntity
import com.android.syncpass.data.local.EstadoSync
import com.android.syncpass.data.local.EventoConProyecto
import com.android.syncpass.data.local.EventoEntity
import com.android.syncpass.data.local.ProyectoEntity
import com.android.syncpass.sync.SyncPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Doble en memoria del DAO de Room: lo bastante fiel (incluidas las restricciones únicas) para probar la lógica. */
class FakeCatalogoDao : CatalogoDao {
    val proyectos = linkedMapOf<Long, ProyectoEntity>()
    val eventos = linkedMapOf<Long, EventoEntity>()
    val asistentes = linkedMapOf<Long, AsistenteEntity>()

    override suspend fun upsertProyectos(proyectos: List<ProyectoEntity>) = proyectos.forEach { this.proyectos[it.id] = it }
    override suspend fun upsertEventos(eventos: List<EventoEntity>) = eventos.forEach { this.eventos[it.id] = it }
    override suspend fun upsertAsistentes(asistentes: List<AsistenteEntity>) = asistentes.forEach { this.asistentes[it.id] = it }

    override fun observarEventos(): Flow<List<EventoConProyecto>> =
        flowOf(eventos.values.map { EventoConProyecto(it, proyectos[it.proyectoId]?.titulo) })

    override fun observarEvento(id: Long): Flow<EventoEntity?> = flowOf(eventos[id])
    override suspend fun evento(id: Long): EventoEntity? = eventos[id]
    override fun observarAsistentes(): Flow<List<AsistenteEntity>> = flowOf(asistentes.values.toList())
    override suspend fun asistente(id: Long): AsistenteEntity? = asistentes[id]
}

class FakeAsistenciaDao : AsistenciaDao {
    val filas = linkedMapOf<String, AsistenciaEntity>()
    private val cambios = MutableStateFlow(0)

    override suspend fun insertar(asistencia: AsistenciaEntity) {
        val duplicada = filas.containsKey(asistencia.uuid) ||
            filas.values.any { it.eventoId == asistencia.eventoId && it.asistenteId == asistencia.asistenteId }
        if (duplicada) throw SQLiteConstraintException("UNIQUE constraint failed")
        filas[asistencia.uuid] = asistencia
        cambios.value++
    }

    override suspend fun buscar(eventoId: Long, asistenteId: Long) =
        filas.values.firstOrNull { it.eventoId == eventoId && it.asistenteId == asistenteId }

    override suspend fun porUuid(uuid: String) = filas[uuid]

    override fun observarPorEvento(eventoId: Long): Flow<List<AsistenciaConAsistente>> = cambios.map {
        filas.values.filter { it.eventoId == eventoId }
            .map { AsistenciaConAsistente(it, "ASI-${it.asistenteId}", tieneEncuesta = false) }
    }

    override suspend fun pendientes(sub: String) =
        filas.values.filter { it.estadoSync == EstadoSync.PENDIENTE && it.usuarioSub == sub }.sortedBy { it.marcadoEnMillis }

    private fun actualizar(uuid: String, cambio: (AsistenciaEntity) -> AsistenciaEntity) {
        filas[uuid]?.let { filas[uuid] = cambio(it) }
    }

    override suspend fun marcarSincronizada(uuid: String, servidorId: Long?, estadoValidacion: String?, distanciaMetros: Int?) =
        actualizar(uuid) {
            it.copy(
                estadoSync = EstadoSync.SINCRONIZADO,
                motivoFallo = null,
                servidorId = servidorId,
                estadoValidacion = estadoValidacion,
                distanciaMetros = distanciaMetros,
            )
        }

    override suspend fun marcarFallida(uuid: String, motivo: String) =
        actualizar(uuid) { it.copy(estadoSync = EstadoSync.FALLIDO, motivoFallo = motivo) }

    override suspend fun reintentar(uuid: String) =
        actualizar(uuid) { if (it.estadoSync == EstadoSync.FALLIDO) it.copy(estadoSync = EstadoSync.PENDIENTE, motivoFallo = null) else it }

    override suspend fun reintentarPorReferencias(): Int {
        val motivos = setOf("EVENTO_NO_ENCONTRADO", "ASISTENTE_NO_ENCONTRADO")
        var cambiadas = 0
        filas.values.toList().forEach {
            if (it.estadoSync == EstadoSync.FALLIDO && it.motivoFallo in motivos) {
                filas[it.uuid] = it.copy(estadoSync = EstadoSync.PENDIENTE, motivoFallo = null)
                cambiadas++
            }
        }
        return cambiadas
    }

    override fun contar(estado: EstadoSync, sub: String): Flow<Int> =
        flowOf(filas.values.count { it.estadoSync == estado && it.usuarioSub == sub })

    override fun observarFallidas(sub: String): Flow<List<AsistenciaEntity>> =
        flowOf(filas.values.filter { it.estadoSync == EstadoSync.FALLIDO && it.usuarioSub == sub })
}

class FakeEncuestaDao : EncuestaDao {
    val filas = linkedMapOf<String, EncuestaEntity>()

    override suspend fun insertar(encuesta: EncuestaEntity) {
        val duplicada = filas.containsKey(encuesta.uuid) || filas.values.any { it.asistenciaUuid == encuesta.asistenciaUuid }
        if (duplicada) throw SQLiteConstraintException("UNIQUE constraint failed")
        filas[encuesta.uuid] = encuesta
    }

    override suspend fun porAsistencia(asistenciaUuid: String) = filas.values.firstOrNull { it.asistenciaUuid == asistenciaUuid }

    override suspend fun pendientes(sub: String) =
        filas.values.filter { it.estadoSync == EstadoSync.PENDIENTE && it.usuarioSub == sub }.sortedBy { it.enviadaEnMillis }

    private fun actualizar(uuid: String, cambio: (EncuestaEntity) -> EncuestaEntity) {
        filas[uuid]?.let { filas[uuid] = cambio(it) }
    }

    override suspend fun marcarSincronizada(uuid: String, servidorId: Long?) =
        actualizar(uuid) { it.copy(estadoSync = EstadoSync.SINCRONIZADO, motivoFallo = null, servidorId = servidorId) }

    override suspend fun marcarFallida(uuid: String, motivo: String) =
        actualizar(uuid) { it.copy(estadoSync = EstadoSync.FALLIDO, motivoFallo = motivo) }

    override suspend fun reintentar(uuid: String) =
        actualizar(uuid) { if (it.estadoSync == EstadoSync.FALLIDO) it.copy(estadoSync = EstadoSync.PENDIENTE, motivoFallo = null) else it }

    override fun contar(estado: EstadoSync, sub: String): Flow<Int> =
        flowOf(filas.values.count { it.estadoSync == estado && it.usuarioSub == sub })

    override fun observarFallidas(sub: String): Flow<List<EncuestaEntity>> =
        flowOf(filas.values.filter { it.estadoSync == EstadoSync.FALLIDO && it.usuarioSub == sub })
}

class FakeSyncPrefs : SyncPrefs {
    var catalogoEn: String? = null
    private val ultima = MutableStateFlow<Long?>(null)

    override suspend fun ultimoCatalogoEn() = catalogoEn
    override suspend fun guardarCatalogoEn(valor: String) {
        catalogoEn = valor
    }

    override suspend fun registrarSincronizacion(ahoraMillis: Long) {
        ultima.value = ahoraMillis
    }

    override fun ultimaSincronizacion(): Flow<Long?> = ultima
}

/** Servidor falso: cada operación es una lambda que la prueba reemplaza, y todo lo recibido queda registrado. */
class FakeSyncApi : SyncPassApi {
    /** Orden de las llamadas, para comprobar p. ej. que las asistencias suben antes que las encuestas. */
    val llamadas = mutableListOf<String>()
    val desdeRecibidos = mutableListOf<String?>()
    val lotesAsistencias = mutableListOf<SincronizarAsistenciasRequest>()
    val lotesEncuestas = mutableListOf<SincronizarEncuestasRequest>()

    var alCatalogo: (String?) -> ApiResponse<CatalogoDto> =
        { ApiResponse(success = true, data = CatalogoDto(servidorEn = "2026-10-28T15:00:00Z")) }
    var alSubirAsistencias: (SincronizarAsistenciasRequest) -> ApiResponse<LoteSyncDto> = { respuestaLote(emptyList()) }
    var alSubirEncuestas: (SincronizarEncuestasRequest) -> ApiResponse<LoteSyncDto> = { respuestaLote(emptyList()) }

    override suspend fun catalogo(desde: String?): ApiResponse<CatalogoDto> {
        llamadas += "catalogo"
        desdeRecibidos += desde
        return alCatalogo(desde)
    }

    override suspend fun subirAsistencias(request: SincronizarAsistenciasRequest): ApiResponse<LoteSyncDto> {
        llamadas += "asistencias"
        lotesAsistencias += request
        return alSubirAsistencias(request)
    }

    override suspend fun subirEncuestas(request: SincronizarEncuestasRequest): ApiResponse<LoteSyncDto> {
        llamadas += "encuestas"
        lotesEncuestas += request
        return alSubirEncuestas(request)
    }

    var alResumen: (Long) -> ApiResponse<ResumenEventoDto> = { ApiResponse(success = true, data = ResumenEventoDto(eventoId = it)) }

    override suspend fun resumenEvento(id: Long): ApiResponse<ResumenEventoDto> = alResumen(id)
}

fun respuestaLote(resultados: List<com.android.syncpass.core.network.ResultadoSyncDto>) = ApiResponse(
    success = true,
    data = LoteSyncDto(
        aceptados = resultados.count { it.estado == "ACEPTADO" },
        duplicados = resultados.count { it.estado == "DUPLICADO" },
        rechazados = resultados.count { it.estado == "RECHAZADO" },
        resultados = resultados,
    ),
)

class RelojFijo(var ahora: Long = 1_793_199_851_000L) : Reloj {
    override fun ahoraMillis() = ahora
}

/** Uuid predecibles: "uuid-1", "uuid-2"... */
class UuidSecuencial : GeneradorUuid {
    private var n = 0
    override fun nuevo() = "uuid-${++n}"
}
