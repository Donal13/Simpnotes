package com.simpnotes.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LineEditsTest {
    private val plain = LineStyle.Plain
    private val red = LineStyle(LineColor.RED)
    private val blue = LineStyle(LineColor.BLUE)
    private val bold = LineStyle(bold = true)

    /** Simulates typing [inserted] over the selection [start, end) and returns the new styles. */
    private fun edit(text: String, styles: List<LineStyle>, start: Int, end: Int = start, inserted: String): List<LineStyle> {
        val newText = text.substring(0, start) + inserted + text.substring(end)
        return LineEdits.afterEdit(text, styles, start, end, newText, start + inserted.length)
    }

    /** Simulates pressing backspace with the cursor at [cursor]. */
    private fun backspace(text: String, styles: List<LineStyle>, cursor: Int): List<LineStyle> {
        val newText = text.removeRange(cursor - 1, cursor)
        return LineEdits.afterEdit(text, styles, cursor, cursor, newText, cursor - 1)
    }

    @Test
    fun typingInsideALineKeepsEveryStyle() {
        assertEquals(listOf(red, blue), edit("abc\ndef", listOf(red, blue), start = 1, inserted = "x"))
    }

    @Test
    fun enterAtEndOfLineContinuesItsStyle() {
        assertEquals(listOf(red, red, blue), edit("abc\ndef", listOf(red, blue), start = 3, inserted = "\n"))
    }

    @Test
    fun enterInMiddleOfLineGivesBothHalvesItsStyle() {
        assertEquals(listOf(red, red, blue), edit("abcdef\nxyz", listOf(red, blue), start = 3, inserted = "\n"))
    }

    @Test
    fun enterAtStartOfLineKeepsThatLinesStyleOnIt() {
        assertEquals(listOf(red, blue, blue), edit("abc\ndef", listOf(red, blue), start = 4, inserted = "\n"))
    }

    @Test
    fun backspaceOnEmptyLineRemovesItWithoutRestylingTheLineAbove() {
        assertEquals(listOf(red, blue), backspace("abc\n\ndef", listOf(red, plain, blue), cursor = 4))
    }

    @Test
    fun joiningTwoLinesKeepsTheUpperLinesStyle() {
        assertEquals(listOf(red), backspace("abc\ndef", listOf(red, blue), cursor = 4))
    }

    @Test
    fun deletingAWholeLineLeavesTheNextLineAlone() {
        // Select "a\n" (the first line and its line break) and delete it.
        assertEquals(listOf(blue), edit("a\na", listOf(red, blue), start = 0, end = 2, inserted = ""))
    }

    @Test
    fun deletingAMiddleLineThatRepeatsItsNeighbourRemovesTheRightOne() {
        // Lines "x", "x", "x": delete the middle one by selecting "x\n" starting at offset 2.
        assertEquals(
            listOf(red, bold),
            edit("x\nx\nx", listOf(red, blue, bold), start = 2, end = 4, inserted = "")
        )
    }

    @Test
    fun pastingSeveralLinesGivesThemTheCurrentLinesStyle() {
        assertEquals(listOf(plain, red, red, red), edit("top\nab", listOf(plain, red), start = 5, inserted = "1\n2\n3"))
    }

    @Test
    fun selectionOnlyChangeKeepsStyles() {
        val styles = listOf(red, blue)
        assertEquals(styles, LineEdits.afterEdit("abc\ndef", styles, 0, 0, "abc\ndef", 5))
    }

    @Test
    fun clearingEverythingLeavesOneLine() {
        assertEquals(listOf(red), edit("abc\ndef", listOf(red, blue), start = 0, end = 7, inserted = ""))
    }

    @Test
    fun selectedLinesIgnoresALineTheSelectionOnlyTouchesAtItsStart() {
        val text = "one\ntwo\nthree"
        assertEquals(0..1, LineEdits.selectedLines(text, 1, 6))
        assertEquals(0..0, LineEdits.selectedLines(text, 0, 4))
        assertEquals(2..2, LineEdits.selectedLines(text, 10, 10))
    }

    @Test
    fun trimDropsBlankEdgeLinesAndTheirStyles() {
        val (text, styles) = LineEdits.trimBlankEdgeLines("\n  \nkeep\n\nme\n", listOf(red, red, blue, plain, bold, red))
        assertEquals("keep\n\nme", text)
        assertEquals(listOf(blue, plain, bold), styles)
    }

    @Test
    fun trimOfBlankTextIsEmpty() {
        assertEquals("" to listOf(plain), LineEdits.trimBlankEdgeLines("\n \n", listOf(red, red, red)))
    }
}
