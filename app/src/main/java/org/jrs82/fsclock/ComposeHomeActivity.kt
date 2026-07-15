package org.jrs82.fsclock

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.jrs82.fsclock.compose.HomeDataController
import org.jrs82.fsclock.compose.HomeScreen
import org.jrs82.fsclock.compose.Page

/** Host of the Compose home screen. Forced landscape + always-on kiosk settings.
 *  Data comes from HomeDataController (reuses the existing repositories). */
class ComposeHomeActivity : ComponentActivity() {

    private lateinit var data: HomeDataController
    private lateinit var pixelShift: PixelShiftController
    private lateinit var brightness: BrightnessController

    private val locationPerm =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Check the actual permission state (approximate/COARSE is fine too), not just the granted flag.
            if (data.hasLocationPermission()) data.fetchLocation()
        }

    private val bleScanPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // After the grant the scanner must be kicked back into action (start failed without the permission).
            if (granted) org.jrs82.fsclock.ruuvi.RuuviRepository.get(this).let { it.start(); it.stop() }
        }

    /** Is the BLE scan permission granted? If not, launches the permission request and returns false.
     *  API 31+ BLUETOOTH_SCAN, API ≤30 ACCESS_FINE_LOCATION. */
    private fun ensureBleScan(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) return true
        bleScanPerm.launch(perm)
        return false
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
                    onRename = { slot, name -> data.setSensorName(slot, name) },
                    onBrightnessChanged = { brightness.reapply() },
                    ensureBleScan = { ensureBleScan() },
                    onSensorsChanged = { data.refreshSensors() },
                )
            }
        }
        pixelShift = PixelShiftController(findViewById(android.R.id.content))
    }

    override fun onStart() {
        super.onStart()
        data.start()
        // Day/night brightness + test day/night (from settings). reapply() forces the
        // Window attribute again because settings may have changed in the meantime.
        brightness.reapply()
        brightness.start()
        if (!UiMetrics.isCompactHeight(resources)) pixelShift.start()
        if (!data.hasLocationPermission()) {
            locationPerm.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
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
