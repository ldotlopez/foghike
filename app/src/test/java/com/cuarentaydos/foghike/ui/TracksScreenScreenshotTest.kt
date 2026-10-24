package com.cuarentaydos.foghike.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/** One screenshot per state in docs/ui/screens/tracks.md (numbers match the spec's States table). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class TracksScreenScreenshotTest {
    @get:Rule val compose = createComposeRule()

    // Dates and times in the rows must not depend on the machine running the tests.
    @Before fun utc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    private fun capture(content: @Composable () -> Unit) {
        compose.setContent(content)
        compose.onRoot().captureRoboImage()
    }

    @Test fun s1Default() = capture { TracksDefault() }
    @Test fun s2Empty() = capture { TracksEmpty() }
    @Test fun s3Recording() = capture { TracksRecording() }

    @Test fun s4Selecting() {
        compose.setContent { TracksDefault() }
        compose.onNodeWithText("Castellón", substring = true).performTouchInput { longClick() }
        compose.onRoot().captureRoboImage()
    }

    @OptIn(ExperimentalRoborazziApi::class) // the dialog is its own window
    @Test fun s5Confirm() {
        compose.setContent { TracksDefault() }
        compose.onAllNodesWithContentDescription("Delete track")[1].performClick()
        compose.waitForIdle() // screen captures don't wait for Compose by themselves
        captureScreenRoboImage()
    }

    @Test fun s6Busy() = capture { TracksBusy() }

    @Config(qualifiers = "+night")
    @Test fun s7Dark() = capture { TracksDark() }
}
