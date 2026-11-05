param(
    [string]$Version = "1.9.0",
    [string]$Executable = "target/release/audify.exe",
    [string]$Output = "dist",
    [ValidateSet("x64compatible", "arm64")]
    [string]$Architecture = "x64compatible",
    [string]$SetupName = "Audify-Setup"
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "../..")
$source = Resolve-Path (Join-Path $root $Executable)
$outputPath = Join-Path $root $Output
New-Item -ItemType Directory -Force $outputPath | Out-Null

$iscc = @(
    (Join-Path ${env:ProgramFiles(x86)} "Inno Setup 6/ISCC.exe")
    (Join-Path $env:LOCALAPPDATA "Programs/Inno Setup 6/ISCC.exe")
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $iscc) {
    throw "Inno Setup 6 is not installed"
}

$env:AUDIFY_VERSION = $Version.TrimStart("v")
$env:AUDIFY_EXE = $source.Path
$env:AUDIFY_DIST = (Resolve-Path $outputPath).Path
$env:AUDIFY_ARCH = $Architecture
$env:AUDIFY_SETUP = $SetupName

& $iscc (Join-Path $PSScriptRoot "audify.iss")
if ($LASTEXITCODE -ne 0) {
    throw "Inno Setup failed with exit code $LASTEXITCODE"
}
