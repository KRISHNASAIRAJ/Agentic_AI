# Jupiter Connect

A standalone Android companion app for the **2026 TVS Jupiter 125 (Copper
Matte)**. It talks to the instrument cluster over TVS SmartXonnect Bluetooth
Low Energy, shows live telemetry and mirrors turn-by-turn navigation from the
maps app you already use.

**No TVS account. No TVS cloud. No telemetry upload.**

> Personal, non-commercial interoperability project. See [CREDITS.md](CREDITS.md)
> and the disclaimer at the end of this file.

---

## What it does today

| Feature | Status |
|---|---|
| Scan for `TVSBT…` cluster and connect over BLE GATT | ✅ |
| Foreground service with 2-second keepalive and reconnect backoff | ✅ |
| Live telemetry: speed, odometer, fuel bars, engine/intake temperature, battery voltage, distance-to-empty, RPM | ✅ |
| Navigation mirroring from Google Maps / MapMyIndia / Waze and others | ✅ |
| Five-screen Material 3 UI in the shared dark design language | ✅ |
| Ride history and last-parked storage | ⏳ planned |

### Screen previews

Open [`design/index.html`](design/index.html) in a browser for all five screens,
or view the rendered [`design/preview.png`](design/preview.png).

| Home | Navigate |
|---|---|
| Connection hero, live telemetry tiles, cluster discovery | Navigation HUD, mirror switch, packet stream |

| Rides | Find | Settings |
|---|---|---|
| Ride history | Last-parked + find-me | Permissions and cluster info |

---

## Quick start

Requirements: **JDK 17–21**, Android SDK platform **35**, Android device with
BLE (API 26+).

```powershell
# local.properties must contain: sdk.dir=<path to Android SDK>
.\gradlew.bat :app:assembleDebug

# or build a release APK signed with your own local keystore
.\gradlew.bat :app:assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

### Play Protect blocks the install

Play Protect flags brand-new sideloaded APKs signed with the shared **Android
Debug** key, especially when the app also asks for Bluetooth, location and
notification access. That is a reputation heuristic, not a code defect.
The release build above uses your own unique keystore (`signing/jupiter.jks`,
gitignored), which removes the debug-key signature problem.

If Play Protect still says *"App blocked to protect your device"*:

1. **Play Store → profile icon → Play Protect → Settings (gear) → Scan apps with
   Play Protect → Off**
2. Install the APK again.
3. Turn the scan back on afterwards.

Alternatively install over ADB, which does not show the blocking dialog:

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

On the scooter:

1. Switch **ignition ON** (the engine does not need to run).
2. Hold the **MODE** button on the speedometer for ~5 s until the cluster shows
   `BT PAIR`.
3. Open the app, grant Bluetooth, Location and Notifications.
4. On **Home**, tap **Find cluster**, then choose the `TVSBT…` device.
5. For navigation, allow **Notification access** for Jupiter Connect in
   Android Settings, then start a route in Google Maps.

Without notification access the app connects fine but navigation mirroring
silently does nothing — this is an Android platform rule, not an app bug.

---

## Architecture

```
app/src/main/java/com/jupiterconnect/
├── MainActivity.kt                  Compose shell + five screens
├── protocol/
│   ├── Constants.kt                 GATT UUIDs, frame IDs, Telemetry model
│   └── JupiterProtocol.kt           byte decoders + packet builders
├── nav/
│   ├── MapsNotificationListener.kt  reads turn-by-turn from any maps app
│   └── NavParsing.kt                normalises instruction + pictogram
├── transport/
│   └── ClusterService.kt            BLE GATT, keepalive, navigation bridge
└── ui/theme/                        Color / Type / Theme
```

### Data flow

```
cluster → BLE notify → decodeFrame() → ClusterRepository.telemetry → Compose UI

maps notification → MapsNotificationListener → NavInstruction
                  → NavigationBridge (5A 4E control every 1.5 s + 5B 4F text)
                  → BLE write → cluster
```

### Protocol notes

The SmartXonnect cluster is a BLE GATT peripheral:

- Service `5456534D-5647-5341-5342-454E544F5251`
- Write characteristic `00005352-…`, notify characteristic `00005354-…`
- 20-byte frames: 2-byte header, 17-byte payload, `0xFF` terminator
- Keepalive `5B 4A` every 2 s (clock, signal, battery, find-me flag, checksum)
- Navigation `5A 4E` control (distance, ETA, pictogram) and `5B 4F` text
- Telemetry: `0x10` speed/odometer/fuel/RPM, `0x18` temperatures and battery,
  `0x19` economy and distance-to-empty — all big-endian
- Cluster renders ASCII only; other characters are folded to `X`

Reliability details copied from the reference implementation: serialised GATT
writes, MTU exchange before service discovery, closing the GATT client on
disconnect, keepalive every 2 s, and 5/10/20/40/60 s reconnect backoff.

---

## CI

`.github/workflows/android.yml` runs on every push and pull request:

1. **validate-env** — JDK 21, Gradle wrapper, project files
2. **unit-tests** — JVM tests
3. **build** — assembles the debug APK and uploads it as an artifact

---

## How this project was researched

The full workflow — four information streams, provenance tags, the
simulate → capture → correct loop, and the reliability lessons — is in
[`HOW_THEY_BUILT_IT.md`](HOW_THEY_BUILT_IT.md) and in the 12-slide PowerPoint
[`docs/how-it-was-built.pptx`](docs/how-it-was-built.pptx).

Regenerate the deck with `python docs/make_ppt.py`.

For the protocol facts themselves, see the first-party reverse engineering of
the Play Store TVS Connect 8.14.3 APK in
[`docs/RE_TVS_CONNECT.md`](docs/RE_TVS_CONNECT.md) (styled:
[`docs/RE_TVS_CONNECT.html`](docs/RE_TVS_CONNECT.html)). The artifact identity,
confirmed byte layouts and cross-source conflicts are summarised in
[`docs/PROTOCOL_NOTES.md`](docs/PROTOCOL_NOTES.md). Further reliability and feature
findings are in [`docs/RE_FINDINGS.md`](docs/RE_FINDINGS.md).

---

## Roadmap

1. Ride history and last-parked location persistence.
2. Verify pictogram IDs and text-row layout on the real 2026 Jupiter cluster.
3. Caller ID / SMS mirroring, rider-name greeting, find-me verification.
4. Harden notification parsing for more map providers.

---

## Disclaimer

This is an independent interoperability project. It is not affiliated with,
endorsed by, or connected to TVS Motor Company. TVS, Jupiter and SmartXonnect
are trademarks of their respective owners. Use at your own risk; do not
interact with the app while riding. Protocol documentation is derived from
public sources with attribution in [CREDITS.md](CREDITS.md).
