package com.mediatek.neuropilot.jnidemo.aibox.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

class AudioCapture(
    private val onAudioFrameReady: (audioData: ShortArray, side: String, isFinal: Boolean) -> Unit
) {
    private val TAG = "AudioCapture"

    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFET_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2

    private val VAD_START_MS = 250L
    private val VAD_END_MS = 1000L
    private val MIN_SPEECH_MS = 1200L
    private val HARD_CAP_MS = 15000L
    private val FRAME_VOICE_DBFS = -42.0
    private val MIN_FINAL_DBFS = -50.0
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

    fun setActiveMicSide(side: String) {
        currentMicSide = side
    }

    @SuppressLint("MissingPermission")
    fun startCapture() {
        if (isRecording.get()) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFET_SIZE
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed")
                return
            }

            resetVadState()
            isRecording.set(true)
            audioRecord?.startRecording()

            recordingThread = Thread({ readAudioData() }, "AudioCapture-Thread")
            recordingThread?.start()
        } catch (e: Exception) {
            Log.e(TAG, "startCapture error: ${e.message}")
        }
    }

    fun stopCapture() {
        if (!isRecording.get()) return
        isRecording.set(false)
        try {
            finalizeCurrentSegment("stop")
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread?.join(1000)
            recordingThread = null
        } catch (e: Exception) {
            Log.e(TAG, "stopCapture error: ${e.message}")
        }
    }

    private fun readAudioData() {
        val audioBuffer = ShortArray(BUFFET_SIZE / 2)
        while (isRecording.get()) {
            val readSize = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
            if (readSize <= 0) continue

            val frame = audioBuffer.copyOfRange(0, readSize)
            val frameMs = frameDurationMs(readSize)
            val currentTime = System.currentTimeMillis()

            if (isVoiceFrame(frame)) {
                handleVoiceFrame(frame, frameMs, currentTime)
            } else {
                handleSilentFrame(frame, frameMs)
            }
        }
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

                onAudioFrameReady(frame, currentMicSide, false)
            }
            return
        }

        appendToBuffer(currentSegmentBuffer, frame)
        onAudioFrameReady(frame, currentMicSide, false)

        val speechDuration = currentTime - speechStartTime
        if (speechDuration >= HARD_CAP_MS) {
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

        if (durationMs < MIN_SPEECH_MS || dbfs < MIN_FINAL_DBFS) {
            return
        }

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
