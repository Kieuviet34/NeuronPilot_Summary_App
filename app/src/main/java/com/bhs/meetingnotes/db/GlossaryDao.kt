package com.bhs.meetingnotes.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GlossaryDao {

    /** Trả về -1 nếu term đã tồn tại. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTerm(term: GlossaryEntity): Long

    /** Trả về -1 nếu alias đã tồn tại. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlias(alias: GlossaryAliasEntity): Long

    @Query("SELECT * FROM glossary WHERE term = :term LIMIT 1")
    suspend fun findTerm(term: String): GlossaryEntity?

    @Query("SELECT * FROM glossary")
    suspend fun getAllTerms(): List<GlossaryEntity>

    @Query("SELECT * FROM glossary_alias")
    suspend fun getAllAliases(): List<GlossaryAliasEntity>

    @Query("SELECT COUNT(*) FROM glossary")
    suspend fun termCount(): Int

    @Query("SELECT COUNT(*) FROM glossary_alias")
    suspend fun aliasCount(): Int
}
