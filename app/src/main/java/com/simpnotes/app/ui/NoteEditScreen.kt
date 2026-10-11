package com.simpnotes.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simpnotes.app.data.LineColor
import com.simpnotes.app.data.LineStyle
import com.simpnotes.app.ui.theme.colorFor
import com.simpnotes.app.ui.theme.label
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    onDone: () -> Unit,
    onChangeWidgetNote: () -> Unit,
    viewModel: NoteEditViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    var bodyFocused by remember { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Every way out of the screen saves first, and only one exit can run.
    fun leave(beforeLeaving: suspend () -> Unit = { viewModel.save() }, then: () -> Unit = onDone) {
        if (leaving) return
        leaving = true
        focusManager.clearFocus()
        scope.launch {
            beforeLeaving()
            then()
        }
    }

    BackHandler { leave() }

    // Leaving the app (home button, switching apps, tapping the widget) saves too, so nothing is
    // lost if Android closes the app in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveInBackground() }

    LaunchedEffect(viewModel.isLoaded) {
        if (!viewModel.isLoaded) return@LaunchedEffect
        when (viewModel.takeInitialFocus()) {
            InitialFocus.TITLE -> titleFocus.requestFocus()
            InitialFocus.BODY -> {
                bodyFocus.requestFocus()
                keyboard?.show()
            }
            null -> Unit
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete note?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    leave(beforeLeaving = { viewModel.delete() })
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Save and go back"
                        )
                    }
                },
                title = {
                    Text(
                        if (viewModel.isNewNote) "New Note" else "Edit Note",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.togglePinned() }) {
                        Icon(
                            imageVector = if (viewModel.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (viewModel.pinned) "Unpin note" else "Pin note",
                            tint = if (viewModel.pinned) MaterialTheme.colorScheme.primary
                                   else LocalContentColor.current
                        )
                    }
                    if (!viewModel.isNewNote) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete note",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.Default.Check, contentDescription = "Save note")
                    }
                    if (viewModel.openedFromWidget) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Show a different note on this widget") },
                                    onClick = {
                                        showMenu = false
                                        leave(then = onChangeWidgetNote)
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            if (!viewModel.isLoaded) return@Column
            TextField(
                value = viewModel.title,
                onValueChange = viewModel::onTitleChange,
                placeholder = { Text("Title") },
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    .focusRequester(titleFocus)
                    // A physical keyboard's Enter moves on to the note, like the on-screen Next key.
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                            bodyFocus.requestFocus()
                            true
                        } else false
                    },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { bodyFocus.requestFocus() })
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = viewModel.body,
                onValueChange = viewModel::onBodyChange,
                placeholder = { Text("Write your note here...") },
                visualTransformation = LineStyleTransformation(viewModel.styles, darkTheme),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .focusRequester(bodyFocus)
                    .onFocusChanged { bodyFocused = it.isFocused },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.surface,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surface
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                )
            )
            if (bodyFocused) {
                FormatBar(
                    current = viewModel.currentStyle,
                    darkTheme = darkTheme,
                    onToggleBold = viewModel::toggleBold,
                    onColor = viewModel::setColor
                )
            }
        }
    }
}

/** Bold and colour for the line(s) under the cursor. */
@Composable
private fun FormatBar(
    current: LineStyle,
    darkTheme: Boolean,
    onToggleBold: () -> Unit,
    onColor: (LineColor) -> Unit
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconToggleButton(checked = current.bold, onCheckedChange = { onToggleBold() }) {
                Icon(Icons.Default.FormatBold, contentDescription = "Bold")
            }
            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
            LineColor.entries.forEach { color ->
                ColorSwatch(
                    color = color,
                    selected = current.color == color,
                    darkTheme = darkTheme,
                    onClick = { onColor(color) }
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: LineColor,
    selected: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit
) {
    val fill = color.colorFor(darkTheme).takeOrElse { MaterialTheme.colorScheme.onSurface }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = "${color.label} text"
                this.selected = selected
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(fill)
            )
        }
    }
}
