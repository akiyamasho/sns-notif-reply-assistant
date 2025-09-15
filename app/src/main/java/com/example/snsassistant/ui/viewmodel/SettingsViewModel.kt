package com.example.snsassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.snsassistant.data.secure.SecurePrefs

class SettingsViewModel(private val securePrefs: SecurePrefs) : ViewModel() {
    fun getApiKey(): String = securePrefs.getApiKey() ?: ""
    fun saveApiKey(key: String) = securePrefs.setApiKey(key)

    fun getDiscordBotToken(): String = securePrefs.getDiscordBotToken() ?: ""
    fun saveDiscordBotToken(token: String) = securePrefs.setDiscordBotToken(token)

    fun getDiscordChannelId(): String = securePrefs.getDiscordChannelId() ?: ""
    fun saveDiscordChannelId(channelId: String) = securePrefs.setDiscordChannelId(channelId)
}
