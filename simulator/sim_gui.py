"""Windows GUI for the Jupiter Connect TVS protocol simulator.

Run:  python sim_gui.py
No third-party dependencies (uses tkinter from the standard library).
"""

import tkinter as tk
from tkinter import ttk, messagebox
import datetime

try:
    import tvs_protocol as tv
except ImportError:
    import sys
    import os
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import tvs_protocol as tv


class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Jupiter Connect - TVS Cluster Simulator")
        self.geometry("980x720")
        self.minsize(820, 600)
        self.columnconfigure(0, weight=1)
        self.rowconfigure(0, weight=1)

        nb = ttk.Notebook(self)
        nb.grid(row=0, column=0, sticky="nsew", padx=8, pady=8)
        self.tab_telemetry = ttk.Frame(nb)
        self.tab_decode = ttk.Frame(nb)
        self.tab_outbound = ttk.Frame(nb)
        self.tab_icons = ttk.Frame(nb)
        self.tab_test = ttk.Frame(nb)
        for tab, label in [
            (self.tab_telemetry, "Cluster telemetry"),
            (self.tab_decode, "Decode inbound"),
            (self.tab_outbound, "Decode app frames"),
            (self.tab_icons, "Pictogram / icon test"),
            (self.tab_test, "Self test"),
        ]:
            nb.add(tab, text=label)

        self._build_telemetry()
        self._build_decode()
        self._build_outbound()
        self._build_icons()
        self._build_test()

    # -------------------------------------------------------------- helpers
    def _copy(self, text):
        self.clipboard_clear()
        self.clipboard_append(text)
        messagebox.showinfo("Copied", "Hex copied to clipboard.")

    def _field(self, parent, label, default, row, col):
        ttk.Label(parent, text=label).grid(row=row, column=col, sticky="w", padx=6, pady=2)
        v = tk.StringVar(value=str(default))
        ttk.Entry(parent, textvariable=v, width=11).grid(row=row, column=col + 1, padx=6, pady=2)
        return v

    def _num(self, var, default=0):
        try:
            return float(var.get())
        except (ValueError, TypeError):
            return default

    # ------------------------------------------------------------- telemetry
    def _build_telemetry(self):
        p = self.tab_telemetry
        p.columnconfigure(0, weight=1)
        p.rowconfigure(0, weight=1)
        left = ttk.LabelFrame(p, text="Speedometer (0x10)")
        left.grid(row=0, column=0, sticky="nsew", padx=6, pady=6)
        right = ttk.LabelFrame(p, text="Engine (0x18) / Economy (0x19) / Service (0x11)")
        right.grid(row=0, column=1, sticky="nsew", padx=6, pady=6)
        p.columnconfigure(1, weight=1)

        self.t = {}
        specs = [
            ("speed km/h", 42), ("odometer km", 5498.4), ("fuel bars", 4),
            ("avg speed", 12), ("top speed", 68), ("throttle %", 18),
            ("backlight", 2), ("0-60 raw", 55), ("trip km", 1534.4), ("rpm", 3578),
        ]
        for i, (label, default) in enumerate(specs):
            self.t[label] = self._field(left, label, default, i, 0)
        self.t["power"] = tk.BooleanVar(value=True)
        self.t["ignition"] = tk.BooleanVar(value=True)
        ttk.Checkbutton(left, text="POWER mode", variable=self.t["power"]).grid(
            row=0, column=2, sticky="w", padx=6)
        ttk.Checkbutton(left, text="Ignition ON", variable=self.t["ignition"]).grid(
            row=1, column=2, sticky="w", padx=6)

        e = {}
        specs2 = [
            ("engine load %", 30), ("intake °C", 35), ("coolant °C", 76),
            ("battery V", 12.6), ("baro raw", 94), ("inj time", 34560),
            ("avg economy", 60), ("inst economy", 48), ("range km", 0),
            ("service due", 1), ("software", 7), ("model", 163), ("theme", 1),
        ]
        for i, (label, default) in enumerate(specs2):
            e[label] = self._field(right, label, default, i, 0)
        self.e = e
        self.t["mil"] = tk.BooleanVar(value=False)
        ttk.Checkbutton(right, text="MIL (check engine) ON", variable=self.t["mil"]).grid(
            row=13, column=0, sticky="w", padx=6, pady=2)

        btn = ttk.Frame(p)
        btn.grid(row=1, column=0, columnspan=2, sticky="w", padx=6, pady=4)
        ttk.Button(btn, text="Build 4 frames", command=self.build_frames).grid(row=0, column=0, padx=4)
        ttk.Button(btn, text="Ride step (+1 km)", command=self.ride_step).grid(row=0, column=1, padx=4)
        ttk.Button(btn, text="Copy hex", command=lambda: self._copy(self.out.get("1.0", "end").strip())).grid(
            row=0, column=2, padx=4)

        self.out = tk.Text(p, height=9, font=("Consolas", 10))
        self.out.grid(row=2, column=0, columnspan=2, sticky="nsew", padx=6, pady=6)
        p.rowconfigure(2, weight=2)
        self.build_frames()

    def build_frames(self):
        t = self.t
        frames = [
            ("0x10 speedometer", tv.encode_speedometer(
                speed=int(self._num(t["speed km/h"])), odo_km=self._num(t["odometer km"]),
                fuel_bars=int(self._num(t["fuel bars"])), avg_speed=int(self._num(t["avg speed"])),
                power=t["power"].get(), top=int(self._num(t["top speed"])),
                throttle=int(self._num(t["throttle %"])), backlight=int(self._num(t["backlight"])),
                ignition=t["ignition"].get(), zero60=int(self._num(t["0-60 raw"])),
                trip_km=self._num(t["trip km"]), rpm=int(self._num(t["rpm"])))),
            ("0x18 engine", tv.encode_engine(
                load=int(self._num(self.e["engine load %"])), intake_c=int(self._num(self.e["intake °C"])),
                coolant_c=int(self._num(self.e["coolant °C"])), battery_v=self._num(self.e["battery V"]),
                baro_raw=int(self._num(self.e["baro raw"])), injection_time=int(self._num(self.e["inj time"])))),
            ("0x19 economy", tv.encode_economy(
                avg_economy=int(self._num(self.e["avg economy"])),
                instant_economy=int(self._num(self.e["inst economy"])),
                dte=int(self._num(self.e["range km"])))),
            ("0x11 service", tv.encode_service(
                service_due=self._num(self.e["service due"]) != 0,
                software=int(self._num(self.e["software"])), model=int(self._num(self.e["model"])),
                theme=int(self._num(self.e["theme"])), mil_on=t["mil"].get())),
        ]
        self.out.delete("1.0", "end")
        for name, frame in frames:
            self.out.insert("end", "%s: %s\n" % (name, tv.to_hex(frame)))

    def ride_step(self):
        odo = self._num(self.t["odometer km"]) + 1.0
        trip = self._num(self.t["trip km"]) + 1.0
        self.t["odometer km"].set("%.1f" % odo)
        self.t["trip km"].set("%.1f" % trip)
        self.build_frames()

    # ---------------------------------------------------------------- decode
    def _build_decode(self):
        p = self.tab_decode
        p.columnconfigure(0, weight=1)
        p.rowconfigure(1, weight=1)
        ttk.Label(p, text="Paste inbound frames (one per line, from cluster / frame log):").grid(
            row=0, column=0, sticky="w", padx=6, pady=4)
        self.in_text = tk.Text(p, height=8, font=("Consolas", 10))
        self.in_text.grid(row=1, column=0, sticky="nsew", padx=6, pady=4)
        ttk.Button(p, text="Decode", command=self.decode_inbound).grid(row=2, column=0, sticky="w", padx=6, pady=4)
        self.dec_out = tk.Text(p, height=16, font=("Consolas", 10))
        self.dec_out.grid(row=3, column=0, sticky="nsew", padx=6, pady=4)
        p.rowconfigure(3, weight=3)

    def decode_inbound(self):
        self.dec_out.delete("1.0", "end")
        current = None
        for line in self.in_text.get("1.0", "end").splitlines():
            frames = tv.parse_hex(line)
            for frame in frames:
                decoded = tv.decode_inbound(frame, current)
                if decoded is None:
                    self.dec_out.insert("end", "REJECTED (not a known telemetry frame): %s\n" % tv.to_hex(frame))
                    continue
                current = decoded
                for key, value in decoded.__dict__.items():
                    if key in ("frame_count", "last_frame"):
                        continue
                    self.dec_out.insert("end", "%-22s %s\n" % (key, value))
                self.dec_out.insert("end", "\n")

    # ------------------------------------------------------------- outbound
    def _build_outbound(self):
        p = self.tab_outbound
        p.columnconfigure(0, weight=1)
        p.rowconfigure(1, weight=1)
        ttk.Label(p, text="Paste frames the app wrote (from the app's frame log):").grid(
            row=0, column=0, sticky="w", padx=6, pady=4)
        self.out_text = tk.Text(p, height=8, font=("Consolas", 10))
        self.out_text.grid(row=1, column=0, sticky="nsew", padx=6, pady=4)
        ttk.Button(p, text="Explain", command=self.explain_outbound).grid(row=2, column=0, sticky="w", padx=6, pady=4)
        self.out_dec = tk.Text(p, height=16, font=("Consolas", 10))
        self.out_dec.grid(row=3, column=0, sticky="nsew", padx=6, pady=4)
        p.rowconfigure(3, weight=3)

    def explain_outbound(self):
        self.out_dec.delete("1.0", "end")
        for line in self.out_text.get("1.0", "end").splitlines():
            for frame in tv.parse_hex(line):
                info = tv.decode_outbound(frame)
                for key, value in info.items():
                    self.out_dec.insert("end", "%-20s %s\n" % (key, value))
                self.out_dec.insert("end", "\n")

    # ---------------------------------------------------------------- icons
    def _build_icons(self):
        p = self.tab_icons
        p.columnconfigure(0, weight=1)
        p.rowconfigure(1, weight=1)
        ttk.Label(p, text="Pictogram IDs from the TVS table. Select one to build a nav control frame:").grid(
            row=0, column=0, sticky="w", padx=6, pady=4)
        self.icon_list = tk.Listbox(p, font=("Consolas", 11))
        for ident, name in sorted(tv.PICTOGRAMS.items()):
            self.icon_list.insert("end", "%3d  %s" % (ident, name))
        self.icon_list.grid(row=1, column=0, sticky="nsew", padx=6, pady=4)
        self.icon_list.selection_set(0)
        ctl = ttk.Frame(p)
        ctl.grid(row=2, column=0, sticky="w", padx=6, pady=4)
        self.nav_dist = self._field(ctl, "distance m", 40, 0, 0)
        self.nav_eta = self._field(ctl, "ETA min", 18, 0, 2)
        self.nav_rem = self._field(ctl, "remaining m", 8628, 0, 4)
        ttk.Button(ctl, text="Build frame", command=self.build_icon).grid(row=0, column=6, padx=6)
        ttk.Button(ctl, text="Copy hex", command=lambda: self._copy(self.icon_out.get("1.0", "end").strip())).grid(
            row=0, column=7, padx=4)
        self.icon_out = tk.Text(p, height=3, font=("Consolas", 11))
        self.icon_out.grid(row=3, column=0, sticky="ew", padx=6, pady=4)
        self.build_icon()

    def build_icon(self):
        sel = self.icon_list.curselection()
        ident = sorted(tv.PICTOGRAMS.keys())[sel[0]] if sel else 7
        frame = tv.encode_nav_control(
            int(self._num(self.nav_dist)), int(self._num(self.nav_eta)),
            int(self._num(self.nav_rem)), ident)
        self.icon_out.delete("1.0", "end")
        self.icon_out.insert("end", tv.to_hex(frame))

    # ----------------------------------------------------------------- test
    def _build_test(self):
        p = self.tab_test
        ttk.Label(p, text="Verifies the Python codec against its golden vectors.\n"
                          "The same vectors are asserted by the JVM tests in ProtocolTest.kt.",
                  justify="left").pack(anchor="w", padx=10, pady=10)
        self.test_out = tk.Text(p, height=18, font=("Consolas", 10))
        self.test_out.pack(fill="both", expand=True, padx=10, pady=6)
        ttk.Button(p, text="Run self test", command=self.run_test).pack(anchor="w", padx=10, pady=6)

    def run_test(self):
        import io
        from contextlib import redirect_stdout
        buf = io.StringIO()
        with redirect_stdout(buf):
            ok = tv.self_test()
        self.test_out.delete("1.0", "end")
        self.test_out.insert("end", buf.getvalue())
        messagebox.showinfo("Self test", "PASSED" if ok else "FAILED - see output")


if __name__ == "__main__":
    App().mainloop()
