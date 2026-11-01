package de.quartalsmappe.app.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import de.quartalsmappe.app.BuildConfig
import de.quartalsmappe.app.data.AppStore
import de.quartalsmappe.app.model.InvoiceCase
import de.quartalsmappe.app.model.Quarter
import de.quartalsmappe.app.model.centsToGerman
import de.quartalsmappe.app.model.formatGerman
import de.quartalsmappe.app.model.signals
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object QuarterExporter {
    fun createPackage(
        context: Context,
        store: AppStore,
        quarter: Quarter,
        includeInvoices: Boolean,
    ): Uri {
        val manifest = QuarterExportManifest.create(
            quarter, includeInvoices, store.dailyCloses, store.invoices, store.documents,
            isAvailable = { store.fileFor(it).let { file -> file.isFile && file.canRead() } },
        )
        check(manifest.missingFiles.isEmpty()) {
            val names = manifest.missingFiles.joinToString(", ") { it.document.originalFileName }
            "Originaldateien nicht verfügbar: $names. Stelle diese Dateien aus einer Sicherung wieder her. Das Paket wurde nicht erstellt."
        }
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val destination = File.createTempFile("Quartalsmappe_${quarter.year}_Q${quarter.number}_", ".zip", exportDir)
        val pdf = File.createTempFile("Uebersicht_", ".pdf", exportDir)
        val csv = File.createTempFile("Tagesumsaetze_", ".csv", exportDir)
        try {
            createOverviewPdf(pdf, quarter, manifest.dailyCloses.size, manifest.invoices,
                manifest.generalDocumentOverviewLines(store::documentTypeLabel))
            csv.writeText(buildCsv(manifest), Charsets.UTF_8)
            ZipOutputStream(FileOutputStream(destination)).use { zip ->
                zip.addFile(pdf, manifest.generatedFiles[0])
                zip.addFile(csv, manifest.generatedFiles[1])
                manifest.files.forEach { entry ->
                    val source = store.fileFor(entry.document)
                    check(source.isFile && source.canRead()) {
                        "${entry.document.originalFileName} ist nicht mehr verfügbar. Bitte prüfe die Originaldatei und erstelle das Paket erneut."
                    }
                    zip.addFile(source, entry.archivePath)
                }
            }
            return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", destination)
        } catch (error: Exception) {
            destination.delete()
            throw error
        } finally {
            pdf.delete()
            csv.delete()
        }
    }

    private fun buildCsv(manifest: QuarterExportManifest): String = buildString {
        appendLine("Datum;Umsatz laut Z-Abschlag (EUR);Notiz;Z-Beleg vorhanden")
        manifest.dailyCloses.forEach { close ->
            val amount = "${close.zAmountCents / 100},${(close.zAmountCents % 100).toString().padStart(2, '0')}"
            appendLine(
                listOf(
                    close.businessDate.formatGerman(),
                    amount,
                    close.note.csvCell(),
                    if (manifest.hasZReport(close.id)) "Ja" else "Nein",
                ).joinToString(";")
            )
        }
    }

    private fun createOverviewPdf(
        file: File,
        quarter: Quarter,
        closeCount: Int,
        invoices: List<InvoiceCase>,
        generalDocumentLines: List<String>,
    ) {
        val lines = buildList {
            add("Quartalsmappe – Übersicht")
            add(quarter.label)
            add("")
            add("$closeCount Tagesabschlüsse erfasst")
            add("${invoices.size} Rechnungsvorgänge betreffen diesen Zeitraum")
            add("")
            add("Rechnungen")
            invoices.sortedBy { it.dueDate ?: it.invoiceDate ?: it.orderDate }.forEach { invoice ->
                add("${invoice.supplier} · ${centsToGerman(invoice.amountCents)} · ${invoice.signals(quarter.end).label}")
                val dates = listOfNotNull(
                    invoice.invoiceDate?.let { "Rechnung ${it.formatGerman()}" },
                    invoice.deliveryDate?.let { "Lieferung ${it.formatGerman()}" },
                    invoice.paidDate?.let { "bezahlt ${it.formatGerman()}" },
                ).joinToString(" · ")
                if (dates.isNotBlank()) add("  $dates")
            }
            if (generalDocumentLines.isNotEmpty()) {
                add("")
                add("Weitere Einträge und Originaldateien")
                addAll(generalDocumentLines)
            }
            add("")
            add("Hinweis: Diese Übersicht prüft nur, welche Unterlagen in der App hinterlegt sind.")
            add("Sie enthält keine steuerliche Bewertung oder Zuordnung.")
        }

        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 48f
        val lineHeight = 22f
        val linesPerPage = ((pageHeight - margin * 2) / lineHeight).toInt()
        val measuringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val wrappedLines = lines.flatMapIndexed { index, line ->
            measuringPaint.textSize = if (index == 0) 20f else 12f
            measuringPaint.isFakeBoldText = index == 0 || line == "Rechnungen" || line == "Weitere Einträge und Originaldateien"
            wrapPdfLine(line, measuringPaint, pageWidth - margin * 2)
        }
        wrappedLines.chunked(linesPerPage).forEachIndexed { pageIndex, pageLines ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 48, 43)
                textSize = 12f
            }
            pageLines.forEachIndexed { lineIndex, line ->
                if (pageIndex == 0 && lineIndex == 0) {
                    paint.textSize = 20f
                    paint.isFakeBoldText = true
                } else {
                    paint.textSize = 12f
                    paint.isFakeBoldText = line == "Rechnungen" || line == "Weitere Einträge und Originaldateien"
                }
                canvas.drawText(line, margin, margin + lineIndex * lineHeight, paint)
            }
            document.finishPage(page)
        }
        FileOutputStream(file).use(document::writeTo)
        document.close()
    }
}

/** Preserve complete user-defined names and file paths instead of clipping long text. */
private fun wrapPdfLine(line: String, paint: Paint, width: Float): List<String> {
    if (line.isEmpty()) return listOf("")
    return buildList {
        var remaining = line
        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, width, null).coerceAtLeast(1)
            add(remaining.take(count))
            remaining = remaining.drop(count)
        }
    }
}

private fun ZipOutputStream.addFile(file: File, path: String) {
    putNextEntry(ZipEntry(path))
    file.inputStream().use { it.copyTo(this) }
    closeEntry()
}

private fun String.csvCell(): String = "\"${replace("\"", "\"\"")}\""

