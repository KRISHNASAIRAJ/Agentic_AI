# TVS Connect 8.14.3 — Definitive BLE Protocol Catalog & Jupiter Connect Gap Analysis

> Source of truth: genuine Play Store APK `com.tvsm.connect` v8.14.3,
> SHA-256 `9F436755473352C6523737AFD9295BA1A340B11EF44565984F9E7CAD1D3D8E88`,
> decompiled with jadx 1.5.7 into `C:\Users\krish\AppData\Local\Temp\opencode\tvs_decomp\sources`.
> All paths/lines below refer to that jadx output, not to any third-party project.
> Companion docs: `RE_TVS_CONNECT.md`, `RE_FINDINGS.md`, `PROTOCOL_NOTES.md`.

---

## 1. THE CARBON QUESTION — answered definitively

**Carbon / CO₂ / "eco" data is NEVER sent over BLE to the cluster.** There is no
command ID, no frame, and no byte layout for it. Every claim below is from the
decompiled source.

### 1.1 The value is computed locally

`com/tvs/ble/feature/datareceiver/IQubeClusterDataReceiver.java:1319,1326`:

```java
cO2Saved = ((odo - firstOdo) + pausedRideTravelledDistance) * 0.035d;
```

`0.035` is the **only** CO₂ constant in the entire decompiled tree (exactly two
hits, both here). For petrol/ICE bikes it is a hardcoded placeholder `100.0d`
(`RaiderClusterDataReceiver.java:1458`).

### 1.2 Where it actually goes (all non-BLE)

| Destination | Evidence |
|---|---|
| Local Room DB `cO2Saved REAL NOT NULL` | `RideDataEntity.java:31-32`, `SaveRideDao_Impl.java:98`, `ElectricAppDatabase_Impl.java:122` |
| HTTPS multipart POST field `cO2Saved` | `SaveBleRideDataSourceImpl.java:189` |
| Ride CSV column 9 | `FileConstant.java:37,205`; `CSVFile.java:568` |
| App UI only, from **cloud** GraphQL | `VehicleStatsFragment.java:263-284,360-368`; `DashboardViewModel.java:699,3838`; `GetVehicleDashboardDetailsSubscription.java:285` (`co2Saved`, `trees_offset`) |

`SaveRideDetailResponse.getCO2Saved()` has **no consumer** anywhere in the APK.
Jupiter's `updateRideDetailsToDB` preserves it as `preservedCo2Saved`
(`JupiterClusterDataReceiver.java:701`; body 1533-1539 not decompiled) — again
DB-only.

### 1.3 "Eco score" does not exist

The only eco concept is **Eco/Economy ride mode** (`ecoModeTotalTime`,
`ecoModeTotalDistance`, `isEcoMode`, `BluetoothConstants.ECONOMY_MODE`) — used
for cloud tallies, **never put in a BLE frame**.

**Conclusion:** Nothing to implement on the BLE-write side. To show carbon on
the dash would require inventing a non-TVS frame. Jupiter already tracks
ISS-based carbon locally and correctly keeps it off the wire.

---

## 2. Universal framing rules (apply to every feature below)

| Rule | Evidence |
|---|---|
| Frames are 20 bytes, `0xFF` terminator | `BluetoothConstants.java:158` (`POST_FIX_BYTE=-1`); `SampleGattAttributes.java:138` |
| Start byte `0x5A` = control/command, `0x5B` = notification/data, `0x9A` = keyless | `BluetoothConstants.java:188-190` |
| Byte 1 = DATA_ID, sometimes byte 2 = sub-command | `BluetoothConstants.java:59,93` |
| Payload zero-padded (climate: `0x20`-padded) to byte 18 | `BlePacketsProvider.java:333-349` |
| Optional checksum: `255 - (sum % 256)` at byte 18 | `BleEngine.java:5205`; `BluetoothUtil.java:595,877` |
| Write funnel (with per-bike encryption) | `BleEngine.writeCharacteristicToCentral(byte[],boolean)` `:5205-5314` |
| Static helper forces checksum OFF | `BleEngine.java:4290-4295` (`writeCharacteristicToCentral$default`) |
| MTU 512; service `5456534D-5647-5341-5342-454E544F5251` | `BluetoothConstants.java:269-284` |

**DATA_ID constants:** `SampleGattAttributes.java:54-96`,
`BluetoothConstants.java:71-95`.

---

## 3. Complete mobile → cluster write-frame catalog

