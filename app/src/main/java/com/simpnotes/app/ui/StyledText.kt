package com.simpnotes.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.simpnotes.app.data.LineStyle
import com.simpnotes.app.ui.theme.colorFor

/** [text] with each line drawn in its colour and weight from [styles]. */
fun buildStyledText(text: String, styles: List<LineStyle>, darkTheme: Boolean): AnnotatedString =
    buildAnnotatedString {
        append(text)
        var start = 0
        text.split('\n').forEachIndexed { i, line ->
            val style = styles.getOrNull(i) ?: LineStyle.Plain
            val end = start + line.length
            if (style != LineStyle.Plain && end > start) {
                addStyle(
                    SpanStyle(
                        color = style.color.colorFor(darkTheme),
                        fontWeight = if (style.bold) FontWeight.Bold else null
                    ),
                    start,
                    end
                )
            }
            start = end + 1
        }
    }

/** Shows line styles in a text field without changing the text being edited. */
data class LineStyleTransformation(
    private val styles: List<LineStyle>,
    private val darkTheme: Boolean
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(buildStyledText(text.text, styles, darkTheme), OffsetMapping.Identity)
}
