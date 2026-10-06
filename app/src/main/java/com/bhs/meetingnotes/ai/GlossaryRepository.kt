package com.bhs.meetingnotes.ai

import com.bhs.meetingnotes.db.EditLogEntity
import com.bhs.meetingnotes.db.GlossaryAliasEntity
import com.bhs.meetingnotes.db.GlossaryEntity
import com.bhs.meetingnotes.db.MeetingDatabase

/**
 * Nạp glossary/alias từ Room, chạy [GlossaryCorrector] và ghi `edit_log` (I8).
 * Bộ seed chỉ là MẪU khởi tạo (thuật ngữ kỹ thuật phổ biến + vài alias của bộ mẫu Plan B);
 * theo TECHNICAL_REVIEW §4.2, alias thật phải xây từ log lỗi thật (G6) và người dùng tự thêm.
 */
class GlossaryRepository(private val db: MeetingDatabase) {

    enum class AddResult { ADDED, DUPLICATE, INVALID }

    private val glossaryDao = db.glossaryDao()
    private val editLogDao = db.editLogDao()

    suspend fun ensureSeeded() {
        for ((term, aliases) in SEED) {
            val existing = glossaryDao.findTerm(term)
            val termId = existing?.id ?: glossaryDao.insertTerm(GlossaryEntity(term = term))
            if (termId <= 0) continue
            for ((alias, mode) in aliases) {
                glossaryDao.insertAlias(GlossaryAliasEntity(glossaryId = termId, alias = alias, mode = mode))
            }
        }
    }

    suspend fun addAlias(alias: String, term: String, suggestOnly: Boolean = false): AddResult {
        val cleanAlias = alias.trim().replace(Regex("\\s+"), " ")
        val cleanTerm = term.trim()
        if (cleanAlias.isEmpty() || cleanTerm.isEmpty()) return AddResult.INVALID
        if (GlossaryCorrector.normalizeKey(cleanAlias) == GlossaryCorrector.normalizeKey(cleanTerm)) return AddResult.INVALID

        val termId = glossaryDao.findTerm(cleanTerm)?.id
            ?: glossaryDao.insertTerm(GlossaryEntity(term = cleanTerm)).takeIf { it > 0 }
            ?: return AddResult.INVALID
        val mode = if (suggestOnly) GlossaryAliasEntity.MODE_SUGGEST else GlossaryAliasEntity.MODE_ALWAYS
        val aliasId = glossaryDao.insertAlias(GlossaryAliasEntity(glossaryId = termId, alias = cleanAlias, mode = mode))
        return if (aliasId > 0) AddResult.ADDED else AddResult.DUPLICATE
    }

    /** Sửa `text` bằng luật và lưu log theo `meetingId` (xóa log cũ của họp đó trước). */
    suspend fun correctAndLog(meetingId: Long, text: String): CorrectionResult {
        ensureSeeded()
        val terms = glossaryDao.getAllTerms()
        val termById = terms.associateBy { it.id }
        val rules = glossaryDao.getAllAliases().mapNotNull { a ->
            val term = termById[a.glossaryId]?.term ?: return@mapNotNull null
            AliasRule(a.alias, term, suggestOnly = a.mode == GlossaryAliasEntity.MODE_SUGGEST)
        }

        val result = GlossaryCorrector.correct(text, rules, terms.map { it.term })

        editLogDao.clearForMeeting(meetingId)
        editLogDao.insertAll(result.edits.map {
            EditLogEntity(meetingId = meetingId, pos = it.pos, beforeText = it.before, afterText = it.after, rule = it.rule)
        })
        return result
    }

    suspend fun termCount() = glossaryDao.termCount()
    suspend fun aliasCount() = glossaryDao.aliasCount()

