package org.jrs82.fsclock;

import android.graphics.Color;

/** Maps a temperature value to a color with linear RGB interpolation.
 *  Used in the sensor circles — below -20° deep blue,
 *  above +35° bright red. */
public final class TempColor {

    private TempColor() {}

    private static final float[] STOPS = { -20f, -5f, 5f, 15f, 25f, 35f };
    private static final int[] COLORS = {
            0xFF003F8F, // -20° deep blue
            0xFF3A7BD5, // -5°  blue
            0xFF6FD7E0, // +5°  light turquoise
            0xFF7BCB5A, // +15° green
            0xFFF2C94C, // +25° yellow
            0xFFE65B3D  // +35° warm red
    };

    public static int forTemperature(double tC) {
        if (Double.isNaN(tC)) return 0xFF606060;
        if (tC <= STOPS[0]) return COLORS[0];
        if (tC >= STOPS[STOPS.length - 1]) return COLORS[COLORS.length - 1];
        for (int i = 1; i < STOPS.length; i++) {
            if (tC <= STOPS[i]) {
                float span = STOPS[i] - STOPS[i - 1];
                float t = span <= 0 ? 0f : (float) ((tC - STOPS[i - 1]) / span);
                return lerp(COLORS[i - 1], COLORS[i], t);
            }
        }
        return COLORS[COLORS.length - 1];
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bC = Math.round(ab + (bb - ab) * t);
        return Color.rgb(r, g, bC);
    }
}
