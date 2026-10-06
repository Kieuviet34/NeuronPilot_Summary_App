package com.bhs.meetingnotes.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity lưu trữ các mốc đánh dấu (Bookmark) trong cuộc họp theo Invariant I2.
 * timestampMs: Thời gian thực tế trong file WAV đã trừ thời lượng tạm dừng (pause).
 */
@Entity(
    tableName = "meeting_bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["meetingId"]),
        Index(value = ["meetingId", "segmentIndex"])
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val meetingId: Long,
    val segmentIndex: Int,
    val timestampMs: Long,
    val formattedTime: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
