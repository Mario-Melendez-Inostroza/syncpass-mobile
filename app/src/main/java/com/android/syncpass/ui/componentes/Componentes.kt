package com.android.syncpass.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.syncpass.data.local.EstadoSync
import com.android.syncpass.sync.ResumenPendientes
import com.android.syncpass.ui.Formato

/** Alto mínimo de los botones principales: grandes, para operar con una sola mano en terreno (caso §3.3). */
val AltoBoton = 56.dp

/** Estructura común de las pantallas: barra superior, botón atrás opcional y avisos (Snackbar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaBase(
    titulo: String,
    onAtras: (() -> Unit)? = null,
    avisos: SnackbarHostState? = null,
    barraInferior: @Composable () -> Unit = {},
    acciones: @Composable RowScope.() -> Unit = {},
    contenido: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(titulo, maxLines = 1) },
                navigationIcon = {
                    if (onAtras != null) {
                        IconButton(onClick = onAtras) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                actions = acciones,
            )
        },
        bottomBar = barraInferior,
        snackbarHost = { avisos?.let { SnackbarHost(it) } },
        content = contenido,
    )
}

@Composable
fun BotonPrimario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    enabled: Boolean = true,
    cargando: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !cargando,
        modifier = modifier.fillMaxWidth().heightIn(min = AltoBoton),
    ) {
        if (cargando) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else if (icono != null) {
            Icon(icono, contentDescription = null)
        }
        Spacer(Modifier.width(8.dp))
        Text(texto)
    }
}

@Composable
fun EstadoVacio(texto: String, modifier: Modifier = Modifier, detalle: String? = null) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(texto, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (detalle != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                detalle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Estado de sincronización de un registro: Pendiente, Enviado o Rechazado (con su motivo). */
@Composable
fun InsigniaSync(estado: EstadoSync, motivo: String? = null) {
    val (icono, texto, color) = when (estado) {
        EstadoSync.PENDIENTE -> Triple(Icons.Default.CloudOff, "Pendiente de enviar", MaterialTheme.colorScheme.tertiary)
        EstadoSync.SINCRONIZADO -> Triple(Icons.Default.CloudDone, "Enviado", MaterialTheme.colorScheme.primary)
        EstadoSync.FALLIDO -> Triple(Icons.Default.ErrorOutline, "Rechazado", MaterialTheme.colorScheme.error)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (estado == EstadoSync.FALLIDO && motivo != null) "$texto: $motivo" else texto,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/**
 * Aviso permanente de lo que falta por enviar (caso §3.3: avisos de "pendientes de sincronización") y acceso a
 * sincronizar a mano.
 */
@Composable
fun BannerSync(
    resumen: ResumenPendientes,
    sincronizando: Boolean,
    onSincronizar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hayPendientes = resumen.hayPendientes
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hayPendientes) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        hayPendientes -> "${resumen.pendientes} registro(s) pendientes de enviar"
                        resumen.fallidos > 0 -> "${resumen.fallidos} registro(s) rechazados"
                        else -> "Todo sincronizado"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Última sincronización: ${Formato.fechaHoraMillis(resumen.ultimaSincronizacion)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onSincronizar, enabled = !sincronizando) {
                if (sincronizando) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Sync, contentDescription = "Sincronizar ahora")
                }
            }
        }
    }
}
