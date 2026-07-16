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
import org.jrs82.fsclock.compose.LocationOutcome
import org.jrs82.fsclock.compose.Page

/** Host of the Compose home screen. Forced landscape + always-on kiosk settings.
 *  Data comes from HomeDataController. */
class ComposeHomeActivity : ComponentActivity() {

    private lateinit var data: HomeDataController
    private lateinit var pixelShift: PixelShiftController
    private lateinit var brightness: BrightnessController

    /** Callback waiting for a permission grant (null for the silent first-launch ask). */
    private var pendingLocationResult: ((LocationOutcome) -> Unit)? = null

    private val locationPerm =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            SettingsManager.get().setLocationPermAsked()
            data.refreshSettingsState()
            val cb = pendingLocationResult
            pendingLocationResult = null
            // Check the actual permission state (approximate/COARSE is fine too).
            if (data.hasLocationPermission()) {
                data.useDeviceLocation { ok ->
                    cb?.invoke(if (ok) LocationOutcome.SUCCESS else LocationOutcome.FAILED)
                }
            } else {
                // Right after a denial, a missing rationale means "don't ask again":
                // the dialog can no longer be shown.
                val rationale =
                    shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
                cb?.invoke(
                    if (rationale) LocationOutcome.PERMISSION_DENIED
                    else LocationOutcome.PERMISSION_DENIED_FOREVER
                )
            }
        }

    private val calendarPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            data.refreshCalendar()
        }

    private fun useDeviceLocation(onDone: (LocationOutcome) -> Unit) {
        if (data.hasLocationPermission()) {
            data.useDeviceLocation { ok ->
                onDone(if (ok) LocationOutcome.SUCCESS else LocationOutcome.FAILED)
            }
        } else {
            pendingLocationResult = onDone
            locationPerm.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    /** Location-first: whenever the permission is granted, the place follows the
     *  device location on every start — a searched city is the secondary option
     *  (and the primary one only while the permission is missing). The first
     *  launch asks for the permission right away (once). */
    private fun ensureLocationSetup() {
        val sm = SettingsManager.get()
        if (data.hasLocationPermission()) {
            data.useDeviceLocation {}
        } else if (!sm.hasPlace() && !sm.wasLocationPermAsked()) {
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
                    onTimeFormatChanged = { data.refreshSettingsState() },
                    onRequestCalendar = { calendarPerm.launch(Manifest.permission.READ_CALENDAR) },
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
        // After data.start() — the location fetch needs the controller's IO executor.
        ensureLocationSetup()
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
