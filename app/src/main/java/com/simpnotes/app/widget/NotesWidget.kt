package com.simpnotes.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.simpnotes.app.R
import com.simpnotes.app.data.LineStyle
import com.simpnotes.app.data.Note
import com.simpnotes.app.data.NoteRepository
import com.simpnotes.app.data.lines
import com.simpnotes.app.data.styles
import com.simpnotes.app.ui.theme.shades
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Which note each widget shows, kept in that widget's own Glance state. */
private val NoteIdKey = longPreferencesKey("noteId")

/** When the header date was last refreshed; changing it makes the widget redraw the date. */
private val DateRefreshedAtKey = longPreferencesKey("dateRefreshedAt")

// Keeps the widget's update small enough for the launcher; the full note is a tap away.
private const val MAX_LINES_SHOWN = 200

private val TINY = DpSize(40.dp, 40.dp)
private val SHORT = DpSize(110.dp, 40.dp)
// Tall enough for the header plus a couple of lines.
private val FULL = DpSize(110.dp, 64.dp)
// Wide enough for the weekday in the header date.
private val SHORT_WIDE = DpSize(200.dp, 40.dp)
private val FULL_WIDE = DpSize(200.dp, 64.dp)

private sealed interface WidgetNote {
    data object Loading : WidgetNote
    data object Unassigned : WidgetNote
    data object Deleted : WidgetNote
    data class Shown(val note: Note) : WidgetNote
}

/**
 * A home screen widget that shows one note, with each line in its own colour and weight.
 * Tapping anywhere on it adds a line, and each widget can show a different note.
 */
class NotesWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(TINY, SHORT, FULL, SHORT_WIDE, FULL_WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        DateChangeReceiver.scheduleNextDayRefresh(context)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val repository = NoteRepository(context)
        // Load before composing so the widget doesn't flash a placeholder first.
        val initialId = getAppWidgetState<Preferences>(context, id)[NoteIdKey]
        val initialNote = initialId?.let { repository.getNoteById(it) }

        provideContent {
            val noteId = currentState(NoteIdKey)
            // Observing Room keeps the widget current for as long as Glance keeps it running;
            // after that, NoteRepository's widget refresh starts it up again.
            val state by remember(noteId) { observe(repository, noteId) }
                .collectAsState(
                    initial = when {
                        noteId == null -> WidgetNote.Unassigned
                        noteId == initialId -> initialNote?.let(WidgetNote::Shown) ?: WidgetNote.Deleted
                        else -> WidgetNote.Loading
                    }
                )
            val today = remember(currentState(DateRefreshedAtKey)) { LocalDate.now() }
            GlanceTheme {
                WidgetContent(state, appWidgetId, today)
            }
        }
    }

    private fun observe(repository: NoteRepository, noteId: Long?): Flow<WidgetNote> =
        if (noteId == null) flowOf(WidgetNote.Unassigned)
        else repository.observeNote(noteId).map { note ->
            note?.let(WidgetNote::Shown) ?: WidgetNote.Deleted
        }

    companion object {
        /** Points widget [appWidgetId] at [noteId]. Ids that aren't this app's widgets are ignored. */
        suspend fun showNote(context: Context, appWidgetId: Int, noteId: Long) = withContext(NonCancellable) {
            if (!isOurWidget(context, appWidgetId)) return@withContext
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, glanceId) { it[NoteIdKey] = noteId }
            NotesWidget().update(context, glanceId)
        }

        /**
         * Redraws the header date on every widget. It goes through each widget's state because
         * update() alone doesn't redraw a widget that's in the middle of updating.
         */
        suspend fun refreshDate(context: Context, glanceIds: List<GlanceId>) {
            val now = System.currentTimeMillis()
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { it[DateRefreshedAtKey] = now }
                NotesWidget().update(context, glanceId)
            }
        }

        fun isOurWidget(context: Context, appWidgetId: Int): Boolean {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false
            val ours = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, NotesWidgetReceiver::class.java))
            return appWidgetId in ours
        }
    }
}

