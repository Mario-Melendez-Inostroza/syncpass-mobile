package com.android.syncpass.sync

import com.android.syncpass.data.local.AsistenciaDao
import com.android.syncpass.data.local.EncuestaDao
import com.android.syncpass.data.local.EstadoSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Cuánto hay por enviar, para mostrarlo en varias pantallas (caso §3.3: "pendientes de sincronización"). */
data class ResumenPendientes(
    val pendientes: Int,
    val fallidos: Int,
    val sincronizados: Int,
    val ultimaSincronizacion: Long?,
) {
    val hayPendientes: Boolean get() = pendientes > 0
}

/** Cuenta asistencias y encuestas por estado, solo del usuario con sesión. */
class SyncEstado(
    private val asistenciaDao: AsistenciaDao,
    private val encuestaDao: EncuestaDao,
    private val prefs: SyncPrefs,
) {
    fun observar(sub: String): Flow<ResumenPendientes> = combine(
        asistenciaDao.contar(EstadoSync.PENDIENTE, sub),
        encuestaDao.contar(EstadoSync.PENDIENTE, sub),
        asistenciaDao.contar(EstadoSync.FALLIDO, sub),
        encuestaDao.contar(EstadoSync.FALLIDO, sub),
        asistenciaDao.contar(EstadoSync.SINCRONIZADO, sub),
    ) { pa, pe, fa, fe, sa -> listOf(pa + pe, fa + fe, sa) }
        .combine(encuestaDao.contar(EstadoSync.SINCRONIZADO, sub)) { cuentas, se -> cuentas + se }
        .combine(prefs.ultimaSincronizacion()) { cuentas, ultima ->
            ResumenPendientes(
                pendientes = cuentas[0],
                fallidos = cuentas[1],
                sincronizados = cuentas[2] + cuentas[3],
                ultimaSincronizacion = ultima,
            )
        }
}
