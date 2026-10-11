package com.simpnotes.app

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.simpnotes.app.ui.NoteEditScreen
import com.simpnotes.app.ui.NoteListScreen
import com.simpnotes.app.ui.NotePickerScreen
import com.simpnotes.app.ui.NoteViewModel
import com.simpnotes.app.ui.Routes
import com.simpnotes.app.ui.theme.SimpNotesTheme
import com.simpnotes.app.widget.NotesWidget
import com.simpnotes.app.widget.WidgetIntents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** A screen a widget asked for, waiting to be opened on top of the note list. */
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // On recreation (rotation, or Android restoring the app) navigation restores itself.
        if (savedInstanceState == null) {
            pendingRoute.value = routeFor(intent)
        }
        setContent {
            SimpNotesTheme {
                val navController = rememberNavController()
                val viewModel: NoteViewModel = viewModel()
                val scope = rememberCoroutineScope()

                val route by pendingRoute.collectAsState()
                LaunchedEffect(route) {
                    route?.let {
                        // The list always stays underneath, so going back never leaves a blank screen.
                        navController.navigate(it) { popUpTo(Routes.LIST) }
                        pendingRoute.value = null
                    }
                }

                // The list is always the start screen; widget requests are opened on top of it.
                NavHost(navController = navController, startDestination = Routes.LIST) {
                    composable(Routes.LIST) {
                        NoteListScreen(
                            viewModel = viewModel,
                            onNoteClick = { note -> navController.navigate(Routes.edit(note.id)) },
                            onAddNote = { navController.navigate(Routes.edit(0)) }
                        )
                    }
                    composable(Routes.EDIT, arguments = Routes.editArguments) { entry ->
                        val widgetId = entry.arguments?.getInt(Routes.ARG_WIDGET_ID)
                            ?: AppWidgetManager.INVALID_APPWIDGET_ID
                        NoteEditScreen(
                            // Opened from a widget: done means back to the home screen.
                            onDone = {
                                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) finish()
                                else navController.popBackStack()
                            },
                            onChangeWidgetNote = {
                                navController.navigate(Routes.pick(widgetId)) { popUpTo(Routes.LIST) }
                            }
                        )
                    }
                    composable(Routes.PICK, arguments = Routes.pickArguments) { entry ->
                        val widgetId = entry.arguments?.getInt(Routes.ARG_WIDGET_ID)
                            ?: AppWidgetManager.INVALID_APPWIDGET_ID
                        NotePickerScreen(
                            viewModel = viewModel,
                            onPick = { note ->
                                scope.launch {
                                    viewModel.showOnWidget(widgetId, note.id)
                                    finish()
                                }
                            },
                            onNewNote = { navController.navigate(Routes.edit(0, widgetId = widgetId)) },
                            onCancel = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFor(intent)?.let { pendingRoute.value = it }
    }

    /**
     * Turns a widget tap into a screen. This activity is reachable by other apps too, so anything
     * that isn't a well-formed request from one of our own widgets just shows the note list.
     */
    private fun routeFor(intent: Intent): String? {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (!NotesWidget.isOurWidget(this, widgetId)) return null
        val noteId = intent.getLongExtra(WidgetIntents.EXTRA_NOTE_ID, 0L)
        return when (intent.action) {
            WidgetIntents.ACTION_OPEN_NOTE -> if (noteId > 0) {
                Routes.edit(noteId, widgetId, line = intent.getIntExtra(WidgetIntents.EXTRA_LINE, -1))
            } else null
            WidgetIntents.ACTION_ADD_LINE -> if (noteId > 0) {
                Routes.edit(noteId, widgetId, append = true)
            } else null
            WidgetIntents.ACTION_PICK_NOTE -> Routes.pick(widgetId)
            else -> null
        }
    }
}
