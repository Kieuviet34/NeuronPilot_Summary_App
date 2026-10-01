package com.mediatek.neuropilot.jnidemo.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.*
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Quản lý ghi âm + nhận diện giọng nói bằng server PhoWhisper offline chạy
 * NGAY TRÊN THIẾT BỊ (encoder chạy NPU của chính chip MT8893 - xem README
 * trong package "BOX"), thay vì gọi Groq/Azure qua internet.
 *
 * YÊU CẦU: server whisper-server phải đang chạy sẵn trên thiết bị trước khi
 * dùng tính năng Voice (khởi động thủ công qua adb theo README):
 *   adb shell "sh /data/local/tmp/wsp/start_server.sh" &
 * App gọi thẳng tới http://127.0.0.1:8080/inference (server và app cùng
 * chạy trên 1 thiết bị nên không cần adb forward).
 *
 * KHÁC VỚI bản Groq/Azure trước: KHÔNG chia audio thành chunk nhỏ gửi liên
 * tục nữa - ghi trọn vẹn cả câu nói, chỉ gửi 1 lần duy nhất khi người dùng
 * bấm dừng ghi. Lý do: PhoWhisper (giống mọi model Whisper) cho kết quả
 * chính xác hơn nhiều khi có trọn vẹn ngữ cảnh câu nói, tránh hallucination
 * ở ranh giới chunk như đã gặp phải với cách chia nhỏ trước đây.
 */
class VoiceInputManager(private val activity: Activity, private val callback: Callback) {

    /** Callback để MainActivity nhận kết quả / trạng thái từ VoiceInputManager */
    interface Callback {
        fun onModelReady()
        fun onModelLoadFailed(error: String)
        fun onPartialText(text: String)
        fun onFinalText(text: String)
        fun onTimerTick(formattedTime: String)
        fun onRecordingStarted(isFirstSegment: Boolean)
        fun onRecordingPaused()
        fun onRecordingResumed()
        fun onRecordingStopped()
        fun onProcessingStarted() // ← thêm mới: bắt đầu gửi lên server, chờ STT
        fun onProcessingFinished() // ← thêm mới: đã có kết quả (thành công hoặc lỗi)
        fun onError(message: String)
    }

    var isRecording = false
        private set
    var isPaused = false
        private set
    private var recordSeconds = 0
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null

    // ==== Ghi âm trực tiếp bằng AudioRecord ====
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    @Volatile
    private var keepRecordingThreadAlive = false

    @Volatile
    private var pausedFlag = false
    private var pcmFile: File? = null
    private var pcmOut: FileOutputStream? = null
    private var bufferSize = 0
    private var recordStartTime: Long = 0
    var isSessionActive = false
        private set

