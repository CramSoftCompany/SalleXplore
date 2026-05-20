package dev.cramsoft.sallexplore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// Eventos que puede emitir el stream
sealed class ChatEvento {
    data class Token(val texto: String) : ChatEvento()
    object   Hecho                      : ChatEvento()
    data class Error(val mensaje: String) : ChatEvento()
}

class ChatRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0,  TimeUnit.SECONDS)   // sin timeout: el stream puede tardar
        .build()

    fun chatStream(
        personaje : String,
        sessionId : String,
        mensaje   : String
    ): Flow<ChatEvento> = flow {

        val cuerpo = JSONObject().apply {
            put("personaje",  personaje)
            put("session_id", sessionId)
            put("mensaje",    mensaje)
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://ragsalle.cramsoft.dev/chat-stream")
            .post(cuerpo)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                emit(ChatEvento.Error("HTTP ${response.code}"))
                return@use
            }
            val source = response.body?.source()
                ?: run { emit(ChatEvento.Error("Respuesta vacía")); return@use }

            while (!source.exhausted()) {
                val linea = source.readUtf8Line() ?: break
                if (!linea.startsWith("data: ")) continue

                val datos = linea.removePrefix("data: ")
                runCatching { JSONObject(datos) }.getOrNull()?.let { json ->
                    when {
                        json.has("token") -> emit(ChatEvento.Token(json.getString("token")))
                        json.has("done")  -> emit(ChatEvento.Hecho)
                        json.has("error") -> emit(ChatEvento.Error(json.getString("error")))
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}