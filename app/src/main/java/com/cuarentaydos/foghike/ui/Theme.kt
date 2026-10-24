package com.cuarentaydos.foghike.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuarentaydos.foghike.R

/** Above Material's 48 dp minimum: the app is used outdoors, while walking (docs/ui/guidelines.md). */
val MIN_TOUCH_TARGET = 56.dp

/** Material 3 theme with the fixed brand palette from [Color.kt], following the phone's light/dark setting. */
@Composable
fun FogHikeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/** Shows [label] in a tooltip when [content] is long-pressed, so an icon's meaning can always be checked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content,
    )
}

/** Icon-only button for toolbars: [label] is both the screen-reader description and the tooltip. */
@Composable
fun MapIconButton(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    WithTooltip(label) {
        IconButton(onClick, modifier.size(MIN_TOUCH_TARGET), enabled = enabled) {
            Icon(painterResource(icon), contentDescription = label)
        }
    }
}

/**
 * The `(?)` button, always at the top right, and its help dialog. [openFirst] opens the dialog by
 * itself (for screens with a gesture that can't be guessed); [onClosed] lets the activity remember it.
 * Tonal so it stays visible over map imagery.
 */
@Composable
fun HelpButton(title: String, text: String, modifier: Modifier = Modifier, openFirst: Boolean = false, onClosed: () -> Unit = {}) {
    // rememberSaveable keeps the dialog open when the phone rotates or the activity is recreated.
    var open by rememberSaveable { mutableStateOf(openFirst) }
    val close = { open = false; onClosed() }
    val label = stringResource(R.string.action_help)
    WithTooltip(label) {
        FilledTonalIconButton({ open = true }, modifier.size(MIN_TOUCH_TARGET)) {
            Icon(painterResource(R.drawable.ic_help), contentDescription = label)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = close,
            confirmButton = { TextButton(close) { Text(stringResource(R.string.close)) } },
            title = { Text(title) },
            text = { Text(text) },
        )
    }
}

/**
 * Translucent panel over the map. The app draws behind the system bars (edge to edge), so the panel
 * pads itself away from them on [sides]: top for the status bar, bottom for navigation and keyboard.
 */
@Composable
fun MapPanel(sides: WindowInsetsSides, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(sides + WindowInsetsSides.Horizontal)).padding(8.dp),
            content = content,
        )
    }
}

/** Fixed-height button whose text shrinks instead of being cut, for long translations. */
@Composable
fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false) {
    val padding = PaddingValues(horizontal = 8.dp)
    val label: @Composable () -> Unit = {
        BasicText(
            text,
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp),
            style = MaterialTheme.typography.labelLarge.copy(color = LocalContentColor.current, textAlign = TextAlign.Center),
        )
    }
    if (filled) Button(onClick, modifier.height(64.dp), contentPadding = padding) { label() }
    else FilledTonalButton(onClick, modifier.height(64.dp), contentPadding = padding) { label() }
}

/** Required wherever IGN imagery is shown. */
@Composable
fun IgnAttribution() {
    Text(
        stringResource(R.string.ign_attribution),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
