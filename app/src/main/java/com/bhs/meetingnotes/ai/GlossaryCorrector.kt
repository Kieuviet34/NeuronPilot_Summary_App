package com.bhs.meetingnotes.ai

import java.text.Normalizer
import java.util.Locale

/** Một luật alias đã nạp: `alias` (ASR nghe sai) -> `term` (dạng chuẩn trong glossary). */
data class AliasRule(val alias: String, val term: String, val suggestOnly: Boolean = false)

/**
 * Một chỉnh sửa tự động (I8). `pos` là vị trí trong văn bản ĐÃ SỬA, nên có thể hoàn tác
 * bằng [GlossaryCorrector.revert].
 */
data class TextEdit(val pos: Int, val before: String, val after: String, val rule: String)

data class CorrectionResult(val text: String, val edits: List<TextEdit>)

/**
 * Sửa lỗi thuật ngữ bằng luật xác định (I7), KHÔNG dùng LLM, KHÔNG sinh lại toàn văn (D6).
 *
 * - So khớp không phân biệt hoa/thường và dấu, theo cửa sổ trượt nhiều âm tiết (ưu tiên cụm dài nhất).
 * - Giữ nguyên dấu câu quanh cụm được thay.
 * - Alias `always` luôn thay. Alias `suggest` (đồng âm với từ thường, vd "bạn sẽ phải" ~ BSP)
 *   chỉ thay khi văn bản có >= [SUGGEST_CONTEXT_MIN] tín hiệu kỹ thuật (thuật ngữ chuẩn hoặc alias `always`).
 *   Ưu tiên precision: thà bỏ sót còn hơn sửa sai.
 */
object GlossaryCorrector {

    const val SUGGEST_CONTEXT_MIN = 2
    const val RULE_ALWAYS = "alias:always"
    const val RULE_SUGGEST = "alias:suggest"

    private class Tok(val start: Int, val end: Int, val coreStart: Int, val coreEnd: Int, val key: String) {
        val hasLeadingPunct get() = coreStart != start
        val hasTrailingPunct get() = coreEnd != end
    }

    private class Match(val start: Int, val end: Int, val term: String?, val suggestOnly: Boolean)

    fun correct(text: String, aliases: List<AliasRule>, knownTerms: Collection<String>): CorrectionResult {
        if (text.isBlank() || (aliases.isEmpty() && knownTerms.isEmpty())) {
            return CorrectionResult(text, emptyList())
        }

        val aliasByKey = buildAliasIndex(aliases)
        val termKeys = knownTerms.map { normalizeKey(it) }.filter { it.isNotEmpty() }.toSet()
        val maxLen = (aliasByKey.keys + termKeys).maxOfOrNull { it.split(' ').size } ?: 1

        val tokens = tokenize(text)
        val matches = scan(tokens, aliasByKey, termKeys, maxLen)

        val contextSignals = matches.count { it.term == null || !it.suggestOnly }
        val applied = matches.filter { it.term != null && (!it.suggestOnly || contextSignals >= SUGGEST_CONTEXT_MIN) }

        return apply(text, applied)
    }

    /** Hoàn tác các chỉnh sửa (I8): thay `after` về `before`, duyệt giảm dần theo pos. */
    fun revert(correctedText: String, edits: List<TextEdit>): String {
        val sb = StringBuilder(correctedText)
        for (edit in edits.sortedByDescending { it.pos }) {
            val end = edit.pos + edit.after.length
            if (end <= sb.length && sb.substring(edit.pos, end) == edit.after) {
                sb.replace(edit.pos, end, edit.before)
            }
        }
        return sb.toString()
    }

    /** Chuẩn hóa để so khớp: chữ thường, bỏ dấu tiếng Việt, gộp khoảng trắng. */
    fun normalizeKey(s: String): String {
        val decomposed = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
            sb.append(if (ch == 'đ') 'd' else ch)
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    private fun buildAliasIndex(aliases: List<AliasRule>): Map<String, AliasRule> {
        val index = LinkedHashMap<String, AliasRule>()
        for (rule in aliases) {
            val key = normalizeKey(rule.alias)
            if (key.isEmpty()) continue
            val existing = index[key]
            // Alias `always` thắng `suggest` nếu trùng khóa.
            if (existing == null || (existing.suggestOnly && !rule.suggestOnly)) index[key] = rule
        }
        return index
    }

    private fun tokenize(text: String): List<Tok> =
        Regex("\\S+").findAll(text).map { m ->
            val s = m.range.first
            val e = m.range.last + 1
            var cs = s
            while (cs < e && !text[cs].isLetterOrDigit()) cs++
            var ce = e
            while (ce > cs && !text[ce - 1].isLetterOrDigit()) ce--
            val key = if (cs < ce) normalizeKey(text.substring(cs, ce)) else ""
            Tok(s, e, cs, ce, key)
        }.toList()

    private fun scan(
        tokens: List<Tok>,
        aliasByKey: Map<String, AliasRule>,
        termKeys: Set<String>,
        maxLen: Int
    ): List<Match> {
        val matches = ArrayList<Match>()
        var i = 0
        while (i < tokens.size) {
            var advanced = 1
            for (n in minOf(maxLen, tokens.size - i) downTo 1) {
                if (!isContiguousPhrase(tokens, i, n)) continue
                val key = (i until i + n).joinToString(" ") { tokens[it].key }
                val rule = aliasByKey[key]
                val start = tokens[i].coreStart
                val end = tokens[i + n - 1].coreEnd
                if (rule != null) {
                    matches.add(Match(start, end, rule.term, rule.suggestOnly))
                    advanced = n
                    break
                }
                if (key in termKeys) {
                    matches.add(Match(start, end, null, false)) // thuật ngữ chuẩn đã đúng: chỉ là tín hiệu ngữ cảnh
                    advanced = n
                    break
                }
            }
            i += advanced
        }
        return matches
    }

    /** Cụm n token liên tiếp chỉ hợp lệ nếu không bị dấu câu/khoảng ngắt ở giữa. */
    private fun isContiguousPhrase(tokens: List<Tok>, from: Int, n: Int): Boolean {
        for (k in from until from + n) {
            if (tokens[k].key.isEmpty()) return false
            if (k > from && tokens[k].hasLeadingPunct) return false
            if (k < from + n - 1 && tokens[k].hasTrailingPunct) return false
        }
        return true
    }

    private fun apply(text: String, applied: List<Match>): CorrectionResult {
        val sb = StringBuilder(text.length)
        val edits = ArrayList<TextEdit>()
        var last = 0
        for (m in applied) {
            val before = text.substring(m.start, m.end)
            val term = m.term ?: continue
            if (before == term) continue
            sb.append(text, last, m.start)
            edits.add(TextEdit(sb.length, before, term, if (m.suggestOnly) RULE_SUGGEST else RULE_ALWAYS))
            sb.append(term)
            last = m.end
        }
        sb.append(text, last, text.length)
        return CorrectionResult(sb.toString(), edits)
    }
}
