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

    @Query("SELECT * FROM meetings WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllMeetingsFlow(): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE language = :lang AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getMeetingsByLanguageFlow(lang: String): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE isDeleted = 1 ORDER BY deletedAt DESC, timestamp DESC")
    fun getTrashMeetingsFlow(): Flow<List<MeetingEntity>>

    @Query("UPDATE meetings SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteMeeting(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE meetings SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restoreMeeting(id: Long)

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun deleteMeetingById(id: Long)

    @Query("DELETE FROM meetings WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Transaction
    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getMeetingWithSegments(id: Long): MeetingWithSegments?

    @Transaction
    @Query("SELECT * FROM meetings WHERE id = :id")
    fun getMeetingWithSegmentsFlow(id: Long): Flow<MeetingWithSegments?>

    @Transaction
    @Query("SELECT * FROM meetings WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllMeetingsWithSegmentsFlow(): Flow<List<MeetingWithSegments>>
}