    @Volatile
    var isProcessing = false
        private set

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS) // xử lý NPU ~2-3s cho câu 3s, để dư thời gian
        .build()

    private fun hasSignificantAudio(pcmFile: File): Boolean {
        try {
            FileInputStream(pcmFile).use { `in` ->
                val buf = ByteArray(4096)
                var sumSquares: Long = 0
                var sampleCount: Long = 0
                var n: Int
                while (`in`.read(buf).also { n = it } != -1) {
                    // Đọc từng cặp byte thành 1 sample 16-bit (little-endian)
                    var i = 0
                    while (i + 1 < n) {
                        val sample = (buf[i].toInt() and 0xFF or (buf[i + 1].toInt() shl 8)).toShort()
                        sumSquares += (sample.toLong() * sample)
                        sampleCount++
                        i += 2
                    }
                }
                if (sampleCount == 0L) return false

                val rms = sqrt(sumSquares.toDouble() / sampleCount)
                Log.d(LOG_TAG, "Audio RMS = $rms (nguong = $SILENCE_RMS_THRESHOLD)")
                return rms >= SILENCE_RMS_THRESHOLD
            }
        } catch (e: IOException) {
            Log.e(LOG_TAG, "hasSignificantAudio loi: ${e.message}")
            return true // lỗi đọc thì cứ cho gửi bình thường, để server tự xử lý
        }
    }

    private fun stripLeadingArtifacts(text: String?): String? {
        if (text.isNullOrEmpty()) return text

        var cleaned = text.trim()

        // Whisper hay tự chèn 1-2 từ tiếng Anh "rác" ở đầu câu (and, andrew,
        // andrani jolie...) trước khi vào nội dung tiếng Việt thật. Cắt bỏ mọi
        // cụm từ thuần chữ cái Latin (a-z, không dấu) liên tiếp ở đầu câu, dừng
        // lại ngay khi gặp từ có dấu tiếng Việt hoặc dấu câu.
        cleaned = cleaned.replaceFirst(
            "(?i)^([a-z]+\\s+){1,3}(?=\\p{L}*[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ])".toRegex(),
            ""
        )

        // Vẫn giữ lại rule cũ cho trường hợp "and," / "and " đơn giản (phòng khi
        // câu tiếp theo cũng không có dấu, ví dụ câu toàn số/tên riêng)
        cleaned = cleaned.replaceFirst("(?i)^and[,\\s]+".toRegex(), "")

        // Loại dấu câu thừa ở đầu (whisper hay bị dính . , ! ? ở đầu đoạn ghi tiếp theo)
        cleaned = cleaned.replaceFirst("^[.,!?]+\\s*".toRegex(), "")

        return cleaned.trim()
    }

    /**
     * Không cần tải model gì trong app - model chạy trên server riêng của
     * thiết bị. Kiểm tra nhanh xem server có đang chạy không bằng cách gửi
     * 1 request test nhẹ, để báo lỗi sớm nếu quên khởi động server qua adb.
     */
    fun initModel() {
        thread {
            try {
                val pingRequest = Request.Builder()
                    .url(WHISPER_SERVER_URL)
                    .head() // chỉ kiểm tra server có phản hồi, không gửi audio thật
                    .build()
                httpClient.newCall(pingRequest).execute().use { response ->
                    Log.d(LOG_TAG, "Whisper server phan hoi, san sang su dung")
                    callback.onModelReady()
                }
            } catch (e: Exception) {
                Log.w(
                    LOG_TAG, "Khong ket noi duoc whisper server luc khoi dong: ${e.message}"
                            + " - van cho phep dung, se bao loi cu the khi thuc su goi API"
                )
                // Không chặn hẳn tính năng Voice chỉ vì ping thất bại (server có thể
                // đang khởi động chậm) - vẫn báo ready, lỗi thật sẽ hiện khi gửi audio.
                callback.onModelReady()
            }
        }
    }

    fun isModelReady(): Boolean = true

    /** Gọi khi người dùng bấm tab Voice. Tự xin quyền mic nếu chưa có. */
    fun requestStart() {
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO
            )
            return
        }
        start()
    }

    /** Gọi từ onRequestPermissionsResult() của Activity khi user vừa cấp quyền mic */
    fun onPermissionGranted() {
        Handler(activity.mainLooper).postDelayed({
            start()
        }, 300)
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        try {
            val isFirstSegment = !isSessionActive
            if (isFirstSegment) {
                recordSeconds = 0
            }
            isPaused = false
            pausedFlag = false
            recordStartTime = System.currentTimeMillis()

            startTimer()

            bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (bufferSize <= 0) {
                callback.onError("Thiết bị không hỗ trợ cấu hình ghi âm này")
                return
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                callback.onError("Không khởi tạo được AudioRecord")
                return
            }

            pcmFile = File(activity.cacheDir, "voice_input.pcm")
            pcmOut = FileOutputStream(pcmFile)

            audioRecord?.startRecording()
            keepRecordingThreadAlive = true

            recordingThread = Thread(recordingRunnable, "VoiceInputRecordingThread")
            recordingThread?.start()

            isRecording = true
            isSessionActive = true
            callback.onRecordingStarted(isFirstSegment)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "start() that bai: ${e.message}")
            callback.onError("Không thể bắt đầu ghi âm")
        }
    }

    private val recordingRunnable = Runnable {
        val buffer = ByteArray(bufferSize)

        while (keepRecordingThreadAlive) {
            val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
            if (read > 0 && !pausedFlag) {
                try {
                    pcmOut?.write(buffer, 0, read)
                } catch (e: IOException) {
                    Log.e(LOG_TAG, "Ghi PCM loi: ${e.message}")
                }
            }

            // Tự động dừng nếu ghi quá lâu (README khuyến nghị <30s/câu)
            val elapsed = System.currentTimeMillis() - recordStartTime
            if (elapsed >= MAX_RECORD_MS) {
                Log.d(LOG_TAG, "Da ghi qua ${MAX_RECORD_MS}ms, tu dong dung de dam bao chat luong")
                activity.runOnUiThread {
                    callback.onError("Câu nói quá dài (>28s), đã tự động dừng ghi")
                    stopKeepResult()
                }
                break
            }
        }
    }

    fun togglePause() {
        if (audioRecord == null) return
        if (isPaused) {
            pausedFlag = false
            startTimer()
            isPaused = false
            callback.onRecordingResumed()
        } else {
            pausedFlag = true
            stopTimer()
            isPaused = true
            callback.onRecordingPaused()
        }
    }

    /**
     * Dừng ĐOẠN ghi âm hiện tại (không kết thúc cả phiên), gửi lên server lấy
     * text, rồi vẫn cho phép bấm mic để ghi tiếp đoạn mới (nối text).
     */
    fun stopKeepResult() {
        if (isProcessing) {
            Log.w(LOG_TAG, "Dang xu ly doan truoc, bo qua yeu cau dung moi")
            return
        }
        val hadData = stopInternal()
        callback.onRecordingStopped()

        if (!hadData) {
            callback.onError("Không có dữ liệu ghi âm")
            return
        }

        val file = pcmFile
        if (file == null || !hasSignificantAudio(file)) {
            Log.d(LOG_TAG, "Am thanh qua nho / im lang, bo qua khong goi STT")
            callback.onError("Không phát hiện giọng nói, vui lòng thử lại")
            return
        }

        isProcessing = true
        callback.onProcessingStarted()
        sendFullRecordingToServer()
    }

    /** Kết thúc HẲN phiên ghi âm — gọi khi bấm Gửi hoặc huỷ bỏ hoàn toàn */
    fun endSession() {
        stopInternal()
        isSessionActive = false
        isProcessing = false
    }

    /** Giữ tên cũ để tương thích chỗ gọi cũ — huỷ hẳn phiên, không gửi server */
    fun cancel() {
        endSession()
    }

    /** @return true nếu có dữ liệu âm thanh được ghi lại */
    private fun stopInternal(): Boolean {
        keepRecordingThreadAlive = false
        recordingThread?.let {
            try {
                it.join(500)
            } catch (ignored: InterruptedException) {
            }
            recordingThread = null
        }
        audioRecord?.let {
            try {
                it.stop()
            } catch (ignored: IllegalStateException) {
            }
            it.release()
            audioRecord = null
        }
        pcmOut?.let {
            try {
                it.close()
            } catch (ignored: IOException) {
            }
            pcmOut = null
        }
        stopTimer()
        isRecording = false
        isPaused = false
        pausedFlag = false

        return pcmFile?.let { it.exists() && it.length() > 0 } ?: false
    }

    /** Giải phóng tài nguyên — gọi trong onDestroy() của Activity */
    fun release() {
        stopInternal()
        isSessionActive = false
        isProcessing = false
    }
    // ============================================================
    // GỬI TOÀN BỘ ÂM THANH ĐÃ GHI LÊN SERVER LOCAL (1 LẦN DUY NHẤT)
    // ============================================================

    private fun sendFullRecordingToServer() {
        try {
            val file = pcmFile ?: return
            val wavFile = File(activity.cacheDir, "voice_input.wav")
            pcmToWav(file, wavFile)

            val contentType = "audio/wav".toMediaTypeOrNull()
            val fileBody = wavFile.asRequestBody(contentType)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", wavFile.name, fileBody)
                .addFormDataPart("response_format", "json")
                .build()

            val request = Request.Builder()
                .url(WHISPER_SERVER_URL)
                .post(requestBody)
                .build()

            Log.d(LOG_TAG, "Gui toan bo file ghi am (${wavFile.length()} bytes) len server local")

            httpClient.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string() ?: ""
                    wavFile.delete()

                    if (!response.isSuccessful) {
                        Log.e(LOG_TAG, "Server loi HTTP ${response.code}: $body")
                        activity.runOnUiThread {
                            isProcessing = false
                            callback.onProcessingFinished()
                            callback.onError(
                                "Server lỗi (HTTP ${response.code}"
                                        + "). Kiểm tra whisper-server đã chạy chưa."
                            )
                        }
                        return
                    }

                    var text = extractWhisperText(body)
                    text = stripLeadingArtifacts(text) ?: ""
                    Log.d(LOG_TAG, "Ket qua: $text")

                    val finalText = text
                    activity.runOnUiThread {
                        isProcessing = false
                        callback.onProcessingFinished()
                        if (finalText.isEmpty()) {
                            callback.onError("Không nhận được giọng nói rõ ràng")
                        } else {
                            callback.onFinalText(finalText)
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    Log.e(LOG_TAG, "Ket noi server local that bai: ${e.message}")
                    wavFile.delete()
                    activity.runOnUiThread {
                        isProcessing = false
                        callback.onProcessingFinished()
                        callback.onError(
                            "Không kết nối được server. "
                                    + "Đã chạy 'adb shell sh /data/local/tmp/wsp/start_server.sh' chưa?"
                        )
                    }
                }
            })
        } catch (e: IOException) {
            Log.e(LOG_TAG, "sendFullRecordingToServer loi: ${e.message}")
            callback.onError("Lỗi xử lý file ghi âm")
        }
    }

    /** Parse JSON trả về từ whisper-server, lọc bỏ nếu khớp mẫu câu "rác" Whisper hay bịa */
    private fun extractWhisperText(jsonBody: String): String {
        return try {
            val json = JSONObject(jsonBody)
            val text = if (json.has("text")) json.getString("text").trim() else ""

            Log.d(LOG_TAG, "RAW text tu whisper (truoc khi loc): '$text'")

            if (looksLikeHallucination(text)) {
                Log.w(LOG_TAG, "BI LOAI vi nghi hallucination: '$text'")
                ""
            } else {
                text
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Parse ket qua whisper-server loi: ${e.message}")
            ""
        }
    }


    /** true nếu văn bản khớp 1 trong các mẫu câu "rác" Whisper hay tự bịa khi audio không rõ */
    private fun looksLikeHallucination(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        val lower = text.lowercase()
        for (pattern in HALLUCINATION_PATTERNS) {
            if (lower.contains(pattern)) return true
        }
        return false
    }

    // ============================================================
    // PCM -> WAV
    // ============================================================

    @Throws(IOException::class)
    private fun pcmToWav(pcm: File, wav: File) {
        val pcmLen = pcm.length()
        val wavLen = pcmLen + 36
        val channels = 1
        val bitsPerSample = 16
        val byteRate = (SAMPLE_RATE * channels * bitsPerSample / 8).toLong()

        FileOutputStream(wav).use { out ->
            FileInputStream(pcm).use { `in` ->
                out.write("RIFF".toByteArray())
                out.write(intToBytes(wavLen.toInt()))
                out.write("WAVE".toByteArray())
                out.write("fmt ".toByteArray())
                out.write(intToBytes(16))
                out.write(shortToBytes(1.toShort()))
                out.write(shortToBytes(channels.toShort()))
                out.write(intToBytes(SAMPLE_RATE))
                out.write(intToBytes(byteRate.toInt()))
                out.write(shortToBytes((channels * bitsPerSample / 8).toShort()))
                out.write(shortToBytes(bitsPerSample.toShort()))
                out.write("data".toByteArray())
                out.write(intToBytes(pcmLen.toInt()))

                val buf = ByteArray(4096)
                var n: Int
                while (`in`.read(buf).also { n = it } != -1) out.write(buf, 0, n)
            }
        }
    }

    private fun intToBytes(v: Int): ByteArray {
        return byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
    }

    private fun shortToBytes(v: Short): ByteArray {
        return byteArrayOf(v.toByte(), (v.toInt() shr 8).toByte())
    }

    // ============================================================
    // TIMER
    // ============================================================

    private fun startTimer() {
        if (timerHandler == null) timerHandler = Handler(activity.mainLooper)
        timerRunnable = object : Runnable {
            override fun run() {
                recordSeconds++
                val m = recordSeconds / 60
                val s = recordSeconds % 60
                callback.onTimerTick(String.format(Locale.getDefault(), "%d:%02d", m, s))
                timerHandler?.postDelayed(this, 1000)
            }
        }
        timerRunnable?.let { timerHandler?.post(it) }
    }

    private fun stopTimer() {
        timerHandler?.let { handler ->
            timerRunnable?.let { runnable ->
                handler.removeCallbacks(runnable)
            }
        }
    }

    companion object {
        private const val LOG_TAG = "NP_LLM_DEMO_VOICE"
        const val REQUEST_RECORD_AUDIO = 300

        // ==== Cấu hình local PhoWhisper server ====
        private const val WHISPER_SERVER_URL = "http://127.0.0.1:8080/inference"
        private const val SAMPLE_RATE = 16000
        private const val MAX_RECORD_MS = 28000L
        private const val SILENCE_RMS_THRESHOLD = 50.0

        private val HALLUCINATION_PATTERNS = arrayOf(
            "subscribe", "đăng ký kênh", "hãy đăng ký", "cảm ơn các bạn đã theo dõi",
            "cảm ơn đã xem", "hẹn gặp lại", "like và share", "để không bỏ lỡ",
            "video hấp dẫn", "ghiền mì gõ"
        )
    }
}
