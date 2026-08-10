# QQ export size + related fixes — emulator tests
$ErrorActionPreference = "Stop"
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
function Resolve-AndroidSerial {
    if ($env:ANDROID_SERIAL) { return $env:ANDROID_SERIAL }
    $lines = & $adb devices
    $device = $lines | Select-String -Pattern "^(\S+)\s+device$" | Select-Object -First 1
    if (!$device) { throw "No online Android emulator/device found. Set ANDROID_SERIAL if needed." }
    return $device.Matches[0].Groups[1].Value
}
$serial = Resolve-AndroidSerial
$pkg = "com.mahou.textbox"
$results = @()

function Invoke-Adb([string[]]$AdbArgs) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $out = & $adb -s $serial @AdbArgs 2>&1 | ForEach-Object { "$_" }
        if ($LASTEXITCODE -ne 0) { throw "adb failed: $($AdbArgs -join ' ') -> $out" }
        return $out
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Add-Result([string]$Name, [bool]$Pass, [string]$Detail) {
    $script:results += [pscustomobject]@{ Test = $Name; Pass = $Pass; Detail = $Detail }
    $mark = if ($Pass) { "PASS" } else { "FAIL" }
    Write-Host "[$mark] $Name — $Detail"
}

function Tap([int]$x, [int]$y) {
    Invoke-Adb @("shell", "input", "tap", "$x", "$y") | Out-Null
    Start-Sleep -Milliseconds 450
}

function KeyBack() {
    Invoke-Adb @("shell", "input", "keyevent", "4") | Out-Null
    Start-Sleep -Milliseconds 600
}

function Clear-Logcat() {
    Invoke-Adb @("logcat", "-c") | Out-Null
}

function Get-LastShareSize() {
    $lines = Invoke-Adb @("logcat", "-d", "-s", "MahouShare:I", "MahouTest:I") | Out-String
    if ($lines -match "share png size:\s*(\d+)x(\d+)") {
        return @{ Width = [int]$Matches[1]; Height = [int]$Matches[2] }
    }
    return $null
}

function Scroll-ToControls() {
    Invoke-Adb @("shell", "input", "swipe", "540", "2000", "540", "700", "400") | Out-Null
    Start-Sleep -Milliseconds 700
}

function Tap-QqSizeNext() {
    foreach ($y in @(1050, 980, 920, 1100, 1180)) {
        Tap 1000 $y
    }
}

function Get-PngSize([string]$path) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    if ($bytes.Length -lt 24) { return $null }
    $w = ($bytes[16] -shl 24) -bor ($bytes[17] -shl 16) -bor ($bytes[18] -shl 8) -bor $bytes[19]
    $h = ($bytes[20] -shl 24) -bor ($bytes[21] -shl 16) -bor ($bytes[22] -shl 8) -bor $bytes[23]
    return @{ Width = $w; Height = $h }
}


function Get-OverlayFrame {
    $dump = Invoke-Adb @("shell", "dumpsys", "window", "windows") | Out-String
    $lines = $dump -split "`n"
    $frames = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "com\.mahou\.textbox" -and $lines[$i] -notmatch "mCurrentFocus") {
            for ($j = $i; $j -lt [Math]::Min($i + 8, $lines.Count); $j++) {
                if ($lines[$j] -match "Frame=\{(\d+),(\d+)\s+(\d+),(\d+)\}") {
                    $frames += [pscustomobject]@{
                        Left = [int]$Matches[1]; Top = [int]$Matches[2]
                        Right = [int]$Matches[3]; Bottom = [int]$Matches[4]
                        Width = [int]$Matches[3] - [int]$Matches[1]
                        Height = [int]$Matches[4] - [int]$Matches[2]
                    }
                }
            }
        }
    }
    return $frames
}

function Trigger-A11y([string]$action, [int]$x) {
    Tap $x 1
}

function Start-TestAction([string]$action, [int]$qqSteps = 0) {
    $args = @("shell", "am", "start", "-n", "$pkg/.MainActivity", "--activity-single-top", "--es", "mahou_test_action", $action)
    if ($qqSteps -gt 0) {
        $args += @("--ei", "mahou_test_qq_steps", "$qqSteps")
    }
    Invoke-Adb $args | Out-Null
}

function Clear-AppPrefs() {
    try {
        Invoke-Adb @("shell", "run-as", $pkg, "rm", "-f", "shared_prefs/mahou_popup_prefs.xml") | Out-Null
    } catch {
        Write-Host "Note: could not clear prefs via run-as"
    }
}

