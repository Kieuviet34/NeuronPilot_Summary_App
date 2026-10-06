package com.bhs.meetingnotes.model

import com.bhs.meetingnotes.db.SegmentEntity

/**
 * Presentation model cho một đoạn ghi âm (Segment) trên giao diện theo BA v2.
 */
data class SegmentItem(
    val id: Long = 0,
    val meetingId: Long,
    val segmentIndex: Int,
    val fileName: String,
    val filePath: String,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val pauseCount: Int = 0,
    val bookmarkCount: Int = 0,
    var status: String = "SAVED", // "RECORDING", "SAVED"
    var isSelected: Boolean = true,
    var note: String = ""
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val sizeFormatted: String
        get() {
            val mb = fileSizeBytes.toDouble() / (1024.0 * 1024.0)
            return String.format("%.1f MB", mb)
        }

    val displayIndex: String
        get() = String.format("%02d", segmentIndex)

    fun toEntity(): SegmentEntity {
        return SegmentEntity(
            id = id,
            meetingId = meetingId,
            segmentIndex = segmentIndex,
            fileName = fileName,
            filePath = filePath,
            durationMs = durationMs,
            fileSizeBytes = fileSizeBytes,
            pauseCount = pauseCount,
            status = status,
            isSelected = isSelected,
            note = note
        )
    }

    companion object {
        fun fromEntity(entity: SegmentEntity): SegmentItem {
            return SegmentItem(
                id = entity.id,
                meetingId = entity.meetingId,
                segmentIndex = entity.segmentIndex,
                fileName = entity.fileName,
                filePath = entity.filePath,
                durationMs = entity.durationMs,
                fileSizeBytes = entity.fileSizeBytes,
                pauseCount = entity.pauseCount,
                status = entity.status,
                isSelected = entity.isSelected,
                note = entity.note
            )
        }
    }
}
