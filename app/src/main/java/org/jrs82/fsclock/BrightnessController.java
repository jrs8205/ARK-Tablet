package org.jrs82.fsclock;

import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowManager;

import java.util.Calendar;
import java.util.Locale;

/** Day/night brightness management. Adjusts Window.screenBrightness based on time of
 *  day and test mode. Runs its own minute tick so that clock-time boundaries
 *  (morning/evening) get noticed without an external wakeup. */
public class BrightnessController {

    private static final Locale FI = new Locale("fi", "FI");
    private static final long TICK_MS = 60_000L;

    private final Window window;            // may be null (then a no-op)
    private final SettingsManager settings;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private float lastBrightness = -1f;

    public BrightnessController(Window window, SettingsManager settings) {
        this.window = window;
        this.settings = settings;
    }

    public void start() {
        applyNow();
        ui.removeCallbacks(tick);
        // Sync the next tick to the next minute boundary so the 06:00/21:00
        // transitions take effect with under a second of delay.
        long delay = TICK_MS - (System.currentTimeMillis() % TICK_MS);
        ui.postDelayed(tick, delay);
    }

    public void stop() {
        ui.removeCallbacks(tick);
    }

    /** Adjust brightness for the current moment (test mode or time of day).
     *  The cache avoids unnecessary Window attribute updates, but if pct changes,
     *  the new value takes effect immediately. */
    public void applyNow() {
        if (window == null) return;
        int testMode = settings.getActiveTestMode();
        int pct;
        if (testMode == SettingsManager.TEST_DAY) {
            pct = settings.getDayBrightness();
        } else if (testMode == SettingsManager.TEST_NIGHT) {
            pct = settings.getNightBrightness();
        } else {
            int hour = Calendar.getInstance(FI).get(Calendar.HOUR_OF_DAY);
            pct = isNightBrightness(hour) ? settings.getNightBrightness() : settings.getDayBrightness();
        }
        float val = Math.max(0.01f, Math.min(1f, pct / 100f));
        if (Math.abs(val - lastBrightness) < 0.005f) return;
        lastBrightness = val;
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.screenBrightness = val;
        window.setAttributes(lp);
    }

    /** Force a re-apply even if pct has not changed.
     *  Used e.g. after returning from SettingsActivity, when the system may have
     *  modified the Window attributes in the meantime. */
    public void reapply() {
        lastBrightness = -1f;
        applyNow();
    }

    private boolean isNightBrightness(int hourOfDay) {
        int morning = settings.getMorningHour();
        int evening = settings.getEveningHour();
        if (evening >= morning) return hourOfDay >= evening || hourOfDay < morning;
        return hourOfDay >= evening && hourOfDay < morning;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            applyNow();
            long delay = TICK_MS - (System.currentTimeMillis() % TICK_MS);
            ui.postDelayed(this, delay);
        }
    };
}
