# Android-App zur Quartalsvorbereitung – MVP-Plan

## 1. Ziel

Die erste Version erleichtert der Inhaberin das tägliche Sammeln und Wiederfinden
der Unterlagen, die sie bislang ausdruckt und quartalsweise an ihre
Finanzberaterin übergibt.

Die App ist in dieser Phase **keine Buchhaltungs- oder Steuersoftware**. Sie
berechnet keine Steuer, entscheidet nicht über die steuerliche Zuordnung eines
Belegs und übermittelt nichts an ELSTER.

Der erste Ablauf lautet:

1. Tagesumsatz laut Z-Abschlag erfassen.
2. Z-Abschlag fotografieren oder als Datei ablegen.
3. Bestellungen/Rechnungen vom Eingang bis zur Zahlung verfolgen.
4. Kontoauszüge, Kassenexporte und jährliche Unterlagen sammeln.
5. Zum Quartalsende die Vollständigkeit prüfen und die Unterlagen geordnet
   bereitstellen.

## 2. Gesicherte Anforderungen und Annahmen

- Zielgerät ist zunächst ein Android-Handy im Hochformat.
- Es gibt vorerst nur einen Betrieb und eine Benutzerin.
- Pro Tag wird der Umsatz laut Z-Abschlag erfasst.
- Detaillierte Kassenexporte werden quartalsweise bereitgestellt.
- Das genaue Layout von Z-Abschlag und Kassenexport ist für den Prototyp noch
  nicht bekannt. Beide werden zunächst als beliebige Foto-/PDF-Dokumente
  behandelt und nicht automatisch ausgelesen.
- Rechnungen kommen bei Bestellungen zuerst per E-Mail und häufig später noch
  einmal in Papierform mit der Lieferung.
- Bestellung, Rechnung, Lieferung, Fälligkeit und Zahlung können an
  unterschiedlichen Tagen liegen.
- Eine Rechnung wird immer vollständig in einer Zahlung beglichen.
- Mehrere Lieferungen zu einer Bestellung sind so selten, dass im MVP eine
  Notiz genügt.
- Die Zahlungsart eines Einkaufs ist für die Benutzerin nicht relevant und wird
  nicht abgefragt.
- Wiederkehrende Unterlagen betreffen unter anderem Miete, Strom, Gas, Löhne,
  GEMA, Rundfunk und BGW.
- Kontoauszüge, Z-Abschläge und der detaillierte Kassenexport werden bisher
  ausgedruckt und der Finanzberaterin übergeben.
- Die Finanzberaterin bekommt aktuell keine Einkaufsrechnungen als Teil dieses
  Ablaufs; die App bewahrt sie dennoch geordnet auf.
- Direkte ELSTER- oder Kanzlei-Anbindungen sind nicht Bestandteil des MVP.

## 3. Nicht Bestandteil des MVP

- Steuerberechnung oder steuerliche Beratung
- automatische Zuordnung einer Ausgabe zu einem Steuerzeitraum
- ELSTER-Übermittlung
- Anbindung an Kanzlei-, DATEV- oder Banksysteme
- automatischer Bankabgleich
- Mehrbenutzerbetrieb und Rollen/Rechte
- Teilzahlungen
- komplexe Teillieferungen
- automatische Texterkennung als Voraussetzung für die Bedienung
- Bearbeitung von Rechnungspositionen

Diese Punkte dürfen später ergänzt werden, ohne das Datenmodell neu aufbauen zu
müssen.

## 4. Hauptbereiche der App

### 4.1 Startseite

Die Startseite beantwortet ohne Fachsprache:

- Ist der heutige Z-Abschlag erfasst?
- Welche Lieferungen stehen noch aus?
- Welche Rechnungen sind bald fällig oder überfällig?
- Welche Vorgänge sind noch unvollständig?
- Was fehlt für das laufende Quartal?

Primäre Schaltflächen:

- `Tagesabschluss eintragen`
- `Rechnung/Bestellung hinzufügen`
- `Dokument hinzufügen`
- `Quartal prüfen`