| Header | Feature | Builder / file:line |
|---|---|---|
| `5B 4A` | Cyclic mobile data (battery, signal, missed calls, call state, time, temp, find-me, DND, voice assist, overspeed) | `BlePacketsProvider.java:700,1026,1138,1263,1794` |
| `5B 73 08` | Calibration write request | `JupiterMobileToCluster.java:1496` |
| `5B 73 <cmd>` | Calibration update (idle stop/start id 9, follow-me id 18, TSL id 23) | `BlePacketsProvider.java:1001` |
| `5B 73 0A 12` | Follow-me headlamp timer (Jupiter variants) | `BlePacketsProvider.java:2033` |
| `5A F1` | Vehicle control / illumination / TSL / ride mode | `BlePacketsProvider.java:926,1834,1944` |
| `5A F1` | Trip select / reset | `BluetoothUtil.java:1461,1520` |
| `5A F1` / `5A F2` | Brightness, auto-brightness, regen (VA settings) | `ble/feature/utils/BluetoothUtil.java:619,885,895` |
| `5A F2` | U577 dual timer (follow-me / idle start-stop) | `BlePacketsProvider.java:2001` |
| `5B 4C` | Custom text line 1 / nav assist / traffic select / **high speed alert trigger** | `BluetoothUtil.java:1283,1375,1448,883,898`; `U368SendDataToCluster.java:303-317` |
| `5B 63` | Custom text line 2 (also music artist) | `BluetoothUtil.java:1283` |
| `5B 52` | Rider name | `BluetoothUtil.java:1424` |
| `5B 43` | Caller ID (category 1 number / 2 name / 3 area) | `BlePacketsProvider.java:437`; `TelephonyUtils.java:269-283` |
| `5B 53` | SMS sender info | `TelephonyUtils.java:83`; `BleEngine.java:3539` |
| `5B 5C` | Current location / address | `BluetoothUtil.java:1152,1242` |
| `5B 5D` | Weather / climate (icon, AQI, rain, 13-byte desc) | `BluetoothUtil.java:1107,1043` |
| `5B 9C` | Social counts (bit-packed) | `BluetoothUtil.java:2222` |
| `5B 60` | Music playback state + volume + durations | `BlePacketsProvider.java:531,565,614` |
| `5B 61/62/63/65` | Music album / title / artist / artist-new | `BlePacketsProvider.java:526` |
| `5B 81` | Cricket score | `ble/feature/utils/BluetoothUtil.java:657` |
| `5B 91` | Football score | `ble/feature/utils/BluetoothUtil.java:769` |
| `5B 96` | News headline (75 bytes) | `ble/feature/utils/BluetoothUtil.java:569,876` |
| `5A 4E` + `5B 4F` | Navigation control + instruction text | `BleNavigationSendData.java:249,364` |
| `5A 49` | Navigation road name / destination arrived | `BleNavigationSendData.java:215` |
| `5A 4E` (20B struct) | Apache navigation | `ApacheClusterNavigationData.java:273` |
| `5B 22` | First-connection user ID | `BlePacketsProvider.java:367` |
| `5B 50` | Pictogram description (voice assist) | `BlePacketsProvider.java:381` |
| `5B 7E` / `5B 80` | Apache birth date / iQube DOB | `ApacheMobileToCluster.java:257`; `BluetoothUtil.java:1170` |
| `5B 81` | iQube mobile number (DID collision, different family) | `BluetoothUtil.java:1267` |
| `5A 7D` | VIN (fixed sample bytes) | `BlePacketsProvider.java:663` |
| `9A F1` | Keyless entry: lock/unlock/find/trunk/auth challenge | `BluetoothUtil.java:927`; `KeylessEntryUtils.java:14-19` |
| `5A 53` / `5A 55` | Image transfer init / chunk (already implemented) | `BLEImageTransferUtils.java:208,320` |

> Note: `0x5B 0x7E` is a **birth-date** frame, not carbon.

---

## 4. Feature-by-feature byte layouts (new, actionable)

### 4.1 Music — fully local, HIGH value
- **Text** (`5B 61` album / `62` title / `63` artist / `65` artist-new):
  `5B <did> <UTF-8 name ≤15 chars if >17> <00 pad to 17> FF`
  (`BlePacketsProvider.java:198-205,526-529`; dispatch `BleMusicDataSourceImpl.java:50-93`).
- **Playback** `5B 60` (Apache/U577 layout, `BlePacketsProvider.java:574-610`):
  `[3]=state (Play=0, Pause=1, Stop=4; Apache Stop=254)`
  `[5]=currentMin [6]=currentSec [7]=volume 0..100 [12]=totalMin [13]=totalSec`, `[19]=FF`.
- **Cluster → phone controls** on `0x6B`: PLAY=0, PAUSE=1, TOGGLE=2, NEXT=3, PREV=4, VOL_UP=5, VOL_DOWN=6 (`BluetoothConstants.java:142,151-152,164`).

