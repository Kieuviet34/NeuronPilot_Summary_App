package com.bhs.meetingnotes.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bhs.meetingnotes.util.FormatUtils
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.os.Environment
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

class I2SAudioRecorder(private val context: Context) {
    private val TAG = "I2SAudioRecorder"

    val sampleRate = 16000
    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    var currentFilePath: String = ""
        private set

    var durationMs: Long = 0L
        private set

    var currentFileSize: Long = 0L
        private set

    var pauseCount: Int = 0
        private set

    private var startTimeMs: Long = 0L
    private var pausedDurationMs: Long = 0L
    private var pauseStartTimeMs: Long = 0L

    var onAudioLevelListener: ((levelDbfs: Double, normalizedLevel: Int) -> Unit)? = null
    var onStateChangedListener: ((isRecording: Boolean, isPaused: Boolean) -> Unit)? = null
    var onTickListener: ((durationMs: Long, fileSize: Long) -> Unit)? = null
    var onSpeechSegmentListener: ((audioData: ShortArray) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording.get() && !isPaused.get()) {
                val now = System.currentTimeMillis()
                durationMs = now - startTimeMs - pausedDurationMs
                val file = File(currentFilePath)
                currentFileSize = if (file.exists()) file.length() else 0L
                onTickListener?.invoke(durationMs, currentFileSize)
                handler.postDelayed(this, 200)
            }
        }
    }

    // VAD logic from AudioCapture
    private val VAD_START_MS = 250L
    private val VAD_END_MS = 1000L
    private val MIN_SPEECH_MS = 800L
    private val HARD_CAP_MS = 15000L
    private val FRAME_VOICE_DBFS = -55.0
    private val MIN_FINAL_DBFS = -60.0

    private val DEBUG_MODE = true

    private var isSpeaking = false
    private var speechStartTime = 0L
    private var pendingVoiceMs = 0L
    private var silenceMs = 0L
    private val pendingStartBuffer = ArrayList<Short>()
    private val currentSegmentBuffer = ArrayList<Short>()

    @SuppressLint("MissingPermission")
    fun startRecording(customFileName: String? = null): String {
        if (isRecording.get()) return currentFilePath

        val dir = File(context.getExternalFilesDir(null), "MeetingNotes")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val safeFileName = if (!customFileName.isNullOrBlank()) {
            val sanitized = customFileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            if (sanitized.endsWith(".wav")) sanitized else "$sanitized.wav"
        } else {
            "$timestamp.wav"
        }
        val file = File(dir, safeFileName)
        file.parentFile?.mkdirs()
        currentFilePath = file.absolutePath
        pauseCount = 0

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed")
                return ""
            }

            writeWavHeaderPlaceholder(file)

            resetVadState()
            isRecording.set(true)
            isPaused.set(false)
            startTimeMs = System.currentTimeMillis()
            pausedDurationMs = 0L

            audioRecord?.startRecording()
            onStateChangedListener?.invoke(true, false)

            recordingThread = Thread({ recordLoop(file) }, "I2SRecorderThread")
            recordingThread?.start()

            handler.post(timerRunnable)
            Log.i(TAG, "Recording started -> $currentFilePath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            isRecording.set(false)
        }

        return currentFilePath
    }

    fun pauseRecording() {
        if (isRecording.get() && !isPaused.get()) {
            isPaused.set(true)
            pauseCount++
            pauseStartTimeMs = System.currentTimeMillis()
            onStateChangedListener?.invoke(true, true)
            Log.i(TAG, "Recording paused (count: $pauseCount)")
        }
    }

    fun resumeRecording() {
        if (isRecording.get() && isPaused.get()) {
            pausedDurationMs += (System.currentTimeMillis() - pauseStartTimeMs)
            isPaused.set(false)
            onStateChangedListener?.invoke(true, false)
            handler.post(timerRunnable)
            Log.i(TAG, "Recording resumed")
        }
    }

    fun stopRecording(): String {
        if (!isRecording.get()) return currentFilePath

        isRecording.set(false)
        isPaused.set(false)
        handler.removeCallbacks(timerRunnable)

        try {
            finalizeCurrentSegment()
            
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            recordingThread?.join(1000)
            recordingThread = null

            val file = File(currentFilePath)
            if (file.exists()) {
                updateWavHeader(file)
                currentFileSize = file.length()
                
                if (DEBUG_MODE) {
                    try {
                        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val debugFile = File(downloadDir, file.name)
                        file.copyTo(debugFile, overwrite = true)
                        Log.d(TAG, "Debug: Copied audio file to ${debugFile.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Debug: Failed to copy audio to Downloads", e)
                    }
                }
            }

            onStateChangedListener?.invoke(false, false)
            Log.i(TAG, "Recording stopped. File size: ${FormatUtils.formatFileSize(currentFileSize)}")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }

        return currentFilePath
    }

    private fun recordLoop(file: File) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        val fos = FileOutputStream(file, true)
        val buffer = ShortArray(bufferSize / 2)
        val byteBuffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN)

        while (isRecording.get()) {
            if (isPaused.get()) {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    break
                }
                continue
            }

            val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (readCount > 0) {
                byteBuffer.clear()
                var sum = 0.0
                for (i in 0 until readCount) {
                    val sample = buffer[i]
                    byteBuffer.putShort(sample)
                    sum += sample * sample
                }

                fos.write(byteBuffer.array(), 0, readCount * 2)

                val rms = sqrt(sum / readCount)
                val dbfs = if (rms > 0) 20.0 * log10(rms / Short.MAX_VALUE) else -120.0
                val normalizedLevel = ((dbfs + 60.0) / 60.0 * 100).toInt().coerceIn(5, 100)

                val frame = buffer.copyOfRange(0, readCount)
                processVad(frame, dbfs)

                handler.post {
                    onAudioLevelListener?.invoke(dbfs, normalizedLevel)
                }
            }
        }

        try {
            fos.flush()
            fos.close()
        } catch (e: Exception) {
            Log.e(TAG, "Fos close exception", e)
        }
    }
    
    private fun processVad(frame: ShortArray, dbfs: Double) {
        val frameMs = (frame.size * 1000L) / sampleRate
        val currentTime = System.currentTimeMillis()
        
        if (dbfs >= FRAME_VOICE_DBFS) {
            // Voice
            silenceMs = 0L
            if (!isSpeaking) {
                for (s in frame) pendingStartBuffer.add(s)
                pendingVoiceMs += frameMs
                
                if (pendingVoiceMs >= VAD_START_MS) {
                    isSpeaking = true
                    speechStartTime = currentTime - pendingVoiceMs
                    currentSegmentBuffer.clear()
                    currentSegmentBuffer.addAll(pendingStartBuffer)
                    pendingStartBuffer.clear()
                    pendingVoiceMs = 0L
                }
            } else {
                for (s in frame) currentSegmentBuffer.add(s)
                if (currentTime - speechStartTime >= HARD_CAP_MS) {
                    finalizeCurrentSegment()
                }
            }
        } else {
            // Silence
            if (!isSpeaking) {
                pendingStartBuffer.clear()
                pendingVoiceMs = 0L
            } else {
                for (s in frame) currentSegmentBuffer.add(s)
                silenceMs += frameMs
                if (silenceMs >= VAD_END_MS) {
                    finalizeCurrentSegment()
                }
            }
        }
    }
    
    private fun finalizeCurrentSegment() {
        if (!isSpeaking || currentSegmentBuffer.isEmpty()) {
            resetVadState()
            return
        }

        val segment = ShortArray(currentSegmentBuffer.size)
        var sum = 0.0
        for (i in currentSegmentBuffer.indices) {
            val sample = currentSegmentBuffer[i]
            segment[i] = sample
            sum += sample * sample
        }
        
        val durationMs = (segment.size * 1000L) / sampleRate
        val rms = sqrt(sum / segment.size)
        val dbfs = if (rms > 0) 20.0 * log10(rms / Short.MAX_VALUE) else -120.0
        
        resetVadState()

        if (durationMs >= MIN_SPEECH_MS && dbfs >= MIN_FINAL_DBFS) {
            handler.post {
                onSpeechSegmentListener?.invoke(segment)
            }
        }
    }

    private fun resetVadState() {
        isSpeaking = false
        speechStartTime = 0L
        pendingVoiceMs = 0L
        silenceMs = 0L
        pendingStartBuffer.clear()
        currentSegmentBuffer.clear()
    }

    private fun writeWavHeaderPlaceholder(file: File) {
        file.parentFile?.mkdirs()
        val fos = FileOutputStream(file)
        val header = ByteArray(44)
        fos.write(header)
        fos.close()
    }

    private fun updateWavHeader(file: File) {
        val totalAudioLen = file.length() - 44
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = sampleRate * channels * 2

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalDataLen.toInt())
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // Subchunk1Size (16 for PCM)
        buffer.putShort(1.toShort()) // AudioFormat (1 for PCM)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((channels * 2).toShort()) // BlockAlign
        buffer.putShort(16.toShort()) // BitsPerSample
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalAudioLen.toInt())

        val raf = RandomAccessFile(file, "rw")
        raf.seek(0)
        raf.write(header)
        raf.close()
    }
}