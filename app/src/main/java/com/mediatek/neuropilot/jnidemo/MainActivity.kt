package com.mediatek.neuropilot.jnidemo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.mediatek.neuropilot.jnidemo.aibox.*
import com.mediatek.neuropilot.jnidemo.chat.*
import com.mediatek.neuropilot.jnidemo.chat.db.ChatDatabase
import com.mediatek.neuropilot.jnidemo.chat.db.ChatSession
import kotlinx.coroutines.launch
import java.io.File
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private var modelHandle: Long = 0
    private var currentModel = PromptBuilder.ModelType.QWEN
    private var fullHistory = ""
    private var kvCachePrimed = false
    private var turnCount = 0
    private var maxTurns = AppSettings.DEFAULT_MAX_TURNS

    private var rvChat: RecyclerView? = null
    private var chatAdapter: ChatAdapter? = null
    private var modelStatusTextView: TextView? = null
    private var userInput: EditText? = null
    private var sendButton: View? = null
    private var settingsButton: View? = null
    private var btnSummarize: View? = null

    private var currentChatTask: StreamingComputeTask? = null
    private var aiBoxBridge: AiBoxBridge? = null
    private var webView: WebView? = null
    private var btnSwitchMode: View? = null
    private var isWebViewMode = false

    private var voiceManager: VoiceInputManager? = null
    private var voiceCard: View? = null
    private var historyCard: View? = null
    private var rvHistory: RecyclerView? = null
    private var historyAdapter: ChatHistoryAdapter? = null
    private var btnHistory: View? = null
    private var btnNewChat: View? = null

    private lateinit var database: ChatDatabase
    private lateinit var repository: ChatRepository
    private var currentSessionId: Long? = null

    private var viewMicRing: View? = null
    private var tvTimer: TextView? = null
    private var tvRecordStatus: TextView? = null
    private var tvRecognizedText: TextView? = null
    private var btnMicRecord: ImageView? = null
    private var tabVoice: LinearLayout? = null
    private var tabText: LinearLayout? = null
    private val micDebounceHandler = Handler(Looper.getMainLooper())
    private var currentToast: Toast? = null

    @Volatile
    private var isSummarizing = false

    @Volatile
    private var isGenerating = false

    external fun initModel(yamlConfigPath: String): Long
    external fun countTokens(text: String): Int
    external fun computeStreaming(modelHandle: Long, inputText: String, callback: TokenCallback)
    external fun resetModel(modelHandle: Long)
    external fun destroyModel(modelHandle: Long)

    interface TokenCallback {
        fun onTokenReceived(token: String): Boolean // Trả về false để yêu cầu NPU dừng sinh ngay lập tức
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(LOG_TAG, "onCreate: Starting...")
        hideSystemBars()
        setContentView(R.layout.activity_main)

        initViews()
        setupChat()
        setupHistory()
        setupModel()
        setupVoiceInput()

        aiBoxBridge = AiBoxBridge(this)
        webView?.let { aiBoxBridge?.init(it) }

        setupListeners()
    }

    private fun initViews() {
        rvChat = findViewById(R.id.rv_chat)
        modelStatusTextView = findViewById(R.id.tv_model_status)
        userInput = findViewById(R.id.userInput)
        sendButton = findViewById(R.id.sendButton)
        settingsButton = findViewById(R.id.btn_settings)
        btnSummarize = findViewById(R.id.btn_summarize)

        voiceCard = findViewById(R.id.voice_recording_card)
        tvTimer = findViewById(R.id.tv_timer)
        tvRecordStatus = findViewById(R.id.tv_record_status)
        tvRecognizedText = findViewById(R.id.tv_recognized_text)
        btnMicRecord = findViewById(R.id.btn_mic_record)
        tabVoice = findViewById(R.id.tab_voice)
        tabText = findViewById(R.id.tab_text)
        viewMicRing = findViewById(R.id.view_mic_ring)

        webView = findViewById(R.id.webView_aibox)
        btnSwitchMode = findViewById(R.id.btn_switch_mode)

        historyCard = findViewById(R.id.history_recording_card)
        rvHistory = findViewById(R.id.rv_history)
        btnHistory = findViewById(R.id.btn_history)
        btnNewChat = findViewById(R.id.btn_new_chat)

        setupKeyboardInsetHandlingLegacy()
    }

    private fun setupKeyboardInsetHandlingLegacy() {
        val rootView = findViewById<View>(R.id.main)
        val bottomArea = findViewById<View>(R.id.layout_bottom_area)
        if (rootView == null || bottomArea == null) return

        val originalBottomPadding = bottomArea.paddingBottom
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - r.bottom
            val thresholdPx = (150 * resources.displayMetrics.density).toInt()

            if (keypadHeight > thresholdPx) {
                bottomArea.setPadding(
                    bottomArea.paddingLeft,
                    bottomArea.paddingTop,
                    bottomArea.paddingRight,
                    keypadHeight
                )
            } else {
                bottomArea.setPadding(
                    bottomArea.paddingLeft,
                    bottomArea.paddingTop,
                    bottomArea.paddingRight,
                    originalBottomPadding
                )
            }
        }
    }

    private fun setupChat() {
        chatAdapter = ChatAdapter()
        rvChat?.layoutManager = LinearLayoutManager(this)
        rvChat?.adapter = chatAdapter
        val animator = rvChat?.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    private fun setupHistory() {
        database = ChatDatabase.getDatabase(this)
        repository = ChatRepository(database.chatDao())

        historyAdapter = ChatHistoryAdapter(
            onSessionClick = { session -> loadSession(session.id) },
            onDeleteClick = { session ->
                lifecycleScope.launch { repository.deleteSession(session) }
            }
        )
        rvHistory?.layoutManager = LinearLayoutManager(this)
        rvHistory?.adapter = historyAdapter

        lifecycleScope.launch {
            repository.allSessions.collect { sessions ->
                historyAdapter?.submitList(sessions)
                findViewById<View>(R.id.tv_no_history)?.visibility =
                    if (sessions.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupModel() {
        if (File(QWEN_YAML_PATH).exists()) {
            currentModel = PromptBuilder.ModelType.QWEN
        } else if (File(LLAMA_YAML_PATH).exists()) {
            currentModel = PromptBuilder.ModelType.LLAMA
        }
        refreshMaxTurnsFromSettings()
        setupNpuPermissions()
        loadModel(currentModel)
    }

    private fun setupListeners() {
        btnHistory?.setOnClickListener {
            if (checkSystemBusy()) return@setOnClickListener
            historyCard?.let { updateUiCardVisibility(it) }
        }

        btnNewChat?.setOnClickListener {
            if (checkSystemBusy()) return@setOnClickListener
            resetToNewChat()
            showVoiceToast("Đã bắt đầu phiên chat mới")
        }

        btnSummarize?.setOnClickListener {
            if (isWebViewMode) {
                aiBoxBridge?.showSummaryDialog()
            } else {
                summarizeConversation()
            }
        }
        settingsButton?.setOnClickListener { launchSettingsActivity() }

        btnSwitchMode?.setOnClickListener { toggleAppMode() }

        userInput?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                userInput?.postDelayed({
                    userInput?.requestRectangleOnScreen(
                        Rect(0, 0, userInput?.width ?: 0, userInput?.height ?: 0)
                    )
                }, 200)
                rvChat?.postDelayed({
                    chatAdapter?.let {
                        if (it.itemCount > 0) rvChat?.smoothScrollToPosition(it.itemCount - 1)
                    }
                }, 300)
            }
        }

        userInput?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                if (!event.isShiftPressed) {
                    sendButton?.performClick()
                    return@setOnKeyListener true
                }
            }
            false
        }

        sendButton?.setOnClickListener {
            val prompt = userInput?.text.toString().trim()
            if (prompt.isNotEmpty()) {
                if (!canSendPrompt(prompt)) {
                    showVoiceToast("Câu hỏi quá dài, hãy rút ngắn nội dung")
                    return@setOnClickListener
                }
                if (modelHandle != 0L) {
                    chatAdapter?.addMessage(ChatMessage(ChatMessage.TYPE_USER, prompt))
                    chatAdapter?.let { rvChat?.smoothScrollToPosition(it.itemCount - 1) }
                    userInput?.setText("")

                    lifecycleScope.launch {
                        if (currentSessionId == null) {
                            val title = if (prompt.length > 30) prompt.take(30) + "..." else prompt
                            currentSessionId = repository.createSession(title)
                        }
                        currentSessionId?.let { sessionId ->
                            repository.saveMessage(sessionId, "USER", prompt)
                        }

                        setGeneratingState(true)
                        currentChatTask = StreamingComputeTask()
                        currentChatTask?.execute(prompt)
                    }
                } else {
                    showVoiceToast("Model still loading...")
                }
            }
        }

        bindPlaceholderFeature(R.id.tab_image)
        bindPlaceholderFeature(R.id.tab_video)
        bindPlaceholderFeature(R.id.tab_live)
    }

    private fun setupVoiceInput() {
        voiceManager = VoiceInputManager(this, object : VoiceInputManager.Callback {
            override fun onModelReady() {
                Log.d(LOG_TAG, "Voice model ready")
            }

            override fun onModelLoadFailed(error: String) {
                showVoiceToast("Không thể tải model giọng nói")
            }

            override fun onPartialText(text: String) {
                tvRecognizedText?.text = text
                tvRecognizedText?.setTextColor(-0xcccccd) // 0xFF333333
            }

            override fun onRecordingResumed() {}
            override fun onRecordingPaused() {}

            override fun onFinalText(text: String) {
                if (isSummarizing) {
                    showVoiceToast("Đang tóm tắt, vui lòng thử lại sau")
                    voiceManager?.endSession()
                    voiceCard?.visibility = View.GONE
                    setOtherTabsEnabled(true)
                    return
                }
                val finalText = text.trim()
                if (finalText.isEmpty()) return

                voiceManager?.endSession()
                voiceCard?.visibility = View.GONE
                setOtherTabsEnabled(true)
                updateMicVisualState(MIC_STATE_IDLE)
                userInput?.setText(finalText)
                sendButton?.performClick()
            }

            override fun onTimerTick(formattedTime: String) {
                tvTimer?.text = formattedTime
            }

            override fun onRecordingStarted(isFirstSegment: Boolean) {
                tvTimer?.text = "0:00"
                updateMicVisualState(MIC_STATE_RECORDING)
                voiceCard?.visibility = View.VISIBLE
                setOtherTabsEnabled(false)
            }

            override fun onRecordingStopped() {
                updateMicVisualState(MIC_STATE_PROCESSING)
            }

            override fun onError(message: String) {
                showVoiceToast(message)
                updateMicVisualState(MIC_STATE_IDLE)
                btnMicRecord?.isEnabled = false
                micDebounceHandler.removeCallbacksAndMessages(null)
                micDebounceHandler.postDelayed({ btnMicRecord?.isEnabled = true }, 700)
            }

            override fun onProcessingStarted() {
                btnMicRecord?.isEnabled = false
                updateMicVisualState(MIC_STATE_PROCESSING)
            }

            override fun onProcessingFinished() {
                btnMicRecord?.isEnabled = true
            }
        })
        voiceManager?.initModel()

        tabVoice?.setOnClickListener {
            if (isSummarizing) {
                showVoiceToast("Đang tóm tắt, vui lòng chờ...")
                return@setOnClickListener
            }
            if (voiceManager?.isSessionActive == false) {
                hideKeyboard()
                historyCard?.visibility = View.GONE
                voiceCard?.visibility = View.VISIBLE
                tvRecognizedText?.text = "Nhận dạng giọng nói..."
                tvRecognizedText?.setTextColor(-0x424243) // 0xFFBDBDBD
                tvTimer?.text = "0:00"
                updateMicVisualState(MIC_STATE_IDLE)
                setOtherTabsEnabled(false)
            }
        }

        tabText?.setOnClickListener {
            if (voiceManager?.isSessionActive == true || voiceCard?.visibility == View.VISIBLE || historyCard?.visibility == View.VISIBLE) {
                if (voiceManager?.isRecording == true) {
                    showVoiceToast("Vui lòng dừng ghi âm trước khi chuyển tab")
                    return@setOnClickListener
                }
                voiceManager?.endSession()
                voiceCard?.visibility = View.GONE
                historyCard?.visibility = View.GONE
                setOtherTabsEnabled(true)
                updateMicVisualState(MIC_STATE_IDLE)
            }
        }

        btnMicRecord?.setOnClickListener {
            if (isSummarizing) {
                showVoiceToast("Đang tóm tắt, vui lòng chờ...")
                return@setOnClickListener
            }
            if (voiceManager?.isProcessing == true) return@setOnClickListener
            if (voiceManager?.isRecording == true) voiceManager?.stopKeepResult()
            else voiceManager?.requestStart()
        }
    }

    private fun toggleAppMode() {
        isWebViewMode = !isWebViewMode
        Log.d(LOG_TAG, "toggleAppMode: isWebViewMode=$isWebViewMode")

        val layoutHeader = findViewById<View>(R.id.layout_header)
        val avatarContainer = findViewById<View>(R.id.layout_avatar_container)

        if (isWebViewMode) {
            if (currentChatTask != null && currentChatTask?.status != AsyncTask.Status.FINISHED) {
                currentChatTask?.cancel(true)
            }

            if (voiceManager?.isRecording == true) {
                voiceManager?.stopKeepResult()
            }
            voiceManager?.endSession()

            voiceCard?.visibility = View.GONE
            rvChat?.visibility = View.GONE
            findViewById<View>(R.id.layout_bottom_area).visibility = View.GONE

            layoutHeader.visibility = View.GONE
            avatarContainer.visibility = View.GONE

            webView?.visibility = View.VISIBLE
            aiBoxBridge?.onResume()
        } else {
            webView?.visibility = View.GONE
            aiBoxBridge?.onPause()

            layoutHeader.visibility = View.VISIBLE
            avatarContainer.visibility = View.VISIBLE

            rvChat?.visibility = View.VISIBLE
            findViewById<View>(R.id.layout_bottom_area).visibility = View.VISIBLE
        }
    }

    private fun loadSession(sessionId: Long) {
        if (checkSystemBusy()) return

        lifecycleScope.launch {
            resetModel(modelHandle)
            kvCachePrimed = false
            chatAdapter?.clear()

            val messages = repository.getMessages(sessionId)
            currentSessionId = sessionId

            val sb = StringBuilder()
            var count = 0
            for (i in messages.indices) {
                val msg = messages[i]
                val type = if (msg.role == "USER") ChatMessage.TYPE_USER else ChatMessage.TYPE_AI
                chatAdapter?.addMessage(ChatMessage(type, msg.content))

                // Build history for context, excluding trailing user message if no response yet
                if (msg.role == "USER") {
                    if (i + 1 < messages.size && messages[i + 1].role == "AI") {
                        sb.append(PromptBuilder.buildUserTurn(currentModel, msg.content))
                        sb.append(messages[i + 1].content)
                        sb.append(PromptBuilder.getEotToken(currentModel)).append("\n")
                        count++
                    }
                }
            }

            fullHistory = trimHistoryToPromptBudget(sb.toString(), "")
            turnCount = minOf(count, maxTurns - 1)
            chatAdapter?.let { rvChat?.smoothScrollToPosition(it.itemCount - 1) }
            historyCard?.visibility = View.GONE
        }
    }

    private fun resetToNewChat() {
        resetModel(modelHandle)
        kvCachePrimed = false
        fullHistory = ""
        turnCount = 0
        chatAdapter?.clear()
        currentSessionId = null
        historyCard?.visibility = View.GONE
        voiceCard?.visibility = View.GONE
    }

    private fun updateUiCardVisibility(target: View) {
        val views = listOf(voiceCard, historyCard)
        views.forEach { v ->
            if (v == target) {
                v?.visibility = if (v?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            } else {
                v?.visibility = View.GONE
            }
        }
        if (target.visibility == View.VISIBLE) hideKeyboard()
    }

    private fun checkSystemBusy(): Boolean {
        if (isGenerating || isSummarizing || (voiceManager?.isRecording == true)) {
            showVoiceToast("Hệ thống đang bận")
            return true
        }
        return false
    }

    private fun loadModel(type: PromptBuilder.ModelType) {
        currentModel = type
        fullHistory = ""
        kvCachePrimed = false
        turnCount = 0
        currentSessionId = null

        val yamlPath = if (type == PromptBuilder.ModelType.QWEN) QWEN_YAML_PATH else LLAMA_YAML_PATH
        val modelName = getModelNameFromYaml(yamlPath)
        updateModelStatus("Loading $modelName")

        val missingFile = getMissingModelFile(type)
        if (missingFile != null) {
            appendChat("Không tìm thấy file model: $missingFile")
            setUiLoadingState(false)
            return
        }

        if (File(yamlPath).exists()) {
            if (!canAccessNpuDevice()) {
                appendChat("Không có quyền mở /dev/apusys.")
                updateModelStatus("NPU permission denied")
                setUiLoadingState(false)
                return
            }
            appendChat("⚙️ Đang khởi tạo $modelName...")
            setUiLoadingState(true)
            InitModelTask().execute(yamlPath)
        } else {
            appendChat("❌ Không tìm thấy YAML: $yamlPath")
            setUiLoadingState(false)
        }
    }

    private fun switchModel(newType: PromptBuilder.ModelType) {
        val yamlPath = if (newType == PromptBuilder.ModelType.QWEN) QWEN_YAML_PATH else LLAMA_YAML_PATH
        if (getMissingModelFile(newType) != null || !File(yamlPath).exists()) {
            showVoiceToast("Model không có sẵn trên thiết bị này")
            return
        }

        setUiLoadingState(true)
        val modelName = getModelNameFromYaml(yamlPath)
        updateModelStatus("Loading $modelName")
        appendChat("\n🔄 Đang chuyển sang $modelName...")

        object : AsyncTask<Void, Void, Void?>() {
            override fun doInBackground(vararg voids: Void): Void? {
                if (modelHandle != 0L) {
                    destroyModel(modelHandle)
                    modelHandle = 0
                }
                return null
            }

            override fun onPostExecute(aVoid: Void?) {
                loadModel(newType)
            }
        }.execute()
    }

    private fun countPromptTokens(text: String?): Int {
        if (text.isNullOrEmpty()) return 0
        if (modelHandle != 0L) {
            try {
                val nativeCount = countTokens(text)
                if (nativeCount >= 0) return nativeCount
            } catch (e: UnsatisfiedLinkError) {
                Log.w(LOG_TAG, "countTokens native unavailable")
            }
        }
        val matcher = Pattern.compile("(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?:\$|[^\\S])|\\s+").matcher(text)
        var count = 0
        while (matcher.find()) count++
        return count
    }

    private fun canSendPrompt(prompt: String): Boolean {
        val newUserTurn = PromptBuilder.buildUserTurn(currentModel, prompt)
        val inferencePrompt = if (kvCachePrimed) {
            PromptBuilder.getEotToken(currentModel) + "\n" + newUserTurn
        } else {
            PromptBuilder.buildFullPrompt(currentModel, trimHistoryToPromptBudget(fullHistory, newUserTurn) + newUserTurn)
        }
        return countPromptTokens(inferencePrompt) <= MAX_PROMPT_TOKENS
    }

    private fun trimHistoryToPromptBudget(history: String, newUserTurn: String): String {
        var trimmedHistory = history
        val eot = PromptBuilder.getEotToken(currentModel)
        while (trimmedHistory.isNotEmpty() && countPromptTokens(PromptBuilder.buildFullPrompt(currentModel, trimmedHistory + newUserTurn)) > MAX_PROMPT_TOKENS) {
            val firstEot = trimmedHistory.indexOf(eot)
            if (firstEot == -1) {
                trimmedHistory = ""
                break
            }
            val secondEot = trimmedHistory.indexOf(eot, firstEot + eot.length)
            if (secondEot == -1) {
                trimmedHistory = ""
                break
            }
            trimmedHistory = trimmedHistory.substring(secondEot + eot.length).trim() + "\n"
        }
        return trimmedHistory
    }

    private fun summarizeConversation() {
        if (isSummarizing) {
            showVoiceToast("Đang tóm tắt, vui lòng chờ...")
            return
        }
        if (modelHandle == 0L) {
            showVoiceToast("Model chưa sẵn sàng")
            return
        }
        chatAdapter?.let {
            if (it.itemCount == 0) {
                showVoiceToast("Chưa có nội dung để tóm tắt")
                return
            }
        }

        isSummarizing = true
        setGeneratingState(true)
        btnSummarize?.isEnabled = false
        setOtherTabsEnabled(false)

        chatAdapter?.addMessage(ChatMessage(ChatMessage.TYPE_INFO, "⌛ Đang chuẩn bị tóm tắt..."))
        chatAdapter?.let { rvChat?.smoothScrollToPosition(it.itemCount - 1) }

        SummarizeTask().execute()
    }

    private inner class SummarizeTask : AsyncTask<Void, String, String>() {
        override fun doInBackground(vararg voids: Void): String {
            val allMessages = chatAdapter?.getAllMessages() ?: return ""
            val pairs = mutableListOf<Array<String>>()
            var pendingUser: String? = null
            for (msg in allMessages) {
                if (msg.type == ChatMessage.TYPE_USER) pendingUser = msg.content
                else if (msg.type == ChatMessage.TYPE_AI && pendingUser != null) {
                    pairs.add(arrayOf(pendingUser, msg.content))
                    pendingUser = null
                }
            }

            if (pairs.isEmpty()) return ""

            val phrases = mutableListOf<String>()
            for (i in pairs.indices) {
                val pair = pairs[i]
                publishProgress("⌛ Đang xử lý ý ${i + 1}/${pairs.size}...")

                val prompt = PromptBuilder.buildPairPhrasePrompt(currentModel, pair[0], pair[1])
                val result = runSingleInference(prompt).trim().replace("[\\s.!?]+$".toRegex(), "").replace("\\s+".toRegex(), " ")
                if (result.isNotEmpty()) {
                    phrases.add(result)
                }
            }

            var joined = phrases.joinToString(". ")
            if (joined.isEmpty()) return ""
            if (!joined.endsWith(".")) joined += "."

            publishProgress("✨ Đang hoàn thiện bản tóm tắt...")
            val finalResult = runSingleInference(PromptBuilder.buildFinalCompressPrompt(currentModel, joined))

            resetModel(modelHandle)
            kvCachePrimed = false
            return if (finalResult.isEmpty()) joined else finalResult
        }

        override fun onProgressUpdate(vararg values: String) {
            chatAdapter?.updateLastMessage(values[0])
        }

        private fun runSingleInference(prompt: String): String {
            resetModel(modelHandle)
            val result = StringBuilder()
            computeStreaming(modelHandle, prompt, object : TokenCallback {
                override fun onTokenReceived(token: String): Boolean {
                    result.append(token)
                    return true
                }
            })
            return result.toString()
        }

        override fun onPostExecute(result: String) {
            isSummarizing = false
            setGeneratingState(false)
            btnSummarize?.isEnabled = true
            setOtherTabsEnabled(true)

            if (result.isEmpty()) {
                chatAdapter?.updateLastMessage("❌ Không tóm tắt được, vui lòng thử lại.")
            } else {
                chatAdapter?.updateLastMessage("📝 $result")
                chatAdapter?.let { rvChat?.smoothScrollToPosition(it.itemCount - 1) }
            }
        }
    }

    private inner class StreamingComputeTask : AsyncTask<String, String, Void?>() {
        private var isFirstToken = true
        private var aiMessage: ChatMessage? = null
        private val assistantResponse = StringBuilder()
        private var startTime: Long = 0
        private var firstTokenTime: Long = 0
        private var endTime: Long = 0
        private var tokenCount = 0
        private val uiPendingBuffer = StringBuilder()
        private val uiBufferLock = Any()
        private var uiFlushHandler: Handler? = null
        private var uiFlushRunnable: Runnable? = null

        @Volatile
        private var stopGenerating = false
        private var hasShownPerfLog = false
        private val completedLines = mutableListOf<String>()
        private val currentLineBuffer = StringBuilder()

        override fun onPreExecute() {
            super.onPreExecute()
            aiMessage = ChatMessage(ChatMessage.TYPE_AI, "...")
            aiMessage?.let { chatAdapter?.addMessage(it) }
            maybeAutoScroll()
        }

        override fun doInBackground(vararg inputs: String): Void? {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            val currentPrompt = inputs[0]
            turnCount++
            if (turnCount > maxTurns) {
                fullHistory = ""
                kvCachePrimed = false
                turnCount = 1
            }

            val newUserTurn = PromptBuilder.buildUserTurn(currentModel, currentPrompt)
            val formattedPrompt = if (kvCachePrimed) {
                PromptBuilder.getEotToken(currentModel) + "\n" + newUserTurn
            } else {
                PromptBuilder.buildFullPrompt(currentModel, trimHistoryToPromptBudget(fullHistory, newUserTurn) + newUserTurn)
            }

            fullHistory += newUserTurn
            startTime = System.currentTimeMillis()
            firstTokenTime = 0
            stopGenerating = false
            hasShownPerfLog = false
            startUiFlushLoop()

            computeStreaming(modelHandle, formattedPrompt, object : TokenCallback {
                override fun onTokenReceived(token: String): Boolean {
                    if (stopGenerating) return false
                    if (firstTokenTime == 0L) firstTokenTime = System.currentTimeMillis()

                    assistantResponse.append(token)
                    tokenCount++

                    synchronized(uiBufferLock) { uiPendingBuffer.append(token) }

                    currentLineBuffer.append(token)
                    while (currentLineBuffer.indexOf("\n") != -1) {
                        val idx = currentLineBuffer.indexOf("\n")
                        val line = currentLineBuffer.substring(0, idx)
                        currentLineBuffer.delete(0, idx + 1)
                        if (detectRepeatedListItem(line)) {
                            triggerEarlyStop("⚠️ Lặp danh sách. Đang dừng NPU...")
                            return false
                        }
                    }
                    if (!stopGenerating && detectLongRepetition(assistantResponse)) {
                        triggerEarlyStop("⚠️ Lặp câu dài. Đang dừng NPU...")
                        return false
                    }
                    return true
                }
            })

            stopUiFlushLoop()

            fullHistory += assistantResponse.toString() + PromptBuilder.getEotToken(currentModel) + "\n"
            if (endTime == 0L) endTime = System.currentTimeMillis()

            kvCachePrimed = true
            return null
        }

        private fun triggerEarlyStop(reason: String) {
            stopGenerating = true
            endTime = System.currentTimeMillis()
            Log.w(LOG_TAG, "Early stop triggered: $reason")
            runOnUiThread { appendChat(reason) }
        }

        private fun detectLongRepetition(fullResponse: StringBuilder): Boolean {
            val text = fullResponse.toString()
            val totalLen = text.length
            if (totalLen < 15) return false
            val maxCheck = Math.min(100, totalLen / 2)
            for (len in 5..maxCheck) {
                val tail = text.substring(totalLen - len)
                val prev = text.substring(totalLen - len * 2, totalLen - len)
                if (tail == prev) {
                    if (len > 15 || tail.contains(" ") || tail.contains("\n")) return true
                }
            }
            return false
        }

        private fun detectRepeatedListItem(line: String): Boolean {
            val normalized = line.replaceFirst("^\\s*\\d+[\\.\\)]\\s*".toRegex(), "").trim().lowercase()
            if (normalized.length < 8) return false
            for (existing in completedLines) {
                if (normalized == existing) return true
            }
            completedLines.add(normalized)
            return false
        }

        private fun startUiFlushLoop() {
            uiFlushHandler = Handler(Looper.getMainLooper())
            uiFlushRunnable = object : Runnable {
                override fun run() {
                    val chunk: String
                    synchronized(uiBufferLock) {
                        chunk = uiPendingBuffer.toString()
                        uiPendingBuffer.setLength(0)
                    }
                    if (chunk.isNotEmpty()) appendToAiMessage(chunk)
                    uiFlushHandler?.postDelayed(this, UI_FLUSH_INTERVAL_MS)
                }
            }
            uiFlushRunnable?.let { uiFlushHandler?.post(it) }
        }

        private fun stopUiFlushLoop() {
            uiFlushHandler?.let { handler ->
                uiFlushRunnable?.let { handler.removeCallbacks(it) }
                val leftover: String
                synchronized(uiBufferLock) {
                    leftover = uiPendingBuffer.toString()
                    uiPendingBuffer.setLength(0)
                }
                if (leftover.isNotEmpty()) handler.post { appendToAiMessage(leftover) }
            }
        }

        private fun appendToAiMessage(chunk: String) {
            if (isFirstToken) {
                aiMessage?.content = ""
                isFirstToken = false
            }
            aiMessage?.let {
                it.content = it.content + chunk
                chatAdapter?.updateLastMessage(it.content)
            }
            maybeAutoScroll()
        }

        override fun onProgressUpdate(vararg values: String) {
            appendToAiMessage(values[0])
        }

        private fun showPerformanceLog() {
            if (hasShownPerfLog || tokenCount == 0) return
            hasShownPerfLog = true
            val finishedAt = if (endTime > 0) endTime else System.currentTimeMillis()
            val totalDur = Math.max(1, finishedAt - startTime)
            val tps = tokenCount / (totalDur / 1000.0)
            val perf = String.format(
                "[Turn %d/%d | %d tokens | TTFT %.2fs | %.2f tok/s]",
                turnCount, maxTurns, tokenCount,
                (if (firstTokenTime > 0) (firstTokenTime - startTime) else 0) / 1000.0,
                tps
            )
            appendChat(perf)
        }

        private fun maybeAutoScroll() {
            rvChat?.post {
                val lm = rvChat?.layoutManager as? LinearLayoutManager ?: return@post
                chatAdapter?.let { adapter ->
                    if (adapter.itemCount == 0) return@post
                    val lastPos = adapter.itemCount - 1
                    val lastVisible = lm.findLastVisibleItemPosition()
                    if (lastVisible >= lastPos - 1) {
                        val lastView = lm.findViewByPosition(lastPos)
                        if (lastView == null) rvChat?.scrollToPosition(lastPos)
                        else {
                            val rBottom = (rvChat?.height ?: 0) - (rvChat?.paddingBottom ?: 0)
                            val vBottom = lastView.bottom
                            if (vBottom > rBottom) rvChat?.scrollBy(0, vBottom - rBottom)
                        }
                    }
                }
            }
        }

        override fun onPostExecute(aVoid: Void?) {
            setGeneratingState(false)
            showPerformanceLog()

            val response = assistantResponse.toString()
            if (!isCancelled && response.isNotEmpty() && response != "...") {
                currentSessionId?.let { sessionId ->
                    lifecycleScope.launch {
                        repository.saveMessage(sessionId, "AI", response)
                    }
                }
            }
        }
    }

    private inner class InitModelTask : AsyncTask<String, Void, Long>() {
        override fun doInBackground(vararg paths: String): Long {
            return initModel(paths[0])
        }

        override fun onPostExecute(result: Long) {
            modelHandle = result
            setUiLoadingState(false)
            if (modelHandle != 0L) {
                updateModelStatus(if (PromptBuilder.getEotToken(currentModel).contains("im")) "Qwen 2.5 Online" else "Llama 3.2 Online")
                appendChat("✅ Sẵn sàng! Bắt đầu chat 🚀")
            } else {
                updateModelStatus("Failed")
                appendChat("❌ Khởi tạo thất bại.")
            }
        }
    }

    private fun appendChat(text: String) {
        runOnUiThread {
            chatAdapter?.addMessage(ChatMessage(ChatMessage.TYPE_INFO, text))
            chatAdapter?.let { rvChat?.smoothScrollToPosition(it.itemCount - 1) }
        }
    }

    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun updateModelStatus(status: String) {
        runOnUiThread { modelStatusTextView?.text = status }
    }

    private fun setGeneratingState(gen: Boolean) {
        isGenerating = gen
        runOnUiThread {
            sendButton?.isEnabled = !gen
            userInput?.isEnabled = !gen
            settingsButton?.isEnabled = !gen
        }
    }

    private fun setUiLoadingState(load: Boolean) {
        runOnUiThread {
            sendButton?.isEnabled = !load
            userInput?.isEnabled = !load
            settingsButton?.isEnabled = !load
        }
    }

    private fun showVoiceToast(msg: String) {
        currentToast?.cancel()
        currentToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT)
        currentToast?.show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (imm != null && currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        }
        userInput?.clearFocus()
    }

    private fun getModelNameFromYaml(path: String): String {
        var name = File(path).name.replaceFirst("^config_".toRegex(), "").replaceFirst("_instruct.*$".toRegex(), "")
        val sep = name.lastIndexOf('_')
        if (sep > 0) {
            name = name.substring(0, 1).uppercase() + name.substring(1, sep) + " (" + name.substring(sep + 1).uppercase() + ")"
        }
        return name
    }

    private fun getMissingModelFile(type: PromptBuilder.ModelType): String? {
        if (type == PromptBuilder.ModelType.QWEN) {
            val basePath = "/system_ext/llm_sdk/Qwen2.5-1.5B-Instruct/"
            for (f in arrayOf(QWEN_YAML_PATH, basePath + "merges.txt", basePath + "vocab.txt", basePath + "embedding_int16.bin")) {
                if (!File(f).exists()) return f
            }
            return null
        }
        return if (File(LLAMA_YAML_PATH).exists()) null else LLAMA_YAML_PATH
    }

    private fun canAccessNpuDevice(): Boolean {
        val f = File("/dev/apusys")
        return f.exists() && f.canRead() && f.canWrite()
    }

    private fun setupNpuPermissions() {
        try {
            val s = File(cacheDir, "npu.sh")
            val fw = java.io.FileWriter(s)
            fw.write("#!/system/bin/sh\n" +
                    "chmod 666 /dev/apusys /dev/apuext /dev/apusys_apummu /dev/dma_heap/system /dev/dma_heap/system-uncached /dev/dma_heap/mtk_mm /dev/dma_heap/mtk_mm-uncached\n" +
                    "setenforce 0\n" +
                    "# Tat log de tang toc do\n" +
                    "setprop vendor.debug.mtk_llm.loglevel 0\n" +
                    "setprop vendor.debug.apusys.loglevel 0\n" +
                    "setprop vendor.debug.neuron.loglevel 0\n" +
                    "setprop vendor.debug.CHECK_API.loglevel 0\n")
            fw.close()
            s.setExecutable(true)
            Runtime.getRuntime().exec(arrayOf("su", "-c", s.absolutePath)).waitFor()
        } catch (ignored: Exception) {
        }
    }

    private fun bindPlaceholderFeature(id: Int) {
        val v = findViewById<View>(id)
        v?.setOnClickListener {
            if (voiceManager?.isSessionActive == true) showVoiceToast("Vui lòng nhấn Gửi để hoàn tất ghi âm trước")
            else showCustomToast("Tính năng đang phát triển")
        }
    }

    private fun showCustomToast(msg: String) {
        val l = LayoutInflater.from(this).inflate(R.layout.custom_toast, null)
        l.findViewById<TextView>(R.id.toast_message).text = msg
        val t = Toast(this)
        t.duration = Toast.LENGTH_SHORT
        t.view = l
        t.show()
    }

    private fun updateMicVisualState(s: Int) {
        when (s) {
            MIC_STATE_RECORDING -> {
                btnMicRecord?.setBackgroundResource(R.drawable.bg_circle_blue)
                btnMicRecord?.setImageResource(android.R.drawable.ic_btn_speak_now)
                viewMicRing?.setBackgroundResource(R.drawable.bg_circle_blue_ring)
                viewMicRing?.visibility = View.VISIBLE
                tvRecordStatus?.text = "Đang ghi âm..."
            }
            MIC_STATE_PROCESSING -> {
                btnMicRecord?.setBackgroundResource(R.drawable.bg_circle_red)
                btnMicRecord?.setImageResource(android.R.drawable.ic_btn_speak_now)
                viewMicRing?.setBackgroundResource(R.drawable.bg_circle_red_ring)
                viewMicRing?.visibility = View.GONE
                tvRecordStatus?.text = "Đang xử lý..."
            }
            else -> {
                btnMicRecord?.setBackgroundResource(R.drawable.bg_circle_red)
                btnMicRecord?.setImageResource(android.R.drawable.ic_btn_speak_now)
                viewMicRing?.setBackgroundResource(R.drawable.bg_circle_red_ring)
                viewMicRing?.visibility = View.GONE
                tvRecordStatus?.text = "Nhấn mic để ghi âm"
            }
        }
    }

    private fun setOtherTabsEnabled(e: Boolean) {
        val a = if (e) 1f else 0.4f
        tabText?.isEnabled = e
        tabText?.alpha = a
        findViewById<View>(R.id.tab_image).apply { isEnabled = e; alpha = a }
        findViewById<View>(R.id.tab_video).apply { isEnabled = e; alpha = a }
        findViewById<View>(R.id.tab_live).apply { isEnabled = e; alpha = a }
    }

    private fun launchSettingsActivity() {
        val i = Intent(this, SettingsActivity::class.java)
        i.putExtra(SettingsActivity.EXTRA_CURRENT_MODEL, if (currentModel == PromptBuilder.ModelType.QWEN) SettingsActivity.MODEL_QWEN else SettingsActivity.MODEL_LLAMA)
        i.putExtra(SettingsActivity.EXTRA_LLAMA_AVAILABLE, File(LLAMA_YAML_PATH).exists())
        i.putExtra(SettingsActivity.EXTRA_QWEN_AVAILABLE, File(QWEN_YAML_PATH).exists())
        startActivityForResult(i, REQUEST_SETTINGS)
    }

    private fun refreshMaxTurnsFromSettings() {
        maxTurns = AppSettings.getMaxTurns(this)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        refreshMaxTurnsFromSettings()
        aiBoxBridge?.onResume()
    }

    override fun onPause() {
        super.onPause()
        aiBoxBridge?.onPause()
        voiceManager?.release()
    }

    override fun onDestroy() {
        if (modelHandle != 0L) destroyModel(modelHandle)
        voiceManager?.release()
        aiBoxBridge?.onDestroy()
        super.onDestroy()
    }

    override fun onActivityResult(req: Int, res: Int, d: Intent?) {
        super.onActivityResult(req, res, d)
        hideSystemBars()
        if (req == REQUEST_SETTINGS && res == RESULT_OK && d != null) {
            val sel = d.getStringExtra(SettingsActivity.EXTRA_SELECTED_MODEL)
            if (sel != null) {
                val type = if (SettingsActivity.MODEL_QWEN == sel) PromptBuilder.ModelType.QWEN else PromptBuilder.ModelType.LLAMA
                if (type != currentModel) switchModel(type)
            }
            refreshMaxTurnsFromSettings()
        }
    }

    companion object {
        private const val LOG_TAG = "NP_LLM_DEMO"
        private var sApuMdwAvailable = false

        private const val LLAMA_YAML_PATH = "/system_ext/llm_sdk/config_llama3.2_1b_instruct.yaml"
        private const val QWEN_YAML_PATH = "/system_ext/llm_sdk/config_qwen2.5_1.5b_instruct.yaml"
        private const val REQUEST_SETTINGS = 1001
        private const val MAX_PROMPT_TOKENS = 128

        private const val MIC_STATE_IDLE = 0
        private const val MIC_STATE_RECORDING = 1
        private const val MIC_STATE_PROCESSING = 2

        private const val UI_FLUSH_INTERVAL_MS = 90L

        init {
            Log.d(LOG_TAG, "StaticBlock: Loading native library dependencies...")
            val libs = arrayOf("c++_shared", "c++", "base", "dmabufheap", "cutils", "apu_mdw", "apu_mdw_batch",
                             "neuron_adapter", "neuron_runtime", "common", "mtk_llm", "nn_sample")
            for (lib in libs) {
                try {
                    System.loadLibrary(lib)
                    Log.d(LOG_TAG, "Loaded lib$lib")
                    if (lib == "apu_mdw") sApuMdwAvailable = true
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(LOG_TAG, "Cannot load lib$lib: ${e.message}")
                }
            }
        }
    }
}
