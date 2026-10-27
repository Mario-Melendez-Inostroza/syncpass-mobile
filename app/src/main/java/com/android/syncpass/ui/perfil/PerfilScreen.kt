package com.android.syncpass.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.android.syncpass.BuildConfig
import com.android.syncpass.core.auth.AuthRepository
import com.android.syncpass.core.auth.UserInfo
import com.android.syncpass.sync.ResumenPendientes
import com.android.syncpass.sync.SyncEstado
import com.android.syncpass.ui.componentes.PantallaBase
import com.android.syncpass.ui.viewModelDe
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PerfilViewModel(sub: String, syncEstado: SyncEstado, private val auth: AuthRepository) : ViewModel() {

    val resumen: StateFlow<ResumenPendientes> = syncEstado.observar(sub)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResumenPendientes(0, 0, 0, null))

    /** Cierra la sesión. Los registros pendientes NO se borran: esperan a que este usuario vuelva a entrar. */
    fun cerrarSesion() {
        viewModelScope.launch { auth.cerrarSesion() }
    }
}

@Composable
fun PerfilScreen(usuario: UserInfo, barraInferior: @Composable () -> Unit) {
    val vm = viewModelDe { c -> PerfilViewModel(usuario.sub.orEmpty(), c.syncEstado, c.authRepository) }
    val resumen by vm.resumen.collectAsStateWithLifecycle()
    var confirmando by remember { mutableStateOf(false) }

    PantallaBase(titulo = "Perfil", barraInferior = barraInferior) { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(usuario.nombre ?: "Usuario", style = MaterialTheme.typography.titleLarge)
                    usuario.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        buildList {
                            if (usuario.esCoordinador) add("Docente / Coordinador(a)")
                            if (usuario.esMonitor) add("Estudiante voluntario / Monitor")
                            if (usuario.esAsistente) add("Asistente")
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (resumen.hayPendientes) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Text(
                        "Tienes ${resumen.pendientes} registro(s) sin enviar. Si cierras sesión se conservan en este " +
                            "teléfono y se enviarán cuando vuelvas a entrar con tu usuario.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            OutlinedButton(
                onClick = { confirmando = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text("  Cerrar sesión")
            }

            Text(
                "SyncPass ${BuildConfig.VERSION_NAME}" + if (BuildConfig.DEBUG) " · ${BuildConfig.API_BASE_URL}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmando) {
        AlertDialog(
            onDismissRequest = { confirmando = false },
            title = { Text("¿Cerrar sesión?") },
            text = {
                Text(
                    if (resumen.hayPendientes) {
                        "Hay ${resumen.pendientes} registro(s) sin enviar. No se pierden, pero no se enviarán hasta " +
                            "que vuelvas a iniciar sesión."
                    } else {
                        "Tendrás que volver a iniciar sesión para usar la app."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmando = false
                    vm.cerrarSesion()
                }) { Text("Cerrar sesión") }
            },
            dismissButton = { TextButton(onClick = { confirmando = false }) { Text("Cancelar") } },
        )
    }
}
