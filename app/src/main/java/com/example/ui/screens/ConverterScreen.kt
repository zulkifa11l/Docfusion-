package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.DocFusionViewModel
import com.example.util.PdfProcessor
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    viewModel: DocFusionViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var activeConversionTask by remember { mutableStateOf("Text to PDF") }
    
    // Picked File States
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    
    // Custom Text Input values (Used for Text to Word / Text to PDF)
    var rawInputText by remember { mutableStateOf("") }

    // Pick PDF size options mapping (fit page, passport size, etc.)
    var selectedPageSizeOption by remember { mutableStateOf("A4") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = resolveFileName(context, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Converter", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Task Selector Menu Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Conversion Task", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val conversionsList = listOf(
                        "Text to PDF", "Text to Word", 
                        "Word to PDF", "Word to Image",
                        "PDF to Word", "PDF to Image",
                        "Image to PDF", "Image to Word"
                    )
                    
                    var menuExpanded by remember { mutableStateOf(false) }
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Text(activeConversionTask, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            conversionsList.forEach { task ->
                                DropdownMenuItem(
                                    text = { Text(task) },
                                    onClick = {
                                        activeConversionTask = task
                                        menuExpanded = false
                                        selectedFileUri = null
                                        selectedFileName = ""
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Input Details based on Selector
            if (activeConversionTask == "Text to PDF" || activeConversionTask == "Text to Word") {
                Text("Write text or select a file", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = rawInputText,
                    onValueChange = { rawInputText = it },
                    placeholder = { Text("Type your paragraphs or layout contents to convert...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            } else {
                // File Picker
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable {
                            val mimeType = when {
                                activeConversionTask.startsWith("PDF") -> "application/pdf"
                                activeConversionTask.startsWith("Word") -> "*/*" // docx files
                                activeConversionTask.startsWith("Image") -> "image/*"
                                else -> "*/*"
                            }
                            filePickerLauncher.launch(mimeType)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = if (selectedFileUri != null) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = if (selectedFileUri != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (selectedFileUri != null) selectedFileName else "Tap to Choose File",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (selectedFileUri != null) "File uploaded successfully" else "Requires ${activeConversionTask.substringBefore(" to ")} file input",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Custom Page Sizes mapping (Only for Image to PDF)
            if (activeConversionTask == "Image to PDF") {
                Column {
                    Text("Select Target Dimensions Size", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        listOf("A4", "Letter", "Passport", "ID Card").forEach { size ->
                            val isSel = selectedPageSizeOption == size
                            ElevatedFilterChip(
                                selected = isSel,
                                onClick = { selectedPageSizeOption = size },
                                label = { Text(size, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Convert Process execution CTA
            Button(
                onClick = {
                    if (activeConversionTask.startsWith("Text")) {
                        if (rawInputText.isEmpty()) {
                            Toast.makeText(context, "Please enter some text sequence.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val tempFile = File(context.cacheDir, "temp_inputText.txt").apply {
                            writeText(rawInputText)
                        }
                        viewModel.performConversion(activeConversionTask, tempFile) {
                            tempFile.delete()
                            rawInputText = ""
                            Toast.makeText(context, "Completed! File recorded in dashboard.", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    } else {
                        val uri = selectedFileUri
                        if (uri == null) {
                            Toast.makeText(context, "Please select an input document first.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val file = getFileFromUri(context, uri, selectedFileName) ?: return@Button
                        
                        if (activeConversionTask == "Image to PDF") {
                            // Direct converter with scale sizing
                            val options = PdfProcessor.PdfOptions(pageSize = selectedPageSizeOption)
                            viewModel.convertImageToCustomPdf(file, options) {
                                Toast.makeText(context, "PDF successfully generated!", Toast.LENGTH_SHORT).show()
                                file.delete()
                                onNavigateBack()
                            }
                        } else {
                            viewModel.performConversion(activeConversionTask, file) {
                                file.delete()
                                selectedFileUri = null
                                selectedFileName = ""
                                Toast.makeText(context, "Done! New file saved to File Hub.", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Start Conversion", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        // Processing Overlay during conversion task loading
        if (viewModel.isConverting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.padding(32.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = viewModel.conversionMsg,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// Helper methods to copy picked document files locally for secure operations
fun getFileFromUri(context: Context, uri: Uri, fileName: String): File? {
    try {
        val file = File(context.cacheDir, fileName)
        val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
        val outputStream = FileOutputStream(file)
        val buffer = ByteArray(4096)
        var length: Int
        while (inputStream.read(buffer).also { length = it } != -1) {
            outputStream.write(buffer, 0, length)
        }
        outputStream.flush()
        outputStream.close()
        inputStream.close()
        return file
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun resolveFileName(context: Context, uri: Uri): String {
    var name = "picked_document"
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                name = it.getString(nameIndex)
            }
        }
    }
    return name
}
