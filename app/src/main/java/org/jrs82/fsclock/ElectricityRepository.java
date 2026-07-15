package org.jrs82.fsclock;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** In-memory cache for electricity prices. Keeps two NordPool days in memory
 *  (today + tomorrow) and refreshes via fetch from a background thread.
 *
 *  Tomorrow's prices are usually published after 14:30 Finnish time.
 *  When the user wants to see
 *  tomorrow as soon as it is available, ClockController calls fetchTomorrowIfNeeded()
 *  repeatedly until tomorrow's quarters are found. */
public final class ElectricityRepository {

    private static final String TAG = "ElectricityRepository";
    private static final long CACHE_TTL_MS = 55L * 60_000L;

    private static volatile ElectricityRepository instance;

    public static ElectricityRepository get(Context ctx) {
        if (instance == null) {
            synchronized (ElectricityRepository.class) {
                if (instance == null) {
                    instance = new ElectricityRepository(ctx.getApplicationContext());
                }
            }
        }
        return instance;
    }

    @SuppressWarnings("unused")
    private final Context appCtx;
    private final ElectricityClient client = new ElectricityClient();

    private ElectricityData data; // today + tomorrow in one bundle
    private long lastFetchOk = 0L;

    private ElectricityRepository(Context appCtx) {
        this.appCtx = appCtx;
    }

    /** Returns the in-memory data immediately (may be null). */
    public synchronized ElectricityData peek() {
        return data;
    }

    /** Fetches new data if the cache is stale. This is a synchronous network call —
     *  the caller is responsible for running it on a background thread. */
    public ElectricityData fetchIfStale() throws Exception {
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (data != null && (now - lastFetchOk) < CACHE_TTL_MS) {
                return data;
            }
        }
        return fetchNow();
    }

    /** Forced refresh (e.g. a poll attempt for tomorrow). */
    public ElectricityData fetchNow() throws Exception {
        TimeZone hel = TimeZone.getTimeZone("Europe/Helsinki");
        Calendar c = Calendar.getInstance(hel);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long startMs = c.getTimeInMillis();
        // Window in calendar days rather than milliseconds, so DST transitions
        // (25 h or 23 h days) do not drop or duplicate quarters.
        c.add(Calendar.DAY_OF_YEAR, 2);
        long endMs = c.getTimeInMillis();

        ElectricityData fresh = client.fetchRange(startMs, endMs);
        synchronized (this) {
            this.data = fresh;
            this.lastFetchOk = System.currentTimeMillis();
        }
        Log.d(TAG, "fetch ok, " + fresh.quarters.size() + " vartti(a)");
        return fresh;
    }

    /** Quarter count (out of 96) after which tomorrow is considered published. Nord Pool's
     *  CET trading day extends to 01:00 Finnish time, so "tomorrow" ALWAYS has
     *  4 quarters (00:00–00:45) even before the actual publication. */
    public static final int MIN_PUBLISHED_QUARTERS = 90;

    /** True if tomorrow has actually been published (≥ MIN_PUBLISHED_QUARTERS quarters). */
    public synchronized boolean hasTomorrow() {
        if (data == null) return false;
        TimeZone hel = TimeZone.getTimeZone("Europe/Helsinki");
        Calendar c = Calendar.getInstance(hel);
        c.add(Calendar.DAY_OF_YEAR, 1);
        int tomY = c.get(Calendar.YEAR);
        int tomM = c.get(Calendar.MONTH) + 1;
        int tomD = c.get(Calendar.DAY_OF_MONTH);
        int count = 0;
        for (ElectricityData.Quarter q : data.quarters) {
            if (q.year == tomY && q.month == tomM && q.dayOfMonth == tomD) count++;
        }
        return count >= MIN_PUBLISHED_QUARTERS;
    }

    /** Returns the current quarter (current time EET) or null if the cache does not
     *  cover the present moment. A stale quarter is never returned, so the header does
     *  not show yesterday's/outdated prices across network issues or day rollover. */
    public synchronized ElectricityData.Quarter currentQuarter() {
        if (data == null || data.quarters.isEmpty()) return null;
        long now = System.currentTimeMillis();
        for (ElectricityData.Quarter q : data.quarters) {
            if (q.timestamp <= now && q.timestamp + 15L * 60_000L > now) return q;
        }
        return null;
    }

    /** The given day's quarters in chronological order. */
    public synchronized List<ElectricityData.Quarter> dayQuarters(int year, int month, int day) {
        List<ElectricityData.Quarter> out = new ArrayList<>();
        if (data == null) return out;
        for (ElectricityData.Quarter q : data.quarters) {
            if (q.year == year && q.month == month && q.dayOfMonth == day) out.add(q);
        }
        return out;
    }
}
