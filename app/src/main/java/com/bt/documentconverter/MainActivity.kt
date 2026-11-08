package com.bt.documentconverter

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val BrandBlue = Color(0xFF1769E0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = BrandBlue)) {
                ConverterHome()
            }
        }
    }

    @Composable
    private fun ConverterHome() {
        val engine = remember { OfflinePdfEngine(applicationContext) }
        val snackbar = remember { SnackbarHostState() }
        var busy by remember { mutableStateOf(false) }
        var pendingFile by remember { mutableStateOf<File?>(null) }
        var textDialog by remember { mutableStateOf(false) }
        var pdfDialog by remember { mutableStateOf(false) }
        var cropDialog by remember { mutableStateOf(false) }
        var textValue by remember { mutableStateOf("") }
        var pageValue by remember { mutableStateOf("1") }
        var cropLeft by remember { mutableStateOf("20") }
        var cropTop by remember { mutableStateOf("20") }
        var cropRight by remember { mutableStateOf("20") }
        var cropBottom by remember { mutableStateOf("20") }
        var selectedPdf by remember { mutableStateOf<Uri?>(null) }

        val saveLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) { destination ->
            val source = pendingFile
            if (destination != null && source != null) {
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            contentResolver.openOutputStream(destination).use { output ->
                                requireNotNull(output) { "Cannot create output file." }
                                source.inputStream().use { it.copyTo(output) }
                            }
                        }
                        snackbar.showSnackbar("PDF saved successfully.")
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Save failed: ${e.message}")
                    } finally {
                        source.delete()
                        pendingFile = null
                    }
                }
            } else {
                source?.delete()
                pendingFile = null
            }
        }

        fun runTask(label: String, task: suspend () -> File) {
            if (busy) return
            lifecycleScope.launch {
                busy = true
                try {
                    val file = task()
                    pendingFile = file
                    saveLauncher.launch(file.name)
                } catch (e: Exception) {
                    snackbar.showSnackbar("$label failed: ${e.message ?: "Unknown error"}")
                } finally { busy = false }
            }
        }

        fun copyUri(uri: Uri, suffix: String): File {
            val file = File(cacheDir, "${suffix}_${System.currentTimeMillis()}")
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot read selected file." }
                file.outputStream().use { output -> input.copyTo(output) }
            }
            return file
        }

        val imagePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isNotEmpty()) runTask("Image to PDF") {
                withContext(Dispatchers.IO) { engine.imagesToPdf(uris) }
            }
        }

        val mergePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.size >= 2) runTask("Merge PDF") {
                withContext(Dispatchers.IO) {
                    val files = uris.map { uri -> copyUri(uri, "merge_input") }
                    try { engine.mergePdfs(files) } finally { files.forEach { it.delete() } }
                }
            } else if (uris.isNotEmpty()) {
                lifecycleScope.launch { snackbar.showSnackbar("Select at least two PDF files.") }
            }
        }

        val pdfPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                selectedPdf = uri
                pdfDialog = true
            }
        }

        val excelPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) runTask("Excel to PDF") {
                withContext(Dispatchers.IO) {
                    val input = copyUri(uri, "excel_input.xlsx")
                    try { engine.excelXlsxToPdf(input) } finally { input.delete() }
                }
            }
        }

        Scaffold(
            containerColor = Color(0xFFF6F8FC),
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(18.dp)
            ) {
                Text("BT Document Converter", fontSize = 25.sp, color = Color(0xFF101828))
                Text("Convert documents easily", color = Color.Gray, fontSize = 16.sp)
                Spacer(Modifier.height(20.dp))
                Surface(color = BrandBlue, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("All Documents,", color = Color.White, fontSize = 23.sp)
                        Text("One Powerful Converter", color = Color.White, fontSize = 20.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("PDF • Word • Excel • PowerPoint", color = Color.White)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Offline PDF & Office Tools", fontSize = 20.sp)
                Spacer(Modifier.height(10.dp))

                val tools = listOf(
                    "Image to PDF" to Icons.Default.Image,
                    "Text to PDF" to Icons.Default.TextSnippet,
                    "Excel to PDF (.xlsx)" to Icons.Default.TableChart,
                    "Merge PDF" to Icons.Default.CallMerge,
                    "PDF Page Tools" to Icons.Default.PictureAsPdf,
                    "Crop PDF" to Icons.Default.Crop,
                    "Add Watermark" to Icons.Default.Watermark,
                    "Optimize PDF" to Icons.Default.Compress
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(tools) { tool ->
                        ElevatedCard(
                            onClick = {
                                when (tool.first) {
                                    "Image to PDF" -> imagePicker.launch(arrayOf("image/*"))
                                    "Text to PDF" -> textDialog = true
                                    "Excel to PDF (.xlsx)" -> excelPicker.launch(
                                        arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                    )
                                    "Merge PDF" -> mergePicker.launch(arrayOf("application/pdf"))
                                    "Crop PDF" -> {
                                        pdfPicker.launch(arrayOf("application/pdf"))
                                        cropDialog = true
                                    }
                                    else -> pdfPicker.launch(arrayOf("application/pdf"))
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(118.dp)
                        ) {
                            Column(
                                Modifier.fillMaxSize().padding(15.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(tool.second, contentDescription = tool.first, tint = BrandBlue)
                                Spacer(Modifier.height(12.dp))
                                Text(tool.first)
                            }
                        }
                    }
                }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Processing…", color = Color.Gray)
                }
                Text("Offline processing • Files remain on your device", color = Color.Gray, fontSize = 11.sp)
            }
        }

        if (textDialog) {
            AlertDialog(
                onDismissRequest = { textDialog = false },
                title = { Text("Text to PDF") },
                text = {
                    OutlinedTextField(
                        value = textValue, onValueChange = { textValue = it },
                        label = { Text("Enter text") }, minLines = 4, maxLines = 8
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val value = textValue
                        textDialog = false
                        runTask("Text to PDF") {
                            withContext(Dispatchers.IO) { engine.textToPdf(value) }
                        }
                    }) { Text("Create PDF") }
                },
                dismissButton = { TextButton(onClick = { textDialog = false }) { Text("Cancel") } }
            )
        }

        if (pdfDialog) {
            AlertDialog(
                onDismissRequest = { pdfDialog = false },
                title = { Text("PDF Page Tools") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pageValue,
                            onValueChange = { pageValue = it.filter(Char::isDigit) },
                            label = { Text("Page number") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val uri = selectedPdf
                        val page = pageValue.toIntOrNull()
                        pdfDialog = false
                        if (uri != null && page != null && page > 0) runTask("Extract page") {
                            withContext(Dispatchers.IO) {
                                val input = copyUri(uri, "pdf_input")
                                try { engine.extractPage(input, page) } finally { input.delete() }
                            }
                        }
                    }) { Text("Extract page") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        val uri = selectedPdf
                        pdfDialog = false
                        if (uri != null) runTask("Rotate PDF") {
                            withContext(Dispatchers.IO) {
                                val input = copyUri(uri, "pdf_input")
                                try { engine.rotateAllPages(input, 90) } finally { input.delete() }
                            }
                        }
                    }) { Text("Rotate 90°") }
                }
            )
        }

        if (cropDialog && selectedPdf != null) {
            AlertDialog(
                onDismissRequest = { cropDialog = false },
                title = { Text("Crop PDF pages") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Margins in PDF points (72 points = 1 inch).")
                        OutlinedTextField(cropLeft, { cropLeft = it }, label = { Text("Left") })
                        OutlinedTextField(cropTop, { cropTop = it }, label = { Text("Top") })
                        OutlinedTextField(cropRight, { cropRight = it }, label = { Text("Right") })
                        OutlinedTextField(cropBottom, { cropBottom = it }, label = { Text("Bottom") })
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val uri = selectedPdf
                        val l = cropLeft.toFloatOrNull()
                        val t = cropTop.toFloatOrNull()
                        val r = cropRight.toFloatOrNull()
                        val b = cropBottom.toFloatOrNull()
                        cropDialog = false
                        if (uri != null && l != null && t != null && r != null && b != null) {
                            runTask("Crop PDF") {
                                withContext(Dispatchers.IO) {
                                    val input = copyUri(uri, "crop_input")
                                    try { engine.cropPdf(input, l, t, r, b) } finally { input.delete() }
                                }
                            }
                        } else {
                            lifecycleScope.launch { snackbar.showSnackbar("Enter valid crop margins.") }
                        }
                    }) { Text("Crop and Save") }
                },
                dismissButton = {
                    TextButton(onClick = { cropDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}
