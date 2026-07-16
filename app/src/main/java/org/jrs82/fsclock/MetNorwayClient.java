package org.jrs82.fsclock;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

/** Client for the MET Norway Locationforecast 2.0 API (compact variant).
 *  Global 9-day forecasts, no API key; the terms of service require an
 *  identifying User-Agent and gzip support, and coordinates are truncated
 *  to 4 decimals (5+ decimals return 403).
 *  https://api.met.no/weatherapi/locationforecast/2.0/documentation */
public class MetNorwayClient {

    private static final String TAG = "MetNorwayClient";
    private static final String BASE = "https://api.met.no/weatherapi/locationforecast/2.0/compact";
    private static final String USER_AGENT = "ARK-Tablet/1.0 github.com/jrs8205/ARK-Tablet";
    private static final int TIMEOUT_MS = 15000;

    private final double lat;
    private final double lon;

    public MetNorwayClient(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public WeatherData fetch() throws Exception {
        String url = BASE + "?lat=" + trunc4(lat) + "&lon=" + trunc4(lon);
        Log.d(TAG, "GET " + url);
        String body = httpGet(url);
        WeatherData data = parse(body);
        data.fetchedAt = System.currentTimeMillis();
        data.forecastFetchedAt = data.fetchedAt;
        Log.d(TAG, "MET Norway — " + data.hours.size() + " hours");
        return data;
    }

    /** The ToS caps coordinate precision at 4 decimals. */
    private static String trunc4(double v) {
        return String.format(Locale.US, "%.4f", v);
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept-Encoding", "gzip");
        try {
            int code = conn.getResponseCode();
            if (code != 200 && code != 203) {
                HttpConnectionUtils.drainErrorStream(conn);
                throw new RuntimeException("HTTP " + code + " " + conn.getResponseMessage());
            }
            InputStream raw = conn.getInputStream();
            InputStream is = "gzip".equalsIgnoreCase(conn.getContentEncoding())
                    ? new GZIPInputStream(raw) : raw;
            try (InputStream in = is; ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) baos.write(buf, 0, n);
                return baos.toString("UTF-8");
            }
        } finally {
            conn.disconnect();
        }
    }

    private WeatherData parse(String body) throws Exception {
        WeatherData data = new WeatherData();
        JSONObject root = new JSONObject(body);
        JSONArray series = root.getJSONObject("properties").getJSONArray("timeseries");

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());

        long now = System.currentTimeMillis();
        boolean currentSet = false;

        for (int i = 0; i < series.length(); i++) {
            JSONObject entry = series.getJSONObject(i);
            Date time = iso.parse(entry.getString("time"));
            if (time == null) continue;
            long ts = time.getTime();

            JSONObject d = entry.getJSONObject("data");
            JSONObject instant = d.getJSONObject("instant").getJSONObject("details");
            JSONObject next1 = d.optJSONObject("next_1_hours");
            JSONObject next6 = d.optJSONObject("next_6_hours");

            double temp = instant.optDouble("air_temperature", Double.NaN);
            double rh = instant.optDouble("relative_humidity", Double.NaN);
            double wind = instant.optDouble("wind_speed", Double.NaN);
            double windDir = instant.optDouble("wind_from_direction", Double.NaN);
            double clouds = instant.optDouble("cloud_area_fraction", Double.NaN);

            String symbol = null;
            double precip = Double.NaN;
            int blockHours = 1;
            if (next1 != null) {
                JSONObject sum = next1.optJSONObject("summary");
                if (sum != null) symbol = sum.optString("symbol_code", null);
                JSONObject det = next1.optJSONObject("details");
                if (det != null) precip = det.optDouble("precipitation_amount", Double.NaN);
            } else if (next6 != null) {
                // Later days only have 6-hour blocks; shown as block rows in the UI,
                // so the precipitation sum is kept (labeled as a 6 h span there).
                blockHours = 6;
                JSONObject sum = next6.optJSONObject("summary");
                if (sum != null) symbol = sum.optString("symbol_code", null);
                JSONObject det = next6.optJSONObject("details");
                if (det != null) precip = det.optDouble("precipitation_amount", Double.NaN);
            }

            WeatherCondition cond = symbol != null
                    ? WeatherCondition.fromMetSymbol(symbol)
                    : WeatherCondition.inferFromValues(temp, precip, clouds,
                        WeatherIconView.isNightHour(hourOf(cal, ts), monthOf(cal, ts)));

            // The first entry at/after "now" fills Current.
            if (!currentSet && ts >= now - 90L * 60_000L) {
                data.current.temperature = temp;
                data.current.humidity = rh;
                data.current.windSpeed = wind;
                data.current.windDirection = windDir;
                data.current.cloudCover = clouds;
                data.current.precip1h = blockHours == 1 ? precip : Double.NaN;
                data.current.feelsLike = WeatherData.computeFeelsLike(temp, wind, rh);
                data.current.condition = cond;
                data.current.timestamp = ts;
                currentSet = true;
            }

            WeatherData.Hour row = new WeatherData.Hour();
            row.timestamp = ts;
            cal.setTimeInMillis(ts);
            row.hour = cal.get(Calendar.HOUR_OF_DAY);
            row.dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
            row.month = cal.get(Calendar.MONTH) + 1;
            row.temperature = temp;
            row.precipitation = precip;
            row.windSpeed = wind;
            row.condition = cond;
            row.blockHours = blockHours;
            data.hours.add(row);
        }
        return data;
    }

    private static int hourOf(Calendar cal, long ts) {
        cal.setTimeInMillis(ts);
        return cal.get(Calendar.HOUR_OF_DAY);
    }

    private static int monthOf(Calendar cal, long ts) {
        cal.setTimeInMillis(ts);
        return cal.get(Calendar.MONTH) + 1;
    }
}
