package com.android.syncpass.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Ubicación con el proveedor combinado de Google. Es REFERENCIAL (caso §3.3): si no hay permiso, señal o
 * tarda más de [TIMEOUT_MS], devuelve null y la marcación se guarda igual. El servidor la clasifica después
 * como SIN_UBICACION; nunca se rechaza.
 */
class FusedUbicacionProvider(private val context: Context) : UbicacionProvider {

    @SuppressLint("MissingPermission") // se comprueba en tienePermiso()
    override suspend fun actual(): Coordenadas? {
        if (!tienePermiso()) return null
        val cliente = LocationServices.getFusedLocationProviderClient(context)

        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<Coordenadas?> { continuacion ->
                val cancelacion = CancellationTokenSource()
                continuacion.invokeOnCancellation { cancelacion.cancel() }
                try {
                    cliente.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancelacion.token)
                        .addOnSuccessListener { ubicacion ->
                            continuacion.resume(ubicacion?.let { Coordenadas(it.latitude, it.longitude) })
                        }
                        .addOnFailureListener { continuacion.resume(null) }
                } catch (e: SecurityException) {
                    continuacion.resume(null)
                }
            }
        }
    }

    private fun tienePermiso(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TIMEOUT_MS = 8_000L
    }
}

/** Notificaciones del sistema. En Android 13+ requieren el permiso POST_NOTIFICATIONS; sin él no se muestran. */
class AndroidNotificador(private val context: Context) : Notificador {

    @SuppressLint("MissingPermission") // se comprueba antes de publicar
    override fun avisar(titulo: String, texto: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        crearCanal()
        val notificacion = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notificacion)
    }

    private fun crearCanal() {
        val gestor = context.getSystemService(NotificationManager::class.java) ?: return
        if (gestor.getNotificationChannel(CANAL) == null) {
            gestor.createNotificationChannel(
                NotificationChannel(CANAL, "Sincronización", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    private companion object {
        const val CANAL = "syncpass_sincronizacion"
    }
}
