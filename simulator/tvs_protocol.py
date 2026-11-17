"""TVS SmartXonnect 20-byte frame codec (Jupiter 125).

This is the Windows-side reference implementation of the frame layouts used by
com.jupiterconnect.protocol (Constants.kt / JupiterProtocol.kt). Offsets here
must stay in sync with the Kotlin decoder; the self-test vectors at the bottom
are also asserted by the JVM unit tests.

No third-party dependencies - runs on stock Python 3 on Windows.
"""

from dataclasses import dataclass, field
from typing import Optional, Dict, Any

FRAME_SIZE = 20
VALID_STARTS = (0x5A, 0x5B, 0x9B)
END_BYTE = 0xFF

ID_SPEEDOMETER = 0x10
ID_SERVICE = 0x11
ID_ENGINE = 0x18
ID_ECONOMY = 0x19
ID_NAV_CONTROL = 0x4E
ID_KEEPALIVE = 0x4A
ID_NAV_TEXT = 0x4F
ID_MUSIC = 0x6B

# Pictogram ids from TVS Connect's ManeuverUtils (see jupiter-scoot.md).
PICTOGRAMS = {
    0: "sharp left", 1: "slight left (alt)", 2: "slight left",
    3: "sharp right", 4: "slight right (alt)", 5: "slight right",
    6: "U-turn left", 7: "straight / continue", 8: "arrived",
    11: "T-junction left", 12: "T-junction right",
    13: "left branch", 14: "right branch / merge left", 15: "fork left",
    16: "fork right", 17: "merge left", 18: "merge right",
    19: "exit left", 20: "exit right", 21: "crossroad", 40: "no entry",
    41: "U-turn right", 65: "roundabout slight left",
    66: "roundabout sharp left", 67: "roundabout slight right",
    68: "roundabout straight", 69: "roundabout right",
    70: "roundabout sharp right", 71: "roundabout U-turn",
    82: "roundabout right set 1", 83: "roundabout right set 2",
    84: "roundabout right set 3", 85: "roundabout right set 4",
    86: "roundabout right set 5", 87: "roundabout right set 6",
    88: "roundabout right set 7",
}


def u8(b: bytes, i: int) -> int:
    return b[i] & 0xFF


def u16(b: bytes, i: int) -> int:
    return (u8(b, i) << 8) | u8(b, i + 1)


def u24(b: bytes, i: int) -> int:
    return (u8(b, i) << 16) | (u8(b, i + 1) << 8) | u8(b, i + 2)


def put16(b: bytearray, i: int, value: int) -> None:
    v = max(0, min(0xFFFF, int(value)))
    b[i] = (v >> 8) & 0xFF
    b[i + 1] = v & 0xFF


def put24(b: bytearray, i: int, value: int) -> None:
    v = max(0, min(0xFFFFFF, int(value)))
    b[i] = (v >> 16) & 0xFF
    b[i + 1] = (v >> 8) & 0xFF
    b[i + 2] = v & 0xFF


def parse_hex(text: str) -> list:
    """Parse hex text (spaces, commas, newlines, 0x prefixes) into byte lists."""
    tokens = text.replace(",", " ").replace("\n", " ").replace("\r", " ").replace("0x", " ").split()
    return [[int(t, 16) & 0xFF for t in tokens]]


def to_hex(frame) -> str:
    return " ".join("%02X" % (b & 0xFF) for b in frame)


def is_valid_frame(b) -> bool:
    return len(b) >= FRAME_SIZE and u8(b, 0) in VALID_STARTS and u8(b, len(b) - 1) == END_BYTE


@dataclass
class Telemetry:
    speed_kmh: int = 0
    odometer_km: float = 0.0
    fuel_bars: int = 0
    avg_speed_kmh: int = 0
    power_mode: bool = False
    cluster_top_kmh: int = 0
    throttle: int = 0
    backlight: int = 0
    ignition_on: bool = False
    zero_to_sixty: int = 0
    trip_km: float = 0.0
    rpm: int = 0
    fuel_sensor_fault: bool = False
    vehicle_state: int = 0
    service_due: bool = False
    iss_mode: int = 0
    software_version: int = 0
    mil_blink: int = 0
    vehicle_model: int = 0
    mil_on: bool = False
    isg_blink: int = 0
    turn_signal: int = 0
    connector: int = 0
    cluster_theme: int = 0
    engine_load: int = 0
    injection_time: int = 0
    manifold_pressure: int = 0
    barometric_raw: int = 0
    intake_temp_c: int = 0
    coolant_c: int = 0
    barometric: int = 0
    battery_v: float = 0.0
    engine_run: int = 0
    distance_travelled: int = 0
    injection_volume: int = 0
    iss_duration: int = 0
    ota_status: int = 0
    avg_economy: int = 0
    instant_economy: int = 0
    distance_to_empty: int = 0
    iss_count: int = 0
    frame_count: int = 0
    last_frame: str = "-"


