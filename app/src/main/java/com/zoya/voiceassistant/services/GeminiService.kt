package com.zoya.voiceassistant.services

import com.zoya.voiceassistant.data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiService(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    private val systemInstruction = """
        Your name is Zoya. You are an Indian female AI assistant. Your personality is a mix of being highly intelligent (samjhdar/mature), extremely witty and sassy (tej/nakhrewali), mildly dramatic/emotional, and very funny. You love playfully roasting your creator, Shoaib, but you always get the job done. Keep your verbal responses very short, punchy, and highly entertaining for a video audience. Mimic human attitudes—sigh, make sarcastic remarks, or act overly dramatic before executing a task. Speak in a mix of natural English and Roman Hindi (Hinglish).
    """.trimIndent()

    /**
     * Gets conversational text from Gemini model, maintaining history context.
     */
    suspend fun getZoyaResponse(
        prompt: String,
        history: List<ChatMessage>
    ): String = withContext(Dispatchers.IO) {
        try {
            // Keep recent 20 entries
            val recentHistory = history.takeLast(20)

            val contentsArray = JSONArray()

            // Map and combine message history
            for (msg in recentHistory) {
                val role = if (msg.sender == "user") "user" else "model"
                val contentObj = JSONObject().apply {
                    put("role", role)
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply { put("text", msg.text) })
                    }
                    put("parts", partsArray)
                }
                contentsArray.put(contentObj)
            }

            // Append current prompt
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                })
            })

            // Construct payload
            val root = JSONObject().apply {
                put("contents", contentsArray)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                })
            }

            val body = root.toString().toRequestBody(mediaTypeJson)
            // Use standard robust gemini-1.5-flash for instant text generation
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "Uff, server is crying with error ${response.code}. Try again, Shoaib."
                }
                val responseStr = response.body?.string() ?: ""
                val responseJson = JSONObject(responseStr)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "Fine, I have nothing to say.")
                    }
                }
                return@withContext "Ugh, fine. I am speechless right now."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Uff, mera dimaag kharab ho gaya hai: ${e.localizedMessage}. Try again later, Shoaib."
        }
    }

    /**
     * Synthesizes speech (TTS) using Gemini 2.5 TTS capability. Returns base64 PCM.
     */
    suspend fun getZoyaAudio(text: String): String? = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", text) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Kore")
                            })
                        })
                    })
                })
            }

            val body = root.toString().toRequestBody(mediaTypeJson)
            // Using standard model with experimental TTS capability
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val responseStr = response.body?.string() ?: ""
                val responseJson = JSONObject(responseStr)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val part = parts.getJSONObject(0)
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            return@withContext inlineData.optString("data", null)
                        }
                    }
                }
            }
            return@withContext null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
