# ARK-Tablet

**Arkikeskus for tablets** — a full-screen, always-on home information display for
Android tablets. Built for 24/7 use as a kitchen/hallway info board: clock, Finnish
weather from two sources, electricity spot prices, RuuviTag sensors, weather warnings,
sun and moon data, and long-term history — all on a single landscape screen with no
ads, no accounts, and no cloud backend.

The user interface is in Finnish, and the data sources are Finland-specific.

## Features

- **Clock and date** — large always-visible clock with fixed-width digits and the next
  Finnish public holiday.
- **Weather** — current conditions and hourly forecasts from both FMI (Finnish
  Meteorological Institute open data) and Open-Meteo, shown side by side for easy
  comparison. 7-day forecast page with an hourly breakdown per day.
- **Electricity spot prices** — Nord Pool day-ahead prices via the Elering API in
  15-minute resolution: current price in the header, plus a dedicated page with
  today/tomorrow views, cheapest/most expensive slots, a color-coded chart and a
  scrolling slot list.
- **RuuviTag sensors** — up to three Bluetooth LE sensors (RAWv2 format) with custom
  names, live temperature/humidity readings and history.
- **Weather warnings** — official warnings with full descriptions and automatic
  scrolling.
- **Info page** — sun arc with sunrise/sunset times (NOAA solar equations) and the
  current moon phase rendered with a correct terminator.
- **History** — local Room database collects battery, weather and sensor samples;
  monthly min/max charts, daily statistics and CSV export. Configurable retention.
- **Built for 24/7 operation** — screen kept on, scheduled day/night brightness with
  an optional night red tint, pixel shift to prevent OLED/LCD burn-in, and location
  that can follow GPS (with reverse geocoding down to district level via the National
  Land Survey of Finland API).

## Pages

Home · Info · 7-day forecast · Electricity · Settings · History — navigated with the
top-bar buttons of a single-activity Jetpack Compose UI.

## Devices

Designed for landscape tablets in both 4:3 and 16:10 aspect ratios. Developed and
tested on a Samsung Galaxy Tab S2 9.7 (Android 11) and a Galaxy Tab A9+ (Android 14).
Requires Android 11 (API 30) or newer.

## Building

Requirements: JDK 17+ and the Android SDK.

Create `local.properties` in the project root:

```properties
sdk.dir=/path/to/Android/Sdk

# Optional: enables district-level reverse geocoding (free key from
# the National Land Survey of Finland developer portal). The app works
# without it; location then falls back to the Android Geocoder.
MML_API_KEY=your-key

# Optional: release signing (uses release.keystore in the project root).
# Without these, assembleRelease produces an unsigned APK.
KEYSTORE_PASSWORD=...
KEY_PASSWORD=...
```

Then:

```bash
./gradlew :app:assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`.

## Data sources

| Source | Used for |
|---|---|
| [FMI open data](https://en.ilmatieteenlaitos.fi/open-data) (WFS) | Observations, forecasts, weather warnings |
| [Open-Meteo](https://open-meteo.com/) | Comparison forecast |
| [Elering](https://dashboard.elering.ee/) | Nord Pool electricity spot prices |
| [National Land Survey of Finland](https://www.maanmittauslaitos.fi/en) | Reverse geocoding |
| RuuviTag BLE broadcasts | Local temperature/humidity sensors |

## Notes

- The application package name `org.jrs82.fsclock` is historical — the project
  started life as a simple clock app called *FsClock* — and is kept so that existing
  installations keep receiving updates.
- Starting an activity automatically at boot is restricted on modern Android;
  on unrestricted devices the launch can be handled by an external mechanism.

## License

[GPL-3.0](LICENSE)
