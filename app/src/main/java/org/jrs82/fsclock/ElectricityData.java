package org.jrs82.fsclock;

import java.util.ArrayList;
import java.util.List;

/** Internal model derived from Elering spot price data. Prices are snt/kWh
 *  without VAT (same unit as the raw spot published by sahkonhintatanaan.fi).
 *  Time is UTC epoch ms, quarter = 15-minute slot. */
public class ElectricityData {

    public static class Quarter {
        public long timestamp;       // ms epoch UTC (quarter start)
        public int hour;             // local 0..23
        public int minute;           // 0, 15, 30, 45
        public int dayOfMonth;       // local
        public int month;            // 1..12
        public int year;
        public double sntPerKwh;     // snt/kWh, VAT 0 %
    }

    public long fetchedAt = 0L;
    public final List<Quarter> quarters = new ArrayList<>();
}
