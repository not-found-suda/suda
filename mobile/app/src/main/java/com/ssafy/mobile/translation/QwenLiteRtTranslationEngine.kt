package com.ssafy.mobile.translation

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.ssafy.mobile.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions")
class QwenLiteRtTranslationEngine(
    private val context: Context,
) : OnDeviceTranslationEngine {
    private var engine: Engine? = null
    private var loaded = false
    private var lastPreparedModelPath: String? = null
    private var lastBackendSummary: String = "Not loaded"
    private val loadMutex = Mutex()

    override val debugModelAssetPath: String
        get() = OnDeviceModelConfig.QWEN_MODEL_ASSET_PATH

    override val debugPreparedModelPath: String?
        get() = lastPreparedModelPath

    override val debugPreparedEntryNames: List<String>
        get() = emptyList()

    override val debugMaxTokens: Int
        get() = MAX_NUM_TOKENS

    override val debugTopK: Int
        get() = TOP_K

    override val debugBackendSummary: String
        get() = lastBackendSummary

    override suspend fun load() =
        withContext(Dispatchers.Default) {
            loadMutex.withLock {
                if (loaded) {
                    logDebug { "Qwen load skipped. backend=$lastBackendSummary" }
                    return@withLock
                }

                val loadStart = System.currentTimeMillis()
                val modelFile = resolveModelFile()
                lastPreparedModelPath = modelFile.absolutePath
                Engine.setNativeMinLogSeverity(LogSeverity.ERROR)

                logDebug {
                    "Qwen load start. path=${modelFile.absolutePath}, " +
                        "sizeBytes=${modelFile.length()}, maxNumTokens=$MAX_NUM_TOKENS"
                }

                val loadedEngine = createEngineWithFallback(modelFile)
                engine = loadedEngine.engine
                loaded = true
                lastBackendSummary =
                    "LiteRT-LM ${loadedEngine.backendName}, " +
                    "model=${OnDeviceModelConfig.QWEN_MODEL_FILE_NAME}"

                logDebug {
                    "Qwen load success. backend=${loadedEngine.backendName}, " +
                        "elapsedMs=${System.currentTimeMillis() - loadStart}"
                }
            }
        }

    override suspend fun translate(
        glossText: String,
        sentenceType: String?,
    ): TranslationResult =
        withContext(Dispatchers.Default) {
            val normalizedGloss = normalizeGloss(glossText)
            val start = System.currentTimeMillis()
            childDirectedOverride(
                glossText = normalizedGloss,
                sentenceType = sentenceType,
            )?.let { overriddenText ->
                logDebug {
                    "Qwen translate skipped by child-directed override. " +
                        "gloss=${normalizedGloss.toLogPreview()}, " +
                        "sentenceType=$sentenceType, " +
                        "resolved=${overriddenText.toLogPreview()}"
                }
                return@withContext TranslationResult(
                    glossText = glossText,
                    koreanText = overriddenText,
                    rawText = overriddenText,
                    usedRuleBased = true,
                    elapsedMs = System.currentTimeMillis() - start,
                )
            }

            if (!loaded) {
                load()
            }

            val prompt =
                buildUserPrompt(
                    glossText = normalizedGloss,
                    sentenceType = sentenceType,
                )
            logDebug {
                "Qwen translate start. gloss=${normalizedGloss.toLogPreview()}, " +
                    "sentenceType=$sentenceType, " +
                    "promptChars=${prompt.length}"
            }

            val raw =
                requireNotNull(engine) {
                    "Qwen LiteRT-LM engine must be loaded before translation."
                }.createConversation(buildConversationConfig()).use { conversation ->
                    extractText(conversation.sendMessage(Message.user(prompt)))
                }

            val elapsed = System.currentTimeMillis() - start
            val cleaned = cleanOutput(raw)
            val resolved = cleaned.ifBlank { fallbackGlossSentence(normalizedGloss).orEmpty() }
            logDebug {
                "Qwen translate result. raw=${raw.toLogPreview()}, " +
                    "cleaned=${cleaned.toLogPreview()}, " +
                    "resolved=${resolved.toLogPreview()}, elapsedMs=$elapsed"
            }

            TranslationResult(
                glossText = glossText,
                koreanText = resolved,
                rawText = raw,
                usedRuleBased = cleaned.isBlank() && resolved.isNotBlank(),
                elapsedMs = elapsed,
            )
        }

    override fun close() {
        engine?.close()
        engine = null
        loaded = false
        lastBackendSummary = "Closed"
    }

    @Suppress("TooGenericExceptionCaught")
    private fun createEngineWithFallback(modelFile: File): LoadedEngine {
        val backendCandidates =
            buildList {
                if (ENABLE_GPU_BACKEND) {
                    add(
                        BackendCandidate(
                            name = "GPU",
                            backend = Backend.GPU(),
                        ),
                    )
                }
                add(
                    BackendCandidate(
                        name = "CPU",
                        backend = Backend.CPU(numOfThreads = CPU_THREAD_COUNT),
                    ),
                )
            }

        var lastError: Exception? = null
        for (candidate in backendCandidates) {
            try {
                logDebug { "Qwen backend init start. backend=${candidate.name}" }
                val qwenEngine =
                    Engine(
                        EngineConfig(
                            modelPath = modelFile.absolutePath,
                            backend = candidate.backend,
                            cacheDir = File(context.cacheDir, CACHE_DIR_NAME).absolutePath,
                            maxNumTokens = MAX_NUM_TOKENS,
                        ),
                    ).also { engine -> engine.initialize() }

                return LoadedEngine(
                    engine = qwenEngine,
                    backendName = candidate.name,
                )
            } catch (exception: Exception) {
                lastError = exception
                Log.w(
                    TAG,
                    "Qwen backend init failed. backend=${candidate.name}. Trying fallback.",
                    exception,
                )
            }
        }

        lastError?.let { exception -> throw exception }
        error("Failed to initialize Qwen LiteRT-LM engine.")
    }

    private fun buildConversationConfig(): ConversationConfig =
        ConversationConfig(
            systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
            samplerConfig =
                SamplerConfig(
                    topK = TOP_K,
                    topP = TOP_P,
                    temperature = TEMPERATURE,
                ),
        )

    private fun buildUserPrompt(
        glossText: String,
        sentenceType: String?,
    ): String =
        """
        다음 수어 gloss를 5세 이하 아이에게 말하듯 자연스러운 한국어 한 문장으로 바꿔.

        gloss: $glossText
        문장유형: ${sentenceType.orEmpty().ifBlank { "미상" }}

        출력:
        """.trimIndent()

    private fun childDirectedOverride(
        glossText: String,
        sentenceType: String?,
    ): String? {
        val normalizedTokens =
            glossText
                .split(' ')
                .filter { token -> token.isNotBlank() && token != NONE_GLOSS }

        val normalizedKey = normalizedTokens.joinToString(" ")
        val override = CHILD_DIRECTED_SENTENCE_OVERRIDES[normalizedKey] ?: return null
        return override.resolve(isQuestionSentenceType(sentenceType))
    }

    private fun isQuestionSentenceType(sentenceType: String?): Boolean =
        sentenceType?.contains("의문") == true ||
            sentenceType.equals("question", ignoreCase = true)

    private fun extractText(message: Message): String {
        val textFromContents =
            message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString(separator = "") { content -> content.text }
                .trim()

        return textFromContents.ifBlank { message.toString() }.trim()
    }

    private fun cleanOutput(rawText: String): String {
        val withoutThinking = removeThinkingBlock(rawText)
        val meaningfulLine =
            withoutThinking
                .lineSequence()
                .map { line ->
                    line
                        .trim()
                        .removePrefix("한국어 문장:")
                        .removePrefix("한국어:")
                        .removePrefix("출력:")
                        .removePrefix("Korean:")
                        .removePrefix("정답:")
                        .trim()
                }.firstOrNull { line ->
                    line.isNotBlank() &&
                        !line.startsWith("<think>", ignoreCase = true) &&
                        !line.startsWith("</think>", ignoreCase = true) &&
                        !line.startsWith("Gloss:") &&
                        !line.startsWith("gloss:") &&
                        !line.equals("user", ignoreCase = true) &&
                        !line.equals("model", ignoreCase = true)
                }.orEmpty()

        return meaningfulLine
            .substringBefore("Gloss:")
            .trim()
            .trim('"')
    }

    private fun removeThinkingBlock(rawText: String): String {
        val removedClosedBlocks =
            rawText.replace(
                Regex(
                    pattern = "<think>.*?</think>",
                    options =
                        setOf(
                            RegexOption.IGNORE_CASE,
                            RegexOption.DOT_MATCHES_ALL,
                        ),
                ),
                "\n",
            )
        val openThinkIndex =
            removedClosedBlocks.indexOf(
                string = "<think>",
                ignoreCase = true,
            )
        if (openThinkIndex < 0) return removedClosedBlocks

        val closeThinkIndex =
            removedClosedBlocks.indexOf(
                string = "</think>",
                startIndex = openThinkIndex,
                ignoreCase = true,
            )
        return if (closeThinkIndex >= 0) {
            removedClosedBlocks.removeRange(
                startIndex = openThinkIndex,
                endIndex = closeThinkIndex + THINK_CLOSE_TAG.length,
            )
        } else {
            removedClosedBlocks.substring(0, openThinkIndex)
        }
    }

    private fun fallbackGlossSentence(glossText: String): String? {
        val tokens = normalizeGloss(glossText).split(' ').filter { token -> token.isNotBlank() }
        return when {
            tokens.isEmpty() -> null
            tokens.size == 1 -> "${tokens.first()}입니다."
            else -> buildFallbackSentence(tokens)
        }
    }

    private fun buildFallbackSentence(tokens: List<String>): String? {
        val subject = tokens.first()
        val hasNegative = tokens.any { token -> token in NEGATIVE_TOKENS }
        val verb = tokens.last { token -> token !in TIME_TOKENS && token !in NEGATIVE_TOKENS }
        val objectTokens =
            tokens
                .drop(1)
                .filter { token -> token !in TIME_TOKENS && token !in NEGATIVE_TOKENS }
                .dropLast(1)
        val timePhrase = tokens.firstOrNull { token -> token in TIME_TOKENS }
        val objectPhrase = objectTokens.joinToString(" ").takeIf { phrase -> phrase.isNotBlank() }

        return fallbackPredicate(verb, hasNegative)?.let { predicatePhrase ->
            buildList {
                add(subject + subjectParticle(subject))
                timePhrase?.let(::add)
                objectPhrase?.let { phrase -> add(phrase + objectParticle(phrase)) }
                add(predicatePhrase)
            }.joinToString(" ") + "."
        }
    }

    private fun fallbackPredicate(
        verb: String,
        hasNegative: Boolean,
    ): String? =
        if (hasNegative) {
            NEGATIVE_PREDICATES[verb]
        } else {
            AFFIRMATIVE_PREDICATES[verb]
        }

    private fun subjectParticle(token: String): String = if (hasBatchim(token)) "은" else "는"

    private fun objectParticle(token: String): String = if (hasBatchim(token)) "을" else "를"

    private fun hasBatchim(token: String): Boolean =
        token.lastOrNull()?.let { lastChar ->
            lastChar in HANGUL_START..HANGUL_END &&
                (lastChar.code - HANGUL_START.code) % HANGUL_CYCLE != 0
        } ?: false

    private data class BackendCandidate(
        val name: String,
        val backend: Backend,
    )

    private data class LoadedEngine(
        val engine: Engine,
        val backendName: String,
    )

    private data class ChildDirectedSentenceOverride(
        val statement: String,
        val question: String,
    ) {
        fun resolve(isQuestion: Boolean): String = if (isQuestion) question else statement
    }

    private fun resolveModelFile(): File {
        val externalModelFile =
            context
                .getExternalFilesDir(OnDeviceModelConfig.MODEL_DIR_NAME)
                ?.resolve(OnDeviceModelConfig.QWEN_MODEL_FILE_NAME)
        val internalModelFile = File(context.filesDir, OnDeviceModelConfig.QWEN_MODEL_FILE_NAME)

        return externalModelFile.takeIfValidModel()
            ?: internalModelFile.takeIfValidModel()
            ?: copyAssetToInternalFile(internalModelFile)
    }

    private fun File?.takeIfValidModel(): File? =
        this?.takeIf { file -> file.isFile && file.length() > 0 }

    private fun copyAssetToInternalFile(outFile: File): File {
        try {
            context.assets.open(OnDeviceModelConfig.QWEN_MODEL_ASSET_PATH).use { assetInput ->
                FileOutputStream(outFile, false).use { output ->
                    assetInput.copyTo(output, BUFFER_SIZE)
                }
            }
        } catch (exception: IOException) {
            val modelFileName = OnDeviceModelConfig.QWEN_MODEL_FILE_NAME
            throw IllegalStateException(
                "Qwen model file is missing. Put $modelFileName under " +
                    "app/src/main/assets/models or push it to app external files/models.",
                exception,
            )
        }

        require(outFile.length() > 0) {
            "Qwen model file was copied but is empty: ${outFile.absolutePath}"
        }
        return outFile
    }

    private fun normalizeGloss(glossText: String): String =
        glossText
            .lineSequence()
            .joinToString(" ") { line -> line.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun logDebug(message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message())
        }
    }

    private fun String.toLogPreview(): String {
        val singleLine = replace(Regex("\\s+"), " ").trim()
        return if (singleLine.length <= LOG_PREVIEW_LIMIT) {
            singleLine
        } else {
            "${singleLine.take(LOG_PREVIEW_LIMIT)}..."
        }
    }

    private companion object {
        private const val TAG = "QwenLiteRtLLM"
        private const val CACHE_DIR_NAME = "litertlm-cache"
        private const val MAX_NUM_TOKENS = 1024
        private const val TOP_K = 1
        private const val TOP_P = 0.9
        private const val TEMPERATURE = 0.2
        private const val CPU_THREAD_COUNT = 4
        private const val ENABLE_GPU_BACKEND = true
        private const val BUFFER_SIZE = 1024 * 1024
        private const val LOG_PREVIEW_LIMIT = 300
        private const val THINK_CLOSE_TAG = "</think>"
        private const val HANGUL_CYCLE = 28
        private const val HANGUL_START_CODE = 0xAC00
        private const val HANGUL_END_CODE = 0xD7A3
        private val HANGUL_START: Char = HANGUL_START_CODE.toChar()
        private val HANGUL_END: Char = HANGUL_END_CODE.toChar()
        private val NEGATIVE_TOKENS = setOf("아니다", "없다", "못", "안")
        private val TIME_TOKENS = setOf("어제", "오늘", "내일", "지금")
        private const val NONE_GLOSS = "none"
        private val goRestaurantOverride =
            ChildDirectedSentenceOverride(
                statement = "배고프면 아빠랑 식당에 가자.",
                question = "배고프면 아빠랑 식당에 갈까?",
            )
        private val comfortScaredOverride =
            ChildDirectedSentenceOverride(
                statement = "무서우면 아빠가 안아줄게.",
                question = "무서우면 아빠가 안아줄까?",
            )
        private val treatPainOverride =
            ChildDirectedSentenceOverride(
                statement = "아프면 아빠가 치료해줄게.",
                question = "아프면 아빠가 치료해줄까?",
            )
        private val carefulHandOverride =
            ChildDirectedSentenceOverride(
                statement = "손 조심해, 다치면 아파.",
                question = "손 조심할까? 다치면 아파.",
            )
        private val sleepBlanketOverride =
            ChildDirectedSentenceOverride(
                statement = "이불 덮고 자자.",
                question = "이불 덮고 잘까?",
            )
        private val coverBlanketOverride =
            ChildDirectedSentenceOverride(
                statement = "잘 때 아빠가 이불 덮어줄게.",
                question = "잘 때 아빠가 이불 덮어줄까?",
            )
        private val helpUnknownOverride =
            ChildDirectedSentenceOverride(
                statement = "모르면 아빠가 도와줄게.",
                question = "모르면 아빠가 도와줄까?",
            )
        private val comfortSadOverride =
            ChildDirectedSentenceOverride(
                statement = "슬프면 아빠가 안아줄게.",
                question = "슬프면 아빠가 안아줄까?",
            )
        private val carefulRainWalkOverride =
            ChildDirectedSentenceOverride(
                statement = "비 오니까 조심해, 다치면 아파.",
                question = "비 오니까 조심할까? 다치면 아파.",
            )
        private val carefulWalkOverride =
            ChildDirectedSentenceOverride(
                statement = "조심해서 걸어, 다치면 아파.",
                question = "조심해서 걸을까? 다치면 아파.",
            )
        private val carefulRainOverride =
            ChildDirectedSentenceOverride(
                statement = "비 오니까 조심하자.",
                question = "비 오니까 조심할까?",
            )
        private val comfortWindOverride =
            ChildDirectedSentenceOverride(
                statement = "바람이 무서우면 아빠가 안아줄게.",
                question = "바람이 무서우면 아빠가 안아줄까?",
            )
        private val comfortMistakeOverride =
            ChildDirectedSentenceOverride(
                statement = "실수해도 괜찮아, 아빠가 있어.",
                question = "실수해도 괜찮아, 아빠가 있을까?",
            )
        private val CHILD_DIRECTED_SENTENCE_OVERRIDES =
            mapOf(
                "배고프다 식당 엄마 가다" to goRestaurantOverride,
                "배고프다 엄마 식당" to goRestaurantOverride,
                "무섭다 엄마 위로" to comfortScaredOverride,
                "아프다 엄마 치료" to treatPainOverride,
                "손 조심 아프다" to carefulHandOverride,
                "이불 자다" to sleepBlanketOverride,
                "자다 엄마 이불" to coverBlanketOverride,
                "모르다 엄마 돕다" to helpUnknownOverride,
                "슬프다 엄마 위로" to comfortSadOverride,
                "비 조심 걷다 아프다" to carefulRainWalkOverride,
                "비 조심 아프다" to carefulRainWalkOverride,
                "조심 걷다 아프다" to carefulWalkOverride,
                "비 조심" to carefulRainOverride,
                "바람 무섭다 엄마 위로" to comfortWindOverride,
                "실수 괜찮다 엄마 위로" to comfortMistakeOverride,
            )
        private val NEGATIVE_PREDICATES =
            mapOf(
                "가다" to "가지 않습니다",
                "오다" to "오지 않습니다",
                "먹다" to "먹지 않습니다",
                "마시다" to "마시지 않습니다",
                "좋다" to "좋아하지 않습니다",
                "좋아" to "좋아하지 않습니다",
                "싫다" to "싫어하지 않습니다",
                "싫어" to "싫어하지 않습니다",
            )
        private val AFFIRMATIVE_PREDICATES =
            mapOf(
                "가다" to "갑니다",
                "오다" to "옵니다",
                "먹다" to "먹습니다",
                "마시다" to "마십니다",
                "좋다" to "좋습니다",
                "좋아" to "좋습니다",
                "싫다" to "싫습니다",
                "싫어" to "싫습니다",
                "아프다" to "아픕니다",
            )
        private val SYSTEM_INSTRUCTION =
            """
            너는 한국 수어 gloss를 자연스러운 한국어 한 문장으로 바꾸는 변환기다.

            상황:
            - 말하는 사람은 농인 부모 또는 보호자이고, 듣는 사람은 5세 이하 아이다.
            - 부모가 아이에게 직접 말하는 짧고 따뜻한 구어체 문장으로 만든다.
            - 현재 모델에는 `아빠` 라벨이 없으므로, `엄마`가 있으면 아빠 또는 보호자가 아이에게 말하는 상황으로 해석한다.
            - 출력에서 보호자 호칭이 필요하면 `엄마` 대신 `아빠`를 사용한다.
            - 직접 말투가 더 자연스러우면 보호자 호칭은 생략할 수 있다.
            - 수어 동작의 방향성은 항상 부모 또는 보호자가 아이에게 향하는 것으로 해석한다.
            - `주다`, `받다`, `돕다`, `위로`, `치료`는 부모가 아이에게 해 주는 말이나 행동으로 바꾼다.

            규칙:
            - 한국어 문장 하나만 출력한다.
            - 설명, 영어, 따옴표, 번호, 후보 문장은 쓰지 않는다.
            - 입력 gloss에 없는 사람, 장소, 시간, 감정, 이유는 새로 만들지 않는다.
            - `none`은 무시하고, 반복 단어는 한 번만 반영한다.
            - 단어 순서가 어색하면 의미를 유지한 채 자연스러운 한국어 어순으로만 바꾼다.
            - 의학적 진단, 원인, 처방은 추측하지 않는다.
            - 의미가 애매하면 아이에게 말해도 안전한 짧은 문장으로 만든다.
            - 예시처럼 짧고 다정한 보호자 말투를 우선 사용한다.
            - 문장 유형이 평서문이면 제안이나 약속 형태로 말한다.
            - 문장 유형이 의문문이면 아이에게 묻는 형태로 말한다.

            예시:
            gloss: 배고프다 식당 엄마 가다
            문장유형: 평서문
            출력: 배고프면 아빠랑 식당에 가자.

            gloss: 배고프다 엄마 식당
            문장유형: 의문문
            출력: 배고프면 아빠랑 식당에 갈까?

            gloss: 무섭다 엄마 위로
            문장유형: 평서문
            출력: 무서우면 아빠가 안아줄게.

            gloss: 무섭다 엄마 위로
            문장유형: 의문문
            출력: 무서우면 아빠가 안아줄까?

            gloss: 아프다 엄마 치료
            문장유형: 평서문
            출력: 아프면 아빠가 치료해줄게.

            gloss: 아프다 엄마 치료
            문장유형: 의문문
            출력: 아프면 아빠가 치료해줄까?

            gloss: 손 조심 아프다
            문장유형: 평서문
            출력: 손 조심해, 다치면 아파.

            gloss: 비 조심 걷다 아프다
            문장유형: 평서문
            출력: 비 오니까 조심해, 다치면 아파.

            gloss: 비 조심 아프다
            문장유형: 평서문
            출력: 비 오니까 조심해, 다치면 아파.

            gloss: 실수 괜찮다 엄마 위로
            문장유형: 평서문
            출력: 실수해도 괜찮아, 아빠가 있어.

            gloss: 이불 자다
            문장유형: 평서문
            출력: 이불 덮고 자자.

            gloss: 모르다 엄마 돕다
            문장유형: 평서문
            출력: 모르면 아빠가 도와줄게.

            gloss: 비 조심
            문장유형: 평서문
            출력: 비 오니까 조심하자.
            """.trimIndent()
    }
}