### 4.2 Tagesabschlüsse

Pflichtangaben:

- Geschäftstag
- Umsatz laut Z-Abschlag in Euro

Optionale Angaben:

- Foto/PDF des Z-Abschlags
- Notiz

Regeln:

- Pro Geschäftstag gibt es höchstens einen aktiven Tagesabschluss.
- Geldbeträge werden intern als Cent-Ganzzahlen gespeichert, nicht als
  Gleitkommazahlen.
- Ein vorhandener Eintrag kann korrigiert werden; Erstellungs- und
  Änderungszeitpunkt bleiben erhalten.
- Erinnerungen sind optional und können in den Einstellungen pro Wochentag und
  mit frei wählbarer Uhrzeit konfiguriert werden. Sie sind standardmäßig
  deaktiviert.

### 4.3 Bestellungen und Rechnungen

Felder:

- Lieferant
- Betrag
- Bestelldatum
- Rechnungsdatum
- Rechnungsnummer
- Lieferdatum
- Fälligkeitsdatum
- Zahlungsdatum
- Rechnung aus E-Mail (PDF/Bild)
- Foto der Papierunterlage oder des Lieferscheins
- Notiz

Nur Lieferant und mindestens ein Datum sind beim ersten Anlegen verpflichtend.
Weitere Angaben können später ergänzt werden.

Der Status wird aus den vorhandenen Daten abgeleitet:

- `Bestellt`
- `Rechnung erhalten`
- `Lieferung ausstehend`
- `Zahlung ausstehend`
- `Bald fällig`
- `Überfällig`
- `Erledigt`

Ein großer Button `Als bezahlt markieren` trägt standardmäßig das heutige Datum
ein. Das Datum kann geändert werden.

Bei gleicher Rechnungsnummer und gleichem Lieferanten warnt die App vor einer
möglichen Doppelerfassung. E-Mail- und Papierrechnung können als zwei Dokumente
am selben Vorgang hängen.

### 4.4 Weitere Unterlagen

Dokumentkategorien im MVP:

- Kontoauszug
- detaillierter Kassenexport
- Miete
- Strom
- Gas
- Lohnunterlagen
- GEMA
- Rundfunk
- BGW
- Sonstiges

Zu jedem Dokument werden Kategorie, Zeitraum/Jahr, Originaldateiname,
Importzeitpunkt und optional eine Notiz gespeichert.

### 4.5 Quartalsübersicht

Auswahl eines Kalenderquartals mit folgenden Prüfungen:

- erfasste Tagesabschlüsse im gewählten Zeitraum
- vorhandene Z-Abschläge
- vorhandene Kontoauszüge
- vorhandener detaillierter Kassenexport
- in diesem Quartal angelegte/erhaltene Rechnungen
- in diesem Quartal bezahlte Rechnungen
- am Quartalsende offene Rechnungen
- vorhandene jährliche Unterlagen

Die App bewertet nur die technische Vollständigkeit. Sie zeigt keine Aussage
wie `steuerlich korrekt` an.

Der erste Export erzeugt ein geordnetes Paket, zum Beispiel:

```text
2026_Q3/
  Uebersicht.pdf
  Tagesumsaetze.csv
  Z-Abschlaege/
  Kontoauszuege/
  Kassenexporte/
  Rechnungen/
  Weitere_Unterlagen/
```

Die Benutzerin entscheidet beim Export, ob der Rechnungsordner enthalten sein
soll. So lässt sich zunächst der bisherige Übergabeweg nachbilden.

## 5. Bedienungsprinzipien

- Große Schaltflächen und gut lesbare Schrift
- Pro Bildschirm nur eine Hauptaufgabe
- Keine buchhalterischen Fachbegriffe, wenn Alltagssprache genügt
- Datumsfelder dürfen leer bleiben und später ergänzt werden
- Dokumentaufnahme mit Kamera und Import vorhandener PDF-/Bilddateien
- Importierte Dateien werden in den privaten App-Speicher kopiert, damit sie
  nicht verschwinden, wenn die ursprüngliche Datei verschoben wird
