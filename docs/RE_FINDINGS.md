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
| R1 | Acquires a **partial wake lock** `TvsConnect::BleClusterSync` (with timeout) while linked; releases it on disconnect/destroy. Survives Doze during a pocketed ride. | `BaseBleConnectHelperService.java:1416-1421` acquire, `:1146-1149` release, `:1727`, `:1804` | **Implemented**: `JupiterConnect::BleClusterSync` partial lock, renewed each keepalive, released on disconnect/destroy | ✅ Done |
| R2 | `startForeground` with **layered fallbacks**: typed → SecurityException → `connectedDevice` only → log. | `BaseBleConnectHelperService.java:1953-2001` | **Implemented**: `startForegroundSafe()` with typed → SecurityException → connectedDevice fallback, also used for the voice type update | ✅ Done |
| R3 | Cluster frames are routed only after the **first successful 0x4A write** (`setFirstCyclicDataSent` gate), avoiding a race where telemetry arrives before the link is considered live. | earlier dex addendum F3 | We send keepalive immediately on GATT ready — equivalent, but not explicit | Medium (connection-related) |
| R4 | Companion Device Manager auto-reconnect for iQube. | `IqubeCompanionDeviceService.java:163-175` | Single remembered MAC + 5/10/20/40/60 s backoff — sufficient for Jupiter | Skip |
| R5 | Extensive structured BLE logs (`saveLogsForBLE`, `saveDataToClusterLogs`) with timestamps and error context. | throughout `com.tvs.ble` | `FrameLog` already captures every frame + notes | Done |

## 2. Feature opportunities

| # | Feature | Evidence | Feasibility for us |
|---|---|---|---|
| F1 | **Call answer/reject from the dash.** Inbound `0x10` byte **13**: 1 answer, 2 reject, 3 reject+SMS, 4 voice assist. | `TelephonyUtils.java:172-179` (`receivedBytes[13]`), `:117-131` | Needs `TelecomManager.acceptRingingCall()` / `endCall()` — restricted to the default dialer. We currently only *decode* `switchAction` from byte 12 low bits. Note the byte 12 vs 13 conflict below. Hard without default-dialer role. |
| F2 | **Rider-name greeting** on connect: `5B 52 <name>` via `createMobileToClusterArray(name, 0x52)`. | `JupiterMobileToCluster.java:1654-1657` | **Implemented** — `sendInitialClusterData()` writes `0x52` on connect; editable name + toggle in More → Rider greeting. |
| F3 | **Current place** frame `0x5C` (`createCurrentLocationArray`). | `BluetoothUtil.java:1152-1167` | **Implemented** — last parked coordinates sent as `0x5C` on connect. |
| F4 | **Media keys from cluster** `0x6B` byte 2 → `AudioManager` dispatch. | dex `getMusicCommandType`; our `decodeFrame` already reads `mediaKey` | **Implemented** — `startMediaKeyBridge()` dispatches play/pause/next/previous/volume on change. |
| F5 | **Play/pause state** `0x60`. | `BlePacketsProvider.returnPlayPauseState` | Optional; needs state source. |
| F6 | **Social unread counters** `0x9C`. | confirmed in 8.14.3 | **Already implemented** (WA/FB/IG clamped, triggers wired). |

## 2b. Rider/caller photo on the dash

TVS Connect does not "draw" the photo inside the name text frame — it runs a
separate document-transfer handshake (`com.tvs.ble.feature.utils.BLEImageTransferUtils`):

| Step | Frame | Detail |
|---|---|---|
| Phone announces image | `5A 53` | `picNo` (4 = rider profile, 5 = caller), total frames (u16 BE), frame No (u16 BE), state (1 transferring / 3 complete / 16 delete), picType (5 for profile), picName "Test", pad, `FF` — 20 bytes |
| Cluster requests a chunk | `5A 54` | `picNo` at byte 2, frame No at bytes 5–6 (BE) |
| Phone sends a chunk | `5A 55` | frame No (u16 BE) + **173 bytes** (JUPITER_U566) + `00 FF` — 179 bytes |
| Phone marks complete | `5A 53` | state = 3, frame No = total |

The APK resizes profile images to 80×80, JPEG quality 50, and splits into
173-byte chunks (74 frames for their fixed image). Evidence:
`BLEImageTransferUtils.java:208-222` (chunk), `:245-247` (173 for JUPITER_U566),
`:320-366` (init), `:465-468` (picNo parse), `:508-574` (profile init).

**Implemented** in `profile/ClusterImageTransfer.kt`: pick a photo in
More → Rider greeting, it is resized/compressed, announced with `0x5A 53` on
ignition, and chunks are served on `0x54` requests. `0x5A 54` is now handled in
both `onCharacteristicChanged` overloads.

## 3. Discrepancies that need a hardware capture

- **Call-switch byte:** legacy/local decode reads `0x10` byte **12** low 6 bits; the newer APK stack reads `receivedBytes[13]` (`TelephonyUtils.java:176`). Different stacks, different offsets — only a capture while pressing the dash call button settles it.
- **Text row width:** wire payload is 17 bytes; the simulator models 10/12-character visual rows. Neither is confirmed on the 2026 Jupiter cluster.
- **`0x53` SMS card:** genuine TVS ID, but community notes say its card is model-gated to U388/iQube. We send it alongside `0x4C`; dash verification pending.

## 4. Weather location — current behaviour and proposed fix

Current bridge (`ClusterService.startTrafficBridge`):
1. `RideStore.parkedLocation` (saved on BLE disconnect) — **can be stale** if you rode elsewhere.
2. Fallback Bengaluru `12.97, 77.59` — arbitrary if nothing saved yet.

**Implemented** resolution (`resolveWeatherLocation()`, no extra permission):

1. **Last-parked coordinates** (`RideStore.parkedLocation`) — weather follows where the scooter was left.
2. Otherwise **Hyderabad** `17.3850, 78.4867` — the only fixed city kept in the UI.

Open-Meteo takes raw lat/lon, so no geocoding API is needed. No active GPS scan is performed, so this costs no extra battery.

## 5. Status

Items 1–4 below were **all implemented and verified** (`:app:testDebugUnitTest`
+ `:app:assembleDebug`, 41 tests green) after your "implement all" approval:

1. ✅ Weather city setting + layered location.
2. ✅ Rider-name greeting and cluster media keys.
3. ✅ Current place on connect.
4. ✅ Wake lock + foreground fallback (connection-related, approved).

Still parked: **F1 call answer/reject** — it needs the default-dialer role.
Only pursue it if you want Jupiter Connect to replace your dialer.
