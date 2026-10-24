package com.cuarentaydos.foghike.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** One screenshot per state in docs/ui/screens/main.md (numbers match the spec's States table). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class MainScreenScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(content: @Composable () -> Unit) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage()
    }

    @Test fun s1Default() = capture { MainDefault() }

    @Test fun s2ExploringExpanded() {
        compose.setContent { MainExploringCollapsed() }
        compose.onNodeWithContentDescription("Show actions").performClick()
        compose.onRoot().captureRoboImage()
    }

    @Test fun s3ExploringCollapsed() = capture { MainExploringCollapsed() }
    @Test fun s4NoMaps() = capture { MainNoMaps() }
    @Test fun s5Snackbar() = capture { MainSnackbar() }

    @OptIn(ExperimentalRoborazziApi::class) // the dialog is its own window
    @Test fun s6Help() {
        compose.setContent { MainDefault() }
        compose.onNodeWithContentDescription("Help").performClick()
        compose.waitForIdle() // screen captures don't wait for Compose by themselves
        captureScreenRoboImage()
    }

    @Config(qualifiers = "+night")
    @Test fun s7Dark() = capture { MainDark() }

    @Test fun s8Following() = capture { MainFollowing() }
    @Test fun s9Compass() = capture { MainCompass() }
}
