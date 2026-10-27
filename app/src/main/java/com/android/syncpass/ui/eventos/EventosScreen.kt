package com.android.syncpass.ui.eventos

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.android.syncpass.core.auth.UserInfo
import com.android.syncpass.data.local.CatalogoDao
import com.android.syncpass.data.local.EventoConProyecto
import com.android.syncpass.sync.ResumenPendientes
import com.android.syncpass.sync.SyncEngine
import com.android.syncpass.sync.SyncEstado
import com.android.syncpass.ui.AccionSincronizar
import com.android.syncpass.ui.Formato
import com.android.syncpass.ui.componentes.BannerSync
import com.android.syncpass.ui.componentes.EstadoVacio
import com.android.syncpass.ui.componentes.PantallaBase
import com.android.syncpass.ui.viewModelDe
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class EventosUi(
    val eventos: List<EventoConProyecto> = emptyList(),
    val resumen: ResumenPendientes = ResumenPendientes(0, 0, 0, null),
    val sincronizando: Boolean = false,
)

class EventosViewModel(
    catalogoDao: CatalogoDao,
    syncEstado: SyncEstado,
    motor: SyncEngine,
    sub: String,
) : ViewModel() {

    private val _mensajes = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val mensajes = _mensajes.asSharedFlow()

    private val accion = AccionSincronizar(motor, viewModelScope) { _mensajes.tryEmit(it) }

    val ui: StateFlow<EventosUi> = combine(
        catalogoDao.observarEventos(),
        syncEstado.observar(sub),
        accion.sincronizando,
    ) { eventos, resumen, sincronizando -> EventosUi(eventos, resumen, sincronizando) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventosUi())

    fun sincronizarAhora() = accion.ejecutar()
}

@Composable
fun EventosScreen(
    usuario: UserInfo,
    onEvento: (Long) -> Unit,
    onCredenciales: () -> Unit,
    barraInferior: @Composable () -> Unit,
) {
    val vm = viewModelDe { c ->
        EventosViewModel(c.baseDeDatos.catalogoDao(), c.syncEstado, c.syncEngine, usuario.sub.orEmpty())
    }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.mensajes.collect { avisos.showSnackbar(it) } }

    // Android 13+: los avisos del sistema requieren permiso. Se pide una sola vez, al entrar.
    val pedirNotificaciones = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var yaPidioNotificaciones by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!yaPidioNotificaciones && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            yaPidioNotificaciones = true
            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    PantallaBase(
        titulo = "Eventos",
        avisos = avisos,
        barraInferior = barraInferior,
        acciones = {
            if (usuario.esCoordinador) {
                IconButton(onClick = onCredenciales) {
                    Icon(Icons.Default.QrCode2, contentDescription = "Credenciales QR de asistentes")
                }
            }
        },
    ) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            BannerSync(
                resumen = ui.resumen,
                sincronizando = ui.sincronizando,
                onSincronizar = vm::sincronizarAhora,
                modifier = Modifier.padding(16.dp),
            )
            if (ui.eventos.isEmpty()) {
                EstadoVacio(
                    texto = "Todavía no hay eventos en este teléfono",
                    detalle = "Conéctate a internet y toca el botón de sincronizar para descargar el catálogo. " +
                        "Después podrás trabajar sin conexión.",
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(ui.eventos, key = { it.evento.id }) { fila ->
                        Card(Modifier.fillMaxWidth().clickable { onEvento(fila.evento.id) }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(fila.evento.nombre, style = MaterialTheme.typography.titleMedium)
                                fila.proyectoTitulo?.let {
                                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(Formato.fechaHoraIso(fila.evento.fechaHoraProgramada), style = MaterialTheme.typography.bodyMedium)
                                fila.evento.recinto?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
