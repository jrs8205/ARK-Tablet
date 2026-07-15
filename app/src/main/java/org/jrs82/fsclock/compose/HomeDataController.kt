package org.jrs82.fsclock.compose

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.jrs82.fsclock.Astronomy
import org.jrs82.fsclock.GeocodingClient
import org.jrs82.fsclock.MetNorwayRepository
import org.jrs82.fsclock.OpenMeteoData
import org.jrs82.fsclock.OpenMeteoRepository
import org.jrs82.fsclock.SettingsManager
import org.jrs82.fsclock.WeatherData
import org.jrs82.fsclock.WeatherSnapshot
import org.jrs82.fsclock.WeatherTextFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Collects home screen data from the (Java) repositories and publishes it
 * as Compose state (uiState). All times use the device's local time zone.
 */
class HomeDataController(activityCtx: Context) {

    private val ctx: Context = activityCtx.applicationContext

    var uiState by mutableStateOf(HomeUi())
        private set

    private val ui = Handler(Looper.getMainLooper())
    private var io: ExecutorService? = null
    private val active = AtomicBoolean(false)
    private val generation = AtomicLong(0L)
    private var wifiTickCount = 0

    @Volatile private var metCache: WeatherData? = null
    @Volatile private var metCacheKey: String = ""
    @Volatile private var omCache: OpenMeteoData? = null

    fun start() {
        if (!active.compareAndSet(false, true)) return
        generation.incrementAndGet()
        SettingsManager.get().init(ctx)
        ensureMetCacheKey(SettingsManager.get())
        if (io == null) io = Executors.newSingleThreadExecutor()
        val sm = SettingsManager.get()
        update { it.copy(
            city = if (sm.hasPlace()) sm.homePlace else "—",
            country = sm.homeCountry,
            needsPlace = !sm.hasPlace()
        ) }
        ui.post(deviceTick)
        ui.post(weatherTick)
        ui.post(sunTick)
    }

    fun stop() {
        if (!active.compareAndSet(true, false)) return
        generation.incrementAndGet()
        ui.removeCallbacksAndMessages(null)
        io?.shutdownNow(); io = null
    }

    /** Posts a state update to the UI; skipped if the controller has been stopped/restarted. */
    private inline fun update(crossinline block: (HomeUi) -> HomeUi) {
        val run = generation.get()
        update(run, block)
    }

    private inline fun update(run: Long, crossinline block: (HomeUi) -> HomeUi) {
        ui.post { if (isCurrent(run)) uiState = block(uiState) }
    }

    private fun isCurrent(run: Long): Boolean = active.get() && generation.get() == run

    private fun executeCurrent(block: (Long) -> Unit) {
        if (!active.get() || io == null) return
        val run = generation.get()
        io?.execute {
            if (isCurrent(run)) block(run)
        }
    }

    // ---------------- Place ----------------

    /** Stores the selected place and refreshes weather + sun for it right away. */
    fun setPlace(place: PlaceUi) {
        SettingsManager.get().setPlace(place.name, place.country, place.lat, place.lon)
        update { it.copy(city = place.name, country = place.country, needsPlace = false) }
        resetMetCache(SettingsManager.get())
        fetchWeatherNow(forceOm = true)
        computeSun()
    }

    /** City search on the IO executor; the callback runs on the main thread
     *  (null = network/parse error, empty list = no matches). */
    fun searchCities(query: String, onResult: (List<PlaceUi>?) -> Unit) {
        val exec = io
        if (query.isBlank() || exec == null) { onResult(null); return }
        exec.execute {
            val result: List<PlaceUi>? = try {
                GeocodingClient.search(query).map {
                    PlaceUi(it.name, it.country, it.lat, it.lon, it.detail())
                }
            } catch (e: Exception) {
                Log.w(TAG, "geocoding", e); null
            }
            ui.post { onResult(result) }
        }
    }

    // ---------------- WiFi + battery + red tint ----------------

