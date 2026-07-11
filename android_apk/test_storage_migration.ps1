$ErrorActionPreference = "Stop"
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "C:\Users\Administrator\AppData\Local\Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$pkg = "com.mahou.textbox"
$apk = Join-Path $PSScriptRoot "mahou-textbox-0.4.0-debug.apk"

if (!(Test-Path $apk)) { throw "Debug APK not found: $apk" }

$serial = $env:ANDROID_SERIAL
if (!$serial) {
    $device = (& $adb devices) | Select-String -Pattern "^(\S+)\s+device$" | Select-Object -First 1
    if (!$device) { throw "No online Android emulator/device found. Set ANDROID_SERIAL if needed." }
    $serial = $device.Matches[0].Groups[1].Value
}

function Invoke-Adb([string[]]$AdbArgs) {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $adb -s $serial @AdbArgs 2>&1 | ForEach-Object { "$_" }
        if ($LASTEXITCODE -ne 0) { throw "adb failed: $($AdbArgs -join ' ') -> $output" }
        return $output
    } finally {
        $ErrorActionPreference = $previousPreference
    }
}

Invoke-Adb @("install", "-r", $apk) | Out-Null
Invoke-Adb @("shell", "pm", "clear", $pkg) | Out-Null
$dataDir = (Invoke-Adb @("shell", "run-as", $pkg, "pwd") | Select-Object -First 1).Trim()
if (!$dataDir) { throw "Unable to resolve app data directory." }

$tempDir = Join-Path $env:TEMP "mahou-storage-test"
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
$pngPath = Join-Path $tempDir "legacy.jpg"
$manifestPath = Join-Path $tempDir "manifest.json"
$pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+XwU2WQAAAABJRU5ErkJggg=="
[System.IO.File]::WriteAllBytes($pngPath, [Convert]::FromBase64String($pngBase64))
$legacyPath = "$dataDir/files/assets/backgrounds/legacy.jpg"
$manifest = @{
    version = 1
    backgrounds = @(@{
        id = "legacy"
        name = "Legacy PNG"
        custom = $true
        path = $legacyPath
        src = "file://$legacyPath"
    })
    characters = @()
    emotions = @()
} | ConvertTo-Json -Depth 5 -Compress
[System.IO.File]::WriteAllText($manifestPath, $manifest, [System.Text.UTF8Encoding]::new($false))

Invoke-Adb @("push", $pngPath, "/data/local/tmp/mahou-legacy.jpg") | Out-Null
Invoke-Adb @("push", $manifestPath, "/data/local/tmp/mahou-manifest.json") | Out-Null
Invoke-Adb @("shell", "run-as", $pkg, "mkdir", "-p", "files/assets/backgrounds") | Out-Null
Invoke-Adb @("shell", "run-as", $pkg, "cp", "/data/local/tmp/mahou-legacy.jpg", "files/assets/backgrounds/legacy.jpg") | Out-Null
Invoke-Adb @("shell", "run-as", $pkg, "cp", "/data/local/tmp/mahou-manifest.json", "files/assets/manifest.json") | Out-Null

Invoke-Adb @("logcat", "-c") | Out-Null
Invoke-Adb @(
    "shell", "am", "start", "-n", "$pkg/.MainActivity",
    "--es", "mahou_test_action", "storage_png_probe"
) | Out-Null
$probeReady = $false
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    Start-Sleep -Seconds 1
    $probeState = Invoke-Adb @("logcat", "-d", "-s", "MahouTest:I") | Out-String
    if ($probeState -match "storage_png_path=|storage_png_probe_failed") {
        $probeReady = $true
        break
    }
}
if (!$probeReady) { Write-Warning "Storage probe did not finish before the migration check." }

$files = Invoke-Adb @("shell", "run-as", $pkg, "ls", "files/assets/backgrounds") | Out-String
$storedManifest = Invoke-Adb @("shell", "run-as", $pkg, "cat", "files/assets/manifest.json") | Out-String
$parsedManifest = $storedManifest | ConvertFrom-Json
$legacyItem = $parsedManifest.backgrounds | Where-Object { $_.id -eq "legacy" } | Select-Object -First 1
$migrated = $files -match "legacy\.png" `
    -and $files -notmatch "legacy\.jpg" `
    -and $parsedManifest.version -eq 3 `
    -and $legacyItem.path -match "legacy\.png$" `
    -and $legacyItem.mime -eq "image/png"

Write-Host "Legacy PNG-in-JPG migration: $migrated"
if (!$migrated) {
    Write-Host "Stored files: $files"
    Write-Host "Stored manifest: $storedManifest"
    $probeLog = Invoke-Adb @("logcat", "-d", "-s", "MahouTest:I", "MahouAssets:E") | Out-String
    Write-Host "Probe log: $probeLog"
}
Invoke-Adb @("shell", "pm", "clear", $pkg) | Out-Null
Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue
if (!$migrated) { exit 1 }
