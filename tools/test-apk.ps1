<#
.SYNOPSIS
Installiert und prueft eine exakt angegebene Android-APK auf einem Emulator oder Testgeraet.

.DESCRIPTION
Der Runner ermittelt ADB, wartet auf genau ein verwendbares Geraet, installiert die
angegebene APK und kopiert die synthetischen Quartalsmappe-Testdaten nach
Download/Quartalsmappe-Testdaten. Optional wird eine separate androidTest-APK
installiert und ihr AndroidJUnitRunner ausgefuehrt.

Ohne -KeepData wird ausschliesslich das zur APK gehoerende App-Paket vor der
Installation deinstalliert. Andere Apps und der Download-Testordner werden nicht
geloescht. Jeder Lauf erzeugt einen eigenen Ergebnisordner unter output/test-runs.

.PARAMETER ApkPath
Exakter Pfad zur zu pruefenden APK. Wildcards werden nicht ausgewertet.

.PARAMETER TestApkPath
Optionaler exakter Pfad zu einer passenden androidTest-APK.

.PARAMETER DeviceSerial
Optionale ADB-Seriennummer. Ohne diesen Parameter muss genau ein Geraet bereit sein.

.PARAMETER KeepData
Aktualisiert die App mit adb install -r, ohne sie vorher zu deinstallieren.

.PARAMETER DeviceTimeoutSeconds
Maximale Wartezeit auf ein autorisiertes ADB-Geraet; Standard sind 120 Sekunden.

.EXAMPLE
.\tools\test-apk.ps1 -ApkPath .\dist\Quartalsmappe-Prototyp-0.2.apk

.EXAMPLE
.\tools\test-apk.ps1 -ApkPath .\app\build\outputs\apk\debug\app-debug.apk `
  -TestApkPath .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk

.EXAMPLE
.\tools\test-apk.ps1 -ApkPath .\dist\Quartalsmappe-Prototyp-0.2.apk `
  -DeviceSerial emulator-5554 -KeepData
#>

#requires -Version 5.1

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
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
    [ValidateRange(1, 600)]
    [int]$DeviceTimeoutSeconds = 120
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:AdbPath = $null
$script:SelectedDevice = $null
$script:RunLogPath = $null
$script:RemoteDataBase = "/sdcard/Download/Quartalsmappe-Testdaten"
$script:RemoteDataRoot = $script:RemoteDataBase
$script:TargetProcessIds = @()
$script:LogcatSince = $null

function Write-RunMessage {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message,

        [ValidateSet("INFO", "WARN", "ERROR", "OK")]
        [string]$Level = "INFO"
    )

    $line = "[{0}] [{1}] {2}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Level, $Message
    switch ($Level) {
        "ERROR" { Write-Host $line -ForegroundColor Red }
        "WARN"  { Write-Host $line -ForegroundColor Yellow }
        "OK"    { Write-Host $line -ForegroundColor Green }
        default { Write-Host $line }
    }

    if (-not [string]::IsNullOrWhiteSpace($script:RunLogPath)) {
        Add-Content -LiteralPath $script:RunLogPath -Value $line -Encoding UTF8
    }
}

function Resolve-ExactFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Description,

        [string]$RequiredExtension
    )

    if ($Path.IndexOfAny([char[]]"*?") -ge 0) {
        throw "$Description muss ein exakter Pfad ohne Wildcards sein: $Path"
    }

    try {
        $item = Get-Item -LiteralPath $Path -ErrorAction Stop
    }
    catch {
        throw "$Description wurde nicht gefunden: $Path"
    }

    if (-not ($item -is [System.IO.FileInfo])) {
        throw "$Description ist keine Datei: $($item.FullName)"
    }

    if (-not [string]::IsNullOrWhiteSpace($RequiredExtension) -and
        -not $item.Extension.Equals($RequiredExtension, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "$Description muss eine $RequiredExtension-Datei sein: $($item.FullName)"
    }

    return $item.FullName
}

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter()]
        [string[]]$CommandArguments = @(),

        [Parameter()]
        [switch]$AllowFailure,

        [Parameter()]
        [switch]$Quiet,

        [Parameter()]
        [string]$OutputPath
    )

    $displayArguments = @($CommandArguments | ForEach-Object {
        if ($_ -match "\s") { '"{0}"' -f ($_ -replace '"', '\"') } else { $_ }
    }) -join " "
    Write-RunMessage -Message ("Ausfuehren: {0} {1}" -f $FilePath, $displayArguments).TrimEnd()

    # Windows PowerShell stellt stderr nativer Prozesse sonst als ErrorRecord dar.
    # Innerhalb dieser Funktion wird es bewusst zusammen mit stdout aufgezeichnet.
    $ErrorActionPreference = "Continue"
    $captured = @(& $FilePath @CommandArguments 2>&1)
    $exitCode = $LASTEXITCODE
    $lines = @($captured | ForEach-Object { $_.ToString() })
    $text = $lines -join [Environment]::NewLine

    if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
        $parent = Split-Path -Parent $OutputPath
        if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
            [void](New-Item -ItemType Directory -Path $parent -Force)
        }
        [System.IO.File]::WriteAllText(
            $OutputPath,
            $(if ($text.Length -gt 0) { $text + [Environment]::NewLine } else { "" }),
            [System.Text.UTF8Encoding]::new($false)
        )
    }

    if (-not $Quiet -and $text.Length -gt 0) {
        Write-Host $text
    }

    $result = [pscustomobject]@{
        ExitCode = $exitCode
        Output = $text
        Lines = $lines
    }

    if ($exitCode -ne 0 -and -not $AllowFailure) {
        $detail = if ($text.Length -gt 0) { "`n$text" } else { "" }
        throw "Befehl fehlgeschlagen (Exitcode $exitCode): $FilePath $displayArguments$detail"
    }

    return $result
}

