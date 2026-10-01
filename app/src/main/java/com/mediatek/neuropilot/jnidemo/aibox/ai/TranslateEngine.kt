package com.mediatek.neuropilot.jnidemo.aibox.ai

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SpeechSegment(
    val id: Long,
    val sourceText: String,
    val sourceLang: String,
    val targetLang: String,
    val speaker: String?,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class SegmentTranslation(
    val segmentId: Long,
    val sourceText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val speaker: String?,
    val timestamp: Long
)

interface TranslationEngine {
    suspend fun translate(segment: SpeechSegment): SegmentTranslation?
    suspend fun summarize(transcriptText: String): String? = null
    fun close() = Unit
}

object TranslationPromptBuilder {
    fun build(segment: SpeechSegment): String {
        val source = languageName(segment.sourceLang)
        val target = languageName(segment.targetLang)
        return """
            Translate the following meeting transcript from $source to $target.
            Return only the translation. Do not explain, summarize, add labels, or repeat the source text.

            Text:
            ${segment.sourceText}
        """.trimIndent()
    }

    fun languageName(lang: String): String {
        return when (lang.lowercase()) {
            "vi", "vi-vn" -> "Vietnamese"
            "en", "en-us", "en-gb" -> "English"
            else -> lang
        }
    }
}

class LlmTranslationEngine(
    private val bridge: NeuroPilotLlmBridge
) : TranslationEngine {
    override suspend fun translate(segment: SpeechSegment): SegmentTranslation? {
        val prompt = TranslationPromptBuilder.build(segment)
        val translated = withContext(Dispatchers.IO) {
            bridge.computeStreamingBlocking(prompt).trim()
        }

        if (translated.isBlank()) {
            Log.w(TAG, "LLM returned blank translation for segment=${segment.id}")
            return null
        }

        return SegmentTranslation(
            segmentId = segment.id,
            sourceText = segment.sourceText,
            translatedText = translated,
            sourceLang = segment.sourceLang,
            targetLang = segment.targetLang,
            speaker = segment.speaker,
            timestamp = segment.timestamp
        )
    }

    override suspend fun summarize(transcriptText: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val result = bridge.summarizeBlocking(transcriptText).trim()
                if (result.isBlank()) null else result
            } catch (e: Exception) {
                Log.e(TAG, "Summarize failed", e)
                null
            }
        }
    }

    override fun close() {
        bridge.close()
    }

    private companion object {
        private const val TAG = "LlmTranslationEngine"
    }
}

class StubTranslationEngine(
    private val reason: String
) : TranslationEngine {
    override suspend fun translate(segment: SpeechSegment): SegmentTranslation? {
        Log.w(TAG, "Skip translation for segment=${segment.id}: $reason")
        return null
    }

    override suspend fun summarize(transcriptText: String): String? {
        Log.w(TAG, "Skip summarize: $reason")
        return null
    }

    private companion object {
        private const val TAG = "StubTranslationEngine"
    }
}

class LazyTranslationEngine(
    private val initializer: () -> TranslationEngine
) : TranslationEngine {
    @Volatile
    private var delegate: TranslationEngine? = null
    private val lock = Any()

    override suspend fun translate(segment: SpeechSegment): SegmentTranslation? {
        return getDelegate().translate(segment)
    }

    override suspend fun summarize(transcriptText: String): String? {
        return getDelegate().summarize(transcriptText)
    }

    override fun close() {
        delegate?.close()
    }

    private fun getDelegate(): TranslationEngine {
        delegate?.let { return it }
        return synchronized(lock) {
            delegate ?: initializer().also { delegate = it }
        }
    }
}

object TranslationEngineFactory {
    fun createDefault(): TranslationEngine {
        return try {
            val bridge = NeuroPilotLlmBridge()
            if (bridge.initDefaultModel()) {
                Log.i(TAG, "NeuroPilot LLM translation engine initialized")
                LlmTranslationEngine(bridge)
            } else {
                bridge.close()
                StubTranslationEngine("NeuroPilot native libraries or YAML/model path are not ready")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize NeuroPilot LLM translation engine", t)
            StubTranslationEngine(t.message ?: "unknown init failure")
        }
    }

    private const val TAG = "TranslationEngineFactory"
}

class TranslationWorker(
    scope: CoroutineScope,
    private val engine: TranslationEngine,
    queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val onTranslated: (SegmentTranslation) -> Unit,
    private val onError: (SpeechSegment, Throwable) -> Unit = { _, _ -> },
    private val onDropped: (SpeechSegment) -> Unit = {}
) {
    private val queue = Channel<SpeechSegment>(capacity = queueCapacity)
    private val job: Job = scope.launch(Dispatchers.IO) {
        for (segment in queue) {
            try {
                Log.d(TAG, "Translate segment=${segment.id}, ${segment.sourceLang}->${segment.targetLang}")
                val result = engine.translate(segment)
                if (result != null) {
                    withContext(Dispatchers.Main.immediate) {
                        onTranslated(result)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Translation failed for segment=${segment.id}", t)
                withContext(Dispatchers.Main.immediate) {
                    onError(segment, t)
                }
            }
        }
    }

    fun enqueue(segment: SpeechSegment) {
        val result = queue.trySend(segment)
        if (result.isSuccess) {
            Log.d(TAG, "Enqueued translation segment=${segment.id}")
        } else {
            Log.w(TAG, "Drop translation segment=${segment.id}: queue is full")
            onDropped(segment)
        }
    }

    fun close() {
        queue.close()
        job.cancel()
        engine.close()
    }

    private companion object {
        private const val TAG = "TranslationWorker"
        private const val DEFAULT_QUEUE_CAPACITY = 4
    }
}
