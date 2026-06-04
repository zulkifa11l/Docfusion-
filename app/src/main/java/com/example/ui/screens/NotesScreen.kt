package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Note
import com.example.ui.viewmodel.DocFusionViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: DocFusionViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val notesList by viewModel.publicNotes.collectAsState()
    
    var isSecureView by remember { mutableStateOf(false) }
    val secureNotesList by viewModel.secureNotes.collectAsState()
    
    val activeNotes = if (isSecureView) secureNotesList else notesList

    // Secure Vault PIN popup states
    var isVaultUnlocked by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinValueText by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

    // Notes Creator Popup States
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteTitleInput by remember { mutableStateOf("") }
    var noteContentInput by remember { mutableStateOf("") }
    var recordedVoiceFile by remember { mutableStateOf<File?>(null) }
    var securedNoteToggle by remember { mutableStateOf(false) }

    val sdf = remember { SimpleDateFormat("MMMM d, YYYY • h:mm a", Locale.getDefault()) }

    // Secure validation check
    LaunchedEffect(isSecureView) {
        if (isSecureView && !isVaultUnlocked) {
            val pin = viewModel.settingsManager.appLockPin
            if (pin == null) {
                isSecureView = false
                Toast.makeText(context, "Please configure App PIN to use Secure Notes.", Toast.LENGTH_SHORT).show()
            } else {
                showPinDialog = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = if (isSecureView) "Secure Notes" else "Productivity Notes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isSecureView) {
                            isSecureView = false
                        } else {
                            isSecureView = true
                        }
                    }) {
                        Icon(
                            imageVector = if (isSecureView) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Notes Lock",
                            tint = if (isSecureView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showAddNoteDialog = true
                    securedNoteToggle = isSecureView
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Note", tint = Color.White)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (activeNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.NoteAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No notes found in ${if (isSecureView) "Secure" else "Public"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Tap + to create text or voice memos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(activeNotes) { note ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(note.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                    IconButton(
                                        onClick = { viewModel.deleteNote(note) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = note.content,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // Real voice player attachment
                                note.audioPath?.let { audio ->
                                    val isPlaying = viewModel.isPlayingAudio && viewModel.currentAudioPlayingPath == audio
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(0.24f))
                                            .clickable {
                                                if (isPlaying) viewModel.stopPlayingVoiceNote() else viewModel.playVoiceNote(audio)
                                            }
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Column {
                                                Text("Voice Memo Recording", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                                Text(if (isPlaying) "Playing audio track..." else "Tap to listen memo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                Text(
                                    text = sdf.format(Date(note.timestamp)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Note Dialog Creator
        if (showAddNoteDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (viewModel.isRecordingAudio) viewModel.stopVoiceRecording()
                    showAddNoteDialog = false
                    recordedVoiceFile = null
                    noteTitleInput = ""
                    noteContentInput = ""
                },
                title = { Text(text = "Add Text / Voice Memo", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = noteTitleInput,
                            onValueChange = { noteTitleInput = it },
                            placeholder = { Text("Note Title") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = noteContentInput,
                            onValueChange = { noteContentInput = it },
                            placeholder = { Text("Type details or notes contents...") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                        )

                        // Microphone Recorder layout
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (viewModel.isRecordingAudio) Color.Red.copy(0.12f) 
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(0.40f)
                                )
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (viewModel.isRecordingAudio) {
                                            recordedVoiceFile = viewModel.stopVoiceRecording()
                                        } else {
                                            viewModel.startVoiceRecording()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (viewModel.isRecordingAudio) Color.Red 
                                            else MaterialTheme.colorScheme.primary, 
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = if (viewModel.isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = "Voice note",
                                        tint = Color.White
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (viewModel.isRecordingAudio) "Recording Audio..." else "Record Voice Note",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = if (recordedVoiceFile != null) "Recording saved!" else "Tap mic to capture voice notes",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Lock in Secure Vault", fontSize = 13.sp)
                            Switch(
                                checked = securedNoteToggle,
                                onCheckedChange = { securedNoteToggle = it }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (noteTitleInput.isEmpty() && noteContentInput.isEmpty() && recordedVoiceFile == null) {
                            Toast.makeText(context, "Note cannot be entirely empty.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.saveNote(
                            title = noteTitleInput,
                            content = noteContentInput,
                            isSecured = securedNoteToggle,
                            audioFile = recordedVoiceFile
                        )
                        showAddNoteDialog = false
                        recordedVoiceFile = null
                        noteTitleInput = ""
                        noteContentInput = ""
                        Toast.makeText(context, "Memo successfully saved!", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Save Notes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        if (viewModel.isRecordingAudio) viewModel.stopVoiceRecording()
                        showAddNoteDialog = false
                        recordedVoiceFile = null
                        noteTitleInput = ""
                        noteContentInput = ""
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Lock verification popup dialog
        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPinDialog = false
                    if (!isVaultUnlocked) isSecureView = false
                },
                title = { Text("Unlock Secure Notes", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Verify your 4 digit app lock pin code.")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pinValueText,
                            onValueChange = { pinValueText = it },
                            placeholder = { Text("PIN") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (pinError.isNotEmpty()) {
                            Text(pinError, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (viewModel.settingsManager.verifyPin(pinValueText)) {
                            isVaultUnlocked = true
                            showPinDialog = false
                            pinValueText = ""
                            pinError = ""
                        } else {
                            pinError = "Wrong security PIN code."
                        }
                    }) {
                        Text("Verify")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPinDialog = false
                        isSecureView = false
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
