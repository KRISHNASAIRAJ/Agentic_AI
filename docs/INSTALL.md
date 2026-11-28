# Install without disabling Play Protect

Play Protect blocks brand-new APKs tapped from a file manager because they have
no reputation with Google. This is not caused by a bug in Jupiter Connect.

**How TVSRide avoided it — checked directly from their repository:** their
README's Download section says *"install `app-debug.apk` (`adb install -r
app-debug.apk`)"*, and their GitHub Release notes repeat *"Install with `adb
install -r app-debug.apk`"*. They never ask users to tap the APK in a file
manager. Their CI also materialises one persistent signing key from secrets so
that `adb install -r` upgrades cleanly instead of failing with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

So there is no trick we are missing: ADB is their documented install path too.

There is no code change that can make Google trust a new sideloaded app
instantly. Only the Play Store signing/review process does that. But you do
**not** need to turn Play Protect off:

## Recommended: ADB install (Play Protect stays ON)

1. On the phone: **Settings → About phone → tap "Build number" 7 times**.
2. **Settings → System → Developer options → USB debugging → ON**.
3. Connect by USB and accept **"Allow USB debugging"**.
4. From `D:\JupiterConnect`:

```powershell
.\install.ps1
```

Or manually:

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

ADB installs are trusted by Android, so the "App blocked" dialog does not
appear and you never have to disable Play Protect.

## Why tapping the APK triggers the warning

| Trigger | Our app | TVS companion |
|---|---|---|
| Signed with shared Android Debug key | Debug build only; release uses a unique key | Debug key too |
| Reputation / number of prior scans | New APK, none | Widely downloaded |
| Bluetooth + location + notification access | Yes | Yes, plus more |
| Install method | Tapped in file manager | Likely ADB |

The sensitive permissions are required for the features: Bluetooth for the
cluster, notification access to read turn-by-turn from Google Maps, foreground
service to keep the link alive when the screen is off.

## If you still want to tap the APK

The release build (`app-release.apk`) is signed with your own unique keystore
(`signing/jupiter.jks`, gitignored), which removes the shared-debug-key
trigger. Play Protect may still show a one-time warning for an unknown
developer. The reliable alternatives are the ADB install above or temporarily
turning the scanner off — but ADB is preferred since nothing is disabled.

## "App not installed" / "Update conflicts with package"

This always means the APK being tapped is not the same app that is installed:

- **GitHub Actions artifact (`app-debug.zip`)** — CI has no project keystore, so
  `assembleDebug` uses Android's *default debug key*. Its signature differs from
  the installed app (`cf9d3353…`), so Android refuses the update. Never install
  the CI zip; use the locally signed `JupiterConnect.apk` instead.
- **APK opened from inside the ZIP preview** — extract it to a real file first,
  then tap the `.apk` from the Files app.
- **Uninstall/reinstall to get around either of the above** — that wipes local
  ride history. Rides are now restored from `jc_rides` on the next signed-in
  sync; fuel fills and service records already come from Meridian's cloud.

To verify a file before installing, run
`apksigner verify --print-certs <apk>`; signer SHA-256 must start with
`cf9d3353fab3bf2f7316a1b7bed39e6d2a75b6d6e5d4b8a6a06d9818808240a4`.
