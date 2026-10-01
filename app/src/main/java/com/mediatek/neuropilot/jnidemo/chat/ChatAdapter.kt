package com.mediatek.neuropilot.jnidemo.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mediatek.neuropilot.jnidemo.R

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(content: String) {
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            val lastMessage = messages[lastIndex]
            lastMessage.content = content
            notifyItemChanged(lastIndex, PAYLOAD_CONTENT_ONLY)
        }
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < messages.size) {
            messages[position].type
        } else {
            ChatMessage.TYPE_INFO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ChatMessage.TYPE_USER -> {
                val view = inflater.inflate(R.layout.item_message_user, parent, false)
                UserMessageViewHolder(view)
            }
            ChatMessage.TYPE_AI -> {
                val view = inflater.inflate(R.layout.item_message_ai, parent, false)
                AiMessageViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_message_ai, parent, false)
                AiMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AiMessageViewHolder -> holder.bind(message)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }

        val message = messages[position]
        for (payload in payloads) {
            if (PAYLOAD_CONTENT_ONLY == payload) {
                when (holder) {
                    is AiMessageViewHolder -> holder.updateContent(message.content)
                    is UserMessageViewHolder -> holder.updateContent(message.content)
                }
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    private class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessageUser: TextView = itemView.findViewById(R.id.tv_message_user)

        fun bind(message: ChatMessage) {
            tvMessageUser.text = message.content
        }

        fun updateContent(content: String) {
            tvMessageUser.text = content
        }
    }

    private class AiMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_message)

        fun bind(message: ChatMessage) {
            tvMessage.text = message.content
        }

        fun updateContent(content: String) {
            tvMessage.text = content
        }
    }

    fun getAllMessages(): List<ChatMessage> {
        return messages
    }

    companion object {
        const val PAYLOAD_CONTENT_ONLY = "content_only"
    }
}
