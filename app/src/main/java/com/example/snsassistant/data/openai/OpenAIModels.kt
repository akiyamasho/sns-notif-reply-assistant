package com.example.snsassistant.data.openai

import com.squareup.moshi.Json

data class ChatMessage(
    val role: String,
    val content: String
)

data class ResponseFormat(
    val type: String
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @Json(name = "response_format") val responseFormat: ResponseFormat? = null
)

data class ChatResponse(
    val id: String?,
    val choices: List<Choice>?
)

data class Choice(
    val index: Int,
    val message: ChatMessage
)
