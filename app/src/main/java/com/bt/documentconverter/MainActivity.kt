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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1769E0)
                )
            ) {
                ConverterScreen()
            }
        }
    }

    @Composable
    private fun ConverterScreen() {
        val engine = remember { OfflinePdfEngine(this) }
        val snackbar = remember { SnackbarHostState() }

        var busy by remember { mutableStateOf(false) }
        var pendingFile by remember { mutableStateOf<File?>(null) }
        var textDialog by remember { mutableStateOf(false) }
        var pdfDialog by remember { mutableStateOf(false) }
        var selectedPdf by remember { mutableStateOf<Uri?>(null) }
        var textValue by remember { mutableStateOf("") }
        var pageValue by remember { mutableStateOf("1") }

        val saveLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) { destination ->
            val source = pendingFile
            if (destination != null && source != null) {
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            contentResolver.openOutputStream(destination).use { output ->
                                requireNotNull(output)
                                source.inputStream().use { it.copyTo(output) }
                            }
                        }
                        snackbar.showSnackbar("PDF saved successfully!")
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Save failed: ${e.message}")
                    } finally {
                        source.delete()
                        pendingFile = null
                    }
                }
            } else {
                pendingFile?.delete()
                pendingFile = null
            }
        }

        fun runTask(name: String, task: suspend () -> File) {
            if (busy) return

            lifecycleScope.launch {
                busy = true
                try {
                    val file = task()
                    pendingFile = file
                    saveLauncher.launch(file.name)
                } catch (e: Exception) {
                    snackbar.showSnackbar("$name failed: ${e.message}")
                } finally {
                    busy = false
                }
            }
        }

        val imagePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isNotEmpty()) {
                runTask("Image to PDF") {
                    withContext(Dispatchers.IO) {
                        engine.imagesToPdf(uris)
                    }
                }
            }
        }

        val mergePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.size >= 2) {
                runTask("Merge PDF") {
                    withContext(Dispatchers.IO) {
                        val files = uris.mapIndexed { index, uri ->
                            val file = File(cacheDir, "merge_$index.pdf")
                            contentResolver.openInputStream(uri).use { input ->
                                requireNotNull(input)
                                file.outputStream().use { input.copyTo(it) }
                            }
                            file
                        }

                        try {
                            engine.mergePdfs(files)
                        } finally {
                            files.forEach { it.delete() }
                        }
                    }
                }
            } else {
                lifecycleScope.launch {
                    snackbar.showSnackbar("Select at least two PDF files.")
                }
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

        Scaffold(
            containerColor = Color(0xFFF6F8FC),
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(18.dp)
            ) {
                Text("BT Document Converter", fontSize = 24.sp)
                Text("Convert documents easily", color = Color.Gray)

                Spacer(Modifier.height(20.dp))

                Surface(
                    color = Color(0xFF1769E0),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "All Documents,",
                            color = Color.White,
                            fontSize = 22.sp
                        )
                        Text(
                            "One Powerful Converter",
                            color = Color.White,
                            fontSize = 19.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "PDF • Word • Excel • PowerPoint",
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                Text("Offline PDF Tools", fontSize = 19.sp)
                Spacer(Modifier.height(12.dp))

                val tools = listOf(
                    "Image to PDF" to Icons.Default.Image,
                    "Text to PDF" to Icons.Default.TextSnippet,
                    "Merge PDF" to Icons.Default.CallMerge,
                    "Split PDF" to Icons.Default.CallSplit,
                    "Rotate PDF" to Icons.Default.RotateRight
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
                                    "Image to PDF" ->
                                        imagePicker.launch(arrayOf("image/*"))

                                    "Text to PDF" ->
                                        textDialog = true

                                    "Merge PDF" ->
                                        mergePicker.launch(arrayOf("application/pdf"))

                                    "Split PDF", "Rotate PDF" ->
                                        pdfPicker.launch(arrayOf("application/pdf"))
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(115.dp)
                        ) {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .padding(15.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    tool.second,
                                    contentDescription = tool.first,
                                    tint = Color(0xFF1769E0)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(tool.first)
                            }
                        }
                    }
                }

                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Processing...", color = Color.Gray)
                }
            }
        }

        if (textDialog) {
            AlertDialog(
                onDismissRequest = { textDialog = false },
                title = { Text("Text to PDF") },
                text = {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        label = { Text("Enter text") },
                        minLines = 4
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val content = textValue
                        textDialog = false
                        runTask("Text to PDF") {
                            withContext(Dispatchers.IO) {
                                engine.textToPdf(content)
                            }
                        }
                    }) {
                        Text("Create PDF")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { textDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (pdfDialog) {
            AlertDialog(
                onDismissRequest = { pdfDialog = false },
                title = { Text("PDF Tools") },
                text = {
                    Column {
                        Text("Extract a single page or rotate all pages.")
                        OutlinedTextField(
                            value = pageValue,
                            onValueChange = {
                                pageValue = it.filter(Char::isDigit)
                            },
                            label = { Text("Page number to extract") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val uri = selectedPdf
                        val page = pageValue.toIntOrNull()
                        pdfDialog = false

                        if (uri != null && page != null && page > 0) {
                            runTask("Extract PDF page") {
                                withContext(Dispatchers.IO) {
                                    val input = copyUriToCache(uri)
                                    try {
                                        engine.extractPage(input, page)
                                    } finally {
                                        input.delete()
                                    }
                                }
                            }
                        }
                    }) {
                        Text("Extract Page")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        val uri = selectedPdf
                        pdfDialog = false

                        if (uri != null) {
                            runTask("Rotate PDF") {
                                withContext(Dispatchers.IO) {
                                    val input = copyUriToCache(uri)
                                    try {
                                        engine.rotateAllPages(input, 90)
                                    } finally {
                                        input.delete()
                                    }
                                }
                            }
                        }
                    }) {
                        Text("Rotate 90°")
                    }
                }
            )
        }
    }

    private fun copyUriToCache(uri: Uri): File {
        val file = File(cacheDir, "input_${System.currentTimeMillis()}.pdf")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot read PDF file." }
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file
    }
}
