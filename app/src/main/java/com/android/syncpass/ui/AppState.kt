package com.android.syncpass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.syncpass.AppContainer
import com.android.syncpass.sync.ResultadoSync
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** El contenedor de dependencias de la app, disponible para cualquier pantalla. */
val LocalContenedor = staticCompositionLocalOf<AppContainer> {
    error("LocalContenedor no está provisto: MainActivity debe envolver la interfaz con CompositionLocalProvider")
}

/** Crea (o recupera) un ViewModel dándole acceso al contenedor, sin framework de DI. */
@Composable
inline fun <reified VM : ViewModel> viewModelDe(noinline crear: (AppContainer) -> VM): VM {
    val contenedor = LocalContenedor.current
    return viewModel(factory = viewModelFactory { initializer { crear(contenedor) } })
}

/** Fechas legibles en la zona horaria del teléfono (el servidor y la base guardan UTC). */
object Formato {
    private val fechaHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("es", "CL"))
        .withZone(ZoneId.systemDefault())
    private val hora = DateTimeFormatter.ofPattern("HH:mm", Locale("es", "CL"))
        .withZone(ZoneId.systemDefault())

    fun fechaHoraIso(iso: String?): String =
        runCatching { fechaHora.format(Instant.parse(iso)) }.getOrDefault(iso ?: "—")

    fun fechaHoraMillis(millis: Long?): String =
        if (millis == null) "—" else fechaHora.format(Instant.ofEpochMilli(millis))

    fun horaMillis(millis: Long): String = hora.format(Instant.ofEpochMilli(millis))

    private val dia = DateTimeFormatter.ofPattern("dd", Locale("es", "CL")).withZone(ZoneId.systemDefault())
    private val mes = DateTimeFormatter.ofPattern("MMM", Locale("es", "CL")).withZone(ZoneId.systemDefault())

    /** Día y mes abreviado ("05", "NOV") para la insignia de fecha de un evento; "—" si la fecha no es válida. */
    fun diaMes(iso: String?): Pair<String, String> = runCatching {
        val instante = Instant.parse(iso)
        dia.format(instante) to mes.format(instante).uppercase().trimEnd('.')
    }.getOrDefault("—" to "")

    fun decimal(valor: Double?): String = valor?.let { String.format(Locale("es", "CL"), "%.1f", it) } ?: "—"
}

/** Texto para el usuario tras una sincronización manual. */
fun mensajeDe(resultado: ResultadoSync): String = when (resultado) {
    is ResultadoSync.Completa -> {
        val r = resultado.resumen
        val enviados = r.totalSincronizados
        val fallidos = r.asistencias.fallidos + r.encuestas.fallidos
        when {
            fallidos > 0 -> "Sincronizado. $fallidos registro(s) fueron rechazados: revisa la pestaña Sincronización."
            enviados > 0 -> "Sincronizado: se enviaron $enviados registro(s) al servidor."
            else -> "Todo está al día."
        }
    }
    ResultadoSync.SinSesion -> "Inicia sesión para sincronizar."
    is ResultadoSync.ErrorRed -> "Sin conexión con el servidor. Se enviará solo cuando vuelva la señal."
    is ResultadoSync.ErrorServidor ->
        "El servidor respondió con un error${resultado.codigo?.let { " ($it)" } ?: ""}. Se reintentará más tarde."
}

/**
 * Ejecuta una sincronización manual y avisa del resultado. Lo comparten las pantallas que tienen un botón
 * "Sincronizar ahora"; el envío automático lo hace WorkManager (SyncWorker).
 */
class AccionSincronizar(
    private val motor: com.android.syncpass.sync.SyncEngine,
    private val alcance: kotlinx.coroutines.CoroutineScope,
    private val alMensaje: (String) -> Unit,
) {
    val sincronizando = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun ejecutar() {
        if (sincronizando.value) return
        sincronizando.value = true
        alcance.launch {
            try {
                alMensaje(mensajeDe(motor.sincronizar()))
            } finally {
                sincronizando.value = false
            }
        }
    }
}
