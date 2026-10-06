package com.bhs.meetingnotes.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MeetingEntity::class, SegmentEntity::class, BookmarkEntity::class,
        GlossaryEntity::class, GlossaryAliasEntity::class, EditLogEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class MeetingDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
    abstract fun segmentDao(): SegmentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun glossaryDao(): GlossaryDao
    abstract fun editLogDao(): EditLogDao

    companion object {
        @Volatile
        private var INSTANCE: MeetingDatabase? = null

        /** v3 -> v4: thêm glossary, glossary_alias, edit_log (giữ nguyên dữ liệu họp). SQL khớp Room sinh ra. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `glossary` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `term` TEXT NOT NULL, `kind` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_glossary_term` ON `glossary` (`term`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `glossary_alias` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `glossaryId` INTEGER NOT NULL, `alias` TEXT NOT NULL, `mode` TEXT NOT NULL, FOREIGN KEY(`glossaryId`) REFERENCES `glossary`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_glossary_alias_glossaryId` ON `glossary_alias` (`glossaryId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_glossary_alias_alias` ON `glossary_alias` (`alias`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `edit_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `meetingId` INTEGER NOT NULL, `pos` INTEGER NOT NULL, `beforeText` TEXT NOT NULL, `afterText` TEXT NOT NULL, `rule` TEXT NOT NULL, FOREIGN KEY(`meetingId`) REFERENCES `meetings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_edit_log_meetingId` ON `edit_log` (`meetingId`)")
            }
        }

        /** v4 -> v5: thêm isDeleted và deletedAt để hỗ trợ Thùng rác (Soft Delete) */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `meetings` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `meetings` ADD COLUMN `deletedAt` INTEGER DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): MeetingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeetingDatabase::class.java,
                    "meeting_notes_db"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}