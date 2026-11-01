from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    Flowable,
    Image,
    KeepTogether,
    PageBreak,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)
from reportlab.lib.utils import ImageReader


ROOT = Path(__file__).resolve().parents[1]
SCREENSHOTS = ROOT / "docs" / "app-dokumentation" / "screenshots"
OUTPUT = ROOT / "output" / "pdf" / "Svetlanas_Finanzkumpel_Funktionsuebersicht.pdf"

FOREST = colors.HexColor("#234D2B")
FOREST_DARK = colors.HexColor("#142719")
SAGE = colors.HexColor("#DDE8D2")
PALE = colors.HexColor("#F4F6EF")
CREAM = colors.HexColor("#FBFAF6")
CORAL = colors.HexColor("#C85D4A")
INK = colors.HexColor("#1D241D")
MUTED = colors.HexColor("#5A635A")
LINE = colors.HexColor("#D8DED3")


def register_fonts():
    candidates = {
        "Body": Path(r"C:\Windows\Fonts\arial.ttf"),
        "BodyBold": Path(r"C:\Windows\Fonts\arialbd.ttf"),
        "Display": Path(r"C:\Windows\Fonts\georgia.ttf"),
        "DisplayBold": Path(r"C:\Windows\Fonts\georgiab.ttf"),
    }
    for name, path in candidates.items():
        if path.exists():
            pdfmetrics.registerFont(TTFont(name, str(path)))


register_fonts()
BODY = "Body" if "Body" in pdfmetrics.getRegisteredFontNames() else "Helvetica"
BODY_BOLD = "BodyBold" if "BodyBold" in pdfmetrics.getRegisteredFontNames() else "Helvetica-Bold"
DISPLAY = "Display" if "Display" in pdfmetrics.getRegisteredFontNames() else "Times-Roman"
DISPLAY_BOLD = "DisplayBold" if "DisplayBold" in pdfmetrics.getRegisteredFontNames() else "Times-Bold"


styles = getSampleStyleSheet()
styles.add(ParagraphStyle(
    name="CoverTitle", fontName=DISPLAY_BOLD, fontSize=30, leading=34,
    textColor=FOREST_DARK, spaceAfter=10,
))
styles.add(ParagraphStyle(
    name="CoverSub", fontName=BODY, fontSize=12, leading=18,
    textColor=MUTED, spaceAfter=18,
))
styles.add(ParagraphStyle(
    name="H1x", fontName=DISPLAY_BOLD, fontSize=22, leading=27,
    textColor=FOREST_DARK, spaceAfter=12,
))
styles.add(ParagraphStyle(
    name="H2x", fontName=BODY_BOLD, fontSize=13, leading=17,
    textColor=FOREST, spaceBefore=4, spaceAfter=6,
))
styles.add(ParagraphStyle(
    name="Bodyx", fontName=BODY, fontSize=10.2, leading=15,
    textColor=INK, spaceAfter=8,
))
styles.add(ParagraphStyle(
    name="Smallx", fontName=BODY, fontSize=8.6, leading=12,
    textColor=MUTED, spaceAfter=4,
))
styles.add(ParagraphStyle(
    name="Caption", fontName=BODY, fontSize=8.5, leading=11,
    textColor=MUTED, alignment=TA_CENTER, spaceBefore=5,
))
styles.add(ParagraphStyle(
    name="CardTitle", fontName=BODY_BOLD, fontSize=11.3, leading=14,
    textColor=FOREST_DARK, spaceAfter=4,
))
styles.add(ParagraphStyle(
    name="CardBody", fontName=BODY, fontSize=9.5, leading=13.5,
    textColor=INK,
))
styles.add(ParagraphStyle(
    name="Label", fontName=BODY_BOLD, fontSize=8.5, leading=10,
    textColor=FOREST, spaceAfter=2,
))
styles.add(ParagraphStyle(
    name="Question", fontName=BODY, fontSize=11, leading=16,
    textColor=INK, leftIndent=18, firstLineIndent=-18, spaceAfter=8,
))


def P(text, style="Bodyx"):
    return Paragraph(text, styles[style])


def bullet(text):
    return Paragraph(f"<font color='#234D2B'>●</font>&nbsp;&nbsp;{text}", styles["Bodyx"])


