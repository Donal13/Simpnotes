package com.simpnotes.app.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The export file format: plain JSON that any text editor can open. It is not encrypted.
 * Note ids aren't exported; imported notes always get fresh ones so nothing is overwritten.
 */
object NoteBackup {
    private const val FORMAT = "simpnotes-export"
    private const val VERSION = 1

    fun toJson(notes: List<Note>, exportedAt: Long = System.currentTimeMillis()): String {
        val array = JSONArray()
        notes.forEach { note ->
            array.put(
                JSONObject()
                    .put("title", note.title)
                    .put("body", note.body)
                    .put("lineStyles", note.lineStyles)
                    .put("pinned", note.pinned)
                    .put("updatedAt", note.updatedAt)
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
            .put("notes", array)
            .toString(2)
    }

    /** @throws IllegalArgumentException with a user-readable message if [json] isn't a SimpNotes export. */
    fun fromJson(json: String): List<Note> {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw IllegalArgumentException("That file isn't a SimpNotes export", e)
        }
        require(root.optString("format") == FORMAT) { "That file isn't a SimpNotes export" }
        require(root.optInt("version", 0) in 1..VERSION) {
            "That export was made by a newer version of SimpNotes"
        }
        val array = root.optJSONArray("notes")
            ?: throw IllegalArgumentException("That export file is damaged")

        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            Note(
                title = item.text("title"),
                body = item.text("body").replace("\r\n", "\n"),
                lineStyles = item.text("lineStyles"),
                pinned = item.optBoolean("pinned", false),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }

    private fun JSONObject.text(name: String): String = if (isNull(name)) "" else optString(name)
}
