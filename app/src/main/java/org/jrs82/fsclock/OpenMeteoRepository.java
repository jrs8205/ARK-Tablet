package org.jrs82.fsclock;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** In-memory cache for Open-Meteo responses. Room is not used in 4C
 *  — the extended weather view always reads from memory, and if the cache is
 *  empty/stale, the API is fetched again. */
public final class OpenMeteoRepository {

    private static final String TAG = "OpenMeteoRepository";
    /** Same TTL as the FMI forecast: no point refreshing more often than
     *  the source (Open-Meteo models are updated at most once an hour). */
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

    /** Returns a fresh cached result or fetches a new one. The caller is
     *  responsible for running this on a background thread — fetch makes a network call.  */
    public OpenMeteoData fetch(String placeName) throws Exception {
        return fetch(placeName, false);
    }

    public OpenMeteoData fetch(String placeName, boolean forceNetwork) throws Exception {
        if (placeName == null || placeName.trim().isEmpty()) {
            throw new IllegalArgumentException("placeName tyhjä");
        }
        String key = placeName.trim().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        if (!forceNetwork) {
            synchronized (cache) {
                CacheEntry hit = cache.get(key);
                if (hit != null && (now - hit.data.fetchedAt) < CACHE_TTL_MS) {
                    Log.d(TAG, "cache HIT " + placeName
                            + " (" + (now - hit.data.fetchedAt) / 1000L + " s)");
                    return hit.data;
                }
            }
        }
        GeoPlace place = resolvePlace(placeName);
        OpenMeteoData data = new OpenMeteoClient(place).fetch();
        synchronized (cache) {
            cache.put(key, new CacheEntry(data));
        }
        return data;
    }

    /** Fetch the forecast with EXPLICIT coordinates. The mobile front page uses this so the
     *  place's coordinates are not derived from its name (the in-memory GeoPlace registry is
     *  lost when the process dies → wrong place / silent Vantaa fallback). {@code label} is only
     *  a cache key + display name; the location is determined by {@code latitude}/{@code longitude}.
     *  Throws if the coordinates are missing — the caller decides to skip the fetch. */
    public OpenMeteoData fetch(String label, double latitude, double longitude, boolean forceNetwork)
            throws Exception {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            throw new IllegalArgumentException("koordinaatit puuttuvat");
        }
        String name = (label == null || label.trim().isEmpty()) ? "Sijainti" : label.trim();
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
        OpenMeteoData data = new OpenMeteoClient(GeoPlace.custom(name, latitude, longitude)).fetch();
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

    private GeoPlace resolvePlace(String placeName) throws Exception {
        SettingsManager sm = SettingsManager.get();
        String trimmed = placeName == null ? "" : placeName.trim();
        String home = sm.getHomePlace() == null ? "" : sm.getHomePlace().trim();
        if (sm.hasHomeCoordinates()
                && !trimmed.isEmpty()
                && (trimmed.equalsIgnoreCase(home)
                || trimmed.toLowerCase(Locale.ROOT).startsWith(home.toLowerCase(Locale.ROOT) + ","))) {
            return GeoPlace.custom(trimmed, sm.getHomeLatitude(), sm.getHomeLongitude());
        }
        GeoPlace known = GeoPlace.tryForPlace(trimmed);
        if (known != null) return known;

        // The FMI city search also registers municipalities outside the built-in list into GeoPlace.
        FmiPlaceSearch.fetchCityNames();
        known = GeoPlace.tryForPlace(trimmed);
        if (known != null) return known;
        throw new IllegalArgumentException("Tuntematon Open-Meteo-paikka: " + trimmed);
    }

    private static final class CacheEntry {
        final OpenMeteoData data;
        CacheEntry(OpenMeteoData data) { this.data = data; }
    }
}
