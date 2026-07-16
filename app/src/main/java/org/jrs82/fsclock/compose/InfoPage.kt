package org.jrs82.fsclock.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/* ---------------- Info page: sun + moon + calendar ---------------- */

@Composable
fun InfoPage(ui: HomeUi, s: Scale, onRequestCalendar: () -> Unit = {}) {
    Row(Modifier.fillMaxSize().padding(horizontal = s.dw(3f), vertical = s.dh(3f))) {
        SunCard(ui, s, Modifier.weight(1.25f).fillMaxHeight())
        Spacer(Modifier.width(s.dw(3f)))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            MoonCard(ui, s, Modifier.fillMaxWidth().weight(0.4f))
            Spacer(Modifier.height(s.dh(3f)))
            CalendarCard(ui, s, onRequestCalendar, Modifier.fillMaxWidth().weight(0.6f))
        }
    }
}

@Composable
private fun SunCard(ui: HomeUi, s: Scale, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().background(Ark.Panel, RoundedCornerShape(20.dp))
            .border(s.dh(0.16f), Ark.Line, RoundedCornerShape(20.dp)).padding(s.dw(2.4f))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☀", color = Ark.Warm, fontSize = s.sh(4.2f))
            Spacer(Modifier.width(s.dw(1.2f)))
            Text("Sun", color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = s.sh(3.4f))
        }
        Spacer(Modifier.height(s.dh(1.6f)))
        // Sun arc
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
            SunArc(ui.sunriseMin, ui.sunsetMin, s, Modifier.fillMaxWidth().fillMaxHeight())
        }
        Spacer(Modifier.height(s.dh(1.6f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SunStat("Sunrise", TimeFormat.minutesOfDay(ui.sunriseMin, ui.twelveHour), Ark.Warm, s)
            SunStat("Day length", ui.dayLen, Ark.Ink, s)
            SunStat("Sunset", TimeFormat.minutesOfDay(ui.sunsetMin, ui.twelveHour), Ark.Cold, s)
        }
    }
}

@Composable
private fun SunStat(label: String, value: String, color: Color, s: Scale) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Ark.Muted, fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium, fontSize = s.sh(2.1f))
        Spacer(Modifier.height(s.dh(0.4f)))
        Text(value, color = color, fontFamily = BigShoulders, fontWeight = FontWeight.Bold, fontSize = s.sh(4.4f), maxLines = 1)
    }
}

/** Semicircular arc on which the sun moves between sunrise and sunset based on the current time. */
@Composable
private fun SunArc(sunriseMin: Int, sunsetMin: Int, s: Scale, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val baseY = h * 0.94f
        val cx = w / 2f
        val rx = w * 0.44f
        val ry = h * 0.78f
        // Ground line
        drawLine(Ark.Line, Offset(w * 0.04f, baseY), Offset(w * 0.96f, baseY), strokeWidth = h * 0.012f, cap = StrokeCap.Round)
        // Arc (dashed look, drawn as segments)
        val steps = 48
        var prev: Offset? = null
        for (i in 0..steps) {
            val frac = i / steps.toFloat()
            val ang = Math.PI * (1.0 - frac) // 180° → 0°
            val x = cx + (rx * cos(ang)).toFloat()
            val y = baseY - (ry * sin(ang)).toFloat()
            val p = Offset(x, y)
            if (prev != null && i % 2 == 0) {
                drawLine(Ark.Faint.copy(alpha = 0.5f), prev!!, p, strokeWidth = h * 0.01f, cap = StrokeCap.Round)
            }
            prev = p
        }
        // Current sun position
        if (sunriseMin in 0..1440 && sunsetMin in 0..1440 && sunsetMin > sunriseMin) {
            val nowMin = nowMinutes()
            val frac = ((nowMin - sunriseMin).toFloat() / (sunsetMin - sunriseMin)).coerceIn(0f, 1f)
            val ang = Math.PI * (1.0 - frac)
            val x = cx + (rx * cos(ang)).toFloat()
            val y = baseY - (ry * sin(ang)).toFloat()
            val up = nowMin in sunriseMin..sunsetMin
            val col = if (up) Ark.Warm else Ark.Faint
            drawCircle(col.copy(alpha = 0.25f), radius = h * 0.11f, center = Offset(x, y))
            drawCircle(col, radius = h * 0.07f, center = Offset(x, y))
        }
    }
}

