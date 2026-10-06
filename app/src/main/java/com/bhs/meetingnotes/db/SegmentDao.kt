package com.bhs.meetingnotes.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: SegmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SegmentEntity>): List<Long>

    @Update
    suspend fun updateSegment(segment: SegmentEntity)

    @Delete
    suspend fun deleteSegment(segment: SegmentEntity)

    @Query("SELECT * FROM meeting_segments WHERE id = :id")
    suspend fun getSegmentById(id: Long): SegmentEntity?

    @Query("SELECT * FROM meeting_segments WHERE meetingId = :meetingId ORDER BY segmentIndex ASC")
    fun getSegmentsForMeetingFlow(meetingId: Long): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM meeting_segments WHERE meetingId = :meetingId ORDER BY segmentIndex ASC")
    suspend fun getSegmentsForMeeting(meetingId: Long): List<SegmentEntity>

    @Query("SELECT * FROM meeting_segments WHERE meetingId = :meetingId AND isSelected = 1 ORDER BY segmentIndex ASC")
    suspend fun getSelectedSegmentsForMeeting(meetingId: Long): List<SegmentEntity>

    @Query("SELECT * FROM meeting_segments WHERE meetingId = :meetingId AND isSelected = 1 ORDER BY segmentIndex ASC")
    fun getSelectedSegmentsForMeetingFlow(meetingId: Long): Flow<List<SegmentEntity>>

    @Query("UPDATE meeting_segments SET isSelected = :isSelected WHERE id = :segmentId")
    suspend fun updateSegmentSelection(segmentId: Long, isSelected: Boolean)

    @Query("UPDATE meeting_segments SET isSelected = :isSelected WHERE meetingId = :meetingId")
    suspend fun updateAllSegmentsSelection(meetingId: Long, isSelected: Boolean)

    @Query("UPDATE meeting_segments SET note = :note WHERE id = :segmentId")
    suspend fun updateSegmentNote(segmentId: Long, note: String)

    @Query("DELETE FROM meeting_segments WHERE meetingId = :meetingId")
    suspend fun deleteSegmentsByMeetingId(meetingId: Long)
}
