package com.simpnotes.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simpnotes.app.data.Note
import com.simpnotes.app.data.NoteBackup
import com.simpnotes.app.data.NoteRepository
import com.simpnotes.app.widget.NotesWidget
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Far larger than any real export; stops a wrong file from being read into memory whole.
private const val MAX_IMPORT_BYTES = 20 * 1024 * 1024

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NoteRepository(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val allNotes: StateFlow<List<Note>> = repository.allNotes.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val displayedNotes: StateFlow<List<Note>> = combine(_searchQuery, allNotes) { query, notes ->
        if (query.isBlank()) notes
        else notes.filter { note ->
            note.title.contains(query, ignoreCase = true) ||
            note.body.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off messages for the snackbar, such as the result of an export. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun togglePin(note: Note) = viewModelScope.launch {
        repository.update(note.copy(pinned = !note.pinned))
    }

    fun delete(note: Note) = viewModelScope.launch {
        repository.delete(note)
    }

    /** Puts a deleted note back, with its original id so any widget showing it picks it up again. */
    fun restore(note: Note) = viewModelScope.launch {
        repository.insert(note)
    }

    suspend fun showOnWidget(appWidgetId: Int, noteId: Long) {
        NotesWidget.showNote(getApplication(), appWidgetId, noteId)
    }

    /** Writes every note to [uri], a file the user picked. */
    fun exportTo(uri: Uri) = viewModelScope.launch {
        val message = try {
            val notes = repository.getAllNotesOnce()
            val json = withContext(Dispatchers.Default) { NoteBackup.toJson(notes) }
            withContext(Dispatchers.IO) {
                val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("Couldn't open the file")
                output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            "Exported ${noteCount(notes.size)}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Export failed. Try another location."
        }
        _messages.send(message)
    }

    /** Adds the notes from an export file at [uri]. Notes already in the app aren't duplicated. */
    fun importFrom(uri: Uri) = viewModelScope.launch {
        val message = try {
            val json = withContext(Dispatchers.IO) { readText(uri) }
            val notes = withContext(Dispatchers.Default) { NoteBackup.fromJson(json) }
            val result = repository.importNotes(notes)
            when {
                result.added == 0 && result.skipped == 0 -> "That file has no notes in it"
                result.added == 0 -> "Nothing new: all ${noteCount(result.skipped)} are already here"
                result.skipped == 0 -> "Imported ${noteCount(result.added)}"
                else -> "Imported ${noteCount(result.added)} (${result.skipped} already here)"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            e.message ?: "Import failed"
        } catch (e: Exception) {
            "Import failed. Couldn't read that file."
        }
        _messages.send(message)
    }

    private fun readText(uri: Uri): String {
        val input = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: throw IOException("Couldn't open the file")
        return input.use { stream ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
                require(buffer.size() <= MAX_IMPORT_BYTES) { "That file is too big to be a SimpNotes export" }
            }
            buffer.toString(Charsets.UTF_8.name())
        }
    }

    private fun noteCount(count: Int) = if (count == 1) "1 note" else "$count notes"
}