def card(title, body, bg=PALE, accent=FOREST):
    content = [P(title, "CardTitle"), P(body, "CardBody")]
    table = Table([[content]], colWidths=[None])
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), bg),
        ("BOX", (0, 0), (-1, -1), 0.7, accent),
        ("LEFTPADDING", (0, 0), (-1, -1), 12),
        ("RIGHTPADDING", (0, 0), (-1, -1), 12),
        ("TOPPADDING", (0, 0), (-1, -1), 10),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 10),
    ]))
    return table


def phone(path, width=64 * mm):
    path = SCREENSHOTS / path
    iw, ih = ImageReader(str(path)).getSize()
    image = Image(str(path), width=width, height=width * ih / iw)
    frame = Table([[image]], colWidths=[width + 4], rowHeights=[image.drawHeight + 4])
    frame.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), FOREST_DARK),
        ("BOX", (0, 0), (-1, -1), 1, FOREST_DARK),
        ("LEFTPADDING", (0, 0), (-1, -1), 2),
        ("RIGHTPADDING", (0, 0), (-1, -1), 2),
        ("TOPPADDING", (0, 0), (-1, -1), 2),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 2),
    ]))
    return frame


def screenshot_pair(left, left_caption, right, right_caption):
    cells = [
        [phone(left), phone(right)],
        [P(left_caption, "Caption"), P(right_caption, "Caption")],
    ]
    table = Table(cells, colWidths=[84 * mm, 84 * mm], hAlign="CENTER")
    table.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("LEFTPADDING", (0, 0), (-1, -1), 3),
        ("RIGHTPADDING", (0, 0), (-1, -1), 3),
        ("TOPPADDING", (0, 0), (-1, -1), 0),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    return table


class NumberedCanvasMixin:
    pass


def on_page(canvas, doc):
    canvas.saveState()
    if doc.page > 1:
        canvas.setStrokeColor(LINE)
        canvas.setLineWidth(0.5)
        canvas.line(20 * mm, 17 * mm, A4[0] - 20 * mm, 17 * mm)
        canvas.setFont(BODY, 8)
        canvas.setFillColor(MUTED)
        canvas.drawString(20 * mm, 11 * mm, "Svetlanas Finanzkumpel - Funktionsübersicht")
        canvas.drawRightString(A4[0] - 20 * mm, 11 * mm, str(doc.page))
    canvas.restoreState()


