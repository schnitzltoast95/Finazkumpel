# Testdaten-Leitfaden – Quartalsmappe

Stand: 13. Oktober 2026  
Referenzbetrieb: Waxing STUDIO Würzburg  
Status: Synthetisches Testpaket erzeugt und automatisch geprüft

## 1. Zweck

Dieser Leitfaden beschreibt einen wiederholbaren manuellen Abnahmetest für den
Android-Prototypen „Quartalsmappe“. Er legt fest:

- welche Daten in die App eingegeben werden,
- welche synthetischen Dateien später einzeln erzeugt werden,
- welches Ergebnis nach jedem Schritt erwartet wird,
- welche zeitabhängigen Fälle vor einer neuen Testrunde aktualisiert werden müssen.

Die Testunterlagen sind keine echten Geschäftsunterlagen. Jede später erzeugte
Datei wird sichtbar mit **„TESTDOKUMENT – KEIN ECHTER BELEG“** gekennzeichnet.
Lieferanten, Rechnungsnummern, Steuerangaben, Bankverbindungen, Personal- und
Kundendaten sind frei erfunden und dürfen nicht für echte Vorgänge verwendet
werden.

## 2. Referenzbetrieb und fachlicher Rahmen

Als Empfänger bzw. Betreiber wird der öffentlich angegebene Betrieb verwendet:

> Waxing STUDIO Würzburg  
> Semmelstraße 59  
> 97070 Würzburg

Öffnungszeiten laut Website:

- Montag, Mittwoch, Donnerstag und Freitag: 10:00–18:00 Uhr
- Samstag: 11:00–16:00 Uhr
- Dienstag und Sonntag: Ruhetag
- weitere Termine nach persönlicher Vereinbarung

Der Testdatensatz verwendet Beispiele aus der veröffentlichten Preisliste,
unter anderem:

| Beispiel-Leistung | Testpreis inkl. MwSt. |
|---|---:|
| Damen: Achseln | 14,00 € |
| Damen: Arme komplett | 28,00 € |
| Damen: Beine komplett | 39,00 € |
| Damen: Gesicht komplett | 47,00 € |
| Damen: Augenbrauen färben | 11,00 € |
| Herren: Achseln | 15,00 € |
| Herren: Arme komplett | 36,00 € |
| Herren: Beine komplett | 46,00 € |
| Herren: Rücken komplett | 35,00 € |
| Herren: Rücken inklusive Schultern/Nacken | 46,00 € |

Diese Preise dienen nur dazu, plausible Tagesumsätze zusammenzustellen. Die App
erfasst selbst keine einzelnen Behandlungen und soll die Summen nicht
nachrechnen.

## 3. Vorbereitung einer Testrunde

1. Für einen vollständig reproduzierbaren Lauf mit leerem App-Datenspeicher
   beginnen. Bei einem Entwicklungsgerät kann dafür die App neu installiert
   oder ihr lokaler Speicher über die Android-Einstellungen geleert werden.
2. Gerätesprache auf Deutsch und Zeitzone auf Europa/Berlin stellen.
3. Als Referenzdatum für diesen Testlauf den 13.10.2026 verwenden.
4. In den Erinnerungen Montag, Mittwoch, Donnerstag, Freitag und Samstag
   aktivieren; Dienstag und Sonntag deaktivieren.
5. Fälligkeitshinweis auf fünf Tage einstellen.

Hinweis: Der 13.10.2026 ist ein Dienstag und damit laut Website ein Ruhetag.
Dass die Startseite trotzdem pauschal einen heutigen Tagesabschluss vermissen
kann, ist ein eigener Beobachtungspunkt und kein Grund, einen falschen
Tagesabschluss anzulegen.

## 4. Testfälle ohne benötigte Datei

### A01 – Leerer Startzustand

**Aktion:** App erstmals öffnen.  
**Erwartet:** Keine Rechnungen und Unterlagen; Kassenexport fehlt; für den
aktuellen Kalendertag zeigt die App den Tagesabschluss-Status.

