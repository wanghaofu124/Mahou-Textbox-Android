$ErrorActionPreference = "Stop"
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "C:\Users\Administrator\AppData\Local\Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$pkg = "com.mahou.textbox"
$apk = Join-Path $PSScriptRoot "mahou-textbox-0.4.0-release.apk"

if (!(Test-Path $apk)) { throw "Release APK not found: $apk" }

$serial = $env:ANDROID_SERIAL
if (!$serial) {
    $device = (& $adb devices) | Select-String -Pattern "^(\S+)\s+device$" | Select-Object -First 1
    if (!$device) { throw "No online Android emulator/device found. Set ANDROID_SERIAL if needed." }
    $serial = $device.Matches[0].Groups[1].Value
}

function Invoke-Adb([string[]]$AdbArgs) {
    $output = & $adb -s $serial @AdbArgs 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($AdbArgs -join ' ') -> $output" }
    return $output
}

$previousPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& $adb -s $serial uninstall $pkg 2>&1 | Out-Null
$ErrorActionPreference = $previousPreference
Invoke-Adb @("install", $apk) | Out-Null
Invoke-Adb @("logcat", "-c") | Out-Null
Invoke-Adb @(
    "shell", "am", "start", "-n", "$pkg/.MainActivity",
    "--es", "mahou_test_action", "storage_png_probe"
) | Out-Null
Start-Sleep -Seconds 4

$logs = Invoke-Adb @("logcat", "-d", "-s", "MahouTest:I") | Out-String
$packageDump = Invoke-Adb @("shell", "dumpsys", "package", $pkg) | Out-String
$noTestLogs = $logs -notmatch "storage_png_path=|storage_png_probe_failed"
$notDebuggable = $packageDump -notmatch "\bDEBUGGABLE\b"
$versionOk = $packageDump -match "versionName=0\.4\.0" -and $packageDump -match "versionCode=23"
$noInternet = $packageDump -notmatch "android\.permission\.INTERNET"

Write-Host "Release version 0.4.0 (23): $versionOk"
Write-Host "Release is not debuggable: $notDebuggable"
Write-Host "Release ignores test action: $noTestLogs"
Write-Host "Release has no INTERNET permission: $noInternet"
if (!$versionOk -or !$notDebuggable -or !$noTestLogs -or !$noInternet) { exit 1 }
