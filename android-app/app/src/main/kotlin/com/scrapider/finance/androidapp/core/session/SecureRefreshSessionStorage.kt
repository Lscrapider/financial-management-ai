package com.scrapider.finance.androidapp.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** 仅保存服务端 refresh_sid，密钥由 Android Keystore 托管。 */
internal class SecureRefreshSessionStorage(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): String? {
        val encryptedValue = preferences.getString(KEY_REFRESH_SID, null) ?: return null
        val refreshSid = runCatching { decrypt(encryptedValue) }
            .getOrElse {
                clear()
                return null
            }
        if (refreshSid.isBlank()) {
            clear()
            return null
        }
        return refreshSid
    }

    @Synchronized
    fun save(refreshSid: String): Boolean {
        if (refreshSid.isBlank()) {
            clear()
            return false
        }
        return runCatching {
            preferences.edit()
                .putString(KEY_REFRESH_SID, encrypt(refreshSid))
                .commit()
        }.getOrElse {
            clear()
            false
        }
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY_REFRESH_SID).commit()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + PAYLOAD_SEPARATOR +
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val separatorIndex = value.indexOf(PAYLOAD_SEPARATOR)
        require(separatorIndex > 0 && separatorIndex < value.lastIndex) { "Invalid refresh session payload" }
        val initializationVector = Base64.decode(value.substring(0, separatorIndex), Base64.NO_WRAP)
        val encryptedBytes = Base64.decode(value.substring(separatorIndex + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH_BITS, initializationVector),
        )
        return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: createSecretKey()
    }

    private fun createSecretKey(): SecretKey = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        ANDROID_KEYSTORE,
    ).apply {
        init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
    }.generateKey()

    private companion object {
        const val PREFERENCES_NAME = "finance_android_refresh_session"
        const val KEY_REFRESH_SID = "refresh_sid"
        const val KEY_ALIAS = "finance_android_refresh_session_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val PAYLOAD_SEPARATOR = '.'
    }
}
