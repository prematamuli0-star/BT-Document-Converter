
package com.bt.documentconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1769E0)
                )
            ) {
                Scaffold(
                    containerColor = Color(0xFFF6F8FC)
                ) { padding ->

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(18.dp)
                    ) {
                        Text(
                            "BT Document Converter",
                            fontSize = 23.sp
                        )

                        Text(
                            "Convert documents easily",
                            color = Color.Gray
                        )

                        Spacer(Modifier.height(20.dp))

                        Surface(
                            color = Color(0xFF1769E0),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                Modifier.padding(20.dp)
                            ) {
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

                        Text(
                            "Offline PDF Tools",
                            fontSize = 19.sp
                        )

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
                            verticalArrangement =
                                Arrangement.spacedBy(12.dp),
                            horizontalArrangement =
                                Arrangement.spacedBy(12.dp)
                        ) {
                            items(tools) { tool ->

                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(115.dp)
                                ) {
                                    Column(
                                        Modifier.padding(15.dp)
                                    ) {
                                        Icon(
                                            tool.second,
                                            contentDescription = null,
                                            tint = Color(0xFF1769E0)
                                        )

                                        Spacer(
                                            Modifier.height(12.dp)
                                        )

                                        Text(tool.first)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
