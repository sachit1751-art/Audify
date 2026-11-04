[CmdletBinding()]
param(
    [string]$SdkPath,
    [switch]$AcceptLicenses
)
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $Root

function Fail([string]$Message) { Write-Error $Message; exit 1 }
function Find-SdkManager([string]$Sdk) {
    if (-not $Sdk) { return $null }
    $Candidates = @(
        (Join-Path $Sdk 'cmdline-tools\latest\bin\sdkmanager.bat'),
        (Join-Path $Sdk 'cmdline-tools\bin\sdkmanager.bat'),
        (Join-Path $Sdk 'tools\bin\sdkmanager.bat')
    )
    $Candidates += Get-ChildItem (Join-Path $Sdk 'cmdline-tools') -Filter sdkmanager.bat -File -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    return $Candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}

Write-Host '=== Audify development environment ===' -ForegroundColor Cyan
$Java = Get-Command java -ErrorAction SilentlyContinue
if (-not $Java) { Fail 'JDK 21 is missing. Install Eclipse Temurin 21 (https://adoptium.net/temurin/releases/?version=21), reopen PowerShell, and rerun this script.' }
$JavaVersion = (cmd /c "java -version 2>&1" | Out-String)
if ($JavaVersion -notmatch 'version "21\.') { Fail "Audify requires JDK 21. Detected: $($JavaVersion.Trim())" }
Write-Host 'JDK 21: OK' -ForegroundColor Green

if (-not $SdkPath) {
    $SdkPath = $env:ANDROID_HOME
    if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
    if (-not $SdkPath) {
        $SdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    }
}
if (-not (Test-Path $SdkPath)) { Fail "Android SDK not found at '$SdkPath'. Install Android Studio, open SDK Manager, install Android SDK Command-line Tools (latest), then rerun: .\scripts\setup.ps1 -SdkPath '<SDK path>'." }
$SdkPath = (Resolve-Path $SdkPath).Path
$env:ANDROID_HOME = $SdkPath
$env:ANDROID_SDK_ROOT = $SdkPath
$SdkManager = Find-SdkManager $SdkPath
if (-not $SdkManager) { Write-Host 'sdkmanager unavailable; validating already-installed SDK packages.' -ForegroundColor Yellow }

$Packages = @('platform-tools','platforms;android-37','build-tools;36.0.0','ndk;28.2.13676358','cmake;3.22.1')
Write-Host "Android SDK: $SdkPath"
Write-Host 'Checking required Android SDK packages...'
if ($SdkManager) {
    $InstalledOutput = (& $SdkManager --sdk_root=$SdkPath --list_installed 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { Fail 'sdkmanager could not inspect the SDK. Open Android Studio > SDK Manager and accept licenses, then rerun setup.' }
    $Missing = @($Packages | Where-Object { $InstalledOutput -notmatch [regex]::Escape($_) })
} else {
    $PackagePaths = @('platform-tools','build-tools\36.0.0','ndk\28.2.13676358','cmake\3.22.1')
    $Missing = @($PackagePaths | Where-Object { -not (Test-Path (Join-Path $SdkPath $_)) })
    if (-not (Get-ChildItem (Join-Path $SdkPath 'platforms') -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'android-37*' })) { $Missing += 'platforms;android-37' }
}
if ($Missing.Count -gt 0) {
    if (-not $SdkManager) { Fail "Required SDK packages are missing ($($Missing -join ', ')) and sdkmanager is unavailable. Install Android SDK Command-line Tools (latest) in Android Studio > SDK Manager > SDK Tools, then rerun setup." }
    Write-Host ('Installing: ' + ($Missing -join ', ')) -ForegroundColor Yellow
    if ($AcceptLicenses) { (1..100 | ForEach-Object { 'y' }) | & $SdkManager --sdk_root=$SdkPath --licenses | Out-Null }
    & $SdkManager --sdk_root=$SdkPath @Missing
    if ($LASTEXITCODE -ne 0) { Fail 'Android SDK package installation failed. Accept SDK licenses in Android Studio or run sdkmanager --licenses, then rerun setup.' }
}
Write-Host 'Android SDK packages: OK' -ForegroundColor Green

# Keep all existing local secrets/settings. Only add or update the machine-specific SDK path.
$LocalProperties = Join-Path $Root 'local.properties'
$Lines = @()
if (Test-Path $LocalProperties) { $Lines = @(Get-Content $LocalProperties) }
$SdkValue = $SdkPath.Replace('\','/')
$SdkLine = 'sdk.dir=' + $SdkValue
$Found = $false
$Updated = foreach ($Line in $Lines) {
    if ($Line -match '^\s*sdk\.dir=') {
        if (-not $Found) { $Found = $true; $SdkLine }
    } else { $Line }
}
if (-not $Found) { $Updated = @($Updated) + $SdkLine }
[System.IO.File]::WriteAllLines($LocalProperties, [string[]]$Updated, [System.Text.UTF8Encoding]::new($false))

$Wrapper = Join-Path $Root 'gradlew.bat'
if (-not (Test-Path $Wrapper)) { Fail 'Gradle wrapper is missing. Restore gradlew.bat and gradle/wrapper files from Git.' }
Write-Host 'Local SDK configuration: written to ignored local.properties (existing settings preserved)' -ForegroundColor Green
Write-Host 'Gradle wrapper: present; Gradle itself is downloaded at the pinned version on first use.' -ForegroundColor Green
if (-not (Test-Path (Join-Path $Root 'keystore.properties'))) {
    Write-Host 'Release signing: no local keystore.properties. Debug builds work; restore your private keystore + keystore.properties backup for locally signed production APKs. CI signing uses GitHub Secrets.' -ForegroundColor Yellow
} else {
    Write-Host 'Release signing config: present (credentials were not read or changed).' -ForegroundColor Green
}
if (-not (Test-Path (Join-Path $Root 'local.properties'))) { Fail 'Could not create local.properties.' }
Write-Host "`nSetup complete. Validate with: .\gradlew.bat :app:tasks --quiet" -ForegroundColor Green