def build():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    doc = BaseDocTemplate(
        str(OUTPUT), pagesize=A4,
        leftMargin=20 * mm, rightMargin=20 * mm,
        topMargin=18 * mm, bottomMargin=22 * mm,
        title="Svetlanas Finanzkumpel - Funktionsübersicht",
        author="Projektteam Svetlanas Finanzkumpel",
        subject="Kurzer Zwischenstand und Fragen zur Datenerfassung",
    )
    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="normal")
    doc.addPageTemplates(PageTemplate(id="all", frames=frame, onPage=on_page))

    story = []

    cover_text = [
        P("Svetlanas<br/>Finanzkumpel", "CoverTitle"),
        P("Kurzer Zwischenstand zu den Grundfunktionen", "CoverSub"),
        card("Was ich gerade von dir brauche", "Bitte prüfe zuerst, ob Tagesabschlüsse, Rechnungen, Bestellungen und Unterlagen so erfasst werden, wie es zu deinem Alltag passt.", SAGE),
        Spacer(1, 10),
        P("Stand: 14. Oktober 2026", "Smallx"),
        P("Alle gezeigten Geschäftsdaten und Dateien sind synthetische Testdaten.", "Smallx"),
    ]
    cover = Table([[cover_text, phone("11-home-final.png", 67 * mm)]], colWidths=[91 * mm, 73 * mm])
    cover.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 0),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
    ]))
    story += [Spacer(1, 8 * mm), cover, PageBreak()]

    story += [P("Ziel dieses Zwischenstands", "H1x")]
    story += [P("Die App soll zuerst zuverlässig die alltäglichen Angaben und Originaldateien aufnehmen. Erst wenn dieser Teil fachlich zu deinem Arbeitsalltag passt, wird der Ablauf für das Quartalspaket vereinfacht und optisch überarbeitet.")]
    two = Table([
        [card("Jetzt prüfen", "Sind die Felder, Begriffe und Abläufe für Tagesabschlüsse, Rechnungen, Bestellungen und Unterlagen richtig und vollständig?", SAGE),
         card("Danach verbessern", "Der Bereich „Quartalspaket vorbereiten“ ist funktional angelegt, aber noch zu dicht. Er ist der nächste größere Designschritt.", colors.HexColor("#FBE4DE"), CORAL)],
    ], colWidths=[82 * mm, 82 * mm])
    two.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP"), ("LEFTPADDING", (0, 0), (-1, -1), 3), ("RIGHTPADDING", (0, 0), (-1, -1), 3)]))
    story += [two, Spacer(1, 14)]
    story += [P("Dummy-Daten in dieser Dokumentation", "H2x")]
    data = [
        [P("TAGESABSCHLUSS", "Label"), P("14.10.2026 · 742,00 EUR<br/><font color='#5A635A'>Notiz: TEST - kein echter Vorgang</font>", "CardBody")],
        [P("RECHNUNG", "Label"), P("Hygiene Sued GmbH TEST · 314,16 EUR<br/>Rechnungsnummer: HS-4711-2026-TEST", "CardBody")],
        [P("TESTDATEI", "Label"), P("F08_Rechnung_Hygienebedarf_bald-faellig_TEST.pdf<br/><font color='#5A635A'>synthetische, sichtbar als TEST gekennzeichnete PDF</font>", "CardBody")],
    ]
    dt = Table(data, colWidths=[40 * mm, 124 * mm])
    dt.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), PALE),
        ("GRID", (0, 0), (-1, -1), 0.5, LINE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 10),
        ("RIGHTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 9),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 9),
    ]))
    story += [dt, Spacer(1, 14), P("Wichtig", "H2x"), P("Die Testwerte sollen nur zeigen, wie die App mit realistischen Größenordnungen und unvollständigen Vorgängen umgeht. Sie sind keine echten Geschäftsunterlagen.")]
    story += [PageBreak()]

    story += [P("1. Tagesabschluss erfassen", "H1x")]
    story += [P("Geschäftstag und Tagesumsatz werden gemeinsam gespeichert. Ein Z-Abschlag kann als Foto oder Datei ergänzt werden; eine Notiz ist optional. Nach dem Speichern zeigt die Startseite den erledigten Tagesabschluss und den Betrag.")]
    story += [screenshot_pair("01-daily-form.png", "Eingabe mit Betrag, optionalem Z-Beleg und Notiz", "02-home-with-data.png", "Gespeicherter Tagesabschluss auf der Startseite")]
    story += [Spacer(1, 6), card("Bitte prüfen", "Sind Geschäftstag, Umsatz, Z-Abschlag und Notiz die richtigen Angaben? Gibt es mehrere Z-Abschläge pro Tag oder Tage, die ausdrücklich als Ruhetag bzw. Urlaub markiert werden sollten?", SAGE)]
    story += [PageBreak()]

    story += [P("2. Rechnungen und Bestellungen", "H1x")]
    story += [P("Die App hält Bestellung, Rechnung, Lieferung und Zahlung in einem Vorgang zusammen. Noch unbekannte Angaben dürfen fehlen und später ergänzt werden. Der Status zeigt in Alltagssprache, was bereits vorhanden ist und was noch fehlt.")]
    story += [screenshot_pair("04-invoice-form.png", "Bekannte Rechnungsdaten werden eingetragen; weitere Angaben dürfen offenbleiben", "05-invoice-detail.png", "Detailansicht mit fehlender Lieferung und offener Zahlung")]
    story += [Spacer(1, 6), card("Im Test gespeichert", "314,16 EUR · Hygiene Sued GmbH TEST · Rechnung vom 14.10.2026 · PDF-Anhang vorhanden. Lieferung und Zahlung sind bewusst noch offen.", PALE)]
    story += [PageBreak()]

    story += [P("3. Unterlagen erfassen und wiederfinden", "H1x")]
    story += [P("Neben Tagesabschlüssen, Rechnungen und Bestellungen können weitere Unterlagen abgelegt werden - etwa Kontoauszug, Kassenexport, Miete oder Strom. In der gemeinsamen Übersicht lassen sich Einträge suchen und nach Art oder Status filtern.")]
    story += [screenshot_pair("07-capture-overview.png", "Schnellauswahl für häufige Unterlagen; eigene Arten können ergänzt werden", "08-entries-overview.png", "Gemeinsame Übersicht mit Status, Betrag und Anhang")]
    story += [Spacer(1, 6), card("Bitte prüfen", "Welche Unterlagen fallen wirklich monatlich, quartalsweise oder jährlich an? Bekommst du sie als PDF, Foto, CSV, ZIP oder in einem anderen Format?", SAGE)]
    story += [PageBreak()]

    story += [P("4. Quartalspaket - bewusst noch in Arbeit", "H1x")]
    story += [card("Aktueller Stand", "Die Vorschau erkennt fehlende Unterlagen, zählt Tagesabschlüsse und Anhänge und bereitet Übersicht und Tabelle vor. Der Bildschirm ist im Moment noch zu lang und zu unübersichtlich.", colors.HexColor("#FBE4DE"), CORAL), Spacer(1, 10)]
    story += [screenshot_pair("09-quarter-top.png", "Oben: Hinweise auf fehlende Unterlagen und Tagesabschlüsse ohne Z-Beleg", "10-quarter-lower.png", "Unten: Paketinhalt, Rechnungsanhänge und Erstellen-Aktion")]
    story += [Spacer(1, 6), P("Die Screens zeigen nur die Richtung. Der Quartalspaket-Ablauf wird erst gepolished, wenn die Datenerfassung fachlich bestätigt ist.", "Smallx")]
    story += [PageBreak()]

    story += [P("Deine Rückmeldung", "H1x"), P("Ein kurzer erster Eindruck reicht. Besonders hilfreich wären Antworten auf diese Fragen:")]
    questions = [
        "1.&nbsp;&nbsp;Passen die gezeigten Grundabläufe zu deinem Alltag?",
        "2.&nbsp;&nbsp;Welche Angabe fehlt bei Tagesabschlüssen, Rechnungen, Bestellungen oder Unterlagen?",
        "3.&nbsp;&nbsp;Sind die Testdaten realistisch, oder läuft etwas bei dir regelmäßig anders?",
        "4.&nbsp;&nbsp;Welche Dokumente bekommst du tatsächlich - und in welchem Dateiformat?",
        "5.&nbsp;&nbsp;Welche Bezeichnungen oder Status sind unverständlich?",
        "6.&nbsp;&nbsp;Welche Sonderfälle kommen vor, zum Beispiel Teilzahlungen, Gutschriften oder mehrere Dateien?",
    ]
    story += [P(q, "Question") for q in questions]
    story += [Spacer(1, 8), card("Beispiele zum Testen", "Anonymisierte oder geschwärzte Rechnungen, Belege und Kassenexporte wären sehr hilfreich. Bitte keine ungeschwärzten Kunden-, Konto- oder anderen sensiblen Daten per WhatsApp senden.", SAGE)]
    story += [Spacer(1, 14), P("Nächster Schritt", "H2x"), P("Wenn die Erfassung so passt, wird als Nächstes das Quartalspaket klarer gegliedert, sprachlich vereinfacht und visuell überarbeitet.")]
    story += [Spacer(1, 18), P("Danke fürs Anschauen und Ausprobieren!", "CoverSub")]

    doc.build(story)
    print(OUTPUT)


