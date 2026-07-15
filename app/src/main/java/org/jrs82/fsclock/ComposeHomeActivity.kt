package org.jrs82.fsclock

import android.Manifest
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.jrs82.fsclock.compose.HomeDataController
import org.jrs82.fsclock.compose.HomeScreen
import org.jrs82.fsclock.compose.Page

/** Host of the Compose home screen. Forced landscape + always-on kiosk settings.
 *  Data comes from HomeDataController. */
class ComposeHomeActivity : ComponentActivity() {

    private lateinit var data: HomeDataController
    private lateinit var pixelShift: PixelShiftController
    private lateinit var brightness: BrightnessController

    /** Callback of the "Use device location" action waiting for a permission grant. */
    private var pendingLocationResult: ((Boolean) -> Unit)? = null

    private val locationPerm =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val cb = pendingLocationResult
            pendingLocationResult = null
            // Check the actual permission state (approximate/COARSE is fine too).
            if (data.hasLocationPermission()) data.useDeviceLocation(cb ?: {})
            else cb?.invoke(false)
        }

    private fun useDeviceLocation(onDone: (Boolean) -> Unit) {
        if (data.hasLocationPermission()) {
            data.useDeviceLocation(onDone)
        } else {
            pendingLocationResult = onDone
            locationPerm.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        SettingsManager.get().init(applicationContext)
        brightness = BrightnessController(window, SettingsManager.get())
        data = HomeDataController(this)
        setContent {
            var page by remember { mutableStateOf(Page.HOME) }
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeScreen(
                    ui = data.uiState,
                    page = page,
                    onPage = { page = it },
                    onBrightnessChanged = { brightness.reapply() },
                    onSearchCities = { query, cb -> data.searchCities(query, cb) },
                    onPickPlace = { data.setPlace(it) },
                    onUseDeviceLocation = { cb -> useDeviceLocation(cb) },
                )
            }
        }
        pixelShift = PixelShiftController(findViewById(android.R.id.content))
    }

    override fun onStart() {
        super.onStart()
        data.start()
        // reapply() forces the Window attribute again because settings may have
        // changed in the meantime.
        brightness.reapply()
        brightness.start()
        if (!UiMetrics.isCompactHeight(resources)) pixelShift.start()
    }

    override fun onStop() {
        pixelShift.stop()
        brightness.stop()
        data.stop()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
            // The system may have restored brightness after dialogs/settings.
            if (::brightness.isInitialized) brightness.reapply()
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
