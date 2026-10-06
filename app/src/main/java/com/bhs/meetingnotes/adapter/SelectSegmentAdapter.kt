package com.bhs.meetingnotes.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bhs.meetingnotes.model.SegmentItem
import com.mediatek.neuropilot.jnidemo.R

class SelectSegmentAdapter(
    private var items: List<SegmentItem> = emptyList(),
    private val onSelectionChanged: (SegmentItem, Boolean) -> Unit,
    private val onPlayPreview: (SegmentItem) -> Unit
) : RecyclerView.Adapter<SelectSegmentAdapter.ViewHolder>() {

    private var playingSegmentId: Long? = null

    fun updateList(newList: List<SegmentItem>) {
        items = newList
        notifyDataSetChanged()
    }

    fun setPlayingSegment(segmentId: Long?) {
        playingSegmentId = segmentId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_select_segment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, isPlaying = item.id == playingSegmentId)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rootLayout: LinearLayout = itemView.findViewById(R.id.ll_select_segment_root)
        private val cbSelect: CheckBox = itemView.findViewById(R.id.cb_segment_select)
        private val tvIndex: TextView = itemView.findViewById(R.id.tv_select_segment_index)
        private val tvFilename: TextView = itemView.findViewById(R.id.tv_select_segment_filename)
        private val tvDetails: TextView = itemView.findViewById(R.id.tv_select_segment_details)
        private val tvNote: TextView = itemView.findViewById(R.id.tv_select_segment_note)
        private val ivPlayPreview: ImageView = itemView.findViewById(R.id.iv_play_preview)

        fun bind(item: SegmentItem, isPlaying: Boolean) {
            tvIndex.text = item.displayIndex
            tvFilename.text = item.fileName

            val pauseText = if (item.pauseCount > 0) " • Pause ${item.pauseCount} lần" else ""
            tvDetails.text = "${item.durationFormatted} • ${item.sizeFormatted}$pauseText"

            // Set checkbox without triggering old listener
            cbSelect.setOnCheckedChangeListener(null)
            cbSelect.isChecked = item.isSelected

            // Opacity & Note styling
            if (item.isSelected) {
                rootLayout.alpha = 1.0f
                if (item.note.isNotBlank()) {
                    tvNote.visibility = View.VISIBLE
                    tvNote.text = item.note
                } else {
                    tvNote.visibility = View.GONE
                }
            } else {
                rootLayout.alpha = 0.5f
                tvNote.visibility = View.VISIBLE
                tvNote.text = if (item.note.isNotBlank()) item.note else "Bỏ chọn"
            }

            // Checkbox event
            cbSelect.setOnCheckedChangeListener { _, isChecked ->
                item.isSelected = isChecked
                if (isChecked) {
                    rootLayout.alpha = 1.0f
                    tvNote.visibility = if (item.note.isNotBlank()) View.VISIBLE else View.GONE
                } else {
                    rootLayout.alpha = 0.5f
                    tvNote.visibility = View.VISIBLE
                    tvNote.text = if (item.note.isNotBlank()) item.note else "Bỏ chọn"
                }
                onSelectionChanged(item, isChecked)
            }

            // Click entire row to toggle checkbox
            rootLayout.setOnClickListener {
                cbSelect.isChecked = !cbSelect.isChecked
            }

            // Play / Pause Preview
            if (isPlaying) {
                ivPlayPreview.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                ivPlayPreview.setImageResource(R.drawable.ic_play_circle_filled)
            }

            ivPlayPreview.setOnClickListener {
                onPlayPreview(item)
            }
        }
    }
}
