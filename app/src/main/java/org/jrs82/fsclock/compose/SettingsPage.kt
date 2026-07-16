package org.jrs82.fsclock.compose

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jrs82.fsclock.SettingsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ---------------- Settings page ----------------
 * High contrast: large Ink texts on a dark panel → readable in both bright
 * and dark conditions. */

@Composable
fun SettingsPage(
    ui: HomeUi,
    s: Scale,
    onBrightnessChanged: () -> Unit,
    onSearchCities: (String, (List<PlaceUi>?) -> Unit) -> Unit,
    onPickPlace: (PlaceUi) -> Unit,
    onUseDeviceLocation: ((LocationOutcome) -> Unit) -> Unit,
    onTimeFormatChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val sm = remember { SettingsManager.get().also { it.init(ctx.applicationContext) } }

    Row(Modifier.fillMaxSize().padding(horizontal = s.dw(2.6f), vertical = s.dh(2f))) {
        // Left column: display and brightness
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(s.dh(2f))
        ) {
            DisplaySection(sm, s, onBrightnessChanged, onTimeFormatChanged)
        }
        Spacer(Modifier.width(s.dw(2.4f)))
        // Right column: location + app info
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(s.dh(2f))
        ) {
            LocationSection(sm, ui.locationPermGranted, s, onSearchCities, onPickPlace, onUseDeviceLocation)
            AppInfoSection(sm, ui.twelveHour, s, ctx)
        }
    }
}

/* ---------------- Shared building blocks ---------------- */

@Composable
internal fun SectionCard(title: String, s: Scale, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Ark.Panel, RoundedCornerShape(18.dp))
            .border(s.dh(0.16f), Ark.Line, RoundedCornerShape(18.dp))
            .padding(horizontal = s.dw(2f), vertical = s.dh(1.8f))
    ) {
        Text(title.uppercase(), color = Ark.Accent, fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = s.sh(2.1f))
        Spacer(Modifier.height(s.dh(1.2f)))
        content()
    }
}

@Composable
private fun RowLabel(title: String, subtitle: String?, s: Scale, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = s.sh(2.5f))
        if (!subtitle.isNullOrEmpty()) {
            Text(subtitle, color = Ark.Muted, fontFamily = HankenGrotesk, fontSize = s.sh(1.9f))
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, s: Scale, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = s.dh(0.7f)), verticalAlignment = Alignment.CenterVertically) {
        RowLabel(title, subtitle, s, Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Ark.Accent, checkedThumbColor = Color.White,
                uncheckedTrackColor = Ark.Line, uncheckedThumbColor = Ark.Muted
            )
        )
    }
}

@Composable
internal fun ActionButton(label: String, s: Scale, accent: Color = Ark.Accent, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = s.dh(0.5f))
            .background(if (enabled) accent.copy(alpha = 0.16f) else Ark.Line.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .border(s.dh(0.14f), if (enabled) accent else Ark.Line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = s.dh(1.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Ark.Ink else Ark.Faint, fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = s.sh(2.3f))
    }
}

/* ---------------- Display and brightness ---------------- */

@Composable
private fun DisplaySection(sm: SettingsManager, s: Scale, onBrightnessChanged: () -> Unit, onTimeFormatChanged: () -> Unit) {
    var day by remember { mutableStateOf(sm.dayBrightness.toFloat()) }
    var night by remember { mutableStateOf(sm.nightBrightness.toFloat()) }
    var morning by remember { mutableStateOf(sm.morningHour) }
    var evening by remember { mutableStateOf(sm.eveningHour) }
    var redTint by remember { mutableStateOf(sm.isNightRedTint) }
    var twelve by remember { mutableStateOf(sm.isTwelveHourClock) }

    SectionCard("Display & brightness", s) {
        BrightnessSlider("Day brightness", day, s) { v, done ->
            day = v
            if (done) { sm.dayBrightness = v.toInt(); onBrightnessChanged() }
        }
        Spacer(Modifier.height(s.dh(1f)))
        BrightnessSlider("Night brightness", night, s) { v, done ->
            night = v
            if (done) { sm.nightBrightness = v.toInt(); onBrightnessChanged() }
        }
        Spacer(Modifier.height(s.dh(1.2f)))
        HourStepper("Day starts", morning, twelve, s) { h ->
            morning = h; sm.morningHour = h; onBrightnessChanged()
        }
        Spacer(Modifier.height(s.dh(0.8f)))
        HourStepper("Night starts", evening, twelve, s) { h ->
            evening = h; sm.eveningHour = h; onBrightnessChanged()
        }
        Spacer(Modifier.height(s.dh(0.6f)))
        SettingSwitch("Night red tint", "Adds a warm red filter during night brightness", redTint, s) {
            redTint = it; sm.setNightRedTint(it)
        }
        Spacer(Modifier.height(s.dh(0.6f)))
        TimeFormatSelector(twelve, s) { v ->
            twelve = v; sm.setTwelveHourClock(v); onTimeFormatChanged()
        }
    }
}

