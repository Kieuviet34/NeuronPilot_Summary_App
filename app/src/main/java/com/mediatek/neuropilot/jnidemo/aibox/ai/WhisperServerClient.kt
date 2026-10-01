package com.mediatek.neuropilot.jnidemo.aibox.ai

import android.util.Log
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class WhisperServerClient(
    private val baseUrl: String = "http://127.0.0.1:8080"
) {
    private val TAG = "WhisperServerClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    fun transcribeWav(wavBytes: ByteArray, fileName: String = "segment.wav"): String {
        val fileBody = wavBytes.toRequestBody("audio/wav".toMediaType())
        return transcribeBody(fileBody, fileName)
    }

    fun transcribeFile(wavFile: File, fileName: String = wavFile.name): String {
        val fileBody = wavFile.asRequestBody("audio/wav".toMediaType())
        return transcribeBody(fileBody, fileName)
    }

    private fun transcribeBody(fileBody: okhttp3.RequestBody, fileName: String): String {
        val startedAt = System.currentTimeMillis()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, fileBody)
            .addFormDataPart("response_format", "json")
            .build()

        val request = Request.Builder()
            .url("$baseUrl/inference")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val elapsedMs = System.currentTimeMillis() - startedAt
            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP ${response.code} after ${elapsedMs}ms")
                error("whisper-server HTTP ${response.code}")
            }

            val raw = response.body?.string().orEmpty().trim()
            Log.d(TAG, "Response after ${elapsedMs}ms: $raw")
            if (raw.isEmpty()) return ""

            return try {
                val json = JsonParser.parseString(raw).asJsonObject
                when {
                    json.has("text") -> json.get("text").asString
                    else -> raw
                }
            } catch (_: Exception) {
                raw
            }
        }
    }
}