def decode_inbound(frame, current: Optional[Telemetry] = None) -> Optional[Telemetry]:
    """Decode one cluster frame. `current` carries forward unchanged fields."""
    if len(frame) < FRAME_SIZE or u8(frame, 0) not in VALID_STARTS:
        return None
    t = Telemetry() if current is None else Telemetry(**current.__dict__)
    t.frame_count += 1
    t.last_frame = " ".join("%02X" % b for b in frame[:4])
    ident = u8(frame, 1)
    if ident == ID_SPEEDOMETER:
        t.speed_kmh = u8(frame, 2)
        t.odometer_km = u24(frame, 3) / 10.0
        t.fuel_bars = u8(frame, 6) & 0x0F
        t.rpm = u16(frame, 17)
        t.avg_speed_kmh = u8(frame, 7)
        t.power_mode = u8(frame, 8) == 1
        t.cluster_top_kmh = u8(frame, 9)
        t.throttle = u8(frame, 10)
        t.backlight = u8(frame, 11) & 0x0F
        t.ignition_on = (u8(frame, 12) & 0x80) != 0
        t.zero_to_sixty = u8(frame, 13)
        t.trip_km = u24(frame, 14) / 10.0
    elif ident == ID_SERVICE:
        t.fuel_sensor_fault = u8(frame, 2) != 0
        t.vehicle_state = u8(frame, 3)
        t.service_due = u8(frame, 4) != 0
        t.iss_mode = u8(frame, 6)
        t.software_version = u8(frame, 7)
        t.mil_blink = u8(frame, 8)
        t.vehicle_model = u8(frame, 9)
        t.mil_on = (u8(frame, 10) & 0x04) != 0
        t.isg_blink = u8(frame, 11)
        t.turn_signal = u8(frame, 12)
        t.connector = u8(frame, 13)
        t.cluster_theme = u8(frame, 14)
    elif ident == ID_ENGINE:
        t.engine_load = u8(frame, 2)
        t.injection_time = u16(frame, 3)
        t.manifold_pressure = u8(frame, 5)
        t.barometric_raw = u8(frame, 6)
        t.intake_temp_c = u8(frame, 7) - 40
        t.coolant_c = u8(frame, 8) - 40
        t.barometric = u16(frame, 9)
        t.battery_v = u8(frame, 11) * 0.1
        t.engine_run = u16(frame, 12)
        t.distance_travelled = u16(frame, 14)
        t.injection_volume = u16(frame, 16)
    elif ident == ID_ECONOMY:
        t.iss_duration = u24(frame, 2)
        t.ota_status = u8(frame, 5)
        t.avg_economy = u8(frame, 8)
        t.instant_economy = u8(frame, 9)
        t.distance_to_empty = u16(frame, 11)
        t.iss_count = u24(frame, 15)
    else:
        return None
    return t


def _new(start: int, ident: int) -> bytearray:
    b = bytearray(FRAME_SIZE)
    b[0] = start
    b[1] = ident
    b[len(b) - 1] = END_BYTE
    return b


def encode_speedometer(speed=0, odo_km=0.0, fuel_bars=0, avg_speed=0, power=False,
                       top=0, throttle=0, backlight=0, ignition=True, zero60=0,
                       trip_km=0.0, rpm=0):
    b = _new(0x5A, ID_SPEEDOMETER)
    b[2] = max(0, min(255, int(speed)))
    put24(b, 3, int(round(odo_km * 10)))
    b[6] = max(0, min(15, int(fuel_bars)))
    b[7] = max(0, min(255, int(avg_speed)))
    b[8] = 1 if power else 0
    b[9] = max(0, min(255, int(top)))
    b[10] = max(0, min(255, int(throttle)))
    b[11] = max(0, min(15, int(backlight)))
    b[12] = 0x80 if ignition else 0x40
    b[13] = max(0, min(255, int(zero60)))
    put24(b, 14, int(round(trip_km * 10)))
    put16(b, 17, int(rpm))
    return bytes(b)


