package org.jrs82.fsclock;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** City search via the free Open-Meteo Geocoding API (global, no API key).
 *  https://open-meteo.com/en/docs/geocoding-api */
public final class GeocodingClient {

    private static final String TAG = "GeocodingClient";
    private static final String BASE = "https://geocoding-api.open-meteo.com/v1/search";
    private static final int TIMEOUT_MS = 15000;

    private GeocodingClient() {}

    public static final class Place {
        public final String name;
        public final String admin;    // state/region, may be empty
        public final String country;  // may be empty
        public final double lat;
        public final double lon;

        Place(String name, String admin, String country, double lat, double lon) {
            this.name = name;
            this.admin = admin;
            this.country = country;
            this.lat = lat;
            this.lon = lon;
        }

        /** e.g. "Hamburg — Hamburg, Germany" or "Paris — Île-de-France, France" */
        public String detail() {
            StringBuilder sb = new StringBuilder();
            if (!admin.isEmpty() && !admin.equalsIgnoreCase(name)) sb.append(admin);
            if (!country.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(country);
            }
            return sb.toString();
        }
    }

    /** Blocking search; call from a background thread. Returns up to 8 matches. */
    public static List<Place> search(String query) throws Exception {
        String url = BASE + "?name=" + URLEncoder.encode(query.trim(), "UTF-8")
                + "&count=8&language=en&format=json";
        Log.d(TAG, "GET " + url);
        String body = httpGet(url);
        List<Place> out = new ArrayList<>();
        JSONObject root = new JSONObject(body);
        JSONArray results = root.optJSONArray("results");
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            String name = r.optString("name", "");
            if (name.isEmpty()) continue;
            out.add(new Place(
                    name,
                    r.optString("admin1", ""),
                    r.optString("country", ""),
                    r.optDouble("latitude", Double.NaN),
                    r.optDouble("longitude", Double.NaN)));
        }
        return out;
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
}
