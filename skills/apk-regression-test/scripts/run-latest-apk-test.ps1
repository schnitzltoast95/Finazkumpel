<#
.SYNOPSIS
Runs the complete Finanzkumpel regression workflow for an exact or latest APK.

.DESCRIPTION
Runs project checks, pairs the Android test APK only when the selected APK is
byte-identical to the current project build, and invokes tools/test-apk.ps1.
The default device mode is a clean package-scoped replacement.
#>

#requires -Version 5.1

[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$ProjectPath = "C:\Users\maria\Documents\ChatGPT\Buchhaltungs App",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$ApkPath,

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$TestApkPath,

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$DeviceSerial,

    [Parameter()]
    [switch]$KeepData,

    [Parameter()]
    [switch]$SkipProjectChecks,

    [Parameter()]
    [switch]$DryRun,

    [Parameter()]
    [ValidateRange(1, 600)]
    [int]$DeviceTimeoutSeconds = 120
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-ProjectFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    $candidate = if ([System.IO.Path]::IsPathRooted($Path)) {
        $Path
    } else {
        Join-Path $script:ResolvedProjectPath $Path
    }
    $fullPath = [System.IO.Path]::GetFullPath($candidate)
    if (-not [System.IO.File]::Exists($fullPath)) {
        throw "$Label not found: $fullPath"
    }
    return $fullPath
}

function Find-Adb {
    $pathCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($null -ne $pathCommand) {
        return $pathCommand.Source
    }

    $candidates = @()
    foreach ($sdkRoot in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)) {
        if (-not [string]::IsNullOrWhiteSpace($sdkRoot)) {
            $candidates += Join-Path $sdkRoot "platform-tools\adb.exe"
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $candidates += Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    }

    foreach ($candidate in $candidates) {
        if ([System.IO.File]::Exists($candidate)) {
            return $candidate
        }
    }
    throw "adb.exe was not found in PATH or the configured Android SDK locations."
}

function Confirm-DryRunDevice {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AdbPath,

        [Parameter()]
        [string]$RequestedSerial
    )

    $output = @(& $AdbPath devices)
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed with exit code $LASTEXITCODE."
    }

    $devices = @(
        foreach ($line in $output) {
            if ($line -match "^(\S+)\s+(\S+)$" -and $Matches[1] -ne "List") {
                [pscustomobject]@{
                    Serial = $Matches[1]
                    State = $Matches[2]
                }
            }
        }
    )

    if (-not [string]::IsNullOrWhiteSpace($RequestedSerial)) {
        $requested = @($devices | Where-Object { $_.Serial -ceq $RequestedSerial })
        if ($requested.Count -ne 1) {
            throw "Requested ADB device is not connected: $RequestedSerial"
        }
        if ($requested[0].State -ne "device") {
            throw "Requested ADB device is not authorized and ready: $RequestedSerial=$($requested[0].State)"
        }
        return $requested[0].Serial
    }

    $ready = @($devices | Where-Object { $_.State -eq "device" })
    if ($ready.Count -eq 0) {
        throw "No authorized ADB device is connected."
    }
    if ($ready.Count -gt 1) {
        throw "Several authorized ADB devices are connected; specify -DeviceSerial."
    }
    return $ready[0].Serial
}

$script:ResolvedProjectPath = [System.IO.Path]::GetFullPath($ProjectPath)
if (-not [System.IO.Directory]::Exists($script:ResolvedProjectPath)) {
    throw "Project directory not found: $script:ResolvedProjectPath"
}

