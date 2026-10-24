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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.cuarentaydos.foghike.R

/**
 * Material 3 theme. Android 12+ (our minSdk) always has dynamic color, so the app takes its palette
 * from the phone's wallpaper and follows its light/dark setting.
 */
@Composable
fun FogHikeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * Translucent panel over the map. The app draws behind the system bars (edge to edge), so the panel
 * pads itself away from them on [sides]: top for the status bar, bottom for navigation and keyboard.
 */
@Composable
fun MapPanel(sides: WindowInsetsSides, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
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

/** Behind the map view while the style loads, instead of a white flash. */
val MAP_BACKGROUND_COLOR = Color(BACKGROUND_COLOR.toColorInt())
