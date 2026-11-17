# How TVSRideCompanion was built — and what Jupiter Connect inherits

This is the workflow behind the open-source app we studied, written as a
repeatable process. It is also the plan for this project.

## 1. The problem and the insight

TVS Connect fails at the one thing it must do: hold a BLE link to the cluster.
The developers noticed that the instrument cluster is a **BLE peripheral**:
every arrow, street name, caller ID, odometer value is just a 20-byte packet
sent over GATT. The TVS cloud is only needed for *remote* features (tracking
when the phone is away, geofencing). When the phone is physically on the
scooter, none of that is necessary.

Consequence: you do not need TVS's backend, account or app. You need to speak
the BLE protocol.

## 2. Four information streams

They did not guess the protocol. They cross-checked four sources:

| Source | What it gave |
|---|---|
| Official TVS Connect APK, decompiled statically with `jadx` / REA MCP | GATT UUIDs, per-model configs, frame layouts, AES key, text sanitiser, checksum rules |
| `overclock98/JupiterRideCompanion` (report + working APK, now taken down) | Proven navigation packet layout, pictogram table, crypto cross-check, doze-bypass architecture |
| FEV pentest (`CVE-2024-35537`) | Proof that the BLE "auth" key is hardcoded — no real security to defeat |
| TechEnclave / Reddit + the Scoot simulator spec | Current community state of the art, Jupiter byte tables, a working cluster simulator |

**Rule:** where two independent implementations agree, treat it as fact. Where
they disagree, record both. Every fact in their docs carries a provenance tag:
`VERIFIED_FROM_APK`, `INHERITED_UNVERIFIED`, or `NEEDS_CAPTURE`.

## 3. The workflow, phase by phase

```
research → write PROTOCOL.md → build simulator → build app → hardware capture
         → fix wrong assumptions → add tests → repeat → release
```

1. **Extract, don't run.** The official APK is read as a specification only.
   A slim APK containing just two DEX files was built so the decompiler could
   finish inside its 120-second timeout.
2. **Write the facts down first.** `docs/PROTOCOL.md` and `docs/NAVIGATION.md`
   record byte offsets, endianness, provenance and open questions *before* any
   UI is built.
3. **Build a fake cluster.** A BlueZ GATT peripheral on a laptop advertises the
   real service UUID and characteristics. The app connects to it unchanged.
   This turns "unverified until the scooter is available" into a testable
   development loop.
4. **Build the transport, not the protocol, first.** `ScooterBleService` knows
   GATT; a `ProtocolDriver` per model knows byte meanings. Unknown models fall
   back to a driver that still streams raw frames.
5. **Capture on hardware.** The app's own frame log is the evidence base:
   every packet in and out, hex-dumped, with timestamps, paced per data ID so
   a ten-hertz telemetry stream does not drown the log.
6. **Let hardware correct the docs.** Examples from their commit history:
   - multi-byte fields were little-endian; the real odometer proved big-endian;
   - byte 6 of `0x10` did not behave like fuel; the gauge read "full" while the
     dash said "FUEL EMPTY";
   - the keepalive sent midnight every 2 seconds, resetting the cluster clock;
   - navigation mirroring looked correct in the UI but had never sent a frame;
   - discovery issued before the MTU exchange meant `onServicesDiscovered`
     never fired on a peripheral that took MTU seriously.
7. **Pin each fix with a test.** 48 pure-JVM tests assert byte layouts,
   endianness, sanitisation and row widths. CI asserts the merged manifest's
   permission set — "no cloud" is enforced by the build, not promised.
8. **Release only after real-hardware verification** — then document what is
   still unverified instead of implying it works.

## 4. Architecture they converged on

```
maps notification → NotificationListenerService → NavInstruction
   → NavigationBridge (0x5A 0x4E control every 1.5 s)
   → ProtocolDriver (per model)
   → BLE GATT write → cluster

cluster → BLE notify → frame decoder → Session StateFlow → Compose UI
```

Layers: `protocol/` (drivers, packet builders), `transport/` (BLE, foreground
service, scanner, reconnect), `nav/` (listener, parser, bridge), `session/`
(one live state), `prefs/`, `log/`, `ui/`.

## 5. Reliability details worth copying

- **Write mutex** — concurrent GATT writes cause status-133 disconnects.
- **Write without response** + MTU 512 — one frame per packet, no flow control.
- **MTU before discovery** — issuing discovery during MTU exchange returns BUSY
  and `onServicesDiscovered` never fires.
- **Close the GATT client on disconnect** — otherwise `connect()` refuses every
  later attempt because it thinks a link still exists.
- **Keepalive every 2 s** — the cluster drops the link when it stops.
- **Reconnect with backoff** — 5/10/20/40/60 s, ten attempts.
- **Foreground service + wake lock only while connected** — Doze otherwise
  kills the link in a pocket during a ride.
- **Notification access is a Settings toggle**, not a runtime permission —
  without it navigation mirroring silently does nothing. The app's first-run
  flow has to point the user there explicitly.

## 6. What we built in Jupiter Connect (so far)

| Piece | File | Status |
|---|---|---|
| Meridian dark design system | `DESIGN.md`, `ui/theme/` | done |
| Five Compose screens + nav | `MainActivity.kt` | done |
| GATT constants + live telemetry model | `protocol/Constants.kt` | done |
| Frame decoder + packet builders | `protocol/JupiterProtocol.kt` | done |
| BLE scan/connect/keepalive service | `transport/ClusterService.kt` | done |
| Live UI wiring (fuel, km, temps, frames) | `MainActivity.kt` | done |
| APK builds | `app/build/outputs/apk/debug/` | ✅ green |

Next: notification mirroring (`5A 4E` / `5B 4F`), ride storage, and the
verification checklist against the real Jupiter 125.

## 7. The verification mindset

The most valuable habit from this project: **do not trust a screen that looks
right**. A UI strip can show an instruction while no BLE frame was ever sent;
a test can agree with the encoder that wrote its own fixture. Only bytes off
the wire — or a photograph of the dash — settle a protocol fact.
