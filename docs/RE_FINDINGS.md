# Deeper RE findings — robustness, stability and feature ideas

Source: static decompilation of `com.tvsm.connect` **8.14.3**
(SHA-256 `9F4367…D8E88`). Line references point at the jadx output described in
[`RE_TVS_CONNECT.md`](RE_TVS_CONNECT.md).

Legend: **connection-related** items need your explicit approval before I touch
them, because you asked that Bluetooth connection behaviour not be changed.

---

## 1. Reliability gaps in Jupiter Connect

| # | What TVS Connect does | Evidence | Our app | Priority |
|---|---|---|---|---|
| R1 | Acquires a **partial wake lock** `TvsConnect::BleClusterSync` (with timeout) while linked; releases it on disconnect/destroy. Survives Doze during a pocketed ride. | `BaseBleConnectHelperService.java:1416-1421` acquire, `:1146-1149` release, `:1727`, `:1804` | **None** — no `WakeLock` anywhere in `app/src/main` | High (connection-related) |
| R2 | `startForeground` with **layered fallbacks**: typed → SecurityException → `connectedDevice` only → log. | `BaseBleConnectHelperService.java:1953-2001` | One `ServiceCompat.startForeground` call; OEM failures could kill the service | High (connection-related) |
| R3 | Cluster frames are routed only after the **first successful 0x4A write** (`setFirstCyclicDataSent` gate), avoiding a race where telemetry arrives before the link is considered live. | earlier dex addendum F3 | We send keepalive immediately on GATT ready — equivalent, but not explicit | Medium (connection-related) |
| R4 | Companion Device Manager auto-reconnect for iQube. | `IqubeCompanionDeviceService.java:163-175` | Single remembered MAC + 5/10/20/40/60 s backoff — sufficient for Jupiter | Skip |
| R5 | Extensive structured BLE logs (`saveLogsForBLE`, `saveDataToClusterLogs`) with timestamps and error context. | throughout `com.tvs.ble` | `FrameLog` already captures every frame + notes | Done |

## 2. Feature opportunities

| # | Feature | Evidence | Feasibility for us |
|---|---|---|---|
| F1 | **Call answer/reject from the dash.** Inbound `0x10` byte **13**: 1 answer, 2 reject, 3 reject+SMS, 4 voice assist. | `TelephonyUtils.java:172-179` (`receivedBytes[13]`), `:117-131` | Needs `TelecomManager.acceptRingingCall()` / `endCall()` — restricted to the default dialer. We currently only *decode* `switchAction` from byte 12 low bits. Note the byte 12 vs 13 conflict below. Hard without default-dialer role. |
| F2 | **Rider-name greeting** on connect: `5B 52 <name>` via `createMobileToClusterArray(name, 0x52)`. | `JupiterMobileToCluster.java:1654-1657` | **Easy** — we already have `buildNavText(name, 0x52)`; just send it after GATT ready using a name from Settings. |
| F3 | **Current place** frame `0x5C` (`createCurrentLocationArray`). | `BluetoothUtil.java:1152-1167` | **Easy** — we already have `buildLocationFrame()` used by voice; can push last parked place on connect. |
| F4 | **Media keys from cluster** `0x6B` byte 2 → `AudioManager` dispatch. | dex `getMusicCommandType`; our `decodeFrame` already reads `mediaKey` | **Easy** — wire `mediaKey` changes to `dispatchMediaKeyEvent` (helper exists in `VoiceCommandProcessor`). |
| F5 | **Play/pause state** `0x60`. | `BlePacketsProvider.returnPlayPauseState` | Optional; needs state source. |
| F6 | **Social unread counters** `0x9C`. | confirmed in 8.14.3 | **Already implemented** (WA/FB/IG clamped, triggers wired). |

## 3. Discrepancies that need a hardware capture

- **Call-switch byte:** legacy/local decode reads `0x10` byte **12** low 6 bits; the newer APK stack reads `receivedBytes[13]` (`TelephonyUtils.java:176`). Different stacks, different offsets — only a capture while pressing the dash call button settles it.
- **Text row width:** wire payload is 17 bytes; the simulator models 10/12-character visual rows. Neither is confirmed on the 2026 Jupiter cluster.
- **`0x53` SMS card:** genuine TVS ID, but community notes say its card is model-gated to U388/iQube. We send it alongside `0x4C`; dash verification pending.

## 4. Weather location — current behaviour and proposed fix

Current bridge (`ClusterService.startTrafficBridge`):
1. `RideStore.parkedLocation` (saved on BLE disconnect) — **can be stale** if you rode elsewhere.
2. Fallback Bengaluru `12.97, 77.59` — arbitrary if nothing saved yet.

Recommended layered resolution (no extra permission):

1. **Optional "Weather city" setting** in More → Traffic screens — a small built-in list of Indian cities (Bengaluru, Delhi, Mumbai, Chennai, Hyderabad, Pune, Kolkata, Ahmedabad…) stored as lat/lon. Deterministic, battery-free, works even when the phone has no GPS fix.
2. Otherwise **`LocationManager.getLastKnownLocation()`** (we already hold `ACCESS_FINE_LOCATION`) — fresher than a parked spot, no active scan.
3. Otherwise **parked location**.
4. Otherwise **Bengaluru default**.

Open-Meteo takes raw lat/lon, so no geocoding API is needed.

## 5. Recommendation order (my suggestion)

1. **Weather city setting + layered location** (non-connection, safe, high value).
2. **F2 rider-name greeting** and **F4 media keys** (non-connection, easy, visible on the dash).
3. **F3 current place on connect** (non-connection, easy).
4. **R1 wake lock + R2 foreground fallback** — biggest stability win, but connection-related: I will not touch these without your go-ahead.
5. F1 call answer/reject — park unless you want the app to become the default dialer.

Tell me which of these to implement and I'll build them with tests and push.
