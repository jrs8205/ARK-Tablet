# Charging details, next alarm, calendar, screensaver, cellular indicator (1.2.0)

Second batch from the 16 Jul device-test feedback. All data is local to the device —
no new network calls.

## Charging details (header)

While charging (and not full), the header shows voltage, power and the estimated
time to full next to the battery glyph: `5.0 V · 4.5 W` / `full in 1 h 20 min`.
Voltage comes from `ACTION_BATTERY_CHANGED` (`EXTRA_VOLTAGE`), current from
`BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` (magnitude only — the sign convention
varies by device), the estimate from `computeChargeTimeRemaining()` (hidden when the
device does not report it).

## Next alarm (bottom band)

`AlarmManager.nextAlarmClock` polled every 5 s; shown as `⏰ Fri 07:30`
(`EEE h:mm a` in 12-hour mode). Hidden when no alarm is set. No permissions needed.

## Calendar card (Info page)

The Info page's right column now stacks Moon + Calendar. The card lists the next
5 event instances within 48 h (`CalendarContract.Instances`, includes in-progress
events), refreshed every 10 min. `READ_CALENDAR` is asked on demand via an
"Allow calendar access" button inside the card; without the permission the card
just shows that button.

## Screensaver (daydream)

`ArkDreamService` renders the same Compose home page as a non-interactive,
fullscreen dream — any touch wakes the device. Selectable in Android's Screen
saver settings. A `DreamService` owns no lifecycle, so the service implements
`LifecycleOwner`/`SavedStateRegistryOwner` manually for `ComposeView`.

## Cellular indicator (header)

On tablets with an active SIM, a second gauge appears next to WiFi: signal bars
(`SignalStrength.getLevel`) and the network type (`5G`, `4G LTE`, `3G`, `2G`).
Uses `READ_BASIC_PHONE_STATE` (a normal permission, API 33+); when the type is
unavailable it falls back from the data network to the voice network type, and
to a plain `Mobile` label on older devices. Hidden when no SIM is ready.

## Versioning

1.2.0 / versionCode 3. Released together with the 1.1.0 changes (first-launch
location flow, permission status row, 12-hour clock, MET 6 h blocks) — 1.1.0 was
never published as a GitHub release.
