package com.example.snsassistant.data.openai

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import com.squareup.moshi.Types
import com.squareup.moshi.JsonAdapter

interface OpenAIService {
    @POST("v1/chat/completions")
    suspend fun chatCompletions(@Body body: ChatRequest): ChatResponse
}

class OpenAIClient(private val apiKeyProvider: ApiKeyProvider) {
    private val baseUrl = "https://api.openai.com/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private fun client(apiKey: String): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(req)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    private fun retrofit(apiKey: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client(apiKey))
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    suspend fun suggestReplies(platform: String, postText: String, link: String?): List<String> {
        val apiKey = apiKeyProvider.getApiKey() ?: return emptyList()

        val basePersona = """
            You are a seasoned senior software engineer.
            Rules for all platforms:
            - Do NOT use hashtags or emojis.
            - Avoid generic platitudes (e.g., "Great post", "Nice!", "Thanks for sharing").
            - Be specific to the post content; add insight or a sharp angle.
            - No salutations, sign-offs, or self-reference.
        """.trimIndent()

        val systemPrompt = when (platform) {
            "LinkedIn" -> (
                basePersona + "\n" +
                """
                LinkedIn specific:
                - Generate 5 replies.
                - If the post seems professional (announcements, engineering, product updates, research, career): reply professionally with insight and clarity.
                - If the post reads like a meme or humorous/low-effort content: reply in an unhinged, witty manner (still respectful, PG-13, no slurs).
                - Keep each reply under 280 characters.
                - No hashtags or emojis. Avoid generic filler.
                Output strictly a JSON array of 5 strings.
                """.trimIndent()
            )
            "X" -> (
                basePersona + "\n" +
                """
                X (Twitter) specific:
                - Suggest 5 witty, concise replies.
                - Each reply must be <= 280 characters.
                - No hashtags or emojis. Avoid generic filler.
                Output strictly a JSON array of 5 strings.
                """.trimIndent()
            )
            "Instagram" -> (
                basePersona + "\n" +
                """
                Instagram specific:
                - Suggest 5 casual, engaging comments (friendly and human), but no hashtags or emojis.
                - Avoid generic comments or just praise; be specific and conversational.
                Output strictly a JSON array of 5 strings.
                """.trimIndent()
            )
            else -> (
                basePersona + "\nSuggest 5 concise, relevant replies. Output strictly a JSON array of 5 strings."
            )
        }

        val userText = buildString {
            append("Post: \"$postText\"\n")
            if (!link.isNullOrBlank()) append("Link: $link\n")
            append("Return ONLY a JSON array of 5 strings. No prose. No surrounding text.")
        }

        val request = ChatRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", userText)
            )
        )

        val service = retrofit(apiKey).create(OpenAIService::class.java)
        val response = service.chatCompletions(request)
        val content = response.choices?.firstOrNull()?.message?.content ?: return emptyList()

        // Try to parse JSON array; fallback to line splitting
        return JsonArrayParser.parseArrayOfStrings(content)
    }
}

interface ApiKeyProvider {
    fun getApiKey(): String?
}

object JsonArrayParser {
    // Robust parser: handles JSON array, object with array field, embedded array, and plain text lists
    fun parseArrayOfStrings(content: String): List<String> {
        val text = content.trim()

        // Try as pure JSON array
        parseJsonArray(text)?.let { return it }

        // Try as JSON object with array field (responses/suggestions/replies/comments)
        if (text.startsWith("{")) {
            val arr = extractArrayFromObject(text)
            if (arr.isNotEmpty()) return arr
        }

        // Try to extract first [...] array substring
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start != -1 && end != -1 && end > start) {
            parseJsonArray(text.substring(start, end + 1))?.let { return it }
        }

        // Fallback: split lines/bullets/numbers
        val bulletRegex = Regex("^(?:[-*•]|\\d+[.)])\\s*")
        return text.lines()
            .map { it.trim() }
            .map { bulletRegex.replace(it, "") }
            .filter { it.isNotBlank() }
            .take(5)
    }

    private fun parseJsonArray(json: String): List<String>? {
        if (!json.startsWith("[")) return null
        return try {
            val type = Types.newParameterizedType(List::class.java, String::class.java)
            val adapter: JsonAdapter<List<String>> = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(type)
            val list = adapter.fromJson(json)?.map { it.trim() }?.filter { it.isNotBlank() }
            list?.take(5)
        } catch (_: Throwable) { null }
    }

    private fun extractArrayFromObject(json: String): List<String> {
        return try {
            val anyType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val adapter: JsonAdapter<Map<String, Any>> = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(anyType)
            val obj = adapter.fromJson(json) ?: return emptyList()
            val candidateKeys = listOf("responses", "suggestions", "replies", "comments", "data")
            val list = candidateKeys.asSequence()
                .mapNotNull { key -> obj[key] as? List<*> }
                .firstOrNull { l -> l.all { it is String } }
                ?: obj.values.firstOrNull { v -> (v as? List<*>)?.all { it is String } == true } as? List<*>
            list?.map { it as String }?.map { it.trim() }?.filter { it.isNotBlank() }?.take(5) ?: emptyList()
        } catch (_: Throwable) { emptyList() }
    }
}
