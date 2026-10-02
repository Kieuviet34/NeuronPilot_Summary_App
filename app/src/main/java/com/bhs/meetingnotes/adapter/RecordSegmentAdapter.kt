package com.bhs.meetingnotes.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bhs.meetingnotes.model.SegmentItem
import com.mediatek.neuropilot.jnidemo.R

class RecordSegmentAdapter(
    private var items: List<SegmentItem> = emptyList()
) : RecyclerView.Adapter<RecordSegmentAdapter.ViewHolder>() {

    fun updateList(newList: List<SegmentItem>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record_segment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rootLayout: LinearLayout = itemView.findViewById(R.id.ll_segment_root)
        private val tvIndex: TextView = itemView.findViewById(R.id.tv_segment_index)
        private val tvFilename: TextView = itemView.findViewById(R.id.tv_segment_filename)
        private val tvDetails: TextView = itemView.findViewById(R.id.tv_segment_details)
        private val tvBadge: TextView = itemView.findViewById(R.id.tv_segment_badge)

        fun bind(item: SegmentItem) {
            val context = itemView.context
            tvIndex.text = item.displayIndex
            tvFilename.text = item.fileName

            val pauseText = if (item.pauseCount > 0) " • Pause ${item.pauseCount} lần" else ""
            tvDetails.text = "${item.durationFormatted} • ${item.sizeFormatted}$pauseText"

            if (item.status == "RECORDING") {
                rootLayout.setBackgroundResource(R.drawable.bg_card_segment_rec)
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "REC"
                tvBadge.setBackgroundResource(R.drawable.bg_badge_rec)
                tvBadge.setTextColor(ContextCompat.getColor(context, R.color.red_text))
            } else {
                rootLayout.setBackgroundResource(R.drawable.bg_card_segment)
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "Đã lưu"
                tvBadge.setBackgroundResource(R.drawable.bg_badge_saved)
                tvBadge.setTextColor(ContextCompat.getColor(context, R.color.green_text))
            }
        }
    }
}
