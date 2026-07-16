package org.jrs82.fsclock

import android.service.dreams.DreamService
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.jrs82.fsclock.compose.HomeDataController
import org.jrs82.fsclock.compose.HomeScreen
import org.jrs82.fsclock.compose.Page

/** Screensaver (daydream) that shows the home page. Selectable in Android's
 *  Screen saver settings; any touch wakes the device (non-interactive dream).
 *  A DreamService has no lifecycle owner of its own, which ComposeView requires —
 *  hence the manual LifecycleRegistry/SavedStateRegistry plumbing. */
class ArkDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var data: HomeDataController? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        SettingsManager.get().init(applicationContext)
        val controller = HomeDataController(this)
        data = controller
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ArkDreamService)
            setViewTreeSavedStateRegistryOwner(this@ArkDreamService)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    HomeScreen(ui = controller.uiState, page = Page.HOME)
                }
            }
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        data?.start()
    }

    override fun onDreamingStopped() {
        data?.stop()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
