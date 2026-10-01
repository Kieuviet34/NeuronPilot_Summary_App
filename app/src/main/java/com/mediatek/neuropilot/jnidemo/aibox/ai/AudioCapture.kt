package com.mediatek.neuropilot.jnidemo.aibox.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

class AudioCapture(
    private val webView: WebView,
    private val onAudioFrameReady: (audioData: ShortArray, side: String, isFinal: Boolean) -> Unit
) {
    private val TAG = "AudioCapture"

    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFET_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2

    private val VAD_START_MS = 250L      // 100 -> 250: cần giọng liên tục dài hơn mới coi là bắt đầu nói thật
    private val VAD_END_MS = 1000L       // giữ nguyên, ổn
    private val MIN_SPEECH_MS = 1200L    // 800 -> 1200: loại các câu quá ngắn (thường là nhiễu/từ đơn lẻ dễ sai)
    private val HARD_CAP_MS = 15000L
    private val FRAME_VOICE_DBFS = -42.0 // -45 -> -42: nâng ngưỡng "có giọng" lên, bớt nhạy với tạp âm nền
    private val MIN_FINAL_DBFS = -50.0   // -58 -> -50: siết ngưỡng chấp nhận segment, giọng phải rõ hơn mới gửi STT
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)

    private var speechStartTime = 0L
    private var isSpeaking = false
    private var currentMicSide = "A"
    private var pendingVoiceMs = 0L
    private var silenceMs = 0L
    private val pendingStartBuffer = ArrayList<Short>()
    private val currentSegmentBuffer = ArrayList<Short>()

    init {
        val msg = "[INIT] AudioCapture initialized (SampleRate: $SAMPLE_RATE Hz, BufferSize: $BUFFET_SIZE)"
        Log.d(TAG, msg)
        logToChromeConsole("info", msg)
    }

    private fun logToChromeConsole(level: String, message: String) {
        val escapedMessage = message.replace("'", "\\'")
        val jsCommand = when (level) {
            "error" -> "console.error('%c[Android Native - AudioCapture]%c $escapedMessage', 'color: white; background: #dc3545; padding: 2px 5px; border-radius: 3px;', '');"
            "warn" -> "console.warn('%c[Android Native - AudioCapture]%c $escapedMessage', 'color: black; background: #ffc107; padding: 2px 5px; border-radius: 3px;', '');"
            "info" -> "console.info('%c[Android Native - AudioCapture]%c $escapedMessage', 'color: white; background: #17a2b8; padding: 2px 5px; border-radius: 3px;', '');"
            else -> "console.log('%c[Android Native - AudioCapture]%c $escapedMessage', 'color: white; background: #6c757d; padding: 2px 5px; border-radius: 3px;', '');"
        }

        webView.post {
            webView.evaluateJavascript(jsCommand, null)
        }
    }

    fun setActiveMicSide(side: String) {
        currentMicSide = side
        val msg = "[CALL] setActiveMicSide() -> active mic side = $side"
        Log.d(TAG, msg)
        logToChromeConsole("log", msg)
    }

    @SuppressLint("MissingPermission")
    fun startCapture() {
        val msgCall = "[CALL] startCapture()"
        Log.d(TAG, msgCall)
        logToChromeConsole("log", msgCall)

        if (isRecording.get()) {
            val msgWarn = "startCapture() ignored: already recording"
            Log.w(TAG, msgWarn)
            logToChromeConsole("warn", msgWarn)
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFET_SIZE
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                val msgError = "AudioRecord init failed"
                Log.e(TAG, msgError)
                logToChromeConsole("error", msgError)
                return
            }

            resetVadState()
            isRecording.set(true)
            audioRecord?.startRecording()

            val msgSuccess = "Mic started successfully"
            Log.d(TAG, msgSuccess)
            logToChromeConsole("info", msgSuccess)

            recordingThread = Thread({ readAudioData() }, "AudioCapture-Thread")
            recordingThread?.start()
        } catch (e: Exception) {
            val msgErr = "startCapture error: ${e.message}"
            Log.e(TAG, msgErr)
            logToChromeConsole("error", msgErr)
        }
    }

    fun stopCapture() {
        val msgCall = "[CALL] stopCapture()"
        Log.d(TAG, msgCall)
        logToChromeConsole("log", msgCall)

        if (!isRecording.get()) {
            val msgWarn = "stopCapture() ignored: not recording"
            Log.w(TAG, msgWarn)
            logToChromeConsole("warn", msgWarn)
            return
        }

        isRecording.set(false)

        try {
            finalizeCurrentSegment("stop")

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            recordingThread?.join(1000)
            recordingThread = null

            val msgSuccess = "Audio device released"
            Log.d(TAG, msgSuccess)
            logToChromeConsole("info", msgSuccess)
        } catch (e: Exception) {
            val msgErr = "stopCapture error: ${e.message}"
            Log.e(TAG, msgErr)
            logToChromeConsole("error", msgErr)
        }
    }

    private fun readAudioData() {
        val msgLoop = "readAudioData() loop started"
        Log.d(TAG, msgLoop)
        logToChromeConsole("info", msgLoop)

        val audioBuffer = ShortArray(BUFFET_SIZE / 2)

        while (isRecording.get()) {
            val readSize = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
            if (readSize <= 0) {
                Log.w(TAG, "Audio read failed or frame size = 0")
                continue
            }

            val frame = audioBuffer.copyOfRange(0, readSize)
            val frameMs = frameDurationMs(readSize)
            val currentTime = System.currentTimeMillis()

            if (isVoiceFrame(frame)) {
                handleVoiceFrame(frame, frameMs, currentTime)
            } else {
                handleSilentFrame(frame, frameMs)
            }
        }

        val msgExit = "readAudioData() loop exited"
        Log.d(TAG, msgExit)
        logToChromeConsole("log", msgExit)
    }

    private fun handleVoiceFrame(frame: ShortArray, frameMs: Long, currentTime: Long) {
        silenceMs = 0L

        if (!isSpeaking) {
            appendToBuffer(pendingStartBuffer, frame)
            pendingVoiceMs += frameMs

            if (pendingVoiceMs >= VAD_START_MS) {
                isSpeaking = true
                speechStartTime = currentTime - pendingVoiceMs
                currentSegmentBuffer.clear()
                appendToBuffer(currentSegmentBuffer, pendingStartBuffer)
                pendingStartBuffer.clear()
                pendingVoiceMs = 0L

                val msgVadStart = "Voice confirmed on side $currentMicSide"
                Log.i(TAG, msgVadStart)
                logToChromeConsole("info", msgVadStart)
                onAudioFrameReady(frame, currentMicSide, false)
            }
            return
        }

        appendToBuffer(currentSegmentBuffer, frame)
        onAudioFrameReady(frame, currentMicSide, false)

        val speechDuration = currentTime - speechStartTime
        if (speechDuration >= HARD_CAP_MS) {
            val msgHardCap = "Hard cap reached after ${speechDuration / 1000}s"
            Log.w(TAG, msgHardCap)
            logToChromeConsole("warn", msgHardCap)
            finalizeCurrentSegment("hard_cap")
        }
    }

    private fun handleSilentFrame(frame: ShortArray, frameMs: Long) {
        if (!isSpeaking) {
            pendingStartBuffer.clear()
            pendingVoiceMs = 0L
            return
        }

        appendToBuffer(currentSegmentBuffer, frame)
        silenceMs += frameMs

        if (silenceMs >= VAD_END_MS) {
            val msgVadEnd = "Silence reached ${silenceMs}ms, flush segment"
            Log.i(TAG, msgVadEnd)
            logToChromeConsole("info", msgVadEnd)
            finalizeCurrentSegment("silence")
        }
    }

    private fun finalizeCurrentSegment(reason: String) {
        if (!isSpeaking || currentSegmentBuffer.isEmpty()) {
            resetVadState()
            return
        }

        val segment = snapshotCurrentSegment()
        val durationMs = segmentDurationMs(segment)
        val dbfs = dbfs(segment)
        resetVadState()

        if (durationMs < MIN_SPEECH_MS) {
            val msg = "Skip STT: segment too short (${durationMs}ms, reason=$reason)"
            Log.w(TAG, msg)
            logToChromeConsole("warn", msg)
            return
        }

        if (dbfs < MIN_FINAL_DBFS) {
            val msg = "Skip STT: segment too quiet (${String.format("%.1f", dbfs)} dBFS, reason=$reason)"
            Log.w(TAG, msg)
            logToChromeConsole("warn", msg)
            return
        }

        val msg = "Final segment accepted (${durationMs}ms, ${String.format("%.1f", dbfs)} dBFS, reason=$reason)"
        Log.i(TAG, msg)
        logToChromeConsole("info", msg)
        onAudioFrameReady(segment, currentMicSide, true)
    }

    private fun resetVadState() {
        isSpeaking = false
        speechStartTime = 0L
        pendingVoiceMs = 0L
        silenceMs = 0L
        pendingStartBuffer.clear()
        currentSegmentBuffer.clear()
    }

    private fun appendToBuffer(buffer: ArrayList<Short>, audioData: ShortArray) {
        if (audioData.isEmpty()) return
        buffer.ensureCapacity(buffer.size + audioData.size)
        for (sample in audioData) {
            buffer.add(sample)
        }
    }

    private fun appendToBuffer(buffer: ArrayList<Short>, source: ArrayList<Short>) {
        if (source.isEmpty()) return
        buffer.ensureCapacity(buffer.size + source.size)
        buffer.addAll(source)
    }

    private fun snapshotCurrentSegment(): ShortArray {
        val segment = ShortArray(currentSegmentBuffer.size)
        for (i in currentSegmentBuffer.indices) {
            segment[i] = currentSegmentBuffer[i]
        }
        return segment
    }

    private fun frameDurationMs(sampleCount: Int): Long {
        return (sampleCount * 1000L) / SAMPLE_RATE
    }

    private fun segmentDurationMs(audioData: ShortArray): Long {
        return (audioData.size * 1000L) / SAMPLE_RATE
    }

    private fun isVoiceFrame(audioData: ShortArray): Boolean {
        return dbfs(audioData) >= FRAME_VOICE_DBFS
    }

    private fun dbfs(audioData: ShortArray): Double {
        if (audioData.isEmpty()) return -120.0
        var sum = 0.0
        for (sample in audioData) {
            sum += sample * sample
        }
        val rms = sqrt(sum / audioData.size)
        if (rms <= 0.0) return -120.0
        return 20.0 * log10(rms / Short.MAX_VALUE)
    }
}
