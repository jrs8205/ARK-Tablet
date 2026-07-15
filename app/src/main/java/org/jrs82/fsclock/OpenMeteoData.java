package org.jrs82.fsclock;

import java.util.ArrayList;
import java.util.List;

/** Hourly response from Open-Meteo. Used in the extended weather view side by side
 *  with the FMI forecast, and a single-hour cross-section can be converted into a
 *  {@link WeatherSnapshot} for the front page's current-conditions comparison. */
public class OpenMeteoData {

    public final String placeName;
    public long fetchedAt;
    public final List<Hour> hours = new ArrayList<>();

    public OpenMeteoData(String placeName) {
        this.placeName = placeName;
    }

    public static class Hour {
        public long timestamp;          // ms epoch UTC
        public int hour;                // local hour 0..23
        public int dayOfMonth;          // local 1..31
        public int month;               // local 1..12
        public Double temperature;      // °C
        public Double feelsLike;        // °C (apparent_temperature)
        public Double humidity;         // %
        public Double windSpeed;        // m/s
        public Double windGust;         // m/s
        public Double windDirection;    // °
        public Double cloudCover;       // %
        public Double precipitation;    // mm/h
        public Double radiationGlobal;  // W/m²
        public WeatherCondition condition = WeatherCondition.unknown();
    }
}