### A02 – Pflichtfelder beim Tagesabschluss

**Aktion:** Nacheinander `abc`, `-1,00` und ein leeres Betragsfeld speichern.  
**Erwartet:** Der Eintrag wird jeweils nicht gespeichert und eine verständliche
Fehlermeldung erscheint.

### A03 – Deutsche Betragsformate

**Aktion:** `1.234,56 €` als Betrag eingeben.  
**Erwartet:** Nach dem Speichern wird `1.234,56 €` angezeigt.

### A04 – Unvollständige Bestellung

Datei: `F05_Bestellbestaetigung_WaxPro_TEST.pdf`

**Eingaben:**

- Lieferant: `WaxPro Bedarf GmbH (TEST)`
- Bestelldatum: 08.10.2026
- alle übrigen Datumsfelder leer
- Betrag leer
- Notiz: `Testfall: telefonisch bestelltes Warmwachs; Angaben folgen später.`

**Erwartet:** Der Vorgang kann gespeichert werden, Status `Bestellt`; Rechnung
und Lieferung werden als fehlend angezeigt.

### A05 – Eingabevalidierung einer Rechnung

**Aktionen:**

1. Ohne Lieferant speichern.
2. Mit Lieferant, aber ohne irgendein Datum speichern.
3. Betrag `0,00`, `-20,00` und `zwanzig` versuchen.

**Erwartet:** Jeder ungültige Versuch wird mit passender Meldung abgewiesen.

### A06 – Logisch widersprüchliche Daten

**Aktion:** Zahlungsdatum vor Rechnungsdatum bzw. Lieferdatum vor Bestelldatum
eingeben.  
**Erwartete Beobachtung im aktuellen Prototyp:** Prüfen, ob die App den Vorgang
akzeptiert. Eine fehlende Plausibilitätsprüfung wird als Produktlücke notiert.

## 5. Tagesabschlüsse

### Z01 – Normaler Montag mit PDF

Datei: `F01_Z-Abschlag_2026-10-12_TEST.pdf`

- Geschäftstag: 12.10.2026
- Umsatz: 742,00 €
- Notiz: `Normaler Montag; PDF aus Kassensystem.`
- plausible Zusammensetzung: 22 synthetische Behandlungen auf Basis der
  veröffentlichten Preise

**Erwartet:** Tagesabschluss wird mit Dokument gespeichert und in der Historie
als `Z-Abschlag hinterlegt` angezeigt.

### Z02 – Samstag als Foto

Datei: `F02_Z-Abschlag_2026-10-10_Foto_TEST.jpg`

- Geschäftstag: 10.10.2026
- Umsatz: 516,00 €
- Notiz: `Samstag; Foto eines Papierausdrucks.`

**Erwartet:** Das Bild wird kopiert, lässt sich erneut öffnen und wird dem
richtigen Tag zugeordnet.

### Z03 – Tagesabschluss ohne Dokument

- Geschäftstag: 09.10.2026
- Umsatz: 681,00 €
- Notiz: `Dokument absichtlich noch nicht vorhanden.`

**Erwartet:** Speicherung ist erlaubt; Historie zeigt `Ohne Dokument`; die
Quartalsübersicht zeigt weniger Z-Dokumente als Tagesabschlüsse.

### Z04 – Korrektur statt Doppelanlage

**Aktion:** Den 09.10.2026 erneut mit 686,00 € speichern.  
**Erwartet:** Es bleibt genau ein Tagesabschluss für dieses Datum vorhanden und
der Betrag lautet anschließend 686,00 €.

### Z05 – Quartalsgrenze

Datei: `F03_Z-Abschlag_2026-09-30_Q3-Grenze_TEST.pdf`

- Geschäftstag: 30.09.2026
- Umsatz: 804,00 €

**Erwartet:** Der Eintrag zählt zu Q3/2026, nicht zu Q4/2026.

### Z06 – Schlechter Fotoscan

Datei: `F04_Z-Abschlag_schief-und-blass_TEST.jpg`

