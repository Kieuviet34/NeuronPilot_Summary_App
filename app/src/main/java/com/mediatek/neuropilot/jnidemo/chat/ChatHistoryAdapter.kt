package com.mediatek.neuropilot.jnidemo.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mediatek.neuropilot.jnidemo.R
import com.mediatek.neuropilot.jnidemo.chat.db.ChatSession
import java.text.SimpleDateFormat
import java.util.*

class ChatHistoryAdapter(
    private val onSessionClick: (ChatSession) -> Unit,
    private val onDeleteClick: (ChatSession) -> Unit
) : ListAdapter<ChatSession, ChatHistoryAdapter.SessionViewHolder>(SessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_session_title)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_session_time)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btn_delete_session)

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        fun bind(session: ChatSession) {
            tvTitle.text = session.title
            tvTime.text = dateFormat.format(Date(session.createdAt))

            itemView.setOnClickListener { onSessionClick(session) }
            btnDelete.setOnClickListener { onDeleteClick(session) }
        }
    }

    class SessionDiffCallback : DiffUtil.ItemCallback<ChatSession>() {
        override fun areItemsTheSame(oldItem: ChatSession, newItem: ChatSession): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatSession, newItem: ChatSession): Boolean {
            return oldItem == newItem
        }
    }
}
