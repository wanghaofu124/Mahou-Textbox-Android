# Install QQ on emulator and wait for user login
$ErrorActionPreference = "Stop"
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "C:\Users\Administrator\AppData\Local\Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
if ($env:ANDROID_SERIAL) {
    $serial = $env:ANDROID_SERIAL
} else {
    $device = (& $adb devices) | Select-String -Pattern "^(\S+)\s+device$" | Select-Object -First 1
    if (!$device) { throw "No online Android emulator/device found. Set ANDROID_SERIAL if needed." }
    $serial = $device.Matches[0].Groups[1].Value
}
$pkg = "com.tencent.mobileqq"
$localApk = "D:\qqbiaoqingbao\downloads\qq.apk"

function Invoke-Adb([string[]]$AdbArgs) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $out = & $adb -s $serial @AdbArgs 2>&1 | ForEach-Object { "$_" }
        if ($LASTEXITCODE -ne 0) { throw "adb failed: $($AdbArgs -join ' ') -> $out" }
        return $out
    } finally { $ErrorActionPreference = $prev }
}

Write-Host "=== QQ install helper (emulator $serial) ===`n"

if (Test-Path $localApk) {
    $len = (Get-Item $localApk).Length
    if ($len -gt 10MB) {
        Write-Host "Installing local APK ($([math]::Round($len/1MB,1)) MB)..."
        Invoke-Adb @("install", "-r", $localApk)
    } else {
        Write-Host "WARN: $localApk is too small ($len bytes) — replace with a real QQ APK."
    }
} else {
    Write-Host "No local APK at: $localApk"
    Write-Host "Opening QQ secure-download page in emulator browser..."
    try { Invoke-Adb @("shell", "appops", "set", "org.chromium.webview_shell", "REQUEST_INSTALL_PACKAGES", "allow") | Out-Null } catch { }
    Invoke-Adb @("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", "https://a.app.qq.com/o/simple.jsp?pkgname=com.tencent.mobileqq") | Out-Null
    Write-Host @"

请在模拟器窗口中操作：
  1. 点击蓝色「安全下载」安装 QQ（约 380MB，需等待下载完成）
  2. 若提示先安装「应用宝」，按提示安装后再装 QQ
  3. 打开 QQ 完成登录

也可将 QQ APK 放到下列路径后重新运行本脚本：
  $localApk

"@
}

$deadline = (Get-Date).AddMinutes(30)
while ((Get-Date) -lt $deadline) {
    $installed = Invoke-Adb @("shell", "pm", "path", $pkg) 2>$null
    if ($installed -match "package:") {
        Write-Host "`nQQ installed: $installed"
        Write-Host "Launching QQ for login..."
        Invoke-Adb @("shell", "monkey", "-p", $pkg, "-c", "android.intent.category.LAUNCHER", "1") | Out-Null
        Write-Host "Login in the emulator, then tell the assistant to run share tests."
        exit 0
    }
    Start-Sleep -Seconds 5
    Write-Host "." -NoNewline
}
Write-Host "`nTimeout: QQ not detected. Check emulator download/install."
exit 1