**Aktion:** Als zusätzliches Dokument zu einem neuen Tagesabschluss auf einem
Öffnungstag verwenden.  
**Erwartet:** Import und erneutes Öffnen funktionieren trotz gedrehter,
kontrastarmer Darstellung. Eine Texterkennung wird nicht erwartet.

## 6. Rechnungsvorgänge und Status

Die Statusfälle sind auf den Referenztag 13.10.2026 abgestimmt. Bei einem
späteren Testlauf müssen Fälligkeitsdaten und gegebenenfalls die zugehörigen
PDFs relativ zum neuen Testtag verschoben werden.

### R01 – Überfällige Verbrauchsmaterialien

Datei: `F06_Rechnung_Verbrauchsmaterial_ueberfaellig_TEST.pdf`

- Lieferant: `MainWachs Handelsgesellschaft mbH (TEST)`
- Betrag: 642,81 €
- Bestelldatum: 14.09.2026
- Rechnungsdatum: 16.09.2026
- Rechnungsnummer: `MW-2026-0916-TEST`
- Lieferdatum: 18.09.2026
- Fälligkeitsdatum: 10.10.2026
- Zahlungsdatum: leer

**Erwartet:** Status `Überfällig`; der Fall erscheint auf der Startseite und im
Filter `Offen`.

### R02 – Heute fällige Reinigung

Datei: `F07_Rechnung_Reinigung_faellig-heute_TEST.pdf`

- Lieferant: `Blank & Glanz Gebäudeservice (TEST)`
- Betrag: 238,00 €
- Rechnungsdatum: 30.09.2026
- Liefer-/Leistungsdatum: 30.09.2026
- Rechnungsnummer: `BG-2026-1007-TEST`
- Fälligkeitsdatum: 13.10.2026
- Zahlungsdatum: leer

**Erwartet:** Status `Bald fällig`, ausdrücklich nicht `Überfällig`.

### R03 – Fünf-Tage-Grenze und mehrere Dokumente

Dateien:

- `F08_Rechnung_Hygienebedarf_bald-faellig_TEST.pdf`
- `F09_Papierrechnung_Hygienebedarf_SCAN_TEST.jpg`
- `F10_Lieferschein_Hygienebedarf_TEST.pdf`

Eingaben:

- Lieferant: `Hygiene Süd GmbH (TEST)`
- Betrag: 314,16 €
- Bestelldatum: 05.10.2026
- Rechnungsdatum: 07.10.2026
- Rechnungsnummer: `HS-4711-2026-TEST`
- Lieferdatum: 09.10.2026
- Fälligkeitsdatum: 18.10.2026
- Zahlungsdatum: leer

**Erwartet:** Status `Bald fällig`; E-Mail-Rechnung, späterer Papierscan und
Lieferschein bleiben am selben Vorgang auffindbar.

**Zu beobachten:** Der aktuelle Prototyp speichert einen über den Detailbildschirm
hinzugefügten Lieferschein technisch ebenfalls als `Papierrechnung`.

### R04 – Offen, aber noch nicht bald fällig

Datei: `F11_Rechnung_Buerobedarf_offen_TEST.pdf`

- Lieferant: `Büro Kontor Franken (TEST)`
- Betrag: 119,84 €
- Rechnungsdatum: 08.10.2026
- Lieferdatum: 09.10.2026
- Rechnungsnummer: `BKF-81826-TEST`
- Fälligkeitsdatum: 30.10.2026

**Erwartet:** Status `Zahlung ausstehend`; nicht als überfällig zählen.

### R05 – Erledigter Wartungsfall

Datei: `F12_Rechnung_Kassenwartung_bezahlt_TEST.pdf`

- Lieferant: `Kassenservice Mainfranken (TEST)`
- Betrag: 178,50 €
- Rechnungsdatum: 01.10.2026
- Liefer-/Leistungsdatum: 01.10.2026
- Rechnungsnummer: `KSM-2026-1001-TEST`
- Fälligkeitsdatum: 15.10.2026
- Zahlungsdatum: 06.10.2026

