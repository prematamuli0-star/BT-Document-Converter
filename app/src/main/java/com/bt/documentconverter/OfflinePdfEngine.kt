package com.bt.documentconverter

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class OfflinePdfEngine(private val context: Context) {
    init { PDFBoxResourceLoader.init(context.applicationContext) }

    fun imagesToPdf(uris: List<Uri>): File {
        require(uris.isNotEmpty()) { "Select at least one image." }
        val pdf = PdfDocument()
        try {
            uris.forEachIndexed { index, uri ->
                val bitmap = context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Cannot open image." }
                    BitmapFactory.decodeStream(input)
                } ?: error("Unsupported image.")
                try {
                    val info = PdfDocument.PageInfo.Builder(
                        bitmap.width.coerceAtLeast(1), bitmap.height.coerceAtLeast(1), index + 1
                    ).create()
                    val page = pdf.startPage(info)
                    page.canvas.drawBitmap(bitmap, null, Rect(0, 0, info.pageWidth, info.pageHeight), null)
                    pdf.finishPage(page)
                } finally { bitmap.recycle() }
            }
            return saveAndroidPdf(pdf, "images")
        } finally { pdf.close() }
    }

    fun textToPdf(text: String): File {
        require(text.isNotBlank()) { "Enter some text first." }
        val pdf = PdfDocument()
        try {
            val width = 595
            val height = 842
            val margin = 42f
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textSize = 11f
            }
            val lines = mutableListOf<String>()
            text.lines().forEach { paragraph ->
                if (paragraph.isBlank()) lines.add("")
                else {
                    var line = ""
                    paragraph.split(Regex("\\s+")).forEach { word ->
                        val candidate = if (line.isEmpty()) word else "$line $word"
                        if (paint.measureText(candidate) > width - 2 * margin && line.isNotEmpty()) {
                            lines.add(line); line = word
                        } else line = candidate
                    }
                    lines.add(line)
                }
            }
            var index = 0
            var pageNumber = 1
            while (index < lines.size) {
                val info = PdfDocument.PageInfo.Builder(width, height, pageNumber++).create()
                val page = pdf.startPage(info)
                var y = margin + 16f
                while (index < lines.size && y < height - margin) {
                    page.canvas.drawText(lines[index++], margin, y, paint)
                    y += 16f
                }
                pdf.finishPage(page)
            }
            return saveAndroidPdf(pdf, "text")
        } finally { pdf.close() }
    }

    fun mergePdfs(inputs: List<File>): File {
        require(inputs.size >= 2) { "Select at least two PDF files." }
        val output = newOutput("merged")
        val merger = PDFMergerUtility()
        inputs.forEach {
            require(it.isFile && it.length() > 0) { "Invalid PDF file." }
            merger.addSource(it)
        }
        merger.destinationFileName = output.absolutePath
        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        require(output.exists() && output.length() > 0) { "PDF merge failed." }
        return output
    }

    fun extractPage(input: File, pageNumber: Int): File {
        PDDocument.load(input).use { source ->
            require(pageNumber in 1..source.numberOfPages) { "Page number is out of range." }
            PDDocument().use { result ->
                result.importPage(source.getPage(pageNumber - 1))
                val output = newOutput("extracted_page")
                result.save(output)
                return output
            }
        }
    }

    fun rotateAllPages(input: File, degrees: Int): File {
        require(degrees in listOf(90, 180, 270)) { "Rotation must be 90, 180, or 270 degrees." }
        PDDocument.load(input).use { document ->
            for (i in 0 until document.numberOfPages) {
                val page = document.getPage(i)
                page.rotation = (page.rotation + degrees) % 360
            }
            val output = newOutput("rotated")
            document.save(output)
            return output
        }
    }

    fun deletePage(input: File, pageNumber: Int): File {
        PDDocument.load(input).use { document ->
            require(document.numberOfPages > 1) { "PDF must contain at least one page." }
            require(pageNumber in 1..document.numberOfPages) { "Invalid page number." }
            document.removePage(pageNumber - 1)
            val output = newOutput("page_deleted")
            document.save(output)
            return output
        }
    }

    fun addWatermark(input: File, watermark: String): File {
        require(watermark.isNotBlank()) { "Watermark cannot be empty." }
        PDDocument.load(input).use { document ->
            for (page in document.pages) {
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 30f)
                    stream.setNonStrokingColor(160, 160, 160)
                    val box = page.mediaBox
                    stream.newLineAtOffset(box.width * 0.20f, box.height * 0.50f)
                    stream.showText(watermark.take(80))
                    stream.endText()
                }
            }
            val output = newOutput("watermarked")
            document.save(output)
            return output
        }
    }

    fun optimizePdf(input: File): File {
        PDDocument.load(input).use { document ->
            val output = newOutput("optimized")
            document.save(output)
            require(output.exists() && output.length() > 0) { "PDF rewrite failed." }
            return output
        }
    }

    // Crop all pages equally. Margins are PDF points (72 points = 1 inch).
    fun cropPdf(input: File, left: Float, top: Float, right: Float, bottom: Float): File {
        val margins = listOf(left, top, right, bottom)
        require(margins.all { it.isFinite() && it >= 0f }) { "Crop margins must be non-negative." }
        PDDocument.load(input).use { document ->
            for (page in document.pages) {
                val box = page.cropBox
                val width = box.width - left - right
                val height = box.height - top - bottom
                require(width > 1f && height > 1f) { "Crop margins are too large." }
                page.cropBox = PDRectangle(
                    box.lowerLeftX + left,
                    box.lowerLeftY + bottom,
                    width,
                    height
                )
            }
            val output = newOutput("cropped")
            document.save(output)
            return output
        }
    }

    // Offline .xlsx to PDF. Reads the first worksheet and common cell types.
    // Formatting, charts, images, formulas recalculation and legacy .xls are not supported.
    fun excelXlsxToPdf(input: File): File {
        require(input.extension.equals("xlsx", ignoreCase = true)) {
            "Please select an .xlsx Excel file. Legacy .xls is not supported offline."
        }
        ZipFile(input).use { zip ->
            val sharedStrings = readSharedStrings(zip)
            val sheet = zip.getEntry("xl/worksheets/sheet1.xml")
                ?: error("The workbook has no first worksheet.")
            val rows = mutableListOf<List<String>>()
            zip.getInputStream(sheet).use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, "UTF-8")
                var event = parser.eventType
                var currentRow: MutableList<String>? = null
                var cellType = ""
                var cellValue = ""
                var inValue = false
                var maxColumn = 0
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "row" -> currentRow = mutableListOf()
                            "c" -> {
                                cellType = parser.getAttributeValue(null, "t") ?: ""
                                val ref = parser.getAttributeValue(null, "r") ?: ""
                                maxColumn = maxOf(maxColumn, columnIndex(ref) + 1)
                                cellValue = ""
                            }
                            "v", "t" -> inValue = true
                        }
                        XmlPullParser.TEXT -> if (inValue) cellValue += parser.text
                        XmlPullParser.END_TAG -> when (parser.name) {
                            "v", "t" -> inValue = false
                            "c" -> {
                                val value = if (cellType == "s") {
                                    sharedStrings.getOrNull(cellValue.toIntOrNull() ?: -1) ?: ""
                                } else cellValue
                                currentRow?.add(value)
                            }
                            "row" -> {
                                rows.add(currentRow ?: mutableListOf())
                                currentRow = null
                            }
                        }
                    }
                    event = parser.next()
                }
            }
            require(rows.isNotEmpty()) { "The first worksheet is empty." }
            return renderRowsToPdf(rows)
        }
    }

    private fun readSharedStrings(zip: ZipFile): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        val result = mutableListOf<String>()
        zip.getInputStream(entry).use { stream ->
            val parser = Xml.newPullParser()
            parser.setInput(stream, "UTF-8")
            var event = parser.eventType
            var inSi = false
            var inText = false
            val current = StringBuilder()
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "si" -> { inSi = true; current.setLength(0) }
                        "t" -> if (inSi) inText = true
                    }
                    XmlPullParser.TEXT -> if (inText) current.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "t" -> inText = false
                        "si" -> { result.add(current.toString()); inSi = false }
                    }
                }
                event = parser.next()
            }
        }
        return result
    }

    private fun columnIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        var value = 0
        for (char in letters) value = value * 26 + (char - 'A' + 1)
        return (value - 1).coerceAtLeast(0)
    }

    private fun renderRowsToPdf(rows: List<List<String>>): File {
        val pageWidth = 842
        val pageHeight = 595
        val margin = 24f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 8f
        }
        val columnCount = (rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
        val columnWidth = (pageWidth - 2 * margin) / columnCount
        val pdf = PdfDocument()
        try {
            var rowIndex = 0
            var pageNo = 1
            while (rowIndex < rows.size) {
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo++).create()
                val page = pdf.startPage(info)
                var y = margin + 14f
                while (rowIndex < rows.size && y < pageHeight - margin) {
                    val row = rows[rowIndex]
                    for (col in 0 until columnCount) {
                        val raw = row.getOrNull(col).orEmpty()
                        val text = if (raw.length > 28) raw.take(27) + "…" else raw
                        page.canvas.drawText(text, margin + col * columnWidth, y, paint)
                    }
                    page.canvas.drawLine(margin, y + 4f, pageWidth - margin, y + 4f, paint)
                    y += 17f
                    rowIndex++
                }
                pdf.finishPage(page)
            }
            return saveAndroidPdf(pdf, "excel")
        } finally { pdf.close() }
    }

    private fun saveAndroidPdf(pdf: PdfDocument, prefix: String): File {
        val output = newOutput(prefix)
        FileOutputStream(output).use { pdf.writeTo(it) }
        require(output.exists() && output.length() > 0) { "PDF creation failed." }
        return output
    }

    private fun newOutput(prefix: String) =
        File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.pdf")
}
