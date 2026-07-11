# Theme prefs + chrome smoke tests (debug intents + MahouTest logcat)
$ErrorActionPreference = "Stop"
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "C:\Users\Administrator\AppData\Local\Android\Sdk" }
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
$apk = Join-Path $PSScriptRoot "mahou-textbox-0.4.0-debug.apk"
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

function Clear-Logcat() {
    Invoke-Adb @("logcat", "-c") | Out-Null
}

function Get-TestLog() {
    return (Invoke-Adb @("logcat", "-d", "-s", "MahouTest:I") | Out-String)
}

function Start-Test([string]$action, [hashtable]$Extras = @{}) {
    $args = @("shell", "am", "start", "-n", "$pkg/.MainActivity", "--activity-single-top", "--es", "mahou_test_action", $action)
    foreach ($key in $Extras.Keys) {
        if ($key -like "*_steps") {
            $args += @("--ei", $key, "$($Extras[$key])")
        }
    }
    Invoke-Adb $args | Out-Null
}

Write-Host "========== Theme prefs tests ==========`n"

if (Test-Path $apk) {
    Invoke-Adb @("install", "-r", $apk) | Out-Null
}

$pkgDump = Invoke-Adb @("shell", "dumpsys", "package", $pkg) | Out-String
$verOk = $pkgDump -match "versionName=0\.4\.0" -and $pkgDump -match "versionCode=23"
Add-Result "APK version 0.4.0 (23)" $verOk "version correct=$verOk"

Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null
Invoke-Adb @("shell", "pm", "clear", $pkg) | Out-Null
Invoke-Adb @("shell", "appops", "set", $pkg, "SYSTEM_ALERT_WINDOW", "allow") | Out-Null
Clear-Logcat
Start-Test "theme_read"
Start-Sleep -Seconds 5
$bootLog = Get-TestLog
$darkOk = -not [string]::IsNullOrWhiteSpace($bootLog) -and (
    $bootLog -match "dom_theme=dark" -or $bootLog -match "pref_theme=dark"
)
Add-Result "Default theme dark" $darkOk "log=$bootLog"

Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null
Clear-Logcat
Start-Test "theme_step" @{ mahou_test_theme_steps = 2 }
Start-Sleep -Seconds 4
$stepLog = Get-TestLog
$lightDom = $stepLog -match "dom_theme=light"
$lightPref = $stepLog -match "pref_theme=light"
Add-Result "Theme stepper -> light (dom)" $lightDom "dom_theme in log=$lightDom"
Add-Result "Theme stepper -> light (prefs)" $lightPref "pref_theme in log=$lightPref"

$chromeLog = Get-TestLog
$lightBar = $chromeLog -match "status_bar_color=0xfff4f4f6" `
    -and $chromeLog -match "light_status_bar=true" `
    -and $chromeLog -match "light_navigation_bar=true"
Add-Result "Light theme system bars" $lightBar "status and navigation icon logs present=$lightBar"

Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null
Clear-Logcat
Start-Test "theme_read"
Start-Sleep -Seconds 5
$afterRestart = Get-TestLog
$persistOk = $afterRestart -match "pref_theme=light" -and $afterRestart -match "dom_theme=light"
Add-Result "Theme persists after restart" $persistOk "log=$afterRestart"

Write-Host "`n========== Summary =========="
$pass = ($results | Where-Object { $_.Pass }).Count
$fail = ($results | Where-Object { -not $_.Pass }).Count
$results | Format-Table -AutoSize
Write-Host "Total: $pass passed, $fail failed"
if ($fail -gt 0) { exit 1 }
