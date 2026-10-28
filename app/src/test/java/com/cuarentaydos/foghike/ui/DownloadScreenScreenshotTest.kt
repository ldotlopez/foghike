package com.cuarentaydos.foghike.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
 * One screenshot per state in docs/ui/screens/download.md (numbers match the spec's States table).
 * State 2 (drawing) is a MapLibre layer, checked on a device (docs/HUMAN_TEST.md).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class DownloadScreenScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(content: @Composable () -> Unit) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage()
    }

    // Dialogs and sheets are their own windows, so the whole screen is captured.
    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureScreen(content: @Composable () -> Unit) {
        compose.setContent(content)
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    @Test fun s1Empty() = capture { DownloadEmpty() }

    @Test fun s3Areas() = capture { DownloadAreas() }

    @Test fun s4TooMany() = capture { DownloadTooMany() }

    @Test fun s5ZoomSheet() = captureScreen { DownloadZoomSheet() }

    @Test fun s6Downloading() = capture { DownloadRunning() }

    @Test fun s7Waiting() = capture { DownloadWaiting() }

    @Test fun s8Finished() = capture { DownloadFinished() }

    @Test fun s9Help() = captureScreen { DownloadHelp() }

    @Config(qualifiers = "+night")
    @Test
    fun s10Dark() = capture { DownloadDark() }
}
