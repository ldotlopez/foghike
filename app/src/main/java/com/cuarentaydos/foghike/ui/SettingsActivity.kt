package com.cuarentaydos.foghike.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Settings: for now only the Advanced section (fog opacity) and the app version. Values are saved as
 * they change; the main screen reads them again when it comes back to the foreground.
 */
class SettingsActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }
    private var fogOpacity by mutableIntStateOf(70)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        fogOpacity = prefs.getInt("fogOpacity", 70)
        // BuildConfig is off in this project; the installed package knows its own version.
        val info = packageManager.getPackageInfo(packageName, 0)
        val version = "${info.versionName} (${info.longVersionCode})"

        setContent {
            FogHikeTheme {
                SettingsScreen(fogOpacity, ::changeFogOpacity, version, onBack = ::finish)
            }
        }
    }

    private fun changeFogOpacity(value: Int) {
        fogOpacity = value
        prefs.edit().putInt("fogOpacity", value).apply()
    }
}
