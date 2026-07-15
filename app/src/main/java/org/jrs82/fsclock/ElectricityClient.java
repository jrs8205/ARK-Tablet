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

/** Lightweight client for the Elering NordPool spot API. Fetches prices for the
 *  given time range at 15-minute resolution and converts them to snt/kWh.
 *
 *  E.g. https://dashboard.elering.ee/api/nps/price?start=2026-05-23T22:00:00Z
 *        &end=2026-05-25T22:00:00Z
 *
 *  The response's "data.fi" is a list of {timestamp, price} (price €/MWh).
 *  snt/kWh = price / 10  (same unit as sahkonhintatanaan.fi's EUR_per_kWh × 100,
 *  without VAT). VAT-exclusive reference value, because the spot prices published
 *  by marketplaces and comparison services do not include VAT. */
public final class ElectricityClient {

    private static final String TAG = "ElectricityClient";
    private static final String BASE = "https://dashboard.elering.ee/api/nps/price";
    private static final int TIMEOUT_MS = 15000;

    public ElectricityData fetchRange(long startMs, long endMs) throws Exception {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        String s = iso.format(new Date(startMs));
        String e = iso.format(new Date(endMs));
        String url = BASE + "?start=" + s + "&end=" + e;

        Log.d(TAG, "GET " + url);
        String body = httpGet(url);
        ElectricityData data = new ElectricityData();
        parse(body, data);
        data.fetchedAt = System.currentTimeMillis();
        Log.d(TAG, "Elering " + data.quarters.size() + " hintaa");
        return data;
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                HttpConnectionUtils.drainErrorStream(conn);
                throw new RuntimeException("HTTP " + code + " " + conn.getResponseMessage());
            }
            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
                return baos.toString("UTF-8");
            }
        } finally {
            conn.disconnect();
        }
    }

    private void parse(String body, ElectricityData data) throws Exception {
        JSONObject root = new JSONObject(body);
        JSONObject d = root.optJSONObject("data");
        if (d == null) return;
        JSONArray fi = d.optJSONArray("fi");
        if (fi == null) return;

        TimeZone hel = TimeZone.getTimeZone("Europe/Helsinki");
        Calendar cal = Calendar.getInstance(hel);

        int n = fi.length();
        for (int i = 0; i < n; i++) {
            JSONObject row = fi.optJSONObject(i);
            if (row == null) continue;
            // timestamp = epoch seconds
            long ts = row.optLong("timestamp", -1L);
            if (ts <= 0L) continue;
            double priceMwh = row.optDouble("price", Double.NaN);
            if (Double.isNaN(priceMwh)) continue;

            ElectricityData.Quarter q = new ElectricityData.Quarter();
            q.timestamp = ts * 1000L;
            cal.setTimeInMillis(q.timestamp);
            q.hour = cal.get(Calendar.HOUR_OF_DAY);
            q.minute = cal.get(Calendar.MINUTE);
            q.dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
            q.month = cal.get(Calendar.MONTH) + 1;
            q.year = cal.get(Calendar.YEAR);
            q.sntPerKwh = priceMwh / 10.0;
            data.quarters.add(q);
        }
    }
}
