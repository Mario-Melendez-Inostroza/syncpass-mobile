package com.android.syncpass.ui.eventos

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.android.syncpass.core.auth.UserInfo
import com.android.syncpass.data.local.AsistenciaConAsistente
import com.android.syncpass.data.local.AsistenciaDao
import com.android.syncpass.data.local.CatalogoDao
import com.android.syncpass.data.local.EstadoSync
import com.android.syncpass.data.local.EventoEntity
import com.android.syncpass.ui.Formato
import com.android.syncpass.ui.componentes.BotonPrimario
import com.android.syncpass.ui.componentes.EstadoVacio
import com.android.syncpass.ui.componentes.InsigniaSync
import com.android.syncpass.ui.componentes.PantallaBase
import com.android.syncpass.ui.viewModelDe
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DetalleUi(
    val cargado: Boolean = false,
    val evento: EventoEntity? = null,
    val asistencias: List<AsistenciaConAsistente> = emptyList(),
)

class DetalleEventoViewModel(eventoId: Long, catalogoDao: CatalogoDao, asistenciaDao: AsistenciaDao) : ViewModel() {
    val ui: StateFlow<DetalleUi> = combine(
        catalogoDao.observarEvento(eventoId),
        asistenciaDao.observarPorEvento(eventoId),
    ) { evento, asistencias -> DetalleUi(cargado = true, evento = evento, asistencias = asistencias) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetalleUi())
}

/** Texto de la validación geográfica que calcula el servidor al sincronizar (caso §3.3). */
internal fun etiquetaValidacion(estado: String?, distancia: Int?): String? = when (estado) {
    "VALIDA" -> "Ubicación dentro del radio del evento${distancia?.let { " (a $it m)" } ?: ""}"
    "FUERA_DE_RADIO" -> "Ubicación fuera del radio del evento${distancia?.let { " (a $it m)" } ?: ""}"
    "SIN_UBICACION" -> "Sin ubicación"
    else -> null
}

@Composable
fun EventoDetalleScreen(
    eventoId: Long,
    usuario: UserInfo,
    onAtras: () -> Unit,
    onEscanear: () -> Unit,
    onLista: () -> Unit,
    onEncuesta: (String) -> Unit,
    onResumen: () -> Unit,
) {
    val vm = viewModelDe { c -> DetalleEventoViewModel(eventoId, c.baseDeDatos.catalogoDao(), c.baseDeDatos.asistenciaDao()) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    // Ubicación: referencial y opcional. Se pide una vez al entrar; sin ella la marcación se guarda igual.
    fun tienePermiso() =
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var ubicacionPermitida by remember { mutableStateOf(tienePermiso()) }
    val pedirUbicacion = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ubicacionPermitida = tienePermiso()
    }
    var yaPidio by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!ubicacionPermitida && !yaPidio) {
            yaPidio = true
            pedirUbicacion.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    PantallaBase(titulo = "Evento", onAtras = onAtras) { relleno ->
        val evento = ui.evento
        if (!ui.cargado) return@PantallaBase
        if (evento == null) {
            EstadoVacio("Este evento ya no está en el catálogo", Modifier.padding(relleno))
            return@PantallaBase
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(relleno),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(evento.nombre, style = MaterialTheme.typography.titleLarge)
                        Text(Formato.fechaHoraIso(evento.fechaHoraProgramada), style = MaterialTheme.typography.bodyMedium)
                        evento.recinto?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        evento.cuposMaximos?.let {
                            Text(
                                "Cupos: $it · Marcados en este teléfono: ${ui.asistencias.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (ubicacionPermitida) "Ubicación referencial activada"
                            else "Ubicación desactivada (opcional)",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Se guarda la posición de cada marcación para verificar que ocurrió en el recinto. " +
                                "Si no hay señal o no das permiso, la asistencia se registra igual.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!ubicacionPermitida) {
                            TextButton(onClick = {
                                pedirUbicacion.launch(
                                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                                )
                            }) {
                                Icon(Icons.Default.Place, contentDescription = null)
                                Spacer(Modifier.padding(4.dp))
                                Text("Permitir ubicación")
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonPrimario("Escanear QR de asistente", onClick = onEscanear, icono = Icons.Default.QrCodeScanner)
                    OutlinedButton(onClick = onLista, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Icon(Icons.Default.List, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("Elegir de la lista")
                    }
                    if (usuario.esCoordinador) {
                        OutlinedButton(onClick = onResumen, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Icon(Icons.Default.Insights, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text("Ver quórum del evento")
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Registros hechos en este teléfono", style = MaterialTheme.typography.titleMedium)
            }
            if (ui.asistencias.isEmpty()) {
                item {
                    Text(
                        "Todavía no hay asistencias registradas en este evento.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(ui.asistencias, key = { it.asistencia.uuid }) { fila ->
                val a = fila.asistencia
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(fila.asistenteCodigo, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Marcado ${Formato.fechaHoraMillis(a.marcadoEnMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        InsigniaSync(a.estadoSync, a.motivoFallo)
                        etiquetaValidacion(a.estadoValidacion, a.distanciaMetros)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (a.latitud == null && a.estadoSync == EstadoSync.PENDIENTE) {
                            Text("Sin ubicación", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (fila.tieneEncuesta) {
                            Text("Encuesta registrada", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        } else {
                            TextButton(onClick = { onEncuesta(a.uuid) }) { Text("Responder encuesta") }
                        }
                    }
                }
            }
        }
    }
}
