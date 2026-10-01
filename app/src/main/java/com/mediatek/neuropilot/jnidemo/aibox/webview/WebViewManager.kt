package com.mediatek.neuropilot.jnidemo.aibox.webview

import android.content.Context
import android.os.Build
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import com.mediatek.neuropilot.jnidemo.BuildConfig
import com.mediatek.neuropilot.jnidemo.aibox.ai.AudioCapture


class WebViewManager(
    private val context: Context,
    private val webView: WebView,
    private val audioCapture: AudioCapture
) {

    private var bridge: AndroidBridge? = null

    fun initWebView(): AndroidBridge {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true

                allowFileAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true

                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT

                builtInZoomControls = false
                displayZoomControls = false
                userAgentString = "AIBox/1.0 (Android)"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    isAlgorithmicDarkeningAllowed = false
                }
            }

            webViewClient = android.webkit.WebViewClient()
            webChromeClient = CustomWebChromeClient(context)

            bridge = AndroidBridge(context, this, audioCapture)
            addJavascriptInterface(bridge!!, "AIBox")
        }

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        return bridge!!
    }

    fun loadApp() {
        webView.loadUrl("file:///android_asset/web/index.html")
    }

    private class CustomWebChromeClient(private val context: Context) : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            request.grant(request.resources)
        }

        override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
            android.util.Log.d("WebConsole", "${message.sourceId()}:${message.lineNumber()} - ${message.message()}")
            return true
        }
    }
}
