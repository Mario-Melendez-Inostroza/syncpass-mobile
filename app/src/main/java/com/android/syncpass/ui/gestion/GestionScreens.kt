package com.android.syncpass.ui.gestion

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.syncpass.core.network.AsistenteFilaDto
import com.android.syncpass.core.network.EventoDto
import com.android.syncpass.core.network.ProyectoDto
import com.android.syncpass.ui.Formato
import com.android.syncpass.ui.componentes.BotonPrimario
import com.android.syncpass.ui.componentes.EstadoVacio
import com.android.syncpass.ui.componentes.PantallaBase
import com.android.syncpass.ui.viewModelDe

// ------------------------------------------------------------------ menú

/** Pestaña "Gestión" del coordinador: entrada a los tres mantenedores. */
@Composable
fun GestionScreen(
    onProyectos: () -> Unit,
    onEventos: () -> Unit,
    onAsistentes: () -> Unit,
    barraInferior: @Composable () -> Unit,
) {
    PantallaBase(titulo = "Gestión", barraInferior = barraInferior) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Crea, edita y elimina lo que usa el equipo en terreno. Necesita conexión con el servidor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BotonPrimario("Proyectos", onProyectos, icono = Icons.Default.Work)
            BotonPrimario("Eventos (talleres)", onEventos, icono = Icons.Default.Event)
            BotonPrimario("Asistentes", onAsistentes, icono = Icons.Default.Group)
        }
    }
}

// ------------------------------------------------------------------ piezas compartidas

/** Marco común de un mantenedor: barra con "recargar", botón "Nuevo", avisos y la lista o su estado. */
@Composable
private fun <T> MarcoMantenedor(
    titulo: String,
    textoNuevo: String,
    vacio: String,
    ui: GestionUi<T>,
    onAtras: () -> Unit,
    onRecargar: () -> Unit,
    onAvisoMostrado: () -> Unit,
    onNuevo: () -> Unit,
    fila: @Composable (T) -> Unit,
) {
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(ui.aviso) {
        ui.aviso?.let {
            avisos.showSnackbar(it)
            onAvisoMostrado()
        }
    }
    PantallaBase(
        titulo = titulo,
        onAtras = onAtras,
        avisos = avisos,
        acciones = {
            IconButton(onClick = onRecargar) { Icon(Icons.Default.Refresh, contentDescription = "Recargar") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            BotonPrimario(
                texto = textoNuevo,
                onClick = onNuevo,
                icono = Icons.Default.Add,
                enabled = !ui.cargando,
                modifier = Modifier.padding(16.dp),
            )
            when {
                ui.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                ui.error != null -> EstadoVacio("No se pudo cargar", detalle = ui.error)
                ui.items.isEmpty() -> EstadoVacio(vacio)
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { items(ui.items) { fila(it) } }
            }
        }
    }
}

@Composable
private fun TarjetaFila(
    titulo: String,
    detalle: List<String>,
    onEditar: () -> Unit,
    onEliminar: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            detalle.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEditar) { Text("Editar") }
                TextButton(onClick = onEliminar) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/** Confirmación antes de eliminar: la baja no se puede deshacer. */
@Composable
private fun ConfirmarEliminar(nombre: String, onConfirmar: () -> Unit, onCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("¿Eliminar?") },
        text = { Text("Se eliminará «$nombre». Esta acción no se puede deshacer.") },
        confirmButton = { TextButton(onClick = onConfirmar) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

@Composable
private fun Campo(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    teclado: KeyboardType = KeyboardType.Text,
    ayuda: String? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        supportingText = ayuda?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Elegir una opción entre pocas: fichas en una fila deslizable. */
@Composable
private fun Opciones(titulo: String, opciones: List<Pair<String, String>>, elegida: String?, alElegir: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opciones.forEach { (valor, texto) ->
                FilterChip(selected = valor == elegida, onClick = { alElegir(valor) }, label = { Text(texto) })
            }
        }
    }
}

/** Diálogo de formulario con el error de validación visible y botones Guardar/Cancelar. */
@Composable
private fun DialogoFormulario(
    titulo: String,
    error: String?,
    trabajando: Boolean,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit,
    contenido: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!trabajando) onCancelar() },
        title = { Text(titulo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                contenido()
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onGuardar, enabled = !trabajando) { Text(if (trabajando) "Guardando…" else "Guardar") } },
        dismissButton = { TextButton(onClick = onCancelar, enabled = !trabajando) { Text("Cancelar") } },
    )
}

