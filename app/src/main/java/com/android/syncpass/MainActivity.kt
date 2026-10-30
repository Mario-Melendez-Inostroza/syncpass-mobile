package com.android.syncpass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.android.syncpass.ui.AppNavegacion
import com.android.syncpass.ui.asistente.AsistenteNavegacion
import com.android.syncpass.ui.asistente.RegistroScreen
import com.android.syncpass.ui.LocalContenedor
import com.android.syncpass.ui.login.LoginScreen
import com.android.syncpass.ui.sesion.EstadoSesion
import com.android.syncpass.ui.sesion.SesionViewModel
import com.android.syncpass.ui.theme.SyncPassTheme
import com.android.syncpass.ui.viewModelDe

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val contenedor = (application as SyncPassApp).contenedor
        setContent {
            val oscuro by contenedor.temaPrefs.oscuro.collectAsStateWithLifecycle()
            SyncPassTheme(oscuro = oscuro) {
                CompositionLocalProvider(LocalContenedor provides contenedor) {
                    Raiz()
                }
            }
        }
    }
}

/**
 * Decide qué se ve según la sesión: cargando, login o la app. Como la decisión sale de los tokens guardados,
 * cerrar sesión (o que el servidor rechace la renovación) devuelve al login sin navegar a mano.
 */
@Composable
private fun Raiz() {
    val sesion = viewModelDe { SesionViewModel(it.authRepository) }
    val estado by sesion.estado.collectAsStateWithLifecycle()
    val contenedor = LocalContenedor.current

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val actual = estado) {
            EstadoSesion.Cargando ->
                Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            EstadoSesion.SinSesion -> {
                var creandoCuenta by rememberSaveable { mutableStateOf(false) }
                if (creandoCuenta) RegistroScreen(onVolver = { creandoCuenta = false })
                else LoginScreen(onCrearCuenta = { creandoCuenta = true })
            }
            is EstadoSesion.Activa -> {
                // Al entrar (o volver a abrir la app con sesión) se envía lo que haya pendiente y se baja el catálogo.
                if (actual.usuario.soloAsistente) {
                    AsistenteNavegacion(actual.usuario)
                } else {
                    LaunchedEffect(actual.usuario.sub) { contenedor.syncScheduler.solicitarAhora() }
                    AppNavegacion(actual.usuario)
                }
            }
        }
    }
}