**Erwartet:** Status `Erledigt`; erscheint im Filter `Erledigt`, nicht unter
offenen Vorgängen.

### R06 – Rechnung in Q3, Zahlung in Q4

Datei: `F13_Rechnung_Q3_Zahlung-Q4_TEST.pdf`

- Lieferant: `StudioTextil Service KG (TEST)`
- Betrag: 428,40 €
- Rechnungsdatum: 30.09.2026
- Lieferdatum: 30.09.2026
- Rechnungsnummer: `STS-0930-26-TEST`
- Fälligkeitsdatum: 14.10.2026
- Zahlungsdatum: 02.10.2026

**Erwartet:** In Q3 zählt eine erhaltene Rechnung und am Quartalsende eine noch
offene Rechnung; in Q4 zählt die Zahlung.

### R07 – Duplikatwarnung

Datei: `F14_Rechnung_Hygienebedarf_DUPLIKATNAME_TEST.pdf`

**Aktion:** Einen zweiten Vorgang mit Lieferant `Hygiene Süd GmbH (TEST)` und
Rechnungsnummer `HS-4711-2026-TEST` beginnen.  
**Erwartet:** Warnung vor möglicher Doppelerfassung. Zusätzlich wird geprüft, ob
die inhaltlich identische Datei unter einem anderen Namen ohne Hinweis erneut
importiert werden kann.

## 7. Allgemeine Quartals- und Jahresunterlagen

| ID | Datei | Kategorie und Zuordnung | Prüferwartung |
|---|---|---|---|
| U01 | `F15_Kontoauszug_2026_Q4_TEST.pdf` | Kontoauszug, 2026 Q4 | Mehrseitige PDF öffnet; Quartalsstatus `Da` |
| U02 | `F16_Kassenexport_Uebersicht_2026_Q4_TEST.pdf` | Kassenexport, 2026 Q4 | Aktuell unterstützter PDF-Fall; Quartalsstatus `Da` |
| U03 | `F17_Miete_Oktober-2026_TEST.pdf` | Miete, 2026 Q4 | Richtige Kategorie und Periode |
| U04 | `F18_Strom_2026_Q4_TEST.pdf` | Strom, 2026 Q4 | Richtige Kategorie und Periode |
| U05 | `F19_Gas_2026_Q4_TEST.pdf` | Gas, 2026 Q4 | Richtige Kategorie und Periode |
| U06 | `F20_Lohnunterlagen_Oktober-2026_TEST.pdf` | Lohnunterlagen, 2026 Q4 | Keine echten Personendaten |
| U07 | `F21_GEMA_Jahresbeitrag-2026_TEST.pdf` | GEMA, Jahr 2026 | Keine Quartalszuordnung; zählt als Jahresunterlage |
| U08 | `F22_Rundfunkbeitrag_2026_TEST.pdf` | Rundfunk, Jahr 2026 | Keine Quartalszuordnung |
| U09 | `F23_BGW_Beitragsbescheid-2026_TEST.pdf` | BGW, Jahr 2026 | Keine Quartalszuordnung |
| U10 | `F24_Betriebshaftpflicht_2026_TEST.pdf` | Sonstiges, 2026 Q4 | Auffindbar unter allgemeinen Unterlagen |

## 8. Realitätscheck Kassenexport

Datei: `F25_DSFinV-K_Kassenexport_2026_Q4_TEST.zip`

Ein echter Datenexport eines Kassensystems besteht typischerweise nicht nur aus
einer lesbaren PDF-Zusammenfassung, sondern kann als strukturierter Export mit
mehreren Dateien vorliegen. Dieser Negativtest soll prüfen, ob ein ZIP- bzw.
CSV-basierter Export ausgewählt werden kann.

**Erwartete Beobachtung im aktuellen Prototyp:** Die Dateiauswahl bietet nur PDF
und Bilder an. Das ZIP kann daher voraussichtlich nicht ausgewählt werden,
obwohl der MVP-Plan beliebige Kassenexportdateien vorsieht.

