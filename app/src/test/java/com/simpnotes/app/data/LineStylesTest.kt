package com.simpnotes.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LineStylesTest {

    @Test
    fun plainNoteStoresNothing() {
        assertEquals("", LineStyles.encode(List(5) { LineStyle.Plain }))
    }

    @Test
    fun roundTripsColoursAndBold() {
        val styles = listOf(
            LineStyle.Plain,
            LineStyle(LineColor.RED),
            LineStyle(bold = true),
            LineStyle(LineColor.PURPLE, bold = true),
            LineStyle.Plain
        )
        val encoded = LineStyles.encode(styles)
        assertEquals(",r,*,p*", encoded)
        assertEquals(styles, LineStyles.decode(encoded, styles.size))
    }

    @Test
    fun decodeAlwaysReturnsOneStylePerLine() {
        assertEquals(List(3) { LineStyle.Plain }, LineStyles.decode("", 3))
        assertEquals(listOf(LineStyle(LineColor.GREEN)), LineStyles.decode("g,r,b", 1))
    }

    @Test
    fun unknownTokensBecomePlain() {
        assertEquals(
            listOf(LineStyle.Plain, LineStyle(bold = true)),
            LineStyles.decode("zz,?*", 2)
        )
    }
}