$projectRunner = Join-Path $script:ResolvedProjectPath "tools\test-project.ps1"
$apkRunner = Join-Path $script:ResolvedProjectPath "tools\test-apk.ps1"
if (-not [System.IO.File]::Exists($projectRunner)) {
    throw "Project test runner not found: $projectRunner"
}
if (-not [System.IO.File]::Exists($apkRunner)) {
    throw "APK test runner not found: $apkRunner"
}

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $distDirectory = Join-Path $script:ResolvedProjectPath "dist"
    if (-not [System.IO.Directory]::Exists($distDirectory)) {
        throw "APK directory not found: $distDirectory"
    }
    $apkCandidates = @(
        Get-ChildItem -LiteralPath $distDirectory -File -Filter "*.apk" |
            Where-Object { $_.Name -notmatch "(?i)androidTest" } |
            Sort-Object -Property @(
                @{ Expression = "LastWriteTimeUtc"; Descending = $true },
                @{ Expression = "Name"; Descending = $true }
            )
    )
    if ($apkCandidates.Count -eq 0) {
        throw "No application APK found in: $distDirectory"
    }
    $resolvedApk = $apkCandidates[0].FullName
} else {
    $resolvedApk = Resolve-ProjectFile -Path $ApkPath -Label "APK"
}

$resolvedApkHash = (Get-FileHash -LiteralPath $resolvedApk -Algorithm SHA256).Hash
Write-Host "Selected APK: $resolvedApk" -ForegroundColor Cyan
Write-Host "APK SHA-256: $resolvedApkHash"
Write-Host "Install mode: $(if ($KeepData) { 'preserve app data' } else { 'clean package replacement' })"

if ($DryRun) {
    $adbPath = Find-Adb
    $confirmedSerial = Confirm-DryRunDevice -AdbPath $adbPath -RequestedSerial $DeviceSerial
    if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
        $DeviceSerial = $confirmedSerial
    }
    Write-Host "ADB device ready: $confirmedSerial" -ForegroundColor Green
}

if (-not $SkipProjectChecks) {
    if ($DryRun) {
        Write-Host "DRY RUN: would execute $projectRunner -Offline"
    } else {
        & $projectRunner -Offline
        if ($LASTEXITCODE -ne 0) {
            throw "Project checks failed with exit code $LASTEXITCODE."
        }
    }
}

$resolvedTestApk = $null
if (-not [string]::IsNullOrWhiteSpace($TestApkPath)) {
    $resolvedTestApk = Resolve-ProjectFile -Path $TestApkPath -Label "Test APK"
} else {
    $builtAppApk = Join-Path $script:ResolvedProjectPath "app\build\outputs\apk\debug\app-debug.apk"
    $builtTestApk = Join-Path $script:ResolvedProjectPath "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
    if ([System.IO.File]::Exists($builtAppApk) -and [System.IO.File]::Exists($builtTestApk)) {
        $builtHash = (Get-FileHash -LiteralPath $builtAppApk -Algorithm SHA256).Hash
        if ($resolvedApkHash -eq $builtHash) {
            $resolvedTestApk = $builtTestApk
            Write-Host "Matching instrumentation APK: $resolvedTestApk" -ForegroundColor Green
        } else {
            Write-Warning "The selected APK is not byte-identical to the current project build. Instrumentation tests will be skipped unless -TestApkPath is supplied."
        }
    } else {
        Write-Warning "No current app/test APK pair was found. Instrumentation tests will be skipped unless -TestApkPath is supplied."
    }
}

$runnerArguments = @{
    ApkPath = $resolvedApk
    DeviceTimeoutSeconds = $DeviceTimeoutSeconds
}
if (-not [string]::IsNullOrWhiteSpace($resolvedTestApk)) {
    $runnerArguments.TestApkPath = $resolvedTestApk
}
if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $runnerArguments.DeviceSerial = $DeviceSerial
}
if ($KeepData) {
    $runnerArguments.KeepData = $true
}

if ($DryRun) {
    Write-Host "DRY RUN: would execute $apkRunner"
    $runnerArguments.GetEnumerator() |
        Sort-Object Key |
        ForEach-Object { Write-Host "  $($_.Key)=$($_.Value)" }
    return
}

& $apkRunner @runnerArguments
if ($LASTEXITCODE -ne 0) {
    throw "APK regression test failed with exit code $LASTEXITCODE."
}
