<#
.SYNOPSIS
    Install Jupiter Connect over ADB — no need to disable Google Play Protect.

.DESCRIPTION
    ADB installs are trusted by Android and do not show the "App blocked to
    protect your device" dialog that appears when tapping a new APK in a file
    manager. Play Protect stays ON the whole time.

    Enable USB debugging on the phone first:
      Settings → About phone → tap "Build number" 7 times
      Settings → System → Developer options → USB debugging → ON
    Then connect the phone by USB and accept the "Allow USB debugging" prompt.
#>
[CmdletBinding()]
param(
    [string]$Apk = "app\build\outputs\apk\release\app-release.apk",
    [switch]$Launch
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$apkPath = if ([IO.Path]::IsPathRooted($Apk)) { $Apk } else { Join-Path $root $Apk }

if (-not (Test-Path $apkPath)) {
    throw "APK not found: $apkPath. Build it first with: .\\gradlew.bat :app:assembleRelease"
}

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    $candidate = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $candidate) { $adb = $candidate }
}
if (-not $adb) { throw "adb not found. Install Android platform-tools or add it to PATH." }

Write-Host "Using: $adb"
& $adb devices
Write-Host "Installing $apkPath ..."
& $adb install -r -d $apkPath

if ($LASTEXITCODE -ne 0) { throw "adb install failed with code $LASTEXITCODE" }

Write-Host "Installed. Play Protect remains enabled."
if ($Launch) {
    & $adb shell monkey -p com.jupiterconnect -c android.intent.category.LAUNCHER 1 | Out-Null
    Write-Host "Launched Jupiter Connect."
}
