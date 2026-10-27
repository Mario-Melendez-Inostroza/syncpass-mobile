package com.android.syncpass.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.android.syncpass.data.local.AsistenciaDao
import com.android.syncpass.data.local.CatalogoDao
import com.android.syncpass.data.local.EncuestaDao
import com.android.syncpass.sync.ResumenPendientes
import com.android.syncpass.sync.SyncEngine
import com.android.syncpass.sync.SyncEstado
import com.android.syncpass.sync.SyncScheduler
import com.android.syncpass.ui.AccionSincronizar
import com.android.syncpass.ui.Formato
import com.android.syncpass.ui.componentes.BotonPrimario
import com.android.syncpass.ui.componentes.PantallaBase
import com.android.syncpass.ui.viewModelDe
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FallidoItem(val uuid: String, val titulo: String, val motivo: String, val esEncuesta: Boolean, val marcadoEnMillis: Long)

data class SincronizacionUi(
    val resumen: ResumenPendientes = ResumenPendientes(0, 0, 0, null),
    val fallidos: List<FallidoItem> = emptyList(),
    val sincronizando: Boolean = false,
)

/** Explica en lenguaje simple por qué el servidor rechazó un registro. */
internal fun motivoLegible(motivo: String?): String {
    val codigo = motivo?.substringBefore(":")
    val detalle = motivo?.substringAfter(":", "")?.trim().orEmpty()
    return when (codigo) {
        "EVENTO_NO_ENCONTRADO" -> "El evento no existe en el servidor."
        "ASISTENTE_NO_ENCONTRADO" -> "El asistente no existe en el servidor."
        "ASISTENCIA_NO_ENCONTRADA" -> "La asistencia de esta encuesta nunca llegó al servidor."
        "DATOS_INVALIDOS" -> "El servidor consideró inválidos los datos" + if (detalle.isNotEmpty()) " ($detalle)." else "."
        else -> motivo ?: "Rechazado por el servidor."
    }
}

class SincronizacionViewModel(
    sub: String,
    private val asistenciaDao: AsistenciaDao,
    private val encuestaDao: EncuestaDao,
    catalogoDao: CatalogoDao,
    syncEstado: SyncEstado,
    motor: SyncEngine,
    private val scheduler: SyncScheduler,
) : ViewModel() {

    private val _mensajes = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val mensajes = _mensajes.asSharedFlow()

    private val accion = AccionSincronizar(motor, viewModelScope) { _mensajes.tryEmit(it) }

    val ui: StateFlow<SincronizacionUi> = combine(
        syncEstado.observar(sub),
        asistenciaDao.observarFallidas(sub),
        encuestaDao.observarFallidas(sub),
        catalogoDao.observarAsistentes(),
        accion.sincronizando,
    ) { resumen, asistenciasFallidas, encuestasFallidas, asistentes, sincronizando ->
        val codigos = asistentes.associate { it.id to it.codigo }
        val items = asistenciasFallidas.map {
            FallidoItem(
                it.uuid,
                "Asistencia de ${codigos[it.asistenteId] ?: "#${it.asistenteId}"}",
                motivoLegible(it.motivoFallo),
                esEncuesta = false,
                marcadoEnMillis = it.marcadoEnMillis,
            )
        } + encuestasFallidas.map {
            FallidoItem(
                it.uuid,
                "Encuesta (satisfacción ${it.satisfaccion}, utilidad ${it.utilidad})",
                motivoLegible(it.motivoFallo),
                esEncuesta = true,
                marcadoEnMillis = it.enviadaEnMillis,
            )
        }
        SincronizacionUi(resumen, items.sortedBy { it.marcadoEnMillis }, sincronizando)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SincronizacionUi())

    fun sincronizarAhora() = accion.ejecutar()

    /** Devuelve un registro rechazado a PENDIENTE y pide el envío (p. ej. tras corregir el catálogo). */
    fun reintentar(item: FallidoItem) {
        viewModelScope.launch {
            if (item.esEncuesta) encuestaDao.reintentar(item.uuid) else asistenciaDao.reintentar(item.uuid)
            scheduler.solicitarAhora()
        }
    }
}

@Composable
private fun Cifra(titulo: String, valor: Int, modifier: Modifier = Modifier, destacada: Boolean = false) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (destacada) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("$valor", style = MaterialTheme.typography.headlineMedium)
            Text(titulo, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SincronizacionScreen(sub: String, barraInferior: @Composable () -> Unit) {
    val vm = viewModelDe { c ->
        SincronizacionViewModel(
            sub, c.baseDeDatos.asistenciaDao(), c.baseDeDatos.encuestaDao(), c.baseDeDatos.catalogoDao(),
            c.syncEstado, c.syncEngine, c.syncScheduler,
        )
    }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.mensajes.collect { avisos.showSnackbar(it) } }

    PantallaBase(titulo = "Sincronización", avisos = avisos, barraInferior = barraInferior) { relleno ->
        LazyColumn(
            Modifier.fillMaxSize().padding(relleno),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Cifra("Pendientes", ui.resumen.pendientes, Modifier.weight(1f), destacada = ui.resumen.hayPendientes)
                    Cifra("Enviados", ui.resumen.sincronizados, Modifier.weight(1f))
                    Cifra("Rechazados", ui.resumen.fallidos, Modifier.weight(1f))
                }
            }
            item {
                BotonPrimario(
                    texto = "Sincronizar ahora",
                    onClick = vm::sincronizarAhora,
                    icono = Icons.Default.Sync,
                    cargando = ui.sincronizando,
                )
            }
            item {
                Text(
                    "Última sincronización completa: ${Formato.fechaHoraMillis(ui.resumen.ultimaSincronizacion)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Lo que registras se guarda primero en este teléfono y se envía solo cuando hay conexión. " +
                        "Si cierras la app o apagas el teléfono, no se pierde.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ui.fallidos.isNotEmpty()) {
                item { Text("Rechazados por el servidor", style = MaterialTheme.typography.titleMedium) }
                items(ui.fallidos, key = { it.uuid }) { f ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(f.titulo, style = MaterialTheme.typography.titleSmall)
                            Text(Formato.fechaHoraMillis(f.marcadoEnMillis), style = MaterialTheme.typography.bodySmall)
                            Text(f.motivo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { vm.reintentar(f) }) { Text("Reintentar") }
                        }
                    }
                }
            }
        }
    }
}
