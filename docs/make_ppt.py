"""Generate the explainer deck: how TVSRide was built and what we copied."""
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pathlib import Path

BG = RGBColor(0x12, 0x12, 0x18)
SURFACE = RGBColor(0x1E, 0x1E, 0x2A)
PRIMARY = RGBColor(0x7B, 0x8E, 0xFF)
TEAL = RGBColor(0x4F, 0xDB, 0xCC)
TEXT = RGBColor(0xE4, 0xE2, 0xF5)
MUTED = RGBColor(0xA8, 0xA6, 0xC0)

prs = Presentation()
prs.slide_width = Inches(13.333)
prs.slide_height = Inches(7.5)
blank = prs.slide_layouts[6]


def add_slide(title, lines, accent=PRIMARY, sub=None):
    slide = prs.slides.add_slide(blank)
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = BG

    bar = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.7), Inches(0.42), Inches(0.12), Inches(0.62))
    bar.fill.solid()
    bar.fill.fore_color.rgb = accent
    bar.line.fill.background()

    tb = slide.shapes.add_textbox(Inches(1.0), Inches(0.36), Inches(11.6), Inches(0.9))
    tf = tb.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.text = title
    p.font.size = Pt(30)
    p.font.bold = True
    p.font.color.rgb = TEXT

    if sub:
        sb = slide.shapes.add_textbox(Inches(1.0), Inches(1.15), Inches(11.6), Inches(0.5))
        sp = sb.text_frame.paragraphs[0]
        sp.text = sub
        sp.font.size = Pt(15)
        sp.font.color.rgb = MUTED

    body = slide.shapes.add_textbox(Inches(1.0), Inches(1.75 if sub else 1.5), Inches(11.5), Inches(5.4))
    bf = body.text_frame
    bf.word_wrap = True
    for i, line in enumerate(lines):
        p = bf.paragraphs[0] if i == 0 else bf.add_paragraph()
        if line.startswith("## "):
            p.text = line[3:]
            p.font.size = Pt(18)
            p.font.bold = True
            p.font.color.rgb = accent
            p.space_before = Pt(10)
        else:
            p.text = "•  " + line
            p.font.size = Pt(16)
            p.font.color.rgb = TEXT
            p.space_after = Pt(7)
    return slide


add_slide(
    "How TVSRide was built — and what Jupiter Connect inherits",
    [
        "A reverse-engineering case study, condensed into a repeatable workflow",
        "Sources: official TVS Connect APK, a working Jupiter companion app, a security pentest, community captures",
        "Output: a BLE-only Android app with no TVS account and no TVS cloud",
    ],
    sub="12 slides · companion to HOW_THEY_BUILT_IT.md",
)

add_slide(
    "1 · The problem",
    [
        "TVS Connect fails at its one essential job: holding the Bluetooth link to the cluster",
        "The app forces an account and a cloud round-trip for features that happen locally",
        "Key insight: the instrument cluster is just a BLE peripheral",
        "Every arrow, street name, odometer value is a 20-byte GATT packet",
        "If the phone is physically on the scooter, the TVS backend is irrelevant",
    ],
    accent=RGBColor(0xFF, 0x6B, 0x6B),
    sub="The complaint that started the project",
)

add_slide(
    "2 · Four information streams — no guessing",
    [
        "Official TVS Connect APK (v8.14.1): decompiled statically for UUIDs, frame layouts, AES key, text rules",
        "overclock98/JupiterRideCompanion: reverse-engineering report plus a working APK — proven navigation frames",
        "FEV pentest, CVE-2024-35537: proved the BLE auth key is hardcoded — no real security to defeat",
        "TechEnclave / Reddit and the Scoot simulator spec: community state of the art and Jupiter byte tables",
        "Rule: two independent sources agreeing = fact. Disagreements are recorded side by side.",
    ],
    sub="Every fact carries a provenance tag: VERIFIED_FROM_APK / INHERITED_UNVERIFIED / NEEDS_CAPTURE",
)

add_slide(
    "3 · The workflow",
    [
        "## Research first",
        "Extract APKs as specifications; build a slim 2-DEX APK to fit the decompiler timeout",
        "## Write the protocol down",
        "PROTOCOL.md and NAVIGATION.md record byte offsets, endianness and open questions before any UI",
        "## Simulate, then implement",
        "BlueZ GATT peripheral advertises the real UUIDs; the app connects unchanged",
        "## Capture on hardware",
        "Hex-dump every frame in and out; let the dash correct the docs; pin each fix with a JVM test",
        "## Release with honesty",
        "Document what is still unverified instead of implying it works",
    ],
    accent=TEAL,
)

add_slide(
    "4 · The architecture they converged on",
    [
        "## Inbound",
        "cluster → BLE notify → frame decoder → ProtocolDriver → Session StateFlow → Compose UI",
        "## Outbound — navigation",
        "maps notification → NotificationListenerService → NavInstruction → NavigationBridge → driver → BLE write",
        "## Layers",
        "protocol/ (byte layouts) · transport/ (BLE, service, reconnect) · nav/ · session/ · prefs/ · log/ · ui/",
        "A model with no driver still connects and streams raw frames for later analysis",
    ],
    sub="One driver per vehicle family; the transport knows nothing about byte meanings",
)

