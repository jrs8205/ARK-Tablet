package org.jrs82.fsclock;

import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class FmiClient {

    private static final String TAG = "FmiClient";
    private static final String BASE = "https://opendata.fmi.fi/wfs";
    private static final int TIMEOUT_MS = 15000;
    /** Max age for a fetched forecast. Observations are fetched every 10 min, but the forecast
     *  does not need refetching every time — refreshing it roughly once an hour is enough. */
    private static final long FORECAST_MAX_AGE_MS = 55L * 60_000L;

    private final String place;
    private final double latitude;
    private final double longitude;

    public FmiClient(String place) {
        this.place = (place == null || place.trim().isEmpty()) ? "Vantaa" : place.trim();
        this.latitude = Double.NaN;
        this.longitude = Double.NaN;
    }

    public FmiClient(String place, double latitude, double longitude) {
        this.place = (place == null || place.trim().isEmpty()) ? "Vantaa" : place.trim();
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** Always fetch observations; fetch the forecast only if cached is null or > 55 min old. */
    public WeatherData fetch(WeatherData cached) throws Exception {
        WeatherData data = new WeatherData();
        String observationLocationParam = observationLocationParam();
        String forecastLocationParam = forecastLocationParam();

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Date now = cal.getTime();
        cal.add(Calendar.HOUR, -25);
        Date obsStart = cal.getTime();

        long nowMs = System.currentTimeMillis();
        boolean needForecast = (cached == null
                || cached.hours == null
                || cached.hours.isEmpty()
                || cached.forecastFetchedAt == 0L
                || (nowMs - cached.forecastFetchedAt) > FORECAST_MAX_AGE_MS);

        // 1) Observations for the last 25 h - compute the 24 h rain and take the most recent values
        String obsUrl = BASE + "?service=WFS&version=2.0.0&request=getFeature"
                + "&storedquery_id=fmi::observations::weather::simple"
                + observationLocationParam
                + "&parameters=t2m,rh,ws_10min,wg_10min,wd_10min,r_1h,n_man,wawa"
                + "&starttime=" + iso.format(obsStart)
                + "&endtime=" + iso.format(now);
        parseObservations(obsUrl, data);

        if (needForecast) {
            cal.setTime(now);
            cal.add(Calendar.HOUR, 240);
            Date fcEnd = cal.getTime();
            // 2) Hourly forecast for the next 10 days. SmartSymbol is primary,
            //    WeatherSymbol3 remains the fallback.
            String fcUrl = BASE + "?service=WFS&version=2.0.0&request=getFeature"
                    + "&storedquery_id=fmi::forecast::edited::weather::scandinavia::point::simple"
                    + forecastLocationParam
                    + "&parameters=Temperature,Precipitation1h,WindSpeedMS,WindGust,RadiationGlobal,SmartSymbol,WeatherSymbol3"
                    + "&timestep=60"
                    + "&starttime=" + iso.format(now)
                    + "&endtime=" + iso.format(fcEnd);
            parseForecast(fcUrl, data);
            data.forecastFetchedAt = nowMs;
            Log.d(TAG, "Forecast refreshed (" + data.hours.size() + " tuntia)");
        } else {
            // Use the cached forecast. Filter out hours already in the past so the forecast
            // list shows no stale rows.
            long minTs = nowMs - 60L * 60_000L;
            for (WeatherData.Hour h : cached.hours) {
                if (h.timestamp >= minTs) data.hours.add(h);
            }
            data.forecastFetchedAt = cached.forecastFetchedAt;
            long ageMin = (nowMs - cached.forecastFetchedAt) / 60_000L;
            Log.d(TAG, "Forecast reused (" + data.hours.size() + " tuntia, ikä " + ageMin + " min)");
        }

        // Current icon: primarily the nearest forecast hour's SmartSymbol; wawa may refine it
        applyCurrentCondition(data);

        data.fetchedAt = nowMs;
        if (!Double.isNaN(data.current.temperature)) {
            data.current.feelsLike = WeatherData.computeFeelsLike(
                    data.current.temperature, data.current.windSpeed, data.current.humidity);
        }
        return data;
    }

    private String observationLocationParam() throws Exception {
        return "&place=" + URLEncoder.encode(place, "UTF-8");
    }

    private String forecastLocationParam() throws Exception {
        if (!Double.isNaN(latitude) && !Double.isNaN(longitude)) {
            return String.format(Locale.US, "&latlon=%.5f,%.5f", latitude, longitude);
        }
        return "&place=" + URLEncoder.encode(place, "UTF-8");
    }

    /** Legacy API form: always fetch both (used on the first fetch). */
    public WeatherData fetch() throws Exception {
        return fetch(null);
    }

    private void parseObservations(String url, WeatherData data) throws Exception {
        Map<Long, Map<String, Double>> byTime = new HashMap<>();
        readWfs(url, byTime);

        long latestTs = 0;
        Map<String, Double> latest = null;
        Integer wawaCode = null;
        for (Map.Entry<Long, Map<String, Double>> e : byTime.entrySet()) {
            if (e.getKey() > latestTs) {
                latestTs = e.getKey();
                latest = e.getValue();
            }
        }
        if (latest != null) {
            data.current.timestamp = latestTs;
            Double v;
            v = latest.get("t2m"); if (v != null && !v.isNaN()) data.current.temperature = v;
            v = latest.get("rh"); if (v != null && !v.isNaN()) data.current.humidity = v;
            v = latest.get("ws_10min"); if (v != null && !v.isNaN()) data.current.windSpeed = v;
            v = latest.get("wg_10min"); if (v != null && !v.isNaN()) data.current.windGust = v;
            v = latest.get("wd_10min"); if (v != null && !v.isNaN()) data.current.windDirection = v;
            v = latest.get("n_man"); if (v != null && !v.isNaN() && v >= 0.0 && v <= 8.0) {
                // FMI's n_man is in oktas 0-8; stored as 0-100 %.
                // 9/8 = "cloud cover cannot be determined" (dense fog/snowfall) → missing.
                data.current.cloudCover = (v / 8.0) * 100.0;
            }
            v = latest.get("wawa"); if (v != null && !v.isNaN()) {
                wawaCode = (int) (double) v;
            }
        }
        // The wawa code is only stored in the applyCurrentCondition phase, because it is
        // merged with the forecast's SmartSymbol
        if (wawaCode != null) {
            data.current.condition.rawWawa = wawaCode;
        }

        // Rain 24h: group r_1h values by epoch hour, take the newest value per hour,
        // sum the last 24 hours. This prevents double counting if FMI returns the same
        // hour's r_1h value more than once (e.g. as part of a 10 min observation row).
        java.util.HashMap<Long, Long> rainHourLatestTs = new java.util.HashMap<>();
        java.util.HashMap<Long, Double> rainHourValue = new java.util.HashMap<>();
        for (Map.Entry<Long, Map<String, Double>> e : byTime.entrySet()) {
            Double r = e.getValue().get("r_1h");
            if (r == null || r.isNaN()) continue;
            long hourEpoch = e.getKey() / 3_600_000L;
            Long prev = rainHourLatestTs.get(hourEpoch);
            if (prev == null || e.getKey() > prev) {
                rainHourLatestTs.put(hourEpoch, e.getKey());
                rainHourValue.put(hourEpoch, Math.max(0.0, r));
            }
        }
        if (rainHourValue.isEmpty()) {
            data.current.rain24h = Double.NaN;
            data.current.rain24hAllMissing = true;
        } else {
            java.util.ArrayList<Long> hourKeys = new java.util.ArrayList<>(rainHourValue.keySet());
            java.util.Collections.sort(hourKeys);
            int from = Math.max(0, hourKeys.size() - 24);
            double sum = 0.0;
            for (int i = from; i < hourKeys.size(); i++) {
                sum += rainHourValue.get(hourKeys.get(i));
            }
            data.current.rain24h = sum;
            data.current.rain24hAllMissing = false;
            // The most recent hour's r_1h kept separately for DB persistence
            data.current.precip1h = rainHourValue.get(hourKeys.get(hourKeys.size() - 1));
            Log.d(TAG, "Rain24h: " + (hourKeys.size() - from) + " tuntia, summa " + sum + " mm");
        }
    }

    private void parseForecast(String url, WeatherData data) throws Exception {
        Map<Long, Map<String, Double>> byTime = new HashMap<>();
        readWfs(url, byTime);

        Long[] keys = byTime.keySet().toArray(new Long[0]);
        java.util.Arrays.sort(keys);
        Calendar c = Calendar.getInstance();
        for (long ts : keys) {
            Map<String, Double> m = byTime.get(ts);
            WeatherData.Hour h = new WeatherData.Hour();
            h.timestamp = ts;
            c.setTimeInMillis(ts);
            h.hour = c.get(Calendar.HOUR_OF_DAY);
            h.dayOfMonth = c.get(Calendar.DAY_OF_MONTH);
            h.month = c.get(Calendar.MONTH) + 1;

            Double v;
            v = m.get("Temperature"); if (v != null && !v.isNaN()) h.temperature = v;
            v = m.get("Precipitation1h"); if (v != null && !v.isNaN()) h.precipitation = v;
            v = m.get("WindSpeedMS"); if (v != null && !v.isNaN()) h.windSpeed = v;
            v = m.get("WindGust"); if (v != null && !v.isNaN()) h.windGust = v;
            v = m.get("RadiationGlobal"); if (v != null && !v.isNaN()) h.radiationGlobal = v;

            Integer smartSymbol = null;
            v = m.get("SmartSymbol"); if (v != null && !v.isNaN()) smartSymbol = (int) (double) v;
            Integer weatherSymbol3 = null;
            v = m.get("WeatherSymbol3"); if (v != null && !v.isNaN()) weatherSymbol3 = (int) (double) v;

            h.condition = buildForecastCondition(smartSymbol, weatherSymbol3, h);
            data.hours.add(h);
            if (data.hours.size() >= 240) break;
        }
    }

    /** Build the forecast hour's weather condition: SmartSymbol primary, WeatherSymbol3 fallback,
     *  otherwise inferred from the values. */
    private WeatherCondition buildForecastCondition(Integer smartSymbol, Integer weatherSymbol3,
                                                     WeatherData.Hour h) {
        WeatherCondition c;
        if (smartSymbol != null) {
            c = WeatherCondition.fromSmartSymbol(smartSymbol);
        } else if (weatherSymbol3 != null) {
            boolean night = WeatherIconView.isNightHour(h.hour, h.month);
            c = WeatherCondition.fromWeatherSymbol3(weatherSymbol3, night);
        } else {
            boolean night = WeatherIconView.isNightHour(h.hour, h.month);
            c = WeatherCondition.inferFromValues(h.temperature, h.precipitation, Double.NaN, night);
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format(Locale.US,
                    "FC %02d.%02d %02d:00 ss=%s ws3=%s -> %s/%s%s%s",
                    h.dayOfMonth, h.month, h.hour,
                    smartSymbol, weatherSymbol3,
                    c.type, c.intensity,
                    c.isShower ? " shower" : "",
                    c.isNight ? " night" : ""));
        }
        return c;
    }

    /** Current-moment icon:
     *  1) Use the nearest forecast hour's SmartSymbol (or its fallback)
     *  2) If a wawa code is available, let it refine the result (but not force it)
     *  3) Store all raw codes in the condition's raw* fields */
    private void applyCurrentCondition(WeatherData data) {
        Integer wawa = data.current.condition.rawWawa;
        WeatherCondition fallback = null;
        if (!data.hours.isEmpty()) {
            // Find the forecast hour closest by timestamp
            WeatherData.Hour nearest = data.hours.get(0);
            long bestDiff = Math.abs(nearest.timestamp - System.currentTimeMillis());
            for (WeatherData.Hour h : data.hours) {
                long d = Math.abs(h.timestamp - System.currentTimeMillis());
                if (d < bestDiff) { bestDiff = d; nearest = h; }
            }
            fallback = nearest.condition;
        }

        WeatherCondition c;
        if (wawa != null) {
            c = WeatherCondition.fromWawa(wawa, fallback);
        } else if (fallback != null) {
            c = fallback;
        } else {
            c = WeatherCondition.unknown();
        }
        // Merge the raw codes for the debug view
        c.rawWawa = wawa;
        if (fallback != null) {
            if (fallback.rawSmartSymbol != null) c.rawSmartSymbol = fallback.rawSmartSymbol;
            if (fallback.rawWeatherSymbol3 != null) c.rawWeatherSymbol3 = fallback.rawWeatherSymbol3;
            // isNight comes primarily from the SmartSymbol
            c.isNight = fallback.isNight;
        }
        // If the SmartSymbol did not indicate night, fall back to the local sunset time
        if (!c.isNight && (c.type == WeatherCondition.Type.CLEAR
                || c.type == WeatherCondition.Type.PARTLY_CLOUDY)) {
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int month = cal.get(Calendar.MONTH) + 1;
            if (WeatherIconView.isNightHour(hour, month)) {
                c.isNight = true;
            }
        }
        // If temperature/rain is missing from the observations, borrow it from the forecast
        if (Double.isNaN(data.current.temperature) && fallback != null
                && !data.hours.isEmpty()) {
            data.current.temperature = data.hours.get(0).temperature;
        }
        data.current.condition = c;
        Log.d(TAG, String.format(Locale.US,
                "NOW ss=%s ws3=%s wawa=%s -> %s/%s%s%s",
                c.rawSmartSymbol, c.rawWeatherSymbol3, c.rawWawa,
                c.type, c.intensity,
                c.isShower ? " shower" : "",
                c.isNight ? " night" : ""));
    }

    private void readWfs(String url, Map<Long, Map<String, Double>> out) throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/xml");
            int code = conn.getResponseCode();
            if (code != 200) {
                HttpConnectionUtils.drainErrorStream(conn);
                throw new Exception("FMI HTTP " + code);
            }
            in = conn.getInputStream();
            XmlPullParser xpp = Xml.newPullParser();
            xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            xpp.setInput(in, "UTF-8");

            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));

            String curTime = null, curParam = null, curVal = null;
            int ev = xpp.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String name = xpp.getName();
                    if (name.endsWith("Time")) {
                        curTime = xpp.nextText().trim();
                    } else if (name.endsWith("ParameterName")) {
                        curParam = xpp.nextText().trim();
                    } else if (name.endsWith("ParameterValue")) {
                        curVal = xpp.nextText().trim();
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    String name = xpp.getName();
                    if (name.endsWith("BsWfsElement")) {
                        if (curTime != null && curParam != null && curVal != null) {
                            try {
                                long ts = iso.parse(curTime).getTime();
                                double v = Double.parseDouble(curVal);
                                Map<String, Double> m = out.get(ts);
                                if (m == null) {
                                    m = new HashMap<>();
                                    out.put(ts, m);
                                }
                                m.put(curParam, v);
                            } catch (Exception ignored) { }
                        }
                        curTime = null; curParam = null; curVal = null;
                    }
                }
                ev = xpp.next();
            }
        } catch (Exception e) {
            Log.w(TAG, "WFS read failed: " + url, e);
            throw e;
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) { }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
