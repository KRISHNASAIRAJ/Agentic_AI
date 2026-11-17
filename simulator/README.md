# Jupiter Connect — TVS Cluster Simulator (Windows)

A dependency-free Python tool for testing Jupiter Connect's protocol handling on Windows.

## Run

Double-click `run_simulator.bat`, or:

```
python sim_gui.py
```

Requires Python 3 (3.8+); uses only the standard library (`tkinter`). Tested on Python 3.13 / Windows 11.

## What it does

| Tab | Purpose |
|---|---|
| **Cluster telemetry** | Build any of the four inbound TVS frames (`0x10` speedometer, `0x11` service, `0x18` engine, `0x19` economy) from editable values. "Ride step" advances the odometer and trip meter by 1 km. Copy the hex. |
| **Decode inbound** | Paste frames from a frame log and see every decoded field — the exact same offsets as `JupiterProtocol.kt`. |
| **Decode app frames** | Paste frames the app wrote and get a byte-by-byte explanation: nav distance/ETA/pictogram, keepalive signal/battery/clock, text rows. |
| **Pictogram / icon test** | All documented maneuver IDs with names. Select one and build a real `0x5A 0x4E` control frame to compare against the cluster's arrow. |
| **Self test** | Verifies the Python codec against golden vectors. The same vectors are asserted by the JVM tests. |

## What it does NOT do

Windows has no public BLE-peripheral (GATT server) API, so this tool **cannot advertise as a scooter to your phone**. It is a protocol workbench, not a wireless fake cluster. For over-the-air testing you need a second Android device (or two emulators with virtual Bluetooth) running a GATT-server simulator app.

## Workflow with the app

1. Use **Cluster telemetry** to create a frame with known values (e.g. odometer exactly 5498.4 km, trip 1534.4 km).
2. Those bytes are asserted by `ProtocolTest.kt` via the golden vectors, so the Kotlin decoder must produce the same numbers.
3. Capture real frames from the phone (frame-log export in the app's Developer mode) and paste them into **Decode app frames** to check what the app actually sent.

## Files

- `tvs_protocol.py` — frame codec, pictogram table, golden vectors, self-test. Pure Python.
- `sim_gui.py` — tkinter GUI.
- `run_simulator.bat` — double-click launcher.
