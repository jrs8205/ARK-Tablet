package org.jrs82.fsclock;

import java.util.Locale;

/** A single FMI/MeteoAlarm weather warning. One instance corresponds to one alert object,
 *  which may cover several regions (areaDesc is already a comma-separated list). */
public class WeatherWarning {

    public enum Level {
        YELLOW(0xFFE6C32E, "Keltainen"),
        ORANGE(0xFFE89B2C, "Oranssi"),
        RED(0xFFD0413B, "Punainen"),
        UNKNOWN(0xFF888888, "");

        public final int color;
        public final String fiName;
        Level(int color, String fiName) { this.color = color; this.fiName = fiName; }

        /** Parses the MeteoAlarm awareness_level string, e.g. "2; yellow; Moderate". */
        public static Level fromAwareness(String raw) {
            if (raw == null) return UNKNOWN;
            String low = raw.toLowerCase(Locale.ROOT);
            if (low.contains("red")) return RED;
            if (low.contains("orange")) return ORANGE;
            if (low.contains("yellow")) return YELLOW;
            return UNKNOWN;
        }

        public int rank() {
            switch (this) {
                case RED: return 3;
                case ORANGE: return 2;
                case YELLOW: return 1;
                default: return 0;
            }
        }
    }

    public final String event;
    public final String description;
    public final String areaDesc;
    public final long onsetMs;
    public final long expiresMs;
    public final Level level;
    public final String identifier;
    /** true if the warning concerns boaters or sea areas (sorted to the end of the list). */
    public final boolean marine;

    public WeatherWarning(String event, String description, String areaDesc,
                           long onsetMs, long expiresMs, Level level, String identifier,
                           boolean marine) {
        this.event = event == null ? "" : event;
        this.description = description == null ? "" : description;
        this.areaDesc = areaDesc == null ? "" : areaDesc;
        this.onsetMs = onsetMs;
        this.expiresMs = expiresMs;
        this.level = level == null ? Level.UNKNOWN : level;
        this.identifier = identifier == null ? "" : identifier;
        this.marine = marine;
    }

    public static boolean detectMarine(String event, String areaDesc, java.util.List<String> emmaIds) {
        String e = event == null ? "" : event.toLowerCase(Locale.ROOT);
        if (e.contains("veneilij") || e.contains("merialue")) return true;
        String a = areaDesc == null ? "" : areaDesc.toLowerCase(Locale.ROOT);
        // Names of Finland's sea areas in MeteoAlarm
        if (a.contains("perämer") || a.contains("selkämer") || a.contains("suomenlah")
                || a.contains("ahvenanm") || a.contains("saaristom") || a.contains("merenkurk")
                || a.contains("riianlah") || a.contains("itämer")) return true;
        if (emmaIds != null) {
            for (String id : emmaIds) {
                if (id != null && id.startsWith("FI8")) return true;
            }
        }
        return false;
    }
}
