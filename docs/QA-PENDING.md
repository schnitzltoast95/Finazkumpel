# Teststatus für Version 0.28.0

Stand: 10.11.2026, 22:58 Uhr

Auf einem Nothing A001 mit Android 16 wurden 64 Instrumentierungstests der
isolierten App `de.quartalsmappe.app.uitest` ausgeführt. Der optionale
Screenshot-Fixture-Test wurde dabei erwartungsgemäß übersprungen und danach
separat erfolgreich gestartet. Die synthetischen Daten sind für den manuellen
Test im isolierten App-Paket vorhanden. Die normale App wurde nicht gelöscht.
Der vollständige Testlauf fand vor dem abschließenden Drive-Fix statt
(Test-APK mit Versionscode 27). Den Drive-Fix und die Version 0.28.0 habe ich
anschließend direkt auf dem Gerät geprüft, ohne die Testdaten erneut zu leeren.

Die Unit-Tests und Android Lint waren erfolgreich. Die signierte Release-APK
0.28.0 wurde gebaut, auf dem Gerät gestartet und mit `apksigner` gegen den
dokumentierten Zertifikat-Fingerprint geprüft. Über Google Drive wurden in der
signierten App 2 synthetische Tagesabschlüsse, 2 Vorgänge und 5 Dokumente
erfolgreich eingelesen. Diese Daten bleiben für den manuellen Test erhalten.

Google Drive wurde mit einem eigenen Unterordner unter `Test` geprüft:
`Finanzkumpel-Test-20261110`. Ein gleichzeitiger Erstabgleich erzeugte zunächst
zwei gleichnamige ZIP-Dateien. Der Konflikt wurde im Code behoben; ein erneuter
Abgleich ließ genau eine `Quartalsmappe_Aktuell.zip` zurück. Nach einer
synthetischen Änderung blieb der Status bis zum manuellen Abgleich ausstehend.
Der tägliche Hintergrundlauf kann erst nach Ablauf des Intervalls
beobachtet werden.

Gerätetest: `output/test-runs/20261110-224125-574-app-debug/`.
