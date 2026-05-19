package com.ssafy.mobile.feature.conversation.data.streaming

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.ssafy.mobile.BuildConfig
import com.ssafy.mobile.feature.conversation.domain.streaming.TranslationStreamingSttClient
import com.ssafy.mobile.feature.conversation.domain.streaming.TranslationStreamingSttConnection
import com.ssafy.mobile.feature.conversation.domain.streaming.TranslationStreamingSttEvent
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString

@Singleton
class OkHttpTranslationStreamingSttClient
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
    ) : TranslationStreamingSttClient {
        override fun start(
            sessionId: Long,
            locale: String,
            listener: TranslationStreamingSttClient.Listener,
        ): TranslationStreamingSttConnection {
            val request =
                Request
                    .Builder()
                    .url(streamingUrl())
                    .build()
            val webSocket =
                okHttpClient.newWebSocket(
                    request,
                    StreamingWebSocketListener(
                        sessionId = sessionId,
                        locale = locale,
                        listener = listener,
                    ),
                )
            return OkHttpTranslationStreamingSttConnection(webSocket)
        }

        private fun streamingUrl(): String {
            val baseUrl = BuildConfig.BASE_URL
            val scheme =
                when {
                    baseUrl.startsWith("https://") -> "wss://"
                    baseUrl.startsWith("http://") -> "ws://"
                    else -> "ws://"
                }
            val withoutScheme =
                baseUrl
                    .removePrefix("https://")
                    .removePrefix("http://")
            val hostAndPath = withoutScheme.substringBefore("/api/")
            return "$scheme$hostAndPath$STREAMING_PATH"
        }

        private class StreamingWebSocketListener(
            private val sessionId: Long,
            private val locale: String,
            private val listener: TranslationStreamingSttClient.Listener,
        ) : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                webSocket.send(gson.toJson(StartMessage(sessionId = sessionId, locale = locale)))
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                parseEvent(text)
                    .onSuccess { event -> listener.onEvent(event) }
                    .onFailure { throwable ->
                        Log.w(TAG, "Failed to parse streaming STT message: $text", throwable)
                        listener.onEvent(
                            TranslationStreamingSttEvent.Error(
                                message = "Streaming STT response parsing failed.",
                                cause = throwable,
                            ),
                        )
                    }
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                listener.onEvent(
                    TranslationStreamingSttEvent.Error(
                        message = "Streaming STT connection failed.",
                        cause = t,
                    ),
                )
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                listener.onEvent(TranslationStreamingSttEvent.Closed)
            }

            private fun parseEvent(text: String): Result<TranslationStreamingSttEvent> =
                runCatching {
                    val message = gson.fromJson(text, StreamingMessage::class.java)
                    when (message.type) {
                        TYPE_STARTED -> TranslationStreamingSttEvent.Started
                        TYPE_CONFIG -> TranslationStreamingSttEvent.Configured
                        TYPE_PARTIAL ->
                            TranslationStreamingSttEvent.Partial(
                                recognizedText = message.recognizedText.orEmpty(),
                                correctedText = message.correctedText.orEmpty(),
                                confidence = message.confidence,
                                locale = message.locale,
                            )
                        TYPE_FINAL ->
                            TranslationStreamingSttEvent.Final(
                                recognizedText = message.recognizedText.orEmpty(),
                                correctedText = message.correctedText.orEmpty(),
                                confidence = message.confidence,
                                locale = message.locale,
                            )
                        TYPE_ERROR ->
                            TranslationStreamingSttEvent.Error(
                                message = message.message ?: "Streaming STT failed.",
                            )
                        TYPE_CLOSED -> TranslationStreamingSttEvent.Closed
                        else ->
                            TranslationStreamingSttEvent.Error(
                                message = "Unsupported streaming STT event: ${message.type}",
                            )
                    }
                }.recoverCatching { throwable ->
                    if (throwable is JsonSyntaxException) {
                        throw throwable
                    }
                    throw IllegalStateException("Invalid streaming STT message.", throwable)
                }
        }

        private class OkHttpTranslationStreamingSttConnection(
            private val webSocket: WebSocket,
        ) : TranslationStreamingSttConnection {
            override fun sendAudio(audioBytes: ByteArray): Boolean =
                webSocket.send(audioBytes.toByteString())

            override fun end(): Boolean = webSocket.send(gson.toJson(EndMessage()))

            override fun close() {
                webSocket.close(NORMAL_CLOSE_CODE, "streaming-stt-client-close")
            }
        }

        private data class StartMessage(
            @SerializedName("type") val type: String = TYPE_START,
            @SerializedName("sessionId") val sessionId: Long,
            @SerializedName("locale") val locale: String,
        )

        private data class EndMessage(
            @SerializedName("type") val type: String = TYPE_END,
        )

        private data class StreamingMessage(
            @SerializedName("type") val type: String?,
            @SerializedName("recognizedText") val recognizedText: String?,
            @SerializedName("correctedText") val correctedText: String?,
            @SerializedName("confidence") val confidence: Double?,
            @SerializedName("locale") val locale: String?,
            @SerializedName("message") val message: String?,
        )

        private companion object {
            private const val TAG = "OkHttpTranslationStreamingSttClient"
            private const val STREAMING_PATH = "/ws/v1/translation/speech-to-text/stream"
            private const val NORMAL_CLOSE_CODE = 1000
            private const val TYPE_START = "start"
            private const val TYPE_END = "end"
            private const val TYPE_STARTED = "started"
            private const val TYPE_CONFIG = "config"
            private const val TYPE_PARTIAL = "partial"
            private const val TYPE_FINAL = "final"
            private const val TYPE_ERROR = "error"
            private const val TYPE_CLOSED = "closed"
            private val gson = Gson()
        }
    }
