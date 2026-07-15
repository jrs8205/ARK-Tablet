# ARK-Tablet

A full-screen, always-on **clock and weather display** for Android tablets.
Built for 24/7 use as a kitchen/hallway info board anywhere in the world:
pick your city (Hamburg, Tokyo, São Paulo…) and the board shows your local
time, weather from two independent sources, and sun and moon data — with no
ads, no accounts, no API keys and no cloud backend.

## Features

- **Clock and date** — large always-visible clock in the device time zone.
- **Weather from two sources, side by side** — MET Norway (Yr) and Open-Meteo,
  so you can see when the models agree and when they don't. Current conditions
  on the home screen and a 7-day page with an hourly breakdown per day.
- **City search** — global place search (Open-Meteo Geocoding API); set once
  and the board keeps itself updated. Optional one-tap "Use device location".
- **Sun and moon** — sunrise/sunset arc, day length and the current moon phase
  rendered with a correct terminator (NOAA solar equations).
- **Built for 24/7 operation** — screen kept on, scheduled day/night brightness
  with an optional night red tint, and pixel shift to prevent panel burn-in.

## Devices

Designed for landscape tablets in both 4:3 and 16:10 aspect ratios.
Requires Android 11 (API 30) or newer. The UI is in English.

## Building

Requirements: JDK 17+ and the Android SDK.

Create `local.properties` in the project root:

```properties
sdk.dir=/path/to/Android/Sdk

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

| Source | Used for | Terms |
|---|---|---|
| [MET Norway](https://api.met.no/) Locationforecast 2.0 | Primary forecast (global, 9 days) | [CC BY 4.0](https://api.met.no/doc/License), identified User-Agent |
| [Open-Meteo](https://open-meteo.com/) | Comparison forecast + city search | Free for non-commercial use |

Weather data by the Norwegian Meteorological Institute (MET Norway),
licensed under CC BY 4.0, and by Open-Meteo.

## Notes

- The application package name `org.jrs82.fsclock` is historical — the project
  started life as a simple clock app called *FsClock* — and is kept so that
  existing installations keep receiving updates.
- The Finnish-market predecessor of this app (FMI weather, Nord Pool
  electricity prices, RuuviTag sensors) is preserved at the tag
  [`arkikeskus-fi-2.4.0`](../../tree/arkikeskus-fi-2.4.0).
- Starting an activity automatically at boot is restricted on modern Android;
  on unrestricted devices the launch can be handled by an external mechanism.

## License

[GPL-3.0](LICENSE)
