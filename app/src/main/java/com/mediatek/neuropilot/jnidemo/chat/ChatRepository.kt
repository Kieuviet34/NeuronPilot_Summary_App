package com.mediatek.neuropilot.jnidemo.chat

import com.mediatek.neuropilot.jnidemo.chat.db.ChatDao
import com.mediatek.neuropilot.jnidemo.chat.db.ChatSession
import com.mediatek.neuropilot.jnidemo.chat.db.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {

    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessionsFlow()

    suspend fun createSession(title: String): Long {
        return chatDao.insertSession(ChatSession(title = title))
    }

    suspend fun updateSessionTitle(session: ChatSession) {
        chatDao.updateSessionTitle(session)
    }

    suspend fun deleteSession(session: ChatSession) {
        chatDao.deleteSession(session)
    }

    suspend fun saveMessage(sessionId: Long, role: String, content: String) {
        chatDao.insertMessage(ChatMessageEntity(sessionId = sessionId, role = role, content = content))
    }

    suspend fun getMessages(sessionId: Long): List<ChatMessageEntity> {
        return chatDao.getMessagesForSession(sessionId)
    }
}
