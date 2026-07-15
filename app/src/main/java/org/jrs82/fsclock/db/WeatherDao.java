package org.jrs82.fsclock.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface WeatherDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(WeatherSample sample);

    /** Half-open interval [start, end). An FMI observation row at 00:00 belongs only
     *  to the starting day, not the previous one, so daily stats do not double-count
     *  the night across the day boundary. */
    @Query("SELECT * FROM weather_samples WHERE channel = :ch AND timestamp >= :start AND timestamp < :end ORDER BY timestamp")
    List<WeatherSample> getSamplesBetween(String ch, long start, long end);

    /** Bucketing check: is there already a stored sample in the 10 min slot
     *  interval [slotStart, slotEnd). Returns the most recent one (later wins,
     *  e.g. if the same slot received rows from both a delayed FMI fetch and a
     *  live request). Fetches a single row only; cheap even on long channels. */
    @Query("SELECT * FROM weather_samples WHERE channel = :ch AND timestamp >= :slotStart AND timestamp < :slotEnd ORDER BY timestamp DESC LIMIT 1")
    WeatherSample getLatestInSlot(String ch, long slotStart, long slotEnd);

    @Query("DELETE FROM weather_samples WHERE timestamp < :cutoff")
    int deleteOlderThan(long cutoff);

    @Query("SELECT COUNT(*) FROM weather_samples")
    long count();

    @Query("SELECT * FROM weather_samples ORDER BY channel, timestamp")
    List<WeatherSample> getAll();

    @Query("SELECT * FROM weather_samples WHERE channel = :ch ORDER BY timestamp")
    List<WeatherSample> getByChannel(String ch);

    /** All FMI weather observation channels (fmi_<place>) — used for the
     *  human-readable weather CSV export. The underscore is escaped so the LIKE
     *  wildcard does not match an arbitrary single character. */
    @Query("SELECT * FROM weather_samples WHERE channel LIKE 'fmi\\_%' ESCAPE '\\' "
            + "ORDER BY channel, timestamp")
    List<WeatherSample> getWeatherChannels();

    /** List of channels for the DailyStat listing (HistoryActivity). */
    @Query("SELECT DISTINCT channel FROM weather_samples ORDER BY channel")
    List<String> listChannels();

    @Query("DELETE FROM weather_samples")
    int clear();
}
