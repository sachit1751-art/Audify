[CmdletBinding()]
param(
    [switch]$AcceptLicenses,
    [switch]$Release
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $Root

function Invoke-Gradle([string[]]$Tasks) {
    Write-Host "`n> .\gradlew.bat --no-daemon $($Tasks -join ' ')" -ForegroundColor Cyan
    & (Join-Path $Root 'gradlew.bat') --no-daemon @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed (exit $LASTEXITCODE): $($Tasks -join ' ')" }
}

Write-Host 'AUDIFY — fresh clone setup and verification' -ForegroundColor Cyan
& (Join-Path $PSScriptRoot 'setup.ps1') -AcceptLicenses:$AcceptLicenses
if ($LASTEXITCODE -ne 0) { throw 'Android environment setup failed.' }

Invoke-Gradle @(
    ':app:testProdDebugUnitTest',
    ':sharedUi:jvmTest'
)

Invoke-Gradle @(
    ':app:assembleProdDebug'
)

if ($Release) {
    $SigningProperties = Join-Path $Root 'keystore.properties'
    if (-not (Test-Path $SigningProperties)) {
        throw 'Release signing config is missing. Restore the private keystore and keystore.properties from your secure backup; do not generate a replacement key for an existing app identity.'
    }
    Invoke-Gradle @(':app:assembleProdRelease')
    Write-Host 'Release variant built using the signing files present in this clone. The signing key and credentials have been exposed publicly; a GitHub account login does not revoke copies already downloaded by others.' -ForegroundColor Red
}

Write-Host "`nFresh-clone verification completed." -ForegroundColor Green
Write-Host 'Debug APKs:'
Write-Host '  app\build\outputs\apk\prod\debug\'
if ($Release) { Write-Host '  app\build\outputs\apk\prod\release\' }
