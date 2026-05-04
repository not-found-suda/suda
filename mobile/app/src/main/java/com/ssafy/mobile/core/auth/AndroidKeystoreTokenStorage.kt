package com.ssafy.mobile.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

class AndroidKeystoreTokenStorage
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : TokenStorage {
        companion object {
            private const val PREFS_NAME = "secure_token_prefs"
            private const val KEY_ACCESS_TOKEN_IV = "access_token_iv"
            private const val KEY_ACCESS_TOKEN_CIPHER = "access_token_cipher"
            private const val KEY_REFRESH_TOKEN_IV = "refresh_token_iv"
            private const val KEY_REFRESH_TOKEN_CIPHER = "refresh_token_cipher"

            private const val ANDROID_KEYSTORE = "AndroidKeyStore"
            private const val KEY_ALIAS = "com.ssafy.mobile.auth.token_key"
            private const val TRANSFORMATION = "AES/GCM/NoPadding"
            private const val GCM_TAG_LENGTH = 128
            private const val KEY_SIZE_BITS = 256
        }

        private val prefs: SharedPreferences =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )
        private val keyStore =
            KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                load(null)
            }

        init {
            createKeyIfNeeded()
        }

        private fun createKeyIfNeeded() {
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator =
                    KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE,
                    )
                val keyGenParameterSpec =
                    KeyGenParameterSpec
                        .Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_SIZE_BITS)
                        .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        }

        private fun getSecretKey(): SecretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey

        private fun encrypt(plainText: String?): Pair<String, String>? {
            if (plainText.isNullOrEmpty()) return null

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            return Pair(
                Base64.encodeToString(iv, Base64.NO_WRAP),
                Base64.encodeToString(cipherText, Base64.NO_WRAP),
            )
        }

        private fun decrypt(
            ivString: String?,
            cipherTextString: String?,
        ): String? {
            if (ivString.isNullOrEmpty() || cipherTextString.isNullOrEmpty()) return null

            return try {
                val iv = Base64.decode(ivString, Base64.NO_WRAP)
                val cipherText = Base64.decode(cipherTextString, Base64.NO_WRAP)

                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

                val plainText = cipher.doFinal(cipherText)
                String(plainText, Charsets.UTF_8)
            } catch (e: java.security.GeneralSecurityException) {
                Log.e("AndroidKeystore", "Decryption failed", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.e("AndroidKeystore", "Base64 decoding failed", e)
                null
            }
        }

        override fun getAccessToken(): String? {
            val iv = prefs.getString(KEY_ACCESS_TOKEN_IV, null)
            val cipher = prefs.getString(KEY_ACCESS_TOKEN_CIPHER, null)
            return decrypt(iv, cipher)
        }

        override fun getRefreshToken(): String? {
            val iv = prefs.getString(KEY_REFRESH_TOKEN_IV, null)
            val cipher = prefs.getString(KEY_REFRESH_TOKEN_CIPHER, null)
            return decrypt(iv, cipher)
        }

        override fun saveTokens(
            accessToken: String,
            refreshToken: String,
        ) {
            val accessEncrypted = encrypt(accessToken)
            val refreshEncrypted = encrypt(refreshToken)

            prefs
                .edit()
                .apply {
                    if (accessEncrypted != null) {
                        putString(KEY_ACCESS_TOKEN_IV, accessEncrypted.first)
                        putString(KEY_ACCESS_TOKEN_CIPHER, accessEncrypted.second)
                    }
                    if (refreshEncrypted != null) {
                        putString(KEY_REFRESH_TOKEN_IV, refreshEncrypted.first)
                        putString(KEY_REFRESH_TOKEN_CIPHER, refreshEncrypted.second)
                    }
                }.apply()
        }

        override fun updateAccessToken(accessToken: String) {
            val accessEncrypted = encrypt(accessToken)
            prefs
                .edit()
                .apply {
                    if (accessEncrypted != null) {
                        putString(KEY_ACCESS_TOKEN_IV, accessEncrypted.first)
                        putString(KEY_ACCESS_TOKEN_CIPHER, accessEncrypted.second)
                    }
                }.apply()
        }

        override fun clearTokens() {
            prefs
                .edit()
                .apply {
                    remove(KEY_ACCESS_TOKEN_IV)
                    remove(KEY_ACCESS_TOKEN_CIPHER)
                    remove(KEY_REFRESH_TOKEN_IV)
                    remove(KEY_REFRESH_TOKEN_CIPHER)
                }.apply()
        }
    }
