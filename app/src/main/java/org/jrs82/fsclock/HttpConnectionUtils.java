package org.jrs82.fsclock;

import java.io.InputStream;
import java.net.HttpURLConnection;

final class HttpConnectionUtils {

    private HttpConnectionUtils() {}

    static void drainErrorStream(HttpURLConnection connection) {
        if (connection == null) return;
        try (InputStream error = connection.getErrorStream()) {
            if (error == null) return;
            byte[] buffer = new byte[2048];
            while (error.read(buffer) != -1) {
                // Drain the response so the HTTP connection can be released/reused.
            }
        } catch (Exception ignored) { }
    }
}
