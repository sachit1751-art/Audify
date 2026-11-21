param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet("home_first_scroll", "home_scroll", "player_open_close", "lyrics_scroll", "playlist", "search", "settings_walk", "artist_album")]
    [string]$Scenario,
    [string]$Serial,
    [string]$PackageName = "com.music.audify",
    [string]$OutputDirectory = "$env:USERPROFILE\Downloads\Audify-Perfetto"
)

$ErrorActionPreference = "Stop"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$config = Join-Path $PSScriptRoot "trace.cfg"
if (-not (Test-Path -LiteralPath $adb)) { throw "ADB not found at $adb" }

$devices = @(& $adb devices | Select-String "`tdevice$" | ForEach-Object { ($_ -split "\s+")[0] })
if ($Serial) {
    if ($Serial -notin $devices) { throw "Device $Serial is not connected and authorised." }
} elseif ($devices.Count -eq 1) {
    $Serial = $devices[0]
} else {
    throw "Expected exactly one authorised device; found $($devices.Count). Pass -Serial explicitly."
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$name = "audify-$Scenario-$stamp"
$remoteConfig = "/data/local/tmp/audify-perf.cfg"
$remoteTrace = "/data/misc/perfetto-traces/$name.pftrace"
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$packageInfo = & $adb -s $Serial shell dumpsys package $PackageName 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) { throw "Could not inspect package $PackageName. Is it installed?" }
if ($packageInfo -notmatch "profileableByShell=true") {
    throw "$PackageName is not profileable by shell. Install the prodRelease APK built with src/prodRelease/AndroidManifest.xml."
}

& $adb -s $Serial push $config $remoteConfig | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Could not push Perfetto config." }
& $adb -s $Serial shell am force-stop $PackageName
& $adb -s $Serial shell monkey -p $PackageName 1 | Out-Null
Start-Sleep -Seconds 3

$start = & $adb -s $Serial shell perfetto --txt -c $remoteConfig -o $remoteTrace --background 2>&1
$pidLine = ($start | Out-String).Trim()
if ($pidLine -notmatch "(\d+)") { throw "Perfetto did not return a background PID: $pidLine" }
$perfettoPid = $Matches[1]
Write-Host "Recording $Scenario on $Serial ($PackageName)."
Write-Host "Follow the matching scenario in tools/perf/scenarios.md. Press Enter to stop early; the trace also has a 180-second cap."
[void](Read-Host)

& $adb -s $Serial shell kill -TERM $perfettoPid
Start-Sleep -Seconds 3
$destination = Join-Path $OutputDirectory "$name.pftrace"
& $adb -s $Serial pull $remoteTrace $destination
if ($LASTEXITCODE -ne 0) { throw "Trace pull failed; check device storage and Perfetto status." }
Write-Host "Trace saved: $destination"
