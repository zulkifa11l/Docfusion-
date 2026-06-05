package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.HistoryEntry
import com.example.data.Note
import com.example.ui.viewmodel.DocFusionViewModel
import com.example.util.PdfProcessor
import com.example.util.getFileFromUri
import com.example.util.resolveFileName
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.InsertDriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.util.GeminiHelper
import com.example.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    viewModel: DocFusionViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var activeConversionTask by remember { mutableStateOf("Text to PDF") }
    
    // Batch Processing State overrides
    var selectedTab by remember { mutableStateOf("Single") } // "Single", "Batch"
    val selectedBatchFileIds = remember { mutableStateMapOf<Long, Boolean>() }
    var searchBatchQuery by remember { mutableStateOf("") }
    var batchAction by remember { mutableStateOf("PDF Compression") } // "PDF Compression", "Add Watermark", "Encrypt Password", "Extract Text (OCR)"
    var batchCompressionQuality by remember { mutableStateOf(50f) }
    var batchWatermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var batchPassword by remember { mutableStateOf("") }
    
    var isProcessingBatch by remember { mutableStateOf(false) }
    var batchProgressMsg by remember { mutableStateOf("") }
    var batchResults by remember { mutableStateOf<List<BatchResult>>(emptyList()) }
    var showBatchResultsDialog by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Picked File States
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    
    // Multiple Picked Gallery Images States
    var selectedFilesUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedFilesNames by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Custom Text Input values (Used for Text to Word / Text to PDF)
    var rawInputText by remember { mutableStateOf("") }

    // Pick PDF size options mapping (fit page, passport size, etc.)
    var selectedPageSizeOption by remember { mutableStateOf("A4") }
    
    // Quality compression selection state -> Low, Medium, High, Original
    var selectedQualityOption by remember { mutableStateOf("Medium") }

    // Text Source options Segment Selector -> "Manual", "Saved Notes", "Scanner OCR"
    var textSourceOption by remember { mutableStateOf("Manual") }

    // Image Source options Segment Selector -> "Gallery Device", "Saved Scans/Photos"
    var imageSourceOption by remember { mutableStateOf("Gallery Device") }

    // Selected Saved Database images for image-to-PDF compilation
    val selectedStoredImages = remember { mutableStateListOf<HistoryEntry>() }
    var outputFileNameText by remember { mutableStateOf("Compiled_Stored_Images") }

    // Success Export dialogue states
    var showExportDialog by remember { mutableStateOf(false) }
    var generatedFileForExport by remember { mutableStateOf<File?>(null) }
    var generatedFileNameForExport by remember { mutableStateOf("") }

    // Collections from ViewModel State
    val notesList by viewModel.publicNotes.collectAsState()
    val historyList by viewModel.history.collectAsState()

    val storedImages = remember(historyList) {
        historyList.filter { it.category == "Images" || it.category == "Scan" }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = resolveFileName(context, uri)
        }
    }

    val multipleImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedFilesUris = uris
            selectedFilesNames = uris.map { resolveFileName(context, it) }
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
            // Segmented Top Tab Selector Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Single Converter", "Batch Processing").forEach { option ->
                    val isSel = (option == "Single Converter" && selectedTab == "Single") || (option == "Batch Processing" && selectedTab == "Batch")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { selectedTab = if (option == "Single Converter") "Single" else "Batch" }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (option == "Single Converter") Icons.Default.InsertDriveFile else Icons.Default.DynamicFeed,
                                contentDescription = null,
                                tint = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = option,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (selectedTab == "Single") {
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer, 
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
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
                                        selectedStoredImages.clear()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Central Dynamic Container Area (Scrollable to accommodate multi-select details comfortably)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeConversionTask == "Text to PDF" || activeConversionTask == "Text to Word") {
                    
                    // Segmented Options Selector for Text Input Sources
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.40f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Manual", "Captured Notes", "Recent OCR").forEach { option ->
                            val isSel = textSourceOption == option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { textSourceOption = option }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    when (textSourceOption) {
                        "Manual" -> {
                            OutlinedTextField(
                                value = rawInputText,
                                onValueChange = { rawInputText = it },
                                placeholder = { Text("Type paragraphs or layout details to convert...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        "Captured Notes" -> {
                            if (notesList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.24f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.NoteAlt, 
                                            contentDescription = null, 
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No captured notes found", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        Text("Go to Voice/Text logger to save some text notes.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Select a Stored Note to Format & Convert:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth().weight(1f)
                                    ) {
                                        items(notesList) { note ->
                                            val isLocalSelected = rawInputText.contains(note.content) && rawInputText.contains(note.title)
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        rawInputText = "${note.title.uppercase()}\n\nSaved: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(note.timestamp))}\n\n${note.content}"
                                                    },
                                                border = BorderStroke(
                                                    width = if (isLocalSelected) 2.dp else 1.dp,
                                                    color = if (isLocalSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isLocalSelected) MaterialTheme.colorScheme.primaryContainer.copy(0.3f) 
                                                                     else MaterialTheme.colorScheme.surface
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Description, 
                                                        contentDescription = null, 
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(note.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text(note.content, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    if (isLocalSelected) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "Recent OCR" -> {
                            val ocrText = viewModel.extractedText
                            if (ocrText.isBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.24f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                        Icon(
                                            Icons.Default.QrCodeScanner, 
                                            contentDescription = null, 
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No scanned OCR text found", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        Text("Extracted text from doc scanners accumulates here.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.24f), RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("Captured Text Preview:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(ocrText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    
                                    Button(
                                        onClick = {
                                            rawInputText = ocrText
                                            textSourceOption = "Manual"
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Load OCR text into conversion workspace")
                                    }
                                }
                            }
                        }
                    }
                } else if (activeConversionTask == "Image to PDF") {
                    
                    // Segmented Options Selector for Image Source
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.40f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Gallery Device", "Saved Scans/Photos").forEach { option ->
                            val isSel = imageSourceOption == option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { imageSourceOption = option }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (imageSourceOption == "Gallery Device") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable { multipleImagePickerLauncher.launch("image/*") },
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
                                    val hasImages = selectedFilesUris.isNotEmpty()
                                    Icon(
                                        imageVector = if (hasImages) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = if (hasImages) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (hasImages) "${selectedFilesUris.size} Images Selected" else "Choose Device Photos / Images",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (hasImages) selectedFilesNames.joinToString(", ") else "Select one or multiple images from gallery to build PDF",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Saved App Scans/Photos List Component
                        if (storedImages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.24f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                    Icon(
                                        Icons.Default.PhotoLibrary, 
                                        contentDescription = null, 
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No stored app scans or pictures found", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text("Images scanned via scanner panel appear here for PDF formatting.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Select one or more stored images to compile:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    TextButton(onClick = {
                                        if (selectedStoredImages.size == storedImages.size) {
                                            selectedStoredImages.clear()
                                        } else {
                                            selectedStoredImages.clear()
                                            selectedStoredImages.addAll(storedImages)
                                        }
                                    }) {
                                        Text(if (selectedStoredImages.size == storedImages.size) "Deselect All" else "Select All", fontSize = 12.sp)
                                    }
                                }

                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                ) {
                                    items(storedImages) { imgEntry ->
                                        val isChecked = selectedStoredImages.contains(imgEntry)
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (isChecked) {
                                                        selectedStoredImages.remove(imgEntry)
                                                    } else {
                                                        selectedStoredImages.add(imgEntry)
                                                    }
                                                },
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(0.25f)
                                                                 else MaterialTheme.colorScheme.surface
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = { checked ->
                                                        if (checked == true) {
                                                            selectedStoredImages.add(imgEntry)
                                                        } else {
                                                            selectedStoredImages.remove(imgEntry)
                                                        }
                                                    }
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.primary.copy(0.1f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(imgEntry.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(
                                                        SimpleDateFormat("MM/dd h:mm a", Locale.getDefault()).format(Date(imgEntry.timestamp)),
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = outputFileNameText,
                                    onValueChange = { outputFileNameText = it },
                                    label = { Text("Output Document Title (.pdf)") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                } else {
                    // Standard File Picker for overall conversions
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
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Compress PDF: Image Quality", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        listOf("Low", "Medium", "High", "Original").forEach { qOption ->
                            val isSel = selectedQualityOption == qOption
                            ElevatedFilterChip(
                                selected = isSel,
                                onClick = { selectedQualityOption = qOption },
                                label = { Text(qOption, fontSize = 11.sp) }
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
                            Toast.makeText(context, "Please write, load a note, or import OCR text first.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val tempFile = File(context.cacheDir, "temp_inputText.txt").apply {
                            writeText(rawInputText)
                        }
                        
                        viewModel.performConversion(activeConversionTask, tempFile) {
                            tempFile.delete()
                            rawInputText = ""
                            
                            // Discover generated file to trigger export screen options
                            val latestFile = discoverLatestHistoryEntryFile(viewModel, activeConversionTask)
                            if (latestFile != null) {
                                generatedFileForExport = latestFile
                                generatedFileNameForExport = latestFile.name
                                showExportDialog = true
                            } else {
                                Toast.makeText(context, "Completed! File recorded in dashboard.", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        }
                    } else if (activeConversionTask == "Image to PDF") {
                        val qualityVal = when (selectedQualityOption) {
                            "Low" -> 30
                            "Medium" -> 60
                            "High" -> 85
                            else -> 100
                        }
                        if (imageSourceOption == "Gallery Device") {
                            if (selectedFilesUris.isEmpty()) {
                                Toast.makeText(context, "Please select at least one gallery image first.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            val filesList = selectedFilesUris.mapIndexedNotNull { index, uri ->
                                val name = selectedFilesNames.getOrNull(index) ?: "img_${index}.jpg"
                                getFileFromUri(context, uri, name)
                            }
                            
                            if (filesList.isEmpty()) {
                                Toast.makeText(context, "Could not load selected images. Verify permissions.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            val options = PdfProcessor.PdfOptions(pageSize = selectedPageSizeOption, compressQuality = qualityVal)
                            viewModel.convertMultipleImagesToCustomPdf(filesList, options) {
                                filesList.forEach { it.delete() }
                                val latestFile = discoverLatestHistoryEntryFile(viewModel, "Image to PDF")
                                if (latestFile != null) {
                                    generatedFileForExport = latestFile
                                    generatedFileNameForExport = latestFile.name
                                    showExportDialog = true
                                } else {
                                    Toast.makeText(context, "PDF successfully generated!", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                            }
                        } else {
                            // Convert multiple stored database images
                            if (selectedStoredImages.isEmpty()) {
                                Toast.makeText(context, "Please choose at least 1 stored application scan file.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val options = PdfProcessor.PdfOptions(pageSize = selectedPageSizeOption, compressQuality = qualityVal)
                            viewModel.convertStoredImagesToPdf(
                                entries = selectedStoredImages.toList(),
                                pdfName = outputFileNameText,
                                options = options
                            ) { outFile ->
                                selectedStoredImages.clear()
                                generatedFileForExport = outFile
                                generatedFileNameForExport = outFile.name
                                showExportDialog = true
                            }
                        }
                    } else {
                        val uri = selectedFileUri
                        if (uri == null) {
                            Toast.makeText(context, "Please select an input document first.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val file = getFileFromUri(context, uri, selectedFileName) ?: return@Button
                        viewModel.performConversion(activeConversionTask, file) {
                            file.delete()
                            selectedFileUri = null
                            selectedFileName = ""
                            
                            val latestFile = discoverLatestHistoryEntryFile(viewModel, activeConversionTask)
                            if (latestFile != null) {
                                generatedFileForExport = latestFile
                                generatedFileNameForExport = latestFile.name
                                showExportDialog = true
                            } else {
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
            } else {
                BatchProcessingModule(
                    viewModel = viewModel,
                    historyList = historyList,
                    searchBatchQuery = searchBatchQuery,
                    onSearchBatchQueryChange = { searchBatchQuery = it },
                    selectedBatchFileIds = selectedBatchFileIds,
                    batchAction = batchAction,
                    onBatchActionChange = { batchAction = it },
                    batchCompressionQuality = batchCompressionQuality,
                    onBatchCompressionQualityChange = { batchCompressionQuality = it },
                    batchWatermarkText = batchWatermarkText,
                    onBatchWatermarkTextChange = { batchWatermarkText = it },
                    batchPassword = batchPassword,
                    onBatchPasswordChange = { batchPassword = it },
                    isProcessingBatch = isProcessingBatch,
                    onIsProcessingBatchChange = { isProcessingBatch = it },
                    batchProgressMsg = batchProgressMsg,
                    onBatchProgressMsgChange = { batchProgressMsg = it },
                    batchResults = batchResults,
                    onBatchResultsChange = { batchResults = it },
                    showBatchResultsDialog = showBatchResultsDialog,
                    onShowBatchResultsDialogChange = { showBatchResultsDialog = it }
                )
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

        // Beautiful Interactive High-Fidelity Success Dialogue with Instant Export / Share options
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = {
                    showExportDialog = false
                    onNavigateBack()
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                        }
                        Text("Conversion Complete!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your formatted document has been successfully created and saved in the local hub DB.")
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (generatedFileNameForExport.endsWith(".docx")) Icons.Default.Description else Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = if (generatedFileNameForExport.endsWith(".docx")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = generatedFileNameForExport,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val formattedSize = generatedFileForExport?.let {
                                        val kb = it.length() / 1024
                                        if (kb > 1024) String.format(Locale.US, "%.1f MB", kb / 1024f) else "$kb KB"
                                    } ?: "Unknown Size"
                                    Text("Size: $formattedSize", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val fileToShare = generatedFileForExport
                            if (fileToShare != null && fileToShare.exists()) {
                                shareExportedFile(context, fileToShare)
                            } else {
                                Toast.makeText(context, "Error sharing: physical file was missing.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export / Share")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showExportDialog = false
                            onNavigateBack()
                        }
                    ) {
                        Text("Done")
                    }
                }
            )
        }
    }
}

// Helper block to query or find latest file based on timestamp to ensure smooth instant sharing after conversion
fun discoverLatestHistoryEntryFile(viewModel: DocFusionViewModel, task: String): File? {
    try {
        val history = viewModel.history.value
        if (history.isEmpty()) return null
        
        // Find most recent PDF or document based on category or name matches
        val isWordTask = task.endsWith("Word")
        val targetCategory = if (isWordTask) "Word" else "PDF"
        
        val latestEntry = history.filter { it.category == targetCategory }.maxByOrNull { it.timestamp }
        if (latestEntry != null) {
            val file = File(latestEntry.path)
            if (file.exists()) return file
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

// Clean and reliable FileProvider share drawer initiator
fun shareExportedFile(context: Context, file: File) {
    try {
        val authority = "${context.packageName}.provider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.name.endsWith(".docx")) {
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            } else {
                "application/pdf"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Document via..."))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Sharing failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

data class BatchResult(
    val originalName: String,
    val originalSize: Long,
    val newName: String,
    val newSize: Long,
    val path: String,
    val success: Boolean,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchProcessingModule(
    viewModel: DocFusionViewModel,
    historyList: List<HistoryEntry>,
    searchBatchQuery: String,
    onSearchBatchQueryChange: (String) -> Unit,
    selectedBatchFileIds: MutableMap<Long, Boolean>,
    batchAction: String,
    onBatchActionChange: (String) -> Unit,
    batchCompressionQuality: Float,
    onBatchCompressionQualityChange: (Float) -> Unit,
    batchWatermarkText: String,
    onBatchWatermarkTextChange: (String) -> Unit,
    batchPassword: String,
    onBatchPasswordChange: (String) -> Unit,
    isProcessingBatch: Boolean,
    onIsProcessingBatchChange: (Boolean) -> Unit,
    batchProgressMsg: String,
    onBatchProgressMsgChange: (String) -> Unit,
    batchResults: List<BatchResult>,
    onBatchResultsChange: (List<BatchResult>) -> Unit,
    showBatchResultsDialog: Boolean,
    onShowBatchResultsDialogChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val filteredHistory = remember(historyList, searchBatchQuery, batchAction) {
        historyList.filter { entry ->
            val matchesSearch = searchBatchQuery.isEmpty() || entry.name.contains(searchBatchQuery, ignoreCase = true)
            val matchesType = when (batchAction) {
                "PDF Compression", "Add Watermark" -> entry.name.endsWith(".pdf", ignoreCase = true)
                "Extract Text (OCR)" -> entry.name.endsWith(".pdf", ignoreCase = true) || 
                                       entry.name.endsWith(".jpg", ignoreCase = true) || 
                                       entry.name.endsWith(".jpeg", ignoreCase = true) || 
                                       entry.name.endsWith(".png", ignoreCase = true)
                else -> true
            }
            matchesSearch && matchesType
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "1. Unified Operation Settings",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    var expandedActions by remember { mutableStateOf(false) }
                    val actionsList = listOf("PDF Compression", "Add Watermark", "Encrypt Password", "Extract Text (OCR)")

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedActions = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        imageVector = when (batchAction) {
                                            "PDF Compression" -> Icons.Default.Compress
                                            "Add Watermark" -> Icons.Default.BorderColor
                                            "Encrypt Password" -> Icons.Default.Lock
                                            else -> Icons.Default.DocumentScanner
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(batchAction, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = expandedActions,
                            onDismissRequest = { expandedActions = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            actionsList.forEach { act ->
                                DropdownMenuItem(
                                    text = { Text(act) },
                                    onClick = {
                                        onBatchActionChange(act)
                                        expandedActions = false
                                        selectedBatchFileIds.clear()
                                    }
                                )
                            }
                        }
                    }

                    when (batchAction) {
                        "PDF Compression" -> {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Compression Level", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${batchCompressionQuality.toInt()}% Quality", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = batchCompressionQuality,
                                    onValueChange = onBatchCompressionQualityChange,
                                    valueRange = 10f..90f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("Lower quality percent yields smaller files.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        "Add Watermark" -> {
                            OutlinedTextField(
                                value = batchWatermarkText,
                                onValueChange = onBatchWatermarkTextChange,
                                label = { Text("Watermark Text Overlay") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        "Encrypt Password" -> {
                            OutlinedTextField(
                                value = batchPassword,
                                onValueChange = onBatchPasswordChange,
                                label = { Text("Master Password Key") },
                                placeholder = { Text("Enter a secure lock password") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        "Extract Text (OCR)" -> {
                            Text(
                                text = "Applies vision transcribing to extract textual content from all chosen files.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "2. Select Documents to Process",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = searchBatchQuery,
                        onValueChange = onSearchBatchQueryChange,
                        placeholder = { Text("Search your local documents...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                filteredHistory.forEach { selectedBatchFileIds[it.id] = true }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select All (${filteredHistory.size})", fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = {
                                selectedBatchFileIds.clear()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Deselect, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deselect All", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (filteredHistory.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.40f), modifier = Modifier.size(48.dp))
                        Text("No matching documents found in File Hub", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Only files matching '$batchAction' are listed.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(filteredHistory, key = { it.id }) { entry ->
                val isSelected = selectedBatchFileIds[entry.id] == true
                val sizeStr = remember(entry.fileSize) {
                    val kb = entry.fileSize / 1024
                    if (kb > 1024) String.format(Locale.US, "%.1f MB", kb / 1024f) else "$kb KB"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedBatchFileIds[entry.id] = !isSelected },
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(0.12f)
                                         else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { selectedBatchFileIds[entry.id] = it ?: false }
                        )

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when (entry.category) {
                                        "PDF" -> Color(0xFFFFECEB)
                                        "Word" -> Color(0xFFE8F1FF)
                                        "Images", "Scan" -> Color(0xFFE6F7FF)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (entry.category) {
                                    "PDF" -> Icons.Default.PictureAsPdf
                                    "Word" -> Icons.Default.Description
                                    "Images", "Scan" -> Icons.Default.Image
                                    else -> Icons.Default.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = when (entry.category) {
                                    "PDF" -> Color(0xFFD32F2F)
                                    "Word" -> Color(0xFF1976D2)
                                    "Images", "Scan" -> Color(0xFF0288D1)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(sizeStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(entry.timestamp)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            val selectedCount = remember(selectedBatchFileIds.values.size, selectedBatchFileIds) {
                selectedBatchFileIds.values.count { it }
            }

            Button(
                onClick = {
                    val targetFiles = historyList.filter { selectedBatchFileIds[it.id] == true }
                    if (targetFiles.isEmpty()) {
                        Toast.makeText(context, "Please select at least 1 document to process", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    coroutineScope.launch(Dispatchers.IO) {
                        onIsProcessingBatchChange(true)
                        val results = mutableListOf<BatchResult>()

                        targetFiles.forEachIndexed { idx, entry ->
                            val sourceFile = File(entry.path)
                            if (!sourceFile.exists()) {
                                results.add(BatchResult(entry.name, entry.fileSize, entry.name, 0, entry.path, false, "Physical file was missing"))
                                return@forEachIndexed
                            }

                            onBatchProgressMsgChange("Processing [${idx + 1}/${targetFiles.size}]: ${entry.name}...")
                            try {
                                val outName = when (batchAction) {
                                    "PDF Compression" -> "${sourceFile.nameWithoutExtension}_compressed_${System.currentTimeMillis() / 1000}.pdf"
                                    "Add Watermark" -> "${sourceFile.nameWithoutExtension}_watermarked_${System.currentTimeMillis() / 1000}.pdf"
                                    "Encrypt Password" -> "${sourceFile.nameWithoutExtension}_encrypted_${System.currentTimeMillis() / 1000}.pdf"
                                    "Extract Text (OCR)" -> "${sourceFile.nameWithoutExtension}_text_${System.currentTimeMillis() / 1000}.txt"
                                    else -> "${sourceFile.nameWithoutExtension}_batch_${System.currentTimeMillis() / 1000}.${sourceFile.extension}"
                                }

                                val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
                                val outFile = File(outDir, outName)

                                when (batchAction) {
                                    "PDF Compression" -> {
                                        PdfProcessor.compressPdf(sourceFile, outFile, batchCompressionQuality.toInt())
                                    }
                                    "Add Watermark" -> {
                                        PdfProcessor.addWatermark(sourceFile, outFile, batchWatermarkText)
                                    }
                                    "Encrypt Password" -> {
                                        if (batchPassword.isEmpty()) {
                                            throw IllegalArgumentException("Passphrase key required")
                                        }
                                        PdfProcessor.encryptDecryptFile(context, sourceFile, outFile, batchPassword, encrypt = true)
                                    }
                                    "Extract Text (OCR)" -> {
                                        if (sourceFile.extension.lowercase() == "pdf") {
                                            val cachedImages = PdfProcessor.pdfToImages(sourceFile, context.cacheDir, 60)
                                            val ocrBuilder = StringBuilder()
                                            if (cachedImages.isNotEmpty()) {
                                                cachedImages.forEachIndexed { pageIdx, frame ->
                                                    val bitmap = BitmapFactory.decodeFile(frame.absolutePath)
                                                    if (bitmap != null) {
                                                        ocrBuilder.append("--- PAGE ${pageIdx + 1} ---\n")
                                                        val key = BuildConfig.GEMINI_API_KEY
                                                        val text = if (key.isNotEmpty() && key != "MY_GEMINI_API_KEY") {
                                                            GeminiHelper.performOcr(bitmap, "Extract clean text. No summaries.")
                                                        } else {
                                                            "Gemini AI Offline OCR placeholder. Please verify your API Key in secrets panel."
                                                        }
                                                        ocrBuilder.append(text).append("\n\n")
                                                    }
                                                    frame.delete()
                                                }
                                            }
                                            outFile.writeText(ocrBuilder.toString())
                                        } else {
                                            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
                                            if (bitmap != null) {
                                                val key = BuildConfig.GEMINI_API_KEY
                                                val text = if (key.isNotEmpty() && key != "MY_GEMINI_API_KEY") {
                                                    GeminiHelper.performOcr(bitmap)
                                                } else {
                                                    "Gemini AI Offline OCR placeholder. Please verify your API Key in secrets panel."
                                                }
                                                outFile.writeText(text)
                                            } else {
                                                throw IllegalArgumentException("Failed to decode image data")
                                            }
                                        }
                                    }
                                }

                                val finalCategory = if (batchAction == "Extract Text (OCR)") "Text" else entry.category
                                viewModel.insertManualHistoryEntry(
                                    name = outName,
                                    absolutePath = outFile.absolutePath,
                                    category = finalCategory,
                                    size = outFile.length()
                                )

                                results.add(BatchResult(entry.name, entry.fileSize, outName, outFile.length(), outFile.absolutePath, true))
                            } catch (e: Exception) {
                                results.add(BatchResult(entry.name, entry.fileSize, entry.name, 0, entry.path, false, e.localizedMessage ?: "Operation failed"))
                            }
                        }

                        withContext(Dispatchers.Main) {
                            onIsProcessingBatchChange(false)
                            onBatchResultsChange(results)
                            onShowBatchResultsDialogChange(true)
                            viewModel.scanDevicePdfs()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = selectedCount > 0,
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Process Unified Batch ($selectedCount Files)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }

    if (isProcessingBatch) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(24.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Unified Batch Operation in Progress",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = batchProgressMsg,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showBatchResultsDialog) {
        AlertDialog(
            onDismissRequest = { onShowBatchResultsDialogChange(false) },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    }
                    Text("Batch Operation Finished", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Unified processing summary of files:")
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Box(modifier = Modifier.heightIn(max = 260.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(batchResults) { res ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (res.success) MaterialTheme.colorScheme.surfaceVariant.copy(0.40f)
                                                         else MaterialTheme.colorScheme.errorContainer.copy(0.20f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(res.originalName, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (res.success) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Saved: ${res.newName}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Text("Before: ${res.originalSize / 1024} KB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text("After: ${res.newSize / 1024} KB", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        shareExportedFile(context, File(res.path))
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        } else {
                                            Text("Error: ${res.error}", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onShowBatchResultsDialogChange(false) }) {
                    Text("Done")
                }
            }
        )
    }
}
