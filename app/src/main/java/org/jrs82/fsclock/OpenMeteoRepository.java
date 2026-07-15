package org.jrs82.fsclock;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** In-memory cache for Open-Meteo responses. */
public final class OpenMeteoRepository {

    private static final String TAG = "OpenMeteoRepository";
    /** Open-Meteo models are updated at most once an hour — no point
     *  refreshing more often than the source. */
    private static final long CACHE_TTL_MS = 55L * 60_000L;

    private static volatile OpenMeteoRepository instance;

    public static OpenMeteoRepository get(Context ctx) {
        if (instance == null) {
            synchronized (OpenMeteoRepository.class) {
                if (instance == null) {
                    instance = new OpenMeteoRepository(ctx.getApplicationContext());
                }
            }
        }
        return instance;
    }

    @SuppressWarnings("unused")
    private final Context appCtx;
    private final Map<String, CacheEntry> cache = new HashMap<>();

    private OpenMeteoRepository(Context appCtx) {
        this.appCtx = appCtx;
    }

    /** Fetch the forecast for explicit coordinates. {@code label} is only a cache
     *  key + display name. The caller is responsible for running this on a
     *  background thread — fetch makes a network call. */
    public OpenMeteoData fetch(String label, double latitude, double longitude, boolean forceNetwork)
            throws Exception {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            throw new IllegalArgumentException("coordinates missing");
        }
        String name = (label == null || label.trim().isEmpty()) ? "Location" : label.trim();
        String key = name.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        if (!forceNetwork) {
            synchronized (cache) {
                CacheEntry hit = cache.get(key);
                if (hit != null && (now - hit.data.fetchedAt) < CACHE_TTL_MS) {
                    Log.d(TAG, "cache HIT " + name
                            + " (" + (now - hit.data.fetchedAt) / 1000L + " s)");
                    return hit.data;
                }
            }
        }
        OpenMeteoData data = new OpenMeteoClient(name, latitude, longitude).fetch();
        synchronized (cache) {
            cache.put(key, new CacheEntry(data));
        }
        return data;
    }

    /** Returns the in-memory version immediately (null if not fetched yet). */
    public OpenMeteoData peek(String placeName) {
        if (placeName == null) return null;
        String key = placeName.trim().toLowerCase(Locale.ROOT);
        synchronized (cache) {
            CacheEntry hit = cache.get(key);
            return hit != null ? hit.data : null;
        }
    }

    private static final class CacheEntry {
        final OpenMeteoData data;
        CacheEntry(OpenMeteoData data) { this.data = data; }
    }
}
