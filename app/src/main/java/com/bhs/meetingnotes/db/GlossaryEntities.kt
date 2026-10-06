package com.bhs.meetingnotes.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Thuật ngữ chuẩn (dạng đúng): "BSP", "sprint", "kernel"... Glossary là chuẩn (I7). */
@Entity(
    tableName = "glossary",
    indices = [Index(value = ["term"], unique = true)]
)
data class GlossaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val term: String,
    val kind: String = "term" // "term" | "person" | "project"
)

/** Biến thể ASR nghe sai của một thuật ngữ, vd "sờ prin" -> sprint. */
@Entity(
    tableName = "glossary_alias",
    foreignKeys = [
        ForeignKey(
            entity = GlossaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["glossaryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["glossaryId"]),
        Index(value = ["alias"], unique = true)
    ]
)
data class GlossaryAliasEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val glossaryId: Long,
    val alias: String,
    val mode: String = MODE_ALWAYS // "always" | "suggest" (chỉ thay khi có ngữ cảnh kỹ thuật)
) {
    companion object {
        const val MODE_ALWAYS = "always"
        const val MODE_SUGGEST = "suggest"
    }
}

/**
 * Nhật ký chỉnh sửa tự động (I8). `pos` là vị trí trong văn bản ĐÃ SỬA,
 * nên hoàn tác = thay `afterText` bằng `beforeText` tại `pos` (duyệt giảm dần theo pos).
 */
@Entity(
    tableName = "edit_log",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["meetingId"])]
)
data class EditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val meetingId: Long,
    val pos: Int,
    val beforeText: String,
    val afterText: String,
    val rule: String
)
