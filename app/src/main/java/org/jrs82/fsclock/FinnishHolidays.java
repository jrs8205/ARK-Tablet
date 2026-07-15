package org.jrs82.fsclock;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Official Finnish public holidays (statutory days off + ecclesiastical) plus
 *  established flag days. The front page shows whichever comes up first. */
public class FinnishHolidays {

    /** The event type affects how ClockController formats the text. */
    public enum EventType { HOLIDAY, FLAG_DAY }

    public static class Holiday {
        public final String name;
        public final int year;
        public final int month;   // 1..12
        public final int day;
        public final EventType type;

        public Holiday(String name, int y, int m, int d) {
            this(name, y, m, d, EventType.HOLIDAY);
        }
        public Holiday(String name, int y, int m, int d, EventType type) {
            this.name = name; this.year = y; this.month = m; this.day = d; this.type = type;
        }
        /** Timezone-independent day number (year*10000 + month*100 + day). */
        public int sortKey() { return year * 10000 + month * 100 + day; }
    }

    /** Anonymous Gregorian algorithm (Meeus/Jones/Butcher) -> Easter Sunday date. */
    public static int[] easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return new int[]{month, day};
    }

    private static Holiday addDays(int y, int m, int d, int delta, String name) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d);
        c.add(Calendar.DAY_OF_MONTH, delta);
        return new Holiday(name, c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Midsummer Day = the Saturday between June 20 and 26. */
    private static Holiday juhannus(int year) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, Calendar.JUNE, 20);
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return new Holiday("Juhannuspäivä", year, 6, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Midsummer Eve = the Friday preceding Midsummer Day. */
    private static Holiday juhannusaatto(int year) {
        Holiday j = juhannus(year);
        return addDays(j.year, j.month, j.day, -1, "Juhannusaatto");
    }

    /** All Saints' Day = the Saturday between Oct 31 and Nov 6. */
    private static Holiday pyhainpaiva(int year) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, Calendar.OCTOBER, 31);
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return new Holiday("Pyhäinpäivä", c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Recurring: the Nth given weekday of the month. */
    private static int nthWeekdayOfMonth(int year, int monthCal, int dayOfWeek, int nth) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, monthCal, 1);
        int sunCount = 0;
        while (true) {
            if (c.get(Calendar.DAY_OF_WEEK) == dayOfWeek) {
                sunCount++;
                if (sunCount == nth) return c.get(Calendar.DAY_OF_MONTH);
            }
            c.add(Calendar.DAY_OF_MONTH, 1);
            if (c.get(Calendar.MONTH) != monthCal) return -1;
        }
    }

    /** Recurring: the last given weekday of the month. */
    private static int lastWeekdayOfMonth(int year, int monthCal, int dayOfWeek) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, monthCal, 1);
        c.add(Calendar.MONTH, 1);
        c.add(Calendar.DAY_OF_MONTH, -1);
        while (c.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
            c.add(Calendar.DAY_OF_MONTH, -1);
        }
        return c.get(Calendar.DAY_OF_MONTH);
    }

    /** Mother's Day = the second Sunday of May. */
    private static Holiday aitienpaiva(int year) {
        int d = nthWeekdayOfMonth(year, Calendar.MAY, Calendar.SUNDAY, 2);
        return new Holiday("Äitienpäivä", year, 5, d);
    }

    /** Father's Day = the second Sunday of November. */
    private static Holiday isanpaiva(int year) {
        int d = nthWeekdayOfMonth(year, Calendar.NOVEMBER, Calendar.SUNDAY, 2);
        return new Holiday("Isänpäivä", year, 11, d);
    }

    /** First Advent = the Sunday between Nov 27 and Dec 3. */
    private static Holiday ekaAdventti(int year) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, Calendar.NOVEMBER, 27);
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return new Holiday("1. adventti", c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static List<Holiday> yearEvents(int year) {
        List<Holiday> list = new ArrayList<>();
        int[] e = easter(year);
        int em = e[0], ed = e[1];

        // --- Official days off / ecclesiastical holidays ---
        list.add(new Holiday("Uudenvuodenpäivä", year, 1, 1));
        list.add(new Holiday("Loppiainen", year, 1, 6));
        list.add(addDays(year, em, ed, -2, "Pitkäperjantai"));
        list.add(new Holiday("Pääsiäispäivä", year, em, ed));
        list.add(addDays(year, em, ed, 1, "2. pääsiäispäivä"));
        list.add(new Holiday("Vappu", year, 5, 1));
        list.add(aitienpaiva(year));
        list.add(addDays(year, em, ed, 39, "Helatorstai"));
        list.add(addDays(year, em, ed, 49, "Helluntai"));
        list.add(juhannusaatto(year));
        list.add(juhannus(year));
        list.add(pyhainpaiva(year));
        list.add(isanpaiva(year));
        list.add(new Holiday("Itsenäisyyspäivä", year, 12, 6));
        list.add(ekaAdventti(year));
        list.add(new Holiday("Jouluaatto", year, 12, 24));
        list.add(new Holiday("Joulupäivä", year, 12, 25));
        list.add(new Holiday("Tapaninpäivä", year, 12, 26));

        // --- Established flag days (option B, ~15 items) ---
        // Established ones not already HOLIDAY (May Day, Mother's Day, Midsummer Day = Day of the Finnish Flag,
        // Father's Day, Independence Day). Election days left out.
        list.add(new Holiday("Runebergin päivä", year, 2, 5, EventType.FLAG_DAY));
        list.add(new Holiday("Kalevalan päivä, suomalaisen kulttuurin päivä",
                year, 2, 28, EventType.FLAG_DAY));
        list.add(new Holiday("Minna Canthin päivä, tasa-arvon päivä",
                year, 3, 19, EventType.FLAG_DAY));
        list.add(new Holiday("Mikael Agricolan päivä, suomen kielen päivä",
                year, 4, 9, EventType.FLAG_DAY));
        list.add(new Holiday("Kansallinen veteraanipäivä", year, 4, 27, EventType.FLAG_DAY));
        list.add(new Holiday("Eurooppa-päivä", year, 5, 9, EventType.FLAG_DAY));
        list.add(new Holiday("J.V. Snellmanin päivä, suomalaisuuden päivä",
                year, 5, 12, EventType.FLAG_DAY));
        int kaatuneet = nthWeekdayOfMonth(year, Calendar.MAY, Calendar.SUNDAY, 3);
        list.add(new Holiday("Kaatuneitten muistopäivä", year, 5, kaatuneet, EventType.FLAG_DAY));
        list.add(new Holiday("Puolustusvoimain lippujuhlan päivä",
                year, 6, 4, EventType.FLAG_DAY));
        list.add(new Holiday("Eino Leinon päivä, runon ja suven päivä",
                year, 7, 6, EventType.FLAG_DAY));
        int luonnonpaiva = lastWeekdayOfMonth(year, Calendar.AUGUST, Calendar.SATURDAY);
        list.add(new Holiday("Suomen luonnon päivä", year, 8, luonnonpaiva, EventType.FLAG_DAY));
        list.add(new Holiday("Aleksis Kiven päivä, suomalaisen kirjallisuuden päivä",
                year, 10, 10, EventType.FLAG_DAY));
        list.add(new Holiday("YK:n päivä", year, 10, 24, EventType.FLAG_DAY));
        list.add(new Holiday("Ruotsalaisuuden päivä, Kustaa Aadolfin päivä",
                year, 11, 6, EventType.FLAG_DAY));
        list.add(new Holiday("Lapsen oikeuksien päivä", year, 11, 20, EventType.FLAG_DAY));

        return list;
    }

    /** Returns the next 'count' events (public holiday or flag day) starting
     *  from the given day (same day allowed). Ordered chronologically. */
    public static List<Holiday> upcoming(Calendar from, int count) {
        int fromKey = from.get(Calendar.YEAR) * 10000
                + (from.get(Calendar.MONTH) + 1) * 100
                + from.get(Calendar.DAY_OF_MONTH);

        List<Holiday> all = new ArrayList<>();
        all.addAll(yearEvents(from.get(Calendar.YEAR)));
        all.addAll(yearEvents(from.get(Calendar.YEAR) + 1));

        List<Holiday> upcoming = new ArrayList<>();
        for (Holiday h : all) {
            if (h.sortKey() >= fromKey) upcoming.add(h);
        }
        Collections.sort(upcoming, new Comparator<Holiday>() {
            @Override public int compare(Holiday a, Holiday b) {
                return Integer.compare(a.sortKey(), b.sortKey());
            }
        });
        if (upcoming.size() > count) return upcoming.subList(0, count);
        return upcoming;
    }
}
