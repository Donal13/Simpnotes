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
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Which note each widget shows, kept in that widget's own Glance state. */
private val NoteIdKey = longPreferencesKey("noteId")

// Keeps the widget's update small enough for the launcher; the full note is a tap away.
private const val MAX_LINES_SHOWN = 200

private val TINY = DpSize(40.dp, 40.dp)
private val SHORT = DpSize(110.dp, 40.dp)
// Tall enough for the title bar plus a couple of lines; a 2-row widget is about 100dp on phones.
private val FULL = DpSize(110.dp, 90.dp)

private sealed interface WidgetNote {
    data object Loading : WidgetNote
    data object Unassigned : WidgetNote
    data object Deleted : WidgetNote
    data class Shown(val note: Note) : WidgetNote
}

/**
 * A home screen widget that shows one note, with each line in its own colour and weight.
 * + adds a line, tapping a line opens the note there, and each widget can show a different note.
 */
class NotesWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(TINY, SHORT, FULL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
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
            GlanceTheme {
                WidgetContent(state, appWidgetId)
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

        fun isOurWidget(context: Context, appWidgetId: Int): Boolean {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false
            val ours = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, NotesWidgetReceiver::class.java))
            return appWidgetId in ours
        }
    }
}

@Composable
private fun WidgetContent(state: WidgetNote, appWidgetId: Int) {
    val context = LocalContext.current
    val pick = actionStartActivity(WidgetIntents.pickNote(context, appWidgetId))
    when (state) {
        WidgetNote.Loading -> Scaffold {}
        WidgetNote.Unassigned -> Message("Tap to choose a note", pick)
        WidgetNote.Deleted -> Message("This note was deleted. Tap to choose another.", pick)
        is WidgetNote.Shown -> NoteContent(state.note, appWidgetId)
    }
}

@Composable
private fun NoteContent(note: Note, appWidgetId: Int) {
    val context = LocalContext.current
    val size = LocalSize.current
    val open = actionStartActivity(WidgetIntents.openNote(context, appWidgetId, note.id))
    val addLine = actionStartActivity(WidgetIntents.addLine(context, appWidgetId, note.id))

    // 1x1: just the add button.
    if (size.width < SHORT.width) {
        Scaffold(horizontalPadding = 0.dp, modifier = GlanceModifier.clickable(open)) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SquareIconButton(
                    imageProvider = ImageProvider(R.drawable.ic_widget_add),
                    contentDescription = "Add a line to ${note.title.ifBlank { "note" }}",
                    onClick = addLine
                )
            }
        }
        return
    }

    Scaffold(
        titleBar = {
            TitleBar(
                startIcon = ImageProvider(R.drawable.ic_widget_note),
                title = note.title.ifBlank { "Untitled" },
                modifier = GlanceModifier.clickable(open),
                actions = {
                    // Keeps the button clear of the widget's rounded corner.
                    Box(modifier = GlanceModifier.padding(top = 4.dp, end = 8.dp)) {
                        CircleIconButton(
                            imageProvider = ImageProvider(R.drawable.ic_widget_add),
                            contentDescription = "Add a line",
                            onClick = addLine,
                            backgroundColor = GlanceTheme.colors.primaryContainer,
                            contentColor = GlanceTheme.colors.onPrimaryContainer
                        )
                    }
                }
            )
        }
    ) {
        // Too short for the note itself (e.g. 4x1): the title bar is the whole widget.
        if (size.height < FULL.height) return@Scaffold

        val lines = note.lines()
        val styles = note.styles()
        if (note.body.isBlank()) {
            Text(
                text = "Empty note. Tap + to add a line.",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp).clickable(addLine)
            )
            return@Scaffold
        }
        LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(bottom = 8.dp)) {
            itemsIndexed(lines.take(MAX_LINES_SHOWN)) { index, line ->
                Text(
                    // An empty Text collapses; a space keeps blank lines in the note visible.
                    text = line.ifEmpty { " " },
                    style = lineTextStyle(styles[index]),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable(
                            actionStartActivity(WidgetIntents.openNote(context, appWidgetId, note.id, line = index))
                        )
                )
            }
            if (lines.size > MAX_LINES_SHOWN) {
                item {
                    Text(
                        text = "${lines.size - MAX_LINES_SHOWN} more lines. Tap to open the note.",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp).clickable(open)
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
private fun Message(text: String, onClick: Action) {
    val size = LocalSize.current
    Scaffold(
        modifier = GlanceModifier.clickable(onClick),
        titleBar = if (size.width >= SHORT.width && size.height >= FULL.height) {
            { TitleBar(startIcon = ImageProvider(R.drawable.ic_widget_note), title = "SimpNotes") }
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
