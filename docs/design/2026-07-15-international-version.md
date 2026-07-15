# ARK-Tablet 1.0.0 — international version design

Date: 2026-07-15. Status: approved.

## Goal

Turn the app into a worldwide clock + weather information display. A user in
Hamburg (or anywhere else) picks their city and sees local weather. All
Finland-specific features are removed. The visual style, fonts and 24/7
display features stay exactly as they are.

## Identity

- Display name **ARK-Tablet**, `applicationId org.jrs82.arktablet`
- Versions restart at **1.0.0** (versionCode 1), APK name `ARK-Tablet-<version>.apk`
- The new applicationId means this app can never install over the legacy
  Finnish app (`org.jrs82.fsclock`); both can run side by side.
- The pre-rewrite state is tagged `arkikeskus-fi-2.4.0`.

## Removed

- FMI weather (client, repository, place search)
- Electricity spot prices (Elering) — page, header pill, repositories
- Weather warnings (MeteoAlarm)
- Finnish public holidays and name-day leftovers
- MML reverse geocoding, bundled CA certificate, `MML_API_KEY`
- Room database: history, CSV export, battery statistics, System page
- RuuviTag BLE sensors and Bluetooth permissions
- The legacy View-based UI (MainActivity, ClockController, settings/history/system
  activities) that has not been in the UI flow since 2.2.0 — the app is pure
  Compose afterwards

## Kept unchanged

- ComposeHomeActivity: landscape lock, immersive mode, KEEP_SCREEN_ON
- Day/night brightness scheduling with optional night red tint; pixel shift
  (burn-in protection)
- Stage scaling for 4:3 and 16:10 tablets; theme and bundled fonts
- Weather icons, WMO weather-code mapping, temperature color scale
- Sun arc and moon phase (NOAA solar equations — location independent)
- Open-Meteo client/repository
- Wi-Fi and battery meters in the top bar

## New

- **MET Norway client** — Locationforecast 2.0 (compact), global 9-day
  forecasts, no API key, identifying User-Agent, refreshed at most every
  10 minutes per the terms of service; `symbol_code` mapped to the existing
  icon set. Takes FMI's place as the primary weather block.
- **City search** — Open-Meteo Geocoding API; first-launch flow and a
  Settings entry. Selected place (name, country, coordinates) is stored.
- **Use device location** — optional button (GPS + Android Geocoder).
- Clock follows the device time zone; dates render in English.
- Attribution in Settings: "Weather data: MET Norway (CC BY 4.0) · Open-Meteo".

## UI

- Top bar: place on two lines (city / country), Wi-Fi + battery, navigation
  **Home | Info | 7-day | ⚙**. Electricity pill removed.
- Home: clock + date on the left; two weather blocks (MET Norway, Open-Meteo)
  on the right; bottom strip shows sunrise/sunset and moon phase.
- Info: full sun arc + moon phase (warnings section removed).
- 7-day: day selector + hourly rows from both sources.
- Settings: Display & brightness | Location (city search, device location) |
  Application (version, attribution).
- All UI text in English.

## Error handling and limits

- The two weather sources are independent: if one fails its block shows a
  "no data" placeholder while the other keeps working.
- If no place is configured, the home screen directs the user to city search.
- MET Norway: max one refresh per 10 minutes (matches the app's cadence).
  Open-Meteo: ~10,000 requests/day allowed; the app uses roughly 150.

## Sequence

1. Tag `arkikeskus-fi-2.4.0`
2. Remove Finland-specific code and the legacy View UI
3. Switch identity (applicationId, name, version, APK name)
4. Translate UI strings to English; clock to device time zone
5. MET Norway client + two-source home and 7-day pages
6. City search + device location + first-launch flow
7. Build, device test
8. Update README, release v1.0.0
