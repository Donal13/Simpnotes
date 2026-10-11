package com.simpnotes.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NoteBackupTest {

    @Test
    fun exportThenImportKeepsEverythingButIds() {
        val notes = listOf(
            Note(id = 7, title = "Shopping", body = "Milk\nBread", lineStyles = ",*", pinned = true, updatedAt = 1_000L),
            Note(id = 9, title = "", body = "Quote \"this\" & that", updatedAt = 2_000L)
        )
        val imported = NoteBackup.fromJson(NoteBackup.toJson(notes))
        assertEquals(notes.map { it.copy(id = 0) }, imported)
    }

    @Test
    fun windowsLineEndingsAreNormalised() {
        val json = """{"format":"simpnotes-export","version":1,"notes":[{"title":"t","body":"a\r\nb","updatedAt":5}]}"""
        assertEquals("a\nb", NoteBackup.fromJson(json).single().body)
    }

    @Test
    fun missingAndNullFieldsGetDefaults() {
        val json = """{"format":"simpnotes-export","version":1,"notes":[{"title":null}, 42]}"""
        val note = NoteBackup.fromJson(json).single()
        assertEquals("", note.title)
        assertEquals("", note.body)
        assertEquals("", note.lineStyles)
    }

    @Test
    fun rejectsFilesThatArentExports() {
        assertThrows(IllegalArgumentException::class.java) { NoteBackup.fromJson("not json") }
        assertThrows(IllegalArgumentException::class.java) { NoteBackup.fromJson("""{"notes":[]}""") }
        assertThrows(IllegalArgumentException::class.java) {
            NoteBackup.fromJson("""{"format":"simpnotes-export","version":99,"notes":[]}""")
        }
    }
}
