package org.jrs82.fsclock;

import android.content.Context;

import java.util.Locale;

/** Cache layer above MetNorwayClient. The MET Norway terms of service ask
 *  mobile clients not to refresh more than once per 10 minutes, so results
 *  are cached per coordinate for that long. */
public final class MetNorwayRepository {

    private static final long TTL_MS = 10L * 60_000L;

    private static MetNorwayRepository instance;
    private final Context appCtx;

    private WeatherData cache;
    private String cacheKey = "";
    private long cachedAt = 0L;

    private MetNorwayRepository(Context appCtx) {
        this.appCtx = appCtx;
    }

    public static synchronized MetNorwayRepository get(Context ctx) {
        if (instance == null) instance = new MetNorwayRepository(ctx.getApplicationContext());
        return instance;
    }

    /** Returns cached data when it is fresh enough, otherwise fetches. */
    public synchronized WeatherData fetch(double lat, double lon) throws Exception {
        String key = key(lat, lon);
        long now = System.currentTimeMillis();
        if (cache != null && key.equals(cacheKey) && now - cachedAt < TTL_MS) return cache;
        WeatherData data = new MetNorwayClient(lat, lon).fetch();
        cache = data;
        cacheKey = key;
        cachedAt = now;
        SettingsManager.get().setLastWeatherUpdate(now);
        return data;
    }

    public synchronized WeatherData peek(double lat, double lon) {
        return key(lat, lon).equals(cacheKey) ? cache : null;
    }

    private static String key(double lat, double lon) {
        return String.format(Locale.US, "%.4f|%.4f", lat, lon);
    }
}
