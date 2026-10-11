package com.simpnotes.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.simpnotes.app.MainActivity

/** Intents the widget uses to open the app. [MainActivity] turns them into screens. */
object WidgetIntents {
    const val ACTION_ADD_LINE = "com.simpnotes.app.action.ADD_LINE"
    const val ACTION_PICK_NOTE = "com.simpnotes.app.action.PICK_NOTE"

    const val EXTRA_NOTE_ID = "com.simpnotes.app.extra.NOTE_ID"

    /** Opens the note with the cursor on a new line at the end. */
    fun addLine(context: Context, appWidgetId: Int, noteId: Long): Intent =
        base(context, ACTION_ADD_LINE, appWidgetId).putExtra(EXTRA_NOTE_ID, noteId)

    fun pickNote(context: Context, appWidgetId: Int): Intent =
        base(context, ACTION_PICK_NOTE, appWidgetId)

    private fun base(context: Context, action: String, appWidgetId: Int) =
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
