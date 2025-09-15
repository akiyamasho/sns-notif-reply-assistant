package com.example.snsassistant.data.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.snsassistant.data.openai.ApiKeyProvider

class SecurePrefs(context: Context) : ApiKeyProvider {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun getApiKey(): String? = prefs.getString(KEY_API, null)

    fun getDiscordBotToken(): String? = prefs.getString(KEY_DISCORD_TOKEN, null)
    fun getDiscordChannelId(): String? = prefs.getString(KEY_DISCORD_CHANNEL, null)

    fun setApiKey(key: String?) {
        prefs.edit().apply {
            if (key.isNullOrBlank()) remove(KEY_API) else putString(KEY_API, key)
        }.apply()
    }

    fun setDiscordBotToken(token: String?) {
        prefs.edit().apply {
            if (token.isNullOrBlank()) remove(KEY_DISCORD_TOKEN) else putString(KEY_DISCORD_TOKEN, token)
        }.apply()
    }

    fun setDiscordChannelId(channelId: String?) {
        prefs.edit().apply {
            if (channelId.isNullOrBlank()) remove(KEY_DISCORD_CHANNEL) else putString(KEY_DISCORD_CHANNEL, channelId)
        }.apply()
    }

    companion object {
        private const val KEY_API = "openai_api_key"
        private const val KEY_DISCORD_TOKEN = "discord_bot_token"
        private const val KEY_DISCORD_CHANNEL = "discord_channel_id"
    }
}
