package com.bhs.meetingnotes

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bhs.meetingnotes.adapter.MeetingAdapter
import com.bhs.meetingnotes.db.MeetingDatabase
import com.bhs.meetingnotes.db.MeetingEntity
import com.bhs.meetingnotes.util.ReportExporter
import com.mediatek.neuropilot.jnidemo.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

import android.view.LayoutInflater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Màn hình 1: Danh sách cuộc họp (Screen 1 theo chuẩn BA v2).
 * Hỗ trợ bộ lọc Ngôn ngữ, Bộ lọc Ngày, Tìm kiếm tức thì, Thùng rác (Soft Delete) và Khôi phục.
 */
class MeetingNotesActivity : AppCompatActivity() {

    private lateinit var db: MeetingDatabase
    private lateinit var meetingAdapter: MeetingAdapter

    private var activeMeetings: List<MeetingEntity> = emptyList()
    private var trashMeetings: List<MeetingEntity> = emptyList()
    private var selectedLanguageFilter: String = "ALL" // "ALL", "vi", "en", "TRASH"
    private var selectedDateFilter: String = "ALL" // "ALL", "TODAY", "WEEK"
    private var currentSearchQuery: String = ""

    // Views
    private var navAll: LinearLayout? = null
    private var navVi: LinearLayout? = null
    private var navEn: LinearLayout? = null
    private var navTrash: LinearLayout? = null
    private var tvCountAll: TextView? = null
    private var tvCountVi: TextView? = null
    private var tvCountEn: TextView? = null
    private var tvCountTrash: TextView? = null

    private var btnFilterDropdown: View? = null
    private var tvSelectedDateFilter: TextView? = null
    private var btnSearchClear: View? = null

    private var tvHeaderTitle: TextView? = null
    private var tvHeaderSubtitle: TextView? = null