    private val deviceTick = object : Runnable {
        override fun run() {
            if (!active.get()) return
            if (wifiTickCount++ % 5 == 0) {
                pushWifi()
                pushRedTint()
            }
            pushBattery()
            ui.postDelayed(this, 1000L)
        }
    }

    private fun pushRedTint() {
        try {
            val sm = SettingsManager.get()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val morning = sm.morningHour
            val evening = sm.eveningHour
            val night = if (evening >= morning) hour >= evening || hour < morning
                        else hour >= evening && hour < morning
            val red = night && sm.isNightRedTint
            update { it.copy(redTint = red) }
        } catch (e: Exception) { Log.w(TAG, "redTint", e) }
    }

    @Suppress("DEPRECATION")
    private fun pushWifi() {
        var level = 0; var mbps = 0; var band = ""
        try {
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null && wm.isWifiEnabled) {
                val info = wm.connectionInfo
                if (info != null && info.supplicantState == SupplicantState.COMPLETED) {
                    val rssi = info.rssi
                    if (rssi != Int.MIN_VALUE && rssi > -127) {
                        level = WifiManager.calculateSignalLevel(rssi, 5) + 1
                        mbps = maxOf(0, info.linkSpeed)
                        band = bandLabel(info.frequency)
                    }
                }
            }
        } catch (e: Exception) { Log.w(TAG, "wifi", e) }
        val l = level; val m = mbps; val b = band
        update { it.copy(wifiLevel = l, wifiMbps = m, wifiBand = b) }
    }

    private fun pushBattery() {
        try {
            val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
            val lvl = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (lvl < 0 || scale <= 0) return
            val pct = Math.round(lvl * 100f / scale)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            update { it.copy(battPct = pct, battCharging = charging) }
        } catch (e: Exception) { Log.w(TAG, "battery", e) }
    }

    // ---------------- Weather (MET Norway + Open-Meteo) ----------------

    private val weatherTick = object : Runnable {
        override fun run() {
            if (!active.get()) return
            fetchWeatherNow(forceOm = false)
            ui.postDelayed(this, 10L * 60_000L)
        }
    }

    private fun fetchWeatherNow(forceOm: Boolean) {
        executeCurrent { run ->
            val sm = SettingsManager.get()
            if (!sm.hasPlace()) {
                update(run) { it.copy(needsPlace = true) }
                return@executeCurrent
            }
            val place = sm.homePlace
            val lat = sm.homeLatitude
            val lon = sm.homeLongitude
            val key = ensureMetCacheKey(sm)
            try {
                val wd = MetNorwayRepository.get(ctx).fetch(lat, lon)
                if (!isCurrent(run) || weatherCacheKey(SettingsManager.get()) != key) return@executeCurrent
                metCache = wd
                metCacheKey = key
                val fc = buildForecast()
                update(run) { it.copy(met = snapshotToUi(WeatherSnapshot.fromMet(wd.current, place, wd.fetchedAt)), forecast = fc) }
            } catch (e: Exception) { Log.w(TAG, "met", e) }
            if (!isCurrent(run) || weatherCacheKey(SettingsManager.get()) != key) return@executeCurrent
            try {
                val om = OpenMeteoRepository.get(ctx).fetch(place, lat, lon, forceOm)
                if (!isCurrent(run) || weatherCacheKey(SettingsManager.get()) != key) return@executeCurrent
                omCache = om
                val h = nearestHour(om)
                val fc = buildForecast()
                update(run) { it.copy(
                    om = if (h != null) snapshotToUi(WeatherSnapshot.fromOpenMeteo(h, place, om.fetchedAt)) else it.om,
                    forecast = fc
                ) }
            } catch (e: Exception) { Log.w(TAG, "om", e) }
        }
    }

    // ---------------- 7-day forecast ----------------

    /** Groups MET Norway and Open-Meteo hours into days. */
    private fun buildForecast(): List<DayForecastUi> {
        val metByDay = sortedMapOf<Int, MutableMap<Int, HourRowUi>>()
        val omByDay = sortedMapOf<Int, MutableMap<Int, HourRowUi>>()
        val dayTs = HashMap<Int, Long>()
        metCache?.let { wd ->
            for (h in wd.hours) {
                val key = dayKey(h.timestamp)
                val wind = if (!h.windSpeed.isNaN()) h.windSpeed else h.windGust
                metByDay.getOrPut(key) { HashMap() }[h.hour] = HourRowUi(
                    h.hour, nanToNull(h.temperature), null, nanToNull(wind), null,
                    nanToNull(h.precipitation), h.condition
                )
                if (!dayTs.containsKey(key)) dayTs[key] = h.timestamp
            }
        }
        omCache?.let { om ->
            for (h in om.hours) {
                val key = dayKey(h.timestamp)
                omByDay.getOrPut(key) { HashMap() }[h.hour] = HourRowUi(
                    h.hour, h.temperature?.toFloat(), h.feelsLike?.toFloat(), h.windSpeed?.toFloat(),
                    h.humidity?.toFloat(), h.precipitation?.toFloat(), h.condition
                )
                if (!dayTs.containsKey(key)) dayTs[key] = h.timestamp
            }
        }
        val dayKeys = sortedSetOf<Int>()
        dayKeys.addAll(metByDay.keys)
        dayKeys.addAll(omByDay.keys)
        val fmt = SimpleDateFormat("EEE d MMM", Locale.ENGLISH)
        val out = ArrayList<DayForecastUi>()
        for (dayK in dayKeys) {
            if (out.size >= 7) break
            val ts = dayTs[dayK] ?: continue
            val label = fmt.format(Date(ts))
            val metMap = metByDay[dayK] ?: emptyMap()
            val omMap = omByDay[dayK] ?: emptyMap()
            val hours = (metMap.keys + omMap.keys).toSortedSet().toList()
            out.add(DayForecastUi(label, metMap, omMap, hours))
        }
        return out
    }

    private fun dayKey(ts: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = ts
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }

    private fun nanToNull(v: Double): Float? = if (v.isNaN()) null else v.toFloat()

    private fun ensureMetCacheKey(sm: SettingsManager): String {
        val key = weatherCacheKey(sm)
        if (metCacheKey != key) {
            metCache = null
            metCacheKey = key
        }
        return key
    }

    private fun resetMetCache(sm: SettingsManager) {
        metCache = null
        omCache = null
        metCacheKey = weatherCacheKey(sm)
    }

    private fun weatherCacheKey(sm: SettingsManager): String {
        val place = sm.homePlace.trim().lowercase(Locale.ROOT)
        return if (sm.hasHomeCoordinates())
            "$place|${sm.homeLatitude}|${sm.homeLongitude}"
        else
            "$place|name"
    }

    private fun snapshotToUi(snap: WeatherSnapshot?): WeatherUi? {
        if (snap == null) return null
        val cond = try { if (snap.condition != null) WeatherTextFormatter.label(ctx, snap.condition) else "" } catch (e: Exception) { "" }
        return WeatherUi(
            t = snap.temperature?.toFloat(),
            cond = cond ?: "",
            feels = snap.feelsLike?.toFloat(),
            wind = snap.windSpeed?.toFloat(),
            hum = snap.humidity?.toFloat(),
            precip = snap.precip1h?.toFloat(),
            condition = snap.condition
        )
    }

    private fun nearestHour(om: OpenMeteoData?): OpenMeteoData.Hour? {
        if (om == null) return null
        val now = System.currentTimeMillis()
        var best = Long.MAX_VALUE; var bestH: OpenMeteoData.Hour? = null
        for (h in om.hours) {
            val diff = Math.abs(h.timestamp - now)
            if (diff < best && diff <= 30L * 60_000L) { best = diff; bestH = h }
        }
        return bestH
    }

    // ---------------- Sun + moon ----------------

    private val sunTick = object : Runnable {
        override fun run() { if (!active.get()) return; computeSun(); ui.postDelayed(this, 60L * 60_000L) }
    }

    private fun computeSun() {
        try {
            val sm = SettingsManager.get()
            if (!sm.hasHomeCoordinates()) return
            val astro = Astronomy.calculate(Date(), sm.homeLatitude, sm.homeLongitude, TimeZone.getDefault())
            update { it.copy(
                sunRise = Astronomy.formatSunrise(astro.sun) ?: "—",
                sunSet = Astronomy.formatSunset(astro.sun) ?: "—",
                dayLen = Astronomy.formatDayLength(astro.sun) ?: "",
                sunriseMin = astro.sun.sunriseMinutes,
                sunsetMin = astro.sun.sunsetMinutes,
                moonLabel = astro.moon.label ?: "",
                moonIllum = Math.round(astro.moon.illumination * 100.0).toInt(),
                moonPhase = astro.moon.phase.toFloat()
            ) }
        } catch (e: Exception) { Log.w(TAG, "sun", e) }
    }

    // ---------------- Device location ("Use device location") ----------------

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** One-shot device locate + reverse geocode; stores the result as the place.
     *  The callback runs on the main thread with true on success. */
    fun useDeviceLocation(onResult: (Boolean) -> Unit) {
        if (!hasLocationPermission()) { onResult(false); return }
        val exec = io
        if (exec == null) { onResult(false); return }
        exec.execute {
            var ok = false
            try {
                val loc = bestLocation()
                if (loc != null) {
                    val pair = reverseGeocode(loc.latitude, loc.longitude)
                    if (pair != null) {
                        ui.post {
                            setPlace(PlaceUi(pair.first, pair.second, loc.latitude, loc.longitude))
                        }
                        ok = true
                    }
                }
            } catch (e: Exception) { Log.w(TAG, "location", e) }
            val success = ok
            ui.post { onResult(success) }
        }
    }

    @SuppressLint("MissingPermission") // checked via hasLocationPermission()
    private fun bestLocation(): Location? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var best: Location? = null
        for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.time > best.time) best = l
            } catch (_: Exception) {}
        }
        val cached = best
        if (cached != null && isUsable(cached)) return cached
        return requestFresh(lm)?.takeIf { isUsable(it) } ?: cached?.takeIf { isUsable(it) }
    }

    private fun isUsable(l: Location): Boolean {
        val ageOk = System.currentTimeMillis() - l.time < 60L * 60_000L
        val accOk = !l.hasAccuracy() || l.accuracy <= 1500f
        return ageOk && accOk
    }

    @SuppressLint("MissingPermission")
    private fun requestFresh(lm: LocationManager): Location? {
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<Location>(1)
        try {
            lm.getCurrentLocation(provider, CancellationSignal(), ctx.mainExecutor) { loc ->
                holder[0] = loc; latch.countDown()
            }
            latch.await(8L, TimeUnit.SECONDS)
        } catch (e: Exception) { Log.w(TAG, "getCurrentLocation", e) }
        return holder[0]
    }

    /** Android Geocoder → (city, country). null when the geocoder is missing or has no result. */
    @Suppress("DEPRECATION")
    private fun reverseGeocode(lat: Double, lon: Double): Pair<String, String>? {
        if (!Geocoder.isPresent()) return null
        return try {
            val geo = Geocoder(ctx, Locale.ENGLISH)
            val addrs = geo.getFromLocation(lat, lon, 1) ?: return null
            if (addrs.isEmpty()) return null
            val a = addrs[0]
            var city = a.locality
            if (city.isNullOrBlank()) city = a.subAdminArea
            if (city.isNullOrBlank()) city = a.adminArea
            if (city.isNullOrBlank()) return null
            Pair(city, a.countryName ?: "")
        } catch (e: Exception) { Log.w(TAG, "geocode", e); null }
    }

    companion object {
        private const val TAG = "HomeDataController"
        private fun bandLabel(freqMhz: Int): String = when {
            freqMhz <= 0 -> ""
            freqMhz < 2500 -> "2.4 GHz"
            freqMhz < 5925 -> "5 GHz"
            else -> "6 GHz"
        }
    }
}
