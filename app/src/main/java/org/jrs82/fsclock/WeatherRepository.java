package org.jrs82.fsclock;

import android.annotation.SuppressLint;
import android.content.Context;

/** UI-level touch point for weather data. At this stage a thin wrapper that
 *  delegates the home locality fetch to {@link FmiRepository}. The intent is
 *  that ClockController gets one stable interface to weather data, so the
 *  retry, scheduler and cache logic can later be moved inside this class
 *  without changing ClockController's call sites. */
public class WeatherRepository {

    // INSTANCE holds a reference to the application context (see get()), not an
    // Activity, so lint's StaticFieldLeak is a false positive here.
    @SuppressLint("StaticFieldLeak")
    private static WeatherRepository INSTANCE;

    private final Context appCtx;

    public static synchronized WeatherRepository get(Context ctx) {
        if (INSTANCE == null) {
            INSTANCE = new WeatherRepository(ctx.getApplicationContext());
        }
        return INSTANCE;
    }

    private WeatherRepository(Context appCtx) {
        this.appCtx = appCtx;
    }

    /** Fetch the home locality's weather and persist the observation to the
     *  Room database (delegates to {@link FmiRepository#fetchHome(WeatherData)}). */
    public WeatherData fetchHome(WeatherData cached) throws Exception {
        return FmiRepository.get(appCtx).fetchHome(cached);
    }

    public WeatherData fetchHome(WeatherData cached, boolean forceNetwork) throws Exception {
        return FmiRepository.get(appCtx).fetchHome(cached, forceNetwork);
    }

    /** Weather lookup for temporary place browsing. Does not persist to Room. */
    public WeatherData fetchBrowse(String place, WeatherData cached) throws Exception {
        return FmiRepository.get(appCtx).fetchBrowse(place, cached);
    }
}
