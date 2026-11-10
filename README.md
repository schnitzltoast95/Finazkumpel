# Svetlanas Finanzkumpel

Ein bewusst einfacher Android-Prototyp zum täglichen Sammeln von Z-Abschlägen,
Rechnungen und Quartalsunterlagen.

## Was bereits funktioniert

- Tagesumsatz laut Z-Abschlag erfassen
- Z-Abschlag fotografieren oder als PDF/Bild importieren
- Bestellungen und Rechnungen schrittweise ergänzen
- bereits verwendete Lieferanten beim nächsten Eintrag wieder vorschlagen
- Bestell-, Rechnungs-, Liefer-, Fälligkeits- und Zahlungsdatum getrennt führen
- offene, bald fällige und überfällige Vorgänge anzeigen
- Papierrechnung und E-Mail-Rechnung demselben Vorgang zuordnen
- Kontoauszug, Kassenexport und Jahresunterlagen ablegen
- lokale Tagesabschluss-Erinnerungen konfigurieren
- Quartalsübersicht mit getrennten Rechnungs- und Zahlungszählungen
- ZIP-Quartalspaket mit PDF-Übersicht, CSV-Tagesumsätzen und Originaldateien
- helle, ruhige Studio-Farbwelt in Grün, Limette und warmem Korall
- auswählbarer Begleiter: Eule (mit ruhigem/zerzaustem Aufgaben-Zustand), Biene, Stachelschwein, Schaf oder Nacktkatze
- zehn auswählbare Farbschemen mit Hell-/Dunkelmodus; Farbe und Tier werden sofort lokal gespeichert, unabhängig von Belegen und Sicherungen
- neutrales App-Icon mit Dokument und Haken, unabhängig vom gewählten Begleiter
- ausdrückliche Bestätigung vor jeder Änderung an bestehenden Einträgen
- lokale, unveränderliche Vorversion bei jeder bestätigten Änderung
- automatische ZIP-Sicherung in einen frei gewählten Android-Ordner; auf Wunsch separate Sicherungsstände
- Sicherungsordner kann über die Android-Ordnerauswahl auch in Google Drive liegen
- konfliktfreie Wiederherstellung: Bestehende Einträge werden niemals überschrieben
- lokale Speicherung ohne Benutzerkonto, Tracking oder externe Dienste

Die App berechnet keine Steuer und sendet keine Daten an ELSTER.

## Projekt öffnen und starten

1. Diesen Ordner in Android Studio öffnen.
2. Gradle-Synchronisierung abwarten.
3. Ein Android-Gerät oder einen Emulator ab API 26 auswählen.
4. Die Run-Konfiguration `app` starten.

Debug-APK erstellen:

```powershell
.\gradlew.bat :app:assembleDebug
```

Unit-Tests und Lint:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

Kompletter automatisierter Projektcheck einschließlich Testdatenprüfung und Bau
der Instrumentierungs-APK:

```powershell
.\tools\test-project.ps1
```

Mit einem laufenden Emulator oder per USB-Debugging verbundenen Testgerät werden
zusätzlich die automatisierten UI- und Integrationsprüfungen ausgeführt:

```powershell
.\tools\test-project.ps1 -WithDevice
```

Eine beliebige bereits gebaute APK kann separat mit `tools/test-apk.ps1`
installiert, gestartet und dokumentiert werden. Alle Varianten und Grenzen sind
in [`docs/APK_TESTING.md`](docs/APK_TESTING.md) beschrieben.

Das erzeugte APK liegt unter
`app/build/outputs/apk/debug/app-debug.apk`.

Der Quellstand ist 0.27.0. Eine signierte Release-APK wird separat gebaut;
die privaten Signierdaten gehören nicht in dieses Repository.
Die Schritte für einen signierten Build stehen in
[`docs/RELEASE.md`](docs/RELEASE.md).
Die geprüfte signierte APK 0.27.0 liegt unter
[`releases/Finanzkumpel-0.27.0.apk`](releases/Finanzkumpel-0.27.0.apk).

## Wichtiger Prototyp-Hinweis

Die Arbeitsdaten liegen im privaten App-Speicher des Handys. Unter
„Einstellungen > Datensicherung“ sollte vor dem Einsatz mit echten
Geschäftsdaten zusätzlich ein Ordner auf dem Gerät oder bei einem über Android
eingebundenen Cloud-Anbieter gewählt werden. Die App aktualisiert dort nach
gespeicherten Änderungen im einstellbaren Abstand (täglich, alle drei oder
sieben Tage) `Quartalsmappe_Aktuell.zip`. Ohne Änderungen wird keine neue Datei
angelegt. Android kann den Zeitpunkt im Hintergrund verschieben. Unter Einstellungen kann
zusätzlich ein separater, datierter Sicherungsstand angelegt werden. Die ZIPs
enthalten die aktuellen Daten, Originaldateien und gespeicherte Vorversionen.

Die Wiederherstellung ist absichtlich vorsichtig: Sie ergänzt nur neue
Datensätze. Bereits vorhandene IDs, Geschäftstage und Dateien werden als
Konflikt übersprungen und nicht ersetzt. In der App gibt es derzeit keine
Löschfunktion.

Die detaillierte fachliche und technische Planung steht in
[`MVP_PLAN.md`](MVP_PLAN.md).

