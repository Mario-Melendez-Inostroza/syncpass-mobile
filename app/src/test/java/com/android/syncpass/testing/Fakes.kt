package com.android.syncpass.testing

import com.android.syncpass.core.auth.TokenStorage
import com.android.syncpass.core.auth.Tokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.Base64

/** Almacén de tokens en memoria: sustituye a DataStore y Keystore en las pruebas de la JVM. */
class FakeTokenStorage(inicial: Tokens? = null, email: String? = null) : TokenStorage {
    private val actuales = MutableStateFlow(inicial)
    private var correo: String? = email

    override suspend fun guardar(tokens: Tokens, email: String?) {
        actuales.value = tokens
        if (email != null) correo = email
    }

    override suspend fun tokens(): Tokens? = actuales.value
    override suspend fun email(): String? = correo
    override suspend fun limpiar() {
        actuales.value = null
        correo = null
    }

    override fun sesionActiva(): Flow<Boolean> = actuales.map { it != null }
}

/** Construye un JWT con los claims dados. La firma es falsa: la app no la verifica (lo hace el servidor). */
fun jwtDePrueba(
    sub: String = "sub-de-prueba",
    email: String? = "monitor.demo@example.com",
    nombre: String? = "Monitor Demo",
    grupos: List<String>? = listOf("MONITOR"),
    exp: Long = 4_102_444_800L,
): String {
    val enc = Base64.getUrlEncoder().withoutPadding()
    fun b64(texto: String) = enc.encodeToString(texto.toByteArray(Charsets.UTF_8))

    val campos = mutableListOf("\"sub\":\"$sub\"", "\"exp\":$exp")
    if (email != null) campos += "\"email\":\"$email\""
    if (nombre != null) campos += "\"name\":\"$nombre\""
    if (grupos != null) campos += "\"cognito:groups\":[${grupos.joinToString(",") { "\"$it\"" }}]"

    return "${b64("{\"alg\":\"none\"}")}.${b64("{${campos.joinToString(",")}}")}.firma"
}
