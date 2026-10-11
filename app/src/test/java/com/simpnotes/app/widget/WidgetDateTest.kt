package com.simpnotes.app.widget

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetDateTest {

    @Test
    fun usWeeksStartOnSunday() {
        // Sunday 11 Oct 2026 starts US week 42, but is still the last day of ISO week 41.
        assertEquals("W42", weekLabel(LocalDate.of(2026, 10, 11), Locale.US))
        assertEquals("W41", weekLabel(LocalDate.of(2026, 10, 11), Locale.GERMANY))
    }

    @Test
    fun weekNumbersFollowTheWeekBasedYearAtNewYear() {
        // Friday 1 Jan 2027 is US week 1, but ISO puts it in week 53 of 2026.
        assertEquals("W1", weekLabel(LocalDate.of(2027, 1, 1), Locale.US))
        assertEquals("W53", weekLabel(LocalDate.of(2027, 1, 1), Locale.GERMANY))
    }
}
