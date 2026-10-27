package com.android.syncpass.ui.encuesta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.android.syncpass.data.RegistroRepository
import com.android.syncpass.data.ResultadoEncuesta
import com.android.syncpass.data.local.AsistenciaDao
import com.android.syncpass.data.local.CatalogoDao
import com.android.syncpass.sync.SyncScheduler
import com.android.syncpass.ui.componentes.BotonPrimario
import com.android.syncpass.ui.componentes.EstadoVacio
import com.android.syncpass.ui.componentes.PantallaBase
import com.android.syncpass.ui.viewModelDe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EncuestaUi(
    val cargado: Boolean = false,
    val asistenteCodigo: String? = null,
    val existe: Boolean = true,
    val satisfaccion: Int = 0,
    val utilidad: Int = 0,
    val comentarios: String = "",
    val guardando: Boolean = false,
    val error: String? = null,
    /** true si esa asistencia ya tiene encuesta registrada. */
    val yaRespondida: Boolean = false,
) {
    val completa: Boolean get() = satisfaccion in RegistroRepository.ESCALA && utilidad in RegistroRepository.ESCALA
}

class EncuestaViewModel(
    private val asistenciaUuid: String,
    private val sub: String,
    private val registro: RegistroRepository,
    asistenciaDao: AsistenciaDao,
    catalogoDao: CatalogoDao,
    private val scheduler: SyncScheduler,
) : ViewModel() {

    private val _ui = MutableStateFlow(EncuestaUi())
    val ui: StateFlow<EncuestaUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val asistencia = asistenciaDao.porUuid(asistenciaUuid)
            val codigo = asistencia?.let { catalogoDao.asistente(it.asistenteId)?.codigo }
            _ui.update { it.copy(cargado = true, existe = asistencia != null, asistenteCodigo = codigo) }
        }
    }

    fun onSatisfaccion(valor: Int) = _ui.update { it.copy(satisfaccion = valor, error = null) }

    fun onUtilidad(valor: Int) = _ui.update { it.copy(utilidad = valor, error = null) }

    fun onComentarios(valor: String) =
        _ui.update { it.copy(comentarios = valor.take(RegistroRepository.MAX_COMENTARIO), error = null) }

    /** Guarda en el teléfono (funciona sin conexión) y pide el envío. [alTerminar] cierra la pantalla. */
    fun guardar(alTerminar: () -> Unit) {
        val actual = _ui.value
        if (!actual.completa || actual.guardando) return
        _ui.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            val resultado = registro.registrarEncuesta(
                asistenciaUuid, actual.satisfaccion, actual.utilidad, actual.comentarios, sub,
            )
            when (resultado) {
                is ResultadoEncuesta.Registrada -> {
                    scheduler.solicitarAhora()
                    alTerminar()
                }
                ResultadoEncuesta.YaRespondida ->
                    _ui.update { it.copy(guardando = false, yaRespondida = true, error = "Esta asistencia ya tiene una encuesta.") }
                ResultadoEncuesta.AsistenciaDesconocida ->
                    _ui.update { it.copy(guardando = false, error = "No se encontró la asistencia.") }
                is ResultadoEncuesta.Invalida ->
                    _ui.update { it.copy(guardando = false, error = resultado.motivo) }
            }
        }
    }
}

/** Fila de 5 botones grandes (1 a 5) para valorar con un toque. */
@Composable
private fun Escala(titulo: String, valor: Int, minimo: String, maximo: String, onCambio: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in RegistroRepository.ESCALA) {
                val seleccionado = n == valor
                val descripcion = "$titulo: $n de 5" + if (seleccionado) ", seleccionado" else ""
                val modificador = Modifier.weight(1f).heightIn(min = 56.dp).semantics { contentDescription = descripcion }
                if (seleccionado) {
                    Button(onClick = { onCambio(n) }, modifier = modificador) { Text("$n") }
                } else {
                    OutlinedButton(onClick = { onCambio(n) }, modifier = modificador) { Text("$n") }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(minimo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(maximo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EncuestaScreen(asistenciaUuid: String, sub: String, onTerminar: () -> Unit) {
    val vm = viewModelDe { c ->
        EncuestaViewModel(
            asistenciaUuid, sub, c.registroRepository,
            c.baseDeDatos.asistenciaDao(), c.baseDeDatos.catalogoDao(), c.syncScheduler,
        )
    }
    val ui by vm.ui.collectAsStateWithLifecycle()

    PantallaBase(titulo = "Encuesta de valoración", onAtras = onTerminar) { relleno ->
        if (!ui.cargado) return@PantallaBase
        if (!ui.existe) {
            EstadoVacio("No se encontró la asistencia de esta encuesta", Modifier.padding(relleno))
            return@PantallaBase
        }
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ui.asistenteCodigo?.let {
                Text("Asistente: $it", style = MaterialTheme.typography.titleLarge)
            }
            Escala("¿Qué tan satisfecho quedó con el evento?", ui.satisfaccion, "Nada satisfecho", "Muy satisfecho", vm::onSatisfaccion)
            Escala("¿Qué tan útil le pareció?", ui.utilidad, "Nada útil", "Muy útil", vm::onUtilidad)
            OutlinedTextField(
                value = ui.comentarios,
                onValueChange = vm::onComentarios,
                label = { Text("Comentarios (opcional)") },
                supportingText = { Text("${ui.comentarios.length} / ${RegistroRepository.MAX_COMENTARIO}") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            BotonPrimario(
                texto = "Guardar encuesta",
                onClick = { vm.guardar(onTerminar) },
                enabled = ui.completa && !ui.yaRespondida,
                cargando = ui.guardando,
            )
            if (!ui.completa) {
                Text(
                    "Elige una nota de 1 a 5 en las dos preguntas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onTerminar, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Omitir por ahora") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