    private var etSearch: EditText? = null
    private var rvMeetings: RecyclerView? = null
    private var llEmptyState: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting_list)

        db = MeetingDatabase.getInstance(this)
        initViews()
        setupRecyclerView()
        observeMeetings()
    }

    private fun initViews() {
        navAll = findViewById(R.id.nav_all)
        navVi = findViewById(R.id.nav_vi)
        navEn = findViewById(R.id.nav_en)
        navTrash = findViewById(R.id.nav_trash)
        tvCountAll = findViewById(R.id.tv_count_all)
        tvCountVi = findViewById(R.id.tv_count_vi)
        tvCountEn = findViewById(R.id.tv_count_en)
        tvCountTrash = findViewById(R.id.tv_count_trash)

        tvHeaderTitle = findViewById(R.id.tv_header_title)
        tvHeaderSubtitle = findViewById(R.id.tv_header_subtitle)

        btnFilterDropdown = findViewById(R.id.btn_filter_dropdown)
        tvSelectedDateFilter = findViewById(R.id.tv_selected_date_filter)
        btnSearchClear = findViewById(R.id.btn_search_clear)

        etSearch = findViewById(R.id.et_search)
        rvMeetings = findViewById(R.id.rv_meetings)
        llEmptyState = findViewById(R.id.ll_empty_state)

        // Dropdown Date Filter (Tất cả, Hôm nay, Tuần này, Tháng này)
        btnFilterDropdown?.setOnClickListener { view ->
            showDateFilterPopup(view)
        }

        // Record Button with New Meeting Dialog (duy nhất trên sidebar)
        findViewById<View>(R.id.btn_record)?.setOnClickListener {
            showNewMeetingDialog()
        }

        // Settings Button
        findViewById<View>(R.id.btn_settings)?.setOnClickListener {
            startActivity(Intent(this, MeetingSettingsActivity::class.java))
        }

        // Sidebar navigation
        navAll?.setOnClickListener { setLanguageFilter("ALL") }
        navVi?.setOnClickListener { setLanguageFilter("vi") }
        navEn?.setOnClickListener { setLanguageFilter("en") }
        navTrash?.setOnClickListener { setLanguageFilter("TRASH") }

        // Real-time search with clear button & keyboard dismissal
        btnSearchClear?.setOnClickListener {
            etSearch?.text?.clear()
            hideKeyboard(etSearch)
            etSearch?.clearFocus()
        }

        etSearch?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(etSearch)
                etSearch?.clearFocus()
                true
            } else {
                false
            }
        }

        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                btnSearchClear?.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        startMicPulseAnimation()
    }

    private fun startMicPulseAnimation() {
        val pulseOuter = findViewById<View>(R.id.v_mic_pulse_outer) ?: return
        val pulseInner = findViewById<View>(R.id.v_mic_pulse_inner) ?: return

        // Outer pulse ring animation: scale 0.95 -> 1.25, alpha 0.7 -> 0.0
        val scaleOuterX = ObjectAnimator.ofFloat(pulseOuter, "scaleX", 0.95f, 1.25f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            duration = 1800
        }
        val scaleOuterY = ObjectAnimator.ofFloat(pulseOuter, "scaleY", 0.95f, 1.25f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            duration = 1800
        }
        val alphaOuter = ObjectAnimator.ofFloat(pulseOuter, "alpha", 0.7f, 0.0f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            duration = 1800
        }

        // Inner pulse ring animation with 400ms offset
        val scaleInnerX = ObjectAnimator.ofFloat(pulseInner, "scaleX", 0.95f, 1.18f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            duration = 1800
            startDelay = 400
        }
        val scaleInnerY = ObjectAnimator.ofFloat(pulseInner, "scaleY", 0.95f, 1.18f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            duration = 1800
            startDelay = 400
        }
        val alphaInner = ObjectAnimator.ofFloat(pulseInner, "alpha", 0.8f, 0.1f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            duration = 1800
            startDelay = 400
        }

        val animSet = AnimatorSet()
        animSet.playTogether(scaleOuterX, scaleOuterY, alphaOuter, scaleInnerX, scaleInnerY, alphaInner)
        animSet.start()
    }

    private fun setupRecyclerView() {
        meetingAdapter = MeetingAdapter(
            onItemClick = { meeting ->
                when (meeting.status) {
                    "RECORDING" -> {
                        val intent = Intent(this, MeetingRecordActivity::class.java)
                        startActivity(intent)
                    }
                    "PROCESSING" -> {
                        val intent = Intent(this, MeetingProcessingActivity::class.java)
                        intent.putExtra("MEETING_ID", meeting.id)
                        startActivity(intent)
                    }
                    else -> {
                        val intent = Intent(this, MeetingResultActivity::class.java)
                        intent.putExtra("MEETING_ID", meeting.id)
                        startActivity(intent)
                    }
                }
            },
            onExportClick = { meeting ->
                showExportDialog(meeting)
            },
            onDeleteClick = { meeting ->
                showConfirmDeleteDialog(meeting, isPermanent = false)
            },
            onRestoreClick = { meeting ->
                restoreMeeting(meeting)
            },
            onDeletePermanentClick = { meeting ->
                showConfirmDeleteDialog(meeting, isPermanent = true)
            }
        )

        rvMeetings?.layoutManager = GridLayoutManager(this, 2)
        rvMeetings?.adapter = meetingAdapter
    }

    private fun observeMeetings() {
        lifecycleScope.launch {
            db.meetingDao().getAllMeetingsFlow().collectLatest { meetings ->
                activeMeetings = meetings
                updateCounts()
                applyFilters()
            }
        }
        lifecycleScope.launch {
            db.meetingDao().getTrashMeetingsFlow().collectLatest { trash ->
                trashMeetings = trash
                updateCounts()
                applyFilters()
            }
        }
    }

    private fun updateCounts() {
        val totalCount = activeMeetings.size
        val viCount = activeMeetings.count { it.language.equals("vi", ignoreCase = true) }
        val enCount = activeMeetings.count { it.language.equals("en", ignoreCase = true) }
        val trashCount = trashMeetings.size

        tvCountAll?.text = totalCount.toString()
        tvCountVi?.text = viCount.toString()
        tvCountEn?.text = enCount.toString()
        tvCountTrash?.text = trashCount.toString()
    }

    private fun setLanguageFilter(filter: String) {
        selectedLanguageFilter = filter

        val gray = ContextCompat.getColor(this, R.color.text_secondary)

        // Reset all sidebar items
        navAll?.setBackgroundResource(if (filter == "ALL") R.drawable.bg_circle_blue_ring else 0)
        navAll?.findViewById<TextView>(R.id.tv_count_all)?.setBackgroundResource(
            if (filter == "ALL") R.drawable.bg_circle_blue else R.drawable.bg_circle_gray
        )
        navAll?.findViewById<TextView>(R.id.tv_count_all)?.setTextColor(
            if (filter == "ALL") ContextCompat.getColor(this, R.color.bg_card) else gray
        )

        navVi?.setBackgroundResource(if (filter == "vi") R.drawable.bg_circle_blue_ring else 0)
        navVi?.findViewById<TextView>(R.id.tv_count_vi)?.setBackgroundResource(
            if (filter == "vi") R.drawable.bg_circle_blue else R.drawable.bg_circle_gray
        )
        navVi?.findViewById<TextView>(R.id.tv_count_vi)?.setTextColor(
            if (filter == "vi") ContextCompat.getColor(this, R.color.bg_card) else gray
        )

        navEn?.setBackgroundResource(if (filter == "en") R.drawable.bg_circle_blue_ring else 0)
        navEn?.findViewById<TextView>(R.id.tv_count_en)?.setBackgroundResource(
            if (filter == "en") R.drawable.bg_circle_blue else R.drawable.bg_circle_gray
        )
        navEn?.findViewById<TextView>(R.id.tv_count_en)?.setTextColor(
            if (filter == "en") ContextCompat.getColor(this, R.color.bg_card) else gray
        )

        navTrash?.setBackgroundResource(if (filter == "TRASH") R.drawable.bg_circle_blue_ring else 0)
        navTrash?.findViewById<TextView>(R.id.tv_count_trash)?.setBackgroundResource(
            if (filter == "TRASH") R.drawable.bg_circle_blue else R.drawable.bg_circle_gray
        )
        navTrash?.findViewById<TextView>(R.id.tv_count_trash)?.setTextColor(
            if (filter == "TRASH") ContextCompat.getColor(this, R.color.bg_card) else gray
        )

        // Set Trash Mode in Adapter & Header
        if (filter == "TRASH") {
            meetingAdapter.isTrashMode = true
            tvHeaderTitle?.text = "Thùng rác"
            tvHeaderSubtitle?.text = "Các cuộc họp đã xóa tạm thời. Bạn có thể khôi phục hoặc xóa vĩnh viễn."
        } else {
            meetingAdapter.isTrashMode = false
            tvHeaderTitle?.text = when (filter) {
                "vi" -> "Cuộc họp Tiếng Việt"
                "en" -> "English Meetings"
                else -> "Tất cả cuộc họp"
            }
            tvHeaderSubtitle?.text = "Danh sách bản ghi âm và tóm tắt thông minh"
        }

        applyFilters()
    }

    private fun showDateFilterPopup(anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Tất cả thời gian")
        popup.menu.add(0, 2, 1, "Hôm nay")
        popup.menu.add(0, 3, 2, "Tuần này")
        popup.menu.add(0, 4, 3, "Tháng này")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> setDateFilter("ALL", "Tất cả")
                2 -> setDateFilter("TODAY", "Hôm nay")
                3 -> setDateFilter("WEEK", "Tuần này")
                4 -> setDateFilter("MONTH", "Tháng này")
            }
            true
        }
        popup.show()
    }

    private fun setDateFilter(filter: String, label: String) {
        selectedDateFilter = filter
        tvSelectedDateFilter?.text = label
        applyFilters()
    }

    private fun hideKeyboard(view: View?) {
        if (view != null) {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = android.graphics.Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    hideKeyboard(v)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun applyFilters() {
        val calendar = Calendar.getInstance()

        // Today start
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis

        // Week start
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        val weekStart = calendar.timeInMillis

        // Month start
        val calMonth = Calendar.getInstance()
        calMonth.set(Calendar.DAY_OF_MONTH, 1)
        calMonth.set(Calendar.HOUR_OF_DAY, 0)
        calMonth.set(Calendar.MINUTE, 0)
        calMonth.set(Calendar.SECOND, 0)
        calMonth.set(Calendar.MILLISECOND, 0)
        val monthStart = calMonth.timeInMillis

        val sourceList = if (selectedLanguageFilter == "TRASH") trashMeetings else activeMeetings

        val filtered = sourceList.filter { meeting ->
            // 1. Language filter (nếu không phải TRASH)
            val matchLang = when (selectedLanguageFilter) {
                "vi" -> meeting.language.equals("vi", ignoreCase = true)
                "en" -> meeting.language.equals("en", ignoreCase = true)
                else -> true
            }

            // 2. Date filter
            val matchDate = when (selectedDateFilter) {
                "TODAY" -> meeting.timestamp >= todayStart
                "WEEK" -> meeting.timestamp >= weekStart
                "MONTH" -> meeting.timestamp >= monthStart
                else -> true
            }

            // 3. Search query
            val matchSearch = if (currentSearchQuery.isEmpty()) {
                true
            } else {
                meeting.title.contains(currentSearchQuery, ignoreCase = true) ||
                        meeting.summary.contains(currentSearchQuery, ignoreCase = true)
            }

            matchLang && matchDate && matchSearch
        }

        meetingAdapter.submitList(filtered)

        // Empty state visibility
        if (filtered.isEmpty()) {
            rvMeetings?.visibility = View.GONE
            llEmptyState?.visibility = View.VISIBLE
            val emptyTitle = llEmptyState?.findViewById<TextView>(R.id.tv_empty_title)
            val emptyTv = llEmptyState?.findViewById<TextView>(R.id.tv_empty_desc)
            if (selectedLanguageFilter == "TRASH") {
                emptyTitle?.text = "Thùng rác trống"
                emptyTv?.text = "Chưa có cuộc họp nào bị xóa tạm thời."
            } else {
                emptyTitle?.text = "Chưa có cuộc họp nào"
                emptyTv?.text = "Nhấn nút Mic ở góc trái để bắt đầu cuộc họp mới."
            }
        } else {
            rvMeetings?.visibility = View.VISIBLE
            llEmptyState?.visibility = View.GONE
        }
    }

    private fun showConfirmDeleteDialog(meeting: MeetingEntity, isPermanent: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tv_dialog_message)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btn_dialog_cancel)
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btn_dialog_confirm)

        if (isPermanent) {
            tvTitle.text = "Xóa vĩnh viễn cuộc họp?"
            tvMessage.text = "Bạn có chắc chắn muốn xóa vĩnh viễn cuộc họp \"${meeting.title}\"?\nToàn bộ dữ liệu văn bản và các tệp âm thanh liên quan sẽ bị xóa hoàn toàn khỏi thiết bị và không thể khôi phục."
            btnConfirm.text = "Xóa vĩnh viễn"
        } else {
            tvTitle.text = "Chuyển vào thùng rác?"
            tvMessage.text = "Cuộc họp \"${meeting.title}\" sẽ được chuyển vào thùng rác.\nBạn có thể khôi phục lại bất kỳ lúc nào từ mục Thùng rác."
            btnConfirm.text = "Chuyển vào thùng rác"
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            if (isPermanent) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        if (meeting.audioFilePath.isNotBlank()) {
                            val f = java.io.File(meeting.audioFilePath)
                            if (f.exists()) f.delete()
                        }
                        val segments = db.segmentDao().getSegmentsForMeeting(meeting.id)
                        for (seg in segments) {
                            if (seg.filePath.isNotBlank()) {
                                val sf = java.io.File(seg.filePath)
                                if (sf.exists()) sf.delete()
                            }
                        }
                        db.meetingDao().deleteMeetingById(meeting.id)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MeetingNotesActivity, "Đã xóa vĩnh viễn cuộc họp", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MeetingNotesActivity, "Lỗi khi xóa: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.meetingDao().softDeleteMeeting(meeting.id)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MeetingNotesActivity, "Đã chuyển vào thùng rác", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun restoreMeeting(meeting: MeetingEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.meetingDao().restoreMeeting(meeting.id)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MeetingNotesActivity, "Đã khôi phục \"${meeting.title}\"", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showExportDialog(meeting: MeetingEntity) {
        val formats = arrayOf("Báo cáo đầy đủ (PDF)", "Văn bản tóm tắt & Transcript (TXT)")
        AlertDialog.Builder(this)
            .setTitle("Xuất báo cáo cuộc họp: ${meeting.title}")
            .setItems(formats) { _, which ->
                val format = if (which == 0) "PDF" else "TXT"
                lifecycleScope.launch {
                    try {
                        ReportExporter.exportReport(this@MeetingNotesActivity, meeting, format)
                        Toast.makeText(this@MeetingNotesActivity, "Đã xuất báo cáo $format thành công vào Download", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@MeetingNotesActivity, "Lỗi xuất báo cáo: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showNewMeetingDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_meeting, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etTitle = dialogView.findViewById<EditText>(R.id.et_title)
        val btnClose = dialogView.findViewById<View>(R.id.btn_close)
        val cardLangVi = dialogView.findViewById<View>(R.id.card_lang_vi)
        val cardLangEn = dialogView.findViewById<View>(R.id.card_lang_en)
        val tvLangVi = dialogView.findViewById<TextView>(R.id.tv_lang_vi)
        val tvLangEn = dialogView.findViewById<TextView>(R.id.tv_lang_en)
        val ivCheckVi = dialogView.findViewById<android.widget.ImageView>(R.id.iv_check_vi)
        val ivCheckEn = dialogView.findViewById<android.widget.ImageView>(R.id.iv_check_en)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_dialog_cancel)
        val btnStart = dialogView.findViewById<View>(R.id.btn_dialog_start)

        val defaultTitle = "Cuộc họp " + java.text.SimpleDateFormat("dd-MM-yyyy HH'h'mm", java.util.Locale.getDefault()).format(java.util.Date())
        etTitle?.setText(defaultTitle)
        etTitle?.setSelectAllOnFocus(true)

        var selectedLang = "vi"
        fun updateLanguageUI() {
            if (selectedLang == "vi") {
                cardLangVi?.setBackgroundResource(R.drawable.bg_lang_selected)
                tvLangVi?.setTextColor(ContextCompat.getColor(this, R.color.bg_card))
                ivCheckVi?.visibility = View.VISIBLE

                cardLangEn?.setBackgroundResource(R.drawable.bg_lang_unselected)
                tvLangEn?.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                ivCheckEn?.visibility = View.INVISIBLE
            } else {
                cardLangVi?.setBackgroundResource(R.drawable.bg_lang_unselected)
                tvLangVi?.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                ivCheckVi?.visibility = View.INVISIBLE

                cardLangEn?.setBackgroundResource(R.drawable.bg_lang_selected)
                tvLangEn?.setTextColor(ContextCompat.getColor(this, R.color.bg_card))
                ivCheckEn?.visibility = View.VISIBLE
            }
        }
        updateLanguageUI()

        cardLangVi?.setOnClickListener {
            selectedLang = "vi"
            updateLanguageUI()
        }
        cardLangEn?.setOnClickListener {
            selectedLang = "en"
            updateLanguageUI()
        }

        btnClose?.setOnClickListener { dialog.dismiss() }
        btnCancel?.setOnClickListener { dialog.dismiss() }

        btnStart?.setOnClickListener {
            dialog.dismiss()
            val typedTitle = etTitle?.text?.toString()?.trim()
            val finalTitle = if (!typedTitle.isNullOrBlank()) {
                typedTitle
            } else {
                defaultTitle
            }

            val intent = Intent(this, MeetingRecordActivity::class.java).apply {
                putExtra("MEETING_TITLE", finalTitle)
                putExtra("MEETING_LANGUAGE", selectedLang)
            }
            startActivity(intent)
        }

        dialog.show()

        // 620dp wide dialog for 1024x600 landscape tablet
        val widthPx = (620 * resources.displayMetrics.density).toInt()
        dialog.window?.setLayout(widthPx, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showLanguageSelectionDialog() {
        val options = arrayOf("Tiếng Việt (PhoWhisper)", "English (Whisper)")
        val checkedItem = if (appSettings.language == "vi") 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Chọn ngôn ngữ cuộc họp")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val selectedLang = if (which == 0) "vi" else "en"
                appSettings.language = selectedLang
                appSettings.asrModel = if (selectedLang == "vi") "PhoWhisper (VI)" else "Whisper (EN)"
                
                // Save the port based on the selected language
                val port = if (selectedLang == "vi") 8080 else 8082
                getSharedPreferences("meeting_notes_prefs", MODE_PRIVATE)
                    .edit()
                    .putInt("whisper_server_port", port)
                    .apply()
                
                dialog.dismiss()
                
                startServerAndProceed(selectedLang)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showUploadModelSelectionDialog() {
        val options = arrayOf("Tiếng Việt (PhoWhisper - Port 8080)", "English (Whisper - Port 8082)")
        val checkedItem = if (appSettings.language == "vi") 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Chọn Model nhận dạng cho file")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val selectedLang = if (which == 0) "vi" else "en"
                appSettings.language = selectedLang
                appSettings.asrModel = if (selectedLang == "vi") "PhoWhisper (VI)" else "Whisper (EN)"
                
                val port = if (selectedLang == "vi") 8080 else 8082
                getSharedPreferences("meeting_notes_prefs", MODE_PRIVATE)
                    .edit()
                    .putInt("whisper_server_port", port)
                    .apply()
                
                dialog.dismiss()
                pickAudioLauncher.launch("*/*")
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun startServerAndProceed(language: String) {
        val port = if (language == "vi") 8080 else 8082
        Log.d(TAG, "Selected language $language, using Whisper server port $port")
        startActivity(Intent(this@MeetingNotesActivity, MeetingRecordActivity::class.java))
    }

    private fun handleSelectedAudio(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dir = File(getExternalFilesDir(null), "MeetingNotes")
                if (!dir.exists()) dir.mkdirs()

                val timestamp = System.currentTimeMillis()
                val localFile = File(dir, "upload_$timestamp.wav")

                contentResolver.openInputStream(uri)?.use { input ->
                    localFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val newMeeting = MeetingEntity(
                    title = "Cuộc họp (File) " + FormatUtils.formatDate(timestamp),
                    audioFilePath = localFile.absolutePath,
                    durationMs = 0L,
                    language = appSettings.language,
                    asrModel = appSettings.asrModel,
                    llmModel = "Qwen2.5 3B",
                    status = "PROCESSING",
                    rawTranscript = "" // Will be transcribed in MeetingAiPipeline Step 1!
                )

                val meetingId = db.meetingDao().insertMeeting(newMeeting)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MeetingNotesActivity, "Đã tải file âm thanh, chuyển sang xử lý AI...", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@MeetingNotesActivity, MeetingProcessingActivity::class.java)
                    intent.putExtra("MEETING_ID", meetingId)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload audio file", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MeetingNotesActivity, "Lỗi đọc file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
