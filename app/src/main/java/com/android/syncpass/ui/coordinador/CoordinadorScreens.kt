package com.android.syncpass.ui.coordinador

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.android.syncpass.core.network.ApiErrors
import com.android.syncpass.core.network.ResumenEventoDto
import com.android.syncpass.core.network.SyncPassApi
import com.android.syncpass.core.qr.CodigoQr
import com.android.syncpass.core.qr.QrBitmap
import com.android.syncpass.data.local.AsistenteEntity
import com.android.syncpass.data.local.CatalogoDao
import com.android.syncpass.ui.Formato
import com.android.syncpass.ui.componentes.EstadoVacio
import com.android.syncpass.ui.componentes.PantallaBase
import com.android.syncpass.ui.viewModelDe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ quórum del evento

data class ResumenUi(
    val cargando: Boolean = true,
    val resumen: ResumenEventoDto? = null,
    val error: String? = null,
)

/**
 * Quórum y satisfacción en vivo (caso §2.4 y §4.1). Es lo único que necesita conexión: viene del servidor y
 * solo lo ve el grupo COORDINADOR (el servidor responde 403 a cualquier otro, aunque la app mostrara el botón).
 */
class ResumenViewModel(private val eventoId: Long, private val api: SyncPassApi) : ViewModel() {

    private val _ui = MutableStateFlow(ResumenUi())
    val ui: StateFlow<ResumenUi> = _ui.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _ui.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val respuesta = api.resumenEvento(eventoId)
                val datos = respuesta.data
                _ui.update {
                    if (respuesta.success && datos != null) ResumenUi(false, datos, null)
                    else ResumenUi(false, null, "El servidor no devolvió el resumen.")
                }
            } catch (e: Exception) {
                val mensaje = if (ApiErrors.esHttp(e, 403)) {
                    "Tu usuario no pertenece al grupo COORDINADOR en el servidor, así que no puede ver el quórum."
                } else {
                    ApiErrors.describir(e)
                }
                _ui.update { ResumenUi(false, null, mensaje) }
            }
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, style = MaterialTheme.typography.bodyLarge)
        Text(valor, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ResumenScreen(eventoId: Long, onAtras: () -> Unit) {
    val vm = viewModelDe { c -> ResumenViewModel(eventoId, c.red.syncPassApi) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    PantallaBase(
        titulo = "Quórum del evento",
        onAtras = onAtras,
        acciones = {
            IconButton(onClick = vm::cargar, enabled = !ui.cargando) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
            }
        },
    ) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno)) {
            when {
                ui.cargando -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.error != null -> EstadoVacio("No se pudo cargar el quórum", detalle = ui.error)
                ui.resumen != null -> {
                    val r = ui.resumen!!
                    Column(Modifier.padding(16.dp)) {
                        Text(r.nombre.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Datos del servidor: solo incluye lo que ya se sincronizó.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Dato("Asistentes marcados", "${r.totalMarcados ?: 0}" + (r.cuposMaximos?.let { " de $it" } ?: ""))
                                HorizontalDivider()
                                Dato("Dentro del radio", "${r.validas ?: 0}")
                                Dato("Fuera del radio", "${r.fueraDeRadio ?: 0}")
                                Dato("Sin ubicación", "${r.sinUbicacion ?: 0}")
                                HorizontalDivider()
                                Dato("Encuestas recibidas", "${r.encuestasRecibidas ?: 0}")
                                Dato("Satisfacción promedio", Formato.decimal(r.satisfaccionPromedio) + " / 5")
                                Dato("Utilidad promedio", Formato.decimal(r.utilidadPromedio) + " / 5")
                                HorizontalDivider()
                                Dato("Primera marcación", Formato.fechaHoraIso(r.primeraMarcacion))
                                Dato("Última marcación", Formato.fechaHoraIso(r.ultimaMarcacion))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ credenciales QR

class CredencialesViewModel(catalogoDao: CatalogoDao) : ViewModel() {
    // Sin los provisionales (id negativo): todavía no existen en el servidor y CodigoQr no admite ids no positivos.
    val asistentes: StateFlow<List<AsistenteEntity>> = catalogoDao.observarAsistentes()
        .map { lista -> lista.filter { it.id > 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Credenciales de los asistentes. Los beneficiarios no tienen cuenta en la app: el monitor escanea este QR.
 * El código solo contiene el identificador sintético, ningún dato personal (caso §5.1).
 */
@Composable
fun CredencialesScreen(onAtras: () -> Unit) {
    val vm = viewModelDe { c -> CredencialesViewModel(c.baseDeDatos.catalogoDao()) }
    val asistentes by vm.asistentes.collectAsStateWithLifecycle()

    PantallaBase(titulo = "Credenciales QR", onAtras = onAtras) { relleno ->
        if (asistentes.isEmpty()) {
            EstadoVacio(
                "No hay asistentes en el catálogo",
                Modifier.padding(relleno),
                detalle = "Sincroniza para descargar el catálogo.",
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(relleno),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Muestra o imprime estos códigos. El monitor los escanea para registrar la asistencia. " +
                            "No contienen datos personales.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(asistentes, key = { it.id }) { asistente ->
                    val contenido = CodigoQr.generarAsistente(asistente.id)
                    val imagen = remember(contenido) { QrBitmap.generar(contenido, 600).asImageBitmap() }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(asistente.codigo, style = MaterialTheme.typography.titleLarge)
                            Text(
                                asistente.tipo.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Image(
                                bitmap = imagen,
                                contentDescription = "Código QR de ${asistente.codigo}",
                                modifier = Modifier.padding(top = 12.dp).size(220.dp),
                            )
                            Text(contenido, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