### 4.2 Navigation — local maps SDK, HIGH value
Write order: **text frame first, sleep 400 ms, then control** (`BleEngine.java:4979-4995`).
- Control `5A 4E`: `[2..3] distance to maneuver (m, BE)`, `[4..5] remaining time (min, BE)`, `[6..8] remaining distance (3B)`, `[9] pictogram`, `[10] noOfString (1 if ≤17 else 2)`, `[11] nav on/off`, `[19] FF`.
- Text `5B 4F`: `5B 4F <UTF-8 ≤17> <00 pad> FF`.
- Road name `5A 49`: `5A 49 <UTF-8 ≤22> <arrived flag> <00 pad> FF`.
- Pictogram mapping tables: `BleNavigationSendData.java:71-213,505-549` and `ApacheUtils.java:508-654` (verbatim port needed).
- Apache fixed 20B struct: `ApacheClusterNavigationData.java:273-291`.

### 4.3 Cluster settings — all local
- Illumination/theme `5A F1` (`BlePacketsProvider.java:1834-1942`): Jupiter U566 uses `0x60/0x70/0x80/0x90/0xA0` for levels 2..5; `[6]=TSL on/off`, `[9]=day [10]=month(0-based) [11]=yy`.
- Brightness U546 (`ble/feature/utils/BluetoothUtil.java:619-655`): new protocol 1→`0x20`, 2→`0x30`, 3→`0x40`, 4→`0x50`; old 1→2 … 5→10.
- Regen `5A F2 00 00 00 <regen> … FF` (`:885-889`).
- Ride mode `5A F1` with `[3]=6/7/10` (`BlePacketsProvider.java:1944`).
- Calibration `5B 73 <cmd:8 request/10 write> <id> <len> <4-byte timer BE> <checksum> … FF` (`:1001-1024`).
- Trip `5A F1 00 <trip|FF> 00 02 00 00 00 <d> <m+1> <yy> <trip> 00 01 … FF` (`BluetoothUtil.java:1461-1560`).

### 4.4 Social counters — exact bit packing
`BleWriteSocialMediaNotificationDataSrcImpl.java:35-41,58`:
- WhatsApp byte = **3-bit chats + 5-bit msgs** (binary-string concat).
- Facebook byte = **4-bit + 4-bit**.
- Instagram = raw count.
- Frame `5B 9C <wa> <fb> <ig> <00 pad> FF` (`BluetoothUtil.java:2222-2237`).
> Jupiter currently caps WhatsApp at 31 and Facebook at 15 with single counts; the genuine app distinguishes chats vs messages.

### 4.5 Battery
- Phone battery + signal are combined into one hex-decimal byte at `[2]` of the `5B 4A` mobile-data frame (`BlePacketsProvider.java:1151-1154`), overspeed `[3]`, temp+40 `[4]`, missed calls, network type, date/time, find-me flag `[17]`.
- Bike EV battery RX: dataID `0x13`, `[2]=battery %`, `[3..4]=D2E` (`U467BatteryPercentage.java:15-25`).

### 4.6 Traffic/info screens (confirms current implementation)
- Screen select `5B 4C 00 <id> <00×15> FF`; IDs: Default `0x00`, Weather `0x78`, Cricket `0x79`, Football `0x80` (`TrafficSequence.java:10-14`; `BluetoothUtil.java:1448-1459`).
- Weather `5B 5D <icon> <AQI:u16be> <rain:u8> <13-byte 0x20-padded desc> FF` (`BluetoothUtil.java:1107-1149`).
- Cricket `5B 81` with nibble-packed innings/state/toss/result (`BluetoothUtil.java:657-767`; packer `:1036-1043`).
- Football `5B 91 <t1> <t2> <tourney> <time> <injury> <state> <name1:5> <name2:5> 00 FF` (`BluetoothUtil.java:769-838`).
- News `5B 96 <≤75-byte title> <00 pad> FF`, pushed with **no screen select** (`BluetoothUtil.java:569-593,876-883`).
- **No stock/Sensex/Nifty screen exists** — Jupiter's market-on-football is a deliberate custom extension.
- Genuine APIs are TVS-only: `https://tvsconnectapi.tvsmotor.com/api/weather`, `/airqualityindex`, `/cricket`, `/football`, `/news` (`NetworkModule.java:40,156`; `IceApiService.java:28-45`).

### 4.7 Ride / trip / service
- `endRide()` is **local-only** — no BLE frame (`BleEndRideDataSourceImpl.java:18-21`).
- `setTripValue()` sends `5A F1` (see 4.3).
- Service reminders are **RX-only** from the cluster (`@ParsingMeta(frameType=SERVICE_REMINDER)` in the speedometer models); no TX reset frame found.

### 4.8 Keyless entry (advanced, local) — NOT IMPLEMENTED
Documented for reference only; removed from the app at the user's request.
`9A F1 <64 init / 80 challenge> <random> <len> <00> <16-byte XOR-obfuscated command UUID> <00 pad> FF`; commands in `KeylessEntryUtils.java:14-19`, AES-CTR keyed challenge at `:19`, `BleEngine.writeChallenge():4279`.

