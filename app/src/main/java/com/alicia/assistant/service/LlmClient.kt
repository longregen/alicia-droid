package com.alicia.assistant.service

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class LlmClient {

    companion object {
        private val CHAT_URL = "${ApiClient.BASE_URL}/v1/chat/completions"
        private const val MODEL = "qwen3-8b"
        private const val TAG = "LlmClient"
        private const val MAX_TURNS = 10
        private const val SYSTEM_PROMPT = "You are Alicia, a concise voice assistant. Keep responses short and helpful — ideally one to two sentences."
    }

    private data class ChatMessage(val role: String, val content: String)
    private data class ChatRequest(val model: String, val messages: List<ChatMessage>)
    private data class ChatResponse(val choices: List<ChatChoice>)
    private data class ChatChoice(val message: ChatMessage)

    private val gson = Gson()
    private val history = mutableListOf<Pair<String, String>>()
    private val historyMutex = Mutex()

    suspend fun chat(userMessage: String): String = withContext(Dispatchers.IO) {
        try {
            val messages = historyMutex.withLock {
                buildList {
                    add(ChatMessage("system", SYSTEM_PROMPT))
                    for ((role, content) in history) {
                        add(ChatMessage(role, content))
                    }
                    add(ChatMessage("user", userMessage))
                }
            }

            val chatRequest = ChatRequest(MODEL, messages)

            val request = Request.Builder()
                .url(CHAT_URL)
                .post(gson.toJson(chatRequest).toRequestBody("application/json".toMediaType()))
                .build()

            ApiClient.client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                    val content = chatResponse.choices.firstOrNull()?.message?.content?.trim()
                        ?: return@use "Sorry, I couldn't get a response right now."
                    Log.d(TAG, "LLM response: $content")

                    historyMutex.withLock {
                        history.add("user" to userMessage)
                        history.add("assistant" to content)
                        if (history.size > MAX_TURNS * 2) {
                            history.removeAt(0)
                            history.removeAt(0)
                        }
                    }

                    content
                } else {
                    Log.e(TAG, "LLM API error: ${response.code} $responseBody")
                    "Sorry, I couldn't get a response right now."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM request failed", e)
            "Sorry, I couldn't get a response right now."
        }
    }

    suspend fun clearHistory() {
        historyMutex.withLock { history.clear() }
    }
}
