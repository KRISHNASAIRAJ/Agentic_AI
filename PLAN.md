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
- Carbon saving shown on the cluster comes from an **estimate**, with no manual
  entry: the cluster's lifetime ISS idle-seconds counter first, then distance +
  fuel economy. A bare zero is never sent, so the dash no longer shows "0 g saved".
- Protocol discovery card and per-connection raw frame logs for passive decoding.
  The card is observation-only: test/probe buttons were removed because they
  produced no useful cluster response. Only real cluster frames are counted.

## Future development (deferred)

### Cluster info / "traffic screens" — REMOVED from the shipped UI

The Ride screen previously had an eight-tab panel (ISS / Fuel Saver, Overview,
Carbon, Weather, News, Cricket, Football, Social). **None of these produced
working data on this cluster**, so the whole panel was removed rather than
left showing dead controls. It is tracked here for a future release.

Known problems to solve before reintroducing:

- The "Fuel Saver / intelliGO" tab was driven by bit 7 of `0x11` byte 6, but the
  same bit is set by a deliberate voice/start button press. It is **not** a
  reliable intelliGO-armed signal, so fuel-saver state must not be inferred
  from it. Do not tie it to ISS either.
- Weather, news, cricket, football and social counts need TVS cloud endpoints
  (`iceApiService`); them not being available locally is expected and needs a
  different data source if they are ever brought back.
- Overview and Carbon tabs duplicate cards that already exist on Home/Rides.

### Other backlog

- Apply the pending Supabase migrations (`0001_jc_rides.sql`,
  `0002_jc_sync.sql`) and verify ride/fuel/settings sync end to end.
- Decode more of the candidate IDs from the frame logs; only proven codes get
  adopted into telemetry.
- Google Maps remaining-distance work remains deferred.
