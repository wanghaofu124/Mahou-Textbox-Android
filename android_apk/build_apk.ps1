param(
    [ValidateSet("Debug", "Release", "All")]
    [string]$Configuration = "All",
    [switch]$Quick
)

$ErrorActionPreference = "Stop"
$AndroidRoot = $PSScriptRoot
$Sdk = if ($env:ANDROID_HOME) {
    $env:ANDROID_HOME
} elseif ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} else {
    "$env:LOCALAPPDATA\Android\Sdk"
}
$Gradle = Join-Path $AndroidRoot "gradlew.bat"
$VersionName = "0.7.25"
$VersionCode = 56
$VersionFile = "mahou-textbox-" + ($VersionName -replace '\.','')
$ExpectedCertificate = (Get-Content -LiteralPath (Join-Path $AndroidRoot "release-cert.sha256") -Raw).Trim().ToLowerInvariant()

function Invoke-Checked {
    param([string]$FilePath, [string[]]$Arguments)
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $FilePath $($Arguments -join ' ')"
    }
}

function Find-BuildTool([string]$Name) {
    $tool = Get-ChildItem (Join-Path $Sdk "build-tools") -Directory |
        Sort-Object { [version]$_.Name } -Descending |
        ForEach-Object { Join-Path $_.FullName $Name } |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1
    if (!$tool) { throw "Android build tool not found: $Name" }
    return $tool
}

function Enable-SystemProxyForGradle {
    if ($env:GRADLE_OPTS -match "proxyHost") { return }
    try {
        $target = [uri]"https://dl.google.com"
        $proxy = [System.Net.WebRequest]::DefaultWebProxy.GetProxy($target)
        if ($proxy -and $proxy.AbsoluteUri -ne $target.AbsoluteUri) {
            $parts = @(
                "-Dhttps.proxyHost=$($proxy.Host)",
                "-Dhttps.proxyPort=$($proxy.Port)",
                "-Dhttp.proxyHost=$($proxy.Host)",
                "-Dhttp.proxyPort=$($proxy.Port)"
            )
            $env:GRADLE_OPTS = (($env:GRADLE_OPTS, ($parts -join " ")) -join " ").Trim()
        }
    } catch {
        Write-Warning "Unable to detect the system proxy; Gradle will use its default network settings."
    }
}

if (!(Test-Path $Gradle)) { throw "Gradle wrapper not found: $Gradle" }
if ($Configuration -in @("Release", "All")) {
    foreach ($file in @("release.keystore", "release-signing.properties")) {
        if (!(Test-Path (Join-Path $AndroidRoot $file))) {
            throw "Missing $file. Release keys are never generated automatically."
        }
    }
}

Enable-SystemProxyForGradle
$tasks = switch ($Configuration) {
    "Debug" { @("clean", ":app:assembleDebug", ":app:lintDebug", ":app:testDebugUnitTest") }
    "Release" { @("clean", ":app:assembleRelease", ":app:lintRelease", ":app:testReleaseUnitTest") }
    default { @("clean", ":app:assembleDebug", ":app:assembleRelease", ":app:lintDebug", ":app:lintRelease", ":app:testDebugUnitTest", ":app:testReleaseUnitTest") }
}
if ($Quick) {
    $tasks = switch ($Configuration) {
        "Debug" { @(":app:assembleDebug") }
        "Release" { @(":app:assembleRelease") }
        default { @(":app:assembleDebug", ":app:assembleRelease") }
    }
}
Invoke-Checked $Gradle (@("--no-daemon", "-p", $AndroidRoot) + $tasks)

$ApkSigner = Find-BuildTool "apksigner.bat"
$Aapt = Find-BuildTool "aapt.exe"
$artifacts = @()
if ($Configuration -in @("Debug", "All")) {
    $artifacts += @{
        Type = "Debug"
        Source = Join-Path $AndroidRoot "app\build\outputs\apk\debug\app-debug.apk"
        Target = Join-Path $AndroidRoot "$VersionFile-debug.apk"
    }
}
if ($Configuration -in @("Release", "All")) {
    $artifacts += @{
        Type = "Release"
        Source = Join-Path $AndroidRoot "app\build\outputs\apk\release\app-release.apk"
        Target = Join-Path $AndroidRoot "$VersionFile.apk"
    }
}

$checksumLines = @()
foreach ($artifact in $artifacts) {
    Copy-Item -LiteralPath $artifact.Source -Destination $artifact.Target -Force
    $verify = & $ApkSigner verify --verbose --print-certs $artifact.Target 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "$($artifact.Type) signature verification failed: $verify" }
    $badging = & $Aapt dump badging $artifact.Target | Select-Object -First 1
    if ($badging -notmatch "versionCode='$VersionCode'" -or $badging -notmatch "versionName='$([regex]::Escape($VersionName))'") {
        throw "$($artifact.Type) APK has the wrong version: $badging"
    }
    if ($artifact.Type -eq "Release") {
        $certificateLine = $verify | Where-Object { $_ -match "certificate SHA-256 digest:" } | Select-Object -First 1
        if (!$certificateLine -or $certificateLine -notmatch "digest:\s*([0-9a-fA-F]+)") {
            throw "Unable to read the Release certificate fingerprint."
        }
        if ($Matches[1].ToLowerInvariant() -ne $ExpectedCertificate) {
            throw "Release certificate mismatch. Expected $ExpectedCertificate"
        }
        if ((Get-Item $artifact.Target).Length -gt 97000000) {
            throw "Release APK exceeds the 97 MB public release limit."
        }
    }
    $hash = (Get-FileHash -LiteralPath $artifact.Target -Algorithm SHA256).Hash.ToLowerInvariant()
    $checksumLines += "$hash  $([IO.Path]::GetFileName($artifact.Target))"
    "$hash  $([IO.Path]::GetFileName($artifact.Target))" | Set-Content -LiteralPath "$($artifact.Target).sha256" -Encoding ASCII
    Write-Host "$($artifact.Type) APK verified: $($artifact.Target)"
}

$checksumPath = Join-Path $AndroidRoot "release-artifacts.sha256"
$checksumLines | Set-Content -LiteralPath $checksumPath -Encoding ASCII
Write-Host "Checksums written to: $checksumPath"

$releaseArtifact = $artifacts | Where-Object { $_.Type -eq "Release" } | Select-Object -First 1
if ($releaseArtifact) {
    $sizeReporter = Join-Path (Split-Path $AndroidRoot) "tools\report_apk_size.ps1"
    & $sizeReporter -Apk $releaseArtifact.Target -Output (Join-Path $AndroidRoot "apk-size-report.json")
}
