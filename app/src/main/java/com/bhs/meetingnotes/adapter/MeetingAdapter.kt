package com.bhs.meetingnotes.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bhs.meetingnotes.db.MeetingEntity
import com.bhs.meetingnotes.model.ActionItem
import com.bhs.meetingnotes.util.FormatUtils
import com.mediatek.neuropilot.jnidemo.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter hiển thị danh sách cuộc họp tại Màn hình 1 theo chuẩn BA v2.
 */
class MeetingAdapter(
    private val onItemClick: (MeetingEntity) -> Unit,
    private val onExportClick: (MeetingEntity) -> Unit,
    private val onDeleteClick: (MeetingEntity) -> Unit,
    private val onRestoreClick: (MeetingEntity) -> Unit,
    private val onDeletePermanentClick: (MeetingEntity) -> Unit
) : ListAdapter<MeetingEntity, MeetingAdapter.MeetingViewHolder>(MeetingDiffCallback()) {

    var isTrashMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeetingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_meeting_card, parent, false)
        return MeetingViewHolder(view)
    }

    override fun onBindViewHolder(holder: MeetingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MeetingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        private val tvDatetime: TextView = itemView.findViewById(R.id.tv_datetime)
        private val tvStatusBadge: TextView = itemView.findViewById(R.id.tv_status_badge)
        private val tvLangBadge: TextView = itemView.findViewById(R.id.tv_lang_badge)
        private val tvDuration: TextView = itemView.findViewById(R.id.tv_duration)
        private val tvWordCount: TextView = itemView.findViewById(R.id.tv_word_count)
        private val tvActionCount: TextView = itemView.findViewById(R.id.tv_action_count)
        private val tvSummaryPreview: TextView = itemView.findViewById(R.id.tv_summary_preview)
        private val btnViewDetails: TextView = itemView.findViewById(R.id.btn_view_details)
        private val btnExport: TextView = itemView.findViewById(R.id.btn_export)
        private val btnDelete: TextView = itemView.findViewById(R.id.btn_delete)
        private val btnRestore: TextView = itemView.findViewById(R.id.btn_restore)
        private val btnDeletePermanent: TextView = itemView.findViewById(R.id.btn_delete_permanent)

        fun bind(meeting: MeetingEntity) {
            val context = itemView.context
            tvTitle.text = meeting.title
            tvDatetime.text = dateFormat.format(Date(meeting.timestamp))

            // Duration & word count
            val durationText = if (meeting.durationMs > 0) {
                FormatUtils.formatDuration(meeting.durationMs)
            } else {
                "00:00"
            }
            tvDuration.text = durationText
            tvWordCount.text = "${meeting.wordCount} từ"

            // Actions count
            val actions = ActionItem.fromJson(meeting.actionItemsJson)
            val countActions = if (actions.isNotEmpty()) actions.size else meeting.actionCount
            tvActionCount.text = "$countActions hành động"

            // Language Badge
            val isVi = meeting.language.equals("vi", ignoreCase = true)
            tvLangBadge.text = if (isVi) "VI" else "EN"
            tvLangBadge.setBackgroundResource(R.drawable.bg_badge_active_local)
            tvLangBadge.setTextColor(ContextCompat.getColor(context, R.color.blue_primary))

            // Status Badge & Card Border
            if (meeting.isDeleted) {
                itemView.setBackgroundResource(R.drawable.bg_card_settings_local)
                tvStatusBadge.text = "Trong thùng rác"
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_delete)
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.red_text))
            } else {
                when (meeting.status) {
                    "RECORDING" -> {
                        itemView.setBackgroundResource(R.drawable.bg_card_segment_rec)
                        tvStatusBadge.text = "● REC"
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_rec)
                        tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.red_text))
                    }
                    "PROCESSING" -> {
                        itemView.setBackgroundResource(R.drawable.bg_card_settings_local)
                        tvStatusBadge.text = "Đang xử lý"
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_active_local)
                        tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.blue_primary))
                    }
                    else -> {
                        itemView.setBackgroundResource(R.drawable.bg_card_settings_local)
                        tvStatusBadge.text = "Hoàn tất"
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_saved)
                        tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.green_text))
                    }
                }
            }

            // Summary preview
            val summaryText = meeting.summary.trim()
            if (summaryText.isNotEmpty()) {
                val preview = if (summaryText.length > 120) {
                    summaryText.substring(0, 120) + "..."
                } else {
                    summaryText
                }
                tvSummaryPreview.text = "Tóm tắt: $preview"
                tvSummaryPreview.visibility = View.VISIBLE
            } else {
                tvSummaryPreview.text = "Chưa có nội dung tóm tắt cuộc họp."
                tvSummaryPreview.visibility = View.VISIBLE
            }

            // Button visibility based on mode
            if (isTrashMode) {
                btnViewDetails.visibility = View.VISIBLE
                btnExport.visibility = View.GONE
                btnDelete.visibility = View.GONE
                btnRestore.visibility = View.VISIBLE
                btnDeletePermanent.visibility = View.VISIBLE
            } else {
                btnViewDetails.visibility = View.VISIBLE
                btnExport.visibility = View.VISIBLE
                btnDelete.visibility = View.VISIBLE
                btnRestore.visibility = View.GONE
                btnDeletePermanent.visibility = View.GONE
            }

            // Click listeners
            itemView.setOnClickListener { onItemClick(meeting) }
            btnViewDetails.setOnClickListener { onItemClick(meeting) }
            btnExport.setOnClickListener { onExportClick(meeting) }
            btnDelete.setOnClickListener { onDeleteClick(meeting) }
            btnRestore.setOnClickListener { onRestoreClick(meeting) }
            btnDeletePermanent.setOnClickListener { onDeletePermanentClick(meeting) }
        }
    }

    class MeetingDiffCallback : DiffUtil.ItemCallback<MeetingEntity>() {
        override fun areItemsTheSame(oldItem: MeetingEntity, newItem: MeetingEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MeetingEntity, newItem: MeetingEntity): Boolean {
            return oldItem == newItem
        }
    }
}
