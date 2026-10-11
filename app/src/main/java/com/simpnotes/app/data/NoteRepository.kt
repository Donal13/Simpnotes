package com.simpnotes.app.data

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.simpnotes.app.widget.NotesWidget
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class ImportResult(val added: Int, val skipped: Int)

class NoteRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = NoteDatabase.getDatabase(appContext).noteDao()

    val allNotes: Flow<List<Note>> = dao.getAllNotes()

    fun observeNote(id: Long): Flow<Note?> = dao.observeNote(id)
    suspend fun getNoteById(id: Long) = dao.getNoteById(id)

    suspend fun insert(note: Note): Long = write { dao.insert(note) }
    suspend fun update(note: Note) = write { dao.update(note) }
    suspend fun delete(note: Note) = write { dao.delete(note) }

    suspend fun getAllNotesOnce(): List<Note> = dao.getAllNotesOnce()

    /** Adds imported notes, skipping any that are already here (or repeated in the import). */
    suspend fun importNotes(incoming: List<Note>): ImportResult {
        val seen = dao.getAllNotesOnce().mapTo(HashSet()) { it.contentKey() }
        val fresh = incoming.filter { seen.add(it.contentKey()) }
        if (fresh.isNotEmpty()) write { dao.insertAll(fresh) }
        return ImportResult(added = fresh.size, skipped = incoming.size - fresh.size)
    }

    // Writes always run to completion, even if the screen that started them closes, and every
    // write refreshes the home screen widgets.
    private suspend fun <T> write(block: suspend () -> T): T = withContext(NonCancellable) {
        val result = block()
        try {
            NotesWidget().updateAll(appContext)
        } catch (e: Exception) {
            // The note is already saved; a failed widget refresh shouldn't fail the save.
            Log.w("NoteRepository", "Widget refresh failed", e)
        }
        result
    }

    private fun Note.contentKey() = listOf(title, body, lineStyles, updatedAt.toString())
}