def encode_service(fuel_fault=False, vehicle_state=0, service_due=False, iss_mode=0,
                   software=0, mil_blink=0, model=0, mil_on=False, isg_blink=0,
                   turn_signal=0, connector=0, theme=0):
    b = _new(0x5A, ID_SERVICE)
    b[2] = 1 if fuel_fault else 0
    b[3] = vehicle_state & 0xFF
    b[4] = 1 if service_due else 0
    b[6] = iss_mode & 0xFF
    b[7] = software & 0xFF
    b[8] = mil_blink & 0xFF
    b[9] = model & 0xFF
    b[10] = 0x04 if mil_on else 0
    b[11] = isg_blink & 0xFF
    b[12] = turn_signal & 0xFF
    b[13] = connector & 0xFF
    b[14] = theme & 0xFF
    return bytes(b)


def encode_engine(load=0, injection_time=0, map_pressure=0, baro_raw=0, intake_c=0,
                  coolant_c=0, baro=0, battery_v=0.0, engine_run=0, distance=0,
                  injection_volume=0):
    b = _new(0x5A, ID_ENGINE)
    b[2] = max(0, min(255, int(load)))
    put16(b, 3, injection_time)
    b[5] = map_pressure & 0xFF
    b[6] = baro_raw & 0xFF
    b[7] = (int(intake_c) + 40) & 0xFF
    b[8] = (int(coolant_c) + 40) & 0xFF
    put16(b, 9, baro)
    b[11] = max(0, min(255, int(round(battery_v * 10))))
    put16(b, 12, engine_run)
    put16(b, 14, distance)
    put16(b, 16, injection_volume)
    return bytes(b)


def encode_economy(iss_duration=0, ota=0, avg_economy=0, instant_economy=0,
                   dte=0, iss_count=0):
    b = _new(0x5A, ID_ECONOMY)
    put24(b, 2, iss_duration)
    b[5] = ota & 0xFF
    b[8] = avg_economy & 0xFF
    b[9] = instant_economy & 0xFF
    put16(b, 11, dte)
    put24(b, 15, iss_count)
    return bytes(b)


def sanitize(text: str, limit: int = 17) -> bytes:
    """Cluster text channel: ASCII only, 17 bytes, padded with 0x00."""
    out = bytearray()
    for c in text:
        if len(out) >= limit:
            break
        out.append(ord(c) if (c.isascii() and (c.isalnum() or c == " ")) else ord("X"))
    out.extend([0] * (limit - len(out)))
    return bytes(out)


def encode_nav_text(text: str, ident: int = ID_NAV_TEXT) -> bytes:
    b = _new(0x5B, ident)
    clean = sanitize(text)
    b[2:19] = clean
    return bytes(b)


def encode_nav_control(distance: int, eta: int, remaining: int, pictogram: int,
                       rows: int = 1, status: int = 1) -> bytes:
    b = _new(0x5A, ID_NAV_CONTROL)
    put16(b, 2, distance)
    put16(b, 4, eta)
    put24(b, 6, remaining)
    b[9] = pictogram & 0xFF
    b[10] = rows & 0xFF
    b[11] = status & 0xFF
    return bytes(b)


