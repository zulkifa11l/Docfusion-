package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.HistoryEntry
import com.example.ui.viewmodel.DocFusionViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DocumentHistoryScreen(
    viewModel: DocFusionViewModel,
    initialCategory: String = "All",
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val historyList by viewModel.history.collectAsState()
    val favoritesList by viewModel.favorites.collectAsState()
    val secureList by viewModel.secureFiles.collectAsState()

    var activeCategory by remember { mutableStateOf(initialCategory) }
    var searchQuery by remember { mutableStateOf("") }
    
    var showRenameDialog by remember { mutableStateOf<HistoryEntry?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    var showSaveAsDialog by remember { mutableStateOf<HistoryEntry?>(null) }
    var saveAsInputText by remember { mutableStateOf("") }
    var exportWithPassword by remember { mutableStateOf(false) }
    var exportPasswordText by remember { mutableStateOf("") }

    var showEditTagsDialog by remember { mutableStateOf<HistoryEntry?>(null) }
    var tagsInputText by remember { mutableStateOf("") }
    var selectedFilterTag by remember { mutableStateOf<String?>(null) }

    // Multi-Selection State
    var isSelectMode by remember { mutableStateOf(false) }
    val selectedEntries = remember { mutableStateListOf<HistoryEntry>() }

    // Secure Folder Vault PIN Prompt state
    var isSecureVaultUnlocked by remember { mutableStateOf(false) }
    var showPinPromptDialog by remember { mutableStateOf(false) }
    var pinInputValue by remember { mutableStateOf("") }
    var pinErrorMsg by remember { mutableStateOf("") }

    // List of active files matching selection
    val displayedFiles = remember(historyList, favoritesList, secureList, activeCategory, searchQuery, selectedFilterTag) {
        val baseList = when (activeCategory) {
            "Secure" -> if (isSecureVaultUnlocked) secureList else emptyList()
            "Favorite" -> favoritesList
            "All" -> historyList.filter { !it.isSecure }
            else -> historyList.filter { !it.isSecure && it.category == activeCategory }
        }
        baseList.filter {
            val matchesSearch = searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true)
            val matchesTag = selectedFilterTag == null || it.tags?.split(",")?.map { t -> t.trim() }?.contains(selectedFilterTag) == true
            matchesSearch && matchesTag
        }
    }

    // Automatically trigger PIN dialog if Category is Secure and not unlocked
    LaunchedEffect(activeCategory) {
        if (activeCategory == "Secure" && !isSecureVaultUnlocked) {
            if (viewModel.settingsManager.appLockPin == null) {
                activeCategory = "All"
                Toast.makeText(context, "Please set up an App Lock PIN to activate Secure Vault.", Toast.LENGTH_SHORT).show()
            } else {
                showPinPromptDialog = true
            }
        }
    }

    fun shareDocFile(c: Context, entry: HistoryEntry) {
        val file = File(entry.path)
        if (!file.exists()) {
            Toast.makeText(c, "Physical file does not exist on storage.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // Standard FileProvider share intent logic
            val authority = "${context.packageName}.provider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = when (entry.category) {
                    "PDF" -> "application/pdf"
                    "Word" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "Images" -> "image/jpeg"
                    else -> "text/plain"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            c.startActivity(Intent.createChooser(shareIntent, "Share Document via..."))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(c, "Error sharing file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectMode) {
                        Text("${selectedEntries.size} Selected", fontWeight = FontWeight.Bold)
                    } else {
                        Text(text = "$activeCategory Documents", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectMode) {
                            isSelectMode = false
                            selectedEntries.clear()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(if (isSelectMode) Icons.Default.Close else Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSelectMode) {
                        // Action buttons for Multi-Select
                        val areAllPdfs = selectedEntries.isNotEmpty() && selectedEntries.all { it.category == "PDF" }
                        if (areAllPdfs && selectedEntries.size > 1) {
                            IconButton(onClick = {
                                val filesList = selectedEntries.map { File(it.path) }.filter { it.exists() }
                                if (filesList.isNotEmpty()) {
                                    viewModel.performPostPdfMerge(filesList) {
                                        Toast.makeText(context, "Successfully merged selected PDFs!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                isSelectMode = false
                                selectedEntries.clear()
                            }) {
                                Icon(Icons.Default.MergeType, contentDescription = "Merge selected PDFs")
                            }
                        }
                        IconButton(onClick = {
                            selectedEntries.forEach { viewModel.toggleFavorite(it) }
                            isSelectMode = false
                            selectedEntries.clear()
                        }) {
                            Icon(Icons.Default.Favorite, contentDescription = "Favorite All")
                        }
                        IconButton(onClick = {
                            selectedEntries.forEach { viewModel.deleteFile(it) }
                            isSelectMode = false
                            selectedEntries.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete All")
                        }
                        IconButton(onClick = {
                            if (selectedEntries.isNotEmpty()) {
                                shareDocFile(context, selectedEntries.first()) // Shares representative first file
                            }
                            isSelectMode = false
                            selectedEntries.clear()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    } else {
                        IconButton(onClick = {
                            if (viewModel.settingsManager.appLockPin != null) {
                                activeCategory = "Secure"
                            } else {
                                Toast.makeText(context, "Configure PIN in App settings to lock secure files.", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                imageVector = if (isSecureVaultUnlocked && activeCategory == "Secure") Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = "Secure Vault",
                                tint = if (activeCategory == "Secure") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search documents here...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Category Tabs Row (skip if in Secure unlocked mode to preserve layout simplicity)
            if (activeCategory != "Secure") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    val categories = listOf("All", "PDF", "Scan", "Word", "Favorite")
                    categories.forEach { cat ->
                        val isSel = activeCategory == cat
                        val containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(0.40f)
                        val contentColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(containerColor)
                                .clickable {
                                    activeCategory = cat
                                    isSelectMode = false
                                    selectedEntries.clear()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(cat, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = contentColor)
                        }
                    }
                }
            }

            // Scrollable Tags Row to Filter Documents
            val allAvailableTags = remember(historyList, favoritesList, secureList) {
                val tags = mutableSetOf<String>()
                (historyList + favoritesList + secureList).forEach { entry ->
                    entry.tags?.split(",")?.forEach {
                        val trimmed = it.trim()
                        if (trimmed.isNotEmpty()) {
                            tags.add(trimmed)
                        }
                    }
                }
                tags.toList().sorted()
            }

            if (allAvailableTags.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    item {
                        val isSelected = selectedFilterTag == null
                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(0.40f)
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(containerColor)
                                .clickable { selectedFilterTag = null }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("All Labels", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentColor)
                        }
                    }
                    
                    items(allAvailableTags) { tag ->
                        val isSelected = selectedFilterTag == tag
                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(0.40f)
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(containerColor)
                                .clickable {
                                    selectedFilterTag = if (selectedFilterTag == tag) null else tag
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(tag, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentColor)
                        }
                    }
                }
            }

            // Results List
            if (displayedFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (activeCategory == "Secure" && !isSecureVaultUnlocked) Icons.Default.Lock else Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (activeCategory == "Secure" && !isSecureVaultUnlocked) "Secure vault locked" else "No files in $activeCategory",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedFiles) { entry ->
                        val isSelected = selectedEntries.contains(entry)
                        
                        HistoryEntryCard(
                            entry = entry,
                            isSelected = isSelected,
                            isSelectMode = isSelectMode,
                            onClick = {
                                if (isSelectMode) {
                                    if (isSelected) selectedEntries.remove(entry) else selectedEntries.add(entry)
                                    if (selectedEntries.isEmpty()) isSelectMode = false
                                } else {
                                    shareDocFile(context, entry)
                                }
                            },
                            onLongClick = {
                                isSelectMode = true
                                selectedEntries.add(entry)
                            },
                            onFavoriteToggle = { viewModel.toggleFavorite(entry) },
                            onSecurityToggle = {
                                val pin = viewModel.settingsManager.appLockPin ?: "1234"
                                viewModel.toggleFileSecurity(entry, pin)
                            },
                            onRename = {
                                renameInputText = entry.name
                                showRenameDialog = entry
                            },
                            onDelete = {
                                viewModel.deleteFile(entry)
                                Toast.makeText(context, "${entry.name} deleted.", Toast.LENGTH_SHORT).show()
                            },
                            onSaveAs = {
                                saveAsInputText = entry.name
                                exportWithPassword = false
                                exportPasswordText = ""
                                showSaveAsDialog = entry
                            },
                            onEditTags = {
                                tagsInputText = entry.tags ?: ""
                                showEditTagsDialog = entry
                            }
                        )
                    }
                }
            }
        }

        // Rename Dialog
        if (showRenameDialog != null) {
            val entry = showRenameDialog!!
            AlertDialog(
                onDismissRequest = { showRenameDialog = null },
                title = { Text("Rename File", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Enter new name for the file:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = renameInputText,
                            onValueChange = { renameInputText = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (renameInputText.isNotBlank()) {
                            viewModel.renameFile(entry, renameInputText.trim())
                            showRenameDialog = null
                        }
                    }) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Save As / Export Dialog
        if (showSaveAsDialog != null) {
            val entry = showSaveAsDialog!!
            val isPdf = entry.category == "PDF" || entry.name.endsWith(".pdf", ignoreCase = true) || saveAsInputText.endsWith(".pdf", ignoreCase = true)
            
            AlertDialog(
                onDismissRequest = { showSaveAsDialog = null },
                title = { Text("Save As & Export", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Specify name for the exported document:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = saveAsInputText,
                            onValueChange = { saveAsInputText = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        
                        if (isPdf) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { exportWithPassword = !exportWithPassword }
                            ) {
                                Checkbox(
                                    checked = exportWithPassword,
                                    onCheckedChange = { exportWithPassword = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Encrypt PDF with Password", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            
                            if (exportWithPassword) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = exportPasswordText,
                                    onValueChange = { exportPasswordText = it },
                                    placeholder = { Text("Enter encryption password...") },
                                    label = { Text("Document Encryption Password") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Note: Remember this password! To open/unlock the PDF, use our Advanced PDF Unlock option.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "This file will be compiled and exported directly to public Downloads/Docfusion folder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    val isConfirmEnabled = !exportWithPassword || exportPasswordText.isNotBlank()
                    Button(
                        onClick = {
                            if (saveAsInputText.isNotBlank()) {
                                val trimmedName = saveAsInputText.trim()
                                val originalFile = File(entry.path)
                                if (originalFile.exists()) {
                                    val uri = if (isPdf && exportWithPassword && exportPasswordText.isNotBlank()) {
                                        try {
                                            val tempEncrypted = File(context.cacheDir, "encrypted_export_${System.currentTimeMillis()}.pdf")
                                            com.example.util.PdfProcessor.encryptDecryptFile(
                                                context,
                                                originalFile,
                                                tempEncrypted,
                                                exportPasswordText.trim(),
                                                true
                                            )
                                            val resultUri = com.example.util.exportFileToDownloads(context, tempEncrypted, trimmedName)
                                            tempEncrypted.delete()
                                            resultUri
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            null
                                        }
                                    } else {
                                        com.example.util.exportFileToDownloads(context, originalFile, trimmedName)
                                    }
                                    
                                    if (uri != null) {
                                        if (isPdf && exportWithPassword && exportPasswordText.isNotBlank()) {
                                            Toast.makeText(context, "Password encrypted PDF saved to Downloads/Docfusion !", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Exported successfully to Downloads/Docfusion !", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Failed to copy file to storage.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Original resource file not found in cache.", Toast.LENGTH_SHORT).show()
                                }
                                showSaveAsDialog = null
                            }
                        },
                        enabled = isConfirmEnabled
                    ) {
                        Text("Export")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveAsDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Secure PIN Verification Dialog
        if (showPinPromptDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPinPromptDialog = false
                    if (!isSecureVaultUnlocked) activeCategory = "All"
                },
                title = { Text("Unlock Secure Vault", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Please enter your App Lock PIN code to open files vault.")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pinInputValue,
                            onValueChange = { pinInputValue = it },
                            placeholder = { Text("PIN") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            isError = pinErrorMsg.isNotEmpty()
                        )
                        if (pinErrorMsg.isNotEmpty()) {
                            Text(pinErrorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (viewModel.settingsManager.verifyPin(pinInputValue)) {
                            isSecureVaultUnlocked = true
                            showPinPromptDialog = false
                            pinInputValue = ""
                            pinErrorMsg = ""
                        } else {
                            pinErrorMsg = "Incorrect PIN code."
                        }
                    }) {
                        Text("Verify Unlock")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPinPromptDialog = false
                        activeCategory = "All"
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Edit Document Tags Dialog
        if (showEditTagsDialog != null) {
            val entry = showEditTagsDialog!!
            AlertDialog(
                onDismissRequest = { showEditTagsDialog = null },
                title = { Text("Edit Document Tags", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Assign custom tags to organize your files (use commas to separate multiple labels):", style = MaterialTheme.typography.bodySmall)
                        
                        OutlinedTextField(
                            value = tagsInputText,
                            onValueChange = { tagsInputText = it },
                            placeholder = { Text("Work, Reference, Invoice, Receipt") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Text("Quick Presets:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val quickPresets = listOf("Work", "Invoice", "Receipt", "Urgent")
                            quickPresets.forEach { pre ->
                                val curSelected = tagsInputText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val active = curSelected.contains(pre)
                                FilterChip(
                                    selected = active,
                                    onClick = {
                                        if (active) {
                                            tagsInputText = curSelected.filter { it != pre }.joinToString(", ")
                                        } else {
                                            tagsInputText = if (tagsInputText.isBlank()) pre else "$tagsInputText, $pre"
                                        }
                                    },
                                    label = { Text(pre) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.updateHistoryTags(entry.id, tagsInputText.trim())
                        showEditTagsDialog = null
                    }) {
                        Text("Save Tags")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditTagsDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryEntryCard(
    entry: HistoryEntry,
    isSelected: Boolean,
    isSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onSecurityToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSaveAs: () -> Unit,
    onEditTags: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("MMMd, yyyy h:mm a", Locale.getDefault()) }
    val formattedDate = remember(entry.timestamp) { sdf.format(Date(entry.timestamp)) }
    val formattedSize = remember(entry.fileSize) {
        val kb = entry.fileSize / 1024
        if (kb > 1024) String.format(Locale.US, "%.1f MB", kb / 1024f) else "$kb KB"
    }

    var showMenu by remember { mutableStateOf(false) }

    val icon = when (entry.category) {
        "PDF" -> Icons.Default.PictureAsPdf
        "Scan" -> Icons.Default.Scanner
        "Word" -> Icons.Default.Article
        "Images" -> Icons.Default.Image
        else -> Icons.Default.InsertDriveFile
    }

    val colors = CardDefaults.cardColors(
        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                         else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formattedDate, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.80f))
                    Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.40f))
                    Text(formattedSize, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.80f))
                }

                // Render File Tags row below the metadata details
                val entryTagsList = remember(entry.tags) {
                    entry.tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                }
                if (entryTagsList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        entryTagsList.take(3).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (entryTagsList.size > 3) {
                            Text("+${entryTagsList.size - 3}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (!isSelectMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onFavoriteToggle) {
                        Icon(
                            imageVector = if (entry.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (entry.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open / Share") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Labels/Tags") },
                                leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onEditTags()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save As (Export)") },
                                leadingIcon = { Icon(Icons.Default.SaveAlt, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onSaveAs()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Vault Secure Toggle") },
                                leadingIcon = { Icon(if (entry.isSecure) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onSecurityToggle()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename File") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete File") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
