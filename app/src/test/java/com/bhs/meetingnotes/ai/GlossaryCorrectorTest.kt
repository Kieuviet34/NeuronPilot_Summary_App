package com.bhs.meetingnotes.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlossaryCorrectorTest {

    private val terms = listOf("BSP", "I2S", "kernel", "driver", "sprint")
    private val aliases = listOf(
        AliasRule("b ét pê", "BSP"),
        AliasRule("i hai ét", "I2S"),
        AliasRule("đờ rai vơ", "driver"),
        AliasRule("sờ prin", "sprint"),
        AliasRule("bạn sẽ phải", "BSP", suggestOnly = true)
    )

    private fun fix(text: String) = GlossaryCorrector.correct(text, aliases, terms)

    @Test
    fun replacesMultiSyllableAlias() {
        val r = fix("hôm nay họp b ét pê xong")
        assertEquals("hôm nay họp BSP xong", r.text)
        assertEquals(1, r.edits.size)
        assertEquals("b ét pê", r.edits[0].before)
        assertEquals(GlossaryCorrector.RULE_ALWAYS, r.edits[0].rule)
    }

    @Test
    fun keepsPunctuationAroundMatch() {
        val r = fix("xong i hai ét đờ rai vơ, ổn định.")
        assertEquals("xong I2S driver, ổn định.", r.text)
    }

    @Test
    fun matchIgnoresCaseAndDiacritics() {
        assertEquals("trong sprint tới", fix("trong SỜ PRIN tới").text)
        assertEquals("trong sprint tới", fix("trong so prin tới").text)
    }

    @Test
    fun phraseDoesNotSpanPunctuation() {
        val r = fix("họp b ét, pê xong")
        assertEquals("họp b ét, pê xong", r.text)
        assertTrue(r.edits.isEmpty())
    }

    @Test
    fun suggestAliasNeedsTechnicalContext() {
        val r = fix("bạn sẽ phải làm việc này")
        assertEquals("bạn sẽ phải làm việc này", r.text)
        assertTrue(r.edits.isEmpty())
    }

    @Test
    fun suggestAliasAppliesWithTwoSignals() {
        val r = fix("bạn sẽ phải kiểm tra kernel và driver")
        assertEquals("BSP kiểm tra kernel và driver", r.text)
        assertEquals(GlossaryCorrector.RULE_SUGGEST, r.edits.single().rule)
    }

    @Test
    fun revertRestoresOriginal() {
        val original = "bạn sẽ phải họp b ét pê và sờ prin, đờ rai vơ ổn."
        val r = fix(original)
        assertTrue(r.edits.size >= 3)
        assertEquals(original, GlossaryCorrector.revert(r.text, r.edits))
    }

    @Test
    fun emptyRulesLeaveTextUntouched() {
        val r = GlossaryCorrector.correct("xin chào", emptyList(), emptyList())
        assertEquals("xin chào", r.text)
        assertTrue(r.edits.isEmpty())
    }
}
