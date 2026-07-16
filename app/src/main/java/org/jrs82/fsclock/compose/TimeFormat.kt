package org.jrs82.fsclock.compose

import java.util.Locale

/** Renders times of day in 24-hour or 12-hour (AM/PM) style. Pure functions — unit tested. */
object TimeFormat {

    /** Minutes since midnight → "06:45" / "6:45 AM". Out-of-range (Astronomy's
     *  never-rises/never-sets sentinels, HomeUi's -1 default) → "—". */
    fun minutesOfDay(minutes: Int, twelveHour: Boolean): String {
        if (minutes < 0 || minutes >= 1440) return "—"
        val h = minutes / 60
        val m = minutes % 60
        return if (twelveHour)
            String.format(Locale.US, "%d:%02d %s", hour12(h), m, amPm(h))
        else
            String.format(Locale.US, "%02d:%02d", h, m)
    }

    /** Hour column of the 7-day table: "07" / "7 AM". */
    fun hourLabel(hour: Int, twelveHour: Boolean): String =
        if (twelveHour) "${hour12(hour)} ${amPm(hour)}"
        else String.format(Locale.US, "%02d", hour)

    /** Day/Night starts stepper value: "06:00" / "6 AM". */
    fun stepperHour(hour: Int, twelveHour: Boolean): String =
        if (twelveHour) hourLabel(hour, true)
        else String.format(Locale.US, "%02d:00", hour)

    private fun hour12(h: Int): Int = if (h % 12 == 0) 12 else h % 12

    private fun amPm(h: Int): String = if (h < 12) "AM" else "PM"
}
