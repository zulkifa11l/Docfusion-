package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.DocFusionViewModel
import com.example.util.getFileFromUri
import com.example.util.resolveFileName
import com.example.util.PdfProcessor
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    viewModel: DocFusionViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val currentDensity = androidx.compose.ui.platform.LocalDensity.current.density
    var activeToolTask by remember { mutableStateOf("Add Watermark") }
    
    // Picked Document properties
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }

    // Page list for live, visual, interactive drag-and-drop reordering
    var extractedPageItems by remember { mutableStateOf<List<PageItem>>(emptyList()) }
    var isExtractingPages by remember { mutableStateOf(false) }
    var selectedForSwapIndex by remember { mutableStateOf<Int?>(null) }

    val reorderTempDir = remember { File(context.cacheDir, "pdf_reorder_temp") }

    LaunchedEffect(selectedFileUri, activeToolTask) {
        if (activeToolTask == "Reorder Pages (Drag-and-Drop)" && selectedFileUri != null) {
            isExtractingPages = true
            try {
                val file = getFileFromUri(context, selectedFileUri!!, selectedFileName)
                if (file != null) {
                    val items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            if (reorderTempDir.exists()) {
                                reorderTempDir.deleteRecursively()
                            }
                            reorderTempDir.mkdirs()
                            
                            val images = PdfProcessor.pdfToImages(file, reorderTempDir)
                            file.delete() // Safe to delete cached source copy
                            images.mapIndexed { idx, imgFile ->
                                PageItem(
                                    id = "page_${idx}_${System.currentTimeMillis()}",
                                    originalIndex = idx + 1,
                                    file = imgFile
                                )
                            }
                        } catch (e: Exception) {
                            emptyList<PageItem>()
                        }
                    }
                    extractedPageItems = items
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isExtractingPages = false
            }
        } else {
            extractedPageItems = emptyList()
            selectedForSwapIndex = null
        }
    }

    // Multiple PDFs picked properties (for Merge)
    var selectedFilesUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedFilesNames by remember { mutableStateOf<List<String>>(emptyList()) }

    // Parameter states
    var textParamValue by remember { mutableStateOf("") }
    var numericParamValue by remember { mutableStateOf(50) } // Compression ratio scale

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = resolveFileName(context, uri)
        }
    }

    val multiplePdfPickerLauncher = rememberLauncherForActivityResult(
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
                title = { Text("Advanced PDF Tools", fontWeight = FontWeight.Bold) },
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
            // Tool Select Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Advanced PDF Tool", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val toolsList = listOf(
                        "Add Watermark", "PDF Compression", 
                        "PDF Protection", "PDF Unlock",
                        "Add Signature", "PDF Split", 
                        "Rotate Pages", "Delete Pages",
                        "Merge multiple PDFs",
                        "Reorder Pages (Drag-and-Drop)"
                    )
                    
                    var expandedMenu by remember { mutableStateOf(false) }
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { expandedMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Text(activeToolTask, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        
                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            toolsList.forEach { tool ->
                                DropdownMenuItem(
                                    text = { Text(tool) },
                                    onClick = {
                                        activeToolTask = tool
                                        expandedMenu = false
                                        textParamValue = ""
                                        numericParamValue = when (tool) {
                                            "PDF Compression" -> 55
                                            else -> 0
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val isMergeMode = activeToolTask == "Merge multiple PDFs"
            val isReorderMode = activeToolTask == "Reorder Pages (Drag-and-Drop)"

            if (isReorderMode && selectedFileUri != null) {
                // Reorder Workbench view
                ReorderWorkbench(
                    extractedPageItems = extractedPageItems,
                    isExtractingPages = isExtractingPages,
                    selectedForSwapIndex = selectedForSwapIndex,
                    selectedFileName = selectedFileName,
                    onUpdatePageItems = { extractedPageItems = it },
                    onUpdateSwapIndex = { selectedForSwapIndex = it },
                    onClearSelection = {
                        selectedFileUri = null
                        selectedFileName = ""
                        extractedPageItems = emptyList()
                        selectedForSwapIndex = null
                    },
                    currentDensity = currentDensity
                )
            } else {
                // High Fidelity File Upload Target
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.24f), RoundedCornerShape(16.dp))
                        .clickable {
                            if (isMergeMode) {
                                multiplePdfPickerLauncher.launch("application/pdf")
                            } else {
                                pdfPickerLauncher.launch("application/pdf")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        val hasSelection = if (isMergeMode) selectedFilesUris.isNotEmpty() else selectedFileUri != null
                        Icon(
                            imageVector = if (hasSelection) Icons.Default.CheckCircle else Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = if (hasSelection) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isMergeMode) {
                                if (selectedFilesUris.isNotEmpty()) "${selectedFilesUris.size} PDFs selected" else "Select Multiple PDF Files"
                            } else {
                                if (selectedFileUri != null) selectedFileName else "Select Source PDF File"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isMergeMode && selectedFilesNames.isNotEmpty()) {
                            Text(
                                text = selectedFilesNames.joinToString(", "),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else {
                            Text(
                                text = if (isMergeMode) "Tap to pick multiple PDF files to merge" else "Tap to choose from device storage files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                            )
                        }
                    }
                }

                // Dynamic Parameters Input Fields
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Operation Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        
                        when (activeToolTask) {
                            "Add Watermark" -> {
                                OutlinedTextField(
                                    value = textParamValue,
                                    onValueChange = { textParamValue = it },
                                    label = { Text("Watermark Text Override") },
                                    placeholder = { Text("CONFIDENTIAL") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            "PDF Compression" -> {
                                Column {
                                    Text("Select Target File Size Quality: $numericParamValue%", fontSize = 13.sp)
                                    Slider(
                                        value = numericParamValue.toFloat(),
                                        onValueChange = { numericParamValue = it.toInt() },
                                        valueRange = 10f..90f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Heavy Compression", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("High Quality", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            "PDF Protection", "PDF Unlock" -> {
                                OutlinedTextField(
                                    value = textParamValue,
                                    onValueChange = { textParamValue = it },
                                    label = { Text("Password Vault Key") },
                                    placeholder = { Text("Type password...") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            "Add Signature" -> {
                                Text("Draw and position your clean vector signature on the last page automatically.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            "PDF Split" -> {
                                Text("Splits PDF by generating separate individual PDF documents for every single page in the document.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            "Rotate Pages" -> {
                                Text("Standard 90° clockwise orientation rotation across all pages.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            "Delete Pages" -> {
                                Text("Trims out page 1 dynamically and updates the rest.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            "Merge multiple PDFs" -> {
                                Text("Combine and concatenate multiple chosen PDF files sequentially into a single unified PDF file.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // CTA Execution Target
            Button(
                onClick = {
                    if (isReorderMode) {
                        val uri = selectedFileUri
                        if (uri == null) {
                            Toast.makeText(context, "Please select an active PDF first.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (extractedPageItems.isEmpty()) {
                            Toast.makeText(context, "Page extraction in progress or document has no readable pages.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val file = getFileFromUri(context, uri, selectedFileName) ?: return@Button
                        val order = extractedPageItems.map { it.originalIndex }
                        
                        viewModel.performPdfPageReorder(file, order) {
                            file.delete()
                            // Clear temp directories
                            if (reorderTempDir.exists()) {
                                reorderTempDir.deleteRecursively()
                            }
                            selectedFileUri = null
                            selectedFileName = ""
                            extractedPageItems = emptyList()
                            selectedForSwapIndex = null
                            Toast.makeText(context, "Successfully compiled and saved reordered PDF!", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    } else if (isMergeMode) {
                        if (selectedFilesUris.isEmpty()) {
                            Toast.makeText(context, "Please select at least two PDFs to merge.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val files = selectedFilesUris.mapIndexed { idx, uri ->
                            getFileFromUri(context, uri, selectedFilesNames.getOrNull(idx) ?: "temp_$idx.pdf")
                        }.filterNotNull()
                        
                        if (files.size < 2) {
                            Toast.makeText(context, "Failed to load files or didn't select enough valid items.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        viewModel.performPostPdfMerge(files) {
                            files.forEach { it.delete() }
                            selectedFilesUris = emptyList()
                            selectedFilesNames = emptyList()
                            Toast.makeText(context, "Merged successfully!", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    } else {
                        val uri = selectedFileUri
                        if (uri == null) {
                            Toast.makeText(context, "Please select an active PDF first.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val file = getFileFromUri(context, uri, selectedFileName) ?: return@Button
                        
                        viewModel.performAdvancedPdfTool(
                            action = activeToolTask,
                            pdfFile = file,
                            textParam = textParamValue,
                            intParam = numericParamValue
                        ) {
                            file.delete()
                            selectedFileUri = null
                            selectedFileName = ""
                            Toast.makeText(context, "Completed! Operations processed successfully.", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = if (isReorderMode) {
                        "Save Reordered PDF Document"
                    } else if (isMergeMode) {
                        "Merge Selected PDFs"
                    } else {
                        "Process PDF Changes"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        // Overlay dialog
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
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

data class PageItem(
    val id: String,
    val originalIndex: Int,
    val file: File
)

@Composable
fun ColumnScope.ReorderWorkbench(
    extractedPageItems: List<PageItem>,
    isExtractingPages: Boolean,
    selectedForSwapIndex: Int?,
    selectedFileName: String,
    onUpdatePageItems: (List<PageItem>) -> Unit,
    onUpdateSwapIndex: (Int?) -> Unit,
    onClearSelection: () -> Unit,
    currentDensity: Float
) {
    val context = LocalContext.current
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Page Arrangement Workbench",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = selectedFileName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Unselect PDF",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            if (isExtractingPages) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Extracting pages as high-fidelity interactive templates...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (extractedPageItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Unable to read pages. Select another PDF.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                // Swap instruction banner
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "💡 Drag the handles, click arrow icons, or use \"Tap to Swap Mode\" by selecting two pages to swap positions visually.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    
                    if (selectedForSwapIndex != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer.copy(0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Page ${selectedForSwapIndex + 1} chosen. Select target page to SWAP.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Cancel",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable { onUpdateSwapIndex(null) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                // Visual Scrollable Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = extractedPageItems,
                        key = { _, item -> item.id }
                    ) { index, item ->
                        val isSelected = selectedForSwapIndex == index
                        
                        // Local page thumbnail bitmap
                        val bitmap = remember(item.file) {
                            try {
                                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                                BitmapFactory.decodeFile(item.file.absolutePath, options)?.asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }

                        // Drag offset states for local gesture feedback
                        var localDragOffsetX by remember { mutableStateOf(0f) }
                        var isDraggingThis by remember { mutableStateOf(false) }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(0.7f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                // Handle tap swap
                                .clickable {
                                    if (selectedForSwapIndex == null) {
                                        onUpdateSwapIndex(index)
                                    } else {
                                        if (selectedForSwapIndex != index) {
                                            val mutable = extractedPageItems.toMutableList()
                                            // swap elements
                                            val temp = mutable[selectedForSwapIndex]
                                            mutable[selectedForSwapIndex] = mutable[index]
                                            mutable[index] = temp
                                            onUpdatePageItems(mutable)
                                            Toast.makeText(context, "Swapped Page ${selectedForSwapIndex + 1} with Page ${index + 1}", Toast.LENGTH_SHORT).show()
                                        }
                                        onUpdateSwapIndex(null)
                                    }
                                }
                                .offset(x = localDragOffsetX.dp)
                                // Add Drag Gesture to Page Item
                                .pointerInput(index, extractedPageItems) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            isDraggingThis = true
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            localDragOffsetX += dragAmount.x / currentDensity
                                            
                                            // Drag threshold calculation
                                            val thresholdDp = 100f
                                            if (localDragOffsetX > thresholdDp) {
                                                if (index < extractedPageItems.lastIndex) {
                                                    val mutable = extractedPageItems.toMutableList()
                                                    val temp = mutable[index]
                                                    mutable[index] = mutable[index + 1]
                                                    mutable[index + 1] = temp
                                                    onUpdatePageItems(mutable)
                                                    localDragOffsetX = 0f
                                                    isDraggingThis = false
                                                }
                                            } else if (localDragOffsetX < -thresholdDp) {
                                                if (index > 0) {
                                                    val mutable = extractedPageItems.toMutableList()
                                                    val temp = mutable[index]
                                                    mutable[index] = mutable[index - 1]
                                                    mutable[index - 1] = temp
                                                    onUpdatePageItems(mutable)
                                                    localDragOffsetX = 0f
                                                    isDraggingThis = false
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            localDragOffsetX = 0f
                                            isDraggingThis = false
                                        },
                                        onDragCancel = {
                                            localDragOffsetX = 0f
                                            isDraggingThis = false
                                        }
                                    )
                                }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Thumbnail header with index and swap helper
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Text(
                                            text = "Item P.${item.originalIndex}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    // Drag handle visual indicator
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = if (isDraggingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Visual render of page image
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(Color.White)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Page Render",
                                            modifier = Modifier.fillMaxHeight().clip(RoundedCornerShape(4.dp))
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error.copy(0.2f),
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

                                // Operations footer bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left shift
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val mutable = extractedPageItems.toMutableList()
                                                val temp = mutable[index]
                                                mutable[index] = mutable[index - 1]
                                                mutable[index - 1] = temp
                                                onUpdatePageItems(mutable)
                                            }
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowLeft,
                                            contentDescription = "Move Left",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Page Modifier Actions
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Duplicate
                                        IconButton(
                                            onClick = {
                                                val mutable = extractedPageItems.toMutableList()
                                                val copy = item.copy(id = "page_copy_${System.currentTimeMillis()}_${index}")
                                                mutable.add(index + 1, copy)
                                                onUpdatePageItems(mutable)
                                                Toast.makeText(context, "Duplicated Page ${index + 1}", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Page",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }

                                        // Delete
                                        IconButton(
                                            onClick = {
                                                if (extractedPageItems.size > 1) {
                                                    val mutable = extractedPageItems.toMutableList()
                                                    mutable.removeAt(index)
                                                    onUpdatePageItems(mutable)
                                                    Toast.makeText(context, "Deleted Page ${index + 1}", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "A document must contain at least one page.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Page",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }

                                    // Right shift
                                    IconButton(
                                        onClick = {
                                            if (index < extractedPageItems.lastIndex) {
                                                val mutable = extractedPageItems.toMutableList()
                                                val temp = mutable[index]
                                                mutable[index] = mutable[index + 1]
                                                mutable[index + 1] = temp
                                                onUpdatePageItems(mutable)
                                            }
                                        },
                                        enabled = index < extractedPageItems.lastIndex,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight,
                                            contentDescription = "Move Right",
                                            modifier = Modifier.size(18.dp)
                                        )
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