@Composable
private fun WidgetContent(state: WidgetNote, appWidgetId: Int, today: LocalDate) {
    val context = LocalContext.current
    val pick = actionStartActivity(WidgetIntents.pickNote(context, appWidgetId))
    when (state) {
        WidgetNote.Loading -> Scaffold {}
        WidgetNote.Unassigned -> Message("Tap to choose a note", pick, today)
        WidgetNote.Deleted -> Message("This note was deleted. Tap to choose another.", pick, today)
        is WidgetNote.Shown -> NoteContent(state.note, appWidgetId, today)
    }
}

/** One compact row: note icon, title, and today's date with its week number on the right. */
@Composable
private fun Header(title: String, today: LocalDate, modifier: GlanceModifier = GlanceModifier) {
    val wide = LocalSize.current.width >= SHORT_WIDE.width
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_note),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.size(18.dp)
        )
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight().padding(start = 8.dp)
        )
        // Glance text is all one style, so the bold week number is a Text of its own. Padding
        // stands in for the space after the dot, which a TextView would trim from the line end.
        Text(
            text = "${headerDay(today, wide)} ·",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            maxLines = 1,
            modifier = GlanceModifier.padding(start = 8.dp)
        )
        Text(
            text = weekLabel(today, Locale.getDefault()),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            modifier = GlanceModifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun NoteContent(note: Note, appWidgetId: Int, today: LocalDate) {
    val context = LocalContext.current
    val size = LocalSize.current
    // Tapping anywhere on the widget opens the note with the cursor on a new line at the end.
    val addLine = actionStartActivity(WidgetIntents.addLine(context, appWidgetId, note.id))
    val title = note.title.ifBlank { "Untitled" }

    // 1 column: just the note icon.
    if (size.width < SHORT.width) {
        Scaffold(horizontalPadding = 0.dp, modifier = GlanceModifier.clickable(addLine)) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_note),
                    contentDescription = "Add a line to $title",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                    modifier = GlanceModifier.size(28.dp)
                )
            }
        }
        return
    }

    // 1 row (e.g. 4x1): just the header, centred.
    if (size.height < FULL.height) {
        Scaffold(horizontalPadding = 0.dp, modifier = GlanceModifier.clickable(addLine)) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Header(title, today)
            }
        }
        return
    }

    Scaffold(
        modifier = GlanceModifier.clickable(addLine),
        titleBar = { Header(title, today, GlanceModifier.padding(top = 10.dp, bottom = 4.dp)) }
    ) {
        val lines = note.lines()
        val styles = note.styles()
        if (note.body.isBlank()) {
            Text(
                text = "Empty note. Tap to add a line.",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            return@Scaffold
        }
        // A list swallows taps instead of passing them to the widget, so every line carries the
        // same action. The list is only as tall as its lines, so the space below stays tappable.
        LazyColumn(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp)) {
            itemsIndexed(lines.take(MAX_LINES_SHOWN)) { index, line ->
                Text(
                    // An empty Text collapses; a space keeps blank lines in the note visible.
                    text = line.ifEmpty { " " },
                    style = lineTextStyle(styles[index]),
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(addLine)
                )
            }
            if (lines.size > MAX_LINES_SHOWN) {
                item {
                    Text(
                        text = "${lines.size - MAX_LINES_SHOWN} more lines in the app",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp).clickable(addLine)
                    )
                }
            }
        }
    }
}

@Composable
private fun lineTextStyle(style: LineStyle): TextStyle {
    val shades = style.color.shades
    return TextStyle(
        color = if (shades != null) ColorProvider(day = shades.light, night = shades.dark)
                else GlanceTheme.colors.onSurface,
        fontSize = 14.sp,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
private fun Message(text: String, onClick: Action, today: LocalDate) {
    val size = LocalSize.current
    Scaffold(
        modifier = GlanceModifier.clickable(onClick),
        titleBar = if (size.width >= SHORT.width && size.height >= FULL.height) {
            { Header("SimpNotes", today, GlanceModifier.padding(top = 10.dp, bottom = 4.dp)) }
        } else null
    ) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (size.width < SHORT.width) "Choose a note" else text,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (size.width < SHORT.width) 12.sp else 14.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}
