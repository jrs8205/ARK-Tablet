# First-launch location permission + 12-hour clock option (1.1.0)

Feedback from the first device test (1.0.0): the app silently shows "No location set"
on first launch instead of asking for the location permission, the settings page never
tells whether the permission is granted, and the clock is hard-coded to 24-hour time.

## 1. First-launch location permission

- On startup, when no place is configured and the permission has never been asked
  (`location_perm_asked` preference), the system location permission dialog opens
  immediately.
- **Granted** → device location is resolved automatically (existing
  `useDeviceLocation` path): place, weather and sun times fill in without further
  interaction. If no location fix is available the setup hint stays visible.
- **Denied** → `location_perm_asked` is set; the app never nags again on startup.
  The "Use device location" button in Settings still requests the permission on demand.
- When no place is set but the permission is already granted (e.g. reinstall over
  `adb install -r`), the device location is used automatically without a dialog.
- The home-screen empty state gains a direct **Search city** button that opens the
  same city search dialog as Settings, so a user who denies the permission does not
  need to find their way to Settings.

## 2. Permission status in Settings

- The Location section shows a new row: **Location permission — Granted / Not
  granted** (green / warning color), refreshed live.
- When not granted, a hint below the row explains the two options: use city search,
  or tap "Use device location" to get the permission dialog.
- "Use device location" outcomes are reported precisely via a `LocationOutcome`
  result (replaces the boolean callback): `SUCCESS`, `FAILED` (no fix / no geocoder),
  `PERMISSION_DENIED`, `PERMISSION_DENIED_FOREVER`. The last one is detected via
  `shouldShowRequestPermissionRationale == false` right after a denial, and the error
  text points to Android app settings (or city search) since the dialog can no longer
  be shown.

## 3. Time format: 24-hour / 12-hour (AM/PM)

- New preference `twelve_hour_clock`. Default on a fresh install comes from the
  device setting (`DateFormat.is24HourFormat`); once the user picks a format in
  Settings, that choice sticks.
- New segmented selector in Display & brightness: **Time format: 24-hour /
  12-hour (AM/PM)**.
- In 12-hour mode every time in the app switches format:
  - main clock, e.g. `3:45` with a small `PM` marker next to the seconds column
    (clock width does not grow);
  - sunrise/sunset (home bottom band + Info page), e.g. `7:12 AM` — these are now
    formatted in the UI layer from `sunriseMin`/`sunsetMin`, so a format change
    applies instantly (the pre-formatted `sunRise`/`sunSet` strings are removed
    from `HomeUi`);
  - 7-day hour column, e.g. `3 PM` (column widens in 12-hour mode);
  - Day starts / Night starts steppers, e.g. `6 AM`;
  - "Last weather update" in the Application section.
- 24-hour mode renders exactly as 1.0.0.
- Formatting lives in a pure-Kotlin `TimeFormat` helper with JUnit tests
  (first tests in this repo; adds the `junit` test dependency).

## 4. MET Norway 6 h blocks in the 7-day view (added after device-test feedback)

MET Norway only provides hourly data for the first ~2.5 days; later days come as
6-hour blocks (4 per day, on UTC boundaries). 1.0.0 dropped those blocks into the
hourly grid, leaving the MET column looking empty ("–" on 20 of 24 rows) and the
precipitation hidden. Now:

- `MetNorwayClient` keeps the block's precipitation sum and tags rows with
  `blockHours` (1 or 6).
- `buildForecast` routes 6 h rows into `DayForecastUi.metBlocks` instead of the
  hourly map.
- The MET column renders block days as four rows labeled with the local time span
  (`03–09`, or `3 AM–9 AM` in 12-hour mode — MET's blocks are UTC-aligned, so local
  labels vary by time zone), showing temperature, wind, condition icon and the 6 h
  precipitation sum. The hourly grid is skipped on days with no hourly MET data.

## Out of scope (backlog)

Charging details (volts/watts/ETA), calendar events, next-alarm info, screensaver
(daydream) mode, wallpaper/background option, alternative UI layouts, weather
history, in-app notifications. Version bumps to 1.1.0 (versionCode 2); release
only after a device test.
