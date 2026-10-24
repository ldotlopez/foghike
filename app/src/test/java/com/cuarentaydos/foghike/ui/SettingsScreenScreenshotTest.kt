package com.cuarentaydos.foghike.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** One screenshot per state in docs/ui/screens/settings.md (numbers match the spec's States table). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class SettingsScreenScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(content: @Composable () -> Unit) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage()
    }

    @Test fun s1Default() = capture { SettingsDefault() }
    @Test fun s2OpacityMin() = capture { SettingsOpacityMin() }
    @Test fun s2OpacityMax() = capture { SettingsOpacityMax() }

    @Config(qualifiers = "+night")
    @Test fun s3Dark() = capture { SettingsDark() }

    // A tall window, so every row fits in one image.
    @Config(qualifiers = "w360dp-h1800dp-xhdpi")
    @Test fun s4AllRows() = capture { SettingsAll() }
    @Config(qualifiers = "w360dp-h1500dp-xhdpi")
    @Test fun s5PendingAndGrouped() = capture { SettingsPending() }
    @Test fun s6Recalculating() = capture { SettingsRecalculating() }
}
