<#
.SYNOPSIS
Fuehrt die automatisierten Projektpruefungen aus und testet optional die gebauten APKs.

.DESCRIPTION
Validiert die synthetischen Belege, startet JVM-Tests und Android Lint, baut die App-
und Instrumentierungs-APK und kann anschliessend beide auf einem Emulator/Testgeraet
mit tools/test-apk.ps1 ausfuehren.

.EXAMPLE
.\tools\test-project.ps1

.EXAMPLE
.\tools\test-project.ps1 -WithDevice -DeviceSerial emulator-5554
#>

#requires -Version 5.1

[CmdletBinding()]
param(
    [Parameter()]
    [switch]$WithDevice,

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$DeviceSerial,

    [Parameter()]
    [switch]$KeepData,

    [Parameter()]
    [switch]$SkipTestDataValidation,

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$PythonPath,

    [Parameter()]
    [switch]$Offline,

    [Parameter()]
    [ValidateRange(1, 600)]
    [int]$DeviceTimeoutSeconds = 120
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$gradleWrapper = Join-Path $repositoryRoot "gradlew.bat"
$appApk = Join-Path $repositoryRoot "app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $repositoryRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
$unitResultDirectory = Join-Path $repositoryRoot "app\build\test-results\testDebugUnitTest"
$unitReport = Join-Path $repositoryRoot "app\build\reports\tests\testDebugUnitTest\index.html"
$lintXml = Join-Path $repositoryRoot "app\build\reports\lint-results-debug.xml"
$lintHtml = Join-Path $repositoryRoot "app\build\reports\lint-results-debug.html"

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Label,

        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter()]
        [string[]]$CommandArguments = @()
    )

    Write-Host ""
    Write-Host "=== $Label ===" -ForegroundColor Cyan
    & $FilePath @CommandArguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Label ist mit Exitcode $LASTEXITCODE fehlgeschlagen."
    }
}

Push-Location $repositoryRoot
try {
    if (-not $SkipTestDataValidation) {
        $pythonCandidates = @()
        if (-not [string]::IsNullOrWhiteSpace($PythonPath)) {
            $pythonCandidates += $PythonPath
        }
        if (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
            $pythonCandidates += Join-Path $env:USERPROFILE ".cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
        }
        $systemPython = Get-Command python.exe -ErrorAction SilentlyContinue
        if ($null -eq $systemPython) {
            $systemPython = Get-Command python -ErrorAction SilentlyContinue
        }
        if ($null -ne $systemPython) {
            $pythonCandidates += $systemPython.Source
        }

        $pythonExecutable = $null
        foreach ($candidate in @($pythonCandidates | Select-Object -Unique)) {
            if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
                continue
            }
            & $candidate -c "import PIL, pypdf, reportlab" 2>$null
            if ($LASTEXITCODE -eq 0) {
                $pythonExecutable = (Get-Item -LiteralPath $candidate).FullName
                break
            }
        }
        if ($null -eq $pythonExecutable) {
            throw "Kein Python mit Pillow, pypdf und reportlab gefunden. Gib -PythonPath an, installiere die Pakete oder nutze -SkipTestDataValidation."
        }
        if (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot "output\Quartalsmappe_Testdaten_Waxingstudio_2026.zip") -PathType Leaf)) {
            Invoke-CheckedCommand `
                -Label "Synthetische Belege erzeugen" `
                -FilePath $pythonExecutable `
                -CommandArguments @((Join-Path $repositoryRoot "tools\generate_test_documents.py"))
        }
        Invoke-CheckedCommand `
            -Label "Synthetische Belege validieren" `
            -FilePath $pythonExecutable `
            -CommandArguments @((Join-Path $repositoryRoot "tools\validate_test_documents.py"))
    }

    $gradleArguments = @(
        ":app:testDebugUnitTest",
        ":app:lintDebug",
        ":app:assembleDebug",
        ":app:assembleDebugAndroidTest"
    )
    if ($Offline) {
        $gradleArguments += "--offline"
    }
    Invoke-CheckedCommand `
        -Label "Unit-Tests, Lint und APKs bauen" `
        -FilePath $gradleWrapper `
        -CommandArguments $gradleArguments

    foreach ($requiredApk in @($appApk, $testApk)) {
        if (-not (Test-Path -LiteralPath $requiredApk -PathType Leaf)) {
            throw "Erwartete APK fehlt nach dem Build: $requiredApk"
        }
    }

    $testCount = 0
    $failureCount = 0
    if (Test-Path -LiteralPath $unitResultDirectory -PathType Container) {
        foreach ($resultFile in @(Get-ChildItem -LiteralPath $unitResultDirectory -Filter "*.xml" -File)) {
            [xml]$result = Get-Content -LiteralPath $resultFile.FullName
            $testCount += [int]$result.testsuite.tests
            $failureCount += [int]$result.testsuite.failures + [int]$result.testsuite.errors
        }
    }

    $lintErrorCount = 0
    $lintWarningCount = 0
    if (Test-Path -LiteralPath $lintXml -PathType Leaf) {
        [xml]$lintResult = Get-Content -LiteralPath $lintXml
        $lintIssues = @($lintResult.issues.issue)
        $lintErrorCount = @($lintIssues | Where-Object { $_.severity -eq "Error" }).Count
        $lintWarningCount = @($lintIssues | Where-Object { $_.severity -eq "Warning" }).Count
    }

    if ($WithDevice) {
        $apkRunner = Join-Path $repositoryRoot "tools\test-apk.ps1"
        $runnerArguments = @(
            "-ApkPath", $appApk,
            "-TestApkPath", $testApk,
            "-DeviceTimeoutSeconds", $DeviceTimeoutSeconds.ToString()
        )
        if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
            $runnerArguments += @("-DeviceSerial", $DeviceSerial)
        }
        if ($KeepData) {
            $runnerArguments += "-KeepData"
        }
        Invoke-CheckedCommand `
            -Label "APK auf Emulator/Testgeraet pruefen" `
            -FilePath $apkRunner `
            -CommandArguments $runnerArguments
    }

    Write-Host ""
    Write-Host "AUTOMATISIERTER TESTLAUF BESTANDEN" -ForegroundColor Green
    Write-Host "JVM-Tests: $testCount, Fehler: $failureCount"
    Write-Host "Lint: $lintErrorCount Fehler, $lintWarningCount Warnungen"
    Write-Host "App-APK: $appApk"
    Write-Host "Test-APK: $testApk"
    Write-Host "Unit-Test-Bericht: $unitReport"
    Write-Host "Lint-Bericht: $lintHtml"
    if (-not $WithDevice) {
        Write-Host "Geraetetests: nicht angefordert (mit -WithDevice aktivieren)" -ForegroundColor Yellow
    }
}
finally {
    Pop-Location
}

