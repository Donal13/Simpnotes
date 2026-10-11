package com.simpnotes.app.ui

import android.app.Application
import android.appwidget.AppWidgetManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.simpnotes.app.data.LineColor
import com.simpnotes.app.data.LineEdits
import com.simpnotes.app.data.LineStyle
import com.simpnotes.app.data.LineStyles
import com.simpnotes.app.data.Note
import com.simpnotes.app.data.NoteRepository
import com.simpnotes.app.data.styles
import com.simpnotes.app.widget.NotesWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Outlives any one screen, so a last-chance save in onCleared() still runs.
private val lastChanceSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

enum class InitialFocus { TITLE, BODY }

/**
 * Holds one note while it's being edited. Lives as long as the editor's back stack entry, so a
 * rotation keeps everything typed, and [save] is safe to call from every way out of the screen.
 */
class NoteEditViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {
    private val repository = NoteRepository(application)

    /** The widget that opened this editor, or [AppWidgetManager.INVALID_APPWIDGET_ID]. */
    val widgetId: Int = savedState[Routes.ARG_WIDGET_ID] ?: AppWidgetManager.INVALID_APPWIDGET_ID
    val openedFromWidget: Boolean get() = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID

    var title by mutableStateOf(TextFieldValue())
        private set
    var body by mutableStateOf(TextFieldValue())
        private set
    var styles by mutableStateOf(listOf(LineStyle.Plain))
        private set
    var pinned by mutableStateOf(false)
        private set
    var isLoaded by mutableStateOf(false)
        private set

    /** 0 until a new note is first saved. */
    var noteId by mutableLongStateOf(0L)
        private set
    val isNewNote: Boolean get() = noteId == 0L

    private var initialFocus: InitialFocus? = null
    private var saved: Note? = null
    private var deleted = false
    private var shownOnWidget = false
    private val saveMutex = Mutex()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val requestedId = savedState[Routes.ARG_NOTE_ID] ?: 0L
        // A note that no longer exists (say, deleted while a widget still showed it) opens as a new note.
        val note = if (requestedId > 0) repository.getNoteById(requestedId) else null
        noteId = note?.id ?: 0L
        saved = note

        var text = note?.body.orEmpty()
        var lineStyles = note?.styles() ?: listOf(LineStyle.Plain)
        var selection = TextRange.Zero
        val line = savedState[Routes.ARG_LINE] ?: -1
        when {
            savedState[Routes.ARG_APPEND] ?: false -> {
                if (text.isNotEmpty()) {
                    text += "\n"
                    lineStyles = lineStyles + LineStyle.Plain
                }
                selection = TextRange(text.length)
                initialFocus = InitialFocus.BODY
            }
            line >= 0 -> {
                selection = TextRange(LineEdits.lineEndOffset(text, line))
                initialFocus = InitialFocus.BODY
            }
            note == null -> initialFocus = InitialFocus.TITLE
        }
        // Only act on the widget's request once, not again if the app is restored later.
        savedState[Routes.ARG_LINE] = -1
        savedState[Routes.ARG_APPEND] = false

        title = TextFieldValue(note?.title.orEmpty())
        body = TextFieldValue(text, selection)
        styles = lineStyles
        pinned = note?.pinned ?: false
        isLoaded = true
    }

    /** Where to put the cursor when the screen first opens; returns null after the first call. */
    fun takeInitialFocus(): InitialFocus? = initialFocus.also { initialFocus = null }

    fun onTitleChange(value: TextFieldValue) {
        title = value
    }

    fun onBodyChange(value: TextFieldValue) {
        val old = body
        styles = LineEdits.afterEdit(
            oldText = old.text,
            oldStyles = styles,
            oldSelectionMin = old.selection.min,
            oldSelectionMax = old.selection.max,
            newText = value.text,
            newSelectionMin = value.selection.min
        )
        body = value
    }

    fun togglePinned() {
        pinned = !pinned
    }

    /** The style of the line the cursor is on. */
    val currentStyle: LineStyle
        get() = styles.getOrElse(LineEdits.lineIndexAt(body.text, body.selection.min)) { LineStyle.Plain }

    /** Bolds every selected line, or un-bolds them if they're all bold already. */
    fun toggleBold() {
        val lines = selectedLines()
        val makeBold = lines.any { !styles[it].bold }
        styles = LineEdits.restyle(styles, lines) { it.copy(bold = makeBold) }
    }

    fun setColor(color: LineColor) {
        styles = LineEdits.restyle(styles, selectedLines()) { it.copy(color = color) }
    }

    private fun selectedLines() =
        LineEdits.selectedLines(body.text, body.selection.min, body.selection.max)

    /**
     * Writes the note if anything changed and returns its id (0 if there's nothing to keep).
     * Runs to completion even if the screen goes away mid-save.
     */
    suspend fun save(): Long = withContext(NonCancellable) {
        saveMutex.withLock {
            if (!isLoaded || deleted) return@withLock noteId
            val (text, lineStyles) = LineEdits.trimBlankEdgeLines(body.text, styles)
            val draft = Note(
                id = noteId,
                title = title.text.trim(),
                body = text,
                pinned = pinned,
                lineStyles = LineStyles.encode(lineStyles)
            )
            val previous = saved
            when {
                // A new note with nothing in it is simply dropped.
                previous == null && draft.title.isEmpty() && draft.body.isEmpty() -> Unit
                previous == null -> {
                    val newId = repository.insert(draft.copy(id = 0, updatedAt = System.currentTimeMillis()))
                    noteId = newId
                    saved = draft.copy(id = newId)
                    // If the app is restored after being killed, reopen this note rather than a blank one.
                    savedState[Routes.ARG_NOTE_ID] = newId
                }
                draft.hasSameContentAs(previous) && draft.pinned == previous.pinned -> Unit
                // Pinning alone doesn't count as editing, so it keeps the note's place in the list.
                draft.hasSameContentAs(previous) -> {
                    val updated = previous.copy(pinned = draft.pinned)
                    repository.update(updated)
                    saved = updated
                }
                else -> {
                    val updated = draft.copy(updatedAt = System.currentTimeMillis())
                    repository.update(updated)
                    saved = updated
                }
            }
            if (openedFromWidget && noteId != 0L && !shownOnWidget) {
                NotesWidget.showNote(getApplication(), widgetId, noteId)
                shownOnWidget = true
            }
            noteId
        }
    }

    fun saveInBackground() {
        viewModelScope.launch { save() }
    }

    suspend fun delete() = withContext(NonCancellable) {
        saveMutex.withLock {
            saved?.let { repository.delete(it) }
            deleted = true
        }
    }

    override fun onCleared() {
        // Normally already saved by now; this catches any way out of the editor that skipped it.
        lastChanceSaveScope.launch { save() }
    }

    private fun Note.hasSameContentAs(other: Note) =
        title == other.title && body == other.body && lineStyles == other.lineStyles
}
