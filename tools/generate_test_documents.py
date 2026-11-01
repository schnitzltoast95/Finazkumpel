from __future__ import annotations

import csv
import hashlib
import io
import os
import shutil
import textwrap
import zipfile
from datetime import date
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    HRFlowable,
    KeepTogether,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
PDF_DIR = ROOT / "output" / "pdf"
IMAGE_DIR = ROOT / "output" / "images"
EXPORT_DIR = ROOT / "output" / "kassenexport"
TMP_DIR = ROOT / "tmp" / "pdfs"
GUIDE = ROOT / "testdaten" / "TESTDATEN-LEITFADEN.md"
TEST_REPORT = ROOT / "output" / "TESTBERICHT.md"
PACKAGE = ROOT / "output" / "Quartalsmappe_Testdaten_Waxingstudio_2026.zip"

STUDIO = ["Waxing STUDIO Würzburg", "Semmelstraße 59", "97070 Würzburg"]
TEST_NOTICE = "TESTDOKUMENT - KEIN ECHTER BELEG"
SOURCE_NOTE = "Testpreise orientieren sich an https://www.waxingstudio.info/preise-1.html"

BURGUNDY = colors.HexColor("#7A244A")
ROSE = colors.HexColor("#F4E6EC")
INK = colors.HexColor("#26232A")
MUTED = colors.HexColor("#6E6670")
LINE = colors.HexColor("#D9CDD2")
RED = colors.HexColor("#B42318")
GREEN = colors.HexColor("#287052")


def register_fonts() -> None:
    candidates = [
        ("Arial", Path("C:/Windows/Fonts/arial.ttf")),
        ("Arial-Bold", Path("C:/Windows/Fonts/arialbd.ttf")),
        ("Arial-Italic", Path("C:/Windows/Fonts/ariali.ttf")),
    ]
    for name, path in candidates:
        if path.exists():
            pdfmetrics.registerFont(TTFont(name, str(path)))


register_fonts()
FONT = "Arial" if "Arial" in pdfmetrics.getRegisteredFontNames() else "Helvetica"
BOLD = "Arial-Bold" if "Arial-Bold" in pdfmetrics.getRegisteredFontNames() else "Helvetica-Bold"
ITALIC = "Arial-Italic" if "Arial-Italic" in pdfmetrics.getRegisteredFontNames() else "Helvetica-Oblique"


