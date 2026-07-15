package org.jrs82.fsclock;

/** Internal weather model. The UI and WeatherIconView use this, not the providers' raw codes. */
public class WeatherCondition {

    public enum Type {
        CLEAR, PARTLY_CLOUDY, CLOUDY, RAIN, SNOW, SLEET, THUNDER, FOG, UNKNOWN
    }

    public enum Intensity {
        NONE, LIGHT, MODERATE, HEAVY
    }

    public Type type = Type.UNKNOWN;
    public Intensity intensity = Intensity.NONE;
    public boolean isShower = false;
    public boolean isNight = false;
    public Integer rawSmartSymbol = null;
    public Integer rawWeatherSymbol3 = null;
    public String rawMetSymbol = null;

    public WeatherCondition() {}

    public WeatherCondition(Type t, Intensity i) {
        this.type = t;
        this.intensity = i;
    }

    public static WeatherCondition unknown() {
        return new WeatherCondition(Type.UNKNOWN, Intensity.NONE);
    }

    public static WeatherCondition cloudy() {
        return new WeatherCondition(Type.CLOUDY, Intensity.NONE);
    }

    // ============================================================
    // MET Norway symbol_code → Type (Locationforecast 2.0)
    // Codes follow the pattern [light|heavy]<kind>[showers][andthunder]
    // with a _day/_night/_polartwilight variant suffix, e.g.
    // "partlycloudy_day", "lightrainshowers_night", "heavysnowandthunder".
    // Parsed by substring so every documented variant (including the
    // historical double-s codes like "lightssleetshowersandthunder") maps.
    // ============================================================
    public static WeatherCondition fromMetSymbol(String symbolCode) {
        WeatherCondition c = new WeatherCondition();
        if (symbolCode == null || symbolCode.isEmpty()) {
            c.type = Type.UNKNOWN;
            return c;
        }
        String s = symbolCode.toLowerCase(java.util.Locale.ROOT);
        c.rawMetSymbol = s;
        int us = s.indexOf('_');
        String variant = us >= 0 ? s.substring(us + 1) : "";
        c.isNight = variant.equals("night");
        String base = us >= 0 ? s.substring(0, us) : s;

        c.isShower = base.contains("showers");
        if (base.startsWith("light")) c.intensity = Intensity.LIGHT;
        else if (base.startsWith("heavy")) c.intensity = Intensity.HEAVY;
        else c.intensity = Intensity.MODERATE;

        // Order matters: "partlycloudy" also contains "cloudy".
        if (base.contains("thunder")) c.type = Type.THUNDER;
        else if (base.contains("sleet")) c.type = Type.SLEET;
        else if (base.contains("snow")) c.type = Type.SNOW;
        else if (base.contains("rain")) c.type = Type.RAIN;
        else if (base.contains("fog")) { c.type = Type.FOG; c.intensity = Intensity.LIGHT; }
        else if (base.contains("clearsky")) { c.type = Type.CLEAR; c.intensity = Intensity.NONE; }
        else if (base.contains("fair") || base.contains("partlycloudy")) { c.type = Type.PARTLY_CLOUDY; c.intensity = Intensity.NONE; }
        else if (base.contains("cloudy")) { c.type = Type.CLOUDY; c.intensity = Intensity.NONE; }
        else { c.type = Type.UNKNOWN; c.intensity = Intensity.NONE; }
        return c;
    }