add_slide(
    "5 · The BLE protocol in a nutshell",
    [
        "Service 5456534D-5647-5341-5342-454E544F5251 (ASCII 'TVSM-VGSASBEN TORQ')",
        "Write characteristic 00005352… / notify characteristic 00005354…; MTU 512; write without response",
        "Every frame is 20 bytes: 2-byte header, 17-byte payload, 0xFF terminator",
        "Keepalive 5B 4A every 2 s carries clock, signal, battery and the find-me flag",
        "Navigation: 5A 4E control (distance, ETA, pictogram) + 5B 4F text row (17 ASCII chars)",
        "Handshake is AES-128-CTR with a hardcoded key — protocol theatre, not security",
    ],
)

add_slide(
    "6 · Telemetry decoding",
    [
        "0x10: byte 2 speed; bytes 3–5 odometer, big-endian, ÷10; byte 6 fuel (low nibble on 125)",
        "0x10: bytes 17–18 RPM; byte 12 switch/ignition on Jupiter",
        "0x18: bytes 7/8 intake and engine temperature (+40); byte 11 battery voltage × 0.1",
        "0x19: byte 8 average fuel economy; bytes 11–12 distance to empty",
        "Big-endian everywhere — settled by the vendor's own parser and a photo of the dash",
        "Cluster cannot render non-ASCII: emoji → @, anything outside [A-Za-z0-9 ] → X",
    ],
    accent=TEAL,
)

add_slide(
    "7 · Navigation mirroring — the account-free trick",
    [
        "The official app itself reads maps notifications; the cluster knows nothing about maps",
        "NotificationListenerService reads title / text / bigText from Google Maps, MapMyIndia, Waze…",
        "A parser normalises maneuver, street, distance and pictogram into one NavInstruction",
        "NavigationBridge re-sends the control frame every 1.5 s — it is a liveness signal, not a one-shot",
        "Text rows compress to short codes and roll when longer than the cluster's 10/12-character rows",
        "Notification access is a Settings toggle, not a runtime permission — no grant means silent failure",
    ],
)

add_slide(
    "8 · Reliability lessons worth stealing",
    [
        "Serialise all GATT writes with a mutex — concurrent writes cause status-133 disconnects",
        "Finish the MTU exchange before service discovery, or onServicesDiscovered never fires",
        "Close the GATT client on disconnect, or every later reconnect is refused as 'link already active'",
        "Reconnect with backoff: 5/10/20/40/60 s, ten attempts",
        "Foreground service + wake lock only while connected; Doze otherwise kills a pocketed ride",
        "Pace the frame log per data ID — a 10 Hz stream otherwise buries the fault you need to see",
    ],
    accent=RGBColor(0xFF, 0xB8, 0x6B),
)

add_slide(
    "9 · Hardware corrected the docs — repeatedly",
    [
        "Endianness: a photo of the dash showed 16,593 km, not 278,733 km",
        "Fuel: the dash said FUEL EMPTY while byte 6 decoded as a full tank",
        "Clock: the keepalive sent midnight every 2 seconds and reset the cluster time",
        "Navigation looked end-to-end on the phone but had never once written a BLE frame",
        "A single dropped link made reconnection impossible for the life of the process",
        "Motto: do not trust a screen that looks right — only bytes on the wire settle a fact",
    ],
    accent=RGBColor(0xFF, 0x6B, 0x6B),
)

add_slide(
    "10 · What we built in Jupiter Connect",
    [
        "## Done",
        "Standalone Android app in D:\\JupiterConnect, with the Meridian dark palette",
        "Five Compose screens + bottom navigation; static mockups and rendered preview",
        "GATT constants, Telemetry model, frame decoder, keepalive/nav packet builders",
        "Foreground BLE service: scan by TVSBT name, connect, MTU, notify, 2 s keepalive, reconnect",
        "Live Home screen: speed, odometer, fuel bars, engine/intake temp, battery, range, frame count",
        "## Verified",
        ":app:assembleDebug → BUILD SUCCESSFUL",
    ],
    accent=TEAL,
)

add_slide(
    "11 · Next steps",
    [
        "NotificationListenerService → NavInstruction → 5A 4E / 5B 4F navigation bridge",
        "Persist rides and last-parked location; wire the Rides and Find screens to live data",
        "Rider-name frame 5B 52 and find-me flag verification on the real scooter",
        "Hardware checklist: ignition ON, MODE 5 s to BT PAIR, unfiltered scan for TVSBT…",
        "Capture a live navigation log; confirm arrows on the cluster; fix pictogram table if needed",
    ],
    sub="Verification, not assumption, is the last step",
)

out = Path(__file__).parent / "how-it-was-built.pptx"
prs.save(out)
print(f"Wrote {out} with {len(prs.slides.__iter__.__self__._sldIdLst)} slides")
