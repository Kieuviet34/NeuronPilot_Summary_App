package com.bhs.meetingnotes.db

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Model quan hệ 1-N giữa MeetingEntity và danh sách SegmentEntity.
 */
data class MeetingWithSegments(
    @Embedded
    val meeting: MeetingEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "meetingId"
    )
    val segments: List<SegmentEntity> = emptyList()
)
