package com.ssafy.mobile.feature.conversation.data.repository

import com.ssafy.mobile.feature.conversation.data.remote.TranslateApiService
import com.ssafy.mobile.feature.conversation.data.remote.model.SignToSpeechRequest
import com.ssafy.mobile.feature.conversation.data.remote.model.SignToSpeechResponse
import com.ssafy.mobile.feature.conversation.data.remote.model.SpeechToTextResponse
import com.ssafy.mobile.feature.conversation.data.remote.model.TranslationFeedbackRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

class DefaultTranslateRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `rejects missing speech audio file before api call`() =
        runBlocking {
            val apiService = FakeTranslateApiService()
            val repository = DefaultTranslateRepository(apiService)
            val missingFile = File(temporaryFolder.root, "missing.wav")

            val result = repository.translateSpeechToText(missingFile, AUDIO_WAV)

            assertTrue(result.isFailure)
            assertEquals(0, apiService.speechToTextCallCount)
        }

    @Test
    fun `rejects wav file without payload before api call`() =
        runBlocking {
            val apiService = FakeTranslateApiService()
            val repository = DefaultTranslateRepository(apiService)
            val audioFile = temporaryFolder.newFile("empty.wav")
            audioFile.writeBytes(ByteArray(WAV_HEADER_SIZE))

            val result = repository.translateSpeechToText(audioFile, AUDIO_WAV)

            assertTrue(result.isFailure)
            assertEquals(0, apiService.speechToTextCallCount)
        }

    @Test
    fun `uploads valid wav file`() =
        runBlocking {
            val apiService = FakeTranslateApiService()
            val repository = DefaultTranslateRepository(apiService)
            val audioFile = temporaryFolder.newFile("speech.wav")
            audioFile.writeBytes(validWavBytes())

            val result = repository.translateSpeechToText(audioFile, AUDIO_WAV)

            assertTrue(result.isSuccess)
            assertEquals(1, apiService.speechToTextCallCount)
            assertEquals(RECOGNIZED_TEXT, result.getOrThrow().recognizedText)
        }

    @Test
    fun `includes problem details when speech api fails`() =
        runBlocking {
            val apiService =
                FakeTranslateApiService(
                    speechToTextResponse =
                        Response.error(
                            BAD_REQUEST,
                            PROBLEM_DETAILS_JSON.toResponseBody(APPLICATION_JSON.toMediaType()),
                        ),
                )
            val repository = DefaultTranslateRepository(apiService)
            val audioFile = temporaryFolder.newFile("speech.wav")
            audioFile.writeBytes(validWavBytes())

            val result = repository.translateSpeechToText(audioFile, AUDIO_WAV)
            val message = result.exceptionOrNull()?.message.orEmpty()

            assertTrue(result.isFailure)
            assertTrue(message.contains("code=INVALID_AUDIO"))
            assertTrue(message.contains("traceId=trace-1"))
        }

    private class FakeTranslateApiService(
        var speechToTextResponse: Response<SpeechToTextResponse> =
            Response.success(
                SpeechToTextResponse(
                    recognizedText = RECOGNIZED_TEXT,
                    correctedText = RECOGNIZED_TEXT,
                    corrected = false,
                    confidence = null,
                    locale = "ko-KR",
                ),
            ),
    ) : TranslateApiService {
        var speechToTextCallCount = 0

        override suspend fun translateSignToSpeech(
            request: SignToSpeechRequest,
            dryRun: Boolean,
        ): Response<SignToSpeechResponse> =
            Response.success(
                SignToSpeechResponse(
                    originalWords = request.words,
                    correctedText = request.words.joinToString(" "),
                    audioBase64 = null,
                    audioMimeType = null,
                    corrected = false,
                ),
            )

        override suspend fun translateSpeechToText(
            audioFile: MultipartBody.Part,
            locale: RequestBody,
            audioMimeType: RequestBody,
            dryRun: Boolean,
        ): Response<SpeechToTextResponse> {
            speechToTextCallCount++
            return speechToTextResponse
        }

        override suspend fun submitTranslationFeedback(
            request: TranslationFeedbackRequest,
        ): Response<Unit> = Response.success(Unit)
    }

    private companion object {
        private const val AUDIO_WAV = "audio/wav"
        private const val APPLICATION_JSON = "application/json"
        private const val BAD_REQUEST = 400
        private const val WAV_HEADER_SIZE = 44
        private const val PCM_DATA_SIZE = 3200
        private const val RECOGNIZED_TEXT = "안녕"
        private const val PROBLEM_DETAILS_JSON =
            """
            {
              "type": "about:blank",
              "title": "Invalid audio",
              "status": 400,
              "detail": "Invalid WAV header",
              "instance": "/api/v1/translation/speech-to-text",
              "code": "INVALID_AUDIO",
              "traceId": "trace-1"
            }
            """

        private fun validWavBytes(): ByteArray =
            ByteArray(WAV_HEADER_SIZE + PCM_DATA_SIZE).apply {
                "RIFF".encodeToByteArray().copyInto(this, destinationOffset = 0)
                "WAVE".encodeToByteArray().copyInto(this, destinationOffset = 8)
            }
    }
}
