<#
.SYNOPSIS
    Prepare a correctly-signed Jupiter Connect APK for install on your phone.

.DESCRIPTION
    Builds the app, verifies it is signed with YOUR project key (CN=Jupiter
    Connect), and copies it to Downloads as JupiterConnect.apk — a fixed name
    you can copy to your phone and tap.

    WHY THIS EXISTS
    The app on your phone is signed with your unique project key. The
    "app-debug.apk" produced by GitHub Actions is signed with the shared
    Android Debug key instead, so tapping it shows "package conflicts" and
    forces an uninstall. ALWAYS use the JupiterConnect.apk this script places
    in Downloads — never the CI app-debug.apk.

    In-place updates (same signature) keep your permissions and the
    "Allow restricted settings" grant, so you never re-grant them again.

    You do NOT need developer options: just copy the APK to your phone and tap
    it in the file manager.
#>
[CmdletBinding()]
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:ANDROID_HOME = "C:\Users\krish\AppData\Local\Android\Sdk"

if (-not $SkipBuild) {
    Write-Host "Building Jupiter Connect..."
    Push-Location $root
    try {
        & .\gradlew.bat :app:assembleDebug --no-daemon
    } finally {
        Pop-Location
    }
    if ($LASTEXITCODE -ne 0) { throw "Build failed." }
}

$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { throw "APK not found: $apk" }

# Refuse to hand over a wrongly-signed APK — that is what causes the conflict.
$bt = Get-ChildItem "$env:ANDROID_HOME\build-tools" -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
$certs = & "$($bt.FullName)\apksigner.bat" verify --print-certs $apk 2>&1 | Out-String
if ($certs -notmatch 'CN=Jupiter Connect') {
    throw "APK is NOT signed with the project key — refusing. Check app/build.gradle.kts signingConfig."
}
Write-Host "Signature verified:" ($certs | Select-String 'DN:' | Select-Object -First 1).Line.Trim()

$target = "C:\Users\krish\Downloads\JupiterConnect.apk"
Copy-Item $apk $target -Force
Write-Host "Ready: $target"
Write-Host "Copy this file to your phone and tap it to update in place. Do NOT use the CI app-debug.apk."
