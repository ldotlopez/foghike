package com.cuarentaydos.foghike.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuarentaydos.foghike.R
import com.cuarentaydos.foghike.data.Source
import com.cuarentaydos.foghike.data.Track
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/**
 * Tracks list (docs/ui/screens/tracks.md): recordings and imports, newest first, with import, multiple
 * selection and delete (trash icon or swipe left, always confirmed). [tracks] is null until read.
 * [activeTrackId] is being recorded and can't be deleted. Holds only UI state (selection, the open
 * dialog); [TracksActivity] imports and deletes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    tracks: List<Track>?,
    activeTrackId: String?,
    busy: Boolean,
    snackbar: SnackbarHostState,
    onImport: () -> Unit,
    onDelete: (List<String>) -> Unit,
    onBack: () -> Unit
) {
    // null: not selecting. Saved so a rotation or recreation keeps it.
    var selection by rememberSaveable { mutableStateOf<Set<String>?>(null) }
    var confirm by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val deletable = tracks.orEmpty().map { it.trackId }.filter { it != activeTrackId }
    BackHandler(selection != null) { selection = null }

    Scaffold(
        topBar = {
            val selected = selection
            TopAppBar(
                title = {
                    Text(
                        if (selected == null) {
                            stringResource(R.string.action_tracks)
                        } else {
                            pluralStringResource(
                                R.plurals.tracks_selected,
                                selected.size,
                                selected.size
                            )
                        }
                    )
                },
                navigationIcon = {
                    if (selected ==
                        null
                    ) {
                        MapIconButton(
                            R.drawable.ic_arrow_back,
                            stringResource(R.string.action_back),
                            onBack
                        )
                    } else {
                        MapIconButton(
                            R.drawable.ic_close,
                            stringResource(
                                R.string.action_cancel_selection
                            ),
                            {
                                selection =
                                    null
                            }
                        )
                    }
                },
                actions = {
                    if (selected == null) {
                        MapIconButton(
                            R.drawable.ic_upload_file,
                            stringResource(R.string.action_import_gpx),
                            onImport,
                            enabled = !busy
                        )
                        MapIconButton(
                            R.drawable.ic_checklist,
                            stringResource(R.string.action_select),
                            {
                                selection =
                                    emptySet()
                            },
                            enabled = !busy && deletable.isNotEmpty()
                        )
                    } else {
                        MapIconButton(
                            R.drawable.ic_select_all,
                            stringResource(
                                R.string.action_select_all
                            ),
                            {
                                selection =
                                    deletable.toSet()
                            }
                        )
                        MapIconButton(
                            R.drawable.ic_delete,
                            stringResource(R.string.action_delete_selected),
                            {
                                confirm =
                                    selected.toList()
                            },
                            enabled = !busy && selected.isNotEmpty()
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                tracks == null -> {}
                tracks.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.tracks_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                else -> LazyColumn {
                    // Keyed, so a row's swipe state stays with its track when the list changes.
                    items(tracks, key = { it.trackId }) { track ->
                        val id = track.trackId
                        val selected = selection
                        TrackRow(
                            track,
                            recording = id == activeTrackId,
                            selected = selected?.contains(id),
                            enabled = !busy,
                            onToggle = {
                                selection =
                                    if (id in
                                        selected.orEmpty()
                                    ) {
                                        selected.orEmpty() - id
                                    } else {
                                        selected.orEmpty() +
                                            id
                                    }
                            },
                            onDelete = { confirm = listOf(id) }
                        )
                    }
                }
            }
        }
    }

    confirm?.let { ids ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(pluralStringResource(R.plurals.delete_tracks_title, ids.size, ids.size))
            },
            text = { Text(stringResource(R.string.delete_tracks_text)) },
            confirmButton = {
                TextButton({
                    confirm = null
                    selection = null
                    onDelete(ids)
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton({ confirm = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/**
 * One track: name, then date and origin in smaller text. [selected] is null outside selection mode.
 * Long press starts selecting; swiping left asks to delete, like the trash icon. A [recording]
 * track can't be selected or deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackRow(
    track: Track,
    recording: Boolean,
    selected: Boolean?,
    enabled: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val canDelete = enabled && !recording
    val swipe = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val origin = stringResource(
        if (track.source ==
            Source.LIVE
        ) {
            R.string.source_own
        } else {
            R.string.source_imported
        }
    )
    val details = listOfNotNull(
        formatDateTime(track.createdAt),
        origin,
        stringResource(R.string.track_recording).takeIf {
            recording
        }
    ).joinToString(" · ")

    SwipeToDismissBox(
        swipe,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(
                    MaterialTheme.colorScheme.errorContainer
                ).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        enableDismissFromStartToEnd = false,
        gesturesEnabled = canDelete && selected == null,
        // The row slides back at once: the dialog decides, and the list drops the row if confirmed.
        onDismiss = {
            onDelete()
            scope.launch { swipe.reset() }
        }
    ) {
        ListItem(
            headlineContent = {
                Text(trackName(track), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = { Text(details, style = MaterialTheme.typography.bodySmall) },
            leadingContent = selected?.let {
                { Checkbox(it, onCheckedChange = null, enabled = !recording) }
            },
            trailingContent = if (selected == null && !recording) {
                {
                    MapIconButton(
                        R.drawable.ic_delete,
                        stringResource(R.string.action_delete_track),
                        onDelete,
                        enabled = enabled
                    )
                }
            } else {
                null
            },
            modifier = Modifier.combinedClickable(
                enabled = canDelete,
                onClick = { if (selected != null) onToggle() },
                onLongClick = { if (selected == null) onToggle() }
            )
        )
    }
}

/** Imports carry the route's own date as a suffix: "Castellón (Oct 11, 2026)". */
@Composable
private fun trackName(track: Track): String {
    val name = track.name ?: if (track.source == Source.LIVE) {
        stringResource(
            R.string.track_live_name,
            format(track.createdAt, DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        )
    } else {
        stringResource(R.string.track_unnamed)
    }
    val started = track.startedAt
    return if (track.source != Source.LIVE && started != null) {
        "$name (${format(started, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))})"
    } else {
        name
    }
}

private fun formatDateTime(millis: Long) =
    format(millis, DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

private fun format(millis: Long, formatter: DateTimeFormatter) =
    formatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))