@Composable
private fun MoonCard(ui: HomeUi, s: Scale, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().background(Ark.Panel, RoundedCornerShape(20.dp))
            .border(s.dh(0.16f), Ark.Line, RoundedCornerShape(20.dp)).padding(s.dw(2.4f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MoonGlyph(ui.moonPhase, ui.moonIllum, Modifier.size(s.dh(13f)))
        Spacer(Modifier.width(s.dw(2.4f)))
        Column {
            Text("Moon", color = Ark.Muted, fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium, fontSize = s.sh(2.3f))
            Spacer(Modifier.height(s.dh(0.4f)))
            Text(
                ui.moonLabel.ifEmpty { "—" }.replaceFirstChar { it.uppercase() },
                color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = s.sh(3.4f), maxLines = 2
            )
            if (ui.moonIllum >= 0) {
                Spacer(Modifier.height(s.dh(0.6f)))
                Text("Illumination ${ui.moonIllum} %", color = Ark.Muted, fontFamily = HankenGrotesk, fontSize = s.sh(2.3f))
            }
        }
    }
}

/** Upcoming calendar events (next 48 h); asks for READ_CALENDAR on demand. */
@Composable
private fun CalendarCard(ui: HomeUi, s: Scale, onRequestCalendar: () -> Unit, modifier: Modifier) {
    Column(
        modifier.background(Ark.Panel, RoundedCornerShape(20.dp))
            .border(s.dh(0.16f), Ark.Line, RoundedCornerShape(20.dp)).padding(s.dw(2.4f))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🗓", fontSize = s.sh(3.6f))
            Spacer(Modifier.width(s.dw(1.2f)))
            Text("Calendar", color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = s.sh(3.4f))
        }
        Spacer(Modifier.height(s.dh(1.4f)))
        when {
            !ui.calendarPermGranted -> {
                Text(
                    "Show your upcoming events here.",
                    color = Ark.Muted, fontFamily = HankenGrotesk, fontSize = s.sh(2.3f)
                )
                Spacer(Modifier.height(s.dh(1.2f)))
                ActionButton("Allow calendar access", s) { onRequestCalendar() }
            }
            ui.calendarEvents.isEmpty() -> {
                Text(
                    "No events in the next 2 days.",
                    color = Ark.Muted, fontFamily = HankenGrotesk, fontSize = s.sh(2.3f)
                )
            }
            else -> {
                for (e in ui.calendarEvents) EventRow(e, ui.twelveHour, s)
            }
        }
    }
}

@Composable
private fun EventRow(e: CalendarEventUi, twelveHour: Boolean, s: Scale) {
    val timeText = if (e.allDay) {
        java.text.SimpleDateFormat("EEE", java.util.Locale.ENGLISH).format(java.util.Date(e.beginTs)) + " all day"
    } else {
        java.text.SimpleDateFormat(if (twelveHour) "EEE h:mm a" else "EEE HH:mm", java.util.Locale.ENGLISH)
            .format(java.util.Date(e.beginTs))
    }
    Row(Modifier.fillMaxWidth().padding(vertical = s.dh(0.55f)), verticalAlignment = Alignment.CenterVertically) {
        Text(
            timeText, color = Ark.Accent, fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold,
            fontSize = s.sh(2.2f), maxLines = 1, modifier = Modifier.width(s.dw(9.5f))
        )
        Spacer(Modifier.width(s.dw(1f)))
        Text(
            e.title, color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold,
            fontSize = s.sh(2.3f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/** Moon phase: correct terminator — lit half + ellipse in the middle.
 *  Phase p: 0 = new moon, 0.25 = first quarter (light on the right), 0.5 = full moon,
 *  0.75 = last quarter (light on the left), as seen from the northern hemisphere. */
@Composable
private fun MoonGlyph(phase: Float, illum: Int, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val lit = Color(0xFFEBE8C8)
        val dark = Color(0xFF1B2330)
        drawCircle(dark, radius = r, center = center)
        if (phase in 0f..1f) {
            val p = phase
            val waxing = p < 0.5f
            val k = cos(2.0 * Math.PI * p).toFloat()   // 1=new, 0=quarter, -1=full
            val litFraction = (1f - k) / 2f
            val ew = abs(k) * r                          // half-width of the terminator ellipse
            val circle = Path().apply { addOval(Rect(center.x - r, center.y - r, center.x + r, center.y + r)) }
            clipPath(circle) {
                // Lit half: a waxing moon lights the right side, a waning one the left.
                val halfLeft = if (waxing) center.x else center.x - r
                drawRect(lit, topLeft = Offset(halfLeft, center.y - r), size = Size(r, 2f * r))
                // Terminator ellipse: lit in the gibbous phase, dark in the crescent phase.
                drawOval(
                    if (litFraction > 0.5f) lit else dark,
                    topLeft = Offset(center.x - ew, center.y - r),
                    size = Size(2f * ew, 2f * r)
                )
            }
        } else if (illum >= 0) {
            // Phase unknown but illumination is known — show a flat disc.
            drawCircle(lit.copy(alpha = (illum / 100f).coerceIn(0.1f, 1f)), radius = r, center = center)
        }
        // rim
        drawCircle(Ark.Faint.copy(alpha = 0.5f), radius = r, center = center, style = Stroke(width = r * 0.05f))
    }
}

private fun nowMinutes(): Int {
    val c = java.util.Calendar.getInstance()
    return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
}
