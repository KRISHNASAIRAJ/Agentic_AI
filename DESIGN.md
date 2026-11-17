---
name: Jupiter Connect
colors:
  # ── Meridian dark surfaces (canonical, reuse exactly) ──────
  surface: '#121218'
  surface-dim: '#0e0e14'
  surface-bright: '#1e1e26'
  surface-container-lowest: '#0a0a10'
  surface-container-low: '#161620'
  surface-container: '#1e1e2a'
  surface-container-high: '#252532'
  surface-container-highest: '#2d2d3c'
  surface-tint: '#7b8eff'
  surface-variant: '#2a2a3a'
  on-surface: '#e4e2f5'
  on-surface-variant: '#a8a6c0'
  on-background: '#e4e2f5'
  outline: '#4a4a62'
  outline-variant: '#2e2e42'
  # ── Primary (periwinkle) — actions, active nav, navigation ──
  primary: '#7b8eff'
  on-primary: '#0a1060'
  primary-container: '#2346d5'
  on-primary-container: '#dde0ff'
  # ── Secondary (muted blue-gray) — metadata, labels ─────────
  secondary: '#8a9099'
  on-secondary: '#1a1e23'
  secondary-container: '#252b32'
  on-secondary-container: '#b8c0c8'
  # ── Tertiary (teal) — connected, success, completed ─────────
  tertiary: '#4fdbcc'
  on-tertiary: '#00201d'
  tertiary-container: '#005049'
  on-tertiary-container: '#70f8e8'
  # ── Error (red) — disconnected, faults ──────────────────────
  error: '#ff6b6b'
  on-error: '#5c0000'
  error-container: '#7a1a1a'
  on-error-container: '#ffdad6'
  # ── Warning (amber) — low fuel, weak signal ─────────────────
  warning: '#ffb86b'
  on-warning: '#3a1e00'
  background: '#121218'
typography:
  display-data:
    fontFamily: Inter
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: -0.02em
    # ONE per screen: speed, navigation distance
  headline-lg:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
    # UPPERCASE section headers
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
rounded:
  sm: 4px
  DEFAULT: 8px
  md: 12px
  lg: 16px
  xl: 24px
  full: 9999px
spacing:
  container-padding: 16px
  stack-gap-sm: 8px
  stack-gap-md: 16px
  stack-gap-lg: 24px
  card-padding: 16px
  grid-gutter: 12px
---

# Jupiter Connect — Design System

## Brand & style

**Jupiter Connect** is the companion app for a 2026 TVS Jupiter 125. It bridges
the phone and the scooter's instrument cluster: connect once, mirror navigation
from any maps app, see ride data, find the scooter.

The UI inherits the **Meridian dark palette exactly** — shared tokens, shared
typography, shared spacing — so both apps feel like the same family. **Dark
mode only** at launch. No light-mode fallbacks.

**Design personality:** premium, instrumental, calm. Data-dense but never
cluttered. Red is not used here; the periwinkle `#7b8eff` is the action colour,
teal `#4fdbcc` means connected/success, and red `#ff6b6b` is reserved strictly
for disconnected/fault states.

## Semantic colour roles (locked)

| Meaning | Token | Hex | Where |
|---|---|---|---|
| Primary action / active / navigation | `primary` | `#7b8eff` | Connect, active tab, navigation HUD |
| Connected / success / completed | `tertiary` | `#4fdbcc` | Connection pill, completed ride |
| Disconnected / fault | `error` | `#ff6b6b` | Offline pill, failed packet |
| Low fuel / weak signal | `warning` | `#ffb86b` | Fuel tile, signal indicator |
| Pending / neutral / metadata | `secondary` | `#8a9099` | Idle state, timestamps |

## Screen structure

```
┌─────────────────────────┐  status bar (system, transparent on dark)
│   Top App Bar (56dp)    │  sticky, bg-surface, no divider
├─────────────────────────┤
│   Scrollable content    │  px-16, gap-16, pb-24
│                         │
├─────────────────────────┤
│  Bottom Navigation 56dp │  5 tabs, bg-surface, border-t outline-variant
└─────────────────────────┘
```

## Components

### Connection hero card
`surface-container` bg, 1px `outline-variant` border, 16px radius. Top: scooter
name + status pill (`CONNECTED` teal / `DISCONNECTED` red). Centre: one
`display-data` value (speed in km/h, or "Connected"). Bottom: three compact
metrics — odometer, fuel, battery — separated by hairlines.

### Navigation HUD card
`surface-container-low` bg, 16px radius. Left: 48dp `primary-container` tile
with a turn arrow icon in `on-primary-container`. Right: distance in
`display-data`, street in `body-lg`. A thin 4px progress track in `primary`
shows distance to turn. Secondary row: ETA and remaining distance in
`label-md`, uppercase labels in `secondary`.

**Mirror toggle** sits directly under the HUD: a `primary` switch with the
state label `Mirroring to cluster` / `Mirror off`.

### Metric tiles
Two-column grid, 12px gutter. Each tile: `surface-container` bg, 16px radius,
16px padding. Icon 20dp in the semantic colour, value in `headline-md`, label
in `label-md` uppercase. Two lines maximum.

### Quick actions
Pill buttons, 999px radius. Primary filled `primary` with `on-primary` text.
Secondary outlined `outline` with `on-surface` text. Minimum 48dp height.

### Ride list row
`surface-container` bg, 12px radius, 12px padding. Left: date (`body-md`) +
distance (`headline-md`). Right: duration in `label-md` + chevron in
`secondary`.

### Permission checklist (Settings)
Rows with a status icon: granted = `tertiary` check, missing = `error` circle.
Label in `body-md`, hint in `body-md` `on-surface-variant`. Tapping a missing
row opens the relevant system setting.

### Bottom navigation
Five tabs: **Home · Navigate · Rides · Find · Settings**. Active icon+label in
`primary`; inactive in `secondary`. 56dp + safe area.

## Screens

1. **Home — Connection**: connection hero, quick actions (Connect / Find /
   Mirror), live telemetry tiles, last ride summary.
2. **Navigate**: navigation HUD, mirror toggle, frame counter, current map
   provider, turn preview list.
3. **Rides**: ride history, one-tap detail sheet.
4. **Find**: last-parked card with location, big locate CTA, find-me feedback.
5. **Settings**: permission checklist, model selector, about.

## Typography rules
- One `display-data` per screen (speed on Home, distance on Navigate).
- Uppercase `label-md` for section headers ("LIVE TELEMETRY", "RECENT RIDES").
- All text ≥ 4.5:1 contrast on dark surfaces.

## Motion
- Connection pulse: 1.6s, opacity 1 → 0.45 → 1 on the status dot.
- Card press: scale 0.98 over 120ms.
- Screen change: 200ms fade + 8dp slide.

## Accessibility
Touch targets ≥ 48dp. Status never colour-only — always pair colour with a
label or icon.
