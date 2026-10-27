package com.android.syncpass.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Tokens(
    val accessToken: String,
    val idToken: String?,
    val refreshToken: String?,
)

/**
 * Dónde viven los tokens de la sesión. Es una interfaz para poder probar la red y la autenticación en la JVM
 * con un almacén en memoria, sin DataStore ni Keystore.
 */
interface TokenStorage {
    suspend fun guardar(tokens: Tokens, email: String?)

    /** null si no hay sesión. */
    suspend fun tokens(): Tokens?

    suspend fun email(): String?

    suspend fun limpiar()

    /** true mientras haya tokens guardados; pasa a false al cerrar sesión o cuando la renovación falla. */
    fun sesionActiva(): Flow<Boolean>
}

/** Cifra y descifra textos cortos. La clave nunca sale del dispositivo. */
interface SecretCipher {
    fun encrypt(plain: String): String

    /** null si el texto está corrupto o la clave ya no existe (p. ej. se borraron los datos de la app). */
    fun decrypt(token: String): String?
}

/**
 * AES-256-GCM con una clave en Android Keystore. Los tokens se guardan cifrados: el ejemplo del profesor
 * los dejaba en claro, y con el respaldo automático activo salían del teléfono.
 */
class KeystoreSecretCipher(private val alias: String = "syncpass_tokens") : SecretCipher {

    private fun clave(): SecretKey {
        val almacen = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (almacen.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generador.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generador.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.ENCRYPT_MODE, clave())
        val cifrado = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cifrado, Base64.NO_WRAP)
    }

    override fun decrypt(token: String): String? = runCatching {
        val bytes = Base64.decode(token, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val cifrado = bytes.copyOfRange(IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(cifrado), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

private val Context.sesionDataStore by preferencesDataStore(name = "syncpass_sesion")

/** Tokens en DataStore, cifrados con [cipher] antes de escribirlos. */
class DataStoreTokenStorage(
    context: Context,
    private val cipher: SecretCipher,
) : TokenStorage {

    private val store = context.applicationContext.sesionDataStore

    private companion object {
        val ACCESS = stringPreferencesKey("access")
        val ID = stringPreferencesKey("id")
        val REFRESH = stringPreferencesKey("refresh")
        val EMAIL = stringPreferencesKey("email")
    }

    override suspend fun guardar(tokens: Tokens, email: String?) {
        store.edit { prefs ->
            prefs[ACCESS] = cipher.encrypt(tokens.accessToken)
            prefs.poner(ID, tokens.idToken)
            prefs.poner(REFRESH, tokens.refreshToken)
            if (email != null) prefs[EMAIL] = cipher.encrypt(email)
        }
    }

    override suspend fun tokens(): Tokens? = leer(store.data.first())

    override suspend fun email(): String? =
        store.data.first()[EMAIL]?.let(cipher::decrypt)

    override suspend fun limpiar() {
        store.edit { it.clear() }
    }

    override fun sesionActiva(): Flow<Boolean> = store.data.map { leer(it) != null }

    private fun leer(prefs: Preferences): Tokens? {
        val acceso = prefs[ACCESS]?.let(cipher::decrypt) ?: return null
        return Tokens(
            accessToken = acceso,
            idToken = prefs[ID]?.let(cipher::decrypt),
            refreshToken = prefs[REFRESH]?.let(cipher::decrypt),
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.poner(
        clave: Preferences.Key<String>,
        valor: String?,
    ) {
        if (valor != null) this[clave] = cipher.encrypt(valor) else remove(clave)
    }
}
