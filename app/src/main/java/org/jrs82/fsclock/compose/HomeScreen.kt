package org.jrs82.fsclock.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ---------------- Clock text (live, second precision, device time zone) ---------------- */

private data class ClockText(val time: String, val sec: String, val date: String)

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val secondFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("ss", Locale.ENGLISH)
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)

@Composable
private fun rememberClockText(): ClockText {
    var now by remember { mutableStateOf(ZonedDateTime.now(ZoneId.systemDefault())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L - System.currentTimeMillis() % 1000L)
            now = ZonedDateTime.now(ZoneId.systemDefault())
        }
    }
    return ClockText(
        time = now.format(timeFormatter),
        sec = now.format(secondFormatter),
        date = now.format(dateFormatter),
    )
}

/* ---------------- Frame: top bar + page ---------------- */

@Composable
fun HomeScreen(
    ui: HomeUi,
    page: Page,
    onPage: (Page) -> Unit = {},
    onBrightnessChanged: () -> Unit = {},
    onSearchCities: (String, (List<PlaceUi>?) -> Unit) -> Unit = { _, cb -> cb(null) },
    onPickPlace: (PlaceUi) -> Unit = {},
    onUseDeviceLocation: ((Boolean) -> Unit) -> Unit = { it(false) },
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Ark.BgGrad1.copy(alpha = 0.76f), Ark.Bg, Ark.BgGrad2.copy(alpha = 0.68f))
            )
        )
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val heightPx = constraints.maxHeight / 100f
            val widthPx = constraints.maxWidth / 100f
            val textPx = minOf(heightPx, constraints.maxWidth / 150f)
            val s = Scale(heightPx, widthPx, textPx, LocalDensity.current)
            Column(Modifier.fillMaxSize()) {
                TopBar(ui, s, page, onPage)
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (page) {
                        Page.HOME -> HomePage(ui, s)
                        Page.INFO -> InfoPage(ui, s)
                        Page.FORECAST -> ForecastPage(ui, s)
                        Page.SETTINGS -> SettingsPage(
                            s = s,
                            onBrightnessChanged = onBrightnessChanged,
                            onSearchCities = onSearchCities,
                            onPickPlace = onPickPlace,
                            onUseDeviceLocation = onUseDeviceLocation,
                        )
                    }
                }
            }
        }
        // Night red tint: warm filter over the whole screen (does not capture touches).
        if (ui.redTint) {
            Box(Modifier.fillMaxSize().background(Color(0x4DFF2A00)))
        }
    }
}

/* ---------------- Top bar ---------------- */

