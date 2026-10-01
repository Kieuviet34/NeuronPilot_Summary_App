package com.mediatek.neuropilot.jnidemo.chat.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert
    suspend fun insertSession(session: ChatSession): Long

    @Update
    suspend fun updateSessionTitle(session: ChatSession)

    @Delete
    suspend fun deleteSession(session: ChatSession)

    @Query("SELECT * FROM chat_sessions ORDER BY createdAt DESC LIMIT 50")
    fun getAllSessionsFlow(): Flow<List<ChatSession>>

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessageEntity>
}