function Export-ViaQQ {
    Clear-Logcat
    Start-TestAction "qq"
    Start-Sleep -Seconds 6
    Invoke-Adb @("shell", "input", "keyevent", "4") | Out-Null
    Start-Sleep -Milliseconds 400
}

Write-Host "========== QQ / Bug fix feature tests ==========`n"

# 1) Version
$pkgDump = Invoke-Adb @("shell", "dumpsys", "package", $pkg) | Out-String
$verOk = $pkgDump -match "versionName=0\.4\.0" -and $pkgDump -match "versionCode=23"
Add-Result "APK version 0.4.0 (23)" $verOk "version correct=$verOk"

# 2) Static dimensions
$staticOk = $true
foreach ($row in @(@(720,235), @(1000,326), @(1280,417), @(1600,521))) {
    if ([math]::Round(834 * $row[0] / 2560) -ne $row[1]) { $staticOk = $false }
}
Add-Result "Export height formula" $staticOk "round(834*w/2560) for all presets"

Invoke-Adb @("shell", "appops", "set", $pkg, "SYSTEM_ALERT_WINDOW", "allow") | Out-Null
Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null

# 3) QQ export default normal (1000x326)
Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null
Invoke-Adb @("shell", "am", "start", "-n", "$pkg/.MainActivity") | Out-Null
Start-Sleep -Seconds 5
Clear-Logcat
Start-TestAction "qq_export_normal"
Start-Sleep -Seconds 2
Export-ViaQQ
$szNorm = Get-LastShareSize
$normDetail = if ($szNorm) { "logcat $($szNorm.Width)x$($szNorm.Height)" } else { "logcat missing" }
Add-Result "QQ export default (normal)" ($szNorm -and $szNorm.Width -eq 1000 -and $szNorm.Height -eq 326) $normDetail

# 4) Step to xlarge (3x next from normal) then QQ share 1600x521
Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null
Invoke-Adb @("shell", "am", "start", "-n", "$pkg/.MainActivity") | Out-Null
Start-Sleep -Seconds 5
Clear-Logcat
Start-TestAction "qq_export_xlarge_and_share"
Start-Sleep -Seconds 8
$szXl = Get-LastShareSize
$xlLog = Invoke-Adb @("logcat", "-d", "-s", "MahouShare:I", "MahouTest:I") | Out-String
$xlDetail = if ($szXl) { "logcat $($szXl.Width)x$($szXl.Height)" } else { "logcat missing; testlog=$xlLog" }
$xlPresetOk = $xlLog -match "qq_export_size=xlarge"
Add-Result "QQ export xlarge preset" ($szXl -and $szXl.Width -eq 1600 -and $szXl.Height -eq 521 -and $xlPresetOk) $xlDetail

# 5) General share stays 1000 even after xlarge UI setting
Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null
Clear-Logcat
Start-TestAction "share_fixed_test"
Start-Sleep -Seconds 12
$shareLog = Invoke-Adb @("logcat", "-d", "-s", "MahouShare:I", "MahouTest:I") | Out-String
$shareTriggered = $shareLog -match "bridge_share_image"
Invoke-Adb @("shell", "input", "keyevent", "4") | Out-Null
$szGen = Get-LastShareSize
$genDetail = if ($szGen) { "logcat $($szGen.Width)x$($szGen.Height)" } else { "logcat missing; bridge=$shareTriggered" }
Add-Result "General share fixed 1000px" ($shareTriggered -and $szGen -and $szGen.Width -eq 1000 -and $szGen.Height -eq 326) $genDetail

# 6) Back closes asset overlay
Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null
Clear-Logcat
Start-TestAction "assetManager"
Start-Sleep -Seconds 5
$openLog = Invoke-Adb @("logcat", "-d", "-s", "MahouTest:I") | Out-String
$opened = $openLog -match "asset_overlay_open=true"
Start-TestAction "android_back"
Start-Sleep -Seconds 3
$backLog = Invoke-Adb @("logcat", "-d", "-s", "MahouTest:I") | Out-String
$handled = $backLog -match "android_back_handled=true"
Add-Result "Asset overlay opens (test action)" $opened "asset_overlay_open in log=$opened"
Add-Result "Back closes asset overlay" $handled "android_back_handled in log=$handled"