@Composable
private fun TopBar(ui: HomeUi, s: Scale, page: Page, onPage: (Page) -> Unit) {
    Column(
        Modifier.fillMaxWidth().height(s.dh(11f)).background(
            Brush.horizontalGradient(listOf(Ark.TopBarStart, Ark.TopBarEnd))
        )
    ) {
        Row(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = s.dw(2.4f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Place on two lines (city / country)
            Icon(Icons.Filled.Place, null, tint = Ark.Accent, modifier = Modifier.size(s.dh(3.2f)))
            Spacer(Modifier.width(s.dw(0.8f)))
            Column {
                Text(ui.city, color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = s.sh(2.7f), maxLines = 1)
                if (ui.country.isNotEmpty()) {
                    Text(ui.country, color = Ark.Muted, fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium, fontSize = s.sh(2.1f), maxLines = 1)
                }
            }
            Spacer(Modifier.width(s.dw(2.4f)))
            // WiFi bars + speed/band on two lines
            WifiBars(ui.wifiLevel, s)
            Spacer(Modifier.width(s.dw(0.8f)))
            Column {
                Text(if (ui.wifiMbps > 0) "${ui.wifiMbps} Mb" else "–", color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = s.sh(1.9f), maxLines = 1)
                Text(ui.wifiBand, color = Ark.Muted, fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium, fontSize = s.sh(1.9f), maxLines = 1)
            }
            Spacer(Modifier.width(s.dw(1.6f)))
            BatteryGlyph(ui.battPct, ui.battCharging, s)

            Spacer(Modifier.weight(1f))

            NavButton("Home", page == Page.HOME, s, Icons.Filled.Home) { onPage(Page.HOME) }
            NavButton("Info", page == Page.INFO, s, Icons.Filled.Info) { onPage(Page.INFO) }
            NavButton("7-day", page == Page.FORECAST, s, null) { onPage(Page.FORECAST) }
            Spacer(Modifier.width(s.dw(0.4f)))
            // Large tap target (48dp class) — a bare 3.4dh icon was too small for a finger.
            Box(
                Modifier.size(s.dh(5.6f)).clickable { onPage(Page.SETTINGS) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Settings, "Settings",
                    tint = if (page == Page.SETTINGS) Ark.Accent else Ark.Faint,
                    modifier = Modifier.size(s.dh(3.4f))
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(s.dh(0.14f)).background(Ark.TopBarLine))
    }
}

@Composable
private fun WifiBars(level: Int, s: Scale) {
    val heights = floatArrayOf(0.34f, 0.52f, 0.70f, 0.86f, 1.0f)
    val col = wifiColor(if (level < 1) 1 else level)
    Row(verticalAlignment = Alignment.Bottom) {
        for (i in 0 until 5) {
            Box(
                Modifier.width(s.dw(0.55f)).height(s.dh(2.8f * heights[i]))
                    .background(if (i < level) col else Ark.Faint.copy(alpha = 0.4f), RoundedCornerShape(1.dp))
            )
            if (i < 4) Spacer(Modifier.width(s.dw(0.35f)))
        }
    }
}

@Composable
private fun BatteryGlyph(pct: Int, charging: Boolean, s: Scale) {
    val fillCol = when { pct <= 15 -> Color(0xFFFF5C5C); pct <= 35 -> Ark.Warm; else -> Ark.Good }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(s.dw(2.6f)).height(s.dh(2.0f))
                .border(s.dh(0.18f), Ark.Muted, RoundedCornerShape(2.dp)).padding(s.dh(0.3f)),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(pct.coerceIn(0, 100) / 100f).background(fillCol, RoundedCornerShape(1.dp)))
        }
        Spacer(Modifier.width(s.dw(0.6f)))
        if (charging) {
            Text("⚡", color = Ark.Good, fontSize = s.sh(2f))
            Spacer(Modifier.width(s.dw(0.3f)))
        }
        Text("$pct %", color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = s.sh(2f), maxLines = 1)
    }
}

@Composable
private fun NavButton(label: String, active: Boolean, s: Scale, leading: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    val mod = if (active)
        Modifier.background(Brush.linearGradient(listOf(Color(0xFF3D7BFF), Ark.Accent)), RoundedCornerShape(10.dp))
    else Modifier
    Row(mod.clickable(onClick = onClick).height(s.dh(5.6f)).padding(horizontal = s.dw(1.3f)), verticalAlignment = Alignment.CenterVertically) {
        if (active && leading != null) {
            Icon(leading, null, tint = Color.White, modifier = Modifier.size(s.dh(2.6f)))
            Spacer(Modifier.width(s.dw(0.5f)))
        }
        Text(label, color = if (active) Color.White else Ark.Muted, fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = s.sh(2.2f), maxLines = 1)
    }
    Spacer(Modifier.width(s.dw(0.5f)))
}

/* ---------------- Home page ---------------- */

@Composable
private fun HomePage(ui: HomeUi, s: Scale) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            ClockCol(ui, s, Modifier.weight(1.42f).fillMaxHeight())
            Box(Modifier.fillMaxHeight().width(s.dh(0.12f)).background(Ark.Line))
            WeatherCol(ui, s, Modifier.weight(1f).fillMaxHeight())
        }
        SunMoonBand(ui, s)
    }
}

