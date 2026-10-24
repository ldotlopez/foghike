package com.cuarentaydos.foghike.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.cuarentaydos.foghike.R
import com.cuarentaydos.foghike.core.AppSettings
import com.cuarentaydos.foghike.core.CELL_SIZES
import com.cuarentaydos.foghike.core.FOG_DETAILS
import com.cuarentaydos.foghike.core.FogMode
import com.cuarentaydos.foghike.core.GPS_INTERVALS
import com.cuarentaydos.foghike.core.LocationMode
import com.cuarentaydos.foghike.core.RADIUS_FACTORS
import com.cuarentaydos.foghike.core.TRAIL_COLORS
import com.cuarentaydos.foghike.core.TRAIL_MINUTES
import com.cuarentaydos.foghike.core.TRAIL_WIDTHS
import com.cuarentaydos.foghike.core.USER_ZOOMS
import kotlin.math.roundToInt

/**
 * Settings layout (docs/ui/screens/settings.md): a top bar with back, then sections of list rows.
 * [cellsBuiltWith] holds the cell size and reveal radius the stored cells use: a different value in
 * [settings] gets a "recalculated when you close" note. While [busy] (recalculating) only a progress
 * message shows. Holds no app state: [SettingsActivity] reads and stores the values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    fogOpacity: Int,
    onFogOpacity: (Int) -> Unit,
    settings: AppSettings,
    cellsBuiltWith: AppSettings,
    onSettings: (AppSettings) -> Unit,
    busy: Boolean,
    version: String,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    val back = stringResource(R.string.action_back)
                    WithTooltip(back) {
                        IconButton(onBack, enabled = !busy) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = back) }
                    }
                },
            )
        },
    ) { padding ->
        if (busy) {
            Column(Modifier.padding(padding).padding(16.dp).fillMaxWidth()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.recalculating), Modifier.padding(top = 16.dp))
            }
            return@Scaffold
        }
        val s = settings
        val recalculate = stringResource(R.string.recalculate_on_close)
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(R.string.section_map)
            ChoiceItem(stringResource(R.string.start_mode), s.startMode, LocationMode.entries, { stringResource(locationModeName(it)) }) {
                onSettings(s.copy(startMode = it))
            }
            SwitchItem(stringResource(R.string.battery_saver), stringResource(R.string.battery_saver_detail), s.batterySaver) {
                onSettings(s.copy(batterySaver = it))
            }

            SectionHeader(R.string.section_advanced)
            val opacityLabel = stringResource(R.string.fog_opacity)
            ListItem(
                // The value goes on the headline row: a trailing slot would center it on the whole item.
                headlineContent = {
                    Row {
                        Text(opacityLabel, Modifier.weight(1f))
                        Text(stringResource(R.string.percent, fogOpacity))
                    }
                },
                supportingContent = {
                    Column {
                        Slider(
                            value = fogOpacity / 100f,
                            onValueChange = { onFogOpacity((it * 20).roundToInt() * 5) },
                            steps = 19, // 5 % steps: 0, 5, … 100
                            modifier = Modifier.semantics { contentDescription = opacityLabel },
                        )
                        FogPreview(fogOpacity)
                        IgnAttribution()
                    }
                },
            )
            ChoiceItem(
                stringResource(R.string.cell_size), s.cellSize, CELL_SIZES, { stringResource(R.string.meters, it) },
                note = recalculate.takeIf { s.cellSize != cellsBuiltWith.cellSize },
            ) { onSettings(s.copy(cellSize = it)) }
            ChoiceItem(
                stringResource(R.string.reveal_radius), s.radiusFactor, RADIUS_FACTORS,
                { stringResource(R.string.radius_option, it, (s.cellSize * it).roundToInt()) },
                note = recalculate.takeIf { s.radiusFactor != cellsBuiltWith.radiusFactor },
            ) { onSettings(s.copy(radiusFactor = it)) }
            ChoiceItem(stringResource(R.string.fog_mode), s.fogMode, FogMode.entries, {
                stringResource(if (it == FogMode.DENSITY) R.string.fog_mode_density else R.string.fog_mode_any_cell)
            }) { onSettings(s.copy(fogMode = it)) }
            SwitchItem(stringResource(R.string.slow_phone), stringResource(R.string.slow_phone_detail), s.slowPhone) {
                onSettings(s.copy(slowPhone = it))
            }
            // Slow phone and battery saver set these themselves, so they only show while those are off.
            if (!s.slowPhone) {
                ChoiceItem(stringResource(R.string.fog_detail), s.fogDetail, FOG_DETAILS, {
                    stringResource(when (it) { 2 -> R.string.detail_high; 4 -> R.string.detail_medium; else -> R.string.detail_low })
                }) { onSettings(s.copy(fogDetail = it)) }
            }
            if (!s.batterySaver) {
                ChoiceItem(stringResource(R.string.gps_interval), s.gpsIntervalSeconds, GPS_INTERVALS, { stringResource(R.string.seconds, it) }) {
                    onSettings(s.copy(gpsIntervalSeconds = it))
                }
            }
            ChoiceItem(stringResource(R.string.user_zoom), s.userZoom, USER_ZOOMS, { "$it" }) { onSettings(s.copy(userZoom = it)) }
            SwitchItem(stringResource(R.string.show_trail), stringResource(R.string.show_trail_detail), s.showTrail) {
                onSettings(s.copy(showTrail = it))
            }
            if (s.showTrail) {
                ChoiceItem(stringResource(R.string.trail_duration), s.trailMinutes, TRAIL_MINUTES, { stringResource(R.string.minutes, it) }) {
                    onSettings(s.copy(trailMinutes = it))
                }
                ChoiceItem(
                    stringResource(R.string.trail_color), s.trailColor, TRAIL_COLORS,
                    { stringResource(TRAIL_COLOR_NAMES[TRAIL_COLORS.indexOf(it)]) },
                    swatch = { Color(it.toColorInt()) },
                ) { onSettings(s.copy(trailColor = it)) }
                ChoiceItem(stringResource(R.string.trail_width), s.trailWidth, TRAIL_WIDTHS, { stringResource(R.string.pixels, it) }) {
                    onSettings(s.copy(trailWidth = it))
                }
            }

            SectionHeader(R.string.section_about)
            ListItem(headlineContent = { Text(stringResource(R.string.version)) }, trailingContent = { Text(version) })
        }
    }
}

private val TRAIL_COLOR_NAMES = listOf(R.string.color_orange, R.string.color_yellow, R.string.color_pink, R.string.color_cyan)

/**
 * A setting picked from a closed list: the row shows the current value (and [note], smaller, if
 * any); tapping it opens a single-choice dialog. [swatch] adds a color dot to the row and each option.
 */
