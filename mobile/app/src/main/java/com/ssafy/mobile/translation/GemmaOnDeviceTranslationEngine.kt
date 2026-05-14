package com.ssafy.mobile.translation

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.ssafy.mobile.BuildConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class TranslationResult(
    val glossText: String,
    val koreanText: String,
    val rawText: String,
    val usedRuleBased: Boolean,
    val elapsedMs: Long,
)

@Suppress("TooManyFunctions", "MagicNumber", "ReturnCount", "MaxLineLength", "LargeClass")
class GemmaOnDeviceTranslationEngine(
    private val context: Context,
) : OnDeviceTranslationEngine {
    private var llmInference: LlmInference? = null
    private var loaded = false
    private var lastPreparedModelPath: String? = null
    private var lastPreparedEntryNames: List<String> = emptyList()
    private var lastBackendSummary: String = "Not loaded"
    private val loadMutex = Mutex()

    override val debugModelAssetPath: String
        get() = GEMMA_MODEL_ASSET_PATH

    override val debugPreparedModelPath: String?
        get() = lastPreparedModelPath

    override val debugPreparedEntryNames: List<String>
        get() = lastPreparedEntryNames

    override val debugMaxTokens: Int
        get() = MAX_TOKENS

    override val debugTopK: Int
        get() = TOP_K

    override val debugBackendSummary: String
        get() = lastBackendSummary

    @Suppress("TooGenericExceptionCaught")
    override suspend fun load() =
        withContext(Dispatchers.Default) {
            loadMutex.withLock {
                if (loaded) {
                    logDebug { "Gemma load skipped. backend=$lastBackendSummary" }
                    return@withLock
                }

                val loadStart = System.currentTimeMillis()
                logDebug {
                    "Gemma load start. asset=$GEMMA_MODEL_ASSET_PATH, " +
                        "maxTokens=$MAX_TOKENS, topK=$TOP_K"
                }
                val preparedModel = copyAssetToFile(assetName = GEMMA_MODEL_ASSET_PATH)
                lastPreparedModelPath = preparedModel.modelPath
                lastPreparedEntryNames = preparedModel.entryNames
                logDebug {
                    "Gemma model prepared. path=${preparedModel.modelPath}, " +
                        "entries=${preparedModel.entryNames.joinToString().toLogPreview()}"
                }
                val backendCandidates =
                    listOf(
                        LlmInference.Backend.GPU,
                        LlmInference.Backend.CPU,
                    )

                var lastError: Exception? = null
                for (backend in backendCandidates) {
                    try {
                        logDebug { "Gemma backend init start. preferredBackend=$backend" }
                        llmInference =
                            LlmInference.createFromOptions(
                                context,
                                buildLlmOptions(
                                    modelPath = preparedModel.modelPath,
                                    backend = backend,
                                ),
                            )
                        lastBackendSummary = "preferredBackend=$backend"
                        loaded = true
                        logDebug {
                            "Gemma backend init success. preferredBackend=$backend, " +
                                "elapsedMs=${System.currentTimeMillis() - loadStart}"
                        }
                        break
                    } catch (exception: Exception) {
                        lastError = exception
                        Log.w(
                            TAG,
                            "Gemma load failed for preferredBackend=$backend. Trying fallback.",
                            exception,
                        )
                    }
                }

                if (!loaded) {
                    lastBackendSummary = "load failed"
                    val failure = lastError ?: error("Failed to initialize Gemma backend.")
                    throw failure
                }
            }
        }

    override suspend fun translate(glossText: String): TranslationResult =
        withContext(Dispatchers.Default) {
            val normalizedGloss = normalizeGlossForPrompt(glossText)
            val start = System.currentTimeMillis()
            logDebug {
                "Gemma translate request. gloss=${glossText.toLogPreview()}, " +
                    "normalized=${normalizedGloss.toLogPreview()}"
            }
            tryRuleBasedTranslation(normalizedGloss)?.let { sentence ->
                val elapsed = System.currentTimeMillis() - start
                logDebug {
                    "Gemma translate rule-based result. " +
                        "normalized=${normalizedGloss.toLogPreview()}, " +
                        "result=${sentence.toLogPreview()}, elapsedMs=$elapsed"
                }
                return@withContext TranslationResult(
                    glossText = glossText,
                    koreanText = sentence,
                    rawText = sentence,
                    usedRuleBased = true,
                    elapsedMs = elapsed,
                )
            }

            if (!loaded) {
                load()
            }

            val prompt = buildPrompt(normalizedGloss)
            logDebug {
                "Gemma inference start. normalized=${normalizedGloss.toLogPreview()}, " +
                    "promptChars=${prompt.length}, " +
                    "promptTail=${prompt.takeLast(LOG_PREVIEW_LIMIT).toLogPreview()}"
            }
            val raw =
                requireNotNull(llmInference) {
                    "Gemma LLM must be loaded before translation."
                }.generateResponse(prompt)
            val elapsed = System.currentTimeMillis() - start
            val cleaned = cleanOutput(raw)
            val resolved =
                resolveGemmaOutput(
                    glossText = normalizedGloss,
                    cleaned = cleaned,
                    raw = raw,
                )

            logDebug {
                "Gemma inference result. normalized=${normalizedGloss.toLogPreview()}, " +
                    "raw=${raw.toLogPreview()}, cleaned=${cleaned.toLogPreview()}, " +
                    "resolved=${resolved.toLogPreview()}, elapsedMs=$elapsed, " +
                    "backend=$lastBackendSummary"
            }
            TranslationResult(
                glossText = glossText,
                koreanText = resolved,
                rawText = raw,
                usedRuleBased = false,
                elapsedMs = elapsed,
            )
        }

    override fun close() {
        llmInference?.close()
        llmInference = null
        loaded = false
    }

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

    private fun buildLlmOptions(
        modelPath: String,
        backend: LlmInference.Backend,
    ): LlmInference.LlmInferenceOptions =
        LlmInference.LlmInferenceOptions
            .builder()
            .setModelPath(modelPath)
            .setMaxTokens(MAX_TOKENS)
            .setMaxTopK(TOP_K)
            .setPreferredBackend(backend)
            .build()

    private fun cleanOutput(text: String): String {
        val normalized =
            text
                .replace(START_OF_TURN_MODEL, "\n")
                .replace(START_OF_TURN_USER, "\n")
                .replace(END_OF_TURN, "\n")
                .replace(BOS_TOKEN, "")
                .replace(EOS_TOKEN, "")

        val meaningfulLines =
            normalized
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { line ->
                    line
                        .removePrefix("한국어:")
                        .removePrefix("Korean:")
                        .removePrefix("정답:")
                        .trim()
                }.filterNot { line ->
                    line.isBlank() ||
                        line == "model" ||
                        line == "user" ||
                        line.startsWith("Gloss:") ||
                        line.startsWith("Korean sentence:") ||
                        line.startsWith("한국어 문장:")
                }.toList()

        val result =
            when {
                meaningfulLines.isEmpty() -> ""
                meaningfulLines.size == 1 -> meaningfulLines.first()
                else -> meaningfulLines.joinToString(" ")
            }

        return result
            .substringBefore("Gloss:")
            .substringBefore(END_OF_TURN)
            .trim()
            .trim('"')
    }

    private fun resolveGemmaOutput(
        glossText: String,
        cleaned: String,
        raw: String,
    ): String {
        val candidate = cleaned.ifBlank { raw.trim() }
        if (looksLikeGlossEcho(glossText, candidate)) {
            tryRuleBasedTranslation(glossText)?.let { return it }
            fallbackGlossSentence(glossText)?.let { return it }
        }

        return candidate.ifBlank {
            fallbackGlossSentence(glossText).orEmpty()
        }
    }

    private fun looksLikeGlossEcho(
        glossText: String,
        output: String,
    ): Boolean {
        val normalizedGloss = normalizeGlossLikeText(glossText)
        val normalizedOutput = normalizeGlossLikeText(output)
        if (normalizedOutput.isBlank()) return true
        return normalizedOutput == normalizedGloss || normalizedOutput.endsWith(normalizedGloss)
    }

    private fun normalizeGlossLikeText(text: String): String =
        normalizeGlossForPrompt(
            text
                .removePrefix("Gloss:")
                .removePrefix("Korean:")
                .replace(Regex("[.!?\",]"), " "),
        )

    private fun buildPrompt(glossText: String): String =
        buildPromptBody(
            glossText = glossText,
            examples = selectFewShotExamples(glossText),
        )

    private fun buildPromptBody(
        glossText: String,
        examples: List<FewShotExample>,
    ): String {
        val examplesBlock =
            examples.joinToString("\n\n") { example ->
                "Gloss: ${example.gloss}\n한국어: ${example.korean}"
            }

        return """
            $START_OF_TURN_USER
            너는 한국 수어 gloss를 자연스러운 한국어 문장으로 바꾸는 변환기다.
            아래 규칙을 반드시 지켜라.

            1. 출력은 한국어 문장 하나만 쓴다.
            2. Gloss, 한국어, 설명, 영어, 따옴표, 번호를 출력하지 않는다.
            3. 입력 gloss에 없는 행동, 장소, 시제, 부정, 감정을 만들지 않는다.
            4. gloss의 단어 순서가 어색하면 한국어 어순으로 자연스럽게 재배열한다.
            5. 조사와 어미를 자연스럽게 붙인다. 예: 학교 가다 -> 학교에 간다.
            6. 어제/오늘/지금/내일 같은 시간 표현이 있으면 시제에 반영한다.
            7. 아니다/없다 같은 부정 표현이 있으면 부정문으로 바꾼다.
            8. 명사만 있으면 "~입니다." 형태의 짧은 문장으로 답한다.

            $examplesBlock

            Gloss: $glossText
            한국어:
            $END_OF_TURN
            $START_OF_TURN_MODEL
            """.trimIndent()
    }

    private fun normalizeGlossForPrompt(glossText: String): String =
        glossText
            .lineSequence()
            .joinToString(" ") { it.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun tryRuleBasedTranslation(glossText: String): String? {
        val tokens = tokenizeGloss(glossText)
        if (tokens.size !in 2..5) return null
        if (tokens.any { it in COMPLEXITY_TOKENS && it !in TOGETHER_TOKENS }) return null

        val subjectToken = tokens.firstOrNull()?.takeIf { it in SUBJECT_PHRASES }
        val timeToken = tokens.firstOrNull { it in TIME_TOKENS }
        val togetherToken = tokens.firstOrNull { it in TOGETHER_TOKENS }
        val hasNegation = tokens.any { it in NEGATION_TOKENS }

        val coreTokens =
            tokens.filterNot { token ->
                token == subjectToken ||
                    token == timeToken ||
                    token == togetherToken ||
                    token in NEGATION_TOKENS
            }
        if (coreTokens.isEmpty()) return null

        val normalizedCoreTokens = coreTokens.map(::normalizePredicateToken)
        val predicateIndex =
            normalizedCoreTokens.indexOfLast { normalizedToken ->
                normalizedToken in VERB_FORMS
            }
        if (predicateIndex < 0) return null

        val predicate = normalizedCoreTokens[predicateIndex]
        val remainingTokens = coreTokens.filterIndexed { index, _ -> index != predicateIndex }

        val sentence =
            when (predicate) {
                "좋아하다",
                "싫어하다",
                ->
                    buildPreferenceSentence(
                        subjectToken = subjectToken,
                        timeToken = timeToken,
                        togetherToken = togetherToken,
                        remainingTokens = remainingTokens,
                        predicate = predicate,
                        hasNegation = hasNegation,
                    )

                else ->
                    buildSimpleSentence(
                        subjectToken = subjectToken,
                        timeToken = timeToken,
                        togetherToken = togetherToken,
                        remainingTokens = remainingTokens,
                        predicate = predicate,
                        hasNegation = hasNegation,
                    )
            }

        return sentence?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun buildSimpleSentence(
        subjectToken: String?,
        timeToken: String?,
        togetherToken: String?,
        remainingTokens: List<String>,
        predicate: String,
        hasNegation: Boolean,
    ): String? {
        if (remainingTokens.size > 1) return null

        val contentToken = remainingTokens.firstOrNull()
        val contentPhrase =
            when {
                contentToken == null -> null
                predicate in MOVEMENT_PREDICATES -> {
                    if (contentToken !in PLACE_TOKENS) return null
                    contentToken + PLACE_PARTICLE
                }

                predicate in COMPANION_PREDICATES ->
                    contentToken + companionParticle(contentToken)
                predicate in OBJECT_PREDICATES ->
                    contentToken + objectParticle(contentToken)
                predicate in SUBJECT_COMPLEMENT_PREDICATES ->
                    contentToken + subjectParticle(contentToken)
                else -> null
            }

        val predicatePhrase = predicateSurface(predicate, timeToken, hasNegation)
        return composeSentence(
            subjectToken = subjectToken,
            timeToken = timeToken,
            adverbPhrase = togetherPhrase(togetherToken),
            contentPhrase = contentPhrase,
            predicatePhrase = predicatePhrase,
        )
    }

    private fun buildPreferenceSentence(
        subjectToken: String?,
        timeToken: String?,
        togetherToken: String?,
        remainingTokens: List<String>,
        predicate: String,
        hasNegation: Boolean,
    ): String? {
        val contentPhrase =
            when (remainingTokens.size) {
                1 -> remainingTokens.first() + objectParticle(remainingTokens.first())
                2 ->
                    nominalizePhrase(
                        nounToken = remainingTokens[0],
                        predicate = normalizePredicateToken(remainingTokens[1]),
                    ) ?: return null
                else -> return null
            }

        val predicatePhrase = predicateSurface(predicate, timeToken, hasNegation)
        return composeSentence(
            subjectToken = subjectToken,
            timeToken = timeToken,
            adverbPhrase = togetherPhrase(togetherToken),
            contentPhrase = contentPhrase,
            predicatePhrase = predicatePhrase,
        )
    }

    private fun predicateSurface(
        predicate: String,
        timeToken: String?,
        hasNegation: Boolean,
    ): String {
        val forms = VERB_FORMS.getValue(predicate)
        return when {
            hasNegation -> forms.negative
            timeToken == PAST_TIME_TOKEN -> forms.past
            else -> forms.present
        }
    }

    private fun composeSentence(
        subjectToken: String?,
        timeToken: String?,
        adverbPhrase: String?,
        contentPhrase: String?,
        predicatePhrase: String,
    ): String =
        buildList {
            subjectToken?.let { add(SUBJECT_PHRASES.getValue(it)) }
            timeToken?.let { add(it) }
            adverbPhrase?.let { add(it) }
            contentPhrase?.let { add(it) }
            add(predicatePhrase)
        }.joinToString(" ") + "."

    private fun togetherPhrase(token: String?): String? = token?.let { TOGETHER_ADVERB }

    private fun nominalizePhrase(
        nounToken: String,
        predicate: String,
    ): String? =
        when (predicate) {
            "먹다" -> nounToken + objectParticle(nounToken) + " 먹는 것을"
            "마시다" -> nounToken + objectParticle(nounToken) + " 마시는 것을"
            "보다" -> nounToken + objectParticle(nounToken) + " 보는 것을"
            else -> null
        }

    private fun selectFewShotExamples(glossText: String): List<FewShotExample> {
        val glossTokens = tokenizeGloss(glossText)
        val exampleLimit = if (glossTokens.size <= SHORT_GLOSS_TOKEN_LIMIT) 4 else 3

        return FEW_SHOT_EXAMPLES
            .map { example -> example to scoreExample(example, glossTokens) }
            .sortedByDescending { (_, score) -> score }
            .take(exampleLimit)
            .map { (example, _) -> example }
    }

    private fun scoreExample(
        example: FewShotExample,
        glossTokens: List<String>,
    ): Int {
        val exampleTokens = tokenizeGloss(example.gloss)
        val glossTokenSet = glossTokens.toSet()
        val exampleTokenSet = exampleTokens.toSet()
        val overlapScore = glossTokenSet.intersect(exampleTokenSet).size * 5
        val glossPredicate = extractMainPredicate(glossTokens)
        val examplePredicate = extractMainPredicate(exampleTokens)
        val predicateScore = if (glossPredicate == examplePredicate) 4 else 0
        val timeScore =
            if (
                glossTokens.any { token -> token in TIME_TOKENS } &&
                exampleTokens.any { token -> token in TIME_TOKENS }
            ) {
                2
            } else {
                0
            }
        val negationScore =
            if (
                glossTokens.any { token -> token in NEGATION_TOKENS } &&
                exampleTokens.any { token -> token in NEGATION_TOKENS }
            ) {
                3
            } else {
                0
            }

        return overlapScore + predicateScore + timeScore + negationScore
    }

    private fun fallbackGlossSentence(glossText: String): String? {
        tryRuleBasedTranslation(glossText)?.let { return it }

        val tokens =
            tokenizeGloss(glossText)
                .filterNot { token -> token.lowercase() in IGNORED_GLOSS_TOKENS }
                .distinct()
                .map(::normalizePredicateToken)
        if (tokens.isEmpty()) return null

        if (tokens.all { token -> token in NOUN_TOKENS }) {
            return buildNounOnlySentence(tokens)
        }

        if (tokens.size == 1) {
            STANDALONE_SENTENCES[tokens.first()]?.let { return it }
        }

        return null
    }

    private fun buildNounOnlySentence(tokens: List<String>): String {
        val phrase =
            when (tokens.size) {
                1 -> tokens.first()
                else ->
                    tokens
                        .dropLast(1)
                        .joinToString(" ") { token -> token + companionParticle(token) } +
                        " " +
                        tokens.last()
            }
        return "${phrase}입니다."
    }

    private fun tokenizeGloss(glossText: String): List<String> =
        glossText.split(' ').map { it.trim() }.filter { it.isNotBlank() }

    private fun normalizePredicateToken(token: String): String =
        when (token) {
            "좋아" -> "좋아하다"
            "싫어" -> "싫어하다"
            else -> token
        }

    private fun extractMainPredicate(tokens: List<String>): String? =
        tokens.map(::normalizePredicateToken).lastOrNull { normalizedToken ->
            normalizedToken in VERB_FORMS
        }

    private fun objectParticle(token: String): String = if (hasBatchim(token)) "을" else "를"

    private fun subjectParticle(token: String): String = if (hasBatchim(token)) "이" else "가"

    private fun companionParticle(token: String): String = if (hasBatchim(token)) "과" else "와"

    private fun hasBatchim(token: String): Boolean {
        val lastChar = token.lastOrNull() ?: return false
        if (lastChar !in HANGUL_START..HANGUL_END) return false
        return (lastChar.code - HANGUL_START.code) % HANGUL_CYCLE != 0
    }

    private fun copyAssetToFile(assetName: String): PreparedModel {
        val fileName = assetName.substringAfterLast("/")
        val outFile = File(context.filesDir, fileName)
        var hadZeroPrefixedZipHeader = false

        context.assets.open(assetName).use { assetInput ->
            BufferedInputStream(assetInput).use { bufferedInput ->
                bufferedInput.mark(TASK_HEADER_SIZE)
                val header = ByteArray(TASK_HEADER_SIZE)
                val headerBytesRead = bufferedInput.read(header)
                bufferedInput.reset()

                hadZeroPrefixedZipHeader =
                    hasZeroPrefixedZipHeader(header = header, headerBytesRead = headerBytesRead)

                if (hadZeroPrefixedZipHeader) {
                    bufferedInput.skipExact(INVALID_PREFIX_SIZE.toLong())
                }

                FileOutputStream(outFile, false).use { fileOutput ->
                    bufferedInput.copyTo(fileOutput, BUFFER_SIZE)
                }
            }
        }

        if (hadZeroPrefixedZipHeader) {
            fixZipOffsetsAfterRemovingPrefix(
                file = outFile,
                removedPrefixSize = INVALID_PREFIX_SIZE,
            )
        }

        val entryNames =
            ZipFile(outFile).use { zipFile ->
                val entries = zipFile.entries()
                generateSequence {
                    if (entries.hasMoreElements()) entries.nextElement() else null
                }.map { it.name }.toList()
            }

        require(entryNames.contains("TOKENIZER_MODEL")) {
            "Prepared task bundle is missing TOKENIZER_MODEL. Entries=$entryNames"
        }
        require(entryNames.contains("METADATA")) {
            "Prepared task bundle is missing METADATA. Entries=$entryNames"
        }

        return PreparedModel(
            modelPath = outFile.absolutePath,
            entryNames = entryNames.toList(),
        )
    }

    private fun hasZeroPrefixedZipHeader(
        header: ByteArray,
        headerBytesRead: Int,
    ): Boolean =
        headerBytesRead == TASK_HEADER_SIZE &&
            header[0] == 0.toByte() &&
            header[1] == 0.toByte() &&
            header[2] == 0.toByte() &&
            header[3] == 0.toByte() &&
            header[4] == ZIP_MAGIC_P &&
            header[5] == ZIP_MAGIC_K &&
            header[6] == ZIP_MAGIC_3 &&
            header[7] == ZIP_MAGIC_4

    private fun BufferedInputStream.skipExact(length: Long) {
        var remaining = length
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }

            val fallback = read()
            require(fallback != -1) {
                "Unexpected end of file while skipping $length bytes."
            }
            remaining -= 1
        }
    }

    private fun fixZipOffsetsAfterRemovingPrefix(
        file: File,
        removedPrefixSize: Int,
    ) {
        RandomAccessFile(file, "rw").use { randomAccessFile ->
            val endOfCentralDirectoryOffset = findEndOfCentralDirectoryOffset(randomAccessFile)
            val endOfCentralDirectory = ByteArray(END_OF_CENTRAL_DIRECTORY_SIZE)

            randomAccessFile.seek(endOfCentralDirectoryOffset)
            randomAccessFile.readFully(endOfCentralDirectory)

            val totalEntries = readLittleEndianUnsignedShort(endOfCentralDirectory, offset = 10)
            val centralDirectorySize =
                readLittleEndianUnsignedInt(
                    endOfCentralDirectory,
                    offset = 12,
                )
            val recordedCentralDirectoryOffset =
                readLittleEndianUnsignedInt(endOfCentralDirectory, offset = 16)
            require(recordedCentralDirectoryOffset >= removedPrefixSize) {
                "Central directory offset is smaller than removed prefix size."
            }

            val actualCentralDirectoryOffset = endOfCentralDirectoryOffset - centralDirectorySize
            val normalizedCentralDirectoryOffset =
                recordedCentralDirectoryOffset - removedPrefixSize.toLong()

            require(actualCentralDirectoryOffset == normalizedCentralDirectoryOffset) {
                "Central directory location mismatch. " +
                    "actual=$actualCentralDirectoryOffset recorded=$recordedCentralDirectoryOffset"
            }

            writeLittleEndianInt(
                randomAccessFile = randomAccessFile,
                position = endOfCentralDirectoryOffset + EOCD_CENTRAL_DIRECTORY_OFFSET_FIELD_OFFSET,
                value = normalizedCentralDirectoryOffset,
            )

            var entryOffset = actualCentralDirectoryOffset
            repeat(totalEntries) {
                val centralDirectoryEntryHeader = ByteArray(CENTRAL_DIRECTORY_ENTRY_FIXED_SIZE)
                randomAccessFile.seek(entryOffset)
                randomAccessFile.readFully(centralDirectoryEntryHeader)

                val signature = readLittleEndianUnsignedInt(centralDirectoryEntryHeader, offset = 0)
                if (signature != CENTRAL_DIRECTORY_SIGNATURE.toLong()) {
                    throw ZipException(
                        "Unexpected central directory signature: 0x${signature.toString(16)}",
                    )
                }

                val fileNameLength =
                    readLittleEndianUnsignedShort(centralDirectoryEntryHeader, offset = 28)
                val extraFieldLength =
                    readLittleEndianUnsignedShort(centralDirectoryEntryHeader, offset = 30)
                val commentLength =
                    readLittleEndianUnsignedShort(centralDirectoryEntryHeader, offset = 32)
                val recordedLocalHeaderOffset =
                    readLittleEndianUnsignedInt(
                        centralDirectoryEntryHeader,
                        offset = CENTRAL_DIRECTORY_RELATIVE_OFFSET_FIELD_OFFSET.toInt(),
                    )

                require(recordedLocalHeaderOffset >= removedPrefixSize) {
                    "Local header offset is smaller than removed prefix size."
                }

                writeLittleEndianInt(
                    randomAccessFile = randomAccessFile,
                    position = entryOffset + CENTRAL_DIRECTORY_RELATIVE_OFFSET_FIELD_OFFSET,
                    value = recordedLocalHeaderOffset - removedPrefixSize.toLong(),
                )

                entryOffset +=
                    CENTRAL_DIRECTORY_ENTRY_FIXED_SIZE +
                    fileNameLength +
                    extraFieldLength +
                    commentLength
            }

            require(entryOffset == endOfCentralDirectoryOffset) {
                "Central directory size mismatch. entryOffset=$entryOffset eocd=$endOfCentralDirectoryOffset"
            }
        }
    }

    private fun findEndOfCentralDirectoryOffset(randomAccessFile: RandomAccessFile): Long {
        val searchSize = minOf(randomAccessFile.length(), MAX_EOCD_SEARCH_SIZE.toLong()).toInt()
        val searchBuffer = ByteArray(searchSize)

        randomAccessFile.seek(randomAccessFile.length() - searchSize)
        randomAccessFile.readFully(searchBuffer)

        for (index in searchSize - END_OF_CENTRAL_DIRECTORY_SIZE downTo 0) {
            val signature = readLittleEndianUnsignedInt(searchBuffer, offset = index)
            if (signature == END_OF_CENTRAL_DIRECTORY_SIGNATURE.toLong()) {
                return randomAccessFile.length() - searchSize + index
            }
        }

        throw ZipException("END header not found while normalizing task archive.")
    }

    private fun readLittleEndianUnsignedShort(
        buffer: ByteArray,
        offset: Int,
    ): Int =
        (buffer[offset].toInt() and 0xFF) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 8)

    private fun readLittleEndianUnsignedInt(
        buffer: ByteArray,
        offset: Int,
    ): Long =
        (buffer[offset].toLong() and 0xFF) or
            ((buffer[offset + 1].toLong() and 0xFF) shl 8) or
            ((buffer[offset + 2].toLong() and 0xFF) shl 16) or
            ((buffer[offset + 3].toLong() and 0xFF) shl 24)

    private fun writeLittleEndianInt(
        randomAccessFile: RandomAccessFile,
        position: Long,
        value: Long,
    ) {
        require(value in 0..MAX_UNSIGNED_INT) {
            "Value out of range for 4-byte unsigned integer: $value"
        }

        randomAccessFile.seek(position)
        randomAccessFile.write(
            byteArrayOf(
                (value and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 24) and 0xFF).toByte(),
            ),
        )
    }

    private data class FewShotExample(
        val gloss: String,
        val korean: String,
    )

    private data class VerbForms(
        val present: String,
        val past: String,
        val negative: String,
    )

    private data class PreparedModel(
        val modelPath: String,
        val entryNames: List<String>,
    )

    private companion object {
        private const val TAG = "GemmaOnDeviceLLM"
        private const val START_OF_TURN_USER = "<start_of_turn>user"
        private const val START_OF_TURN_MODEL = "<start_of_turn>model"
        private const val END_OF_TURN = "<end_of_turn>"
        private const val BOS_TOKEN = "<bos>"
        private const val EOS_TOKEN = "<eos>"
        private const val PAST_TIME_TOKEN = "어제"
        private const val SHORT_GLOSS_TOKEN_LIMIT = 4
        private const val TOGETHER_ADVERB = "\uAC19\uC774"
        private const val PLACE_PARTICLE = "에"
        private const val HANGUL_CYCLE = 28
        private const val HANGUL_START_CODE = 0xAC00
        private const val HANGUL_END_CODE = 0xD7A3
        const val GEMMA_MODEL_ASSET_PATH = "models/gemma3-1b-it-int4.task"
        const val MAX_TOKENS = 1024
        const val TOP_K = 1
        const val LOG_PREVIEW_LIMIT = 300
        const val TASK_HEADER_SIZE = 8
        const val END_OF_CENTRAL_DIRECTORY_SIZE = 22
        const val CENTRAL_DIRECTORY_ENTRY_FIXED_SIZE = 46
        const val CENTRAL_DIRECTORY_RELATIVE_OFFSET_FIELD_OFFSET = 42L
        const val EOCD_CENTRAL_DIRECTORY_OFFSET_FIELD_OFFSET = 16L
        const val MAX_EOCD_SEARCH_SIZE = END_OF_CENTRAL_DIRECTORY_SIZE + 65_535
        const val INVALID_PREFIX_SIZE = 4
        const val MAX_UNSIGNED_INT = 0xFFFF_FFFFL
        const val BUFFER_SIZE = 64 * 1024
        const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50
        const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054B50
        const val ZIP_MAGIC_P = 0x50.toByte()
        const val ZIP_MAGIC_K = 0x4B.toByte()
        const val ZIP_MAGIC_3 = 0x03.toByte()
        const val ZIP_MAGIC_4 = 0x04.toByte()
        val HANGUL_START: Char = HANGUL_START_CODE.toChar()
        val HANGUL_END: Char = HANGUL_END_CODE.toChar()
        val TOGETHER_TOKENS = setOf("\uAC19\uC774", "\uD568\uAED8")
        val SUBJECT_PHRASES =
            mapOf(
                "나" to "나는",
                "저" to "저는",
                "너" to "너는",
                "엄마" to "엄마는",
                "아빠" to "아빠는",
                "아이" to "아이는",
                "아기" to "아기는",
                "친구" to "친구는",
                "선생님" to "선생님은",
                "할머니" to "할머니는",
                "할아버지" to "할아버지는",
                "우리" to "우리는",
            )
        val TIME_TOKENS = setOf("오늘", "어제", "내일", "지금")
        val NEGATION_TOKENS = setOf("안", "못", "아니다")
        val COMPLEXITY_TOKENS = setOf("함께", "같이", "그리고", "그래서", "하지만", "왜냐하면")
        val PLACE_TOKENS = setOf("학교", "집", "시장", "병원", "회사", "공원", "유치원")
        val MOVEMENT_PREDICATES = setOf("가다", "오다")
        val COMPANION_PREDICATES = setOf("놀다")
        val OBJECT_PREDICATES = setOf("먹다", "마시다", "만나다", "보다", "사다", "좋아하다", "싫어하다")
        val SUBJECT_COMPLEMENT_PREDICATES =
            setOf("좋다", "싫다", "아프다", "배고프다", "목마르다", "무섭다")
        val VERB_FORMS =
            mapOf(
                "가다" to VerbForms("간다", "갔다", "가지 않는다"),
                "오다" to VerbForms("온다", "왔다", "오지 않는다"),
                "먹다" to VerbForms("먹는다", "먹었다", "먹지 않는다"),
                "마시다" to VerbForms("마신다", "마셨다", "마시지 않는다"),
                "만나다" to VerbForms("만난다", "만났다", "만나지 않는다"),
                "보다" to VerbForms("본다", "봤다", "보지 않는다"),
                "놀다" to VerbForms("논다", "놀았다", "놀지 않는다"),
                "자다" to VerbForms("잔다", "잤다", "자지 않는다"),
                "공부하다" to VerbForms("공부한다", "공부했다", "공부하지 않는다"),
                "사다" to VerbForms("산다", "샀다", "사지 않는다"),
                "좋아하다" to VerbForms("좋아한다", "좋아했다", "좋아하지 않는다"),
                "싫어하다" to VerbForms("싫어한다", "싫어했다", "싫어하지 않는다"),
                "좋다" to VerbForms("좋다", "좋았다", "좋지 않다"),
                "싫다" to VerbForms("싫다", "싫었다", "싫지 않다"),
                "배고프다" to VerbForms("배고프다", "배고팠다", "배고프지 않다"),
                "아프다" to VerbForms("아프다", "아팠다", "아프지 않다"),
                "목마르다" to VerbForms("목마르다", "목말랐다", "목마르지 않다"),
                "무섭다" to VerbForms("무섭다", "무서웠다", "무섭지 않다"),
            )
        val IGNORED_GLOSS_TOKENS = setOf("none", "unknown", "<none>", "<unknown>")
        val NOUN_TOKENS =
            setOf(
                "아기",
                "아이",
                "감기",
                "기차",
                "밥",
                "손",
                "장난감",
                "병원",
                "엄마",
            )
        val STANDALONE_SENTENCES =
            mapOf(
                "감사" to "감사합니다.",
                "조심" to "조심하세요.",
                "좋다" to "좋습니다.",
                "싫다" to "싫습니다.",
                "배고프다" to "배고픕니다.",
                "목마르다" to "목마릅니다.",
                "무섭다" to "무섭습니다.",
                "아프다" to "아픕니다.",
            )
        val FEW_SHOT_EXAMPLES =
            listOf(
                FewShotExample("엄마", "엄마입니다."),
                FewShotExample("기차", "기차입니다."),
                FewShotExample("엄마 기차", "엄마와 기차입니다."),
                FewShotExample("아이 좋다", "아이는 좋다."),
                FewShotExample("아이 가다", "아이는 간다."),
                FewShotExample("감사", "감사합니다."),
                FewShotExample("아프다", "아픕니다."),
                FewShotExample("배고프다", "배고픕니다."),
                FewShotExample("학교 가다 어제", "어제 학교에 갔다."),
                FewShotExample("나 학교 가다 내일", "나는 내일 학교에 간다."),
                FewShotExample("나 먹다 밥 어제", "나는 어제 밥을 먹었다."),
                FewShotExample("나 내일 학교 가다 아니다", "나는 내일 학교에 가지 않는다."),
                FewShotExample("밥 먹다 나 지금", "나는 지금 밥을 먹는다."),
                FewShotExample("친구 만나다 어제", "어제 친구를 만났다."),
                FewShotExample("엄마 밥 먹다 좋아", "엄마는 밥 먹는 것을 좋아한다."),
                FewShotExample("집 오다 지금", "지금 집에 온다."),
                FewShotExample("나 배고프다 지금", "나는 지금 배고프다."),
                FewShotExample("나 아프다 오늘", "나는 오늘 아프다."),
            )
    }
}
