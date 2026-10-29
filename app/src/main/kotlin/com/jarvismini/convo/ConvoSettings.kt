package com.jarvismini.convo

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface ConvoSettingsStore {
    var baseUrl: String
    var token: String
}

class InMemoryConvoSettings(
    override var baseUrl: String = DEFAULT_CONVO_BASE_URL,
    override var token: String = "",
) : ConvoSettingsStore

/** Base URL + admin token. The token lives only in EncryptedSharedPreferences; it is never logged. */
class EncryptedConvoSettings(context: Context) : ConvoSettingsStore {

    private val prefs: SharedPreferences? = try {
        val key = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        null
    }

    // Fallback if the keystore is unusable: keep the token in memory for this process only.
    private var memToken: String = ""
    private var memUrl: String = DEFAULT_CONVO_BASE_URL

    override var baseUrl: String
        get() = prefs?.getString(K_URL, null) ?: memUrl
        set(value) {
            memUrl = normalizeBaseUrl(value)
            prefs?.edit()?.putString(K_URL, memUrl)?.apply()
        }

    override var token: String
        get() = prefs?.getString(K_TOKEN, null) ?: memToken
        set(value) {
            memToken = value.trim()
            prefs?.edit()?.putString(K_TOKEN, memToken)?.apply()
        }

    private companion object {
        const val FILE = "convo_secure_prefs"
        const val K_URL = "base_url"
        const val K_TOKEN = "admin_token"
    }
}
