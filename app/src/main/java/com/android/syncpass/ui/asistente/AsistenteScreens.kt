package com.android.syncpass.ui.asistente

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.android.syncpass.core.auth.AuthRepository
import com.android.syncpass.core.auth.UserInfo
import com.android.syncpass.core.network.ApiErrors
import com.android.syncpass.core.network.MiCuentaApi
import com.android.syncpass.core.network.MiPerfilDto
import com.android.syncpass.core.network.TallerDto
import com.android.syncpass.core.qr.CodigoQr
import com.android.syncpass.core.qr.QrBitmap
import com.android.syncpass.ui.Formato
import com.android.syncpass.ui.componentes.BotonPrimario
import com.android.syncpass.ui.componentes.EncabezadoMarca
import com.android.syncpass.ui.componentes.EstadoVacio
import com.android.syncpass.ui.componentes.PantallaBase
import com.android.syncpass.ui.perfil.PerfilScreen
import com.android.syncpass.ui.viewModelDe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tipos de participante del caso (§3.2), con el texto que ve la persona al registrarse. */
private val TIPOS = listOf("VECINO" to "Vecino o vecina", "ESTUDIANTE" to "Estudiante", "DOCENTE" to "Docente")

// ------------------------------------------------------------------ registro

data class RegistroUi(
    val email: String = "",
    val password: String = "",
    val tipo: String = "VECINO",
    val cargando: Boolean = false,
    val error: String? = null,
)

/**
 * Crear la cuenta de asistente (caso §3.1). Solo correo, contraseña y tipo: el servidor no guarda nombre ni correo,
 * solo un código sintético (§5.1). Al terminar bien se inicia sesión sola y la app cambia de pantalla.
 */
class RegistroViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _ui = MutableStateFlow(RegistroUi())
    val ui: StateFlow<RegistroUi> = _ui.asStateFlow()

    fun onEmail(v: String) = _ui.update { it.copy(email = v, error = null) }
    fun onPassword(v: String) = _ui.update { it.copy(password = v, error = null) }
    fun onTipo(v: String) = _ui.update { it.copy(tipo = v, error = null) }

    fun crear() {
        val actual = _ui.value
        if (actual.cargando) return
        val correo = actual.email.trim()
        when {
            !correo.contains("@") -> return _ui.update { it.copy(error = "Escribe un correo válido.") }
            actual.password.length < 8 -> return _ui.update { it.copy(error = "La contraseña debe tener al menos 8 caracteres.") }
        }
        _ui.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val resultado = auth.registrarAsistente(correo, actual.password, actual.tipo)
            _ui.update {
                it.copy(
                    cargando = false,
                    password = if (resultado.isSuccess) "" else it.password,
                    error = resultado.exceptionOrNull()?.message,
                )
            }
        }
    }
}

@Composable
fun RegistroScreen(onVolver: () -> Unit) {
    val vm = viewModelDe { RegistroViewModel(it.authRepository) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState())) {
        EncabezadoMarca("Crea tu cuenta para ver los talleres, inscribirte y mostrar tu código QR al llegar")
        Column(
            Modifier.navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Crear mi cuenta", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = ui.email,
                onValueChange = vm::onEmail,
                label = { Text("Correo") },
                singleLine = true,
                enabled = !ui.cargando,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.password,
                onValueChange = vm::onPassword,
                label = { Text("Contraseña (mínimo 8 caracteres)") },
                singleLine = true,
                enabled = !ui.cargando,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text("Soy…", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
            TIPOS.forEach { (valor, texto) ->
                Row(
                    Modifier.fillMaxWidth().height(48.dp)
                        .selectable(selected = ui.tipo == valor, enabled = !ui.cargando, role = Role.RadioButton) { vm.onTipo(valor) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = ui.tipo == valor, onClick = null)
                    Text("  $texto", style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (ui.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(ui.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))
            BotonPrimario(texto = "Crear cuenta", onClick = vm::crear, cargando = ui.cargando)
            Spacer(Modifier.height(8.dp))
            Text(
                "No guardamos tu nombre: te identificamos con un código y un QR.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onVolver, enabled = !ui.cargando) { Text("Ya tengo cuenta · Iniciar sesión") }
        }
        }
    }
}

// ------------------------------------------------------------------ talleres

data class TalleresUi(
    val cargando: Boolean = true,
    val talleres: List<TallerDto> = emptyList(),
    val error: String? = null,
    val trabajando: Long? = null,
    val aviso: String? = null,
)

class TalleresViewModel(private val api: MiCuentaApi) : ViewModel() {
    private val _ui = MutableStateFlow(TalleresUi())
    val ui: StateFlow<TalleresUi> = _ui.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _ui.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val datos = api.talleres().data
                _ui.update { it.copy(cargando = false, talleres = datos.orEmpty()) }
            } catch (e: Exception) {
                _ui.update { it.copy(cargando = false, error = ApiErrors.describir(e)) }
            }
        }
    }

    /** Inscribe o cancela según el estado actual del taller; el servidor valida cupos y duplicados. */
    fun cambiarInscripcion(taller: TallerDto) {
        if (_ui.value.trabajando != null) return
        _ui.update { it.copy(trabajando = taller.id, aviso = null) }
        viewModelScope.launch {
            val aviso = try {
                if (taller.inscrito == true) {
                    api.cancelar(taller.id ?: 0)
                    "Inscripción cancelada."
                } else {
                    api.inscribir(taller.id ?: 0)
                    "¡Listo! Quedaste inscrito en ${taller.nombre}."
                }
            } catch (e: Exception) {
                // Un 409 trae el motivo ("sin cupos", "ya estás inscrito"): se muestra tal cual.
                (e as? retrofit2.HttpException)?.let { ApiErrors.cuerpo(it)?.mensaje } ?: ApiErrors.describir(e)
            }
            _ui.update { it.copy(trabajando = null, aviso = aviso) }
            cargar()
        }
    }

    fun avisoMostrado() = _ui.update { it.copy(aviso = null) }
}

