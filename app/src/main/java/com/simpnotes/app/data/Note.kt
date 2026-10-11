package com.simpnotes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    /** One style per line of [body]; see [LineStyles]. Added in schema version 3. */
    val lineStyles: String = ""
)
