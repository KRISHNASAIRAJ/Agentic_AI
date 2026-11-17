<#
.SYNOPSIS
    Capture Jupiter Connect's BLE logs so we can see what the scan found.

.DESCRIPTION
    Connect the phone by USB (USB debugging on), run the app, tap Find, then
    run this script. It dumps only Jupiter Connect's log lines and the
    system Bluetooth lines. Send the output back for diagnosis.
#>
[CmdletBinding()]
param([int]$Seconds = 20)

$ErrorActionPreference = 'Stop'
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    $candidate = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $candidate) { $adb = $candidate }
}
if (-not $adb) { throw "adb not found." }

Write-Host "== connected devices =="
& $adb devices
Write-Host "== is app installed? =="
& $adb shell pm list packages com.jupiterconnect
Write-Host "== clearing old logcat =="
& $adb logcat -c
Write-Host "Now open the app and tap Find. Capturing for $Seconds seconds..."
$deadline = (Get-Date).AddSeconds($Seconds)
$lines = @()
while ((Get-Date) -lt $deadline) {
    $chunk = & $adb logcat -d -t 500 2>$null | Select-String -Pattern 'JupiterBle|BluetoothAdapter|BluetoothLeScanner|BtGatt|bta_'
    if ($chunk) { $lines += $chunk }
    Start-Sleep -Milliseconds 500
}
$lines | Select-Object -Last 250 | ForEach-Object { $_.Line } | Out-String -Width 400 |
    Set-Content -Path "jupiter-ble-log.txt" -Encoding UTF8
Write-Host "Saved jupiter-ble-log.txt ($($lines.Count) matching lines)."
Get-Content "jupiter-ble-log.txt" -Tail 120 | Out-String -Width 400
