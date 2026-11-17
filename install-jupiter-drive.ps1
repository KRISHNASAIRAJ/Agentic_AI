<#
.SYNOPSIS
    Install Jupiter Drive over ADB, side by side with Jupiter Connect.

.DESCRIPTION
    Jupiter Drive uses applicationId "com.jupiterdrive", so it installs next to
    the existing "com.jupiterconnect" app without replacing it. ADB installs
    are trusted by Android, so Google Play Protect stays ON and no blocking
    dialog appears.

    Enable USB debugging first:
      Settings > About phone > tap Build number 7 times
      Settings > System > Developer options > USB debugging > ON
    Then connect by USB and accept "Allow USB debugging".
#>
[CmdletBinding()]
param([switch]$Launch)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apk)) {
    throw "APK not found: $apk. Build it first with .\gradlew.bat :app:assembleDebug"
}

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    $candidate = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $candidate) { $adb = $candidate }
}
if (-not $adb) { throw "adb not found." }

& $adb devices
Write-Host "Installing Jupiter Drive (com.jupiterdrive) ..."
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw "adb install failed with code $LASTEXITCODE" }
Write-Host "Installed. Play Protect remains enabled."
if ($Launch) {
    & $adb shell monkey -p com.jupiterdrive -c android.intent.category.LAUNCHER 1 | Out-Null
}