@Composable
private fun TimeFormatSelector(twelve: Boolean, s: Scale, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = s.dh(0.7f)), verticalAlignment = Alignment.CenterVertically) {
        RowLabel("Time format", null, s, Modifier.weight(1f))
        SegmentChip("24 h", !twelve, s) { onChange(false) }
        Spacer(Modifier.width(s.dw(0.8f)))
        SegmentChip("12 h AM/PM", twelve, s) { onChange(true) }
    }
}

@Composable
private fun SegmentChip(label: String, active: Boolean, s: Scale, onClick: () -> Unit) {
    Box(
        Modifier.background(if (active) Ark.Accent else Ark.SensorPanel, RoundedCornerShape(10.dp))
            .border(s.dh(0.14f), if (active) Ark.Accent else Ark.Line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = s.dw(1.3f), vertical = s.dh(1f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = if (active) Color(0xFF06222B) else Ark.Muted,
            fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = s.sh(2.1f), maxLines = 1
        )
    }
}

@Composable
private fun BrightnessSlider(label: String, value: Float, s: Scale, onChange: (Float, Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = s.sh(2.5f), modifier = Modifier.weight(1f))
            Text("${value.toInt()} %", color = Ark.Accent, fontFamily = BigShoulders, fontWeight = FontWeight.Bold, fontSize = s.sh(3.4f))
        }
        Slider(
            value = value,
            onValueChange = { onChange(it, false) },
            onValueChangeFinished = { onChange(value, true) },
            valueRange = 1f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Ark.Accent, activeTrackColor = Ark.Accent, inactiveTrackColor = Ark.Line
            )
        )
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, twelveHour: Boolean, s: Scale, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = s.sh(2.5f), modifier = Modifier.weight(1f))
        StepButton("−", s) { onChange((hour + 23) % 24) }
        Text(
            TimeFormat.stepperHour(hour, twelveHour), color = Ark.Ink, fontFamily = BigShoulders, fontWeight = FontWeight.Bold,
            fontSize = s.sh(3.4f), modifier = Modifier.padding(horizontal = s.dw(1.2f))
        )
        StepButton("+", s) { onChange((hour + 1) % 24) }
    }
}

@Composable
private fun StepButton(glyph: String, s: Scale, onClick: () -> Unit) {
    Box(
        Modifier.size(s.dh(5.2f)).background(Ark.SensorPanel, CircleShape)
            .border(s.dh(0.14f), Ark.Line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = Ark.Accent, fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = s.sh(3.2f))
    }
}

/* ---------------- Location ---------------- */