    // ============================================================
    // WMO weather_code → Type (Open-Meteo)
    // Source: https://open-meteo.com/en/docs (WMO Weather interpretation codes)
    // ============================================================
    public static WeatherCondition fromWmoCode(int code, boolean night) {
        WeatherCondition c = new WeatherCondition();
        c.rawWeatherSymbol3 = code;
        c.isNight = night;
        switch (code) {
            case 0:  c.type = Type.CLEAR;          c.intensity = Intensity.NONE;     break;
            // 1 = "mainly clear" → PARTLY_CLOUDY, so both source columns
            // show the same icon in the same weather.
            case 1:  c.type = Type.PARTLY_CLOUDY;  c.intensity = Intensity.NONE;     break;
            case 2:  c.type = Type.PARTLY_CLOUDY;  c.intensity = Intensity.NONE;     break;
            case 3:  c.type = Type.CLOUDY;         c.intensity = Intensity.NONE;     break;
            case 45: case 48:
                     c.type = Type.FOG;            c.intensity = Intensity.NONE;     break;
            // Drizzle 51/53/55 = light/moderate/dense (WMO).
            case 51: c.type = Type.RAIN;           c.intensity = Intensity.LIGHT;    break;
            case 53: c.type = Type.RAIN;           c.intensity = Intensity.MODERATE; break;
            case 55: c.type = Type.RAIN;           c.intensity = Intensity.HEAVY;    break;
            // Freezing drizzle/rain is not sleet — shown as rain, consistent
            // with the MET Norway mapping, so both sources show the same icon.
            case 56: c.type = Type.RAIN;           c.intensity = Intensity.LIGHT;    break;
            case 57: c.type = Type.RAIN;           c.intensity = Intensity.HEAVY;    break;
            case 61: c.type = Type.RAIN;           c.intensity = Intensity.LIGHT;    break;
            case 63: c.type = Type.RAIN;           c.intensity = Intensity.MODERATE; break;
            case 65: c.type = Type.RAIN;           c.intensity = Intensity.HEAVY;    break;
            case 66: c.type = Type.RAIN;           c.intensity = Intensity.LIGHT;    break;
            case 67: c.type = Type.RAIN;           c.intensity = Intensity.HEAVY;    break;
            case 71: c.type = Type.SNOW;           c.intensity = Intensity.LIGHT;    break;
            case 73: c.type = Type.SNOW;           c.intensity = Intensity.MODERATE; break;
            case 75: c.type = Type.SNOW;           c.intensity = Intensity.HEAVY;    break;
            case 77: c.type = Type.SNOW;           c.intensity = Intensity.LIGHT;    break;
            case 80: c.type = Type.RAIN;           c.intensity = Intensity.LIGHT;    c.isShower = true; break;
            case 81: c.type = Type.RAIN;           c.intensity = Intensity.MODERATE; c.isShower = true; break;
            case 82: c.type = Type.RAIN;           c.intensity = Intensity.HEAVY;    c.isShower = true; break;
            case 85: c.type = Type.SNOW;           c.intensity = Intensity.LIGHT;    c.isShower = true; break;
            case 86: c.type = Type.SNOW;           c.intensity = Intensity.HEAVY;    c.isShower = true; break;
            case 95: c.type = Type.THUNDER;        c.intensity = Intensity.MODERATE; break;
            case 96: c.type = Type.THUNDER;        c.intensity = Intensity.MODERATE; break;
            case 99: c.type = Type.THUNDER;        c.intensity = Intensity.HEAVY;    break;
            default: c.type = Type.UNKNOWN;        c.intensity = Intensity.NONE;     break;
        }
        return c;
    }

    /** Infer the weather from temperature, precipitation and cloud cover when no symbol is available. */
    public static WeatherCondition inferFromValues(double temperatureC, double precipitation1h,
                                                    double totalCloudCoverPct, boolean night) {
        WeatherCondition c = new WeatherCondition();
        c.isNight = night;
        boolean hasPrecip = !Double.isNaN(precipitation1h) && precipitation1h >= 0.1;
        if (hasPrecip) {
            if (!Double.isNaN(temperatureC)) {
                if (temperatureC <= -1.0) c.type = Type.SNOW;
                else if (temperatureC >= 2.0) c.type = Type.RAIN;
                else c.type = Type.SLEET;
            } else {
                c.type = Type.RAIN;
            }
            if (precipitation1h < 0.5) c.intensity = Intensity.LIGHT;
            else if (precipitation1h < 2.0) c.intensity = Intensity.MODERATE;
            else c.intensity = Intensity.HEAVY;
            return c;
        }
        if (!Double.isNaN(totalCloudCoverPct)) {
            if (totalCloudCoverPct < 20.0) c.type = Type.CLEAR;
            else if (totalCloudCoverPct < 70.0) c.type = Type.PARTLY_CLOUDY;
            else c.type = Type.CLOUDY;
        } else {
            c.type = Type.UNKNOWN;
        }
        return c;
    }
}