@Composable
private fun <T> ChoiceItem(
    label: String,
    value: T,
    options: List<T>,
    name: @Composable (T) -> String,
    note: String? = null,
    swatch: ((T) -> Color)? = null,
    onSelect: (T) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Column {
                Text(name(value))
                if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        },
        leadingContent = swatch?.let { { Swatch(it(value)) } },
        modifier = Modifier.clickable { open = true },
    )
    if (!open) return
    AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = { TextButton({ open = false }) { Text(stringResource(R.string.cancel)) } },
        title = { Text(label) },
        text = {
            Column(Modifier.selectableGroup()) {
                for (option in options) {
                    Row(
                        Modifier.fillMaxWidth().height(MIN_TOUCH_TARGET)
                            .selectable(option == value, role = Role.RadioButton) { open = false; onSelect(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(option == value, onClick = null)
                        Spacer(Modifier.size(16.dp))
                        if (swatch != null) {
                            Swatch(swatch(option))
                            Spacer(Modifier.size(12.dp))
                        }
                        Text(name(option))
                    }
                }
            }
        },
    )
}

@Composable
private fun Swatch(color: Color) = Box(Modifier.size(24.dp).background(color, CircleShape))

/** An on/off setting; the whole row toggles it. */
@Composable
private fun SwitchItem(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) = ListItem(
    headlineContent = { Text(label) },
    supportingContent = { Text(detail) },
    trailingContent = { Switch(checked, onCheckedChange = null) },
    modifier = Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange),
)

@Composable
private fun SectionHeader(text: Int) = Text(
    stringResource(text),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
)

/**
 * Sample IGN imagery under the same fog texture the map uses, at [opacity] %, so the slider can be
 * judged without the map. Decorative: the slider already tells screen readers the value.
 */
@Composable
private fun FogPreview(opacity: Int) {
    val fog = remember {
        val size = FOG_TEXTURE_SIZE
        val bitmap = Bitmap.createBitmap(fogTexture(size), size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
        // Repeated at its pixel size, as MapLibre repeats it as a fill pattern.
        ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(120.dp)) {
        Box {
            Image(painterResource(R.drawable.fog_preview_sample), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(fog, alpha = opacity / 100f))
        }
    }
}

// Previews: one per state in docs/ui/screens/settings.md. ui/SettingsScreenScreenshotTest.kt renders them.

@Composable
internal fun SettingsPreview(opacity: Int = 70, initial: AppSettings = AppSettings(), busy: Boolean = false) = FogHikeTheme {
    var value by remember { mutableIntStateOf(opacity) }
    var settings by remember { mutableStateOf(initial) }
    SettingsScreen(value, { value = it }, settings, AppSettings(), { settings = it }, busy, "0.1 (1)", {})
}

@Preview(showSystemUi = true) @Composable internal fun SettingsDefault() = SettingsPreview()
@Preview(showSystemUi = true) @Composable internal fun SettingsOpacityMin() = SettingsPreview(0)
@Preview(showSystemUi = true) @Composable internal fun SettingsOpacityMax() = SettingsPreview(100)
@Preview(showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES) @Composable internal fun SettingsDark() = SettingsPreview()
// Tall, to show every row: the default values, then pending recalculation with the grouping switches on.
@Preview(heightDp = 1800) @Composable internal fun SettingsAll() = SettingsPreview()
@Preview(heightDp = 1500) @Composable internal fun SettingsPending() =
    SettingsPreview(initial = AppSettings(cellSize = 50, radiusFactor = 2.0, slowPhone = true, batterySaver = true, showTrail = false))
@Preview(showSystemUi = true) @Composable internal fun SettingsRecalculating() = SettingsPreview(busy = true)