## 9. Quartalsprüfung und Export

### Q01 – Exportwarnung bei fehlenden Pflichtunterlagen

**Aktion:** Q4/2026 exportieren, bevor U01 und U02 importiert sind.  
**Erwartet:** Warnung, dass Kontoauszug oder Kassenexport fehlen; Abbruch und
bewusstes Fortfahren sind möglich.

### Q02 – Vollständigkeitsanzeige

**Aktion:** U01, U02 und mindestens eine Jahresunterlage importieren.  
**Erwartet:** Kontoauszug, Kassenexport und Jahresunterlagen stehen auf `Da`;
Tagesabschluss- und Z-Dokument-Zähler sind nachvollziehbar.

### Q03 – Export ohne Rechnungsdateien

**Aktion:** Schalter `Rechnungsdateien mitnehmen` ausschalten und exportieren.  
**Erwarteter ZIP-Inhalt:**

```text
Uebersicht.pdf
Tagesumsaetze.csv
Z-Abschlaege/
Kontoauszuege/
Kassenexporte/
Weitere_Unterlagen/
```

Rechnungsdateien dürfen nicht enthalten sein.

### Q04 – Export mit Rechnungsdateien

**Aktion:** Schalter einschalten und erneut exportieren.  
**Erwartet:** Zusätzlich werden die zu Q4 gehörenden Rechnungsdokumente im
Ordner `Rechnungen/` exportiert. Die Q3/Q4-Grenzfälle werden anhand der in der
App gespeicherten Vorgänge geprüft.

### Q05 – Persistenz

**Aktion:** App vollständig schließen und neu öffnen. Danach mehrere gespeicherte
PDFs und Bilder aufrufen und erneut ein Quartalspaket erzeugen.  
**Erwartet:** Strukturierte Daten und kopierte Originaldateien sind weiterhin
vorhanden.

## 10. Bekannte Beobachtungspunkte des aktuellen Prototyps

Diese Punkte sind bewusst Teil des Tests und nicht automatisch Fehler in den
synthetischen Dateien:

1. Die Startseite kennt Öffnungs- bzw. Ruhetage nicht direkt. Erinnerungen
   lassen sich zwar nach Wochentagen konfigurieren, der Tagesabschluss-Hinweis
   kann am Dienstag trotzdem erscheinen.
2. Für einen Lieferschein existiert im Datenmodell eine eigene Kategorie, der
   aktuelle Detailbildschirm speichert neue Zusatzdokumente jedoch pauschal als
   `Papierrechnung`.
3. Der Datei-Picker akzeptiert derzeit nur PDF und Bilder. Ein üblicher
   CSV-/ZIP-Kassenexport kann deshalb nicht gewählt werden.
4. Eine SHA-256-Prüfsumme wird gespeichert, aber es ist noch zu prüfen, ob daraus
   eine sichtbare Warnung für identische Dateien entsteht.
5. Statuswerte hängen vom tatsächlichen Gerätedatum ab. Ohne ein konfigurierbares
   Testdatum altern die Fälle `heute fällig` und `bald fällig`.
6. Bei der Quartalsanzeige sollte geprüft werden, ob `Z-Abschläge: Da` schon bei
   nur einem vorhandenen Dokument missverständlich wirkt, obwohl weitere
   Tagesabschlüsse kein Dokument besitzen.

## 11. Speicherorte des erzeugten Pakets

- PDFs: `output/pdf/`
- Fotoscans: `output/images/`
- Strukturierter Kassenexport: `output/kassenexport/`
- Gesamtpaket: `output/Quartalsmappe_Testdaten_Waxingstudio_2026.zip`

Das Gesamtpaket enthält F00 bis F25 sowie ein SHA-256-Manifest. Die erzeugten
Dateien wurden auf Lesbarkeit, TEST-Kennzeichnung, Seitenformat, ZIP-Inhalt,
Duplikatgleichheit und rechnerische Abstimmung geprüft.
