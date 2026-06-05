package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AdBanner
import com.example.ui.viewmodel.DocfusionViewModel
import com.example.util.AdMobManager

data class PremiumTool(
    val key: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumToolsScreen(
    viewModel: DocfusionViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val unlockedFeatures by viewModel.unlockedFeatures.collectAsState()

    val premiumTools = remember {
        listOf(
            PremiumTool(
                "ai_summarizer",
                "AI Smart Summarizer",
                "Extract bullet point highlights and key intelligence automatically using local LLM models.",
                Icons.Default.Psychology,
                Color(0xFFE040FB)
            ),
            PremiumTool(
                "ultra_compressor",
                "Lossless PDF Optimizer",
                "Compress files up to 90% without losing visual detail or text structures.",
                Icons.Default.Compress,
                Color(0xFF00E5FF)
            ),
            PremiumTool(
                "digital_signer",
                "Premium E-Signatures",
                "Overlay secure cryptographically signed handwritten vectors onto contract pages.",
                Icons.Default.Gesture,
                Color(0xFF00E676)
            ),
            PremiumTool(
                "vault_encryptor",
                "AES-256 Crypto Vault",
                "Encrypt documents with high-grade security passphrases to defend sensitive info.",
                Icons.Default.Security,
                Color(0xFFFF3D00)
            )
        )
    }

    var selectedToolForUnlock by remember { mutableStateOf<PremiumTool?>(null) }
    var activeToolResult by remember { mutableStateOf<PremiumTool?>(null) }
    var mockExecutionText by remember { mutableStateOf("") }
    var isExecutingAction by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Premium AI Portal",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activity != null) {
                                AdMobManager.showInterstitialOnTransition(activity) {
                                    onBack()
                                }
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("premium_back_btn")
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                AdBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("premium_bottom_banner_ad")
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Introductory Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.24f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiary.copy(0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            "Rewarded Ad Unlocking Room",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "Watch a brief ad to fully unlock pro features for the entire session. Ads assist developers with operation cost.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Grid of Premium Tools
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(premiumTools) { tool ->
                    val isUnlocked = unlockedFeatures.contains(tool.key)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isUnlocked) {
                                    activeToolResult = tool
                                    mockExecutionText = ""
                                } else {
                                    selectedToolForUnlock = tool
                                }
                            }
                            .testTag("premium_tool_card_${tool.key}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUnlocked) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)
                        ),
                        border = BorderStroke(
                            width = if (isUnlocked) 2.dp else 1.dp,
                            color = if (isUnlocked) MaterialTheme.colorScheme.secondary.copy(0.6f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(tool.color.copy(0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tool.icon,
                                        contentDescription = null,
                                        tint = tool.color,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Status Icon Indicator (Lock vs Unlocked)
                                if (isUnlocked) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFE8F5E9))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "ACTIVE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Feature locked",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = tool.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = tool.description,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                                    lineHeight = 13.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Unlocking Request Dialog (Asks confirmation to play the rewarded ad)
    selectedToolForUnlock?.let { tool ->
        AlertDialog(
            onDismissRequest = { selectedToolForUnlock = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text("Unlock ${tool.title}", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "This is a pro feature. Watch a brief rewarded ad to unlock full access to this utility. Granting occurs immediately after completing.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activeActivity = activity
                        if (activeActivity != null) {
                            Toast.makeText(context, "Initiating AdMob Rewarded request...", Toast.LENGTH_SHORT).show()
                            AdMobManager.showRewardedAd(
                                activity = activeActivity,
                                onRewardGranted = {
                                    viewModel.unlockFeature(tool.key)
                                    Toast.makeText(context, "${tool.title} successfully unlocked!", Toast.LENGTH_LONG).show()
                                    // Automatically open the screen after unlock
                                    activeToolResult = tool
                                    mockExecutionText = ""
                                },
                                onFailure = {
                                    Toast.makeText(context, "Ad completed but no watch reward granted", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            // Fallback unlock if not in activity
                            viewModel.unlockFeature(tool.key)
                        }
                        selectedToolForUnlock = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Watch Ad to Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedToolForUnlock = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Active Feature Demonstration Workspace (Runs after successful unlock)
    activeToolResult?.let { tool ->
        AlertDialog(
            onDismissRequest = { activeToolResult = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = tool.icon, contentDescription = null, tint = tool.color)
                    Text("Interactive: ${tool.title}", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "You have unlocked this feature. Enter mock contents to run a demonstration of this tool.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = mockExecutionText,
                        onValueChange = { mockExecutionText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter prompt, document title, status, or parameters...") },
                        maxLines = 3,
                        singleLine = false
                    )

                    if (isExecutingAction) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Simulating premium operations...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (mockExecutionText.isBlank()) {
                            Toast.makeText(context, "Please enter mock parameters to proceed", Toast.LENGTH_SHORT).show()
                        } else {
                            isExecutingAction = true
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                isExecutingAction = false
                                Toast.makeText(context, "Docfusion successfully compiled Premium action results!", Toast.LENGTH_LONG).show()
                                viewModel.addDocument(
                                    name = "Docfusion_" + tool.title.replace(" ", "_") + "_" + System.currentTimeMillis() % 1000,
                                    extension = "pdf",
                                    size = "1.8 MB",
                                    isPremium = true
                                )
                                activeToolResult = null
                            }, 1500)
                        }
                    },
                    enabled = !isExecutingAction
                ) {
                    Text("Execute Premium Action")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { activeToolResult = null },
                    enabled = !isExecutingAction
                ) {
                    Text("Close")
                }
            }
        )
    }
}
