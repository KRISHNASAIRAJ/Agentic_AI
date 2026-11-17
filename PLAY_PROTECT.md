# Why Google Play Protect blocks Jupiter Drive

If you tapped `app-debug.apk` in a file manager and saw
*"App blocked to protect your device"*, nothing is wrong with the app. Play
Protect makes a reputation decision about every newly sideloaded APK. Three
things made this one a clear target.

## 1. It was signed with the shared Android Debug key

The APK built before this note was signed with:

```
CN=Android Debug, O=Android
SHA-1: 95025f8ec98a26510ab1f0a2752262dc2f796685
```

That certificate is the same on **every debug build in the world** — it ships
inside the Android SDK. Play Protect treats it as low-reputation by design.

`app/build.gradle.kts` already knows how to sign with a real key, but the key
is only materialised from GitHub Actions secrets (`TVS_KEYSTORE_B64`, etc.).
Our fork has no such secrets, so local builds silently fell back to the shared
debug key.

**Fix applied locally:** `key.properties` + `signing/jupiter.jks` (both
gitignored, never committed). The APK is now signed with our own key:

```
CN=Jupiter Connect, OU=Personal, O=Jupiter Connect, C=IN
SHA-256: cf9d3353fab3bf2f7316a1b7bed39e6d2a75b6d6e5d4b8a6a06d9818808240a4
```

Rebuild with `.\gradlew.bat :app:assembleDebug` to get the uniquely-signed APK.

## 2. It requests a lot of sensitive permissions

Play Protect weighs the permission set. The original build asked for Bluetooth,
fine **and background** location, notification-listener access, **contacts, call
log and phone state**, `QUERY_ALL_PACKAGES`, battery-optimisation exemption,
boot receiver and `INTERNET`. Several of those are not actually needed:

- `QUERY_ALL_PACKAGES` — no code path enumerates installed apps. Removed.
- `READ_CALL_LOG` / `READ_CONTACTS` / `READ_PHONE_STATE` — caller ID is read
  from *notification content*, not from those stores. No code reads them.
  Removed; only cosmetic contact-name resolution is lost.
- `ACCESS_BACKGROUND_LOCATION` / `FOREGROUND_SERVICE_LOCATION` — "Last Parked"
  uses the last known fix from `LocationManager`, which needs only fine/coarse
  location. Removed.

The **lean build** keeps only what the features genuinely use: Bluetooth,
fine/coarse location, notification listener, foreground service, boot receiver,
battery exemption and `INTERNET` (map tiles). This removes the "stalkerware
profile" signal that made the automated scanner suspicious.

Note: the upstream CI workflow still asserts the old caller-ID permissions are
present. That assertion is unchanged at your request, so it will fail on this
lean build — the local APK itself builds and tests cleanly.

## 3. Zero reputation

A brand-new APK hash has no prior scans and no developer reputation. TVSRide's
unmodified APK has been downloaded and scanned many times, so Google already
has a "safe" verdict for its exact hash. Rebuilding from source produces a new
hash and starts from zero — cloning their repo does not inherit their
reputation.

## Why TVSRide installed without the prompt

Their README and release notes both say:

> Install with `adb install -r app-debug.apk`

They never ask users to tap the APK. ADB installs are trusted by Android and
do **not** show the blocking dialog. There is no manifest or permission trick.

## Install without disabling Play Protect

Use the supplied installer — it uses ADB, so Play Protect stays ON:

```powershell
cd <this folder>
.\install-jupiter-drive.ps1
```

Enable USB debugging first (Settings → About phone → Build number ×7 →
Developer options → USB debugging), connect by USB, accept the prompt.

Or manually:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Jupiter Drive uses `applicationId = com.jupiterdrive`, so it installs beside
the `com.jupiterconnect` app from `main` without replacing it.
