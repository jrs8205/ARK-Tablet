package org.jrs82.fsclock;

import android.content.Context;
import android.content.SharedPreferences;

/** App settings on top of SharedPreferences. Call init(context) once
 *  (FsClockApp does this) before using the getters. */
public final class SettingsManager {

    public static final String KEY_HOME_PLACE = "home_place";
    public static final String KEY_HOME_COUNTRY = "home_country";
    public static final String KEY_HOME_LATITUDE = "home_latitude";
    public static final String KEY_HOME_LONGITUDE = "home_longitude";
    public static final String KEY_DAY_BRIGHTNESS = "day_brightness";
    public static final String KEY_NIGHT_BRIGHTNESS = "night_brightness";
    public static final String KEY_DAY_MORNING_HOUR = "day_morning_hour";
    public static final String KEY_NIGHT_EVENING_HOUR = "night_evening_hour";
    public static final String KEY_NIGHT_RED_TINT = "night_red_tint";
    public static final String KEY_LAST_WEATHER_UPDATE = "last_weather_update";

    public static final int DEFAULT_DAY_BRIGHTNESS = 60;
    public static final int DEFAULT_NIGHT_BRIGHTNESS = 8;
    public static final int DEFAULT_MORNING_HOUR = 6;
    public static final int DEFAULT_EVENING_HOUR = 21;

    private static final String PREFS_NAME = "arktablet_prefs";

    private static SettingsManager instance;
    private SharedPreferences prefs;

    private SettingsManager() {}

    public static synchronized SettingsManager get() {
        if (instance == null) instance = new SettingsManager();
        return instance;
    }

    public synchronized void init(Context appCtx) {
        if (prefs == null) {
            prefs = appCtx.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    private SharedPreferences prefs() {
        if (prefs == null) throw new IllegalStateException("SettingsManager.init(context) must be called first");
        return prefs;
    }

    // ---------------- Place ----------------

    /** Empty string = no place configured yet (first-launch state). */
    public String getHomePlace() {
        return prefs().getString(KEY_HOME_PLACE, "");
    }

    public String getHomeCountry() {
        return prefs().getString(KEY_HOME_COUNTRY, "");
    }

    public boolean hasPlace() {
        return !getHomePlace().isEmpty() && hasHomeCoordinates();
    }

    /** Stores the selected place (from city search or device location) atomically. */
    public void setPlace(String name, String country, double latitude, double longitude) {
        prefs().edit()
                .putString(KEY_HOME_PLACE, name != null ? name : "")
                .putString(KEY_HOME_COUNTRY, country != null ? country : "")
                .putLong(KEY_HOME_LATITUDE, Double.doubleToRawLongBits(latitude))
                .putLong(KEY_HOME_LONGITUDE, Double.doubleToRawLongBits(longitude))
                .apply();
    }

    public boolean hasHomeCoordinates() {
        return prefs().contains(KEY_HOME_LATITUDE) && prefs().contains(KEY_HOME_LONGITUDE);
    }

    public double getHomeLatitude() {
        return Double.longBitsToDouble(prefs().getLong(KEY_HOME_LATITUDE, 0L));
    }

    public double getHomeLongitude() {
        return Double.longBitsToDouble(prefs().getLong(KEY_HOME_LONGITUDE, 0L));
    }

    // ---------------- Display ----------------

    public int getDayBrightness() {
        return prefs().getInt(KEY_DAY_BRIGHTNESS, DEFAULT_DAY_BRIGHTNESS);
    }

    public void setDayBrightness(int v) {
        prefs().edit().putInt(KEY_DAY_BRIGHTNESS, clampPct(v)).apply();
    }

    public int getNightBrightness() {
        return prefs().getInt(KEY_NIGHT_BRIGHTNESS, DEFAULT_NIGHT_BRIGHTNESS);
    }

    public void setNightBrightness(int v) {
        prefs().edit().putInt(KEY_NIGHT_BRIGHTNESS, clampPct(v)).apply();
    }

    public int getMorningHour() {
        return prefs().getInt(KEY_DAY_MORNING_HOUR, DEFAULT_MORNING_HOUR);
    }

    public void setMorningHour(int v) {
        prefs().edit().putInt(KEY_DAY_MORNING_HOUR, clampHour(v)).apply();
    }

    public int getEveningHour() {
        return prefs().getInt(KEY_NIGHT_EVENING_HOUR, DEFAULT_EVENING_HOUR);
    }

    public void setEveningHour(int v) {
        prefs().edit().putInt(KEY_NIGHT_EVENING_HOUR, clampHour(v)).apply();
    }

    public boolean isNightRedTint() {
        return prefs().getBoolean(KEY_NIGHT_RED_TINT, false);
    }

    public void setNightRedTint(boolean v) {
        prefs().edit().putBoolean(KEY_NIGHT_RED_TINT, v).apply();
    }

    // ---------------- Status ----------------

    public long getLastWeatherUpdate() {
        return prefs().getLong(KEY_LAST_WEATHER_UPDATE, 0L);
    }

    public void setLastWeatherUpdate(long timestampMs) {
        prefs().edit().putLong(KEY_LAST_WEATHER_UPDATE, timestampMs).apply();
    }

    private static int clampPct(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static int clampHour(int v) {
        return Math.max(0, Math.min(23, v));
    }
}
