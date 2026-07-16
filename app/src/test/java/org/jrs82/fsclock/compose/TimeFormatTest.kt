package org.jrs82.fsclock.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    // ---------------- minutesOfDay ----------------

    @Test fun minutesOfDay24h() {
        assertEquals("00:00", TimeFormat.minutesOfDay(0, false))
        assertEquals("06:45", TimeFormat.minutesOfDay(405, false))
        assertEquals("12:45", TimeFormat.minutesOfDay(765, false))
        assertEquals("23:59", TimeFormat.minutesOfDay(1439, false))
    }

    @Test fun minutesOfDay12h() {
        assertEquals("12:00 AM", TimeFormat.minutesOfDay(0, true))
        assertEquals("6:45 AM", TimeFormat.minutesOfDay(405, true))
        assertEquals("11:59 AM", TimeFormat.minutesOfDay(719, true))
        assertEquals("12:00 PM", TimeFormat.minutesOfDay(720, true))
        assertEquals("12:45 PM", TimeFormat.minutesOfDay(765, true))
        assertEquals("1:05 PM", TimeFormat.minutesOfDay(785, true))
        assertEquals("11:59 PM", TimeFormat.minutesOfDay(1439, true))
    }

    @Test fun minutesOfDayUnknownIsDash() {
        // Astronomy uses -1 (never rises) and -2 (never sets); -1 is also the HomeUi default.
        assertEquals("—", TimeFormat.minutesOfDay(-1, false))
        assertEquals("—", TimeFormat.minutesOfDay(-2, true))
        assertEquals("—", TimeFormat.minutesOfDay(1440, false))
    }

    // ---------------- hourLabel (7-day table) ----------------

    @Test fun hourLabel24h() {
        assertEquals("00", TimeFormat.hourLabel(0, false))
        assertEquals("09", TimeFormat.hourLabel(9, false))
        assertEquals("23", TimeFormat.hourLabel(23, false))
    }

    @Test fun hourLabel12h() {
        assertEquals("12 AM", TimeFormat.hourLabel(0, true))
        assertEquals("1 AM", TimeFormat.hourLabel(1, true))
        assertEquals("11 AM", TimeFormat.hourLabel(11, true))
        assertEquals("12 PM", TimeFormat.hourLabel(12, true))
        assertEquals("1 PM", TimeFormat.hourLabel(13, true))
        assertEquals("11 PM", TimeFormat.hourLabel(23, true))
    }

    // ---------------- stepperHour (Day/Night starts) ----------------

    @Test fun stepperHour24h() {
        assertEquals("06:00", TimeFormat.stepperHour(6, false))
        assertEquals("21:00", TimeFormat.stepperHour(21, false))
        assertEquals("00:00", TimeFormat.stepperHour(0, false))
    }

    @Test fun stepperHour12h() {
        assertEquals("6 AM", TimeFormat.stepperHour(6, true))
        assertEquals("9 PM", TimeFormat.stepperHour(21, true))
        assertEquals("12 AM", TimeFormat.stepperHour(0, true))
        assertEquals("12 PM", TimeFormat.stepperHour(12, true))
    }
}