function Find-Adb {
    $candidates = @()
    $fromPath = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($null -eq $fromPath) {
        $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    }
    if ($null -ne $fromPath) {
        $candidates += $fromPath.Source
    }

    foreach ($sdkRoot in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)) {
        if (-not [string]::IsNullOrWhiteSpace($sdkRoot)) {
            $candidates += Join-Path $sdkRoot "platform-tools\adb.exe"
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $candidates += Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    }

    foreach ($candidate in @($candidates | Select-Object -Unique)) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Get-Item -LiteralPath $candidate).FullName
        }
    }

    throw "adb.exe wurde weder im PATH noch unter ANDROID_SDK_ROOT, ANDROID_HOME oder LOCALAPPDATA\Android\Sdk gefunden. Installiere Android SDK Platform-Tools."
}

function Get-SdkRoots {
    param([string]$AdbExecutable)

    $roots = @()
    foreach ($candidate in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)) {
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            $roots += $candidate
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $roots += Join-Path $env:LOCALAPPDATA "Android\Sdk"
    }

    $adbParent = Split-Path -Parent $AdbExecutable
    if ((Split-Path -Leaf $adbParent) -ieq "platform-tools") {
        $roots += Split-Path -Parent $adbParent
    }

    return @($roots | Where-Object { Test-Path -LiteralPath $_ -PathType Container } | Select-Object -Unique)
}

function Find-Aapt {
    param([string[]]$SdkRoots)

    $fromPath = Get-Command aapt.exe -ErrorAction SilentlyContinue
    if ($null -ne $fromPath) {
        return $fromPath.Source
    }

    $buildTools = @()
    foreach ($sdkRoot in $SdkRoots) {
        $buildToolsRoot = Join-Path $sdkRoot "build-tools"
        if (Test-Path -LiteralPath $buildToolsRoot -PathType Container) {
            $buildTools += Get-ChildItem -LiteralPath $buildToolsRoot -Directory -ErrorAction SilentlyContinue
        }
    }

    foreach ($directory in @($buildTools | Sort-Object {
        $parsed = [version]::new()
        if ([version]::TryParse($_.Name, [ref]$parsed)) { $parsed } else { [version]"0.0" }
    } -Descending)) {
        $candidate = Join-Path $directory.FullName "aapt.exe"
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }

    return $null
}

function Get-ApkMetadata {
    param(
        [string]$Path,
        [AllowNull()][string]$AaptPath,
        [string]$BadgingOutputPath
    )

    if (-not [string]::IsNullOrWhiteSpace($AaptPath)) {
        $result = Invoke-NativeCommand -FilePath $AaptPath -CommandArguments @("dump", "badging", $Path) -AllowFailure -Quiet -OutputPath $BadgingOutputPath
        if ($result.ExitCode -eq 0) {
            $match = [regex]::Match($result.Output, "(?m)^package:\s+name='([^']+)'\s+versionCode='([^']*)'\s+versionName='([^']*)'")
            if ($match.Success) {
                return [pscustomobject]@{
                    PackageName = $match.Groups[1].Value
                    VersionCode = $match.Groups[2].Value
                    VersionName = $match.Groups[3].Value
                    MetadataSource = "aapt"
                }
            }
        }
        Write-RunMessage -Level "WARN" -Message "aapt konnte die Paketmetadaten von '$Path' nicht lesen."
    }

    throw "Die Paket-ID der APK konnte nicht sicher gelesen werden. Installiere Android SDK Build-Tools (aapt.exe). Der Lauf wurde beendet, bevor ein App-Paket deinstalliert wurde."
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$AdbArguments,

        [switch]$AllowFailure,
        [switch]$Quiet,
        [string]$OutputPath,
        [switch]$WithoutDevice
    )

    $arguments = @()
    if (-not $WithoutDevice) {
        if ([string]::IsNullOrWhiteSpace($script:SelectedDevice)) {
            throw "Intern: Noch kein ADB-Geraet ausgewaehlt."
        }
        $arguments += @("-s", $script:SelectedDevice)
    }
    $arguments += $AdbArguments

    return Invoke-NativeCommand -FilePath $script:AdbPath -CommandArguments $arguments -AllowFailure:$AllowFailure -Quiet:$Quiet -OutputPath $OutputPath
}

