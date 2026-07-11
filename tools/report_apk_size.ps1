param(
    [Parameter(Mandatory = $true)]
    [string]$Apk,
    [string]$Output = ""
)

$ErrorActionPreference = "Stop"
$resolvedApk = (Resolve-Path -LiteralPath $Apk).Path
if (!$Output) {
    $Output = Join-Path (Split-Path $resolvedApk) "apk-size-report.json"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($resolvedApk)
try {
    $groups = @{}
    foreach ($entry in $zip.Entries) {
        $name = $entry.FullName
        if ($name -like "assets/background/*") {
            $group = "builtInBackgrounds"
        } elseif ($name -like "assets/*.ttf") {
            $group = "fonts"
        } elseif ($name -match '^assets/[^/]+/.+\.(png|jpg|jpeg|webp)$') {
            $group = "builtInCharacters"
        } elseif ($name -like "assets/*") {
            $group = "webRuntime"
        } elseif ($name -like "classes*.dex") {
            $group = "dex"
        } elseif ($name -like "res/*" -or $name -eq "resources.arsc") {
            $group = "androidResources"
        } elseif ($name -like "META-INF/*") {
            $group = "signingMetadata"
        } else {
            $group = "other"
        }
        if (!$groups.ContainsKey($group)) {
            $groups[$group] = [ordered]@{ files = 0; uncompressedBytes = 0L; compressedBytes = 0L }
        }
        $groups[$group].files++
        $groups[$group].uncompressedBytes += $entry.Length
        $groups[$group].compressedBytes += $entry.CompressedLength
    }

    $orderedGroups = [ordered]@{}
    foreach ($key in ($groups.Keys | Sort-Object)) {
        $orderedGroups[$key] = $groups[$key]
    }
    $report = [ordered]@{
        apk = [IO.Path]::GetFileName($resolvedApk)
        apkBytes = (Get-Item -LiteralPath $resolvedApk).Length
        generatedAt = (Get-Date).ToUniversalTime().ToString("o")
        groups = $orderedGroups
    }
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $Output -Encoding UTF8
    Write-Host "APK size report written to: $Output"
} finally {
    $zip.Dispose()
}
