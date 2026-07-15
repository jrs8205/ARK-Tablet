package org.jrs82.fsclock;

import android.content.res.Configuration;
import android.content.res.Resources;

/** Small progressive UI scaling helper. Decisions are based on Android
 *  Configuration values, not on individual devices (e.g. SM-T819).
 *  Not named DisplayMetrics to avoid confusion with android.util.DisplayMetrics. */
public final class UiMetrics {

    private UiMetrics() {}

    /** True when the vertical dp height is limited (e.g. a landscape phone).
     *  Used for space-constrained tweaks, e.g. reducing the font size so a
     *  combined line does not overflow. SM-T819 landscape stays clearly above the limit. */
    public static boolean isCompactHeight(Resources r) {
        Configuration c = r.getConfiguration();
        return c.screenHeightDp < 480;
    }

    /** True when the smallest screen side is tablet-sized (>= 600dp).
     *  Used in later phases e.g. for shortLabel visibility on the hourly page. */
    public static boolean isTabletLike(Resources r) {
        Configuration c = r.getConfiguration();
        return c.smallestScreenWidthDp >= 600;
    }
}
