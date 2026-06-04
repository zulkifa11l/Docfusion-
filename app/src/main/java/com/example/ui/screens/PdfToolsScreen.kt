package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.DocFusionViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    viewModel: DocFusionViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var activeToolTask by remember { mutableStateOf("Add Watermark") }
    
    // Picked Document properties
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }

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
                        "Rotate Pages", "Delete Pages"
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

            // High Fidelity File Upload Target
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.24f), RoundedCornerShape(16.dp))
                    .clickable { pdfPickerLauncher.launch("application/pdf") },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = if (selectedFileUri != null) Icons.Default.CheckCircle else Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = if (selectedFileUri != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (selectedFileUri != null) selectedFileName else "Select Source PDF File",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap to choose from device storage files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                    )
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
                            Text("Splits PDF by generating page 1 to separate document automatically.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        "Rotate Pages" -> {
                            Text("Standard 90° clockwise orientation rotation across all pages.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        "Delete Pages" -> {
                            Text("Trims out page 1 dynamically and updates the rest.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // CTA Execution Target
            Button(
                onClick = {
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
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Process PDF Changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