function Get-AdbDevices {
    $result = Invoke-Adb -AdbArguments @("devices") -WithoutDevice -Quiet
    $devices = @()
    foreach ($line in $result.Lines) {
        $match = [regex]::Match($line.Trim(), "^(\S+)\s+(device|offline|unauthorized|recovery|sideload|bootloader)$")
        if ($match.Success) {
            $devices += [pscustomobject]@{
                Serial = $match.Groups[1].Value
                State = $match.Groups[2].Value
            }
        }
    }
    return @($devices)
}

function Wait-ForReadyDevice {
    param(
        [AllowNull()][string]$RequestedSerial,
        [int]$TimeoutSeconds = 120
    )

    if (-not [string]::IsNullOrWhiteSpace($RequestedSerial) -and $RequestedSerial -match "\s") {
        throw "DeviceSerial darf keine Leerzeichen enthalten: '$RequestedSerial'"
    }

    Write-RunMessage -Message "Warte bis zu $TimeoutSeconds Sekunden auf ein autorisiertes ADB-Geraet ..."
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastStateDescription = "kein Geraet erkannt"

    while ((Get-Date) -lt $deadline) {
        $devices = @(Get-AdbDevices)
        $lastStateDescription = if ($devices.Count -gt 0) {
            ($devices | ForEach-Object { "$($_.Serial)=$($_.State)" }) -join ", "
        }
        else {
            "kein Geraet erkannt"
        }

        if (-not [string]::IsNullOrWhiteSpace($RequestedSerial)) {
            $requested = @($devices | Where-Object { $_.Serial -ceq $RequestedSerial })
            if ($requested.Count -gt 0 -and $requested[0].State -eq "device") {
                return $RequestedSerial
            }
        }
        else {
            $ready = @($devices | Where-Object { $_.State -eq "device" })
            if ($ready.Count -eq 1) {
                return $ready[0].Serial
            }
            if ($ready.Count -gt 1) {
                $serials = ($ready | ForEach-Object { $_.Serial }) -join ", "
                throw "Mehrere ADB-Geraete sind bereit ($serials). Wiederhole den Aufruf mit -DeviceSerial."
            }
        }

        Start-Sleep -Seconds 2
    }

    if (-not [string]::IsNullOrWhiteSpace($RequestedSerial)) {
        throw "ADB-Geraet '$RequestedSerial' wurde innerhalb von $TimeoutSeconds Sekunden nicht bereit. Letzter Zustand: $lastStateDescription"
    }
    throw "Innerhalb von $TimeoutSeconds Sekunden wurde kein einzelnes autorisiertes ADB-Geraet bereit. Letzter Zustand: $lastStateDescription"
}

function Test-PackageInstalled {
    param([string]$PackageName)

    $result = Invoke-Adb -AdbArguments @("shell", "pm", "list", "packages", $PackageName) -AllowFailure -Quiet
    if ($result.ExitCode -ne 0) {
        return $false
    }
    return @($result.Lines | Where-Object { $_.Trim() -ceq "package:$PackageName" }).Count -gt 0
}

function Uninstall-PackageIfPresent {
    param([string]$PackageName)

    if (Test-PackageInstalled -PackageName $PackageName) {
        Write-RunMessage -Message "Saubere Installation: Deinstalliere ausschliesslich '$PackageName'."
        [void](Invoke-Adb -AdbArguments @("uninstall", $PackageName))
    }
}

