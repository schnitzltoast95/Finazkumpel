# Eine konkrete APK automatisch testen

Der PowerShell-Runner installiert genau die angegebene APK auf einem Android-Emulator oder Testgeraet, kopiert die synthetischen Belege in den Download-Ordner, startet die App und sammelt einen reproduzierbaren Ergebnisordner.

## Empfohlener Ablauf bei jeder neuen Iteration

Wenn die APK aus diesem Projekt gebaut wird, reicht ein Befehl fuer den gesamten
Quellcode-, Beleg-, Build- und Geraetetest:

```powershell
.\tools\test-project.ps1 -WithDevice
```

Ohne laufenden Emulator oder Testgeraet prueft derselbe Runner alles, was lokal
moeglich ist, und baut beide APKs:

```powershell
.\tools\test-project.ps1
```

Bereits abgedeckt sind:

- 14 schnelle JVM-Tests fuer Betraege, Quartale, Status, Faelligkeit, Duplikate und feste Testdaten
- 5 Compose-UI-Smoke-Tests fuer Startzustand, Tagesabschluss, Validierung, Bestellung und Quartalsnavigation
- 3 Geraete-Integrationspruefungen fuer Persistenz, echten PDF-Import samt SHA-256 und das erzeugte Quartals-ZIP
- Android Lint sowie reproduzierbarer Bau von App- und Test-APK
- Installation, Paket-/Versions-/Hashpruefung, Start-Gesundheit, Screenshots und paketbezogene Logs

Der System-Dateiauswahldialog wird bewusst noch nicht ferngesteuert. Die 27
Testdateien werden pro Lauf auf das Geraet kopiert; die Integrationspruefungen
importieren zwei repraesentative PDFs direkt und deterministisch in den App-Speicher.

## Voraussetzungen

- Android SDK Platform-Tools (`adb.exe`); der Runner sucht im `PATH`, in `ANDROID_SDK_ROOT`, `ANDROID_HOME` und im ueblichen lokalen Android-SDK-Ordner.
- Ein gestarteter Emulator oder ein per USB-Debugging autorisiertes Testgeraet.
- Android SDK Build-Tools (`aapt.exe`) zur sicheren Ermittlung der APK-Paket-ID. Ohne diese Ermittlung bricht der Runner ab, bevor er eine installierte App entfernt.

## Aufruf

Nur Installation und Start pruefen:

```powershell
.\tools\test-apk.ps1 -ApkPath .\app\build\outputs\apk\debug\app-debug.apk
```

Zusaetzlich eine passende `androidTest`-APK ausfuehren:

```powershell
.\tools\test-apk.ps1 `
  -ApkPath .\app\build\outputs\apk\debug\app-debug.apk `
  -TestApkPath .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
```

Die beiden Debug-APKs koennen gemeinsam gebaut werden:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
```

Wichtig: Die Test-APK muss fuer dieselbe Application-ID bzw. Build-Variante gebaut und signaturkompatibel mit der Ziel-APK sein. Eine lokal debug-signierte Test-APK kann eine fremd oder als Release signierte APK normalerweise nicht instrumentieren. Der Runner bricht dann mit der Installations- oder Instrumentation-Ausgabe im Ergebnisordner ab.

Bei mehreren angeschlossenen Geraeten die Seriennummer aus `adb devices` angeben:

```powershell
.\tools\test-apk.ps1 -ApkPath .\app\build\outputs\apk\debug\app-debug.apk -DeviceSerial emulator-5554
```

Der Runner wartet standardmaessig 120 Sekunden. Fuer einen kurzen Verbindungscheck kann die Frist angepasst werden, zum Beispiel mit `-DeviceTimeoutSeconds 10`.

Standardmaessig wird nur das Paket der Test-App vor der Installation deinstalliert, damit jeder Lauf mit leeren App-Daten beginnt. Mit `-KeepData` wird stattdessen ein Update installiert:

```powershell
.\tools\test-apk.ps1 -ApkPath .\app\build\outputs\apk\debug\app-debug.apk -KeepData
```

## Ergebnisse

Jeder Lauf legt unter `output/test-runs/<Zeitstempel>-<APK-Name>/` unter anderem Folgendes ab:

- `run-summary.json` und `run.log`
- SHA-256, Paket- und Versionsinformationen
- Installations-, Start- und gegebenenfalls Instrumentation-Ausgabe
- Screenshots nach dem Start und am Laufende
- auf den Zielprozess und den Laufzeitraum begrenztes Logcat sowie Geraete-, Paket- und Vordergrunddiagnosen
- `pushed-files.txt` mit allen auf das Geraet kopierten Testdateien

Ohne `-TestApkPath` ist der Modus bewusst nur `launch-only`: Installation, Start, laufender Prozess und Artefaktsammlung werden geprueft, aber keine UI-Ablaeufe ausgefuehrt. `run-summary.json` weist den Modus und `automaticUiTestsExecuted` deshalb explizit aus.

Die Testdaten liegen pro Lauf getrennt unter `Download/Quartalsmappe-Testdaten/<Zeitstempel>`. Das vermeidet Vermischungen, ohne aeltere Dateien zu loeschen. Die Dateien sind fuer manuelle Importe und kuenftige UI-Automator-Dateipicker-Tests vorbereitet; das Kopieren allein importiert sie noch nicht in die App.