private fun etiqueta(valor: String?): String = valor?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "—"

// ------------------------------------------------------------------ proyectos

@Composable
fun ProyectosGestionScreen(onAtras: () -> Unit) {
    val vm = viewModelDe { ProyectosViewModel(it.red.syncPassApi, it.red.gestionApi) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editando by remember { mutableStateOf<ProyectoDto?>(null) }
    var creando by remember { mutableStateOf(false) }
    var eliminando by remember { mutableStateOf<ProyectoDto?>(null) }

    MarcoMantenedor(
        titulo = "Proyectos", textoNuevo = "Nuevo proyecto", vacio = "Todavía no hay proyectos",
        ui = ui, onAtras = onAtras, onRecargar = vm::cargar, onAvisoMostrado = vm::avisoMostrado,
        onNuevo = { creando = true },
    ) { p ->
        TarjetaFila(
            titulo = p.titulo.orEmpty(),
            detalle = listOf(
                "${p.areaTematica.orEmpty()} · ${p.unidadVecinal.orEmpty()}",
                "${p.fechaInicio ?: "—"} → ${p.fechaTermino ?: "sin término"} · ${etiqueta(p.estado)}",
            ),
            onEditar = { editando = p },
            onEliminar = { eliminando = p },
        )
    }

    if (creando || editando != null) {
        val actual = editando
        var form by remember(actual) {
            mutableStateOf(
                actual?.let {
                    FormProyecto(
                        it.titulo.orEmpty(), it.descripcion.orEmpty(), it.areaTematica.orEmpty(), it.unidadVecinal.orEmpty(),
                        it.fechaInicio.orEmpty(), it.fechaTermino.orEmpty(), it.estado ?: "PLANIFICADO",
                    )
                } ?: FormProyecto(),
            )
        }
        var error by remember(actual) { mutableStateOf<String?>(null) }
        val cerrar = { creando = false; editando = null }
        DialogoFormulario(
            titulo = if (actual == null) "Nuevo proyecto" else "Editar proyecto",
            error = error, trabajando = ui.trabajando, onCancelar = cerrar,
            onGuardar = {
                when (val v = validarProyecto(form)) {
                    is Validacion.Error -> error = v.mensaje
                    is Validacion.Ok -> {
                        error = null
                        val id = actual?.id
                        if (id == null) vm.crear(v.valor, cerrar) else vm.editar(id, v.valor, cerrar)
                    }
                }
            },
        ) {
            Campo(form.titulo, { form = form.copy(titulo = it) }, "Título")
            Campo(form.descripcion, { form = form.copy(descripcion = it) }, "Descripción (opcional)")
            Campo(form.areaTematica, { form = form.copy(areaTematica = it) }, "Área temática")
            Campo(form.unidadVecinal, { form = form.copy(unidadVecinal = it) }, "Unidad vecinal")
            Campo(form.fechaInicio, { form = form.copy(fechaInicio = it) }, "Fecha de inicio", ayuda = "AAAA-MM-DD")
            Campo(form.fechaTermino, { form = form.copy(fechaTermino = it) }, "Fecha de término (opcional)", ayuda = "AAAA-MM-DD")
            Opciones("Estado", ESTADOS_PROYECTO.map { it to etiqueta(it) }, form.estado) { form = form.copy(estado = it) }
        }
    }

    eliminando?.let { p ->
        ConfirmarEliminar(p.titulo.orEmpty(), onConfirmar = { p.id?.let(vm::eliminar); eliminando = null }, onCancelar = { eliminando = null })
    }
}

// ------------------------------------------------------------------ eventos

@Composable
fun EventosGestionScreen(onAtras: () -> Unit) {
    val vm = viewModelDe { EventosGestionViewModel(it.red.syncPassApi, it.red.gestionApi) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val proyectos by vm.proyectos.collectAsStateWithLifecycle()
    var editando by remember { mutableStateOf<EventoDto?>(null) }
    var creando by remember { mutableStateOf(false) }
    var eliminando by remember { mutableStateOf<EventoDto?>(null) }

    MarcoMantenedor(
        titulo = "Eventos", textoNuevo = "Nuevo evento", vacio = "Todavía no hay eventos",
        ui = ui, onAtras = onAtras, onRecargar = vm::cargar, onAvisoMostrado = vm::avisoMostrado,
        onNuevo = { creando = true },
    ) { e ->
        TarjetaFila(
            titulo = e.nombre.orEmpty(),
            detalle = listOf(
                proyectos.firstOrNull { it.id == e.proyectoId }?.titulo.orEmpty(),
                "${Formato.fechaHoraIso(e.fechaHoraProgramada)} · ${e.recinto ?: "sin recinto"}",
                "Cupos: ${e.cuposMaximos ?: "sin límite"} · Quórum mínimo: ${e.quorumMinimo ?: "—"}",
            ).filter { it.isNotBlank() },
            onEditar = { editando = e },
            onEliminar = { eliminando = e },
        )
    }

    if (creando || editando != null) {
        val actual = editando
        var form by remember(actual) {
            mutableStateOf(
                actual?.let {
                    FormEvento(
                        proyectoId = it.proyectoId, nombre = it.nombre.orEmpty(),
                        fechaHora = fechaHoraParaFormulario(it.fechaHoraProgramada),
                        cuposMaximos = it.cuposMaximos?.toString().orEmpty(), quorumMinimo = it.quorumMinimo?.toString().orEmpty(),
                        recinto = it.recinto.orEmpty(), latitud = it.latitud?.toString().orEmpty(),
                        longitud = it.longitud?.toString().orEmpty(), radioMetros = it.radioMetros?.toString().orEmpty(),
                    )
                } ?: FormEvento(radioMetros = "200"),
            )
        }
        var error by remember(actual) { mutableStateOf<String?>(null) }
        val cerrar = { creando = false; editando = null }
        DialogoFormulario(
            titulo = if (actual == null) "Nuevo evento" else "Editar evento",
            error = error, trabajando = ui.trabajando, onCancelar = cerrar,
            onGuardar = {
                when (val v = validarEvento(form)) {
                    is Validacion.Error -> error = v.mensaje
                    is Validacion.Ok -> {
                        error = null
                        val id = actual?.id
                        if (id == null) vm.crear(v.valor, cerrar) else vm.editar(id, v.valor, cerrar)
                    }
                }
            },
        ) {
            Opciones(
                "Proyecto", proyectos.mapNotNull { p -> p.id?.let { it.toString() to p.titulo.orEmpty() } },
                form.proyectoId?.toString(),
            ) { form = form.copy(proyectoId = it.toLongOrNull()) }
            Campo(form.nombre, { form = form.copy(nombre = it) }, "Nombre del evento")
            Campo(form.fechaHora, { form = form.copy(fechaHora = it) }, "Fecha y hora", ayuda = "DD/MM/AAAA HH:MM (hora del teléfono)")
            Campo(form.cuposMaximos, { form = form.copy(cuposMaximos = it) }, "Cupos máximos (opcional)", KeyboardType.Number)
            Campo(form.quorumMinimo, { form = form.copy(quorumMinimo = it) }, "Quórum mínimo (opcional)", KeyboardType.Number)
            Campo(form.recinto, { form = form.copy(recinto = it) }, "Recinto (opcional)")
            Campo(form.latitud, { form = form.copy(latitud = it) }, "Latitud (opcional)", KeyboardType.Decimal)
            Campo(form.longitud, { form = form.copy(longitud = it) }, "Longitud (opcional)", KeyboardType.Decimal)
            Campo(form.radioMetros, { form = form.copy(radioMetros = it) }, "Radio en metros", KeyboardType.Number)
        }
    }

    eliminando?.let { e ->
        ConfirmarEliminar(e.nombre.orEmpty(), onConfirmar = { e.id?.let(vm::eliminar); eliminando = null }, onCancelar = { eliminando = null })
    }
}

// ------------------------------------------------------------------ asistentes

@Composable
fun AsistentesGestionScreen(onAtras: () -> Unit) {
    val vm = viewModelDe { AsistentesGestionViewModel(it.red.gestionApi) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editando by remember { mutableStateOf<AsistenteFilaDto?>(null) }
    var creando by remember { mutableStateOf(false) }
    var eliminando by remember { mutableStateOf<AsistenteFilaDto?>(null) }

    MarcoMantenedor(
        titulo = "Asistentes", textoNuevo = "Nuevo asistente", vacio = "Todavía no hay asistentes",
        ui = ui, onAtras = onAtras, onRecargar = vm::cargar, onAvisoMostrado = vm::avisoMostrado,
        onNuevo = { creando = true },
    ) { a ->
        TarjetaFila(
            titulo = a.codigo.orEmpty(),
            detalle = listOf(
                "${etiqueta(a.tipo)} · ${etiqueta(a.estadoInscripcion)}",
                "Marcado en ${a.totalAsistencias ?: 0} evento(s)" + if (a.creadoEnTerreno == true) " · alta en terreno" else "",
            ),
            onEditar = { editando = a },
            onEliminar = { eliminando = a },
        )
    }

    if (creando || editando != null) {
        val actual = editando
        var tipo by remember(actual) { mutableStateOf(actual?.tipo ?: "VECINO") }
        var estado by remember(actual) { mutableStateOf(actual?.estadoInscripcion ?: "INSCRITO") }
        var cantidad by remember(actual) { mutableStateOf("1") }
        var error by remember(actual) { mutableStateOf<String?>(null) }
        val cerrar = { creando = false; editando = null }
        DialogoFormulario(
            titulo = if (actual == null) "Nuevo asistente" else "Editar ${actual.codigo.orEmpty()}",
            error = error, trabajando = ui.trabajando, onCancelar = cerrar,
            onGuardar = {
                val id = actual?.id
                if (id != null) {
                    error = null
                    vm.editar(id, tipo, estado, cerrar)
                } else {
                    val n = cantidad.trim().toIntOrNull()
                    if (n == null || n !in 1..200) error = "La cantidad debe ser un número entre 1 y 200."
                    else {
                        error = null
                        vm.crear(tipo, estado, n, cerrar)
                    }
                }
            },
        ) {
            Opciones("Tipo", TIPOS_PARTICIPANTE.map { it to etiqueta(it) }, tipo) { tipo = it }
            Opciones("Inscripción", ESTADOS_INSCRIPCION.map { it to etiqueta(it) }, estado) { estado = it }
            if (actual == null) {
                Campo(cantidad, { cantidad = it }, "Cantidad", KeyboardType.Number, ayuda = "El servidor genera el código de cada uno")
            } else {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Código: ${actual.codigo.orEmpty()} (no editable)") }
            }
        }
    }

    eliminando?.let { a ->
        ConfirmarEliminar(a.codigo.orEmpty(), onConfirmar = { a.id?.let(vm::eliminar); eliminando = null }, onCancelar = { eliminando = null })
    }
}