@Composable
private fun TarjetaTaller(t: TallerDto, trabajando: Boolean, onCambiar: () -> Unit) {
    val inscrito = t.inscrito == true
    val libres = t.cuposDisponibles
    val sinCupos = libres != null && libres <= 0 && !inscrito
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t.nombre.orEmpty(), style = MaterialTheme.typography.titleMedium)
            Text(t.proyecto.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Formato.fechaHoraIso(t.fechaHoraProgramada) + (t.recinto?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                when {
                    libres == null -> "Sin límite de cupos"
                    sinCupos -> "Sin cupos disponibles"
                    else -> "$libres cupo(s) disponible(s)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (sinCupos) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (inscrito) {
                Text("✔ Estás inscrito", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            if (inscrito) {
                OutlinedButton(onClick = onCambiar, enabled = !trabajando, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(if (trabajando) "Un momento…" else "Cancelar inscripción")
                }
            } else {
                BotonPrimario("Inscribirme", onClick = onCambiar, enabled = !sinCupos, cargando = trabajando)
            }
        }
    }
}

@Composable
private fun TalleresScreen(barra: @Composable () -> Unit) {
    val vm = viewModelDe { TalleresViewModel(it.red.miCuentaApi) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(ui.aviso) {
        ui.aviso?.let {
            avisos.showSnackbar(it)
            vm.avisoMostrado()
        }
    }

    PantallaBase(
        titulo = "Talleres",
        avisos = avisos,
        barraInferior = barra,
        acciones = {
            IconButton(onClick = vm::cargar, enabled = !ui.cargando) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
        },
    ) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno)) {
            when {
                ui.cargando && ui.talleres.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.error != null && ui.talleres.isEmpty() -> EstadoVacio("No se pudieron cargar los talleres", detalle = ui.error)
                ui.talleres.isEmpty() -> EstadoVacio("Todavía no hay talleres próximos")
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(ui.talleres, key = { it.id ?: 0L }) { t ->
                        TarjetaTaller(t, trabajando = ui.trabajando == t.id, onCambiar = { vm.cambiarInscripcion(t) })
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ credencial

data class CredencialUi(val cargando: Boolean = true, val perfil: MiPerfilDto? = null, val error: String? = null)

class CredencialViewModel(private val api: MiCuentaApi) : ViewModel() {
    private val _ui = MutableStateFlow(CredencialUi())
    val ui: StateFlow<CredencialUi> = _ui.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _ui.update { CredencialUi(cargando = true) }
        viewModelScope.launch {
            try {
                _ui.update { CredencialUi(cargando = false, perfil = api.perfil().data) }
            } catch (e: Exception) {
                _ui.update { CredencialUi(cargando = false, error = ApiErrors.describir(e)) }
            }
        }
    }
}

@Composable
private fun CredencialScreen(barra: @Composable () -> Unit) {
    val vm = viewModelDe { CredencialViewModel(it.red.miCuentaApi) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    PantallaBase(titulo = "Mi credencial", barraInferior = barra) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno)) {
            val perfil = ui.perfil
            val id = perfil?.id
            when {
                ui.cargando -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.error != null || perfil == null || id == null || id <= 0 ->
                    EstadoVacio("No se pudo cargar tu credencial", detalle = ui.error)
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    val imagen = remember(id) { QrBitmap.generar(CodigoQr.generarAsistente(id), 768).asImageBitmap() }
                    Image(imagen, contentDescription = "Mi código QR ${perfil.codigo}", modifier = Modifier.size(280.dp))
                    Text(perfil.codigo.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Muéstraselo al monitor al llegar al taller.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ navegación del asistente

/** Lo que ve quien entra con una cuenta de asistente: sus talleres, su QR y su perfil. */
@Composable
fun AsistenteNavegacion(usuario: UserInfo) {
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    val barra: @Composable () -> Unit = {
        NavigationBar {
            listOf(
                Triple("Talleres", Icons.Default.Event, 0),
                Triple("Mi credencial", Icons.Default.QrCode2, 1),
                Triple("Perfil", Icons.Default.Person, 2),
            ).forEach { (titulo, icono, indice) ->
                NavigationBarItem(
                    selected = pestana == indice,
                    onClick = { pestana = indice },
                    icon = { Icon(icono, contentDescription = null) },
                    label = { Text(titulo) },
                )
            }
        }
    }
    when (pestana) {
        0 -> TalleresScreen(barra)
        1 -> CredencialScreen(barra)
        else -> PerfilScreen(usuario, barra)
    }
}
