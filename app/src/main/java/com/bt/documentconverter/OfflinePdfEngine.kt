
package com.bt.documentconverter

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.FileOutputStream

class OfflinePdfEngine(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    // IMAGE TO PDF
    fun imagesToPdf(uris: List<Uri>): File {
        require(uris.isNotEmpty()) {
            "Select images first."
        }

        val pdf = PdfDocument()

        try {
            uris.forEachIndexed { index, uri ->

                val bitmap = context.contentResolver
                    .openInputStream(uri).use { input ->
                        requireNotNull(input) {
                            "Cannot open image."
                        }
                        BitmapFactory.decodeStream(input)
                    } ?: error("Unsupported image.")

                try {
                    val info = PdfDocument.PageInfo.Builder(
                        bitmap.width.coerceAtLeast(1),
                        bitmap.height.coerceAtLeast(1),
                        index + 1
                    ).create()

                    val page = pdf.startPage(info)

                    page.canvas.drawBitmap(
                        bitmap,
                        null,
                        Rect(
                            0, 0,
                            info.pageWidth,
                            info.pageHeight
                        ),
                        null
                    )

                    pdf.finishPage(page)

                } finally {
                    bitmap.recycle()
                }
            }

            return savePdf(pdf, "images")

        } finally {
            pdf.close()
        }
    }

    // TEXT TO PDF
    fun textToPdf(text: String): File {
        require(text.isNotBlank()) {
            "Text is empty."
        }

        val pdf = PdfDocument()

        try {
            val width = 595
            val height = 842
            val margin = 42f

            val paint = android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = android.graphics.Color.BLACK
                textSize = 11f
            }

            val lines = mutableListOf<String>()

            text.lines().forEach { paragraph ->
                var line = ""

                paragraph.split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }
                    .forEach { word ->

                        val next = if (line.isEmpty()) {
                            word
                        } else {
                            "$line $word"
                        }

                        if (
                            paint.measureText(next) >
                            width - 2 * margin &&
                            line.isNotEmpty()
                        ) {
                            lines.add(line)
                            line = word
                        } else {
                            line = next
                        }
                    }

                lines.add(line)
            }

            var index = 0
            var pageNumber = 1

            while (index < lines.size) {

                val info = PdfDocument.PageInfo.Builder(
                    width,
                    height,
                    pageNumber++
                ).create()

                val page = pdf.startPage(info)
                var y = margin + 16f

                while (
                    index < lines.size &&
                    y < height - margin
                ) {
                    page.canvas.drawText(
                        lines[index++],
                        margin,
                        y,
                        paint
                    )

                    y += 16f
                }

                pdf.finishPage(page)
            }

            return savePdf(pdf, "text")

        } finally {
            pdf.close()
        }
    }

    // MERGE PDF
    fun mergePdfs(inputs: List<File>): File {

        require(inputs.size >= 2) {
            "Select at least two PDF files."
        }

        val output = File(
            context.cacheDir,
            "merged_${System.currentTimeMillis()}.pdf"
        )

        val merger = PDFMergerUtility()

        inputs.forEach { file ->
            require(file.isFile && file.length() > 0) {
                "Invalid PDF file."
            }

            merger.addSource(file)
        }

        merger.destinationFileName = output.absolutePath
        merger.mergeDocuments(null)

        require(output.exists() && output.length() > 0) {
            "PDF merge failed."
        }

        return output
    }

    // EXTRACT SINGLE PDF PAGE
    fun extractPage(
        input: File,
        pageNumber: Int
    ): File {

        PDDocument.load(input).use { source ->

            require(
                pageNumber in 1..source.numberOfPages
            ) {
                "Page number is out of range."
            }

            PDDocument().use { result ->

                result.addPage(
                    source.getPage(pageNumber - 1)
                )

                val output = File(
                    context.cacheDir,
                    "page_${pageNumber}_${System.currentTimeMillis()}.pdf"
                )

                result.save(output)

                return output
            }
        }
    }

    // ROTATE ALL PDF PAGES
    fun rotateAllPages(
        input: File,
        degrees: Int
    ): File {

        require(degrees in listOf(90, 180, 270)) {
            "Invalid rotation."
        }

        PDDocument.load(input).use { document ->

            for (i in 0 until document.numberOfPages) {

                val page = document.getPage(i)

                page.rotation =
                    (page.rotation + degrees) % 360
            }

            val output = File(
                context.cacheDir,
                "rotated_${System.currentTimeMillis()}.pdf"
            )

            document.save(output)

            return output
        }
    }

    // SAVE PDF TO CACHE
    private fun savePdf(
        pdf: PdfDocument,
        prefix: String
    ): File {

        val output = File(
            context.cacheDir,
            "${prefix}_${System.currentTimeMillis()}.pdf"
        )

        FileOutputStream(output).use {
            pdf.writeTo(it)
        }

        require(output.exists() && output.length() > 0) {
            "PDF creation failed."
        }

        return output
    }
}