    private companion object {
        const val ALWAYS = GlossaryAliasEntity.MODE_ALWAYS
        const val SUGGEST = GlossaryAliasEntity.MODE_SUGGEST

        val SEED: List<Pair<String, List<Pair<String, String>>>> = listOf(
            // Hardware & Low-level
            "BSP" to listOf("b ét pê" to ALWAYS, "bạn sẽ phải" to SUGGEST),
            "I2S" to listOf("i hai ét" to ALWAYS, "ai tu ét" to ALWAYS),
            "driver" to listOf("đờ rai vơ" to ALWAYS, "đờ-rai-vơ" to ALWAYS),
            "kernel" to listOf("kơ nần" to ALWAYS, "kơ-nơn" to ALWAYS),
            "SoC" to listOf("ét ô xi" to ALWAYS, "sóc" to SUGGEST),
            "Genio" to listOf("ghen nio" to ALWAYS, "jê ni ô" to ALWAYS, "genio" to ALWAYS),
            "NeuroPilot" to listOf("nơ rô pi lốt" to ALWAYS, "nơ rồ pai lợt" to ALWAYS),
            "RMS" to listOf("rờ mờ ét" to ALWAYS),
            "DLA" to listOf("dê lờ a" to ALWAYS, "đi eo ê" to ALWAYS),
            "RAM" to listOf("bộ nhớ ram" to ALWAYS),
            "NPU" to listOf("en pi u" to ALWAYS),
            "ASR" to listOf("a ét rờ" to ALWAYS, "ây ét a" to ALWAYS),
            "LLM" to listOf("en en em" to ALWAYS),
            "firmware" to listOf("phơm que" to ALWAYS, "phơm-we" to ALWAYS),
            "bootloader" to listOf("bút lót đơ" to ALWAYS, "bút-lâu-đơ" to ALWAYS),
            "bring-up" to listOf("brinh úp" to ALWAYS, "brinh-ắp" to ALWAYS),
            "overheat" to listOf("ô va hít" to ALWAYS),
            "latency" to listOf("lây tân xi" to ALWAYS, "lay ten xi" to ALWAYS),
            "buffer" to listOf("bắp pho" to ALWAYS, "bắp phơ" to ALWAYS, "búp phê" to SUGGEST),

            // AI & Speech Models
            "PhoWhisper" to listOf("pho quýt xờ bơ" to ALWAYS, "pho-whisper" to ALWAYS, "phô quýt-xpơ" to ALWAYS),
            "Whisper" to listOf("quýt xờ bơ" to ALWAYS, "uých-s-pơ" to ALWAYS),
            "PW" to listOf("pít đúp bờ liu" to ALWAYS),
            "Qwen2.5" to listOf("quân hai chấm năm" to ALWAYS, "kiu wen" to ALWAYS),
            "llama.cpp" to listOf("lờ la ma" to ALWAYS, "la ma xi pi pi" to ALWAYS),

            // Software Development Lifecycle & Agile
            "bug" to listOf("bắc" to ALWAYS, "bấc" to ALWAYS, "búc" to ALWAYS),
            "fix bug" to listOf("phích bắc" to ALWAYS, "phích bấc" to ALWAYS, "fix bắc" to ALWAYS),
            "deploy" to listOf("đi poi" to ALWAYS, "đì lôi" to ALWAYS, "đíp loi" to ALWAYS),
            "release" to listOf("rì lí" to ALWAYS, "ry li" to ALWAYS, "ri-lít" to ALWAYS),
            "merge" to listOf("mơ gơ" to ALWAYS, "mớt" to ALWAYS),
            "commit" to listOf("com mít" to ALWAYS, "cam mít" to ALWAYS),
            "review" to listOf("ri viu" to ALWAYS, "rì viu" to ALWAYS),
            "task" to listOf("tát" to ALWAYS, "tắc" to ALWAYS),
            "sprint" to listOf("sờ prin" to ALWAYS, "s-prin" to ALWAYS),
            "deadline" to listOf("đét lai" to ALWAYS, "đết lai" to ALWAYS, "đét line" to ALWAYS),
            "meeting" to listOf("mít ting" to ALWAYS, "mít tinh" to ALWAYS),
            "feature" to listOf("phi chờ" to ALWAYS, "fi chờ" to ALWAYS),
            "log" to listOf("lốc" to ALWAYS, "lóc" to ALWAYS),
            "build" to listOf("bin" to ALWAYS, "bưu" to ALWAYS),
            "project" to listOf("pô chếch" to ALWAYS, "prô dếch" to ALWAYS),
            "module" to listOf("mô đun" to ALWAYS),
            "code" to listOf("cốt" to ALWAYS, "gõ cốt" to ALWAYS),
            "hotfix" to listOf("hốt phít" to ALWAYS, "hót phích" to ALWAYS),
            "repo" to listOf("ri pho" to ALWAYS, "re pho" to ALWAYS),
            "GitHub" to listOf("gít hắp" to ALWAYS),
            "reproduce" to listOf("ri pờ rô điu" to ALWAYS, "ri-pờ-ro-điu" to ALWAYS),
            "test" to listOf("tét" to ALWAYS),

            // Architecture & Networking
            "SDK" to listOf("ét ti ca" to ALWAYS, "ét đi cây" to ALWAYS),
            "API" to listOf("ây pi ai" to ALWAYS, "a pi ai" to ALWAYS),
            "backend" to listOf("bác ken" to ALWAYS, "bách en" to ALWAYS),
            "frontend" to listOf("phờ rôn en" to ALWAYS, "phơ ron ten" to ALWAYS),
            "server" to listOf("sơ vơ" to ALWAYS, "sơ vờ" to ALWAYS),
            "cloud" to listOf("klao" to ALWAYS, "cờ lao" to ALWAYS),
            "database" to listOf("đê ta bây" to ALWAYS, "đa ta bây" to ALWAYS),
            "coroutine" to listOf("cô run tin" to ALWAYS),
            "channel" to listOf("chen gồ" to ALWAYS, "trần nồ" to ALWAYS),
            "framework" to listOf("phờ rêm quấc" to ALWAYS),
            "batch size" to listOf("bát sai" to ALWAYS, "bách sai" to ALWAYS),
            "optimize" to listOf("ốp ti mai" to ALWAYS, "ốp ti mài" to ALWAYS),
            "custom" to listOf("cút tầm" to ALWAYS),
            "block" to listOf("bờ lốc" to ALWAYS, "bơ lốc" to ALWAYS),
            "download" to listOf("đao loát" to ALWAYS, "đao lốt" to ALWAYS),
            "upload" to listOf("ắp loát" to ALWAYS, "ắp lốt" to ALWAYS),
            "convert" to listOf("phai cơn vớt" to ALWAYS, "con vớt" to ALWAYS),
            "tool" to listOf("tút" to ALWAYS, "tun" to ALWAYS)
        )
    }
}