# 7) Popup mini QQ keeps mini width (~170dp) and exports QQ size
Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null
Invoke-Adb @("shell", "am", "start", "-n", "$pkg/.MainActivity") | Out-Null
Start-Sleep -Seconds 5
Start-TestAction "qq_export_normal"
Start-Sleep -Seconds 2
Start-TestAction "popup"
Start-Sleep -Seconds 6
$framesBefore = Get-OverlayFrame
$miniW = ($framesBefore | Sort-Object Width | Select-Object -First 1).Width
Invoke-Adb @("shell", "uiautomator", "dump", "/sdcard/uidump.xml") | Out-Null
$popupUi = Invoke-Adb @("shell", "cat", "/sdcard/uidump.xml") | Out-String
$minNode = [regex]::Match($popupUi, 'content-desc="[^"]*最小化[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
if (-not $minNode.Success) {
    $minNode = [regex]::Match($popupUi, 'text="−"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
}
if ($minNode.Success) {
    $mx = [int](([int]$minNode.Groups[1].Value + [int]$minNode.Groups[3].Value) / 2)
    $my = [int](([int]$minNode.Groups[2].Value + [int]$minNode.Groups[4].Value) / 2)
    Tap $mx $my
    Start-Sleep -Seconds 2
    Invoke-Adb @("shell", "uiautomator", "dump", "/sdcard/uidump.xml") | Out-Null
    $popupUi = Invoke-Adb @("shell", "cat", "/sdcard/uidump.xml") | Out-String
}
$qqNode = [regex]::Match($popupUi, 'text="QQ"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
if ($qqNode.Success) {
    $qx = [int](([int]$qqNode.Groups[1].Value + [int]$qqNode.Groups[3].Value) / 2)
    $qy = [int](([int]$qqNode.Groups[2].Value + [int]$qqNode.Groups[4].Value) / 2)
    Clear-Logcat
    Tap $qx $qy
    Start-Sleep -Seconds 18
    $framesDuring = Get-OverlayFrame
    $maxW = ($framesDuring | Measure-Object -Property Width -Maximum).Maximum
    $miniOk = $maxW -lt 800
    Add-Result "Mini popup stays narrow on QQ tap" $miniOk "miniW~$miniW maxW=$maxW (expect <800)"
    $popLog = Invoke-Adb @("logcat", "-d", "-s", "MahouShare:I", "MahouTest:I") | Out-String
    $exportStarted = $popLog -match "popup_mini_export_start qq=true"
    $szPopup = Get-LastShareSize
    $popDetail = if ($szPopup) { "logcat $($szPopup.Width)x$($szPopup.Height)" } elseif ($exportStarted) { "export started, share log missing" } else { "logcat missing" }
    $exportDone = $popLog -match "popup_export_done qq=true"
    Add-Result "Popup mini QQ uses QQ export size" ($exportStarted -and $exportDone -and $szPopup -and $szPopup.Width -ge 720) $popDetail
} else {
    Add-Result "Mini popup stays narrow on QQ tap" $false "QQ button not found in overlay UI dump"
    Add-Result "Popup mini QQ uses QQ export size" $false "skipped"
}
Invoke-Adb @("shell", "input", "keyevent", "4") | Out-Null

# 8) Transparent PNG storage probe
Clear-Logcat
Start-TestAction "storage_png_probe"
Start-Sleep -Seconds 3
$storageLog = Invoke-Adb @("logcat", "-d", "-s", "MahouTest:I") | Out-String
$pngStored = $storageLog -match "storage_png_path=.*\.png" -and $storageLog -match "storage_png_mime=image/png"
Add-Result "Transparent PNG keeps PNG format" $pngStored "path and MIME log present=$pngStored"

# 9) Monkey
Clear-Logcat
Invoke-Adb @("shell", "monkey", "-p", $pkg, "-v", "--throttle", "120", "300") | Out-Null
$stabilityLog = Invoke-Adb @("logcat", "-d", "-t", "2000") | Out-String
$crash = $stabilityLog -match "FATAL EXCEPTION|ANR in com\.mahou\.textbox|SecurityException|FileUriExposedException|OutOfMemoryError"
Add-Result "Monkey 300 no fatal/ANR/image URI error" (-not $crash) "failure signature in logcat=$crash"

Write-Host "`n========== Summary =========="
$pass = ($results | Where-Object { $_.Pass }).Count
$fail = ($results | Where-Object { -not $_.Pass }).Count
$results | Format-Table -AutoSize
Write-Host "Total: $pass passed, $fail failed"
if ($fail -gt 0) { exit 1 }
