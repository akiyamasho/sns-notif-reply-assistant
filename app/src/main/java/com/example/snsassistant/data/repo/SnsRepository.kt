package com.example.snsassistant.data.repo

import android.util.Log
import com.example.snsassistant.data.db.PostDao
import com.example.snsassistant.data.db.PostEntity
import com.example.snsassistant.data.db.PostWithReplies
import com.example.snsassistant.data.db.ReplyDao
import com.example.snsassistant.data.db.ReplyEntity
import com.example.snsassistant.data.openai.OpenAIClient
import com.example.snsassistant.data.secure.SecurePrefs
import com.example.snsassistant.data.discord.DiscordClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

class SnsRepository(
    private val postDao: PostDao,
    private val replyDao: ReplyDao,
    private val openAI: OpenAIClient,
    private val securePrefs: SecurePrefs,
    private val discord: DiscordClient = DiscordClient(securePrefs)
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeFeed(): Flow<List<PostWithReplies>> = postDao.observePostsWithReplies()

    suspend fun addIncomingPost(platform: String, text: String, timestamp: Long, link: String?): Long {
        val id = postDao.insert(PostEntity(platform = platform, text = text, timestamp = timestamp, link = link))
        // Send to Discord immediately without waiting for replies
        appScope.launch {
            runCatching {
                val post = postDao.getById(id) ?: return@launch
                discord.sendPostWithReplies(post, emptyList())
            }
        }
        return id
    }

    suspend fun generateRepliesForPost(postId: Long) {
        val post = postDao.getById(postId) ?: return
        if (securePrefs.getApiKey().isNullOrBlank()) {
            postDao.setLastError(postId, "OpenAI API key missing. Set it in Settings.")
            return
        }

        // Retry with simple backoff up to 3 attempts
        var attempt = 0
        var success = false
        var lastError: Throwable? = null
        while (attempt < 3 && !success) {
            try {
                val suggestions = openAI.suggestReplies(post.platform, post.text, post.link)
                if (suggestions.isNotEmpty()) {
                    val trimmed = suggestions.map { it.trim() }.filter { it.isNotBlank() }
                    val capped = trimmed.subList(0, min(5, trimmed.size))
                    replyDao.deleteForPost(postId)
                    replyDao.insertAll(capped.map { ReplyEntity(postId = postId, replyText = it) })
                    postDao.setLastError(postId, null)
                    // Send to Discord (fire-and-forget)
                    appScope.launch {
                        runCatching {
                            val entity = postDao.getById(postId) ?: return@launch
                            discord.sendPostWithReplies(entity, capped)
                        }
                    }
                } else {
                    postDao.setLastError(postId, "No suggestions returned.")
                }
                success = true
            } catch (t: Throwable) {
                lastError = t
                Log.e("SnsRepository", "OpenAI suggest error (attempt ${attempt + 1}): ${t.message}")
                val backoff = (1000L * (attempt + 1))
                withContext(Dispatchers.IO) { try { Thread.sleep(backoff) } catch (_: InterruptedException) {} }
                attempt++
            }
        }
        if (!success) {
            val msg = lastError?.message ?: lastError?.toString() ?: "Unknown error"
            Log.e("SnsRepository", "Failed to generate replies after retries: $msg")
            postDao.setLastError(postId, msg)
        }
    }

    suspend fun markDone(postId: Long) {
        postDao.setDone(postId, true)
    }

    suspend fun markUndone(postId: Long) {
        postDao.setDone(postId, false)
    }

    suspend fun deleteRepliesForPost(postId: Long) {
        replyDao.deleteForPost(postId)
    }

    suspend fun deletePosts(ids: Collection<Long>) {
        ids.forEach { id ->
            // Ensure replies are removed first to maintain referential integrity expectations
            replyDao.deleteForPost(id)
            postDao.deleteById(id)
        }
    }

    suspend fun resendToDiscord(postId: Long) {
        val post = postDao.getById(postId) ?: return
        val replies = replyDao.getForPost(postId).map { it.replyText }
        discord.sendPostWithReplies(post, replies)
    }
}
