package com.bhs.meetingnotes.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bhs.meetingnotes.model.SegmentItem
import com.mediatek.neuropilot.jnidemo.R

class RecordSegmentAdapter(
    private var items: List<SegmentItem> = emptyList(),
    var onPlayClickListener: ((SegmentItem) -> Unit)? = null
) : RecyclerView.Adapter<RecordSegmentAdapter.ViewHolder>() {

    private var currentlyPlayingIndex: Int? = null

    fun updateList(newList: List<SegmentItem>) {
        items = newList
        notifyDataSetChanged()
    }

    fun setPlayingSegment(segmentIndex: Int?) {
        currentlyPlayingIndex = segmentIndex
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record_segment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isPlaying = (item.segmentIndex == currentlyPlayingIndex)
        holder.bind(item, isPlaying, onPlayClickListener)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rootLayout: LinearLayout = itemView.findViewById(R.id.ll_segment_root)
        private val tvIndex: TextView = itemView.findViewById(R.id.tv_segment_index)
        private val tvFilename: TextView = itemView.findViewById(R.id.tv_segment_filename)
        private val tvDetails: TextView = itemView.findViewById(R.id.tv_segment_details)
        private val tvBadge: TextView = itemView.findViewById(R.id.tv_segment_badge)
        private val btnPlay: LinearLayout = itemView.findViewById(R.id.btn_play_segment)
        private val ivPlayIcon: ImageView = itemView.findViewById(R.id.iv_play_icon)
        private val tvPlayLabel: TextView = itemView.findViewById(R.id.tv_play_label)

        fun bind(
            item: SegmentItem,
            isPlaying: Boolean,
            onPlayClick: ((SegmentItem) -> Unit)?
        ) {
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
                btnPlay.visibility = View.GONE
            } else {
                rootLayout.setBackgroundResource(R.drawable.bg_card_segment)
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "Đã lưu"
                tvBadge.setBackgroundResource(R.drawable.bg_badge_saved)
                tvBadge.setTextColor(ContextCompat.getColor(context, R.color.green_text))

                btnPlay.visibility = View.VISIBLE
                if (isPlaying) {
                    ivPlayIcon.setImageResource(R.drawable.ic_pause)
                    tvPlayLabel.text = "Dừng"
                    btnPlay.setBackgroundResource(R.drawable.bg_btn_card_delete)
                    ivPlayIcon.setColorFilter(ContextCompat.getColor(context, R.color.red_text))
                    tvPlayLabel.setTextColor(ContextCompat.getColor(context, R.color.red_text))
                } else {
                    ivPlayIcon.setImageResource(R.drawable.ic_play)
                    tvPlayLabel.text = "Nghe lại"
                    btnPlay.setBackgroundResource(R.drawable.bg_btn_card_detail)
                    ivPlayIcon.setColorFilter(ContextCompat.getColor(context, R.color.blue_primary))
                    tvPlayLabel.setTextColor(ContextCompat.getColor(context, R.color.blue_primary))
                }

                btnPlay.setOnClickListener {
                    onPlayClick?.invoke(item)
                }
            }
        }
    }
}