def de_money(value: Decimal | int | float | str) -> str:
    amount = Decimal(str(value)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    raw = f"{amount:,.2f}"
    return raw.replace(",", "X").replace(".", ",").replace("X", ".") + " €"


def dec(value: str | int | float | Decimal) -> Decimal:
    return Decimal(str(value)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def styles():
    sample = getSampleStyleSheet()
    return {
        "title": ParagraphStyle(
            "Title",
            parent=sample["Title"],
            fontName=BOLD,
            fontSize=20,
            leading=24,
            textColor=BURGUNDY,
            spaceAfter=4 * mm,
        ),
        "subtitle": ParagraphStyle(
            "Subtitle",
            parent=sample["Normal"],
            fontName=FONT,
            fontSize=9,
            leading=13,
            textColor=MUTED,
            spaceAfter=3 * mm,
        ),
        "h2": ParagraphStyle(
            "H2",
            parent=sample["Heading2"],
            fontName=BOLD,
            fontSize=11,
            leading=14,
            textColor=INK,
            spaceBefore=4 * mm,
            spaceAfter=2 * mm,
        ),
        "body": ParagraphStyle(
            "Body",
            parent=sample["Normal"],
            fontName=FONT,
            fontSize=9,
            leading=13,
            textColor=INK,
        ),
        "small": ParagraphStyle(
            "Small",
            parent=sample["Normal"],
            fontName=FONT,
            fontSize=7.5,
            leading=10,
            textColor=MUTED,
        ),
        "right": ParagraphStyle(
            "Right",
            parent=sample["Normal"],
            fontName=FONT,
            fontSize=9,
            leading=12,
            alignment=TA_RIGHT,
            textColor=INK,
        ),
        "center": ParagraphStyle(
            "Center",
            parent=sample["Normal"],
            fontName=FONT,
            fontSize=9,
            leading=12,
            alignment=TA_CENTER,
            textColor=INK,
        ),
        "test": ParagraphStyle(
            "Test",
            parent=sample["Normal"],
            fontName=BOLD,
            fontSize=8.5,
            leading=11,
            alignment=TA_CENTER,
            textColor=RED,
            backColor=colors.HexColor("#FDECEA"),
            borderColor=colors.HexColor("#F3B8B2"),
            borderWidth=0.6,
            borderPadding=5,
            spaceAfter=5 * mm,
        ),
    }


S = styles()


def on_page(canvas, doc):
    canvas.saveState()
    width, height = A4
    canvas.setStrokeColor(LINE)
    canvas.setLineWidth(0.5)
    canvas.line(18 * mm, 14 * mm, width - 18 * mm, 14 * mm)
    canvas.setFont(FONT, 7)
    canvas.setFillColor(MUTED)
    canvas.drawString(18 * mm, 9 * mm, "Synthetische Testdaten für die App Quartalsmappe")
    canvas.drawRightString(width - 18 * mm, 9 * mm, f"Seite {doc.page}")
    canvas.saveState()
    canvas.translate(width / 2, height / 2)
    canvas.rotate(34)
    canvas.setFillColor(colors.Color(0.70, 0.08, 0.08, alpha=0.065))
    canvas.setFont(BOLD, 31)
    canvas.drawCentredString(0, 0, TEST_NOTICE)
    canvas.restoreState()
    canvas.restoreState()


def doc(path: Path, title: str, subtitle: str = ""):
    path.parent.mkdir(parents=True, exist_ok=True)
    story = [Paragraph(TEST_NOTICE, S["test"]), Paragraph(title, S["title"])]
    if subtitle:
        story.append(Paragraph(subtitle, S["subtitle"]))
    return SimpleDocTemplate(
        str(path),
        pagesize=A4,
        leftMargin=18 * mm,
        rightMargin=18 * mm,
        topMargin=17 * mm,
        bottomMargin=20 * mm,
        title=title,
        author="Quartalsmappe Testdatengenerator",
    ), story


def p(text: str, style: str = "body"):
    return Paragraph(text.replace("&", "&amp;"), S[style])


def heading(text: str):
    return Paragraph(text, S["h2"])


def key_value(rows: list[tuple[str, str]], widths=(48 * mm, 105 * mm)):
    data = [[Paragraph(f"<b>{k}</b>", S["body"]), Paragraph(v, S["body"])] for k, v in rows]
    table = Table(data, colWidths=list(widths), hAlign="LEFT")
    table.setStyle(
        TableStyle(
            [
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
                ("TOPPADDING", (0, 0), (-1, -1), 4),
                ("LINEBELOW", (0, 0), (-1, -1), 0.25, LINE),
            ]
        )
    )
    return table


def data_table(headers: list[str], rows: list[list[str]], widths: list[float], right_cols=()):
    data = [[Paragraph(f"<b>{h}</b>", S["small"]) for h in headers]]
    for row in rows:
        data.append([Paragraph(str(v), S["small"]) for v in row])
    table = Table(data, colWidths=widths, repeatRows=1, hAlign="LEFT")
    commands = [
        ("BACKGROUND", (0, 0), (-1, 0), BURGUNDY),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTNAME", (0, 0), (-1, 0), BOLD),
        ("GRID", (0, 0), (-1, -1), 0.35, LINE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]
    for col in right_cols:
        commands.append(("ALIGN", (col, 1), (col, -1), "RIGHT"))
    for idx in range(1, len(data)):
        if idx % 2 == 0:
            commands.append(("BACKGROUND", (0, idx), (-1, idx), colors.HexColor("#FAF7F8")))
    table.setStyle(TableStyle(commands))
    return table


def build_pdf(path: Path, story: list):
    template, base = doc(path, story[0][0], story[0][1]) if story and isinstance(story[0], tuple) else (None, None)
    if template is None:
        raise ValueError("Story must start with (title, subtitle)")
    template.build(base + story[1:], onFirstPage=on_page, onLaterPages=on_page)


def create_z_report(path: Path, business_date: str, z_number: str, total: Decimal,
                    cash: Decimal, card: Decimal, service_rows: list[tuple[str, int, Decimal]],
                    start: str, end: str, note: str = ""):
    net = (total / Decimal("1.19")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    tax = total - net
    rows = []
    treatment_count = 0
    calculated = Decimal("0")
    for name, count, unit in service_rows:
        line_total = dec(count) * unit
        treatment_count += count
        calculated += line_total
        rows.append([name, str(count), de_money(unit), de_money(line_total)])
    if calculated != total:
        raise ValueError(f"Z report {path.name}: service sum {calculated} != total {total}")
    if cash + card != total:
        raise ValueError(f"Z report {path.name}: payments do not reconcile")
    story = [
        ("Z-Abschlag", f"Geschäftstag {business_date} | Abschluss {end}"),
        key_value([
            ("Betrieb", "<br/>".join(STUDIO)),
            ("Kasse", "KASSE-01-TEST / Seriennummer WST-2026-TEST"),
            ("Z-Nummer", z_number),
            ("Geschäftszeit", f"{start} bis {end}"),
            ("Bediener", "Studio-Team TEST"),
        ]),
        heading("Leistungsübersicht"),
        data_table(["Leistung", "Anzahl", "Einzelpreis", "Summe"], rows,
                   [76 * mm, 20 * mm, 29 * mm, 32 * mm], right_cols=(1, 2, 3)),
        Spacer(1, 4 * mm),
        data_table(
            ["Steuergruppe", "Netto", "USt.", "Brutto"],
            [["19 %", de_money(net), de_money(tax), de_money(total)]],
            [48 * mm, 36 * mm, 36 * mm, 37 * mm], right_cols=(1, 2, 3),
        ),
        heading("Zahlungsarten"),
        key_value([
            ("Bar", de_money(cash)),
            ("Kartenzahlungen", de_money(card)),
            ("Gesamtumsatz", f"<b>{de_money(total)}</b>"),
            ("Behandlungen", str(treatment_count)),
            ("Stornos", "0,00 €"),
            ("Trinkgeld", "Nicht im Testumsatz enthalten"),
        ]),
        Spacer(1, 4 * mm),
        p(note or "Kassenabschluss erfolgreich durchgeführt.", "small"),
        p(SOURCE_NOTE, "small"),
    ]
    build_pdf(path, story)


def create_invoice(path: Path, supplier: list[str], invoice_number: str, invoice_date: str,
                   service_date: str, due_date: str, lines: list[tuple[str, str, Decimal]],
                   net: Decimal, tax: Decimal, gross: Decimal, reference: str = "",
                   paid_note: str = "", intro: str = "Rechnung"):
    line_sum = sum((qty_price for _, _, qty_price in lines), Decimal("0"))
    if line_sum != net:
        raise ValueError(f"{path.name}: line sum {line_sum} != net {net}")
    if net + tax != gross:
        raise ValueError(f"{path.name}: totals do not reconcile")
    rows = [[str(i), name, qty, de_money(value)] for i, (name, qty, value) in enumerate(lines, 1)]
    story = [
        (intro, f"Rechnungsnummer {invoice_number}"),
        Table(
            [[p("<b>Absender</b><br/>" + "<br/>".join(supplier)), p("<b>Rechnungsempfänger</b><br/>" + "<br/>".join(STUDIO))]],
            colWidths=[78 * mm, 78 * mm], hAlign="LEFT",
            style=TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP"), ("BOX", (0, 0), (-1, -1), 0.5, LINE),
                              ("INNERGRID", (0, 0), (-1, -1), 0.25, LINE), ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#FAF7F8")),
                              ("TOPPADDING", (0, 0), (-1, -1), 8), ("BOTTOMPADDING", (0, 0), (-1, -1), 8)]),
        ),
        Spacer(1, 4 * mm),
        key_value([
            ("Rechnungsdatum", invoice_date),
            ("Liefer-/Leistungsdatum", service_date),
            ("Zahlbar bis", due_date),
            ("Kundennummer", "WST-WUE-TEST"),
            ("Ihre Referenz", reference or "Testvorgang Quartalsmappe"),
        ]),
        heading("Positionen"),
        data_table(["Pos.", "Leistung / Artikel", "Menge", "Netto"], rows,
                   [14 * mm, 91 * mm, 22 * mm, 30 * mm], right_cols=(2, 3)),
        Spacer(1, 4 * mm),
        Table(
            [[p("Nettobetrag", "right"), p(de_money(net), "right")],
             [p("Umsatzsteuer 19 %", "right"), p(de_money(tax), "right")],
             [Paragraph("<b>Rechnungsbetrag</b>", S["right"]), Paragraph(f"<b>{de_money(gross)}</b>", S["right"])]],
            colWidths=[112 * mm, 45 * mm], hAlign="LEFT",
            style=TableStyle([("LINEABOVE", (0, 2), (-1, 2), 1, BURGUNDY), ("TOPPADDING", (0, 0), (-1, -1), 4),
                              ("BOTTOMPADDING", (0, 0), (-1, -1), 4)]),
        ),
        Spacer(1, 5 * mm),
        p(paid_note or f"Bitte überweisen Sie den Rechnungsbetrag bis {due_date} unter Angabe der Rechnungsnummer auf das TEST-Konto DE00 0000 0000 0000 0000 00."),
        Spacer(1, 3 * mm),
        p("Steuernummer: TEST-257/999/99999 | USt-IdNr.: DE000000000 (ungültige Testwerte)", "small"),
        p("Bankverbindung und Kontaktdaten des Lieferanten sind vollständig synthetisch.", "small"),
    ]
    build_pdf(path, story)


def create_order_confirmation(path: Path):
    story = [
        ("Bestellbestätigung", "Auftrag WP-2026-1008-TEST"),
        key_value([
            ("Lieferant", "WaxPro Bedarf GmbH (TEST)<br/>Industriestraße 12<br/>97076 Würzburg"),
            ("Besteller", "<br/>".join(STUDIO)),
            ("Bestelldatum", "08.10.2026"),
            ("Liefertermin", "Noch offen"),
            ("Rechnungsbetrag", "Noch offen"),
        ]),
        heading("Bestellte Waren"),
        data_table(["Artikel", "Menge", "Hinweis"], [["Warmwachs Sensitive", "6 Kartons", "Preis folgt mit Rechnung"],
                                                      ["Holzspatel, groß", "4 Packungen", "Lieferbarkeit wird bestätigt"]],
                   [78 * mm, 30 * mm, 49 * mm]),
        Spacer(1, 6 * mm),
        p("Dies ist bewusst noch keine Rechnung. Der Vorgang testet eine Bestellung mit fehlendem Betrag, Rechnungsdatum und Lieferdatum."),
    ]
    build_pdf(path, story)


def create_delivery_note(path: Path):
    story = [
        ("Lieferschein", "Zu Rechnung HS-4711-2026-TEST"),
        key_value([
            ("Lieferant", "Hygiene Süd GmbH (TEST)<br/>Sauberweg 8<br/>97084 Würzburg"),
            ("Empfänger", "<br/>".join(STUDIO)),
            ("Lieferscheinnummer", "LS-HS-1009-TEST"),
            ("Lieferdatum", "09.10.2026"),
            ("Bestellung", "05.10.2026 / HS-B-4102-TEST"),
        ]),
        heading("Gelieferte Artikel"),
        data_table(["Artikel", "Menge", "Status"], [
            ["Flächendesinfektion 5 l", "4 Kanister", "vollständig"],
            ["Einmalhandschuhe Nitril", "8 Boxen", "vollständig"],
            ["Papierauflagen Behandlungsliege", "6 Rollen", "vollständig"],
        ], [86 * mm, 31 * mm, 40 * mm]),
        Spacer(1, 8 * mm),
        p("Ware vollständig und äußerlich unbeschädigt erhalten."),
        Spacer(1, 14 * mm),
        HRFlowable(width=65 * mm, thickness=0.6, color=INK, hAlign="LEFT"),
        p("Unterschrift Wareneingang (TEST)", "small"),
    ]
    build_pdf(path, story)


def create_simple_notice(path: Path, title: str, subtitle: str, rows: list[tuple[str, str]],
                         body: list[str], table=None):
    story = [(title, subtitle), key_value(rows)]
    for paragraph in body:
        story += [Spacer(1, 3 * mm), p(paragraph)]
    if table:
        story += [heading(table[0]), data_table(table[1], table[2], table[3], right_cols=table[4])]
    build_pdf(path, story)


def create_bank_statement(path: Path):
    opening = dec("12500.00")
    entries = [
        ("01.10.2026", "Kartenzahlungen Sammelgutschrift 30.09.", dec("486.00")),
        ("02.10.2026", "StudioTextil Service KG TEST STS-0930-26", dec("-428.40")),
        ("02.10.2026", "Kartenzahlungen Sammelgutschrift 01.10.", dec("612.00")),
        ("05.10.2026", "Miete Oktober TEST", dec("-1428.00")),
        ("06.10.2026", "Kassenservice Mainfranken TEST", dec("-178.50")),
        ("07.10.2026", "Kartenzahlungen Sammelgutschrift 06.10.", dec("391.00")),
        ("08.10.2026", "SEPA Strom Abschlag TEST", dec("-300.00")),
        ("09.10.2026", "Kartenzahlungen Sammelgutschrift 08.10.", dec("445.00")),
        ("12.10.2026", "Kartenzahlungen Sammelgutschrift 10.10.", dec("320.00")),
        ("12.10.2026", "Bareinzahlung Tageskasse TEST", dec("1100.00")),
        ("13.10.2026", "Kontoführung Oktober TEST", dec("-12.90")),
    ]
    running = opening
    rows = []
    for d, text, amount in entries:
        running += amount
        rows.append([d, text, de_money(amount), de_money(running)])
    story = [
        ("Kontoauszug", "TEST-Bank | Kontoauszug 10/2026 | Seite 1 von 2"),
        key_value([
            ("Kontoinhaber", "Waxing STUDIO Würzburg"),
            ("IBAN", "DE00 0000 0000 0000 0000 00 (ungültige Test-IBAN)"),
            ("Zeitraum", "01.10.2026 bis 13.10.2026"),
            ("Anfangssaldo", de_money(opening)),
        ]),
        heading("Buchungen"),
        data_table(["Datum", "Buchungstext", "Betrag", "Saldo"], rows[:7],
                   [25 * mm, 72 * mm, 30 * mm, 30 * mm], right_cols=(2, 3)),
        PageBreak(),
        Paragraph(TEST_NOTICE, S["test"]),
        Paragraph("Kontoauszug - Fortsetzung", S["title"]),
        Paragraph("TEST-Bank | Kontoauszug 10/2026 | Seite 2 von 2", S["subtitle"]),
        data_table(["Datum", "Buchungstext", "Betrag", "Saldo"], rows[7:],
                   [25 * mm, 72 * mm, 30 * mm, 30 * mm], right_cols=(2, 3)),
        Spacer(1, 6 * mm),
        key_value([
            ("Endsaldo", de_money(running)),
            ("Hinweis", "Alle Buchungen, Kontodaten und Beträge sind synthetische Testdaten."),
        ]),
    ]
    build_pdf(path, story)


def create_cash_export_overview(path: Path):
    daily = [
        ("01.10.2026", "Z-0201", dec("612.00"), dec("226.00"), dec("386.00")),
        ("02.10.2026", "Z-0202", dec("588.00"), dec("247.00"), dec("341.00")),
        ("03.10.2026", "Z-0203", dec("471.00"), dec("201.00"), dec("270.00")),
        ("05.10.2026", "Z-0204", dec("695.00"), dec("285.00"), dec("410.00")),
        ("07.10.2026", "Z-0205", dec("391.00"), dec("166.00"), dec("225.00")),
        ("08.10.2026", "Z-0206", dec("445.00"), dec("175.00"), dec("270.00")),
        ("09.10.2026", "Z-0207", dec("686.00"), dec("281.00"), dec("405.00")),
        ("10.10.2026", "Z-0208", dec("516.00"), dec("196.00"), dec("320.00")),
        ("12.10.2026", "Z-0209", dec("742.00"), dec("312.00"), dec("430.00")),
    ]
    total = sum((r[2] for r in daily), Decimal("0"))
    cash = sum((r[3] for r in daily), Decimal("0"))
    card = sum((r[4] for r in daily), Decimal("0"))
    rows = [[d, z, de_money(t), de_money(c), de_money(k)] for d, z, t, c, k in daily]
    story = [
        ("Kassenexport - lesbare Übersicht", "Q4/2026 | 01.10.2026 bis 12.10.2026"),
        key_value([
            ("Betrieb", "<br/>".join(STUDIO)),
            ("Kasse", "KASSE-01-TEST"),
            ("Exportkennung", "KEXP-2026-Q4-TEST"),
            ("Format", "Lesbare PDF-Zusammenfassung für den aktuellen Prototyp"),
        ]),
        heading("Tagesabschlüsse"),
        data_table(["Datum", "Z-Nr.", "Brutto", "Bar", "Karte"], rows,
                   [30 * mm, 24 * mm, 35 * mm, 34 * mm, 34 * mm], right_cols=(2, 3, 4)),
        Spacer(1, 5 * mm),
        key_value([
            ("Gesamtumsatz", de_money(total)),
            ("Bar", de_money(cash)),
            ("Karte", de_money(card)),
            ("Abstimmung", "Bar + Karte = Gesamtumsatz"),
        ]),
        Spacer(1, 4 * mm),
        p("Diese PDF ist nicht DSFinV-K-konform. Sie testet ausschließlich die gegenwärtige PDF-Ablage der App.", "small"),
    ]
    build_pdf(path, story)


def create_payroll(path: Path):
    rows = [
        ["TEST-P001", "Mitarbeiterin Alpha", "2.180,00 €", "436,00 €", "1.744,00 €"],
        ["TEST-P002", "Mitarbeiterin Beta", "1.420,00 €", "284,00 €", "1.136,00 €"],
    ]
    story = [
        ("Lohnübersicht", "Abrechnungsmonat Oktober 2026"),
        key_value([
            ("Arbeitgeber", "<br/>".join(STUDIO)),
            ("Abrechnungslauf", "2026-10-TEST"),
            ("Erstellt am", "10.10.2026"),
            ("Datenschutz", "Ausschließlich erfundene Namen und Personalnummern"),
        ]),
        heading("Abrechnungen"),
        data_table(["Personalnr.", "Name", "Brutto", "Abzüge TEST", "Netto TEST"], rows,
                   [27 * mm, 51 * mm, 27 * mm, 28 * mm, 27 * mm], right_cols=(2, 3, 4)),
        Spacer(1, 6 * mm),
        p("Die Abzüge sind bewusst vereinfachte Testwerte und keine reale Lohnabrechnung. Das Dokument dient nur der Kategorie- und Exportprüfung."),
    ]
    build_pdf(path, story)


def receipt_image(path: Path, title: str, lines: list[str], rotate: float = 0.0,
                  low_contrast: bool = False, blur: float = 0.0):
    width, height = 1240, 1754
    paper = Image.new("RGB", (width, height), "#F7F4EE")
    draw = ImageDraw.Draw(paper)
    font_path = Path("C:/Windows/Fonts/consola.ttf")
    bold_path = Path("C:/Windows/Fonts/consolab.ttf")
    regular = ImageFont.truetype(str(font_path), 32) if font_path.exists() else ImageFont.load_default()
    bold = ImageFont.truetype(str(bold_path), 42) if bold_path.exists() else regular
    small = ImageFont.truetype(str(font_path), 25) if font_path.exists() else regular
    red = "#8C2B2B" if not low_contrast else "#987C7C"
    ink = "#252525" if not low_contrast else "#8A8580"
    draw.rectangle((75, 60, width - 75, height - 60), fill="#FFFEFA", outline="#D5CEC2", width=3)
    draw.text((width // 2, 100), TEST_NOTICE, font=bold, fill=red, anchor="ma")
    draw.text((width // 2, 175), title, font=bold, fill=ink, anchor="ma")
    y = 260
    for line in lines:
        if line == "---":
            draw.line((130, y + 12, width - 130, y + 12), fill=ink, width=2)
            y += 45
            continue
        for wrapped in textwrap.wrap(line, width=57) or [""]:
            draw.text((130, y), wrapped, font=regular, fill=ink)
            y += 47
        y += 4
    draw.text((width // 2, height - 145), "Synthetische Testdaten - nicht buchen", font=small, fill=red, anchor="ma")
    if low_contrast:
        paper = ImageEnhance.Contrast(paper).enhance(0.55)
        noise = Image.effect_noise((width, height), 8).convert("L")
        noise_rgb = Image.merge("RGB", (noise, noise, noise))
        paper = Image.blend(paper, noise_rgb, 0.055)
    if blur:
        paper = paper.filter(ImageFilter.GaussianBlur(blur))
    if rotate:
        canvas = Image.new("RGB", (width + 180, height + 180), "#D8D4CE")
        rotated = paper.rotate(rotate, expand=True, resample=Image.Resampling.BICUBIC, fillcolor="#D8D4CE")
        x = (canvas.width - rotated.width) // 2
        y0 = (canvas.height - rotated.height) // 2
        canvas.paste(rotated, (x, y0))
        paper = canvas.resize((width, height), Image.Resampling.LANCZOS)
    path.parent.mkdir(parents=True, exist_ok=True)
    paper.save(path, "JPEG", quality=90, optimize=True)


def create_structured_export(path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    files: dict[str, list[list[str]]] = {
        "cashregister.csv": [
            ["KASSE_ID", "KASSE_BRAND", "KASSE_MODELL", "KASSE_SERIENNR"],
            ["KASSE-01-TEST", "TestKasse", "StudioPOS", "WST-2026-TEST"],
        ],
        "location.csv": [
            ["LOCATION_ID", "NAME", "STRASSE", "PLZ", "ORT"],
            ["WST-WUE-TEST", "Waxing STUDIO Würzburg", "Semmelstr. 59", "97070", "Würzburg"],
        ],
        "transactions.csv": [
            ["BON_ID", "BON_TYP", "BON_NAME", "BON_START", "BON_ENDE", "BRUTTO", "UST_SATZ"],
            ["T-20261012-001", "Beleg", "Waxing", "2026-10-12T10:03:00", "2026-10-12T10:21:00", "39.00", "19.00"],
            ["T-20261012-002", "Beleg", "Waxing", "2026-10-12T10:30:00", "2026-10-12T10:52:00", "47.00", "19.00"],
            ["T-20261012-003", "Beleg", "Waxing", "2026-10-12T11:05:00", "2026-10-12T11:18:00", "14.00", "19.00"],
        ],
        "payment.csv": [
            ["BON_ID", "ZAHLART", "BETRAG"],
            ["T-20261012-001", "Karte", "39.00"],
            ["T-20261012-002", "Bar", "47.00"],
            ["T-20261012-003", "Karte", "14.00"],
        ],
        "lines.csv": [
            ["BON_ID", "POS", "ARTIKELTEXT", "MENGE", "EINZELPREIS", "BRUTTO"],
            ["T-20261012-001", "1", "Damen Beine komplett", "1", "39.00", "39.00"],
            ["T-20261012-002", "1", "Damen Gesicht komplett", "1", "47.00", "47.00"],
            ["T-20261012-003", "1", "Damen Achseln", "1", "14.00", "14.00"],
        ],
    }
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("README_TEST.txt", (
            f"{TEST_NOTICE}\n\n"
            "Dieser ZIP-Inhalt ist nur DSFinV-K-artig aufgebaut und nicht als formal konformer "
            "Export zertifiziert. Er testet die Dateiauswahl und Ablage strukturierter Kassendaten.\n"
        ))
        for name, rows in files.items():
            buffer = io.StringIO(newline="")
            writer = csv.writer(buffer, delimiter=";", lineterminator="\n")
            writer.writerows(rows)
            archive.writestr(name, "\ufeff" + buffer.getvalue())


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(65536), b""):
            digest.update(block)
    return digest.hexdigest()


def create_package(files: list[Path]):
    PACKAGE.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(PACKAGE, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.write(GUIDE, "F00_TESTDATEN-LEITFADEN.md")
        if TEST_REPORT.exists():
            archive.write(TEST_REPORT, "TESTBERICHT.md")
        manifest_rows = [["Datei", "SHA-256", "Bytes"]]
        for path in files:
            archive.write(path, path.name)
            manifest_rows.append([path.name, sha256(path), str(path.stat().st_size)])
        buffer = io.StringIO(newline="")
        csv.writer(buffer, delimiter=";", lineterminator="\n").writerows(manifest_rows)
        archive.writestr("MANIFEST_SHA256.csv", "\ufeff" + buffer.getvalue())


def generate_all() -> list[Path]:
    for directory in (PDF_DIR, IMAGE_DIR, EXPORT_DIR, TMP_DIR):
        directory.mkdir(parents=True, exist_ok=True)

    outputs: list[Path] = []

    f01 = PDF_DIR / "F01_Z-Abschlag_2026-10-12_TEST.pdf"
    create_z_report(f01, "12.10.2026", "Z-0209", dec("742.00"), dec("312.00"), dec("430.00"), [
        ("Damen Achseln", 6, dec("14")),
        ("Damen Beine komplett", 5, dec("39")),
        ("Damen Gesicht komplett", 3, dec("47")),
        ("Damen Augenbrauen modellieren", 4, dec("23")),
        ("Herren Rücken inkl. Schultern/Nacken", 3, dec("46")),
        ("Herren Beine komplett", 2, dec("46")),
    ], "10:00", "18:07")
    outputs.append(f01)

    f02 = IMAGE_DIR / "F02_Z-Abschlag_2026-10-10_Foto_TEST.jpg"
    receipt_image(f02, "Z-ABSCHLAG 10.10.2026", [
        "Waxing STUDIO Würzburg", "Semmelstr. 59, 97070 Würzburg", "Kasse: KASSE-01-TEST   Z-Nr.: Z-0208", "---",
        "14 synthetische Behandlungen", "Brutto 19 %                     516,00 EUR", "Netto                           433,61 EUR",
        "USt. 19 %                        82,39 EUR", "---", "Bar                             196,00 EUR",
        "Karte                           320,00 EUR", "GESAMT                          516,00 EUR", "---",
        "Öffnung 11:00   Abschluss 16:09", "Stornos 0,00 EUR",
    ], rotate=-1.2)
    outputs.append(f02)

    f03 = PDF_DIR / "F03_Z-Abschlag_2026-09-30_Q3-Grenze_TEST.pdf"
    create_z_report(f03, "30.09.2026", "Z-0198", dec("804.00"), dec("344.00"), dec("460.00"), [
        ("Damen Gesicht komplett", 6, dec("47")),
        ("Damen Beine komplett", 6, dec("39")),
        ("Damen Brazilian komplett", 4, dec("35")),
        ("Damen Arme komplett", 4, dec("28")),
        ("Herren Arme komplett", 1, dec("36")),
    ], "10:00", "18:11", "Letzter Geschäftstag des dritten Quartals.")
    outputs.append(f03)

    f04 = IMAGE_DIR / "F04_Z-Abschlag_schief-und-blass_TEST.jpg"
    receipt_image(f04, "Z-ABSCHLAG 14.10.2026", [
        "Waxing STUDIO Würzburg", "Kasse KASSE-01-TEST / Z-0210", "---", "Tagesumsatz brutto              634,00 EUR",
        "Bar                             264,00 EUR", "Karte                           370,00 EUR", "---",
        "Abschluss 18:04", "Dieser Scan ist absichtlich blass und schief.",
    ], rotate=4.0, low_contrast=True, blur=0.7)
    outputs.append(f04)

    f05a = PDF_DIR / "F05_Bestellbestaetigung_WaxPro_TEST.pdf"
    create_order_confirmation(f05a)
    outputs.append(f05a)

    f05 = PDF_DIR / "F06_Rechnung_Verbrauchsmaterial_ueberfaellig_TEST.pdf"
    create_invoice(f05, ["MainWachs Handelsgesellschaft mbH (TEST)", "Wachsweg 14", "97076 Würzburg"],
                   "MW-2026-0916-TEST", "16.09.2026", "18.09.2026", "10.10.2026",
                   [("Warmwachsperlen Sensitive, 10 kg", "2 Kartons", dec("318.00")),
                    ("Vliesstreifen 100 m", "6 Rollen", dec("132.00")),
                    ("Holzspatel Sortiment", "8 Packungen", dec("90.18"))],
                   dec("540.18"), dec("102.63"), dec("642.81"), "Bestellung vom 14.09.2026")
    outputs.append(f05)

    f06 = PDF_DIR / "F07_Rechnung_Reinigung_faellig-heute_TEST.pdf"
    create_invoice(f06, ["Blank & Glanz Gebäudeservice (TEST)", "Reinweg 7", "97070 Würzburg"],
                   "BG-2026-1007-TEST", "30.09.2026", "30.09.2026", "13.10.2026",
                   [("Grundreinigung Studio und Nebenräume", "1 Pauschale", dec("160.00")),
                    ("Hygienische Reinigung Behandlungskabinen", "1 Pauschale", dec("40.00"))],
                   dec("200.00"), dec("38.00"), dec("238.00"))
    outputs.append(f06)

    f07 = PDF_DIR / "F08_Rechnung_Hygienebedarf_bald-faellig_TEST.pdf"
    create_invoice(f07, ["Hygiene Süd GmbH (TEST)", "Sauberweg 8", "97084 Würzburg"],
                   "HS-4711-2026-TEST", "07.10.2026", "09.10.2026", "18.10.2026",
                   [("Flächendesinfektion 5 l", "4 Kanister", dec("108.00")),
                    ("Einmalhandschuhe Nitril", "8 Boxen", dec("92.00")),
                    ("Papierauflagen Behandlungsliege", "6 Rollen", dec("64.00"))],
                   dec("264.00"), dec("50.16"), dec("314.16"), "Bestellung HS-B-4102-TEST")
    outputs.append(f07)

    f08 = IMAGE_DIR / "F09_Papierrechnung_Hygienebedarf_SCAN_TEST.jpg"
    receipt_image(f08, "PAPIERRECHNUNG HS-4711-2026-TEST", [
        "Hygiene Süd GmbH (TEST)", "an Waxing STUDIO Würzburg", "Rechnungsdatum 07.10.2026", "Lieferdatum 09.10.2026", "---",
        "Flächendesinfektion 5 l          108,00 EUR", "Einmalhandschuhe Nitril           92,00 EUR",
        "Papierauflagen                    64,00 EUR", "---", "Netto                            264,00 EUR",
        "USt. 19 %                         50,16 EUR", "RECHNUNGSBETRAG                  314,16 EUR",
        "Fällig am 18.10.2026",
    ], rotate=1.1, low_contrast=True, blur=0.25)
    outputs.append(f08)

    f09 = PDF_DIR / "F10_Lieferschein_Hygienebedarf_TEST.pdf"
    create_delivery_note(f09)
    outputs.append(f09)

    f10 = PDF_DIR / "F11_Rechnung_Buerobedarf_offen_TEST.pdf"
    create_invoice(f10, ["Büro Kontor Franken (TEST)", "Papierstraße 21", "97074 Würzburg"],
                   "BKF-81826-TEST", "08.10.2026", "09.10.2026", "30.10.2026",
                   [("Thermorollen für Kasse", "10 Rollen", dec("32.50")),
                    ("Druckerpapier und Etiketten", "1 Set", dec("43.21")),
                    ("Archivordner", "6 Stück", dec("25.00"))],
                   dec("100.71"), dec("19.13"), dec("119.84"))
    outputs.append(f10)

    f11 = PDF_DIR / "F12_Rechnung_Kassenwartung_bezahlt_TEST.pdf"
    create_invoice(f11, ["Kassenservice Mainfranken (TEST)", "Systemweg 3", "97080 Würzburg"],
                   "KSM-2026-1001-TEST", "01.10.2026", "01.10.2026", "15.10.2026",
                   [("Wartung Kassensystem", "1 Termin", dec("120.00")),
                    ("Funktionsprüfung TSE", "1 Prüfung", dec("30.00"))],
                   dec("150.00"), dec("28.50"), dec("178.50"), paid_note="Zahlungseingang am 06.10.2026 - TESTVERMERK.")
    outputs.append(f11)

    f12 = PDF_DIR / "F13_Rechnung_Q3_Zahlung-Q4_TEST.pdf"
    create_invoice(f12, ["StudioTextil Service KG (TEST)", "Textilring 5", "97082 Würzburg"],
                   "STS-0930-26-TEST", "30.09.2026", "30.09.2026", "14.10.2026",
                   [("Handtuchservice September 2026", "1 Monat", dec("280.00")),
                    ("Reinigung Behandlungslaken", "1 Monat", dec("80.00"))],
                   dec("360.00"), dec("68.40"), dec("428.40"), paid_note="Zahlungseingang am 02.10.2026 - TESTVERMERK.")
    outputs.append(f12)

    f13 = PDF_DIR / "F14_Rechnung_Hygienebedarf_DUPLIKATNAME_TEST.pdf"
    shutil.copy2(f07, f13)
    outputs.append(f13)

    f14 = PDF_DIR / "F15_Kontoauszug_2026_Q4_TEST.pdf"
    create_bank_statement(f14)
    outputs.append(f14)

    f15 = PDF_DIR / "F16_Kassenexport_Uebersicht_2026_Q4_TEST.pdf"
    create_cash_export_overview(f15)
    outputs.append(f15)

    f16 = PDF_DIR / "F17_Miete_Oktober-2026_TEST.pdf"
    create_invoice(f16, ["Immobilienverwaltung Mainbogen (TEST)", "Verwalterweg 2", "97070 Würzburg"],
                   "IVM-MIETE-2026-10-TEST", "01.10.2026", "01.10.2026", "05.10.2026",
                   [("Gewerberaummiete Oktober 2026", "1 Monat", dec("1050.00")),
                    ("Nebenkostenvorauszahlung", "1 Monat", dec("150.00"))],
                   dec("1200.00"), dec("228.00"), dec("1428.00"), paid_note="SEPA-Testabbuchung am 05.10.2026.")
    outputs.append(f16)

    f17 = PDF_DIR / "F18_Strom_2026_Q4_TEST.pdf"
    create_invoice(f17, ["Energie Main Test AG", "Versorgerallee 1", "97070 Würzburg"],
                   "EMT-STROM-2026-Q4-TEST", "01.10.2026", "01.10.2026", "08.10.2026",
                   [("Stromabschlag Oktober bis Dezember", "1 Abschlag", dec("252.10"))],
                   dec("252.10"), dec("47.90"), dec("300.00"), paid_note="SEPA-Testabbuchung am 08.10.2026.")
    outputs.append(f17)

    f18 = PDF_DIR / "F19_Gas_2026_Q4_TEST.pdf"
    create_invoice(f18, ["Wärmeversorgung Franken TEST", "Energieweg 9", "97076 Würzburg"],
                   "WVF-GAS-2026-Q4-TEST", "02.10.2026", "01.10.2026", "20.10.2026",
                   [("Gasabschlag Q4/2026", "1 Abschlag", dec("168.07"))],
                   dec("168.07"), dec("31.93"), dec("200.00"))
    outputs.append(f18)

    f19 = PDF_DIR / "F20_Lohnunterlagen_Oktober-2026_TEST.pdf"
    create_payroll(f19)
    outputs.append(f19)

    f20 = PDF_DIR / "F21_GEMA_Jahresbeitrag-2026_TEST.pdf"
    create_simple_notice(f20, "Jahresbeitrag GEMA", "Kalenderjahr 2026", [
        ("Adressat", "<br/>".join(STUDIO)), ("Vertragskonto", "GEMA-WST-2026-TEST"),
        ("Zeitraum", "01.01.2026 bis 31.12.2026"), ("Fälligkeit", "15.02.2026"),
        ("Jahresbetrag", "385,56 €"),
    ], ["Synthetische Jahresunterlage für die Kategorie GEMA. Dieses Dokument stammt nicht von der GEMA und begründet keine Forderung."])
    outputs.append(f20)

    f21 = PDF_DIR / "F22_Rundfunkbeitrag_2026_TEST.pdf"
    create_simple_notice(f21, "Rundfunkbeitrag - Jahresübersicht", "Kalenderjahr 2026", [
        ("Adressat", "<br/>".join(STUDIO)), ("Beitragsnummer", "000 000 000 TEST"),
        ("Betriebsstätte", "1"), ("Jahresbetrag", "220,32 €"),
    ], ["Reine Testunterlage. Kein Schreiben des Beitragsservice und keine Zahlungsaufforderung."])
    outputs.append(f21)

    f22 = PDF_DIR / "F23_BGW_Beitragsbescheid-2026_TEST.pdf"
    create_simple_notice(f22, "BGW-Beitragsübersicht", "Beitragsjahr 2026", [
        ("Mitgliedsbetrieb", "<br/>".join(STUDIO)), ("Unternehmensnummer", "BGW-WST-TEST-2026"),
        ("Gefahrtarifstelle", "Kosmetikbetrieb - TEST"), ("Testbeitrag", "318,40 €"),
    ], ["Dieses synthetische Dokument ist kein Bescheid einer Berufsgenossenschaft und enthält keine echte Unternehmensnummer."])
    outputs.append(f22)

    f23 = PDF_DIR / "F24_Betriebshaftpflicht_2026_TEST.pdf"
    create_simple_notice(f23, "Betriebshaftpflicht - Beitragsrechnung", "Versicherungsjahr 2026", [
        ("Versicherer", "Sicher & Ruhig Versicherung AG (TEST)"),
        ("Versicherungsnehmer", "<br/>".join(STUDIO)), ("Vertragsnummer", "SRV-WST-2026-TEST"),
        ("Versicherungszeitraum", "01.01.2026 bis 31.12.2026"), ("Jahresbeitrag", "540,00 €"),
    ], ["Synthetische Unterlage für die App-Kategorie Sonstiges. Es besteht kein Versicherungsschutz."])
    outputs.append(f23)

    f24 = EXPORT_DIR / "F25_DSFinV-K_Kassenexport_2026_Q4_TEST.zip"
    create_structured_export(f24)
    outputs.append(f24)

    create_package(outputs)
    return outputs


if __name__ == "__main__":
    generated = generate_all()
    print(f"Generated {len(generated)} individual files")
    for item in generated:
        print(item.relative_to(ROOT))
    print(PACKAGE.relative_to(ROOT))
