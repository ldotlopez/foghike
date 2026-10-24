package com.cuarentaydos.foghike.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
 * Floating toolbar over the map: a pill of icon buttons with [fab] beside it. When [expanded] is false
 * only [fab] stays. With [onExpandedChange], a handle button opens (`more_horiz`) and closes (`close`) it.
 * shortcut: hand-made because Material's HorizontalFloatingToolbar needs material3 1.5 (not stable yet);
 * swap it in when it is (docs/PLAN.md).
 */
@Composable
fun MapToolbar(
    expanded: Boolean,
    fab: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(
            expanded,
            enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
        ) {
            ToolbarPill {
                if (onExpandedChange != null) {
                    MapIconButton(R.drawable.ic_close, stringResource(R.string.action_hide_toolbar), { onExpandedChange(false) })
                }
                content()
            }
        }
        if (!expanded && onExpandedChange != null) {
            ToolbarPill { MapIconButton(R.drawable.ic_more_horiz, stringResource(R.string.action_show_toolbar), { onExpandedChange(true) }) }
        }
        fab()
    }
}

@Composable
private fun ToolbarPill(content: @Composable RowScope.() -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 3.dp) {
        Row(Modifier.padding(horizontal = 4.dp), content = content)
    }
}

/** [IgnAttribution] over the map, on a tight translucent background that keeps it readable over bright imagery. */
@Composable
fun MapAttribution() {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), shape = MaterialTheme.shapes.extraSmall) {
        Box(Modifier.padding(horizontal = 4.dp)) { IgnAttribution() }
    }
}

/** Required wherever IGN imagery is shown. One line: the text shrinks on narrow phones instead of wrapping. */
@Composable
fun IgnAttribution() {
    BasicText(
        stringResource(R.string.ign_attribution),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = MaterialTheme.typography.labelSmall.fontSize),
        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
    )
}