@Composable
private fun LocationSection(
    sm: SettingsManager,
    permGranted: Boolean,
    s: Scale,
    onSearchCities: (String, (List<PlaceUi>?) -> Unit) -> Unit,
    onPickPlace: (PlaceUi) -> Unit,
    onUseDeviceLocation: ((LocationOutcome) -> Unit) -> Unit,
) {
    var placeText by remember { mutableStateOf(currentPlaceText(sm)) }
    var searchOpen by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<LocationOutcome?>(null) }

    SectionCard("Location", s) {
        InfoRow("Current place", placeText, s)
        Row(Modifier.fillMaxWidth().padding(vertical = s.dh(0.6f)), verticalAlignment = Alignment.CenterVertically) {
            Text("Location permission", color = Ark.Muted, fontFamily = HankenGrotesk, fontSize = s.sh(2.3f), modifier = Modifier.weight(1f))
            Text(
                if (permGranted) "Granted" else "Not granted",
                color = if (permGranted) Ark.Good else Ark.Warn,
                fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = s.sh(2.3f)
            )
        }
        if (!permGranted) {
            Text(
                "Set the location with city search, or tap \"Use device location\" to grant the permission.",
                color = Ark.Muted, fontFamily = HankenGrotesk, fontSize = s.sh(1.9f)
            )
        }
        Spacer(Modifier.height(s.dh(0.6f)))
        ActionButton("Search city", s) { searchOpen = true }
        ActionButton(if (locating) "Locating…" else "Use device location", s, enabled = !locating) {
            locating = true; locationError = null
            onUseDeviceLocation { outcome ->
                locating = false
                locationError = if (outcome == LocationOutcome.SUCCESS) null else outcome
                if (outcome == LocationOutcome.SUCCESS) placeText = currentPlaceText(sm)
            }
        }
        locationError?.let { err ->
            Text(
                when (err) {
                    LocationOutcome.PERMISSION_DENIED ->
                        "Location permission was denied. Tap the button to try again, or use city search."
                    LocationOutcome.PERMISSION_DENIED_FOREVER ->
                        "Location permission is blocked. Allow Location for ARK-Tablet in Android app settings, or use city search."
                    else ->
                        "Could not resolve the device location. Check that location is enabled, or use city search."
                },
                color = Ark.Warn, fontFamily = HankenGrotesk, fontSize = s.sh(1.9f),
                modifier = Modifier.padding(top = s.dh(0.4f))
            )
        }
    }

    if (searchOpen) {
        CitySearchDialog(s, onSearchCities,
            onPick = { place ->
                onPickPlace(place)
                placeText = place.name + (if (place.country.isNotEmpty()) ", " + place.country else "")
                searchOpen = false
            },
            onDismiss = { searchOpen = false })
    }
}

private fun currentPlaceText(sm: SettingsManager): String {
    if (!sm.hasPlace()) return "Not set"
    val country = sm.homeCountry
    return sm.homePlace + (if (country.isNotEmpty()) ", $country" else "")
}

@Composable
internal fun CitySearchDialog(
    s: Scale,
    onSearchCities: (String, (List<PlaceUi>?) -> Unit) -> Unit,
    onPick: (PlaceUi) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<PlaceUi>?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun runSearch() {
        if (query.isBlank() || searching) return
        searching = true; failed = false
        onSearchCities(query) { list ->
            searching = false
            failed = list == null
            results = list
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search city") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("e.g. Hamburg") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(s.dw(1f)))
                    TextButton(onClick = { runSearch() }, enabled = query.isNotBlank() && !searching) {
                        Text(if (searching) "…" else "Search")
                    }
                }
                Spacer(Modifier.height(s.dh(1f)))
                Column(Modifier.fillMaxWidth().heightIn(max = s.dh(38f)).verticalScroll(rememberScrollState())) {
                    when {
                        failed -> Text("Search failed — check the network connection and try again.")
                        results != null && results!!.isEmpty() -> Text("No matches. Try a different spelling.")
                        results != null -> {
                            for (place in results!!) {
                                Column(
                                    Modifier.fillMaxWidth().clickable { onPick(place) }.padding(vertical = s.dh(1f))
                                ) {
                                    Text(place.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (place.detail.isNotEmpty()) {
                                        Text(place.detail, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/* ---------------- App ---------------- */

@Composable
private fun AppInfoSection(sm: SettingsManager, twelveHour: Boolean, s: Scale, ctx: android.content.Context) {
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }
    }
    val lastUpdate = remember(twelveHour) {
        val ts = sm.lastWeatherUpdate
        val pattern = if (twelveHour) "d MMM yyyy h:mm a" else "d MMM yyyy HH:mm"
        if (ts <= 0) "—" else SimpleDateFormat(pattern, Locale.ENGLISH).format(Date(ts))
    }
    SectionCard("Application", s) {
        InfoRow("Version", version, s)
        InfoRow("Last weather update", lastUpdate, s)
        Spacer(Modifier.height(s.dh(0.8f)))
        Text(
            "Weather data: MET Norway (CC BY 4.0) · Open-Meteo\nCity search: Open-Meteo Geocoding API",
            color = Ark.Muted, fontFamily = HankenGrotesk, fontSize = s.sh(1.9f)
        )
    }
}

@Composable
internal fun InfoRow(label: String, value: String, s: Scale) {
    Row(Modifier.fillMaxWidth().padding(vertical = s.dh(0.6f)), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ark.Muted, fontFamily = HankenGrotesk, fontSize = s.sh(2.3f), modifier = Modifier.weight(1f))
        Text(value, color = Ark.Ink, fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = s.sh(2.3f))
    }
}