function Get-DefaultTestDataFiles {
    param([string]$RepositoryRoot)

    $entries = @()
    $outputRoot = Join-Path $RepositoryRoot "output"
    $archive = Join-Path $outputRoot "Quartalsmappe_Testdaten_Waxingstudio_2026.zip"
    if (Test-Path -LiteralPath $archive -PathType Leaf) {
        $entries += [pscustomobject]@{ LocalPath = $archive; RemoteDirectory = "" }
    }

    foreach ($subdirectory in @("pdf", "images", "kassenexport")) {
        $localDirectory = Join-Path $outputRoot $subdirectory
        if (Test-Path -LiteralPath $localDirectory -PathType Container) {
            foreach ($file in @(Get-ChildItem -LiteralPath $localDirectory -File -Recurse | Sort-Object FullName)) {
                $relativeParent = $file.DirectoryName.Substring($localDirectory.Length).TrimStart('\', '/')
                $remoteSubdirectory = @($subdirectory, $relativeParent) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
                $entries += [pscustomobject]@{
                    LocalPath = $file.FullName
                    RemoteDirectory = ($remoteSubdirectory -join "/")
                }
            }
        }
    }

    $guide = Join-Path $RepositoryRoot "testdaten\TESTDATEN-LEITFADEN.md"
    if (Test-Path -LiteralPath $guide -PathType Leaf) {
        $entries += [pscustomobject]@{ LocalPath = $guide; RemoteDirectory = "" }
    }

    return @($entries)
}

function Push-TestData {
    param(
        [string]$RepositoryRoot,
        [string]$ManifestPath
    )

    [void](Invoke-Adb -AdbArguments @("shell", "mkdir", "-p", $script:RemoteDataRoot))
    $files = @(Get-DefaultTestDataFiles -RepositoryRoot $RepositoryRoot)
    if ($files.Count -eq 0) {
        Write-RunMessage -Level "WARN" -Message "Keine generierten Testdaten gefunden; der APK-Lauf wird ohne Belegdateien fortgesetzt."
        [System.IO.File]::WriteAllText($ManifestPath, "Keine Testdaten gefunden.`r`n", [System.Text.UTF8Encoding]::new($false))
        return
    }

    $manifestLines = @()
    foreach ($group in @($files | Group-Object RemoteDirectory)) {
        $remoteDirectory = $script:RemoteDataRoot
        if (-not [string]::IsNullOrWhiteSpace($group.Name)) {
            $remoteDirectory = "$remoteDirectory/$($group.Name)"
            [void](Invoke-Adb -AdbArguments @("shell", "mkdir", "-p", $remoteDirectory))
        }

        foreach ($entry in $group.Group) {
            $remotePath = "$remoteDirectory/$([System.IO.Path]::GetFileName($entry.LocalPath))"
            [void](Invoke-Adb -AdbArguments @("push", $entry.LocalPath, $remotePath))
            $manifestLines += "$($entry.LocalPath)`t$remotePath"
        }
    }

    [System.IO.File]::WriteAllLines($ManifestPath, $manifestLines, [System.Text.UTF8Encoding]::new($false))
    Write-RunMessage -Level "OK" -Message "$($files.Count) Testdateien nach '$($script:RemoteDataRoot)' kopiert (vorhandene andere Dateien wurden nicht geloescht)."
}

function Start-TargetApp {
    param(
        [string]$PackageName,
        [string]$LaunchOutputPath
    )

    $resolve = Invoke-Adb -AdbArguments @(
        "shell", "cmd", "package", "resolve-activity", "--brief",
        "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LAUNCHER",
        "-p", $PackageName
    ) -AllowFailure -Quiet

    $component = @($resolve.Lines | ForEach-Object { $_.Trim() } | Where-Object {
        $_ -match "^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$"
    } | Select-Object -Last 1)

    if ($resolve.ExitCode -eq 0 -and $component.Count -eq 1) {
        $launch = Invoke-Adb -AdbArguments @("shell", "am", "start", "-W", "-n", $component[0]) -AllowFailure -OutputPath $LaunchOutputPath
        if ($launch.ExitCode -eq 0 -and
            $launch.Output -match "(?im)^Status:\s*ok\s*$" -and
            $launch.Output -notmatch "(?im)^Error:") {
            Write-RunMessage -Level "OK" -Message "App gestartet: $($component[0])"
            return
        }
    }

    Write-RunMessage -Level "WARN" -Message "Launcher-Komponente konnte nicht direkt aufgeloest werden; versuche einen gezielten Monkey-Start."
    $fallback = Invoke-Adb -AdbArguments @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1") -AllowFailure -OutputPath $LaunchOutputPath
    if ($fallback.ExitCode -ne 0 -or $fallback.Output -notmatch "Events injected:\s*1") {
        throw "Die installierte App '$PackageName' konnte nicht gestartet werden. Details: $LaunchOutputPath"
    }
    Write-RunMessage -Level "OK" -Message "App ueber Launcher-Intent gestartet."
}

function Get-TargetProcessIds {
    param([string]$PackageName)

    $result = Invoke-Adb -AdbArguments @("shell", "pidof", $PackageName) -AllowFailure -Quiet
    if ($result.ExitCode -ne 0) {
        return @()
    }

    return @([regex]::Matches($result.Output, "(?<!\d)\d+(?!\d)") | ForEach-Object { $_.Value } | Select-Object -Unique)
}

function Assert-TargetAppHealthy {
    param(
        [string]$PackageName,
        [string]$ForegroundOutputPath
    )

    $processIds = @(Get-TargetProcessIds -PackageName $PackageName)
    if ($processIds.Count -eq 0) {
        throw "Die App '$PackageName' besitzt zwei Sekunden nach dem Start keinen laufenden Prozess. Sie ist wahrscheinlich beim Start abgestuerzt."
    }
    $script:TargetProcessIds = @($script:TargetProcessIds + $processIds | Select-Object -Unique)

    $window = Invoke-Adb -AdbArguments @("shell", "dumpsys", "window", "windows") -AllowFailure -Quiet
    $focusLines = @($window.Lines | Where-Object { $_ -match "mCurrentFocus|mFocusedApp" })
    [System.IO.File]::WriteAllLines($ForegroundOutputPath, $focusLines, [System.Text.UTF8Encoding]::new($false))

    $anrLine = @($focusLines | Where-Object { $_ -match "(?i)Application Not Responding|\bANR\b" })
    if ($anrLine.Count -gt 0) {
        throw "Android meldet direkt nach dem Start einen ANR-Zustand fuer die App. Details: $ForegroundOutputPath"
    }

    if (@($focusLines | Where-Object { $_ -match [regex]::Escape($PackageName) }).Count -eq 0) {
        Write-RunMessage -Level "WARN" -Message "Der App-Prozess laeuft, aber die Ziel-App ist laut WindowManager nicht im Vordergrund (moeglicherweise liegt ein Systemdialog darueber)."
    }

    Write-RunMessage -Level "OK" -Message "Start-Gesundheitspruefung bestanden; Prozess-ID(s): $($processIds -join ', ')."
}

function Get-DeviceLogcatMarker {
    $result = Invoke-Adb -AdbArguments @("shell", "date", "+%m-%d %H:%M:%S.000") -AllowFailure -Quiet
    if ($result.ExitCode -eq 0) {
        $match = [regex]::Match($result.Output, "\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}")
        if ($match.Success) {
            return $match.Value
        }
    }
    Write-RunMessage -Level "WARN" -Message "Logcat-Startzeit konnte nicht ermittelt werden; Logs werden weiterhin auf die Zielprozess-ID begrenzt."
    return $null
}

function Save-DeviceScreenshot {
    param(
        [string]$RunId,
        [string]$Name,
        [string]$LocalPath
    )

    $safeName = $Name -replace "[^A-Za-z0-9_-]", "-"
    $remotePath = "$($script:RemoteDataRoot)/.runner-$RunId-$safeName.png"
    try {
        $capture = Invoke-Adb -AdbArguments @("shell", "screencap", "-p", $remotePath) -AllowFailure -Quiet
        if ($capture.ExitCode -ne 0) {
            Write-RunMessage -Level "WARN" -Message "Screenshot '$Name' konnte auf dem Geraet nicht erzeugt werden."
            return
        }

        $pull = Invoke-Adb -AdbArguments @("pull", $remotePath, $LocalPath) -AllowFailure -Quiet
        if ($pull.ExitCode -eq 0 -and (Test-Path -LiteralPath $LocalPath -PathType Leaf) -and (Get-Item -LiteralPath $LocalPath).Length -gt 0) {
            Write-RunMessage -Level "OK" -Message "Screenshot gespeichert: $LocalPath"
        }
        else {
            Write-RunMessage -Level "WARN" -Message "Screenshot '$Name' konnte nicht auf den PC kopiert werden."
        }
    }
    finally {
        # Entfernt nur die von diesem Lauf erzeugte temporaere Datei.
        [void](Invoke-Adb -AdbArguments @("shell", "rm", "-f", $remotePath) -AllowFailure -Quiet)
    }
}

function Find-InstrumentationComponent {
    param(
        [string]$TargetPackage,
        [string]$TestPackage
    )

    $result = Invoke-Adb -AdbArguments @("shell", "pm", "list", "instrumentation") -Quiet
    $matches = @()
    foreach ($line in $result.Lines) {
        $match = [regex]::Match($line.Trim(), "^instrumentation:(\S+)\s+\(target=([^\)]+)\)$")
        if ($match.Success -and $match.Groups[2].Value -ceq $TargetPackage) {
            $matches += [pscustomobject]@{
                Component = $match.Groups[1].Value
                Target = $match.Groups[2].Value
            }
        }
    }

    $exact = @($matches | Where-Object { $_.Component.StartsWith("$TestPackage/", [System.StringComparison]::Ordinal) })
    if ($exact.Count -eq 1) {
        return $exact[0].Component
    }
    if ($matches.Count -eq 1) {
        return $matches[0].Component
    }
    if ($matches.Count -eq 0) {
        throw "Die Test-APK registriert keinen Instrumentation Runner fuer '$TargetPackage'."
    }

    $components = ($matches | ForEach-Object { $_.Component }) -join ", "
    throw "Mehrere Instrumentation Runner zielen auf '$TargetPackage' ($components). Der Runner der Test-APK '$TestPackage' konnte nicht eindeutig bestimmt werden."
}

function Invoke-InstrumentationTests {
    param(
        [string]$Component,
        [string]$OutputPath
    )

    Write-RunMessage -Message "Starte Instrumentation-Tests: $Component"
    $result = Invoke-Adb -AdbArguments @("shell", "am", "instrument", "-w", "-r", $Component) -AllowFailure -OutputPath $OutputPath
    if ($result.ExitCode -ne 0) {
        throw "Instrumentation schlug mit Exitcode $($result.ExitCode) fehl. Details: $OutputPath"
    }

    if ($result.Output -match "(?im)(FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|shortMsg=Process crashed|Process crashed)") {
        throw "Mindestens ein Instrumentation-Test ist fehlgeschlagen oder abgestuerzt. Details: $OutputPath"
    }

    $ok = [regex]::Match($result.Output, "(?im)^OK\s+\((\d+)\s+tests?\)")
    if (-not $ok.Success) {
        throw "Der AndroidJUnitRunner meldete kein eindeutiges 'OK (... tests)'. Details: $OutputPath"
    }
    if ([int]$ok.Groups[1].Value -lt 1) {
        throw "Die Test-APK wurde ausgefuehrt, enthielt aber keine Tests. Details: $OutputPath"
    }

    Write-RunMessage -Level "OK" -Message "$($ok.Groups[1].Value) Instrumentation-Test(s) bestanden."
    return [int]$ok.Groups[1].Value
}

function Collect-Diagnostics {
    param(
        [string]$ResultDirectory,
        [string]$PackageName
    )

    Write-RunMessage -Message "Sammle Geraete-, Paket- und Logdaten ..."
    $deviceInfoPath = Join-Path $ResultDirectory "device-info.txt"
    $deviceLines = @("serial=$($script:SelectedDevice)")
    foreach ($property in @(
        "ro.product.manufacturer",
        "ro.product.model",
        "ro.product.device",
        "ro.build.version.release",
        "ro.build.version.sdk",
        "ro.build.fingerprint"
    )) {
        $value = Invoke-Adb -AdbArguments @("shell", "getprop", $property) -AllowFailure -Quiet
        $deviceLines += "$property=$($value.Output.Trim())"
    }
    [System.IO.File]::WriteAllLines($deviceInfoPath, $deviceLines, [System.Text.UTF8Encoding]::new($false))

    [void](Invoke-Adb -AdbArguments @("shell", "dumpsys", "package", $PackageName) -AllowFailure -Quiet -OutputPath (Join-Path $ResultDirectory "package-info.txt"))

    $currentProcessIds = @(Get-TargetProcessIds -PackageName $PackageName)
    $allProcessIds = @($script:TargetProcessIds + $currentProcessIds | Select-Object -Unique)
    $logSections = @()
    if ($allProcessIds.Count -eq 0) {
        $logSections += "Keine Zielprozess-ID verfuegbar; zum Schutz anderer Apps wurde kein systemweites Logcat gespeichert."
    }
    else {
        foreach ($processId in $allProcessIds) {
            $logcatArguments = @("logcat", "-d", "-v", "threadtime")
            if (-not [string]::IsNullOrWhiteSpace($script:LogcatSince)) {
                $logcatArguments += @("-T", $script:LogcatSince)
            }
            else {
                $logcatArguments += @("-t", "5000")
            }
            $logcatArguments += "--pid=$processId"
            $logcat = Invoke-Adb -AdbArguments $logcatArguments -AllowFailure -Quiet
            $logSections += "===== PID $processId ====="
            if ($logcat.ExitCode -eq 0 -and $logcat.Output.Length -gt 0) {
                $logSections += $logcat.Output
            }
            else {
                $logSections += "Kein Logcat fuer diese PID verfuegbar."
            }
        }
    }
    [System.IO.File]::WriteAllText((Join-Path $ResultDirectory "logcat.txt"), ($logSections -join [Environment]::NewLine) + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
}

# Validate literal input paths before changing any device state.
$resolvedApkPath = Resolve-ExactFile -Path $ApkPath -Description "APK" -RequiredExtension ".apk"
$resolvedTestApkPath = $null
if ($PSBoundParameters.ContainsKey("TestApkPath")) {
    $resolvedTestApkPath = Resolve-ExactFile -Path $TestApkPath -Description "Test-APK" -RequiredExtension ".apk"
}

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$runsRoot = Join-Path $repositoryRoot "output\test-runs"
[void](New-Item -ItemType Directory -Path $runsRoot -Force)

$startedAt = Get-Date
$runId = $startedAt.ToString("yyyyMMdd-HHmmss-fff")
$script:RemoteDataRoot = "$($script:RemoteDataBase)/$runId"
$safeApkName = [regex]::Replace([System.IO.Path]::GetFileNameWithoutExtension($resolvedApkPath), "[^A-Za-z0-9._-]", "-")
$resultDirectory = Join-Path $runsRoot "$runId-$safeApkName"
$collision = 1
while (Test-Path -LiteralPath $resultDirectory) {
    $resultDirectory = Join-Path $runsRoot ("{0}-{1}-{2:d2}" -f $runId, $safeApkName, $collision)
    $collision++
}
[void](New-Item -ItemType Directory -Path $resultDirectory)
$script:RunLogPath = Join-Path $resultDirectory "run.log"

$succeeded = $false
$failureMessage = $null
$targetMetadata = $null
$testMetadata = $null
$instrumentationComponent = $null
$instrumentationTestCount = $null
$apkHash = (Get-FileHash -LiteralPath $resolvedApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
$testApkHash = $null
if ($null -ne $resolvedTestApkPath) {
    $testApkHash = (Get-FileHash -LiteralPath $resolvedTestApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
}

Write-RunMessage -Message "APK-Testlauf beginnt. Ergebnisordner: $resultDirectory"
Write-RunMessage -Message "Exakte APK: $resolvedApkPath (SHA-256 $apkHash)"

try {
    $script:AdbPath = Find-Adb
    Write-RunMessage -Message "ADB gefunden: $($script:AdbPath)"
    $sdkRoots = @(Get-SdkRoots -AdbExecutable $script:AdbPath)
    $aaptPath = Find-Aapt -SdkRoots $sdkRoots
    if (-not [string]::IsNullOrWhiteSpace($aaptPath)) {
        Write-RunMessage -Message "APK-Metadatenwerkzeug gefunden: $aaptPath"
    }

    $targetMetadata = Get-ApkMetadata -Path $resolvedApkPath -AaptPath $aaptPath -BadgingOutputPath (Join-Path $resultDirectory "apk-badging.txt")
    if ($null -ne $resolvedTestApkPath) {
        $testMetadata = Get-ApkMetadata -Path $resolvedTestApkPath -AaptPath $aaptPath -BadgingOutputPath (Join-Path $resultDirectory "test-apk-badging.txt")
    }

    Write-RunMessage -Message "Zielpaket: $($targetMetadata.PackageName), Version $($targetMetadata.VersionName) ($($targetMetadata.VersionCode))"

    [void](Invoke-Adb -AdbArguments @("start-server") -WithoutDevice)
    $script:SelectedDevice = Wait-ForReadyDevice -RequestedSerial $DeviceSerial -TimeoutSeconds $DeviceTimeoutSeconds
    Write-RunMessage -Level "OK" -Message "ADB-Geraet ausgewaehlt: $($script:SelectedDevice)"
    [void](Invoke-Adb -AdbArguments @("get-state"))
    $script:LogcatSince = Get-DeviceLogcatMarker

    if (-not $KeepData) {
        if ($null -ne $testMetadata) {
            Uninstall-PackageIfPresent -PackageName $testMetadata.PackageName
        }
        Uninstall-PackageIfPresent -PackageName $targetMetadata.PackageName
    }
    else {
        Write-RunMessage -Message "-KeepData aktiv: Vorhandene App-Daten werden nicht geloescht."
    }

    [void](Invoke-Adb -AdbArguments @("install", "-r", $resolvedApkPath) -OutputPath (Join-Path $resultDirectory "install-app.txt"))
    if (-not (Test-PackageInstalled -PackageName $targetMetadata.PackageName)) {
        throw "Die APK wurde installiert, aber das erwartete Paket '$($targetMetadata.PackageName)' ist nicht vorhanden. Installiere Android SDK Build-Tools, damit aapt die APK-Paket-ID exakt bestimmen kann."
    }
    Write-RunMessage -Level "OK" -Message "APK installiert."

    Push-TestData -RepositoryRoot $repositoryRoot -ManifestPath (Join-Path $resultDirectory "pushed-files.txt")
    Start-TargetApp -PackageName $targetMetadata.PackageName -LaunchOutputPath (Join-Path $resultDirectory "launch.txt")
    Start-Sleep -Seconds 2
    Assert-TargetAppHealthy -PackageName $targetMetadata.PackageName -ForegroundOutputPath (Join-Path $resultDirectory "foreground-info.txt")
    Save-DeviceScreenshot -RunId $runId -Name "after-launch" -LocalPath (Join-Path $resultDirectory "01-after-launch.png")

    if ($null -ne $resolvedTestApkPath) {
        [void](Invoke-Adb -AdbArguments @("install", "-r", "-t", $resolvedTestApkPath) -OutputPath (Join-Path $resultDirectory "install-test-apk.txt"))
        if (-not (Test-PackageInstalled -PackageName $testMetadata.PackageName)) {
            throw "Die Test-APK wurde installiert, aber das erwartete Paket '$($testMetadata.PackageName)' ist nicht vorhanden."
        }
        $instrumentationComponent = Find-InstrumentationComponent -TargetPackage $targetMetadata.PackageName -TestPackage $testMetadata.PackageName
        $instrumentationTestCount = Invoke-InstrumentationTests -Component $instrumentationComponent -OutputPath (Join-Path $resultDirectory "instrumentation.txt")
    }
    else {
        Write-RunMessage -Level "WARN" -Message "Keine -TestApkPath angegeben: Installation, Start und Artefaktsammlung werden geprueft; UI-/Instrumentation-Tests werden uebersprungen."
    }

    $succeeded = $true
}
catch {
    $failureMessage = $_.Exception.Message
    Write-RunMessage -Level "ERROR" -Message $failureMessage
}
finally {
    if (-not [string]::IsNullOrWhiteSpace($script:SelectedDevice) -and $null -ne $targetMetadata) {
        try {
            Save-DeviceScreenshot -RunId $runId -Name "final" -LocalPath (Join-Path $resultDirectory "02-final.png")
        }
        catch {
            Write-RunMessage -Level "WARN" -Message "Finaler Screenshot nicht verfuegbar: $($_.Exception.Message)"
        }

        try {
            Collect-Diagnostics -ResultDirectory $resultDirectory -PackageName $targetMetadata.PackageName
        }
        catch {
            Write-RunMessage -Level "WARN" -Message "Diagnosedaten konnten nicht vollstaendig gesammelt werden: $($_.Exception.Message)"
        }
    }

    $finishedAt = Get-Date
    $summary = [ordered]@{
        success = $succeeded
        failure = $failureMessage
        startedAt = $startedAt.ToString("o")
        finishedAt = $finishedAt.ToString("o")
        durationSeconds = [math]::Round(($finishedAt - $startedAt).TotalSeconds, 2)
        apkPath = $resolvedApkPath
        apkSha256 = $apkHash
        packageName = if ($null -ne $targetMetadata) { $targetMetadata.PackageName } else { $null }
        apkVersionName = if ($null -ne $targetMetadata) { $targetMetadata.VersionName } else { $null }
        apkVersionCode = if ($null -ne $targetMetadata) { $targetMetadata.VersionCode } else { $null }
        keepData = [bool]$KeepData
        deviceSerial = $script:SelectedDevice
        testApkPath = $resolvedTestApkPath
        testApkSha256 = $testApkHash
        testMode = if ($null -ne $resolvedTestApkPath) { "instrumentation" } else { "launch-only" }
        automaticUiTestsExecuted = ($null -ne $instrumentationTestCount -and $instrumentationTestCount -gt 0)
        instrumentationComponent = $instrumentationComponent
        instrumentationTestCount = $instrumentationTestCount
        testDataDeviceDirectory = $script:RemoteDataRoot
        resultDirectory = $resultDirectory
    }
    $summaryJson = $summary | ConvertTo-Json -Depth 4
    [System.IO.File]::WriteAllText((Join-Path $resultDirectory "run-summary.json"), $summaryJson + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
}

if ($succeeded) {
    Write-RunMessage -Level "OK" -Message "APK-Testlauf erfolgreich. Ergebnisse: $resultDirectory"
    exit 0
}

Write-RunMessage -Level "ERROR" -Message "APK-Testlauf fehlgeschlagen. Ergebnisse: $resultDirectory"
exit 1