def encode_keepalive(hour: int, minute: int, second: int, is_pm: bool,
                     day: int, month: int, year: int, signal_bars: int = 4,
                     battery_pct: int = 100, find_me: bool = False,
                     overspeed: int = 120, ambient_c: int = 0) -> bytes:
    b = _new(0x5B, ID_KEEPALIVE)
    bucket = max(0, min(4, (int(battery_pct) + 24) // 25)) if battery_pct >= 0 else 0
    bars = max(0, min(4, int(signal_bars)))
    b[2] = (bars << 4) | bucket
    b[3] = max(0, min(255, int(overspeed)))
    b[4] = (int(ambient_c) + 40) & 0xFF
    b[6] = (int(hour) if 1 <= hour <= 12 else 12) & 0xFF
    b[7] = int(minute) & 0xFF
    b[8] = int(second) & 0xFF
    b[9] = 1 if is_pm else 0
    b[10] = 0  # missed calls
    b[11] = 4  # network type
    b[12] = int(day) & 0xFF
    b[13] = int(month) & 0xFF
    b[14] = int(year) % 100
    b[17] = 1 if find_me else 0
    return bytes(b)


def decode_outbound(frame) -> Dict[str, Any]:
    """Explain a frame the app wrote (nav control, keepalive, text)."""
    if len(frame) < 2:
        return {"error": "too short"}
    start, ident = u8(frame, 0), u8(frame, 1)
    info: Dict[str, Any] = {"start": "0x%02X" % start, "id": "0x%02X" % ident,
                            "hex": to_hex(frame)}
    if start == 0x5A and ident == ID_NAV_CONTROL:
        d, eta, rem = u16(frame, 2), u16(frame, 4), u24(frame, 6)
        pic = u8(frame, 9)
        info.update({
            "kind": "navigation control",
            "distance_m": d,
            "distance_display": ("%.1fkm" % (d / 1000.0)) if d >= 1000 else ("%dm" % d),
            "eta_min": eta,
            "remaining_m": rem,
            "pictogram": "%d (%s)" % (pic, PICTOGRAMS.get(pic, "unknown")),
            "rows": u8(frame, 10),
            "status": u8(frame, 11),
            "end": "0x%02X" % u8(frame, 19),
        })
    elif start == 0x5B and ident == ID_KEEPALIVE:
        packed = u8(frame, 2)
        info.update({
            "kind": "keepalive / mobile data",
            "signal_bars": packed >> 4,
            "battery_bucket": packed & 0x0F,
            "overspeed": u8(frame, 3),
            "ambient_c": u8(frame, 4) - 40,
            "clock": "%02d:%02d:%02d %s" % (u8(frame, 6), u8(frame, 7), u8(frame, 8),
                                             "PM" if u8(frame, 9) else "AM"),
            "date": "%02d/%02d/20%02d" % (u8(frame, 12), u8(frame, 13), u8(frame, 14)),
            "find_me": u8(frame, 17) == 1,
            "byte18": u8(frame, 18),
            "end": "0x%02X" % u8(frame, 19),
        })
    elif start == 0x5B and ident in (ID_NAV_TEXT, 0x50, 0x4C, 0x63, 0x52, 0x53):
        raw = bytes(frame[2:19])
        text = "".join(chr(c) if 32 <= c < 127 else "." for c in raw).rstrip("\x00")
        info.update({"kind": "text row", "id": "0x%02X" % ident, "text": text,
                     "end": "0x%02X" % u8(frame, 19)})
    else:
        info["kind"] = "unrecognised frame"
    return info


# Golden vectors shared with the JVM tests (same bytes, same expected values).
GOLDEN_VECTORS = [
    {
        "name": "speedometer",
        "hex": to_hex(encode_speedometer(speed=42, odo_km=5498.4, fuel_bars=4,
                                         avg_speed=12, power=True, top=68,
                                         throttle=18, backlight=2, ignition=True,
                                         zero60=55, trip_km=1534.4, rpm=3578)),
        "expect": {"speed_kmh": 42, "odometer_km": 5498.4, "fuel_bars": 4,
                   "rpm": 3578, "avg_speed_kmh": 12, "power_mode": True,
                   "cluster_top_kmh": 68, "trip_km": 1534.4, "ignition_on": True},
    },
    {
        "name": "engine",
        "hex": to_hex(encode_engine(load=30, intake_c=35, coolant_c=76,
                                    battery_v=12.6, baro_raw=94)),
        "expect": {"intake_temp_c": 35, "coolant_c": 76, "battery_v": 12.6,
                   "engine_load": 30, "barometric_raw": 94},
    },
    {
        "name": "economy",
        "hex": to_hex(encode_economy(avg_economy=60, instant_economy=48, dte=0)),
        "expect": {"avg_economy": 60, "instant_economy": 48, "distance_to_empty": 0},
    },
    {
        "name": "service",
        "hex": to_hex(encode_service(service_due=True, software=7, model=163,
                                     theme=1, mil_on=True)),
        "expect": {"service_due": True, "software_version": 7, "vehicle_model": 163,
                   "cluster_theme": 1, "mil_on": True},
    },
]


def self_test() -> bool:
    ok = True
    for v in GOLDEN_VECTORS:
        frame = parse_hex(v["hex"])[0]
        decoded = decode_inbound(frame)
        if decoded is None:
            print("FAIL %s: frame did not decode" % v["name"])
            ok = False
            continue
        for key, expected in v["expect"].items():
            actual = getattr(decoded, key)
            good = abs(actual - expected) < 0.02 if isinstance(expected, float) else actual == expected
            if not good:
                print("FAIL %s.%s: expected %r, got %r" % (v["name"], key, expected, actual))
                ok = False
    nav = decode_outbound(encode_nav_control(40, 18, 8628, 7))
    if nav["distance_display"] != "40m" or not nav["pictogram"].startswith("7 ("):
        print("FAIL nav control decode: %r" % nav)
        ok = False
    keep = decode_outbound(encode_keepalive(9, 36, 15, True, 17, 11, 26,
                                            signal_bars=4, battery_pct=48))
    if keep["signal_bars"] != 4 or keep["battery_bucket"] != 2 or keep["byte18"] != 0:
        print("FAIL keepalive decode: %r" % keep)
        ok = False
    if ok:
        print("All simulator self-tests passed (%d vectors + outbound checks)." % len(GOLDEN_VECTORS))
    return ok


if __name__ == "__main__":
    import sys
    sys.exit(0 if self_test() else 1)
