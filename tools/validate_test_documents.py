from __future__ import annotations

import csv
import hashlib
import io
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from pypdf import PdfReader


ROOT = Path(__file__).resolve().parents[1]
PDF_DIR = ROOT / "output" / "pdf"
IMAGE_DIR = ROOT / "output" / "images"
EXPORT_ZIP = ROOT / "output" / "kassenexport" / "F25_DSFinV-K_Kassenexport_2026_Q4_TEST.zip"
PACKAGE = ROOT / "output" / "Quartalsmappe_Testdaten_Waxingstudio_2026.zip"
TEST_REPORT = ROOT / "output" / "TESTBERICHT.md"
RENDER_DIR = ROOT / "tmp" / "pdfs" / "rendered"
CONTACT_SHEET = ROOT / "tmp" / "pdfs" / "contact_sheet.png"
EXPECTED_PDFS = 21
EXPECTED_IMAGES = 3
EXPECTED_EXPORT_MEMBERS = {
    "README_TEST.txt",
    "cashregister.csv",
    "location.csv",
    "transactions.csv",
    "payment.csv",
    "lines.csv",
}


def fail(message: str) -> None:
    raise AssertionError(message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def find_pdftoppm() -> str:
    candidates = [
        shutil.which("pdftoppm"),
        r"C:\Users\maria\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\poppler\Library\bin\pdftoppm.exe",
    ]
    for item in candidates:
        if item and Path(item).exists():
            return str(item)
    fail("pdftoppm not found")


def validate_pdfs() -> tuple[list[Path], int]:
    # The output folder may also contain generated user documentation. Only the
    # deterministic Fxx fixtures belong to this regression suite.
    pdfs = sorted(PDF_DIR.glob("F*_TEST.pdf"))
    if len(pdfs) != EXPECTED_PDFS:
        fail(f"Expected {EXPECTED_PDFS} PDFs, found {len(pdfs)}")
    page_count = 0
    for path in pdfs:
        reader = PdfReader(str(path))
        if not reader.pages:
            fail(f"No pages in {path.name}")
        page_count += len(reader.pages)
        text = "\n".join((page.extract_text() or "") for page in reader.pages)
        if "TESTDOKUMENT" not in text:
            fail(f"Missing test label in {path.name}")
        if len(text.strip()) < 120:
            fail(f"Suspiciously little text in {path.name}")
        box = reader.pages[0].mediabox
        if round(float(box.width)) != 595 or round(float(box.height)) != 842:
            fail(f"Unexpected page size in {path.name}: {box.width} x {box.height}")
    duplicate_a = PDF_DIR / "F08_Rechnung_Hygienebedarf_bald-faellig_TEST.pdf"
    duplicate_b = PDF_DIR / "F14_Rechnung_Hygienebedarf_DUPLIKATNAME_TEST.pdf"
    if sha256(duplicate_a) != sha256(duplicate_b):
        fail("Duplicate test PDFs are not byte-identical")
    return pdfs, page_count


def validate_images() -> list[Path]:
    images = sorted(IMAGE_DIR.glob("*.jpg"))
    if len(images) != EXPECTED_IMAGES:
        fail(f"Expected {EXPECTED_IMAGES} JPGs, found {len(images)}")
    for path in images:
        with Image.open(path) as image:
            if image.width < 1000 or image.height < 1400:
                fail(f"Image too small: {path.name} {image.size}")
            if image.mode != "RGB":
                fail(f"Unexpected mode in {path.name}: {image.mode}")
            image.verify()
    return images


def parse_semicolon_csv(raw: bytes) -> list[list[str]]:
    text = raw.decode("utf-8-sig")
    return list(csv.reader(io.StringIO(text), delimiter=";"))


def validate_export_zip() -> None:
    with zipfile.ZipFile(EXPORT_ZIP) as archive:
        members = set(archive.namelist())
        if members != EXPECTED_EXPORT_MEMBERS:
            fail(f"Unexpected structured export contents: {sorted(members)}")
        transactions = parse_semicolon_csv(archive.read("transactions.csv"))
        payments = parse_semicolon_csv(archive.read("payment.csv"))
        lines = parse_semicolon_csv(archive.read("lines.csv"))
        transaction_total = sum(float(row[5]) for row in transactions[1:])
        payment_total = sum(float(row[2]) for row in payments[1:])
        line_total = sum(float(row[5]) for row in lines[1:])
        if not (transaction_total == payment_total == line_total == 100.0):
            fail("Structured export totals do not reconcile")


def validate_package(individual_files: list[Path]) -> None:
    with zipfile.ZipFile(PACKAGE) as archive:
        names = set(archive.namelist())
        expected_names = {path.name for path in individual_files} | {
            "F00_TESTDATEN-LEITFADEN.md",
            "TESTBERICHT.md",
            "MANIFEST_SHA256.csv",
        }
        if names != expected_names:
            missing = sorted(expected_names - names)
            extra = sorted(names - expected_names)
            fail(f"Package mismatch; missing={missing}, extra={extra}")
        manifest = parse_semicolon_csv(archive.read("MANIFEST_SHA256.csv"))
        manifest_map = {row[0]: row[1] for row in manifest[1:]}
        for path in individual_files:
            digest = hashlib.sha256(archive.read(path.name)).hexdigest()
            if manifest_map.get(path.name) != digest:
                fail(f"Manifest mismatch for {path.name}")


def render_pdfs(pdfs: list[Path]) -> list[Path]:
    pdftoppm = find_pdftoppm()
    if RENDER_DIR.exists():
        shutil.rmtree(RENDER_DIR)
    RENDER_DIR.mkdir(parents=True, exist_ok=True)
    rendered = []
    for path in pdfs:
        prefix = RENDER_DIR / path.stem
        subprocess.run([pdftoppm, "-png", "-r", "90", str(path), str(prefix)], check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        pages = sorted(RENDER_DIR.glob(path.stem + "-*.png"))
        if not pages:
            fail(f"No render for {path.name}")
        rendered.extend(pages)
    return rendered


def create_contact_sheet(rendered: list[Path], images: list[Path]) -> None:
    sources = rendered + images
    thumb_w, thumb_h = 340, 480
    label_h = 54
    columns = 4
    rows = (len(sources) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * thumb_w, rows * (thumb_h + label_h)), "white")
    draw = ImageDraw.Draw(sheet)
    font_path = Path("C:/Windows/Fonts/arial.ttf")
    font = ImageFont.truetype(str(font_path), 16) if font_path.exists() else ImageFont.load_default()
    for index, path in enumerate(sources):
        with Image.open(path) as source:
            preview = source.convert("RGB")
            preview.thumbnail((thumb_w - 24, thumb_h - 20), Image.Resampling.LANCZOS)
            col = index % columns
            row = index // columns
            x = col * thumb_w + (thumb_w - preview.width) // 2
            y = row * (thumb_h + label_h) + 8
            sheet.paste(preview, (x, y))
            label = path.stem
            if len(label) > 38:
                label = label[:35] + "..."
            draw.text((col * thumb_w + 10, row * (thumb_h + label_h) + thumb_h + 5), label, fill="#222222", font=font)
    CONTACT_SHEET.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(CONTACT_SHEET, "PNG", optimize=True)


def main() -> None:
    pdfs, page_count = validate_pdfs()
    images = validate_images()
    validate_export_zip()
    individual_files = pdfs + images + [EXPORT_ZIP]
    validate_package(individual_files)
    rendered = render_pdfs(pdfs)
    if len(rendered) != page_count:
        fail(f"Rendered {len(rendered)} pages but PDFs contain {page_count}")
    create_contact_sheet(rendered, images)
    print(f"PASS: {len(pdfs)} PDFs / {page_count} pages")
    print(f"PASS: {len(images)} JPG files")
    print("PASS: structured export totals and members")
    print(f"PASS: package manifest for {len(individual_files)} individual files")
    print(f"CONTACT_SHEET={CONTACT_SHEET}")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise
