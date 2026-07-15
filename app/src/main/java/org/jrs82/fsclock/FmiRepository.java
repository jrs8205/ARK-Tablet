package org.jrs82.fsclock;

import android.content.Context;
import android.util.Log;

import org.jrs82.fsclock.db.HistoryRepository;
import org.jrs82.fsclock.db.WeatherSample;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Layer wrapped around FmiClient that combines the fetch logic with
 *  DB persistence. Only the home locality's data is persisted
 *  (channel {@link SettingsManager#homeChannel()}). Data for a browsed
 *  locality stays in an in-memory cache for at most 10 minutes.
 *
 *  Bucketing (v1.6.4): home locality observations are rounded to a 10 min
 *  slot timestamp before DB persistence. If a sample for the same slot is
 *  already in the DB and the forecast cache has not expired, fetchHome returns
 *  the stored value without a new FMI request. This keeps the observation shown
 *  on multiple devices consistent (same slot → same "Sää päivitetty: HH:MM"). */
public final class FmiRepository {

    private static final String TAG = "FmiRepository";
    private static final long BROWSE_CACHE_TTL_MS = 10L * 60_000L;
    /** Slot length for bucketing: 10 min, aligned to even ten-minute marks. */
    static final long SLOT_MS = 10L * 60_000L;
    /** Same constant as FmiClient.FORECAST_MAX_AGE_MS — kept in two places so the
     *  DB cache path does not require exposing FmiClient's internal constant. */
    private static final long FORECAST_MAX_AGE_MS = 55L * 60_000L;

    private static volatile FmiRepository instance;

    public static FmiRepository get(Context context) {
        if (instance == null) {
            synchronized (FmiRepository.class) {
                if (instance == null) {
                    instance = new FmiRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private final HistoryRepository history;
    private final Map<String, BrowseCacheEntry> browseCache = new HashMap<>();

    private FmiRepository(Context appContext) {
        this.history = HistoryRepository.get(appContext);
    }

    /** Fetch the home locality's weather and persist the observation to the DB on
     *  channel {@link SettingsManager#homeChannel()}.
     *
     *  Bucketing: before the FMI request, check whether the DB already has a sample
     *  for the current 10 min slot and whether the cached forecast is still fresh.
     *  If both hold, return a constructed WeatherData without a network call.
     *  Otherwise fetch from FMI and round the observation timestamp to the start
     *  of the slot before DB persistence. */
    public WeatherData fetchHome(WeatherData cached) throws Exception {
        return fetchHome(cached, false);
    }

    public WeatherData fetchHome(WeatherData cached, boolean forceNetwork) throws Exception {
        SettingsManager sm = SettingsManager.get();
        String channel = sm.homeChannel();
        long now = System.currentTimeMillis();
        long slotStart = slotStartFor(now);

        if (!forceNetwork && !sm.hasHomeCoordinates() && canServeFromDb(cached, now)) {
            WeatherSample slotSample = history.getLatestInSlot(channel, slotStart, slotStart + SLOT_MS);
            if (slotSample != null) {
                Log.d(TAG, "fetchHome slot " + slotStart + " löytyi DB:stä, ei FMI-pyyntöä");
                return buildFromSample(cached, slotSample, now);
            }
        }

        String place = sm.getHomePlace();
        FmiClient client = sm.hasHomeCoordinates()
                ? new FmiClient(place, sm.getHomeLatitude(), sm.getHomeLongitude())
                : new FmiClient(place);
        WeatherData data = client.fetch(forceNetwork ? null : cached);
        roundCurrentToSlot(data);
        persistObservation(channel, data);
        return data;
    }

    /** Can the return value be assembled from a DB sample? Requires that the cached
     *  forecast is available and not older than {@link #FORECAST_MAX_AGE_MS} (otherwise
     *  the forecast must be fetched from FMI anyway, so the DB cache saves nothing). */
    private boolean canServeFromDb(WeatherData cached, long nowMs) {
        if (cached == null) return false;
        if (cached.hours == null || cached.hours.isEmpty()) return false;
        if (cached.forecastFetchedAt <= 0L) return false;
        return (nowMs - cached.forecastFetchedAt) <= FORECAST_MAX_AGE_MS;
    }

    /** Round the observation timestamp to the start of the 10 min slot. The UNIQUE
     *  index (channel, timestamp) guarantees the same slot is never written twice,
     *  even if several concurrent calls arrive during the same tick. */
    private static void roundCurrentToSlot(WeatherData data) {
        if (data == null || data.current == null) return;
        if (data.current.timestamp <= 0) return;
        data.current.timestamp = slotStartFor(data.current.timestamp);
    }

    private static long slotStartFor(long ms) {
        return (ms / SLOT_MS) * SLOT_MS;
    }

    /** Builds the WeatherData object to return from the cached forecast and a DB sample.
     *  The forecast is kept from cached (same instances; only past hours are filtered
     *  out, a low-impact operation). current is filled from the slot sample's fields. */
    private WeatherData buildFromSample(WeatherData cached, WeatherSample s, long nowMs) {
        WeatherData out = new WeatherData();
        out.fetchedAt = nowMs;
        out.forecastFetchedAt = cached.forecastFetchedAt;

        long minTs = nowMs - 60L * 60_000L;
        for (WeatherData.Hour h : cached.hours) {
            if (h.timestamp >= minTs) out.hours.add(h);
        }

        WeatherData.Current c = out.current;
        c.timestamp = s.timestamp;
        c.temperature = s.temperature;
        c.humidity = (s.humidity != null) ? s.humidity : Double.NaN;
        c.windSpeed = (s.windSpeed != null) ? s.windSpeed : Double.NaN;
        c.windGust = (s.windGust != null) ? s.windGust : Double.NaN;
        c.windDirection = (s.windDirection != null) ? s.windDirection : Double.NaN;
        c.cloudCover = (s.cloudCover != null) ? s.cloudCover : Double.NaN;
        c.radiationGlobal = (s.radiationGlobal != null) ? s.radiationGlobal : Double.NaN;
        c.precip1h = (s.precipitation1h != null) ? s.precipitation1h : Double.NaN;
        // rain24h cannot be reconstructed from a single row — keep the cached value
        // if available, otherwise mark it as missing.
        if (cached.current != null && !cached.current.rain24hAllMissing) {
            c.rain24h = cached.current.rain24h;
            c.rain24hAllMissing = false;
        }
        // wawa code → WeatherCondition. FmiClient.applyCurrentCondition would build
        // this, but on the DB path we trust the cache (same slot → same state).
        if (cached.current != null && cached.current.condition != null) {
            c.condition = cached.current.condition;
        }
        if (!Double.isNaN(c.temperature)) {
            c.feelsLike = WeatherData.computeFeelsLike(c.temperature, c.windSpeed, c.humidity);
        }
        return out;
    }

    /** Fetch any locality's weather for browsing. Does not persist to the DB.
     *  The same place is served from the 10 min in-memory cache when available.
     *  The result is returned on the calling thread, so this must be invoked
     *  from a background thread (a network call may be needed). */
    public WeatherData fetchBrowse(String place, WeatherData cached) throws Exception {
        if (place == null || place.trim().isEmpty()) {
            throw new IllegalArgumentException("place tyhjä");
        }
        String key = place.trim().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        synchronized (browseCache) {
            BrowseCacheEntry hit = browseCache.get(key);
            if (hit != null && (now - hit.fetchedAt) < BROWSE_CACHE_TTL_MS) {
                Log.d(TAG, "browse cache HIT " + place + " (" + (now - hit.fetchedAt) / 1000L + " s)");
                return hit.data;
            }
        }
        WeatherData data = new FmiClient(place).fetch(cached);
        synchronized (browseCache) {
            // Sweep expired entries before inserting so the cache does not grow
            // without bound over a long uptime (fix B5).
            sweepExpiredLocked(now);
            browseCache.put(key, new BrowseCacheEntry(data, now));
        }
        return data;
    }

    private void sweepExpiredLocked(long now) {
        java.util.Iterator<Map.Entry<String, BrowseCacheEntry>> it = browseCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, BrowseCacheEntry> e = it.next();
            if ((now - e.getValue().fetchedAt) >= BROWSE_CACHE_TTL_MS) {
                it.remove();
            }
        }
    }

    private void persistObservation(String channel, WeatherData data) {
        if (data == null || data.current == null) return;
        if (data.current.timestamp <= 0) return;
        if (Double.isNaN(data.current.temperature)) return; // no basis for a sample

        final WeatherSample s = new WeatherSample();
        s.channel = channel;
        s.timestamp = data.current.timestamp;
        s.temperature = data.current.temperature;
        s.humidity = nullIfNan(data.current.humidity);
        s.windSpeed = nullIfNan(data.current.windSpeed);
        s.windGust = nullIfNan(data.current.windGust);
        s.windDirection = nullIfNan(data.current.windDirection);
        s.precipitation1h = nullIfNan(data.current.precip1h);
        s.cloudCover = nullIfNan(data.current.cloudCover);
        s.radiationGlobal = nullIfNan(data.current.radiationGlobal);
        // weatherSymbol is left NULL on new rows: the legacy field came from the
        // forecast's rawSmartSymbol and varied per device. observedWawa is the
        // new source of truth (the observation's wawa code).
        s.weatherSymbol = null;
        // observedWawa: the observation's deterministic wawa code, independent of the
        // forecast. FmiClient.parseObservations sets data.current.condition.rawWawa
        // before the applyCurrentCondition merge, so the value comes straight from
        // the observations.
        s.observedWawa = (data.current.condition != null)
                ? data.current.condition.rawWawa : null;
        s.batteryLevel = null;
        // pressure is not currently fetched from FMI

        history.io().execute(() -> {
            try {
                history.saveSample(s);
            } catch (Exception e) {
                Log.w(TAG, "saveSample failed", e);
            }
        });
    }

    private static Double nullIfNan(double v) {
        return Double.isNaN(v) ? null : v;
    }

    private static final class BrowseCacheEntry {
        final WeatherData data;
        final long fetchedAt;
        BrowseCacheEntry(WeatherData data, long fetchedAt) {
            this.data = data;
            this.fetchedAt = fetchedAt;
        }
    }
}
