# RE_TVS_CONNECT — first-party reverse engineering of the Play Store TVS Connect app

**Status:** research complete; change list **implemented and building green**
(35 JVM tests, 0 failures). C6 was intentionally skipped: Bluetooth connection
behaviour was left untouched at the owner's request.
**Date:** 2026-11-20
**Target cluster:** 2026 TVS Jupiter 125 (JUPITER_U566 family)

---

## 0. Artifact and method

This document is derived from **direct static decompilation of the genuine TVS
Connect APK**, not from any third-party open-source companion project.

| Property | Value |
|---|---|
| File | `C:\Users\krish\Downloads\TVS Connect.apk` |
| Package | `com.tvsm.connect` |
| Version | **8.14.3** (`versionCode=339`) — verified with `aapt dump badging` |
| Size | 179,594,350 bytes |
| SHA-256 | `9F436755473352C6523737AFD9295BA1A340B11EF44565984F9E7CAD1D3D8E88` |
| Tool | **jadx 1.5.7** (Java 21), `-j 4 --no-res`, sources only |
| Output | `C:\Users\krish\AppData\Local\Temp\opencode\tvs_decomp\sources\com\tvsm\connect\` |

> This build is **newer** than the v8.14.1 APK cited by community research
> (upstream hash `35046ea7…1ae15f0c4`). Where this APK disagrees with older
> reports, **this APK wins**; the disagreement is recorded in §5.
>
> The APK was read statically only. It was never executed, instrumented, or
> connected to TVS servers. Findings marked `[APK-8.14.3]` are quoted from the
> decompiled bytecode; `[JC-*]` refers to JupiterConnect's own code, which is
> evidence of local behaviour only.

### Reproduce

```powershell
$aapt = (Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Filter aapt.exe -Recurse | Select-Object -First 1).FullName
& $aapt dump badging 'C:\Users\krish\Downloads\TVS Connect.apk' | Select-String '^package:'
jadx -j 4 --no-res -d <out> 'C:\Users\krish\Downloads\TVS Connect.apk'
```

---

## 1. The outbound data-ID table (first party)

`com.tvsm.connect.bluetooth.bluetoothlegatt.SampleGattAttributes` — all IDs are
`byte` constants, so negative values are unsigned hex. `[APK-8.14.3]`

| Constant | Decimal | Hex | Meaning |
|---|---|---|---|
| `DATA_ID_CALLER_ID` | 67 | `0x43` | Caller ID |
| `DATA_ID_MOBILEDATA` | 74 | `0x4A` | Keepalive / mobile data |
| `DATA_ID_MESSAGE1` | 76 | `0x4C` | Text row 1 |
| `DATA_ID_MESSAGE2` | 99 | `0x63` | Text row 2 |
| `DATA_ID_SMS` | 83 | `0x53` | SMS card |
| `DATA_ID_NAME` | 82 | `0x52` | Rider-name greeting |
| `DATA_ID_NAVIGATION_CONTROL` | 78 | `0x4E` | Nav control |
| `DATA_ID_NAVIGATIONDATA1` | 79 | `0x4F` | Nav text row 1 |
| `DATA_ID_NAVIGATIONDATA2` | 80 | `0x50` | Nav text row 2 |
| `DATA_ID_CURRENT_LOCATION` | 92 | `0x5C` | Place name |
| `DATA_ID_MESSAGE5` | **-100** | **`0x9C`** | **Social / notification counters** |
| `DATA_ID_VEHICLE_CONTROL` | -15 | `0xF1` | Brightness / vehicle control |
| `DATA_ID_WEATHER` | 93 | `0x5D` | Weather screen |
| `DATA_ID_CRICKET` | -127 | `0x81` | Cricket screen |
| `DATA_ID_FOOTBALL` | -111 | `0x91` | Football screen |
| `DATA_ID_NEWS` | -106 | `0x96` | News screen |

Legacy SPP-era prefix constants in the same class confirm the semantics:
`SEND_PREFIX_SIGNAL_BATTERY_CALL_MESSAGE = "X"`,
`SEND_PREFIX_CALLER_NAME = "C"`, `SEND_PREFIX_TEXT_MESSAGE_1/2 = "J"/"K"`.

---

## 2. Carbon

### Definitive answer

1. **The phone app has no BLE command that writes a carbon value to the dash.**
   There is no carbon data ID in `SampleGattAttributes`, and no builder that emits
   one. `BluetoothUtil.convertCO2(double)` is a **display/formatter** helper, not
   a wire encoder. `[APK-8.14.3]`
2. TVS Connect computes and stores CO₂ saved locally, and the cluster derives its
   own figure from ISS (idle start-stop) data. The app's Carbon UI is backed by
   persisted ride/cumulative models and analytics, not by a BLE write. `[APK-8.14.3]`
3. JupiterConnect's decision to keep its carbon estimate **app-only** is therefore
   correct; its formula is an independent estimate, not a recovered TVS formula.

### First-party evidence

| Finding | Location |
|---|---|
| `convertCO2(double d) { return String.valueOf(d * 0.8d); }` — display scaling by **0.8** | `bluetooth/BluetoothUtil.java:127-129` |
| `co2Image(double d)` — icon thresholds: `<=25` low, `<=50` medium, `>50` high | `bluetooth/BluetoothUtil.java:116-121` |
| Carbon Saved UI resources: `layout_carbon_saved`, `layout_carbon_stats`, `tv_carbon_saved`, `tv_carbon_value`, `carbonSavedTag`, `bg_carbon_saved`, `carbon_leaf`, `color/carbonSavedTagColour` | `aapt dump resources` + `R.java:2523,9133,9632,15787-15789,22128-22129,25284-25285` |
| Persisted `co2Saved` / `CO2Saved` fields on N251, N360, U408, U532 ride and cumulative Realm models | `bikedashboard/{n251,n360,u408}/model/*BleRideDataModel.java`, `DBHelper.java:281-282,304-311` |
| Analytics key `CO2_saved` and event "Battery/Fuel level, odometer, vehicle status, CO2_saved and TPMS values are available" | `analytics/constants/AnalyticsKey.java:20`, `AnalyticsConstant.java:297` |
| `CARBONFOOTPRINT = 6` — a **field index**, not a data ID | `SampleGattAttributes.java:48` |
| **`carbonEmission(int i)` is misnamed**: it calls `sendMobileDataNotificationAndAlert(..., i)`, i.e. it writes the `0x9C` social frame with byte 10 = `i`. It is NOT a carbon write. | `bluetooth/sendreceive/N109U347N251DataSenderToCluster.java:1063-1065` |
| Inbound `0x19` ECONOMY carries ISS duration (bytes 2–4) and ISS count (15–17), units not decoded | upstream dex notes; local decoder `JupiterProtocol.kt:70-77` |

### JupiterConnect today `[JC-CODE]`

`RideStore.kt:210-251`, `279-286`, `463-478` computes a local estimate:

```
issSeconds × (0.5/3600) × 2.31          // preferred, from 0x19 lifetime ISS counter
| (odo / economy) × 0.10 × 2.31         // fallback: 10% of fuel would be idling
| (odo / 3) × 25 × (0.5/3600) × 2.31   // distance-only fallback
```

`foldIssDuration()` (`RideStore.kt:123-137`) adopts the cluster's cumulative ISS
counter as a floor, adds deltas, ignores zero, and never decreases. Displayed in
the app only (`RedesignScreens.kt:1577-1584`) and synced to Supabase as
`carbon_seconds` (`ClusterService.kt:1193`).

> **Doc conflict:** `PLAN.md:18-20` still claims the estimate is "shown on the
> cluster" and that "a bare zero is never sent". The decompiled app and the UI
> comment both say no frame writes the dash. Fix the wording (change C1).

---

## 3. Caller ID and message formats

### 3.1 What the genuine APK actually sends (v8.14.3)

**The 8.14.3 APK contains two BLE stacks**, and the model selects the path:

| Stack | Package | Jupiter path |
|---|---|---|
| legacy | `com.tvsm.connect.bluetooth.*` | `createCallerNameArray()` — one 20-byte frame |
| newer | `com.tvs.ble.*` + `com.tvs.bluetooth.core.*` | `writeJupiterCallerInformationToCluster()` — **one 20-byte frame** |

**For the Jupiter 125 the answer is now definitive: a single 20-byte frame.**

- `com.tvs.ble.feature.utils.TelephonyUtils.writeJupiterCallerInformationToCluster()`
  (`com/tvs/ble/feature/utils/TelephonyUtils.java:315-326`) resolves the contact
  name (falls back to the number), strips `+91`, and writes **one** frame via
  `createMobileToClusterArray(name, (byte) 67)` → `0x5B 0x43`.
- Legacy equivalents: `N109…sendCallerName()` (`sendreceive/N109U347N251DataSenderToCluster.java:1186-1200`)
  and `N360DataSenderToCluster.java:684-694`, both using
  `BluetoothUtil.createCallerNameArray()` (`bluetooth/BluetoothUtil.java:199-223`)
  = `{0x5B,0x43} + UTF-8 + 00-pad + {FF}`.

The three-packet sub-ID sequence **does exist in this APK**, but in the generic
`writeCallerInformationToCluster()` (`TelephonyUtils.java:269-283`) used by other
models: `BlePacketsProvider.returnContactInfoPacket()` (`com/tvs/bluetooth/core/utils/BlePacketsProvider.java:437-455`)
builds `{0x5B,0x43,subId} + UTF-8 + 00-pad + {FF}` = 21 bytes, sent with sub-IDs
`01` number, `03` country code, `02` name. It is **not** the Jupiter path.
JupiterConnect's 3-packet builder is therefore following the wrong model's
branch. `[APK-8.14.3]`

### 3.3 Sanitiser — first party

`com.tvs.bluetooth.core.utils.BluetoothUtil.createMobileToClusterArray()`
(`com/tvs/bluetooth/core/utils/BluetoothUtil.java:1283-1324`) confirms the
upstream rule exactly: emoji/symbol ranges (`\p{So}`, `\p{Cn}`, U+1F300–1F6FF,
U+1F900–1F9FF, U+1FA70–1FAFF, U+2600–26FF, ZWJ) → `@`; any character outside
`[A-Za-z0-9 ]` → `X`; trim; truncate to 17 characters (17 **bytes** for non-
English text); `0x00` pad; `0xFF` terminator. JupiterConnect's space-folding is a
deliberate readability deviation, not stock behaviour. `[APK-8.14.3]`

### 3.2 Message / text rows

| Builder | Frame | Evidence |
|---|---|---|
| `createSendSMSNameArray` | `5B 53 <UTF-8> 00-pad FF` (20 B) | `BluetoothUtil.java:300-321`; `sendSMS()` at `N109…:1042-1050` strips `+91`, truncates 17 |
| `sendMobileData5B4C` | `5B 4C <UTF-8> 00-pad FF` (20 B) | `BluetoothUtil.java:1192-1206` |
| `sendMobileData5B63` | `5B 63 <UTF-8> 00-pad FF` (20 B), truncates to 16 chars | `BluetoothUtil.java:1208-1227` |
| `createRiderNameArray` | `5B 52 <UTF-8> 00-pad FF` (20 B) | `BluetoothUtil.java:274-298`; `sendRiderName()` at `N109…:1038-1040` |
| `sendCustomMessage` | `5B 4C 00 00 <text> ... FF` | `BluetoothUtil.java:1176-1185` |

So `0x53` is a **genuine TVS data ID**, not an invention. Whether the Jupiter 125
renders that card is still unproven; the older dex note says model-gated to
U388/iQube.

### 3.3 Sanitising

The v8.14.3 builder above writes raw UTF‑8 with zero padding; no
`isEnglishCharacters` / `createMobileToClusterArray` helper survives under those
names in this build (older dex documented `@`/`X` substitution and 17-byte
truncation). JupiterConnect deliberately folds punctuation to a space
(`JupiterProtocol.kt:92-106`) so names read as `Mom Hello` instead of `MomXHello`.
Keep that behaviour, but label it as a local deviation, not stock parity.

### 3.4 JupiterConnect divergence

| Topic | APK v8.14.3 | JupiterConnect | Verdict |
|---|---|---|---|
| Caller frame | one 20-byte `5B 43` frame | three 21-byte `5B 43` packets, sub-IDs 1/3/2 (`JupiterProtocol.kt:241-262`) | ⚠️ version conflict; needs dash test |
| Number handling | strips `+91`, truncates 17 | keeps digits, `PhoneEventFormat.call()` | ⚠️ mostly equivalent |
| `0x53` SMS | genuine builder | sends `0x4C` **and** `0x53` (`ClusterService.kt:845-846`) | ✅ legitimate ID; dash effect unverified |
| Text rows | `0x4C`/`0x63`, zero pad, `FF` | identical | ✅ aligned |
| Sanitiser | raw UTF‑8 here; older dex `X`/`@` | punctuation → space | ⚠️ local deviation |

---

## 4. Social counters — **confirmed, with corrected semantics**

This reverses the earlier "no source" finding. The genuine APK implements all
three counters.

### 4.1 Missed calls — keepalive `0x4A` byte 10

`N109U347N251DataSenderToCluster.sendMobileData()` — `sendreceive/N109U347N251DataSenderToCluster.java:1369-1447`:

```
concat( {5B,4A},
        {signal<<4 | battery},      // byte 2
        {overspeed},                // byte 3
        {ambient+40},               // byte 4 (U445B/U566)
        {2},                        // byte 5
        {12h hour}, {minute}, {second}, {AM/PM},   // bytes 6-9
        {missedCalls DELTA},        // byte 10  <-- line 1414-1423
        {network type},             // byte 11
        {day}, {month+1}, {year%100},// bytes 12-14
        {getUnreadMsg()},           // byte 15 (stub returns 0 in 8.14.3)
        {voice-assist state},       // byte 16
        {0},{0},                    // bytes 17-18 (no checksum)
        {FF} )                      // byte 19
```

Counter semantics, `bluetooth/BluetoothUtil.java:698-731`:
- reads `CallLog.Calls` with `type=3 AND is_read=0` (missed + unread),
- returns 0 without `READ_CALL_LOG`,
- caps at **99**.

The legacy sender subtracts a stored `missedCall` preference and transmits the
**delta**, clamped to `>= 0`, capped at 99 for U408. The **newer Jupiter stack**
is slightly different: `JupiterMobileToCluster.sendMobileData()`
(`com/tvs/ble/feature/datasender/JupiterMobileToCluster.java:1613-1634`) writes
the `getMissedCalls()` value into the `TVSM / MISSED_CALL` preference, and
`BlePacketsProvider.sendJupiterMobileData()`
(`com/tvs/bluetooth/core/utils/BlePacketsProvider.java:1138-1204`) reads that
value back into byte 10. Net effect for Jupiter: **current unread-missed-call
count from the call log, capped at 99, sent every keepalive**. `[APK-8.14.3]`

JupiterConnect currently increments an in-memory counter per call, never resets,
and allows 0–255 (`ClusterService.kt:225-226,823`; `JupiterProtocol.kt:147`).
Change C3 below.

### 4.2 WhatsApp / Facebook / Instagram — frame `0x5B 0x9C`

`DATA_ID_MESSAGE5 = -100` (0x9C). Builder:
`sendreceive/N109U347N251DataSenderToCluster.java:1542-1568` (U566 branch):

```
5B 9C | WA | FB | IG | deliveryAlert | 00 | 00 | 00 | 00 | alertType | <zero pad> | FF
        b2   b3   b4        b5                                  b10
```

- **byte 2 WhatsApp**: `getWhatsappByte()` (`:706-715`) takes
  `SocialMediaCountHelper.getLastValues().getWhatsApp()`, caps at **31**, encodes
  it in the low 5 bits.
- **byte 3 Facebook**: `getFBByte()` (`:1075-1080`) low 4 bits, max **15**.
- **byte 4 Instagram**: `getInstaByte()` (`:1086-1090`) raw count byte.
- **byte 5** delivery-alert type (`sendDeliveryAlert()`, `:1250-1252`).
- **byte 10** "carbonEmission"/alert parameter (`carbonEmission(int)`, `:1063-1065`).
- Counts live in `localdatabaseSQLite/SocialMediaCountHelper.java`: increments per
  notification, serialised as JSON under preference key `SocialMediaCount`,
  with `clearRecords()` to reset.
- Triggers in this APK: `sendFBData()` (`:1294-1297`), `sendInstaData()`
  (`:1363-1366`), WhatsApp notification path (`:1640`), and the alert/carbon
  methods above. A parallel implementation exists in
  `N360DataSenderToCluster.java:919,736,749,805,1064,1075`.

JupiterConnect's `buildSocialCounts()` (`JupiterProtocol.kt:204-214`) already
writes `5B 9C <wa> <fb> <ig> … FF` — **the frame ID and byte positions are
correct**. What is missing:

- no clamp to 31/15 for WhatsApp/Facebook,
- no Facebook or Instagram trigger (only WhatsApp increments,
  `ClusterService.kt:156-161`),
- no reset path (`SocialMediaCountHelper.clearRecords()` equivalent),
- bytes 5 and 10 always zero.

### 4.3 Verdict table

| Counter | Wire | Status |
|---|---|---|
| Missed calls | `0x4A` byte 10, **delta since last sync**, cap 99 | **Confirmed** `[APK-8.14.3]` |
| WhatsApp unread | `0x9C` byte 2, low 5 bits, cap 31 | **Confirmed** `[APK-8.14.3]` |
| Facebook unread | `0x9C` byte 3, low 4 bits, cap 15 | **Confirmed** `[APK-8.14.3]` |
| Instagram unread | `0x9C` byte 4, raw byte | **Confirmed** `[APK-8.14.3]` |
| Unread SMS | `0x4A` byte 15 | Field exists; `getUnreadMsg()` is a **stub returning 0** in 8.14.3 |

---

## 5. Cross-source conflicts (recorded, not smoothed over)

| Topic | This APK (8.14.3) | Older community dex (8.12.4) / Scoot spec | Action |
|---|---|---|---|
| Caller ID | **Jupiter path: single 20-byte `5B 43` frame**. Generic 3-packet 1/3/2 builder exists for other models | 3×21-byte packets described as universal | **Resolved for Jupiter**: use the single frame; keep 3-packet only as legacy fallback |
| Checksum | mobile-data byte 18 = 0 (no checksum) | Scoot spec says byte 18 = `255 − (sum[0..17] % 256)` | Keep zero on `0x4A`; do not add a checksum until a frame is rejected |
| Sanitiser | raw UTF‑8 in the builders above | emoji→`@`, non-ASCII→`X`, 17-byte truncate | Keep JupiterConnect's space-folding; label as local |
| Social counters | `0x9C` fully implemented | not documented | Trust this APK |
| `0x53` SMS | genuine TVS builder present | model-gated to U388/iQube | Keep, mark Jupiter rendering unverified |

---

## 6. Change list — **implemented**

All changes below were applied and verified with `:app:testDebugUnitTest` and
`:app:assembleDebug` (35 tests, 0 failures). C6 was deliberately **not** applied:
keepalive bytes 3 and 11 remain at zero because Bluetooth connection behaviour
was left untouched.

| # | Pri | File(s) | Change | Rationale |
|---|---|---|---|---|
| **C1** | High | `PLAN.md:18-20` | Correct the carbon bullet: estimate is **app-only**; no BLE frame writes the dash. | Matches decompiled app and `RedesignScreens.kt:1582-1584`. |
| **C2** | High | `protocol/JupiterProtocol.kt:204-214`, `transport/ClusterService.kt:156-161,880-883` | **Keep** `0x9C`; clamp WA to 31 (low 5 bits), FB to 15 (low 4 bits), IG to 0–255; add an explicit `clearSocialCounts()` that sends zeroes. | First-party confirmed frame and semantics. |
| **C3** | High | `transport/ClusterService.kt:225-226,823`, `JupiterProtocol.kt:147` | Derive missed calls from the Android call log (`type=3 AND is_read=0`), cap at 99, refresh each keepalive; remove the unbounded `missedCalls++`. | Exact behaviour of `getMissedCalls()` + the Jupiter sender preference path. |
| **C4** | High | `protocol/JupiterProtocol.kt:241-262`, `ClusterService.kt:834` | Switch Jupiter caller mirroring to the **single 20-byte `5B 43` frame**; keep the 3-packet 1/3/2 builder only as a legacy/other-model fallback. | `writeJupiterCallerInformationToCluster()` is the Jupiter branch in the supplied APK. |
| **C5** | Medium | `protocol/JupiterProtocol.kt:264-276`, `ClusterService.kt:845-846` | **Keep** the `0x53` send; add a comment that it is a genuine TVS ID whose Jupiter card is unverified. Remove the earlier "remove 0x53" recommendation. | First-party builder exists; do not drop a real protocol feature on a third-party note. |
| **C6** | Medium | `transport/ClusterService.kt:1148-1163` | Align keepalive with the APK: byte 3 = overspeed (default 120), byte 11 = network type (e.g. 4), keep byte 18 = 0. | Matches `sendMobileData()` while preserving the no-checksum rule. |
| **C7** | Medium | `nav/PhoneEventFormat.kt`, `RedesignScreens.kt:1577-1584` | Label carbon as an **independent estimate**; optionally show the TVS display factor 0.8 and icon bands 25/50 as reference, not as our formula. | Honest provenance; avoids implying TVS parity. |
| **C8** | Low | `debug/FrameCatalog.kt:33-74` | Add `0x9C` label "social counts (out)" — it is now a confirmed ID. | Frame-catalogue completeness. |
| **C9** | Low | `docs/` | Record the APK identity (8.14.3, SHA-256 above) and the cross-version caller conflict in the repo's protocol notes. | Provenance survives upstream changes. |

### Decisions — resolved

1. **C3:** approved — `READ_CALL_LOG` added; missed calls now come from the call log,
   capped at 99.
2. **C2:** approved — Facebook and Instagram notification triggers wired; counters
   clamped to the APK's bit widths; a "Clear social counters" control added.
3. **C5:** keep sending `0x53` on Jupiter until a dash capture settles rendering.
4. **C6:** skipped at the owner's request — Bluetooth connection behaviour is
   untouched; keepalive bytes 3 and 11 remain zero.

### Deferred: traffic screens

The APK confirms the traffic-screen builders (weather `0x5D`, cricket `0x81`,
football `0x91`, news `0x96`, `sendTrafficSequence`), but their content comes from
TVS cloud APIs. Reproducing them needs either TVS endpoints (against this project's
no-cloud rule) or free-API data mapped into the TVS payloads. A Nifty/Sensex screen
should use the generic 17-byte text rows (`0x4C`/`0x63`) rather than the football
frame, which the cluster renders as a sports scoreboard. Separate feature — not
part of this change list.

---

## 7. Verification checklist (needs the bike)

- [ ] Capture an incoming call and confirm the Jupiter cluster renders the single
      `5B 43` frame (keep the 3-packet sequence available as A/B fallback).
- [ ] Confirm keepalive byte 10 with a known number of unread missed calls.
- [ ] Send a `5B 9C` frame with known WA/FB/IG values and photograph the Social
      screen; confirm the 31/15 caps.
- [ ] Check whether `0x53` produces a card on the Jupiter 125.
- [ ] Confirm that no outbound frame needs a checksum at byte 18.
