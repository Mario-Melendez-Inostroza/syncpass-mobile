package com.android.syncpass.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.android.syncpass.core.auth.AuthRepository
import com.android.syncpass.ui.componentes.BotonPrimario
import com.android.syncpass.ui.componentes.EncabezadoMarca
import com.android.syncpass.ui.viewModelDe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUi(
    val email: String = "",
    val password: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _ui = MutableStateFlow(LoginUi())
    val ui: StateFlow<LoginUi> = _ui.asStateFlow()

    fun onEmail(valor: String) = _ui.update { it.copy(email = valor, error = null) }

    fun onPassword(valor: String) = _ui.update { it.copy(password = valor, error = null) }

    /** Al terminar bien no hay que navegar: SesionViewModel detecta los tokens guardados y cambia la pantalla. */
    fun iniciarSesion() {
        val actual = _ui.value
        if (actual.cargando) return
        val correo = actual.email.trim()
        if (!correo.contains("@") || actual.password.isEmpty()) {
            _ui.update { it.copy(error = "Escribe tu correo y tu contraseña.") }
            return
        }
        _ui.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val resultado = auth.iniciarSesion(correo, actual.password)
            _ui.update {
                it.copy(
                    cargando = false,
                    // La contraseña no se conserva en memoria más de lo necesario.
                    password = if (resultado.isSuccess) "" else it.password,
                    error = resultado.exceptionOrNull()?.message,
                )
            }
        }
    }
}

@Composable
fun LoginScreen(onCrearCuenta: () -> Unit = {}) {
    val vm = viewModelDe { LoginViewModel(it.authRepository) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var verPassword by rememberSaveable { mutableStateOf(false) }
    val foco = LocalFocusManager.current

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState())) {
        EncabezadoMarca("Registro de asistencia en terreno, con o sin conexión")
        Column(
            Modifier.navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Iniciar sesión", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
            Text(
                "Entra con tu correo y contraseña",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = ui.email,
                onValueChange = vm::onEmail,
                label = { Text("Correo") },
                singleLine = true,
                enabled = !ui.cargando,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.password,
                onValueChange = vm::onPassword,
                label = { Text("Contraseña") },
                singleLine = true,
                enabled = !ui.cargando,
                visualTransformation = if (verPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    foco.clearFocus()
                    vm.iniciarSesion()
                }),
                trailingIcon = {
                    IconButton(onClick = { verPassword = !verPassword }) {
                        Icon(
                            if (verPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (verPassword) "Ocultar contraseña" else "Mostrar contraseña",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (ui.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(ui.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
            BotonPrimario(
                texto = "Iniciar sesión",
                onClick = {
                    foco.clearFocus()
                    vm.iniciarSesion()
                },
                cargando = ui.cargando,
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextButton(onClick = onCrearCuenta, enabled = !ui.cargando) {
                Text("Soy asistente · Crear mi cuenta")
            }
        }
        }
    }
}