class AnnotatedImage(Flowable):
    """Image with optional slim notes beside it and dashed leader lines."""

    def __init__(self, path, width, annotations=(), border=FOREST_DARK):
        super().__init__()
        self.path = Path(path)
        iw, ih = ImageReader(str(self.path)).getSize()
        self.image_width = width
        self.image_height = width * ih / iw
        self.annotations = annotations
        self.border = border
        self.note_gap = 2.5 * mm if annotations else 0
        self.note_width = 15.5 * mm if annotations else 0
        self.width = self.image_width + 4 + self.note_gap + self.note_width
        self.height = self.image_height + 4

    def draw(self):
        canvas = self.canv
        canvas.setFillColor(self.border)
        canvas.rect(0, 0, self.image_width + 4, self.height, stroke=0, fill=1)
        canvas.drawImage(
            str(self.path), 2, 2,
            width=self.image_width, height=self.image_height,
            preserveAspectRatio=True, mask="auto",
        )
        for item in self.annotations:
            tx = 2 + item["target"][0] * self.image_width
            ty = 2 + (1 - item["target"][1]) * self.image_height
            accent = item.get("color", FOREST)
            font_size = item.get("font_size", 6.4)
            note_x = self.image_width + 4 + self.note_gap
            lines = item["text"].split("\n")
            line_height = font_size + 2
            note_y = max(5, min(self.height - len(lines) * line_height - 2, ty + line_height / 2))

            canvas.setStrokeColor(accent)
            canvas.setLineWidth(0.75)
            canvas.setDash(2.5, 2)
            canvas.line(tx, ty, note_x - 2, ty)
            canvas.setDash()
            canvas.circle(tx, ty, 1.6, stroke=1, fill=0)
            canvas.setFillColor(accent)
            canvas.setFont(BODY_BOLD, font_size)
            for line_index, line in enumerate(lines):
                canvas.drawString(note_x, note_y - line_index * line_height, line)


