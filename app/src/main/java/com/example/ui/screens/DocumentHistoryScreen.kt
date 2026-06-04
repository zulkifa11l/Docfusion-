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
    
    // Multi-Selection State
    var isSelectMode by remember { mutableStateOf(false) }
    val selectedEntries = remember { mutableStateListOf<HistoryEntry>() }

    // Secure Folder Vault PIN Prompt state
    var isSecureVaultUnlocked by remember { mutableStateOf(false) }
    var showPinPromptDialog by remember { mutableStateOf(false) }
    var pinInputValue by remember { mutableStateOf("") }
    var pinErrorMsg by remember { mutableStateOf("") }

    // List of active files matching selection
    val displayedFiles = remember(historyList, favoritesList, secureList, activeCategory, searchQuery) {
        val baseList = when (activeCategory) {
            "Secure" -> if (isSecureVaultUnlocked) secureList else emptyList()
            "Favorite" -> favoritesList
            "All" -> historyList.filter { !it.isSecure }
            else -> historyList.filter { !it.isSecure && it.category == activeCategory }
        }
        baseList.filter {
            searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true)
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
                                    // Open options modal sheet or share file directly!
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
                            }
                        )
                    }
                }
            }
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
    onSecurityToggle: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("MMMd, yyyy h:mm a", Locale.getDefault()) }
    val formattedDate = remember(entry.timestamp) { sdf.format(Date(entry.timestamp)) }
    val formattedSize = remember(entry.fileSize) {
        val kb = entry.fileSize / 1024
        if (kb > 1024) String.format(Locale.US, "%.1f MB", kb / 1024f) else "$kb KB"
    }

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
            }

            if (!isSelectMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onFavoriteToggle) {
                        Icon(
                            imageVector = if (entry.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (entry.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onSecurityToggle) {
                        Icon(
                            imageVector = if (entry.isSecure) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Security Vault",
                            tint = if (entry.isSecure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
