package com.simpnotes.app.ui

import android.appwidget.AppWidgetManager
import androidx.navigation.NavType
import androidx.navigation.navArgument

object Routes {
    const val ARG_NOTE_ID = "noteId"
    const val ARG_WIDGET_ID = "widgetId"
    const val ARG_LINE = "line"
    const val ARG_APPEND = "append"

    private const val NO_WIDGET = AppWidgetManager.INVALID_APPWIDGET_ID

    const val LIST = "list"

    /**
     * [ARG_WIDGET_ID] is set when the editor was opened from a widget; [ARG_LINE] puts the cursor
     * on that line and [ARG_APPEND] starts a new line at the end.
     */
    const val EDIT =
        "edit/{$ARG_NOTE_ID}?$ARG_WIDGET_ID={$ARG_WIDGET_ID}&$ARG_LINE={$ARG_LINE}&$ARG_APPEND={$ARG_APPEND}"

    /** Choose which note a widget shows. */
    const val PICK = "pick/{$ARG_WIDGET_ID}"

    val editArguments = listOf(
        navArgument(ARG_NOTE_ID) { type = NavType.LongType },
        navArgument(ARG_WIDGET_ID) { type = NavType.IntType; defaultValue = NO_WIDGET },
        navArgument(ARG_LINE) { type = NavType.IntType; defaultValue = -1 },
        navArgument(ARG_APPEND) { type = NavType.BoolType; defaultValue = false }
    )

    val pickArguments = listOf(
        navArgument(ARG_WIDGET_ID) { type = NavType.IntType }
    )

    fun edit(noteId: Long, widgetId: Int = NO_WIDGET, line: Int = -1, append: Boolean = false) =
        "edit/$noteId?$ARG_WIDGET_ID=$widgetId&$ARG_LINE=$line&$ARG_APPEND=$append"

    fun pick(widgetId: Int) = "pick/$widgetId"
}