def annotated(path, width=64 * mm, annotations=()):
    source = Path(path)
    if not source.is_absolute():
        source = SCREENSHOTS / source
    return AnnotatedImage(source, width, annotations)


def visual_pair(left, left_caption, right, right_caption, col_width=84 * mm):
    table = Table(
        [[left, right], [P(left_caption, "Caption"), P(right_caption, "Caption")]],
        colWidths=[col_width, col_width], hAlign="CENTER",
    )
    table.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("LEFTPADDING", (0, 0), (-1, -1), 3),
        ("RIGHTPADDING", (0, 0), (-1, -1), 3),
        ("TOPPADDING", (0, 0), (-1, -1), 0),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    return table


def build_v2():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    doc = BaseDocTemplate(
        str(OUTPUT), pagesize=A4,
        leftMargin=20 * mm, rightMargin=20 * mm,
        topMargin=18 * mm, bottomMargin=22 * mm,
        title="Svetlanas Finanzkumpel - Funktionsübersicht",
        author="Projektteam Svetlanas Finanzkumpel",
        subject="Kurzer Zwischenstand der Grundfunktionen",
    )
    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="normal-v2")
    doc.addPageTemplates(PageTemplate(id="all-v2", frames=frame, onPage=on_page))
    story = []

    # Cover: show the useful, unfinished state rather than an already completed day.
    cover_text = [
        P("Svetlanas<br/>Finanzkumpel", "CoverTitle"),
        P("Kurzer Überblick über den aktuellen App-Stand", "CoverSub"),
        card(
            "Wofür die App gedacht ist",
            "Tagesabschlüsse, Rechnungen, Bestellungen und weitere Unterlagen an einem Ort sammeln - damit später nichts für das Quartal fehlt.",
            SAGE,
        ),
        Spacer(1, 10),
        P("Stand: 14. Oktober 2026", "Smallx"),
        P("Alle gezeigten Geschäftsdaten und Dateien sind synthetische Testdaten.", "Smallx"),
    ]
    cover_phone = annotated("00-current.png", 67 * mm)
    cover = Table([[cover_text, cover_phone]], colWidths=[91 * mm, 73 * mm])
    cover.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 0),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
    ]))
    story += [Spacer(1, 8 * mm), cover, PageBreak()]

    story += [P("Die drei wichtigsten Bereiche", "H1x")]
    nav = Table([
        [card("Start", "Zeigt, was heute noch zu tun ist, welche Zahlungen Aufmerksamkeit brauchen und bietet schnelle Einstiege.", SAGE),
         card("+ Erfassen", "Hier werden Tagesabschluss, Rechnung, Bestellung und alle weiteren Unterlagen neu aufgenommen.", PALE),
         card("Einträge", "Gemeinsame Liste zum Suchen, Filtern, Öffnen und späteren Ergänzen bereits erfasster Vorgänge.", PALE)],
    ], colWidths=[55 * mm, 55 * mm, 55 * mm])
    nav.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 3),
        ("RIGHTPADDING", (0, 0), (-1, -1), 3),
    ]))
    story += [nav, Spacer(1, 13)]
    story += [visual_pair(
        annotated("07-capture-overview.png"),
        "Über das Plus werden neue Daten und Unterlagen erfasst",
        annotated("08-entries-overview.png"),
        "Unter Einträge ist alles gemeinsam auffindbar",
    )]
    story += [PageBreak()]

    story += [P("1. Tagesabschluss erfassen", "H1x")]
    story += [P("Der Tagesabschluss speichert Geschäftstag und Tagesumsatz. Ein Z-Abschlag kann direkt als Foto oder Datei ergänzt werden; eine kurze Notiz ist optional. Nach dem Speichern zeigt die Startseite den erledigten Zustand und den Betrag.")]
    daily_form = annotated("01-daily-form.png")
    daily_saved = annotated("02-home-with-data.png")
    story += [visual_pair(
        daily_form, "Eingabe mit Betrag, optionalem Z-Beleg und Notiz",
        daily_saved, "Nach dem Speichern erscheint der Tagesabschluss auf Start",
    )]
    story += [Spacer(1, 6), card("Der Testfall", "14.10.2026 · 742,00 EUR · Notiz: TEST - kein echter Vorgang. Der Z-Abschlag wurde in diesem Beispiel bewusst noch nicht ergänzt.", PALE)]
    story += [PageBreak()]

    story += [P("2. Rechnung und Bestellung", "H1x")]
    story += [P("Eine Bestellung kann zuerst mit den bereits bekannten Angaben gespeichert werden. Sobald die Rechnung kommt, wird oben über „Vorhandenen Eintrag auswählen“ genau diese Bestellung gewählt. So bleiben Bestellung, Rechnung, Lieferung, Zahlung und Dateien zusammen.")]
    invoice_form = annotated("04-invoice-form.png", annotations=[{
        "text": "Bestellung\nverknüpfen",
        "target": (0.80, 0.28),
        "color": FOREST,
    }])
    invoice_detail = annotated("05-invoice-detail.png")
    story += [visual_pair(
        invoice_form, "Rechnung erfassen oder mit einer vorhandenen Bestellung verbinden",
        invoice_detail, "Die Detailansicht zeigt fehlende Lieferung und offene Zahlung",
    )]
    story += [Spacer(1, 6), card("Der Testfall", "Hygiene Sued GmbH TEST · 314,16 EUR · Rechnungsnummer HS-4711-2026-TEST · synthetische PDF angehängt.", PALE)]
    story += [PageBreak()]

    story += [P("3. Weitere Unterlagen und Testdateien", "H1x")]
    story += [P("Weiter unten unter Erfassen stehen die quartals- und jahresbezogenen Dokumentarten. Neben Kontoauszug und Kassenexport können zum Beispiel Miete, Strom, Gas, Lohnunterlagen, GEMA, Rundfunk und BGW abgelegt werden. Eine eigene Art lässt sich ebenfalls anlegen.")]
    test_pdf = ROOT / "tmp" / "pdfs" / "test-document-render" / "rechnung-test.png"
    lower_capture = annotated("12-capture-lower.png")
    pdf_preview = annotated(test_pdf, 64 * mm)
    story += [visual_pair(
        lower_capture, "Vollständige Auswahl der weiteren Unterlagen",
        pdf_preview, "Beispiel der angehängten synthetischen Testrechnung",
    )]
    story += [Spacer(1, 5), P("Die deutliche TEST-Kennzeichnung bleibt im Originaldokument sichtbar. Damit kann die Ablage realitätsnah geprüft werden, ohne einen echten Geschäftsbeleg zu verwenden.", "Smallx")]
    story += [PageBreak()]

    story += [P("4. Quartalspaket vorbereiten", "H1x")]
    story += [P("Dieser Bereich soll später alle Einträge eines Quartals prüfen und für die Weitergabe zusammenstellen. Er zeigt fehlende Unterlagen, zählt Tagesabschlüsse und Originaldateien und bereitet eine Übersicht sowie eine Tabelle vor.")]
    story += [card("Aktuell noch in Überarbeitung", "Der Ablauf funktioniert als Vorschau, ist aber noch zu lang und zu unübersichtlich. Sobald die Datenerfassung fachlich passt, wird dieser Bereich klarer gegliedert und optisch gepolished.", colors.HexColor("#FBE4DE"), CORAL), Spacer(1, 10)]
    story += [visual_pair(
        annotated("09-quarter-top.png"),
        "Oben: fehlende Unterlagen und Tagesabschlüsse ohne Z-Abschlag",
        annotated("10-quarter-lower.png"),
        "Unten: vorgesehener Paketinhalt und Erstellen-Aktion",
    )]
    story += [Spacer(1, 6), P("Die Ansicht zeigt den geplanten Umfang, noch nicht die endgültige Bedienung.", "Smallx")]

    doc.build(story)
    print(OUTPUT)


if __name__ == "__main__":
    build_v2()
