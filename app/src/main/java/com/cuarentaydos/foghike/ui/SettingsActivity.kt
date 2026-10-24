package com.cuarentaydos.foghike.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.cuarentaydos.foghike.FogHikeApp
import com.cuarentaydos.foghike.core.AppSettings
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings. Values are saved as they change, except the cell size and reveal radius: those wait
 * until the user leaves, so several changes rebuild the stored cells only once. The main screen
 * reads everything again when it comes back to the foreground.
 */
class SettingsActivity : ComponentActivity() {
    private val app get() = application as FogHikeApp
    private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }
    private var fogOpacity by mutableIntStateOf(70)
    private var settings by mutableStateOf(AppSettings())
    private lateinit var cellsBuiltWith: AppSettings
    private var busy by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        fogOpacity = prefs.getInt("fogOpacity", 70)
        cellsBuiltWith = app.settings
        settings = cellsBuiltWith
        // BuildConfig is off in this project; the installed package knows its own version.
        val info = packageManager.getPackageInfo(packageName, 0)
        val version = "${info.versionName} (${info.longVersionCode})"
        // The system back gesture also goes through leave(), so pending changes are applied.
        onBackPressedDispatcher.addCallback(this) { leave() }

        setContent {
            FogHikeTheme {
                SettingsScreen(fogOpacity, ::changeFogOpacity, settings, cellsBuiltWith, ::changeSettings, busy, version, onBack = ::leave)
            }
        }
    }

    private fun changeFogOpacity(value: Int) {
        fogOpacity = value
        prefs.edit().putInt("fogOpacity", value).apply()
    }

    private fun changeSettings(s: AppSettings) {
        settings = s
        lifecycleScope.launch { app.saveSettings(s.copy(cellSize = cellsBuiltWith.cellSize, radiusFactor = cellsBuiltWith.radiusFactor)) }
    }

    /** Back: rebuilds the cells first if the cell size or reveal radius changed. */
    private fun leave() {
        if (busy) return
        if (settings.grid == cellsBuiltWith.grid) return finish()
        busy = true
        lifecycleScope.launch {
            // Finishes even if the screen goes away; if the process dies, the app reruns it at the next start.
            withContext(NonCancellable) { app.saveSettings(settings) }
            finish()
        }
    }
}
