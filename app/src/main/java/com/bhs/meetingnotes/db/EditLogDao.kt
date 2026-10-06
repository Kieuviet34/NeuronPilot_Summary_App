package com.bhs.meetingnotes.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface EditLogDao {

    @Insert
    suspend fun insertAll(edits: List<EditLogEntity>)

    @Query("DELETE FROM edit_log WHERE meetingId = :meetingId")
    suspend fun clearForMeeting(meetingId: Long)

    @Query("SELECT * FROM edit_log WHERE meetingId = :meetingId ORDER BY pos ASC")
    suspend fun getForMeeting(meetingId: Long): List<EditLogEntity>

    @Query("SELECT COUNT(*) FROM edit_log WHERE meetingId = :meetingId")
    suspend fun countForMeeting(meetingId: Long): Int
}
