package com.mediatek.neuropilot.jnidemo.chat

data class ChatMessage(
    val type: Int,
    var content: String,
    val timestamp: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TYPE_USER = 0
        const val TYPE_AI = 1
        const val TYPE_INFO = 2
    }
}
