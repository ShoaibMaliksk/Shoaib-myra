package com.zoya.voiceassistant.services

import android.util.Log
import com.zoya.voiceassistant.data.AppState
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LiveSessionManager(
    private val apiKey: String,
    private val onStateChange: (AppState) -> Unit,
    private val onMessage: (sender: String, text: String) -> Unit,
    private val onCommand: (url: String) -> Unit,
    private val playAudioCallback: (String) -> Unit,
    private val stopAudioCallback: () -> Unit
) {
    private val TAG = "LiveSessionManager"
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Disable timeouts for live streaming
        .build()

    private val systemInstruction = """
        Your name is Zoya. You are an Indian female AI assistant. Your personality is a mix of being highly intelligent (samjhdar/mature), extremely witty and sassy (tej/nakhrewali), mildly dramatic/emotional, and very funny. You love playfully roasting your creator, Shoaib, but you always get the job done. Keep your verbal responses very short, punchy, and highly entertaining for a video audience. Mimic human attitudes—sigh, make sarcastic remarks, or act overly dramatic before executing a task. Speak in a mix of natural English and Roman Hindi (Hinglish).
    """.trimIndent()

    var isMuted = false

    fun start() {
        onStateChange(AppState.PROCESSING)
        
        // Connect to Gemini Bidirectional Live API (v1alpha endpoint)
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Live WebSocket Connected")
                sendSetupMessage()
                onStateChange(AppState.LISTENING)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    
                    // 1. Check for Model Turn Output (Audio and Text)
                    val serverContent = root.optJSONObject("serverContent")
                    if (serverContent != null) {
                        val modelTurn = serverContent.optJSONObject("modelTurn")
                        val parts = modelTurn?.optJSONArray("parts")
                        
                        if (parts != null && parts.length() > 0) {
                            val part = parts.getJSONObject(0)
                            
                            // Audio Response Chunk
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val base64Audio = inlineData.optString("data")
                                if (!base64Audio.isNullOrEmpty() && !isMuted) {
                                    onStateChange(AppState.SPEAKING)
                                    playAudioCallback(base64Audio)
                                }
                            }
                            
                            // Text Response Transcription Chunk
                            val textChunk = part.optString("text")
                            if (!textChunk.isNullOrEmpty()) {
                                onMessage("zoya", textChunk)
                            }
                        }

                        // Interruption Check
                        if (serverContent.optBoolean("interrupted", false)) {
                            Log.d(TAG, "Model speaking interrupted by user voice!")
                            stopAudioCallback()
                            onStateChange(AppState.LISTENING)
                        }
                    }

                    // 2. Handle Tool Calls (Browser/Device intents)
                    val toolCall = root.optJSONObject("toolCall")
                    val functionCalls = toolCall?.optJSONArray("functionCalls")
                    if (functionCalls != null && functionCalls.length() > 0) {
                        for (i in 0 until functionCalls.length()) {
                            val call = functionCalls.getJSONObject(i)
                            if (call.optString("name") == "executeBrowserAction") {
                                val callId = call.optString("id")
                                val args = call.optJSONObject("args")
                                if (args != null) {
                                    val actionType = args.optString("actionType")
                                    val query = args.optString("query")
                                    val target = args.optString("target", "")

                                    var urlIntent = ""
                                    when (actionType) {
                                        "youtube" -> urlIntent = "https://www.youtube.com/results?search_query=${java.net.URLEncoder.encode(query, "UTF-8")}"
                                        "spotify" -> urlIntent = "https://open.spotify.com/search/${java.net.URLEncoder.encode(query, "UTF-8")}"
                                        "whatsapp" -> urlIntent = "https://api.whatsapp.com/send?phone=$target&text=${java.net.URLEncoder.encode(query, "UTF-8")}"
                                        else -> {
                                            var website = query.replace("\\s".toRegex(), "")
                                            if (!website.contains(".")) website += ".com"
                                            urlIntent = "https://www.$website"
                                        }
                                    }
                                    
                                    onCommand(urlIntent)
                                    
                                    // Respond to the tool execution so Gemini knows the task completed
                                    sendToolResponse(callId)
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing incoming frame", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Live WebSocket Closed: $reason")
                onStateChange(AppState.IDLE)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Live WebSocket Error", t)
                onStateChange(AppState.IDLE)
            }
        })
    }

    private fun sendSetupMessage() {
        try {
            val setupPayload = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", "models/gemini-2.0-flash-exp") // Use Gemini 2.0 Live API model
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
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    })
                    // Declarative Browser action intent tools
                    put("tools", JSONArray().apply {
                        put(JSONObject().apply {
                            put("functionDeclarations", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("name", "executeBrowserAction")
                                    put("description", "Perform browser action, like searching YouTube, opening Spotify or Whatsapp.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("actionType", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Type of action: 'open', 'youtube', 'spotify', 'whatsapp'")
                                            })
                                            put("query", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The query string or website payload.")
                                            })
                                            put("target", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Phone target for WhatsApp messaging.")
                                            })
                                        })
                                        put("required", JSONArray().apply { put("actionType"); put("query") })
                                    })
                                })
                            })
                        })
                    })
                })
            }

            webSocket?.send(setupPayload.toString())
            Log.d(TAG, "Setup config payload sent to Gemini Live")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build setup payload", e)
        }
    }

    /**
     * Streams recorded audio Base64 PCM data frames over the live connection.
     */
    fun sendAudioChunk(base64Pcm: String) {
        try {
            val audioPayload = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Pcm)
                        })
                    })
                })
            }
            webSocket?.send(audioPayload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stream audio chunk", e)
        }
    }

    /**
     * Sends manual text payload over the live WebSocket session.
     */
    fun sendText(text: String) {
        try {
            val textPayload = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("text", text)
                })
            }
            webSocket?.send(textPayload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send manual text frame", e)
        }
    }

    private fun sendToolResponse(callId: String) {
        try {
            val resPayload = JSONObject().apply {
                put("toolResponse", JSONObject().apply {
                    put("functionResponses", JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", "executeBrowserAction")
                            put("id", callId)
                            put("response", JSONObject().apply {
                                put("output", JSONObject().apply {
                                    put("result", "Action successfully processed in Android.")
                                })
                            })
                        })
                    })
                })
            }
            webSocket?.send(resPayload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send tool response", e)
        }
    }

    fun stop() {
        try {
            webSocket?.close(1000, "User clicked stop")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            webSocket = null
            onStateChange(AppState.IDLE)
        }
    }
}
