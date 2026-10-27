package com.android.syncpass.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.android.syncpass.SyncPassApp
import java.util.concurrent.TimeUnit

/** Pide ejecutar la sincronización en segundo plano. Interfaz para poder sustituirla en las pruebas. */
interface SyncScheduler {
    /** Sincroniza en cuanto haya conexión. Se llama después de cada registro y al abrir la app. */
    fun solicitarAhora()

    /** Descarga periódica del catálogo y reenvío de pendientes (caso §3.4). */
    fun programarPeriodica()
}

/**
 * WorkManager es el mecanismo adecuado: el trabajo persiste aunque se cierre la app o se reinicie el
 * teléfono, y el sistema lo ejecuta cuando vuelve la red (restricción CONNECTED), con reintento exponencial.
 */
class WorkManagerSyncScheduler(context: Context) : SyncScheduler {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    private val conRed = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    override fun solicitarAhora() {
        val peticion = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(conRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE: si ya hay una ejecución en curso, la nueva espera a que termine en lugar de
        // descartarse, para no dejar sin enviar lo que se registró mientras la anterior leía los pendientes.
        workManager.enqueueUniqueWork(TRABAJO_INMEDIATO, ExistingWorkPolicy.APPEND_OR_REPLACE, peticion)
    }

    override fun programarPeriodica() {
        val peticion = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(conRed)
            .build()
        workManager.enqueueUniquePeriodicWork(TRABAJO_PERIODICO, ExistingPeriodicWorkPolicy.KEEP, peticion)
    }

    private companion object {
        const val TRABAJO_INMEDIATO = "syncpass-sincronizar-ahora"
        const val TRABAJO_PERIODICO = "syncpass-sincronizar-periodico"
    }
}

class SyncWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val contenedor = (applicationContext as SyncPassApp).contenedor
        return when (val resultado = contenedor.syncEngine.sincronizar()) {
            is ResultadoSync.Completa -> {
                val enviados = resultado.resumen.totalSincronizados
                if (enviados > 0) {
                    contenedor.notificador.avisar(
                        "Sincronización completa",
                        if (enviados == 1) "Se envió 1 registro al servidor." else "Se enviaron $enviados registros al servidor.",
                    )
                }
                Result.success()
            }
            // Sin sesión no hay nada que hacer; el próximo inicio de sesión vuelve a pedir la sincronización.
            ResultadoSync.SinSesion -> Result.success()
            is ResultadoSync.ErrorRed -> Result.retry()
            is ResultadoSync.ErrorServidor -> if (runAttemptCount < MAX_REINTENTOS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_REINTENTOS = 4
    }
}
