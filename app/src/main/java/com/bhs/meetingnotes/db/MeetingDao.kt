package com.bhs.meetingnotes.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeeting(meeting: MeetingEntity): Long

    @Update
    suspend fun updateMeeting(meeting: MeetingEntity)

    @Delete
    suspend fun deleteMeeting(meeting: MeetingEntity)

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getMeetingById(id: Long): MeetingEntity?

    @Query("SELECT * FROM meetings WHERE id = :id")
    fun getMeetingByIdFlow(id: Long): Flow<MeetingEntity?>

    @Query("SELECT * FROM meetings ORDER BY timestamp DESC")
    fun getAllMeetingsFlow(): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE language = :lang ORDER BY timestamp DESC")
    fun getMeetingsByLanguageFlow(lang: String): Flow<List<MeetingEntity>>

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun deleteMeetingById(id: Long)
}