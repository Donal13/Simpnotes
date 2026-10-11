package com.simpnotes.app.data

/** Helpers that keep per-line styles lined up with a note's text while it is edited. */
object LineEdits {

    fun lineIndexAt(text: String, offset: Int): Int {
        var line = 0
        for (i in 0 until offset.coerceIn(0, text.length)) {
            if (text[i] == '\n') line++
        }
        return line
    }

    /** Offset just past the last character of [line], i.e. where the cursor goes to "end of line". */
    fun lineEndOffset(text: String, line: Int): Int {
        var current = 0
        for (i in text.indices) {
            if (text[i] == '\n') {
                if (current == line) return i
                current++
            }
        }
        return text.length
    }

    /** Pads with plain styles or drops extras so there is exactly one style per line. */
    fun fit(styles: List<LineStyle>, lineCount: Int): List<LineStyle> = when {
        styles.size == lineCount -> styles
        styles.size > lineCount -> styles.subList(0, lineCount)
        else -> styles + List(lineCount - styles.size) { LineStyle.Plain }
    }

    /**
     * Works out the styles for [newText] after the user edited [oldText].
     *
     * The edited range is found by comparing the texts, anchored on the old selection and the new
     * cursor so that repeated lines don't confuse it. Lines outside the edit keep their style.
     * Lines created inside the edit take the style of the line the edit started on, so pressing
     * Enter continues the current line's style, and joining two lines keeps the upper one's.
     * Deleting whole lines (from a line start through a line break) leaves the next line alone.
     */
    fun afterEdit(
        oldText: String,
        oldStyles: List<LineStyle>,
        oldSelectionMin: Int,
        oldSelectionMax: Int,
        newText: String,
        newSelectionMin: Int
    ): List<LineStyle> {
        val styles = fit(oldStyles, lineCountOf(oldText))
        if (oldText == newText) return styles

        val oldMin = oldSelectionMin.coerceIn(0, oldText.length)
        val oldMax = oldSelectionMax.coerceIn(oldMin, oldText.length)
        val newMin = newSelectionMin.coerceIn(0, newText.length)

        val prefix = minOf(commonPrefixLength(oldText, newText), oldMin, newMin)
        val suffix = minOf(
            commonSuffixLength(oldText, newText),
            minOf(oldText.length, newText.length) - prefix,
            oldText.length - oldMax
        ).coerceAtLeast(0)

        val removed = oldText.substring(prefix, oldText.length - suffix)
        val inserted = newText.substring(prefix, newText.length - suffix)
        val startLine = lineIndexAt(oldText, prefix)
        val removedBreaks = removed.count { it == '\n' }
        val insertedBreaks = inserted.count { it == '\n' }
        val startStyle = styles[startLine]

        val wholeLinesReplaced = removed.endsWith('\n') &&
            (prefix == 0 || oldText[prefix - 1] == '\n') &&
            (inserted.isEmpty() || inserted.endsWith('\n'))

        val result = ArrayList<LineStyle>(styles.size + insertedBreaks)
        result += styles.subList(0, startLine)
        if (wholeLinesReplaced) {
            repeat(insertedBreaks) { result += startStyle }
            result += styles.subList(startLine + removedBreaks, styles.size)
        } else {
            repeat(insertedBreaks + 1) { result += startStyle }
            result += styles.subList(startLine + removedBreaks + 1, styles.size)
        }
        return fit(result, lineCountOf(newText))
    }

    /** The lines a selection touches. A selection that ends right at a line start doesn't include that line. */
    fun selectedLines(text: String, selectionMin: Int, selectionMax: Int): IntRange {
        val min = selectionMin.coerceIn(0, text.length)
        val max = selectionMax.coerceIn(min, text.length)
        val first = lineIndexAt(text, min)
        var last = lineIndexAt(text, max)
        if (max > min && last > first && text[max - 1] == '\n') last--
        return first..last
    }

    fun restyle(
        styles: List<LineStyle>,
        lines: IntRange,
        change: (LineStyle) -> LineStyle
    ): List<LineStyle> = styles.mapIndexed { i, style -> if (i in lines) change(style) else style }

    /** Drops blank lines (and their styles) from the start and end of the text. */
    fun trimBlankEdgeLines(text: String, styles: List<LineStyle>): Pair<String, List<LineStyle>> {
        val lines = text.split('\n')
        val fitted = fit(styles, lines.size)
        val first = lines.indexOfFirst { it.isNotBlank() }
        if (first < 0) return "" to listOf(LineStyle.Plain)
        val last = lines.indexOfLast { it.isNotBlank() }
        return lines.subList(first, last + 1).joinToString("\n") to fitted.subList(first, last + 1)
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val max = minOf(a.length, b.length)
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return i
    }

    private fun commonSuffixLength(a: String, b: String): Int {
        val max = minOf(a.length, b.length)
        var i = 0
        while (i < max && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
        return i
    }
}
