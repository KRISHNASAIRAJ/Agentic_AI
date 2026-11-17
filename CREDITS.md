# Credits and licensing

Jupiter Connect is a non-commercial interoperability project. The BLE protocol
knowledge it uses was not invented here — it was derived from public research
by others, and that work is credited below.

## Primary source

**[athulkrishna2015/TVSRideCompanion](https://github.com/athulkrishna2015/TVSRideCompanion)**
— open-source companion app for TVS SmartXonnect clusters. Its
`docs/PROTOCOL.md`, `docs/NAVIGATION.md`, `docs/CAPTURE.md` and source code
provided the GATT UUIDs, frame layouts, decoder offsets, navigation bridge
design, keepalive/checksum rules and reliability patterns used here. That
project is licensed **CC BY-NC 4.0**, and its protocol documentation reuses
material from the Jupiter companion below under the same non-commercial terms.
This project follows the same non-commercial restriction.

## Foundational reverse engineering

**[overclock98/JupiterRideCompanion](https://github.com/overclock98/JupiterRideCompanion)**
(pinned commit `47eed62aba740b278770006bc27f0822584b9de8`, **CC BY-NC 4.0**) —
its `REVERSE_ENGINEERING_REPORT.md` and working APK supplied the proven
navigation packet layout, pictogram tables, AES-CTR cross-check and the
doze-bypassing connection architecture. The repository was later disabled by
GitHub; the attribution is preserved here.

**[aaravavi/TVS-Connect-Application-VAPT](https://github.com/aaravavi/TVS-Connect-Application-VAPT)**
(**CC0 1.0**) — FEV LTD's pentest. CVE-2024-35537 established that the client
secrets are hardcoded; this informed the decision to treat the BLE handshake
as protocol fidelity rather than security.

**[sudo-Mystic/Scoot](https://github.com/sudo-Mystic/Scoot) release feed** —
its public simulator specification and dex-verified Jupiter byte tables were
the second independent source for the telemetry layouts. No code from the
simulator is redistributed here.

**[Hyundai-Kia-Connect/kia_uvo.wiki](https://github.com/Hyundai-Kia-Connect/kia_uvo.wiki)**
(**CC0**) — methodology only: intercept, observe, reimplement.

Community discussions on r/indianbikes and TechEnclave documented the current
state of the art and the Google-Maps-notification navigation approach.

## What we wrote ourselves

- The Android project structure, Gradle setup and GitHub Actions workflow
- The Meridian-derived dark design system and all five Compose screens
- The Kotlin implementation of the BLE transport, decoder, notification
  listener and navigation bridge
- The explainer document and PowerPoint deck

## Trademarks

TVS, Jupiter and SmartXonnect are trademarks of TVS Motor Company. Jupiter
Connect is unaffiliated and makes no claim of endorsement.
