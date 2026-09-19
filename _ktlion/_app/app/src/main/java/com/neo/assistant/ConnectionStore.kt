package com.neo.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The phone's API pairing token is encrypted using an Android Keystore key. */
class ConnectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("neo_connection", Context.MODE_PRIVATE)
    val url: String get() = prefs.getString("url", "http://127.0.0.1:8765")!!
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("neo_pairing", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("neo_pairing", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun token(): String {
        val encrypted = prefs.getString("token", null) ?: return ""
        val iv = prefs.getString("iv", null) ?: return ""
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
                String(doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
            }
        } catch (_: Exception) { "" } // A lost Keystore key requires pairing again.
    }
    fun save(url: String, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("url", url.trim().trimEnd('/'))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }
}
