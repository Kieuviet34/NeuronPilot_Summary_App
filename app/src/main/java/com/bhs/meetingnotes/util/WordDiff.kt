package com.bhs.meetingnotes.util

import java.util.Locale

/**
 * So sánh từng từ giữa văn bản gốc (ASR) và văn bản đã sửa (Cleaned) để giao diện tô màu
 * trực quan các chỗ khác nhau. Dùng LCS (Longest Common Subsequence).
 */
object WordDiff {

    private const val MAX_CELLS = 1_000_000

    /** `trailing` là khoảng trắng gốc sau từ (giữ xuống dòng/dấu phân đoạn khi hiển thị). */
    data class Token(
        val text: String,
        val changed: Boolean,
        val trailing: String = " "
    )

    data class Result(
        val raw: List<Token>,
        val clean: List<Token>
    )

    fun diff(raw: String?, clean: String?): Result {
        val aSplit = split(raw)
        val bSplit = split(clean)
        val a = aSplit.map { it.first }
        val b = bSplit.map { it.first }
        val aMatched = BooleanArray(a.size)
        val bMatched = BooleanArray(b.size)

        if (a.size.toLong() * b.size > MAX_CELLS) {
            aMatched.fill(true)
            bMatched.fill(true)
        } else {
            markLcs(a, b, aMatched, bMatched)
        }
        return Result(toTokens(aSplit, aMatched), toTokens(bSplit, bMatched))
    }

    private fun markLcs(
        a: List<String>,
        b: List<String>,
        aMatched: BooleanArray,
        bMatched: BooleanArray
    ) {
        val n = a.size
        val m = b.size
        val na = Array(n) { normalize(a[it]) }
        val nb = Array(m) { normalize(b[it]) }
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (na[i] == nb[j]) {
                    dp[i + 1][j + 1] + 1
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        var i = 0
        var j = 0
        while (i < n && j < m) {
            if (na[i] == nb[j]) {
                aMatched[i] = true
                bMatched[j] = true
                i++
                j++
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++
            } else {
                j++
            }
        }
    }

    private fun toTokens(words: List<Pair<String, String>>, matched: BooleanArray): List<Token> {
        return words.mapIndexed { index, (word, trailing) ->
            Token(word, !matched[index], trailing)
        }
    }

    /** Tách từ, giữ khoảng trắng sau mỗi từ (chứa xuống dòng thì giữ nguyên, ngược lại 1 dấu cách). */
    private fun split(text: String?): List<Pair<String, String>> {
        if (text.isNullOrBlank()) return emptyList()
        val matches = Regex("\\S+").findAll(text).toList()
        return matches.mapIndexed { i, m ->
            val gapEnd = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val gap = text.substring(m.range.last + 1, gapEnd)
            m.value to (if (gap.contains('\n')) gap else " ")
        }
    }

    private fun normalize(word: String): String {
        val lower = word.lowercase(Locale.ROOT)
        var start = 0
        var end = lower.length
        while (start < end && !lower[start].isLetterOrDigit()) {
            start++
        }
        while (end > start && !lower[end - 1].isLetterOrDigit()) {
            end--
        }
        return if (start < end) lower.substring(start, end) else lower
    }
}