- Keine Werbung, kein Tracking und keine unnötigen Berechtigungen
- Fehler oder fehlende Angaben werden als konkrete nächste Handlung angezeigt

## 6. Technische Architektur

Empfohlener Stack:

- Kotlin
- Jetpack Compose für die Oberfläche
- Material Design 3 mit einer eigenen, ruhigen Farbgestaltung
- Room für strukturierte lokale Daten
- app-privater Dateispeicher für Bilder und PDFs
- Android Photo Picker/Dateiauswahl sowie Kameraaufnahme
- WorkManager für verlässliche lokale Erinnerungen
- Storage Access Framework für den Quartalsexport

Architekturschichten:

```text
Compose-Oberfläche
       |
ViewModels / Anwendungslogik
       |
Repositories
       |--------------------|
Room-Datenbank        Dokumentenspeicher
```

Der Zugriff auf Daten, Dokumente, Export und spätere Sicherung erfolgt jeweils
über getrennte Schnittstellen. Dadurch kann später eine Cloud-Sicherung oder
Kanzlei-Anbindung ergänzt werden, ohne die Bedienoberfläche neu zu schreiben.

## 7. Datenmodell

### DailyClose

- `id`
- `businessDate`
- `zAmountCents`
- `note`
- `createdAt`
- `updatedAt`
- `archivedAt`

### InvoiceCase

- `id`
- `supplier`
- `amountCents`
- `orderDate`
- `invoiceDate`
- `invoiceNumber`
- `deliveryDate`
- `dueDate`
- `paidDate`
- `note`
- `createdAt`
- `updatedAt`
- `archivedAt`

### Document

- `id`
- `documentType`
- `linkedEntityType`
- `linkedEntityId`
- `periodYear`
- `periodQuarter`
- `originalFileName`
- `storedFileName`
- `mimeType`
- `sha256`
- `createdAt`
- `note`

Das Dateiformat und der Inhalt eines Kassenexports werden im MVP nicht
interpretiert. Die App ordnet die Originaldatei lediglich dem gewählten Quartal
zu und nimmt sie in das Exportpaket auf.

### ReminderSettings

- geöffnete Wochentage
- Uhrzeit für Tagesabschluss-Erinnerung
- Anzahl Tage vor Rechnungsfälligkeit
- Erinnerungen aktiviert/deaktiviert

Ein eigener gespeicherter Quartalsdatensatz ist zunächst nicht notwendig; die
Übersicht wird aus den vorhandenen Daten berechnet. Exportvorgänge können zur
Nachvollziehbarkeit protokolliert werden.

## 8. Datensicherheit

Im aktuellen Prototyp:

- Arbeitsdaten liegen im privaten Speicher der App.
- Dokumente werden beim Import kopiert und mit einer Prüfsumme versehen.
- Die Oberfläche bietet keine Löschfunktion.
- Änderungen an bestehenden Tagesabschlüssen und Rechnungsvorgängen benötigen
  eine ausdrückliche Bestätigung; die vorherige Fassung wird lokal als Revision
  aufbewahrt.
- Ein gewählter Android-Ordner erhält nach gespeicherten Änderungen neue,
  versionierte ZIP-Sicherungen. Vorhandene Sicherungen werden nicht ersetzt.
- Die Wiederherstellung ergänzt ausschließlich neue Einträge und überschreibt
  keine vorhandenen Daten.
- Quartalsexporte enthalten sensible Daten und werden entsprechend deutlich
  gekennzeichnet.
- Es werden für Entwicklung und Demonstration nur Testdaten oder geschwärzte
  Beispieldokumente verwendet.

Vor produktiver Nutzung zwingend zu testen bzw. festzulegen:

- vollständiger Wiederherstellungstest auf einem anderen Gerät
- dauerhafte Erreichbarkeit des gewählten Sicherungsordners bzw. Google Drive
- Entscheidung über App-Sperre per Geräteauthentifizierung
- Aufbewahrungs- und Löschkonzept
- Datenschutzinformation

