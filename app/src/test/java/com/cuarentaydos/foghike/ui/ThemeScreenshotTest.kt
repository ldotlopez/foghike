package com.cuarentaydos.foghike.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.cuarentaydos.foghike.R
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the shared components in [Theme.kt], light and dark. `./gradlew recordRoborazziDebug`
 * writes the reference images to `app/src/test/screenshots/`; `verifyRoborazziDebug` compares against them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real rendering instead of Robolectric's blank canvas
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ThemeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Components() = FogHikeTheme {
        Column(Modifier.background(MAP_BACKGROUND_COLOR).padding(8.dp)) {
            Row {
                // Toolbar buttons always sit on a surface, which gives them their content color.
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row {
                        MapIconButton(R.drawable.ic_help, "Enabled", {})
                        MapIconButton(R.drawable.ic_help, "Disabled", {}, enabled = false)
                    }
                }
                HelpButton("Title", "Help text")
            }
            Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(4.dp)) { IgnAttribution() }
        }
    }

    @Test
    fun componentsLight() {
        compose.setContent { Components() }
        compose.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun componentsDark() {
        compose.setContent { Components() }
        compose.onRoot().captureRoboImage()
    }

    @OptIn(ExperimentalRoborazziApi::class) // the dialog is its own window, so the whole screen is captured
    @Test
    fun helpDialog() {
        compose.setContent {
            FogHikeTheme { HelpButton("Hike Explorer", "Walk with exploring on and the fog clears around you.", openFirst = true) }
        }
        captureScreenRoboImage()
    }
}
