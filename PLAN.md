# Jupiter Connect — Plan

Working plan for the Android BLE companion to the 2026 TVS Jupiter 125
SmartXonnect cluster. This file is the source of truth for what is shipped and
what is deferred.

## Shipped

- BLE scan/connect to the cluster, foreground service with a 2-second keepalive.
- Automatic reconnect with capped backoff (5s / 10s / 20s / 30s). The connected
  device address is persisted, so a killed service or a reboot redials it
  without the user opening the app.
- Live telemetry (speed, odo, fuel bars, temperatures, battery, DTE, RPM) and
  turn-by-turn mirroring from any navigation app's notification.
- Ride recording with checkpoints; a brief link drop resumes the same ride
  instead of splitting it into several.
- Fuel logs, cumulative stats, parked location, call/message cards.
- Carbon saving is an **app-only estimate** with no manual entry: the cluster's
  lifetime ISS idle-seconds counter first, then distance + fuel economy. The dash
  computes its own figure from ISS data; no BLE frame can write it. TVS Connect
  8.14.3 formats its figure with a x0.8 factor — ours is an independent estimate
  and is labelled as such in the UI.
- Protocol discovery card and per-connection raw frame logs for passive decoding.
  The card is observation-only: test/probe buttons were removed because they
  produced no useful cluster response. Only real cluster frames are counted.

## Future development (deferred)

### Cluster info / "traffic screens" — IMPLEMENTED with free APIs

The old eight-tab panel was removed because it only sent the screen-select
frame (`5B 4C 00 <id> ...`) on a manual button and never the required data
frame. Reverse engineering TVS Connect 8.14.3 recovered both halves:

- screen select — `TrafficSequence`: Weather `0x78`, Cricket `0x79`,
  Football `0x80`, verified in `com.tvs.ble.feature.utils.TrafficSequence`;
- data frames — weather `0x5D` (`createClimateData`), cricket `0x81`
  (`getCricketData`), football `0x91` (`getFootballData`), news `0x96`
  (`sendNewsHeadline`).

The **TRAFFIC SCREENS** toggle in More → Settings now runs a bridge that cycles
Weather → Cricket → Market → News while connected and no route is active,
sending the select frame followed by the data frame. Data comes from keyless
free APIs (Open-Meteo + US AQI, ESPN cricket, Yahoo Finance for Nifty/Sensex,
Google News RSS) — no TVS cloud and no account.

The Football screen is repurposed for **Nifty 50 vs BSE Sensex**, as requested:
team names become `NIFTY` / `SENSE`, and the score bytes carry the signed
change-percent ×10; the full values are also written on text row `0x63`.

Still deliberately excluded:

- **Fuel Saver / intelliGO** — no confirmed BLE toggle exists. The old tab
  inferred state from bit 7 of `0x11` byte 6, but the same bit is set by a voice
  button press, so it is unreliable. We do not drive it.
- Overview and Carbon tabs duplicate cards that already exist on Home/Rides.
- Social **unread counters** are mirrored on `0x9C` (confirmed by RE), but the
  old Social tab is not restored until its rendering is verified on hardware.

### Other backlog

- Apply the pending Supabase migrations (`0001_jc_rides.sql`,
  `0002_jc_sync.sql`) and verify ride/fuel/settings sync end to end.
- Decode more of the candidate IDs from the frame logs; only proven codes get
  adopted into telemetry.
- Google Maps remaining-distance work remains deferred.