Eine reine Speicherung auf dem Handy erfüllt den Anspruch `kein Datenverlust`
nicht. Deshalb sollte vor dem Pilotbetrieb ein externer Sicherungsordner
eingerichtet und das Einlesen einer Sicherung einmal vollständig erprobt werden.

## 9. Umsetzung in Etappen

### Etappe 1 – Projektgrundlage

- Android-Projekt anlegen
- Navigation und Design-Grundlagen
- Room-Datenbank und Repository-Struktur
- Testdatenmodus

### Etappe 2 – Kernabläufe

- Startseite
- Tagesabschluss anlegen/bearbeiten
- Rechnungsfall anlegen/bearbeiten
- Listen, Filter und automatisch berechnete Statuswerte

### Etappe 3 – Dokumente

- Kameraaufnahme
- PDF-/Bildimport
- Dokumentvorschau
- Verknüpfung mehrerer Dokumente mit einem Rechnungsfall
- Duplikatwarnung

### Etappe 4 – Erinnerungen und Quartal

- Erinnerungen für Tagesabschluss und Fälligkeit
- Quartalscheckliste
- CSV- und Dokumentexport
- druckbare Quartalsübersicht

### Etappe 5 – Stabilisierung

- Tests für Datums-/Quartalsgrenzen und Statusberechnung
- Tests mit fehlenden und doppelten Dokumenten
- Bedienungstest auf kleinem Android-Handy
- Sicherung und Wiederherstellung auf dem Zielgerät praktisch testen
- Pilotbetrieb mit Testdaten, danach mit einem echten Quartal

## 10. Abnahmekriterien für den ersten nutzbaren Prototyp

Der Prototyp ist vorzeigbar, wenn die Benutzerin ohne Erklärung:

1. einen Tagesumsatz in höchstens drei Schritten speichern kann,
2. einen Z-Abschlag fotografieren und wiederfinden kann,
3. eine Rechnung zunächst unvollständig anlegen und später Liefer- und
   Zahlungsdatum ergänzen kann,
4. erkennen kann, welche Rechnung bald fällig oder überfällig ist,
5. einen Kontoauszug und einen Kassenexport einem Quartal zuordnen kann,
6. in der Quartalsübersicht erkennt, welche Unterlagen noch fehlen,
7. ein geordnetes Exportpaket erzeugen kann,
8. nach einem App-Neustart alle erfassten Daten und Dokumente wiederfindet.

## 11. Offene Fragen, die den Start nicht blockieren

Für den Prototyp werden zunächst sinnvolle Standardwerte verwendet. Später sind
noch zu klären:

- Android-Version und Bildschirmgröße des tatsächlichen Handys
- ob ein Z-Abschlag-Foto verpflichtend sein soll
- ob Bestellungen auch ohne bereits bekannten Betrag angelegt werden
- ob die App per Fingerabdruck/PIN zusätzlich gesperrt werden soll
- welche Dokumente genau auf der Quartalscheckliste verpflichtend sind
- ob Dateien aus E-Mails über `Teilen mit ...` direkt in die App übernommen
  werden sollen
- gewünschter Sicherungsort und späterer digitaler Übergabeweg

Ein echtes, bei Bedarf geschwärztes Beispiel eines Z-Abschlags und eines
Kassenexports wird erst für den späteren Bedienungs- und Exporttest benötigt.
Die Entwicklung der manuellen Erfassung hängt nicht davon ab.

## 12. Empfohlener nächster Schritt

Zuerst wird ein lokaler Android-Prototyp mit Testdaten für drei Abläufe gebaut:

1. Tagesabschluss erfassen
2. Rechnung vom Eingang bis zur Zahlung verfolgen
3. Quartalsvollständigkeit prüfen

Dokumentimport und Export werden direkt danach ergänzt. Dieser Zuschnitt liefert
früh verwertbares Feedback, ohne steuerliche oder externe Schnittstellen
vorwegzunehmen.
