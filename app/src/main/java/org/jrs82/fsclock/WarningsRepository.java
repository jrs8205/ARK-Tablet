package org.jrs82.fsclock;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Singleton: keeps the latest weather warnings in memory, fetches them in the
 *  background and notifies listeners when the list changes. Does not use Room:
 *  warnings are short-lived and no history is needed. */
public class WarningsRepository {

    private static final String TAG = "WarningsRepo";
    private static final long REFRESH_MIN_INTERVAL_MS = 12L * 60_000L;

    private static volatile WarningsRepository instance;

    public static WarningsRepository get() {
        if (instance == null) {
            synchronized (WarningsRepository.class) {
                if (instance == null) instance = new WarningsRepository();
            }
        }
        return instance;
    }

    public interface Listener {
        void onWarningsChanged(List<WeatherWarning> warnings);
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final WarningsClient client = new WarningsClient();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile List<WeatherWarning> latest = Collections.emptyList();
    private volatile long lastFetchAt = 0L;
    private volatile boolean inFlight = false;

    private WarningsRepository() {}

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
            l.onWarningsChanged(latest);
        }
    }

    public void removeListener(Listener l) {
        if (l != null) listeners.remove(l);
    }

    public List<WeatherWarning> getLatest() { return latest; }

    /** Fetches new warnings if more than REFRESH_MIN_INTERVAL_MS has passed since the last fetch. */
    public void refreshIfStale() {
        long now = System.currentTimeMillis();
        pruneExpired(now);
        if (inFlight) return;
        if (now - lastFetchAt < REFRESH_MIN_INTERVAL_MS && lastFetchAt > 0L) return;
        refreshNow();
    }

    public void refreshNow() {
        if (inFlight) return;
        inFlight = true;
        io.execute(() -> {
            try {
                List<WeatherWarning> list = client.fetch();
                removeExpired(list, System.currentTimeMillis());
                sortBySeverityThenOnset(list);
                latest = Collections.unmodifiableList(new ArrayList<>(list));
                lastFetchAt = System.currentTimeMillis();
                Log.d(TAG, "Refreshed: " + list.size() + " warnings");
                notifyListeners();
            } catch (Exception e) {
                Log.w(TAG, "Warnings fetch failed: " + e.getMessage());
            } finally {
                inFlight = false;
            }
        });
    }

    private static void sortBySeverityThenOnset(List<WeatherWarning> list) {
        List<WeatherWarning> tmp = new ArrayList<>(list);
        tmp.sort((a, b) -> {
            // Non-marine first, marine at the end
            if (a.marine != b.marine) return a.marine ? 1 : -1;
            int sev = Integer.compare(b.level.rank(), a.level.rank());
            if (sev != 0) return sev;
            return Long.compare(a.onsetMs, b.onsetMs);
        });
        list.clear();
        list.addAll(tmp);
    }

    private void pruneExpired(long now) {
        List<WeatherWarning> current = latest;
        List<WeatherWarning> filtered = new ArrayList<>(current);
        removeExpired(filtered, now);
        if (filtered.size() == current.size()) return;
        latest = Collections.unmodifiableList(filtered);
        notifyListeners();
    }

    private static void removeExpired(List<WeatherWarning> list, long now) {
        list.removeIf(w -> w != null && w.expiresMs > 0L && w.expiresMs < now);
    }

    private void notifyListeners() {
        List<WeatherWarning> snapshot = latest;
        for (Listener l : listeners) {
            try { l.onWarningsChanged(snapshot); } catch (Exception ignored) {}
        }
    }
}
