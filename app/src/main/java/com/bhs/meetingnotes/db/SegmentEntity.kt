package com.bhs.meetingnotes.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity lưu trữ thông tin từng phân đoạn (segment) âm thanh của cuộc họp theo chuẩn BA v2.
 * Mỗi cuộc họp có thể chứa 1 hoặc nhiều segment âm thanh riêng biệt.
 */
@Entity(
    tableName = "meeting_segments",
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
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val meetingId: Long,
    val segmentIndex: Int, // Thứ tự đoạn: 1, 2, 3...
    val fileName: String, // Ví dụ: Hop_BSP_01.wav
    val filePath: String, // Đường dẫn tuyệt đối file WAV
    val durationMs: Long = 0L, // Thời lượng đoạn (ms)
    val fileSizeBytes: Long = 0L, // Dung lượng file (bytes)
    val pauseCount: Int = 0, // Số lần pause trong lúc ghi
    val status: String = "SAVED", // "RECORDING" (đang ghi), "SAVED" (đã lưu)
    val isSelected: Boolean = true, // Người dùng chọn đưa vào AI Pipeline hay bỏ chọn
    val note: String = "", // Ghi chú (vd: "Phần mở đầu", "Giải lao - Bỏ chọn")
    val createdAt: Long = System.currentTimeMillis()
)
