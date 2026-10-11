package com.simpnotes.app.widget

import android.text.format.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * "W42": the week number, following the phone's region. In the US weeks start on Sunday and
 * week 1 holds 1 January; most of Europe uses ISO 8601 (Monday start, week 1 holds a Thursday).
 */
internal fun weekLabel(date: LocalDate, locale: Locale): String =
    "W${date.get(WeekFields.of(locale).weekOfWeekBasedYear())}"

/** The widget header's date in the phone's own format, e.g. "Sun, Oct 11", or "Oct 11" when narrow. */
internal fun headerDay(date: LocalDate, wide: Boolean, locale: Locale = Locale.getDefault()): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, if (wide) "EEEMMMd" else "MMMd")
    return runCatching { date.format(DateTimeFormatter.ofPattern(pattern, locale)) }
        .getOrElse { date.toString() }
}