### 4.9 Receive-side status (`VAClusterOperator.java:41-52`, no writes)
`valueReceived[12]==4` → invoke voice assist; `[11]>33` → cluster at max brightness; `[2]` → over-speed TTS.

### 4.10 Over-speed alert — two separate frames
1. **Threshold setting:** the limit is byte `[3]` of the `5B 4A` mobile-data frame
   (`BlePacketsProvider.java:721-738,1193-1196`); `120` (0x78) = disabled,
   `255` (0xFF) = disabled on U408, enabled = the limit itself. Detection is
   nevertheless done in the app (`U368ClusterDataManager:499-505`,
   `U368AlertHelper:221-268`): `speed >= limit`, one-shot until speed drops
   below the limit.
   > **Jupiter Connect deliberately does NOT write `5B 4A` byte 3.** The stable
   > keepalive leaves it zero: a past experiment writing a value there changed
   > cluster behaviour after ~1 min and dropped the BLE link. The user's hard
   > constraint is "keepalive bytes 3/11 remain untouched". Detection therefore
   > stays entirely app-side.
2. **Alert trigger:** `5B 4C` + ASCII `"HIGH SPEED ALERT"` (16 bytes) + one
   zero pad + `FF` = 20 bytes (`U368SendDataToCluster.sendHighSpeedMessage():303-317`,
   also U449/U507). This is the frame that makes the cluster render its alert.
   The Raider/U577 branch additionally posts a real Android notification
   (`U577DashboardFragment.setOverSpeedNotification():1804-1826`).

---

## 5. Jupiter Connect gap analysis

### 5.1 Already implemented (verified in `transport/ClusterService.kt`)
`5B 4A` keepalive (lines 1015,1344), `5B 43` caller (995), `5B 53` SMS (1007),
`5B 4C`/`63` text rows (1006,1422), `5B 52` rider name (1513), `5B 5C` location
(1039,1516), `5B 9C` social counts (1044), `5A F1` brightness (1031),
`5A 4E`/`4F` nav (1434,1454), traffic select + `5D`/`81`/`91`/`96` (1576-1598),
`5A 53/54/55` photo handshake (1520 + RX).

Inbound decoded: speedometer `0x10`, service `0x11`, engine `0x18`, economy `0x19`
(incl. ISS), cluster address `0x42`, music keys `0x6B`.

### 5.2 Gap analysis status

| Priority | Feature | Frame(s) | Status |
|---|---|---|---|
| 1 | Music metadata + controls | `5B 60/61/62/63/65`, RX `6B` | **Not implemented** (next candidate) |
| 2 | Cluster settings (theme, illumination, regen, TSL, ride mode, calibration, follow-me) | `5A F1/F2`, `5B 73` | ✅ Implemented — More → Cluster Settings |
| 3 | Trip select/reset + service reminder RX | `5A F1`, RX `0x11` byte 4 | ✅ Implemented |
| 4 | Pictogram tables + road-name frame | `5A 49` | ✅ Implemented — `nav/Pictograms.kt` has verbatim `fromU725`, `fromMmiV2`, `fromManeuverId`; More → Navigation Extras |
| 5 | Social chats-vs-messages packing | `5B 9C` | ✅ Implemented — `buildSocialCountsV2` |
| 6 | Keyless entry / find-my | `9A F1` + AES-CTR challenge | ❌ Removed — frame builders, UI and RX challenge handling deleted at the user's request. Documented above only as reverse-engineering reference for the genuine APK. |
| 7 | VIN / DOB / user ID handshake | `5B 22/7E/80`, `5A 7D` | ✅ Implemented — More → Setup Data (manual only) |
| 8 | High speed alert | `5B 4C` "HIGH SPEED ALERT" + held text + vibration + status-bar notification | ✅ Implemented — Settings → HIGH SPEED ALERT; threshold/switch persisted; one-shot with re-arm below limit. Keepalive `5B 4A` byte 3 intentionally NOT written. |

All new frames live in `protocol/ClusterControlFrames.kt` and are verified by
`ClusterControlFramesTest.kt` (13 tests). None are sent automatically on
connect, so Bluetooth link behaviour is unchanged.

### 5.3 Deliberately out of scope
Carbon/eco writes (feature does not exist), TVS-cloud news/cricket/football
parity (we substitute free APIs), stock screen (does not exist upstream).

---

## 6. Caveats
Several Kotlin coroutine bodies (`C11661`, `C11751`, `C11881`, `C11741`,
`C11871`) and two weather-icon mappers (`getWeatherIcon(byte,String,String)`,
`getWeatherIconForPremium`) failed jadx decompilation. Their layouts above come
from the surrounding array-builder code and method names, not from inference.
All byte layouts in this document are derived from actual array-builder code.
