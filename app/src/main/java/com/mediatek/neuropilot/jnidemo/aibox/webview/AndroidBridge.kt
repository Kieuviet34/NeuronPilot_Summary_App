package com.mediatek.neuropilot.jnidemo.aibox.webview

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.Keep
import com.mediatek.neuropilot.jnidemo.aibox.ai.AudioCapture
import com.google.gson.Gson
import java.util.Locale

/**
 * Tầng cầu nối xử lý các lệnh gọi từ giao diện React FE xuống Native
 */
class AndroidBridge(
    private val context: Context,
    private val webView: WebView,
    private val audioCapture: AudioCapture
) {

    @Keep
    @JavascriptInterface
    fun startListening(lang: String) {
        Log.d("AndroidBridge", "FE yêu cầu bật STT với ngôn ngữ: $lang")

        // 1. Log báo cho FE biết Android Native đã nhận được lệnh
        webView.post {
            webView.evaluateJavascript("console.log('Android Native: Đã nhận lệnh startListening ($lang)');", null)
        }

        // 2. Kích hoạt thu âm mic thật dưới Native
        try {
            // Thiết lập mic ban đầu dựa theo ngôn ngữ FE yêu cầu làm gợi ý
            val initialSide = if (lang.contains("vi")) "A" else "B"
            audioCapture.setActiveMicSide(initialSide)

            // Bắt đầu luồng thu âm, VAD và Hard-cap ngầm
            audioCapture.startCapture()

        } catch (e: Exception) {
            Log.e("AndroidBridge", "Lỗi khởi chạy Audio Capture: ${e.message}")
        }
    }

    @Keep
    @JavascriptInterface
    fun stopListening() {
        Log.d("AndroidBridge", "FE yêu cầu dừng STT")

        // Dừng luồng thu âm mic thật ngầm dưới Native
        audioCapture.stopCapture()

        webView.post {
            webView.evaluateJavascript("console.log('Android Native: Đã dừng thu âm theo yêu cầu.');", null)
        }
    }

    @JavascriptInterface
    fun setVolume(side: String, volume: Int) {
        Log.d("AndroidBridge", "FE chỉnh Volume kênh $side lên $volume")

        // 🛠️ SỬA LỖI CÚ PHÁP: Chuyển chữ về lowercase an toàn để so sánh bằng cấu trúc chuẩn Kotlin
        val sideLower = side.lowercase(Locale.ROOT)
        if (sideLower == "left" || sideLower == "a") {
            audioCapture.setActiveMicSide("A") // Chuyển luồng VAD tập trung nhận diện cho Bên A
        } else if (sideLower == "right" || sideLower == "b") {
            audioCapture.setActiveMicSide("B") // Chuyển luồng VAD tập trung nhận diện cho Bên B
        }

        webView.post {
            webView.evaluateJavascript("console.log('Android Native: Cập nhật volume kênh $side -> $volume%');", null)
        }
    }

    @JavascriptInterface
    fun setLang(lang: String) {
        Log.d("AndroidBridge", "FE chuyển nhanh ngôn ngữ sang: $lang")

        // Cập nhật nhanh mic active tương ứng với ngôn ngữ được chuyển đổi
        val targetSide = if (lang.contains("vi")) "A" else "B"
        audioCapture.setActiveMicSide(targetSide)

        webView.post {
            webView.evaluateJavascript("console.log('Android Native: Đã chuyển nhanh cấu hình ngôn ngữ sang: $lang');", null)
        }
    }

    /**
     * Gửi kết quả nhận diện (Partial / Final) ngược lại cho giao diện WebView
     */
    fun fireTranscript(payload: TranscriptPayload) {
        val json = Gson().toJson(payload)
        val js = """
            window.dispatchEvent(new CustomEvent('aibox_transcript', {
                detail: $json
            }));
        """.trimIndent()

        // Đảm bảo luôn chạy trên luồng chính của WebView
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    /**
     * Gửi trạng thái cắm/rút thiết bị dongle (Kênh A/B) xuống cho FE
     */
    fun fireDongleStatus(side: String, connected: Boolean, name: String? = null) {
        val map = mapOf("side" to side, "connected" to connected, "name" to name)
        val json = Gson().toJson(map)
        webView.post {
            webView.evaluateJavascript("""
                window.dispatchEvent(new CustomEvent('aibox_dongle', { detail: $json }));
            """.trimIndent(), null)
        }
    }
}

/**
 * Cấu trúc dữ liệu phản hồi đúng hợp đồng (contract) ký kết với FE
 */
data class TranscriptPayload(
    val segmentId: Long? = null,
    val text: String,
    val translation: String? = null,
    val lang: String,                 // "vi" | "en" | "auto"
    val isFinal: Boolean,             // false: đang nhận diện, true: chốt câu
    val timestamp: Long = System.currentTimeMillis(),
    val speaker: String? = null
)