// Previews: one per state in docs/ui/screens/tracks.md. ui/TracksScreenScreenshotTest.kt renders them.

private const val DAY = 86_400_000L
private const val NOW = 1_792_000_000_000L // 2026-10-14 17:46 UTC

internal val SAMPLE_TRACKS = listOf(
    Track("live-3", Source.LIVE, NOW),
    Track("gpx-b", Source.GPX_IMPORT, NOW - DAY, "Castellón", NOW - 3 * DAY),
    Track(
        "gpx-a",
        Source.GPX_IMPORT,
        NOW - 2 * DAY,
        "Subida a Peñalara por la laguna Grande y vuelta por el Zabala"
    ),
    Track("live-1", Source.LIVE, NOW - 9 * DAY)
)

@Composable
internal fun TracksPreview(tracks: List<Track>? = SAMPLE_TRACKS, activeTrackId: String? = null, busy: Boolean = false) =
    FogHikeTheme {
        TracksScreen(tracks, activeTrackId, busy, remember { SnackbarHostState() }, {}, {}, {})
    }

@Preview(showSystemUi = true)
@Composable
internal fun TracksDefault() = TracksPreview()

@Preview(showSystemUi = true)
@Composable
internal fun TracksEmpty() = TracksPreview(tracks = emptyList())

@Preview(showSystemUi = true)
@Composable
internal fun TracksRecording() = TracksPreview(activeTrackId = "live-3")

@Preview(showSystemUi = true)
@Composable
internal fun TracksBusy() = TracksPreview(busy = true)

@Preview(showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun TracksDark() = TracksPreview()
