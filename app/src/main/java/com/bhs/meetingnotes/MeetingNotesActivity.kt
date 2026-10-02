package com.bhs.meetingnotes

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

/**
 * Màn hình 1: Danh sách cuộc họp (Screen 1 theo chuẩn BA v2).
 * Hỗ trợ bộ lọc Ngôn ngữ, Bộ lọc Ngày, Tìm kiếm tức thì và Thống kê tổng số.
 */
class MeetingNotesActivity : AppCompatActivity() {

    private lateinit var db: MeetingDatabase
    private lateinit var meetingAdapter: MeetingAdapter

    private var allMeetings: List<MeetingEntity> = emptyList()
    private var selectedLanguageFilter: String = "ALL" // "ALL", "vi", "en"
    private var selectedDateFilter: String = "ALL" // "ALL", "TODAY", "WEEK"
    private var currentSearchQuery: String = ""

    // Views
    private var navAll: LinearLayout? = null
    private var navVi: LinearLayout? = null
    private var navEn: LinearLayout? = null
    private var tvCountAll: TextView? = null
    private var tvCountVi: TextView? = null
    private var tvCountEn: TextView? = null

    private var filterAllTime: TextView? = null
    private var filterToday: TextView? = null
    private var filterThisWeek: TextView? = null

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
        tvCountAll = findViewById(R.id.tv_count_all)
        tvCountVi = findViewById(R.id.tv_count_vi)
        tvCountEn = findViewById(R.id.tv_count_en)

        filterAllTime = findViewById(R.id.filter_all_time)
        filterToday = findViewById(R.id.filter_today)
        filterThisWeek = findViewById(R.id.filter_this_week)

        etSearch = findViewById(R.id.et_search)
        rvMeetings = findViewById(R.id.rv_meetings)
        llEmptyState = findViewById(R.id.ll_empty_state)

        // Record Button
        findViewById<View>(R.id.btn_record)?.setOnClickListener {
            startActivity(Intent(this, MeetingRecordActivity::class.java))
        }

        // Settings Button
        findViewById<View>(R.id.btn_settings)?.setOnClickListener {
            startActivity(Intent(this, MeetingSettingsActivity::class.java))
        }

        // Language sidebar navigation
        navAll?.setOnClickListener { setLanguageFilter("ALL") }
        navVi?.setOnClickListener { setLanguageFilter("vi") }
        navEn?.setOnClickListener { setLanguageFilter("en") }

        // Date filters
        filterAllTime?.setOnClickListener { setDateFilter("ALL") }
        filterToday?.setOnClickListener { setDateFilter("TODAY") }
        filterThisWeek?.setOnClickListener { setDateFilter("WEEK") }

        // Real-time search
        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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
            }
        )

        rvMeetings?.layoutManager = LinearLayoutManager(this)
        rvMeetings?.adapter = meetingAdapter
    }

    private fun observeMeetings() {
        lifecycleScope.launch {
            db.meetingDao().getAllMeetingsFlow().collectLatest { meetings ->
                allMeetings = meetings
                updateCounts(meetings)
                applyFilters()
            }
        }
    }

    private fun updateCounts(meetings: List<MeetingEntity>) {
        val totalCount = meetings.size
        val viCount = meetings.count { it.language.equals("vi", ignoreCase = true) }
        val enCount = meetings.count { it.language.equals("en", ignoreCase = true) }

        tvCountAll?.text = totalCount.toString()
        tvCountVi?.text = viCount.toString()
        tvCountEn?.text = enCount.toString()
    }

    private fun setLanguageFilter(lang: String) {
        selectedLanguageFilter = lang

        // Update UI highlight
        val blue = ContextCompat.getColor(this, R.color.blue_primary)
        val gray = ContextCompat.getColor(this, R.color.text_secondary)

        navAll?.setBackgroundResource(if (lang == "ALL") R.drawable.bg_circle_blue_ring else 0)
        navAll?.findViewById<TextView>(R.id.tv_count_all)?.setBackgroundResource(
            if (lang == "ALL") R.drawable.bg_circle_blue else R.drawable.bg_circle_gray
        )
        navAll?.findViewById<TextView>(R.id.tv_count_all)?.setTextColor(
            if (lang == "ALL") ContextCompat.getColor(this, R.color.bg_card) else gray
        )

        navVi?.setBackgroundResource(if (lang == "vi") R.drawable.bg_circle_blue_ring else 0)
        navVi?.findViewById<TextView>(R.id.tv_count_vi)?.setBackgroundResource(
            if (lang == "vi") R.drawable.bg_circle_blue else R.drawable.bg_circle_gray
        )
        navVi?.findViewById<TextView>(R.id.tv_count_vi)?.setTextColor(
            if (lang == "vi") ContextCompat.getColor(this, R.color.bg_card) else gray
        )

        navEn?.setBackgroundResource(if (lang == "en") R.drawable.bg_circle_blue_ring else 0)
        navEn?.findViewById<TextView>(R.id.tv_count_en)?.setBackgroundResource(
            if (lang == "en") R.drawable.bg_circle_blue else R.drawable.bg_circle_gray
        )
        navEn?.findViewById<TextView>(R.id.tv_count_en)?.setTextColor(
            if (lang == "en") ContextCompat.getColor(this, R.color.bg_card) else gray
        )

        applyFilters()
    }

    private fun setDateFilter(filter: String) {
        selectedDateFilter = filter

        val white = ContextCompat.getColor(this, R.color.bg_card)
        val gray = ContextCompat.getColor(this, R.color.text_secondary)

        filterAllTime?.setBackgroundResource(if (filter == "ALL") R.drawable.bg_circle_blue else R.drawable.bg_circle_gray)
        filterAllTime?.setTextColor(if (filter == "ALL") white else gray)

        filterToday?.setBackgroundResource(if (filter == "TODAY") R.drawable.bg_circle_blue else R.drawable.bg_circle_gray)
        filterToday?.setTextColor(if (filter == "TODAY") white else gray)

        filterThisWeek?.setBackgroundResource(if (filter == "WEEK") R.drawable.bg_circle_blue else R.drawable.bg_circle_gray)
        filterThisWeek?.setTextColor(if (filter == "WEEK") white else gray)

        applyFilters()
    }

    private fun applyFilters() {
        val now = System.currentTimeMillis()
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

        val filtered = allMeetings.filter { meeting ->
            // 1. Language filter
            val matchLang = when (selectedLanguageFilter) {
                "vi" -> meeting.language.equals("vi", ignoreCase = true)
                "en" -> meeting.language.equals("en", ignoreCase = true)
                else -> true
            }

            // 2. Date filter
            val matchDate = when (selectedDateFilter) {
                "TODAY" -> meeting.timestamp >= todayStart
                "WEEK" -> meeting.timestamp >= weekStart
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
        } else {
            rvMeetings?.visibility = View.VISIBLE
            llEmptyState?.visibility = View.GONE
        }
    }

    private fun showExportDialog(meeting: MeetingEntity) {
        val formats = arrayOf("Báo cáo đầy đủ (PDF)", "Tài liệu văn bản (DOCX)", "Chỉ Transcript (TXT)")
        AlertDialog.Builder(this)
            .setTitle("Xuất báo cáo cuộc họp: ${meeting.title}")
            .setItems(formats) { _, which ->
                val format = when (which) {
                    0 -> "PDF"
                    1 -> "DOCX"
                    else -> "TXT"
                }
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
}
