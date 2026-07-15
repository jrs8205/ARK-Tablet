package org.jrs82.fsclock.db;

import android.content.Context;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryRepository {

    private static final ZoneId ZONE = ZoneId.of("Europe/Helsinki");
    private static final int DEFAULT_RETENTION_DAYS = 1095;
    private static final long PARTIAL_THRESHOLD_MS = 30L * 60_000L;

    private final FsClockDb db;
    private final ExecutorService io;

    private static volatile HistoryRepository instance;

    public static HistoryRepository get(Context context) {
        if (instance == null) {
            synchronized (HistoryRepository.class) {
                if (instance == null) {
                    instance = new HistoryRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private HistoryRepository(Context appContext) {
        this.db = FsClockDb.get(appContext);
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "fsclock-db");
            t.setDaemon(true);
            return t;
        });
    }

    public ExecutorService io() {
        return io;
    }

    public void saveSample(WeatherSample sample) {
        db.weatherDao().insert(sample);
    }

    public List<DailyStat> getMonth(String channel, int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.with(TemporalAdjusters.lastDayOfMonth());
        return db.dailyStatDao().getRange(channel, first.toString(), last.toString());
    }

    /** Per-day values for a Ruuvi MAC over one month. Aggregates directly
     *  from the ruuvi_samples table — does not use the daily_stats table, since Ruuvi
     *  already stores samples only ~every 5 min. */
    public List<DailyStat> getRuuviMonth(String mac, int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate next = first.plusMonths(1);
        long from = first.atStartOfDay(ZONE).toInstant().toEpochMilli();
        long to = next.atStartOfDay(ZONE).toInstant().toEpochMilli();
        String channel = "ruuvi:" + (mac == null ? "" : mac);
        List<RuuviDailyAggregate> rows = db.ruuviSamplesDao().dailyAggregate(mac, from, to);
        List<DailyStat> out = new ArrayList<>(rows.size());
        for (RuuviDailyAggregate r : rows) {
            if (r == null || r.day == null) continue;
            DailyStat s = new DailyStat();
            s.channel = channel;
            s.date = r.day;
            s.minTemp = r.minTemp == null ? 0.0 : r.minTemp;
            s.maxTemp = r.maxTemp == null ? 0.0 : r.maxTemp;
            s.avgTemp = r.avgTemp == null ? 0.0 : r.avgTemp;
            s.sampleCount = r.sampleCount;
            // The totalPrecip field is reused to carry the average humidity
            // (the adapter knows to read it for the Ruuvi channel). DailyStat itself
            // is designed for weather data, but it is sufficient for this use.
            s.totalPrecip = r.avgHumidity;
            s.isPartial = false;
            out.add(s);
        }
        return out;
    }

    public List<String> listRuuviMacs() {
        return db.ruuviSamplesDao().listMacs();
    }

    public List<WeatherSample> getSamplesBetween(String channel, long startMs, long endMs) {
        return db.weatherDao().getSamplesBetween(channel, startMs, endMs);
    }

    /** Bucketing: fetch the sample for a 10 min slot. Used by FmiRepository.fetchHome
     *  as a consistency check — if a sample is already in the DB, it is used
     *  instead of making a new FMI request. Synchronous; call from the io pool. */
    public WeatherSample getLatestInSlot(String channel, long slotStartMs, long slotEndMs) {
        return db.weatherDao().getLatestInSlot(channel, slotStartMs, slotEndMs);
    }

    public void recomputeDailyStats(String channel, LocalDate date) {
        long start = date.atStartOfDay(ZONE).toInstant().toEpochMilli();
        long end = date.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();
        List<WeatherSample> samples = db.weatherDao().getSamplesBetween(channel, start, end);
        if (samples.isEmpty()) return;

        double minTemp = Double.POSITIVE_INFINITY;
        double maxTemp = Double.NEGATIVE_INFINITY;
        long minTempAt = 0;
        long maxTempAt = 0;
        double sumTemp = 0.0;
        Double maxWindGust = null;

        // r_1h is the previous hour's precipitation and FMI returns it every 10 min
        // with the same value. Take one value per hour (later sample wins)
        // so precipitation is not multiplied roughly 6x.
        Map<Long, Double> hourlyPrecip = new HashMap<>();

        for (WeatherSample s : samples) {
            if (s.temperature < minTemp) {
                minTemp = s.temperature;
                minTempAt = s.timestamp;
            }
            if (s.temperature > maxTemp) {
                maxTemp = s.temperature;
                maxTempAt = s.timestamp;
            }
            sumTemp += s.temperature;

            if (s.precipitation1h != null) {
                hourlyPrecip.put(s.timestamp / 3_600_000L, s.precipitation1h);
            }
            if (s.windGust != null) {
                maxWindGust = (maxWindGust == null) ? s.windGust : Math.max(maxWindGust, s.windGust);
            }
        }

        Double totalPrecip = null;
        if (!hourlyPrecip.isEmpty()) {
            double sum = 0.0;
            for (Double v : hourlyPrecip.values()) sum += v;
            totalPrecip = sum;
        }

        DailyStat stat = new DailyStat();
        stat.channel = channel;
        stat.date = date.toString();
        stat.minTemp = minTemp;
        stat.minTempAt = minTempAt;
        stat.maxTemp = maxTemp;
        stat.maxTempAt = maxTempAt;
        stat.avgTemp = sumTemp / samples.size();
        stat.totalPrecip = totalPrecip;
        stat.maxWindGust = maxWindGust;
        stat.sampleCount = samples.size();

        // isPartial=true if more than 30 min of data is missing at the start OR end of the day.
        // For the current day "end" is tomorrow's 00:00 → tail gap always large → isPartial=true.
        long firstSampleAt = samples.get(0).timestamp;
        long lastSampleAt = samples.get(samples.size() - 1).timestamp;
        boolean firstLate = (firstSampleAt - start) > PARTIAL_THRESHOLD_MS;
        boolean lastEarly = (end - lastSampleAt) > PARTIAL_THRESHOLD_MS;
        stat.isPartial = firstLate || lastEarly;

        db.dailyStatDao().upsert(stat);
    }

    public int trimOldSamples() {
        return trimOldSamples(DEFAULT_RETENTION_DAYS);
    }

    public int trimOldSamples(int retentionDays) {
        long cutoffMs = Instant.now().minus(retentionDays, ChronoUnit.DAYS).toEpochMilli();
        int samples = db.weatherDao().deleteOlderThan(cutoffMs);
        String cutoffDate = LocalDate.now(ZONE).minusDays(retentionDays).toString();
        int stats = db.dailyStatDao().deleteOlderThan(cutoffDate);
        int ruuvi = db.ruuviSamplesDao().deleteOlderThan(cutoffMs);
        return samples + stats + ruuvi;
    }

    public DailyStat allTimeMax(String channel) {
        return db.dailyStatDao().getAllTimeMax(channel);
    }

    public DailyStat allTimeMin(String channel) {
        return db.dailyStatDao().getAllTimeMin(channel);
    }

    public long sampleCount() {
        return db.weatherDao().count() + db.ruuviSamplesDao().count();
    }

    public void clearAll() {
        db.weatherDao().clear();
        db.dailyStatDao().clear();
        db.ruuviSamplesDao().deleteAll();
    }
}
