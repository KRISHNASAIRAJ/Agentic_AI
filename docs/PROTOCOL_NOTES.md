# Protocol provenance notes

This file records the authoritative artifact behind the protocol facts in this
repository, so the conclusions survive upstream repositories disappearing.

## Authoritative artifact

| Field | Value |
|---|---|
| File | `C:\Users\krish\Downloads\TVS Connect.apk` |
| Package | `com.tvsm.connect` (Google Play) |
| Version | **8.14.3**, `versionCode=339` |
| Size | 179,594,350 bytes |
| SHA-256 | `9F436755473352C6523737AFD9295BA1A340B11EF44565984F9E7CAD1D3D8E88` |
| Analysis | static decompilation only, jadx 1.5.7, Java 21, `-j 4 --no-res` |
| Scope | 41,612 classes; never executed, never instrumented, never contacted TVS servers |

The full evidence dossier is [`RE_TVS_CONNECT.md`](RE_TVS_CONNECT.md), with a
styled version at [`RE_TVS_CONNECT.html`](RE_TVS_CONNECT.html).

## Confirmed facts used in the implementation

- **Carbon:** no BLE frame writes the dash's Carbon screen. TVS Connect formats
  its own figure with `convertCO2(d) = d * 0.8` and persists `co2Saved` per ride.
  `carbonEmission(int)` in the legacy sender is misnamed — it writes the `0x9C`
  social frame, not a carbon value. Jupiter Connect's estimate is app-only.
- **Caller ID:** the APK ships two BLE stacks. Jupiter's branch
  (`writeJupiterCallerInformationToCluster`) writes **one 20-byte `5B 43` frame**.
  The 21-byte `1`/`3`/`2` sub-ID sequence is the generic branch for other models;
  it remains in Jupiter Connect behind the `legacyCallerPackets` debug flag.
- **Sanitiser:** emoji/symbol ranges → `@`, other non-alphanumerics → `X`, trim,
  17 characters (17 bytes for non-English text), `0x00` pad, `0xFF` terminator.
  Jupiter Connect deliberately folds punctuation to a space for readability.
- **Missed calls:** `BluetoothUtil.getMissedCalls()` reads call log
  `type=MISSED AND is_read=0`, requires `READ_CALL_LOG`, caps at **99**. The
  Jupiter sender places that value in `0x4A` byte 10 every keepalive.
- **Social counters (`0x5B 0x9C`):** byte 2 = WhatsApp low 5 bits (≤31), byte 3 =
  Facebook low 4 bits (≤15), byte 4 = Instagram raw byte. Counts come from
  `SocialMediaCountHelper`, incremented per app notification.
- **`0x53` SMS:** a genuine TVS data ID (`createSendSMSNameArray`); community
  notes say its card is model-gated to U388/iQube, so Jupiter Connect sends it
  alongside the `0x4C` text row. Rendering on the 2026 Jupiter 125 is unverified.

## Cross-source conflict on caller framing

Third-party notes (derived from an older v8.12.4 dex) present the 3-packet
`1`/`3`/`2` sequence as universal. The supplied 8.14.3 APK shows it is model
specific. Both are recorded in `RE_TVS_CONNECT.md`; the single frame is the
Jupiter default because that is what the supplied app actually builds.

## Deliberate non-changes

Bluetooth connection behaviour — GATT handling, keepalive cadence, reconnect
backoff, MTU exchange and write serialisation — was **not modified**. In
particular, keepalive bytes 3 (overspeed) and 11 (network type) remain at zero
because writing byte 3 previously correlated with link drops on this rider's
cluster. Only display payload fields were aligned with the APK.