@Composable
private fun ClockCol(ui: HomeUi, s: Scale, modifier: Modifier) {
    val clock = rememberClockText()
    Column(
        modifier.padding(horizontal = s.dw(3f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(clock.time, color = Color(0xFFEAF3FF), fontFamily = BigShoulders, fontWeight = FontWeight.Bold, fontSize = s.sh(30f), maxLines = 1)
            Spacer(Modifier.width(s.dw(1f)))
            // Seconds as fixed-width digit slots → never overlaps for any value.
            Row(Modifier.padding(top = s.ds(2.2f)), verticalAlignment = Alignment.Top) {
                for (ch in clock.sec) {
                    Box(Modifier.width(s.ds(4.0f)), contentAlignment = Alignment.Center) {
                        Text(ch.toString(), color = Ark.Accent, fontFamily = BigShoulders, fontWeight = FontWeight.Bold, fontSize = s.sh(8.5f), maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(s.dh(2.2f)))
        Text(clock.date, color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = s.sh(4.4f), maxLines = 1)
    }
}

@Composable
private fun WeatherCol(ui: HomeUi, s: Scale, modifier: Modifier) {
    Column(
        modifier.padding(horizontal = s.dw(3.4f)),
        verticalArrangement = Arrangement.Center
    ) {
        if (ui.needsPlace) {
            Text(
                "No location set",
                color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = s.sh(3.6f)
            )
            Spacer(Modifier.height(s.dh(1.2f)))
            Text(
                "Open Settings (⚙) and search for your city to get weather.",
                color = Ark.Muted, fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium, fontSize = s.sh(2.6f)
            )
        } else {
            WeatherBlock("MET NORWAY (YR)", ui.met, Ark.Good, Ark.SourceText, s)
            Spacer(Modifier.height(s.dh(1.6f)))
            Box(Modifier.fillMaxWidth().height(s.dh(0.12f)).background(Ark.Line))
            Spacer(Modifier.height(s.dh(1.6f)))
            WeatherBlock("OPEN-METEO", ui.om, Ark.Cold, Ark.OpenMeteoText, s)
        }
    }
}

@Composable
private fun WeatherBlock(
    source: String,
    weather: WeatherUi?,
    sourceColor: Color,
    sourceTextColor: Color,
    s: Scale,
) {
    Column {
        Text(
            source, color = sourceTextColor, fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = s.sh(1.7f),
            modifier = Modifier.background(sourceColor, RoundedCornerShape(6.dp)).padding(horizontal = s.dw(0.7f), vertical = s.dh(0.35f))
        )
        Spacer(Modifier.height(s.dh(1.1f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AndroidView(
                factory = { org.jrs82.fsclock.WeatherIconView(it) },
                update = { v -> v.setCondition(weather?.condition) },
                modifier = Modifier.size(s.dh(11.5f))
            )
            Spacer(Modifier.width(s.dw(2.4f)))
            Column {
                Text(numUnit(weather?.t, 0, "°"), color = tempColor(weather?.t), fontFamily = BigShoulders, fontWeight = FontWeight.SemiBold, fontSize = s.sh(13f), maxLines = 1)
                Text(weather?.cond ?: "", color = Ark.Muted, fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium, fontSize = s.sh(2.5f), maxLines = 1)
            }
        }
        Spacer(Modifier.height(s.dh(1.3f)))
        WxRows(weather, s)
    }
}

@Composable
private fun WxRows(w: WeatherUi?, s: Scale) {
    Column(verticalArrangement = Arrangement.spacedBy(s.dh(0.9f))) {
        Row(horizontalArrangement = Arrangement.spacedBy(s.dw(2.4f))) {
            MetricCell("feels", "Feels", numUnit(w?.feels, 0, "°"), s, Modifier.weight(1f))
            MetricCell("wind", "Wind", numUnit(w?.wind, 0, " m/s"), s, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(s.dw(2.4f))) {
            MetricCell("hum", "Humidity", numUnit(w?.hum, 0, " %"), s, Modifier.weight(1f))
            MetricCell("rain", "Rain", numUnit(w?.precip, 1, " mm"), s, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCell(type: String, label: String, value: String, s: Scale, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        MetricIcon(type, Modifier.size(s.dh(2.3f)))
        Spacer(Modifier.width(s.dw(0.9f)))
        Text(label + " ", color = Ark.Muted, fontFamily = HankenGrotesk, fontSize = s.sh(2.3f))
        Text(value, color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = s.sh(2.3f), maxLines = 1)
    }
}

/* ---------------- Sun + moon band (bottom strip) ---------------- */

@Composable
private fun SunMoonBand(ui: HomeUi, s: Scale) {
    Column(
        Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(Ark.BandStart, Ark.BandEnd))
        )
    ) {
        Box(Modifier.fillMaxWidth().height(s.dh(0.14f)).background(Ark.Line))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = s.dw(3.4f), vertical = s.dh(2.4f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("☀", color = Ark.Warm, fontSize = s.sh(3.2f))
            Spacer(Modifier.width(s.dw(1.2f)))
            Text("Sunrise ", color = Ark.Muted, fontFamily = HankenGrotesk, fontSize = s.sh(2.6f))
            Text(ui.sunRise, color = Ark.Warm, fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = s.sh(2.6f))
            Text("  ·  Sunset ", color = Ark.Muted, fontFamily = HankenGrotesk, fontSize = s.sh(2.6f))
            Text(ui.sunSet, color = Ark.Warm, fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = s.sh(2.6f))
            if (ui.dayLen.isNotEmpty()) {
                Text("  ·  ", color = Ark.Muted, fontFamily = HankenGrotesk, fontSize = s.sh(2.6f))
                Text(ui.dayLen, color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = s.sh(2.6f))
            }
            if (ui.moonLabel.isNotEmpty()) {
                Spacer(Modifier.width(s.dw(4f)))
                Text("☾", color = Ark.Cold, fontSize = s.sh(3.0f))
                Spacer(Modifier.width(s.dw(1.2f)))
                Text(ui.moonLabel, color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = s.sh(2.6f), maxLines = 1)
                if (ui.moonIllum >= 0) {
                    Text("  ·  ${ui.moonIllum} %", color = Ark.Muted, fontFamily = HankenGrotesk, fontSize = s.sh(2.6f))
                }
            }
        }
    }
}

/* ---------------- Metric icons (Canvas, shared between pages) ---------------- */

@Composable
internal fun MetricIcon(type: String, modifier: Modifier) {
    val col = when (type) {
        "feels" -> Ark.Warm
        "wind" -> Ark.Cold
        "hum" -> Color(0xFF5AC8FF)
        else -> Color(0xFF7FB2FF)
    }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val st = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        when (type) {
            "feels" -> {
                drawLine(col, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.6f), strokeWidth = st.width, cap = StrokeCap.Round)
                drawCircle(col, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.74f))
            }
            "wind" -> {
                drawLine(col, Offset(w * 0.12f, h * 0.38f), Offset(w * 0.7f, h * 0.38f), strokeWidth = st.width, cap = StrokeCap.Round)
                drawLine(col, Offset(w * 0.12f, h * 0.62f), Offset(w * 0.78f, h * 0.62f), strokeWidth = st.width, cap = StrokeCap.Round)
                drawCircle(col, radius = w * 0.13f, center = Offset(w * 0.78f, h * 0.30f), style = st)
            }
            "hum" -> {
                drawCircle(col, radius = w * 0.26f, center = Offset(w * 0.5f, h * 0.62f), style = st)
                drawLine(col, Offset(w * 0.5f, h * 0.14f), Offset(w * 0.5f, h * 0.5f), strokeWidth = st.width, cap = StrokeCap.Round)
            }
            else -> {
                drawLine(col, Offset(w * 0.3f, h * 0.5f), Offset(w * 0.22f, h * 0.78f), strokeWidth = st.width, cap = StrokeCap.Round)
                drawLine(col, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.42f, h * 0.78f), strokeWidth = st.width, cap = StrokeCap.Round)
                drawLine(col, Offset(w * 0.7f, h * 0.5f), Offset(w * 0.62f, h * 0.78f), strokeWidth = st.width, cap = StrokeCap.Round)
                drawLine(col, Offset(w * 0.18f, h * 0.36f), Offset(w * 0.8f, h * 0.36f), strokeWidth = st.width, cap = StrokeCap.Round)
            }
        }
    }
}
