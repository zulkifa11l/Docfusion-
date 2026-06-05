package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.HistoryEntry
import com.example.ui.viewmodel.DocFusionViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DocFusionViewModel,
    onNavigateToScan: () -> Unit,
    onNavigateToConvert: () -> Unit,
    onNavigateToPdfTools: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onNavigateToHistory: (String) -> Unit, // pass category
    onNavigateToNotes: () -> Unit,
    onNavigateToPhotoStudio: () -> Unit
) {
    val historyList by viewModel.history.collectAsState()
    val favoritesList by viewModel.favorites.collectAsState()
    val secureFiles by viewModel.secureFiles.collectAsState()
    val secureNotes by viewModel.secureNotes.collectAsState()
    val publicNotes by viewModel.publicNotes.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }

    val recentFiles = remember(historyList, searchQuery) {
        historyList.filter {
            val dateFormatted1 = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp))
            val dateFormatted2 = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(it.timestamp))
            val dateFormatted3 = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it.timestamp))
            val dateFormatted4 = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(it.timestamp))

            !it.isSecure && (searchQuery.isEmpty() || 
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    dateFormatted1.contains(searchQuery, ignoreCase = true) ||
                    dateFormatted2.contains(searchQuery, ignoreCase = true) ||
                    dateFormatted3.contains(searchQuery, ignoreCase = true) ||
                    dateFormatted4.contains(searchQuery, ignoreCase = true))
        }.take(5)
    }

    val context = LocalContext.current

    // Backup & Restore Dialog Triggers
    var showBackupExportDialog by remember { mutableStateOf(false) }
    var backupNameInput by remember { mutableStateOf("DocFusion_Backup_${System.currentTimeMillis() / 1000}.zip") }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showContactUsDialog by remember { mutableStateOf(false) }

    val backupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.restoreData(uri) { success ->
                if (success) {
                    Toast.makeText(context, "All notes, vault database, and matching documents restored!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed to import backup selected. Verify zip format.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Docfusion",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "ALL-IN-ONE UTILITY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.toggleDarkMode()
                        }
                    ) {
                        Icon(
                            imageVector = if (viewModel.isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                    if (viewModel.settingsManager.isAppLockEnabled) {
                        IconButton(onClick = { viewModel.lockApp() }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock App")
                        }
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "JD",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search documents, tools, notes...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            // Storage Statistics Dashboard Card
            item {
                val docSize = remember(historyList) {
                    derivedStateOf {
                        var total = 0L
                        historyList.forEach { if (!it.isSecure) total += it.fileSize }
                        total
                    }
                }.value

                val notesSize = remember(publicNotes) {
                    derivedStateOf {
                        var total = 0L
                        publicNotes.forEach { note ->
                            total += (note.title.length + note.content.length) * 2L
                            note.audioPath?.let { path ->
                                val f = File(path)
                                if (f.exists()) total += f.length()
                            }
                        }
                        total
                    }
                }.value

                val vaultSize = remember(secureFiles, secureNotes) {
                    derivedStateOf {
                        var total = 0L
                        secureFiles.forEach { total += it.fileSize }
                        secureNotes.forEach { note ->
                            total += (note.title.length + note.content.length) * 2L
                            note.audioPath?.let { path ->
                                val f = File(path)
                                if (f.exists()) total += f.length()
                            }
                        }
                        total
                    }
                }.value

                val totalSize = docSize + notesSize + vaultSize
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.7f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Header with Icon
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "Storage Health & Insights",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Total: ${formatBytes(totalSize)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stacked Multi-segment Bar Visualizer
                        val docsWeight = if (totalSize > 0) docSize.toFloat() / totalSize else 0f
                        val notesWeight = if (totalSize > 0) notesSize.toFloat() / totalSize else 0f
                        val vaultWeight = if (totalSize > 0) vaultSize.toFloat() / totalSize else 0f

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                        ) {
                            if (totalSize == 0L) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
                                )
                            } else {
                                if (docsWeight > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(docsWeight)
                                            .background(Color(0xFF2196F3)) // Vibrant Blue
                                    )
                                }
                                if (notesWeight > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(notesWeight)
                                            .background(Color(0xFFFF9800)) // Energetic Amber
                                    )
                                }
                                if (vaultWeight > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(vaultWeight)
                                            .background(Color(0xFFE91E63)) // Crimson Pink
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stats Grid Breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Documents Segment Info
                            StorageCategoryLegend(
                                title = "Documents",
                                sizeLabel = formatBytes(docSize),
                                color = Color(0xFF2196F3),
                                percentage = if (totalSize > 0L) (docsWeight * 100).toInt() else 0,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Notes Segment Info
                            StorageCategoryLegend(
                                title = "Notes & Voice",
                                sizeLabel = formatBytes(notesSize),
                                color = Color(0xFFFF9800),
                                percentage = if (totalSize > 0L) (notesWeight * 100).toInt() else 0,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Vault Segment Info
                            StorageCategoryLegend(
                                title = "Encrypted Vault",
                                sizeLabel = formatBytes(vaultSize),
                                color = Color(0xFFE91E63),
                                percentage = if (totalSize > 0L) (vaultWeight * 100).toInt() else 0,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Quick Operations Grid
            item {
                Text(
                    text = "Quick Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 3
                ) {
                    val itemWidthModifier = Modifier
                        .weight(1f)
                        .minWidth(100.dp)
                    
                    QuickToolCard(
                        title = "Smart Scan",
                        icon = Icons.Default.CameraAlt,
                        color = Color(0xFF4CAF50),
                        onClick = onNavigateToScan,
                        modifier = itemWidthModifier
                    )
                    QuickToolCard(
                        title = "Convert",
                        icon = Icons.Default.Transform,
                        color = Color(0xFF2196F3),
                        onClick = onNavigateToConvert,
                        modifier = itemWidthModifier
                    )
                    QuickToolCard(
                        title = "PDF Tools",
                        icon = Icons.Default.PictureAsPdf,
                        color = Color(0xFFE91E63),
                        onClick = onNavigateToPdfTools,
                        modifier = itemWidthModifier
                    )
                    QuickToolCard(
                        title = "AI OCR",
                        icon = Icons.Default.Psychology,
                        color = Color(0xFF9C27B0),
                        onClick = onNavigateToOcr,
                        modifier = itemWidthModifier
                    )
                    QuickToolCard(
                        title = "Rich Notes",
                        icon = Icons.Default.NoteAlt,
                        color = Color(0xFFFF9800),
                        onClick = onNavigateToNotes,
                        modifier = itemWidthModifier
                    )
                    QuickToolCard(
                        title = "Secure Vault",
                        icon = Icons.Default.VerifiedUser,
                        color = Color(0xFF607D8B),
                        onClick = { onNavigateToHistory("Secure") },
                        modifier = itemWidthModifier
                    )
                    QuickToolCard(
                        title = "Photo Studio",
                        icon = Icons.Default.Face,
                        color = Color(0xFF009688),
                        onClick = onNavigateToPhotoStudio,
                        modifier = itemWidthModifier
                    )
                }
            }

            // Categories list
            item {
                Text(
                    text = "File Hubs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CategoryHubCard("PDFs", Icons.Default.Description, Color(0xFFEC407A).copy(0.15f), onClick = { onNavigateToHistory("PDF") })
                    CategoryHubCard("Scans", Icons.Default.Scanner, Color(0xFF26A69A).copy(0.15f), onClick = { onNavigateToHistory("Scan") })
                    CategoryHubCard("Words", Icons.Default.Article, Color(0xFF42A5F5).copy(0.15f), onClick = { onNavigateToHistory("Word") })
                    CategoryHubCard("Favs", Icons.Default.Favorite, Color(0xFFFFCA28).copy(0.15f), onClick = { onNavigateToHistory("Favorite") })
                }
            }

            // Sleek Pro Tip Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PRO TIP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiary,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Merge 5+ images into a single PDF instantly with PDF Tools.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onTertiary,
                                lineHeight = 16.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Secure Backup & Trust Center
            item {
                Text(
                    text = "Backup & Trust Center",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Port, migrate or secure all your documents, rich text/voice notes, and metadata offline to your local storage or external media.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showBackupExportDialog = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Backup,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("Export Data", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = { backupPickerLauncher.launch("*/*") },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("Import Data", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPrivacyPolicyDialog = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Policy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Privacy Statement & Security Policy",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Learn about 100% offline data guarantee & credentials.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showContactUsDialog = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContactSupport,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Contact Us & Support",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Connect with M.Zulkifal Khan via WhatsApp or Email.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // High Fidelity Real Recent Files List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Documents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { onNavigateToHistory("All") }) {
                        Text("View All")
                    }
                }
            }

            if (recentFiles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No documents found",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Scanned or converted files will appear here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.8f)
                            )
                        }
                    }
                }
            } else {
                items(recentFiles) { entry ->
                    RecentFileItem(
                        entry = entry,
                        onClick = { onNavigateToHistory("All") }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Backup Export Dialog
    if (showBackupExportDialog) {
        AlertDialog(
            onDismissRequest = { showBackupExportDialog = false },
            title = { Text("Export Secure Backup", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This will bundle all your offline notes, voice recordings, scans, and metadata into a highly portable backup file.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = backupNameInput,
                        onValueChange = { backupNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        label = { Text("Backup File Name") },
                        singleLine = true
                    )
                    Text(
                        text = "File will be saved under Downloads/Docfusion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = backupNameInput.trim()
                        val finalName = if (input.endsWith(".zip", ignoreCase = true)) input else "$input.zip"
                        viewModel.backupData(finalName) { uri ->
                            if (uri != null) {
                                Toast.makeText(context, "Backup exported successfully!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Error compiling backup.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showBackupExportDialog = false
                    }
                ) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PrivacyTip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Privacy & Security Policy", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Last Updated: June 2026",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Welcome to Docfusion. Your trust is our highest priority. This policy outlines how your information is handled with a absolute guarantee of security.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "1. 100% Offline Architecture",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Docfusion is engineered to run completely offline. All data processing (including PDF creation, CamScanner-style processing, Rich Notes logging, and audio memo recording) occurs strictly on your physical device. We maintain zero cloud storage servers, database proxies, or background telemetry handlers.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "2. Secure Local Vault Protection",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "When you toggle file or note security, entries are isolated in an internal sandbox database accessible only after verifying your custom Vault PIN. Since your PIN hash is stored in local encrypted SharedPreferences, nobody (including the developers) can retrieve your secured files if the PIN is lost.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "3. Sandbox Isolation & Local Storage",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Permissions such as CAMERA or external writes are requested purely to operate physical scanning and compilation. Backups are bundled and saved directly to your local downloads partition, giving you complete custody and portability of your personal data.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "4. Zero-Data Collection & External Audits",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "There are no trackers, third-party analytics scripts, or cookie trackers injected inside of this app. Your files are yours alone.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showPrivacyPolicyDialog = false }) {
                    Text("I Understand")
                }
            }
        )
    }

    // Contact Us & Developer Support Dialog
    if (showContactUsDialog) {
        AlertDialog(
            onDismissRequest = { showContactUsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContactSupport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text("Contact Us", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Have questions, feedback, or need help with Docfusion? Get in touch with us directly offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Developer Profile Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Name
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Developer",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "M.Zulkifal Khan",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // WhatsApp Connection
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                data = Uri.parse("https://api.whatsapp.com/send?phone=92370464248")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: java.lang.Exception) {
                                            Toast.makeText(context, "Could not open WhatsApp automatically.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = null,
                                        tint = Color(0xFF25D366), // Official WhatsApp green
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "WhatsApp Support",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "+92370464248",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF25D366).copy(0.12f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Message",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF1E824C)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Email Connection
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("mailto:")
                                                putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("khanzulkifal650@gmail.com"))
                                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Docfusion Support Inquiry")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: java.lang.Exception) {
                                            Toast.makeText(context, "No email client found.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Email Address",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "khanzulkifal650@gmail.com",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(0.12f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Mail Us",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showContactUsDialog = false }
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun QuickToolCard(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(110.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RowScope.CategoryHubCard(
    title: String,
    icon: ImageVector,
    bgTint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgTint)
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun RecentFileItem(
    entry: HistoryEntry,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("MMMM d, h:mm a", Locale.getDefault()) }
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

    val tint = when (entry.category) {
        "PDF" -> Color(0xFFE91E63)
        "Scan" -> Color(0xFF4CAF50)
        "Word" -> Color(0xFF2196F3)
        "Images" -> Color(0xFF9C27B0)
        else -> Color(0xFF607D8B)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedDate,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = formattedSize,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// Inline extension to clean up width weights in FlowRow setup
private fun Modifier.minWidth(width: androidx.compose.ui.unit.Dp): Modifier = this

@Composable
fun StorageCategoryLegend(
    title: String,
    sizeLabel: String,
    color: Color,
    percentage: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = sizeLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$percentage%",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
        )
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
