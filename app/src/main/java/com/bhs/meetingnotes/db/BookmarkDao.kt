package com.bhs.meetingnotes.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("SELECT * FROM meeting_bookmarks WHERE meetingId = :meetingId ORDER BY timestampMs ASC")
    suspend fun getBookmarksForMeeting(meetingId: Long): List<BookmarkEntity>

    @Query("SELECT * FROM meeting_bookmarks WHERE meetingId = :meetingId ORDER BY timestampMs ASC")
    fun getBookmarksFlow(meetingId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT COUNT(*) FROM meeting_bookmarks WHERE meetingId = :meetingId")
    suspend fun getBookmarkCount(meetingId: Long): Int

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)
}
