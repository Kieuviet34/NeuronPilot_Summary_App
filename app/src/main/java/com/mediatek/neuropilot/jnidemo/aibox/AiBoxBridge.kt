package com.mediatek.neuropilot.jnidemo.aibox

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import com.mediatek.neuropilot.jnidemo.aibox.ai.*
import com.mediatek.neuropilot.jnidemo.aibox.webview.AndroidBridge
import com.mediatek.neuropilot.jnidemo.aibox.webview.TranscriptPayload
import com.mediatek.neuropilot.jnidemo.aibox.webview.WebViewManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicLong

class AiBoxBridge(private val context: Context) {

    private var webView: WebView? = null
    private var webViewManager: WebViewManager? = null
    private var audioCapture: AudioCapture? = null
    private lateinit var sttEngine: STTEngine
    private lateinit var translationWorker: TranslationWorker
    private lateinit var translationEngine: LazyTranslationEngine
    private var androidBridge: AndroidBridge? = null

    private val nextSegmentId = AtomicLong(1L)
    private val sttQueue = Channel<Pair<ShortArray, String>>(capacity = 10)
    
    private val appJob = SupervisorJob()
    private val appScope = CoroutineScope(appJob + Dispatchers.Main.immediate)
    
    private var wasSpeakingBefore = false

    fun init(webView: WebView) {
        this.webView = webView
        
        sttEngine = WhisperServerSTTEngine()
        translationEngine = LazyTranslationEngine { TranslationEngineFactory.createDefault() }

        translationWorker = TranslationWorker(
            scope = appScope,
            engine = translationEngine,
            onTranslated = { result ->
                Log.d("AiBoxBridge", "Translation ready segment=${result.segmentId}")
                val payload = TranscriptPayload(
                    segmentId = result.segmentId,
                    text = result.sourceText,
                    translation = result.translatedText,
                    lang = result.sourceLang,
                    isFinal = true,
                    timestamp = result.timestamp,
                    speaker = result.speaker
                )

                TranscriptHistory.append(
                    speaker = result.speaker ?: "?",
                    sourceText = result.sourceText,
                    timestamp = result.timestamp
                )

                androidBridge?.fireTranscript(payload)
            }
        )

        appScope.launch(Dispatchers.IO) {
            for ((audioData, side) in sttQueue) {
                val langCode = if (side == "A") "vi" else "en"
                val targetLangCode = if (langCode == "vi") "en" else "vi"

                val text = try {
                    sttEngine.transcribe(audioData, 16000)
                } catch (e: Exception) {
                    "STT error: ${e.message ?: "unknown"}"
                }
                Log.e("AiBoxBridge", "RAW_STT_RESULT: '$text'")

                val displayText = TranscriptSanitizer.cleanOrNull(text)
                if (displayText == null) {
                    Log.d("AiBoxBridge", "Ignore unclear STT result")
                    continue
                }

                val timestamp = System.currentTimeMillis()
                translationWorker.enqueue(
                    SpeechSegment(
                        id = nextSegmentId.getAndIncrement(),
                        sourceText = displayText,
                        sourceLang = langCode,
                        targetLang = targetLangCode,
                        speaker = side,
                        startedAtMs = timestamp,
                        endedAtMs = timestamp,
                        timestamp = timestamp
                    )
                )
            }
        }

        audioCapture = AudioCapture(webView) { audioData, side, isFinal ->
            val langCode = if (side == "A") "vi" else "en"

            if (isFinal) {
                wasSpeakingBefore = false
                val result = sttQueue.trySend(audioData to side)
                if (!result.isSuccess) {
                    Log.w("AiBoxBridge", "STT queue full, dropped segment")
                }
            } else if (!wasSpeakingBefore) {
                wasSpeakingBefore = true
                androidBridge?.fireTranscript(
                    TranscriptPayload(
                        text = "Đang lắng nghe ...",
                        translation = null,
                        lang = langCode,
                        isFinal = false,
                        speaker = side
                    )
                )
            }
        }

        webViewManager = WebViewManager(context, webView, audioCapture!!)
        androidBridge = webViewManager!!.initWebView()
        webViewManager!!.loadApp()
    }

    fun onPause() {
        webView?.onPause()
        audioCapture?.stopCapture()
        wasSpeakingBefore = false
    }

    fun onResume() {
        webView?.onResume()
    }

    fun onDestroy() {
        appJob.cancel()
        sttQueue.close()
        translationWorker.close()
        androidBridge = null
        webView = null
    }

    fun showSummaryDialog() {
        if (TranscriptHistory.isEmpty()) {
             AlertDialog.Builder(context)
                .setTitle("Tóm tắt cuộc họp")
                .setMessage("Chưa có nội dung để tóm tắt")
                .setPositiveButton("Đóng", null)
                .show()
            return
        }

        appScope.launch {
            val transcriptText = TranscriptHistory.buildTranscriptText()
            val result = withContext(Dispatchers.IO) {
                try {
                    val bridge = NeuroPilotLlmBridge()
                    if (bridge.initDefaultModel()) {
                        val summary = bridge.summarizeBlocking(transcriptText)
                        bridge.close()
                        summary
                    } else {
                        bridge.close()
                        null
                    }
                } catch (e: Exception) {
                    Log.e("AiBoxBridge", "Summarize error", e)
                    null
                }
            }

            AlertDialog.Builder(context)
                .setTitle("Tóm tắt cuộc họp")
                .setMessage(result ?: "Không thể tạo bản tóm tắt. Vui lòng thử lại.")
                .setPositiveButton("Đóng", null)
                .show()
        }
    }
}
