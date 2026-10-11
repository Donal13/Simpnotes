package com.simpnotes.app.data

/** Text colours a line can use. [code] is what gets stored in the database, so never change one. */
enum class LineColor(val code: String) {
    DEFAULT(""),
    RED("r"),
    ORANGE("o"),
    GREEN("g"),
    BLUE("b"),
    PURPLE("p");

    companion object {
        fun fromCode(code: String): LineColor = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

data class LineStyle(val color: LineColor = LineColor.DEFAULT, val bold: Boolean = false) {
    companion object {
        val Plain = LineStyle()
    }
}

/**
 * Per-line styles are stored as one comma-separated token per line: the colour code, plus "*"
 * for bold. Trailing plain lines are dropped, so a note without any styling stores "".
 */
object LineStyles {
    private const val BOLD = '*'

    fun encode(styles: List<LineStyle>): String {
        val last = styles.indexOfLast { it != LineStyle.Plain }
        if (last < 0) return ""
        return styles.subList(0, last + 1).joinToString(",") { style ->
            style.color.code + if (style.bold) BOLD.toString() else ""
        }
    }

    /** Always returns exactly [lineCount] styles; unknown or missing tokens become plain. */
    fun decode(encoded: String, lineCount: Int): List<LineStyle> {
        val tokens = if (encoded.isEmpty()) emptyList() else encoded.split(',', limit = lineCount + 1)
        return List(lineCount) { i ->
            val token = tokens.getOrNull(i).orEmpty()
            LineStyle(
                color = LineColor.fromCode(token.trimEnd(BOLD)),
                bold = token.endsWith(BOLD)
            )
        }
    }
}

fun lineCountOf(text: String): Int = text.count { it == '\n' } + 1

fun Note.lines(): List<String> = body.split('\n')

fun Note.styles(): List<LineStyle> = LineStyles.decode(lineStyles, lineCountOf(body))
