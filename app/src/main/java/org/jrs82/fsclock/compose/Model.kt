package org.jrs82.fsclock.compose

import org.jrs82.fsclock.WeatherCondition

/* ---------------- Pages ---------------- */

enum class Page { HOME, INFO, FORECAST, SETTINGS }

/* ---------------- State model ---------------- */

data class WeatherUi(
    val t: Float?, val cond: String, val feels: Float?, val wind: Float?, val hum: Float?, val precip: Float?,
    val condition: WeatherCondition? = null
)

/** Single-hour forecast row (MET Norway or Open-Meteo). */
data class HourRowUi(
    val hour: Int,
    val temp: Float?,
    val feels: Float?,
    val wind: Float?,
    val hum: Float?,
    val precip: Float?,
    val condition: WeatherCondition?,
)

data class DayForecastUi(
    val label: String,                 // e.g. "Wed 15 Jul"
    val met: Map<Int, HourRowUi>,
    val om: Map<Int, HourRowUi>,
    val hours: List<Int>,              // union, in order
)

/** A geocoded place selected by the user (or via device location). */
data class PlaceUi(
    val name: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    /** Secondary line for search-result lists, e.g. "Hamburg, Germany". */
    val detail: String = "",
)

data class HomeUi(
    val time: String = "--:--",
    val sec: String = "--",
    val date: String = "—",
    val city: String = "—",
    val country: String = "",
    /** True when no place has been configured yet — home screen shows a setup hint. */
    val needsPlace: Boolean = false,
    val wifiLevel: Int = 0,
    val wifiMbps: Int = 0,
    val wifiBand: String = "",
    val battPct: Int = 0,
    val battCharging: Boolean = false,
    val met: WeatherUi? = null,
    val om: WeatherUi? = null,
    val sunRise: String = "—",
    val sunSet: String = "—",
    val dayLen: String = "",
    val sunriseMin: Int = -1,
    val sunsetMin: Int = -1,
    val moonLabel: String = "",
    val moonIllum: Int = -1,
    val moonPhase: Float = -1f,
    /** Night red-tint overlay active (setting + nighttime). */
    val redTint: Boolean = false,
    val forecast: List<DayForecastUi> = emptyList(),
)

/* ---------------- Number formatting ---------------- */

internal fun num(n: Float?, dec: Int): String {
    if (n == null || n.isNaN()) return "–"
    return String.format(java.util.Locale.US, "%.${dec}f", n)
}

internal fun numUnit(n: Float?, dec: Int, unit: String): String =
    if (n == null || n.isNaN()) "–" else num(n, dec) + unit
