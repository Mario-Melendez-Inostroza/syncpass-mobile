package com.android.syncpass.ui.asistencia

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.syncpass.core.Reloj
import com.android.syncpass.data.RegistroRepository
import com.android.syncpass.data.local.EstadoSync
import com.android.syncpass.ui.componentes.AltoBoton
import com.android.syncpass.ui.componentes.BotonPrimario
import com.android.syncpass.ui.componentes.CamaraEscaner
import com.android.syncpass.ui.componentes.EstadoVacio
import com.android.syncpass.ui.componentes.PantallaBase
import com.android.syncpass.ui.viewModelDe

@Composable
private fun marcarViewModel(eventoId: Long, sub: String): MarcarViewModel = viewModelDe { c ->
    MarcarViewModel(
        eventoId = eventoId,
        sub = sub,
        registro = c.registroRepository,
        catalogoDao = c.baseDeDatos.catalogoDao(),
        asistenciaDao = c.baseDeDatos.asistenciaDao(),
        ubicacion = c.ubicacion,
        scheduler = c.syncScheduler,
        reloj = Reloj.Sistema,
    )
}

/** Aviso con el resultado de la última marcación. Si fue nueva, ofrece responder la encuesta de inmediato. */
@Composable
private fun TarjetaResultado(
    resultado: ResultadoVisible,
    onEncuesta: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (resultado.esError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!resultado.esError) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                }
                Text(resultado.texto, style = MaterialTheme.typography.bodyLarge)
            }
            if (resultado.asistenciaUuid != null) {
                TextButton(onClick = { onEncuesta(resultado.asistenciaUuid) }) { Text("Responder encuesta ahora") }
            }
        }
    }
}

/** Lector de QR. Se queda abierto para marcar a varios asistentes seguidos. */
@Composable
fun EscanerScreen(
    eventoId: Long,
    sub: String,
    onAtras: () -> Unit,
    onLista: () -> Unit,
    onEncuesta: (String) -> Unit,
) {
    val vm = marcarViewModel(eventoId, sub)
    val ultimo by vm.ultimo.collectAsStateWithLifecycle()
    val marcadas by vm.sesionMarcadas.collectAsStateWithLifecycle()
    val ocupado by vm.ocupado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    var permitida by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var rechazada by rememberSaveable { mutableStateOf(false) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        permitida = concedido
        rechazada = !concedido
    }

    PantallaBase(titulo = "Escanear asistente", onAtras = onAtras) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (permitida) {
                    CamaraEscaner(Modifier.fillMaxSize(), onCodigo = vm::alLeerQr)
                    if (ocupado) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                } else {
                    EstadoVacio(
                        texto = "Se necesita la cámara para leer los QR",
                        detalle = if (rechazada) {
                            "No diste permiso a la cámara. Puedes concederlo en los ajustes del teléfono, " +
                                "o elegir al asistente de una lista."
                        } else {
                            "Usaremos la cámara solo para leer el código QR de cada asistente."
                        },
                    )
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ultimo?.let { TarjetaResultado(it, onEncuesta) }
                Text(
                    "Marcados en esta visita: $marcadas",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!permitida) {
                    BotonPrimario("Permitir cámara", onClick = { pedir.launch(Manifest.permission.CAMERA) })
                }
                OutlinedButton(
                    onClick = onLista,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp),
                ) {
                    Icon(Icons.Default.List, contentDescription = null)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                    Text("Elegir de la lista")
                }
            }
        }
    }
}

/** Alternativa sin cámara: buscar al asistente por su código y marcarlo. */
@Composable
fun ListaAsistentesScreen(
    eventoId: Long,
    sub: String,
    onAtras: () -> Unit,
    onEncuesta: (String) -> Unit,
) {
    val vm = marcarViewModel(eventoId, sub)
    val asistentes by vm.asistentes.collectAsStateWithLifecycle()
    val ultimo by vm.ultimo.collectAsStateWithLifecycle()
    var filtro by rememberSaveable { mutableStateOf("") }
    var porConfirmar by remember { mutableStateOf<AsistenteItem?>(null) }
    var mostrarAlta by remember { mutableStateOf(false) }

    val visibles = remember(asistentes, filtro) {
        asistentes.filter { it.asistente.codigo.contains(filtro.trim(), ignoreCase = true) }
    }

    PantallaBase(titulo = "Elegir asistente", onAtras = onAtras) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            OutlinedTextField(
                value = filtro,
                onValueChange = { filtro = it },
                label = { Text("Buscar por código") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            // Caso §3.1: quien llega sin inscripción previa se da de alta aquí mismo, sin papel y sin conexión.
            OutlinedButton(
                onClick = { mostrarAlta = true },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp).heightIn(min = AltoBoton),
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Agregar persona sin inscripción")
            }
            ultimo?.let { TarjetaResultado(it, onEncuesta, Modifier.padding(horizontal = 16.dp)) }

            if (visibles.isEmpty()) {
                EstadoVacio(
                    texto = if (asistentes.isEmpty()) "No hay asistentes en el catálogo" else "Ningún código coincide",
                    detalle = if (asistentes.isEmpty()) "Sincroniza para descargar el catálogo." else null,
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibles, key = { it.asistente.id }) { item ->
                        Card(
                            Modifier.fillMaxWidth().clickable(enabled = !item.marcado) { porConfirmar = item },
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.marcado) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.asistente.codigo, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${item.asistente.tipo.lowercase().replaceFirstChar { it.uppercase() }} · " +
                                            (if (item.asistente.estadoInscripcion == "INSCRITO") "Inscrito" else "Sin inscripción") +
                                            when {
                                                item.asistente.estadoSync == EstadoSync.FALLIDO -> " · Rechazado"
                                                item.asistente.esProvisional -> " · Pendiente de enviar"
                                                else -> ""
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (item.marcado) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Ya marcado", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostrarAlta) {
        AlertDialog(
            onDismissRequest = { mostrarAlta = false },
            title = { Text("Persona sin inscripción") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Elige qué tipo de persona es. Queda registrada y marcada como presente. " +
                            "No se guarda ningún dato personal.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    RegistroRepository.TIPOS_ASISTENTE.forEach { tipo ->
                        OutlinedButton(
                            onClick = {
                                mostrarAlta = false
                                vm.altaYMarcar(tipo)
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = AltoBoton),
                        ) { Text(tipo.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { mostrarAlta = false }) { Text("Cancelar") } },
        )
    }

    porConfirmar?.let { item ->
        AlertDialog(
            onDismissRequest = { porConfirmar = null },
            title = { Text("Registrar asistencia") },
            text = { Text("¿Marcar a ${item.asistente.codigo} como presente en este evento?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.marcar(item.asistente.id)
                    porConfirmar = null
                }) { Text("Registrar") }
            },
            dismissButton = { TextButton(onClick = { porConfirmar = null }) { Text("Cancelar") } },
        )
    }
}
