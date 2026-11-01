# Teststatus für Version 0.27.0

Stand: 01.11.2026, 20:22 Uhr

Der Gerätetest wurde auf einem Nothing A001 mit Android 16 ausgeführt.
Die isolierte App `de.quartalsmappe.app.uitest` (Version 0.27.0, Code 27)
wurde sauber neu installiert. Die normale App und ihre Daten blieben unberührt.

Ergebnis: 62 Instrumentierungstests bestanden, 1 Screenshot-Fixture-Test
erwartungsgemäß übersprungen (nur mit `visual_seed=true` vorgesehen), 0 Fehler.
Der Runner meldete `OK (63 tests)`.

Lokale Prüfung: 46 Unit-Tests ohne Fehler; Android Lint ohne Fehler
(28 Warnungen). Die synthetischen Testbelege wurden validiert.

Ergebnisordner: `output/test-runs/20261101-202056-907-isolated-app/`.
Vor einem öffentlichen Release sind Release-Signierung und die geplante
Sicherung des Signierschlüssels noch zu klären.
