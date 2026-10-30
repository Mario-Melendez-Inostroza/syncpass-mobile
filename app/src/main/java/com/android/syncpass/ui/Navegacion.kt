package com.android.syncpass.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.android.syncpass.core.auth.UserInfo
import com.android.syncpass.ui.asistencia.EscanerScreen
import com.android.syncpass.ui.asistencia.ListaAsistentesScreen
import com.android.syncpass.ui.coordinador.CredencialesScreen
import com.android.syncpass.ui.coordinador.ResumenScreen
import com.android.syncpass.ui.encuesta.EncuestaScreen
import com.android.syncpass.ui.gestion.AsistentesGestionScreen
import com.android.syncpass.ui.gestion.EventosGestionScreen
import com.android.syncpass.ui.gestion.GestionScreen
import com.android.syncpass.ui.gestion.ProyectosGestionScreen
import com.android.syncpass.ui.eventos.EventoDetalleScreen
import com.android.syncpass.ui.eventos.EventosScreen
import com.android.syncpass.ui.perfil.PerfilScreen
import com.android.syncpass.ui.sync.SincronizacionScreen

/** Rutas de la app. Las pestañas principales son EVENTOS, SYNC y PERFIL; el resto cuelga de ellas. */
object Rutas {
    const val EVENTOS = "eventos"
    const val SYNC = "sincronizacion"
    const val PERFIL = "perfil"
    const val CREDENCIALES = "credenciales"
    const val GESTION = "gestion"
    const val GESTION_PROYECTOS = "gestion/proyectos"
    const val GESTION_EVENTOS = "gestion/eventos"
    const val GESTION_ASISTENTES = "gestion/asistentes"

    const val ARG_EVENTO = "eventoId"
    const val ARG_ASISTENCIA = "asistenciaUuid"

    const val DETALLE = "evento/{$ARG_EVENTO}"
    const val ESCANEAR = "escanear/{$ARG_EVENTO}"
    const val LISTA = "lista/{$ARG_EVENTO}"
    const val RESUMEN = "resumen/{$ARG_EVENTO}"
    const val ENCUESTA = "encuesta/{$ARG_ASISTENCIA}"

    fun detalle(eventoId: Long) = "evento/$eventoId"
    fun escanear(eventoId: Long) = "escanear/$eventoId"
    fun lista(eventoId: Long) = "lista/$eventoId"
    fun resumen(eventoId: Long) = "resumen/$eventoId"
    fun encuesta(asistenciaUuid: String) = "encuesta/$asistenciaUuid"
}

private data class Pestana(val ruta: String, val titulo: String, val icono: ImageVector)

private val pestanas = listOf(
    Pestana(Rutas.EVENTOS, "Eventos", Icons.Default.Event),
    Pestana(Rutas.SYNC, "Sincronización", Icons.Default.Sync),
    Pestana(Rutas.PERFIL, "Perfil", Icons.Default.Person),
)

private val pestanaGestion = Pestana(Rutas.GESTION, "Gestión", Icons.Default.Settings)

/** Las pestañas de la barra: "Gestión" (los mantenedores) solo existe para el coordinador. */
private fun pestanasPara(usuario: UserInfo): List<Pestana> =
    if (usuario.esCoordinador) pestanas.take(2) + pestanaGestion + pestanas.drop(2) else pestanas

@Composable
private fun BarraInferior(actual: String, nav: NavHostController, usuario: UserInfo) {
    NavigationBar {
        pestanasPara(usuario).forEach { pestana ->
            NavigationBarItem(
                selected = pestana.ruta == actual,
                onClick = {
                    nav.navigate(pestana.ruta) {
                        // Una sola copia de cada pestaña, conservando su estado al volver a ella.
                        popUpTo(Rutas.EVENTOS) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(pestana.icono, contentDescription = null) },
                label = { Text(pestana.titulo) },
            )
        }
    }
}

@Composable
fun AppNavegacion(usuario: UserInfo) {
    val nav = rememberNavController()
    val sub = usuario.sub.orEmpty()

    fun barra(actual: String): @Composable () -> Unit = { BarraInferior(actual, nav, usuario) }
    val atras: () -> Unit = { nav.popBackStack() }
    val argEvento = listOf(navArgument(Rutas.ARG_EVENTO) { type = NavType.LongType })

    NavHost(nav, startDestination = Rutas.EVENTOS) {
        composable(Rutas.EVENTOS) {
            EventosScreen(
                usuario = usuario,
                onEvento = { nav.navigate(Rutas.detalle(it)) },
                onCredenciales = { nav.navigate(Rutas.CREDENCIALES) },
                barraInferior = barra(Rutas.EVENTOS),
            )
        }
        composable(Rutas.SYNC) { SincronizacionScreen(sub, barra(Rutas.SYNC)) }
        composable(Rutas.PERFIL) { PerfilScreen(usuario, barra(Rutas.PERFIL)) }
        composable(Rutas.CREDENCIALES) { CredencialesScreen(atras) }
        composable(Rutas.GESTION) {
            GestionScreen(
                onProyectos = { nav.navigate(Rutas.GESTION_PROYECTOS) },
                onEventos = { nav.navigate(Rutas.GESTION_EVENTOS) },
                onAsistentes = { nav.navigate(Rutas.GESTION_ASISTENTES) },
                barraInferior = barra(Rutas.GESTION),
            )
        }
        composable(Rutas.GESTION_PROYECTOS) { ProyectosGestionScreen(atras) }
        composable(Rutas.GESTION_EVENTOS) { EventosGestionScreen(atras) }
        composable(Rutas.GESTION_ASISTENTES) { AsistentesGestionScreen(atras) }

        composable(Rutas.DETALLE, arguments = argEvento) { entrada ->
            val id = entrada.arguments!!.getLong(Rutas.ARG_EVENTO)
            EventoDetalleScreen(
                eventoId = id,
                usuario = usuario,
                onAtras = atras,
                onEscanear = { nav.navigate(Rutas.escanear(id)) },
                onLista = { nav.navigate(Rutas.lista(id)) },
                onEncuesta = { nav.navigate(Rutas.encuesta(it)) },
                onResumen = { nav.navigate(Rutas.resumen(id)) },
            )
        }
        composable(Rutas.ESCANEAR, arguments = argEvento) { entrada ->
            val id = entrada.arguments!!.getLong(Rutas.ARG_EVENTO)
            EscanerScreen(
                eventoId = id,
                sub = sub,
                onAtras = atras,
                onLista = { nav.navigate(Rutas.lista(id)) },
                onEncuesta = { nav.navigate(Rutas.encuesta(it)) },
            )
        }
        composable(Rutas.LISTA, arguments = argEvento) { entrada ->
            val id = entrada.arguments!!.getLong(Rutas.ARG_EVENTO)
            ListaAsistentesScreen(id, sub, onAtras = atras, onEncuesta = { nav.navigate(Rutas.encuesta(it)) })
        }
        composable(Rutas.RESUMEN, arguments = argEvento) { entrada ->
            ResumenScreen(entrada.arguments!!.getLong(Rutas.ARG_EVENTO), atras)
        }
        composable(
            Rutas.ENCUESTA,
            arguments = listOf(navArgument(Rutas.ARG_ASISTENCIA) { type = NavType.StringType }),
        ) { entrada ->
            EncuestaScreen(entrada.arguments!!.getString(Rutas.ARG_ASISTENCIA).orEmpty(), sub, onTerminar = atras)
        }
    }
}
