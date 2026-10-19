package ai.droidrun.officetv

import ai.droidrun.officetv.ui.StatLabels
import ai.droidrun.officetv.ui.common.accentText
import org.junit.Assert.*
import org.junit.Test

class TypographyTest {
    @Test fun statAccentsKeepLabelsAndCoverOnlyTheAccentWord() {
        listOf(
            Triple(StatLabels.USERS, 14, 19),
            Triple(StatLabels.RUNNER_STEPS, 12, 17),
            Triple(StatLabels.TASKS, 0, 5),
            Triple(StatLabels.MACHINES, 6, 15),
            Triple(StatLabels.STARS, 7, 12),
            Triple(StatLabels.CALENDAR, 9, 15),
        ).forEach { (label, start, end) ->
            assertAccent(label, StatLabels.accent(label), start, end)
        }
    }

    @Test fun missingAccentLeavesTextUnchangedWithoutSpans() {
        val text = "Nutzer in der Cloud"
        listOf(null, "", "fehlt").forEach { word ->
            val result = accentText(text, word)
            assertEquals(text, result.text)
            assertTrue(result.spanStyles.isEmpty())
        }
        assertNull(StatLabels.accent(StatLabels.CLOCK))
        assertNull(StatLabels.accent("unbekannt"))
    }

    private fun assertAccent(text: String, word: String?, start: Int, end: Int) {
        val result = accentText(text, word)
        assertEquals(text, result.text)
        val span = result.spanStyles.single()
        assertEquals(start, span.start)
        assertEquals(end, span.end)
        assertEquals(text.substring(start, end), word)
    }
}
