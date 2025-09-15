package com.example.snsassistant.data.discord

import android.util.Log
import com.example.snsassistant.data.db.PostEntity
import com.example.snsassistant.data.secure.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class DiscordClient(private val prefs: SecurePrefs) {
    private val client = OkHttpClient()
    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun sendPostWithReplies(post: PostEntity, replies: List<String>) {
        val token = prefs.getDiscordBotToken()?.trim().orEmpty()
        val channelId = prefs.getDiscordChannelId()?.trim().orEmpty()
        if (token.isBlank() || channelId.isBlank()) return

        val content = buildString {
            // Title: italic + platform emoji
            append("*")
            append(platformEmoji(post.platform))
            append(" ")
            append(post.platform)
            append("*\n")
            // Body: original notification text
            append(post.text.take(1200))
            append('\n')
            if (!post.link.isNullOrBlank()) {
                append("Link: ")
                append(post.link)
                append('\n')
            }
            if (replies.isNotEmpty()) {
                append("\nSuggested replies:\n")
                replies.forEachIndexed { idx, r ->
                    append("${idx + 1}. ")
                    append(r.take(500))
                    append('\n')
                }
            }
        }.take(1900) // Discord content limit 2000

        val payload = "{" + "\"content\": " + content.quoteJson() + "}"
        val body: RequestBody = payload.toRequestBody(json)

        val req = Request.Builder()
            .url("https://discord.com/api/v10/channels/$channelId/messages")
            .addHeader("Authorization", "Bot $token")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        withContext(Dispatchers.IO) {
            runCatching { client.newCall(req).execute() }
                .onSuccess { resp ->
                    if (!resp.isSuccessful) {
                        Log.w("DiscordClient", "Post failed: ${resp.code} ${resp.message}")
                    }
                    resp.close()
                }
                .onFailure { t -> Log.w("DiscordClient", "Post error: ${t.message}") }
        }
    }
}

private fun String.quoteJson(): String {
    val escaped = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
    return "\"$escaped\""
}

private fun platformEmoji(platform: String): String = when (platform) {
    "LinkedIn" -> "🔗"
    "X" -> "✖️"
    "Instagram" -> "📸"
    else -> "💬"
}
