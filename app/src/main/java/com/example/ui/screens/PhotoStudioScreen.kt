package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.media.FaceDetector
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.print.PrintHelper
import com.example.ui.viewmodel.DocFusionViewModel
import com.example.data.HistoryEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoStudioScreen(
    viewModel: DocFusionViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) }
    
    // Original untouched source image backup
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // Interactive active canvas bitmap (all transformations accumulate here or run dynamically)
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // File metadata
    var imageSourceLabel by remember { mutableStateOf("No source chosen") }
    var scaleQuality by remember { mutableStateOf(95f) } // 0-100%

    // 1. Gallery Pick Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(inputStream)
                if (bmp != null) {
                    originalBitmap = bmp
                    currentBitmap = bmp
                    imageSourceLabel = "Imported from Gallery"
                    activeTab = 1 // redirect to edit tab
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading image file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 2. Camera Take Preview Launcher (Saves directly to Bitmap reliably)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            originalBitmap = bmp
            currentBitmap = bmp
            imageSourceLabel = "Captured with Camera"
            activeTab = 1 // redirect to edit tab
        }
    }

    // Tab items array
    val tabs = listOf(
        TabItem("Source", Icons.Default.Source),
        TabItem("Dimensions", Icons.Default.AspectRatio),
        TabItem("Enhance", Icons.Default.ColorLens),
        TabItem("Passport", Icons.Default.Face),
        TabItem("Export & Print", Icons.Default.Print)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Photo Studio & Passport Maker",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, maxLines = 1, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Live Preview Canvas Header (Always stays pinned on top if an image is loaded, helping visual feedback)
            if (currentBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(Color.Black.copy(0.95f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmapToShow = currentBitmap!!
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(bitmapToShow) {
                                    // Pointer detection for direct backdrop replace color on tab 3 (Passport Backdrop replacer)
                                    detectTapGestures { offset ->
                                        if (activeTab == 3) {
                                            // Scale touch to bitmap coordinates safely
                                            val containerW = size.width.toFloat()
                                            val containerH = size.height.toFloat()
                                            
                                            // Find exact dimensions of fits image
                                            val bmpW = bitmapToShow.width.toFloat()
                                            val bmpH = bitmapToShow.height.toFloat()
                                            val scale = min(containerW / bmpW, containerH / bmpH)
                                            
                                            val actualDrawW = bmpW * scale
                                            val actualDrawH = bmpH * scale
                                            
                                            val insetX = (containerW - actualDrawW) / 2f
                                            val insetY = (containerH - actualDrawH) / 2f
                                            
                                            val relativeX = offset.x - insetX
                                            val relativeY = offset.y - insetY
                                            
                                            if (relativeX in 0f..actualDrawW && relativeY in 0f..actualDrawH) {
                                                val normX = relativeX / actualDrawW
                                                val normY = relativeY / actualDrawH
                                                
                                                val finalPixelX = (normX * bmpW).toInt().coerceIn(0, bitmapToShow.width - 1)
                                                val finalPixelY = (normY * bmpH).toInt().coerceIn(0, bitmapToShow.height - 1)
                                                
                                                // Trigger global color changing callback
                                                viewModel.insertManualHistoryEntry("trigger_bkg_tap", "tap", "${finalPixelX}_${finalPixelY}", 0L)
                                            }
                                        }
                                    }
                                }
                        ) {
                            Image(
                                bitmap = bitmapToShow.asImageBitmap(),
                                contentDescription = "Active Image Preview",
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .border(1.dp, Color.White.copy(0.3f), RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Text(
                                text = "Resolution: ${bitmapToShow.width} × ${bitmapToShow.height} px",
                                color = Color.White.copy(0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = imageSourceLabel,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                // Banner telling them to pick a photo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Get started by capturing or picking a source image below",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Tabs workflow panels container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (activeTab) {
                    0 -> SourceSelectorTab(
                        onSelectGallery = { galleryLauncher.launch("image/*") },
                        onSelectCamera = { cameraLauncher.launch(null) },
                        currentBitmap = currentBitmap,
                        onReset = {
                            currentBitmap = null
                            originalBitmap = null
                            imageSourceLabel = "Canceled"
                        }
                    )
                    1 -> DimensionsTab(
                        currentBmp = currentBitmap,
                        onUpdateBitmap = { currentBitmap = it }
                    )
                    2 -> EnhanceTab(
                        originalBmp = originalBitmap,
                        onUpdateActiveBitmap = { currentBitmap = it }
                    )
                    3 -> PassportTab(
                        currentBmp = currentBitmap,
                        onUpdateBitmap = { currentBitmap = it },
                        viewModel = viewModel
                    )
                    4 -> ExportTab(
                        currentBmp = currentBitmap,
                        scaleQuality = scaleQuality,
                        onQualityChange = { scaleQuality = it },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

// ==========================================
// 1st Tab Component: Source Pick
// ==========================================
@Composable
fun SourceSelectorTab(
    onSelectGallery: () -> Unit,
    onSelectCamera: () -> Unit,
    currentBitmap: Bitmap?,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.35f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Choose Image Source",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Capture a clean portrait directly or upload any historic gallery capture to start cropping, resizing, and converting into standard passport/VISA outputs instantly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onSelectCamera,
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Snap Camera", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onSelectGallery,
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Browse Gallery", fontWeight = FontWeight.Bold)
                }
            }

            if (currentBitmap != null) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Workspace Current Picture", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// 2nd Tab Component: Dimension Change
// ==========================================
@Composable
fun DimensionsTab(
    currentBmp: Bitmap?,
    onUpdateBitmap: (Bitmap) -> Unit
) {
    if (currentBmp == null) {
        NoImagePrompt()
        return
    }

    val context = LocalContext.current
    var unitMode by remember { mutableStateOf(0) } // 0: Pixels, 1: cm, 2: inches
    var lockAspectRatio by remember { mutableStateOf(true) }

    var widthText by remember { mutableStateOf(currentBmp.width.toString()) }
    var heightText by remember { mutableStateOf(currentBmp.height.toString()) }

    // Constants
    val dpi = 300f // Printing dpi target

    // Utility methods
    fun getRatio() = currentBmp.width.toFloat() / currentBmp.height.toFloat()

    // When unit changes, update input display text
    LaunchedEffect(unitMode) {
        when (unitMode) {
            0 -> { // pixels
                widthText = currentBmp.width.toString()
                heightText = currentBmp.height.toString()
            }
            1 -> { // cm
                val wCm = (currentBmp.width / dpi) * 2.54f
                val hCm = (currentBmp.height / dpi) * 2.54f
                widthText = String.format("%.2f", wCm)
                heightText = String.format("%.2f", hCm)
            }
            2 -> { // inches
                val wIn = currentBmp.width / dpi
                val hIn = currentBmp.height / dpi
                widthText = String.format("%.2f", wIn)
                heightText = String.format("%.2f", hIn)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Image Resizing & Scaling", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            // Unit Selector
            TabRow(
                selectedTabIndex = unitMode,
                modifier = Modifier.height(40.dp).clip(RoundedCornerShape(8.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)
            ) {
                Tab(selected = unitMode == 0, onClick = { unitMode = 0 }) { Text("Pixels (px)", fontSize = 12.sp) }
                Tab(selected = unitMode == 1, onClick = { unitMode = 1 }) { Text("Metric (cm)", fontSize = 12.sp) }
                Tab(selected = unitMode == 2, onClick = { unitMode = 2 }) { Text("Print (Inches)", fontSize = 12.sp) }
            }

            // Templates Row Shortcut
            Text("Dimension Templates Preset:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    TemplateButton("US Passport (2×2\")") {
                        if (unitMode == 0) {
                            widthText = "600"; heightText = "600"
                        } else if (unitMode == 1) {
                            widthText = "5.08"; heightText = "5.08"
                        } else {
                            widthText = "2.0"; heightText = "2.0"
                        }
                    }
                }
                item {
                    TemplateButton("Standard Passport (35×45mm)") {
                        if (unitMode == 0) {
                            widthText = "413"; heightText = "531"
                        } else if (unitMode == 1) {
                            widthText = "3.50"; heightText = "4.50"
                        } else {
                            widthText = "1.38"; heightText = "1.77"
                        }
                    }
                }
                item {
                    TemplateButton("CNIC Card Size") {
                        if (unitMode == 0) {
                            widthText = "1050"; heightText = "660"
                        } else if (unitMode == 1) {
                            widthText = "8.89"; heightText = "5.59"
                        } else {
                            widthText = "3.5"; heightText = "2.2"
                        }
                    }
                }
                item {
                    TemplateButton("US Blue Stamp Visa") {
                        if (unitMode == 0) {
                            widthText = "600"; heightText = "600"
                        } else if (unitMode == 1) {
                            widthText = "5.08"; heightText = "5.08"
                        } else {
                            widthText = "2.0"; heightText = "2.0"
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

            // Manual inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { newVal ->
                        widthText = newVal
                        if (lockAspectRatio) {
                            val wVal = newVal.toFloatOrNull() ?: 0f
                            val ratio = getRatio()
                            if (wVal > 0) {
                                val targetH = wVal / ratio
                                heightText = String.format("%.2f", targetH).replace(",", ".")
                            }
                        }
                    },
                    label = { Text("Width") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = heightText,
                    onValueChange = { newVal ->
                        heightText = newVal
                        if (lockAspectRatio) {
                            val hVal = newVal.toFloatOrNull() ?: 0f
                            val ratio = getRatio()
                            if (hVal > 0) {
                                val targetW = hVal * ratio
                                widthText = String.format("%.2f", targetW).replace(",", ".")
                            }
                        }
                    },
                    label = { Text("Height") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Maintain original aspect ratio", fontSize = 13.sp)
                Switch(
                    checked = lockAspectRatio,
                    onCheckedChange = { lockAspectRatio = it }
                )
            }

            Button(
                onClick = {
                    val wInput = widthText.toFloatOrNull() ?: 0f
                    val hInput = heightText.toFloatOrNull() ?: 0f
                    
                    if (wInput <= 0f || hInput <= 0f) {
                        Toast.makeText(context, "Please enter positive numeric dimension parameters", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    // Convert measurements back to absolute pixels
                    val pixelW: Int
                    val pixelH: Int
                    when (unitMode) {
                        0 -> { // pixels
                            pixelW = wInput.toInt()
                            pixelH = hInput.toInt()
                        }
                        1 -> { // cm to px
                            pixelW = ((wInput / 2.54f) * dpi).toInt()
                            pixelH = ((hInput / 2.54f) * dpi).toInt()
                        }
                        else -> { // inches to px
                            pixelW = (wInput * dpi).toInt()
                            pixelH = (hInput * dpi).toInt()
                        }
                    }

                    if (pixelW <= 10 || pixelH <= 10) {
                        Toast.makeText(context, "Calculated resolution is too small", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    try {
                        val scaled = Bitmap.createScaledBitmap(currentBmp, pixelW, pixelH, true)
                        onUpdateBitmap(scaled)
                        Toast.makeText(context, "Image scaled smoothly to ${pixelW}x${pixelH}px!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Scaling failed: Out of memory context size", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AspectRatio, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Execute Smooth Image Resize", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// 3rd Tab Component: Studio Filters & Adjustments
// ==========================================
@Composable
fun EnhanceTab(
    originalBmp: Bitmap?,
    onUpdateActiveBitmap: (Bitmap) -> Unit
) {
    if (originalBmp == null) {
        NoImagePrompt()
        return
    }

    val context = LocalContext.current
    var brightnessVal by remember { mutableStateOf(0f) }   // -100 to 100
    var contrastVal by remember { mutableStateOf(1.0f) }    // 0.5 to 2.0
    var saturationVal by remember { mutableStateOf(1.0f) }  // 0.0 to 2.0
    var isEnhanced by remember { mutableStateOf(false) }

    fun regenerateFilteredImage() {
        // Build color matrix
        val matrix = ColorMatrix()
        
        // 1. Brightness offset
        val brightnessMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                1f, 0f, 0f, 0f, brightnessVal,
                0f, 1f, 0f, 0f, brightnessVal,
                0f, 0f, 1f, 0f, brightnessVal,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        
        // 2. Contrast scaling
        val t = (1.0f - contrastVal) * 127.5f
        val contrastMatrix = ColorMatrix().apply {
            set(floatArrayOf(
                contrastVal, 0f, 0f, 0f, t,
                0f, contrastVal, 0f, 0f, t,
                0f, 0f, contrastVal, 0f, t,
                0f, 0f, 0f, 1f, 0f
            ))
        }

        // 3. Saturation
        val saturationMatrix = ColorMatrix().apply {
            setSaturation(saturationVal)
        }

        // Concatenate filters
        matrix.postConcat(brightnessMatrix)
        matrix.postConcat(contrastMatrix)
        matrix.postConcat(saturationMatrix)

        val outBmp = Bitmap.createBitmap(originalBmp.width, originalBmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBmp)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
            isAntiAlias = true
            isFilterBitmap = true
        }
        canvas.drawBitmap(originalBmp, 0f, 0f, paint)
        onUpdateActiveBitmap(outBmp)
    }

    // Rotations & Flips helper
    fun modifyOrientation(rotateDegrees: Float, flipX: Boolean, flipY: Boolean) {
        val matrix = Matrix()
        if (rotateDegrees != 0f) {
            matrix.postRotate(rotateDegrees)
        }
        val scaleX = if (flipX) -1f else 1f
        val scaleY = if (flipY) -1f else 1f
        if (flipX || flipY) {
            matrix.postScale(scaleX, scaleY)
        }

        try {
            val rotated = Bitmap.createBitmap(originalBmp, 0, 0, originalBmp.width, originalBmp.height, matrix, true)
            onUpdateActiveBitmap(rotated)
            Toast.makeText(context, "Transformation modified!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Adjustments & Enhancements", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            // Direct Presets Aspect Ratio Cropping
            Spacer(modifier = Modifier.height(4.dp))
            Text("Crop to Ratio Aspect Preset:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        // Crop Square (1:1 Center crop)
                        val size = min(originalBmp.width, originalBmp.height)
                        val x = (originalBmp.width - size) / 2
                        val y = (originalBmp.height - size) / 2
                        val cropped = Bitmap.createBitmap(originalBmp, x, y, size, size)
                        onUpdateActiveBitmap(cropped)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("1:1 Sq", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = {
                        // Crop Portrait (3:4 Center Crop)
                        val targetW: Int
                        val targetH: Int
                        val originalRatio = originalBmp.width.toFloat() / originalBmp.height.toFloat()
                        if (originalRatio > 0.75f) {
                            targetH = originalBmp.height
                            targetW = (targetH * 0.75f).toInt()
                        } else {
                            targetW = originalBmp.width
                            targetH = (targetW / 0.75f).toInt()
                        }
                        val x = (originalBmp.width - targetW) / 2
                        val y = (originalBmp.height - targetH) / 2
                        val cropped = Bitmap.createBitmap(originalBmp, x, y, targetW, targetH)
                        onUpdateActiveBitmap(cropped)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("3:4 Pt", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = {
                        // Crop 16:9 Landscape Wide
                        val targetW: Int
                        val targetH: Int
                        val originalRatio = originalBmp.width.toFloat() / originalBmp.height.toFloat()
                        if (originalRatio > (16f/9f)) {
                            targetH = originalBmp.height
                            targetW = (targetH * (16f/9f)).toInt()
                        } else {
                            targetW = originalBmp.width
                            targetH = (targetW / (16f/9f)).toInt()
                        }
                        val x = (originalBmp.width - targetW) / 2
                        val y = (originalBmp.height - targetH) / 2
                        val cropped = Bitmap.createBitmap(originalBmp, x, y, targetW, targetH)
                        onUpdateActiveBitmap(cropped)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("16:9 Ls", fontSize = 11.sp)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

            // Rotations and flips row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { modifyOrientation(90f, false, false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rotate", fontSize = 11.sp)
                }
                Button(
                    onClick = { modifyOrientation(0f, true, false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Flip H", fontSize = 11.sp)
                }
                Button(
                    onClick = { modifyOrientation(0f, false, true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Flip V", fontSize = 11.sp)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

            // SLIDERS
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Brightness offset: ${brightnessVal.toInt()}", fontSize = 12.sp)
                    Icon(Icons.Outlined.BrightnessHigh, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = brightnessVal,
                    onValueChange = { brightnessVal = it; regenerateFilteredImage() },
                    valueRange = -100f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Contrast multiplier: ${String.format("%.2f", contrastVal)}", fontSize = 12.sp)
                    Icon(Icons.Default.Compare, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = contrastVal,
                    onValueChange = { contrastVal = it; regenerateFilteredImage() },
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Color Saturation: ${String.format("%.2f", saturationVal)}", fontSize = 12.sp)
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = saturationVal,
                    onValueChange = { saturationVal = it; regenerateFilteredImage() },
                    valueRange = 0.0f..2.0f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

            // 1-Click adaptive enhancer
            Button(
                onClick = {
                    isEnhanced = !isEnhanced
                    if (isEnhanced) {
                        brightnessVal = 12f
                        contrastVal = 1.25f
                        saturationVal = 1.15f
                    } else {
                        brightnessVal = 0f
                        contrastVal = 1f
                        saturationVal = 1f
                    }
                    regenerateFilteredImage()
                    Toast.makeText(context, if (isEnhanced) "1-Click Auto IQ Enhancement Enabled" else "Enhancements Cleared", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEnhanced) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEnhanced) "Disable Studio IQ Enhancement" else "Enable 1-Click Auto IQ Enhancement", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// 4th Tab Component: Passport & Backdrop Exchange
// ==========================================
@Composable
fun PassportTab(
    currentBmp: Bitmap?,
    onUpdateBitmap: (Bitmap) -> Unit,
    viewModel: DocFusionViewModel
) {
    if (currentBmp == null) {
        NoImagePrompt()
        return
    }

    val context = LocalContext.current
    
    // Backdrop replacement setups
    var backdropReplaceColor by remember { mutableStateOf(Color.White) }
    var toleranceSlider by remember { mutableStateOf(45f) }

    // Face centering trigger
    fun executeFaceCenterCrop() {
        val cardBmp = currentBmp.copy(Bitmap.Config.RGB_565, true)
        val detector = FaceDetector(cardBmp.width, cardBmp.height, 1)
        val faces = arrayOfNulls<FaceDetector.Face>(1)
        val count = detector.findFaces(cardBmp, faces)
        
        if (count > 0 && faces[0] != null) {
            val face = faces[0]!!
            val midPoint = PointF()
            face.getMidPoint(midPoint)
            val baseSpread = face.eyesDistance()
            
            // Frame standard portrait bounding crop containing head and shoulder base lines
            val cropW = (baseSpread * 4.2f).toInt()
            val cropH = (cropW * 1.25f).toInt() // Golden vertical portrait ratio
            
            val startX = (midPoint.x - cropW / 2).toInt().coerceIn(0, currentBmp.width - cropW)
            val startY = (midPoint.y - cropH * 0.44f).toInt().coerceIn(0, currentBmp.height - cropH)
            
            val clampedW = cropW.coerceAtMost(currentBmp.width - startX)
            val clampedH = cropH.coerceAtMost(currentBmp.height - startY)
            
            if (clampedW > 40 && clampedH > 40) {
                val headCrop = Bitmap.createBitmap(currentBmp, startX, startY, clampedW, clampedH)
                onUpdateBitmap(headCrop)
                Toast.makeText(context, "Face Centered & Cropped Perfectly!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Face found too close to border. Crop manually", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "No face detected in photo. Please ensure clear lighting", Toast.LENGTH_LONG).show()
        }
    }

    // Monitor tap coordinates through our viewmodel channel!
    val listFlowState by viewModel.history.collectAsState()
    LaunchedEffect(listFlowState) {
        val tapTrigger = listFlowState.find { it.name == "trigger_bkg_tap" }
        if (tapTrigger != null) {
            val parts = tapTrigger.tags?.split("_") ?: emptyList()
            if (parts.size == 2) {
                val touchX = parts[0].toIntOrNull() ?: -1
                val touchY = parts[1].toIntOrNull() ?: -1
                
                if (touchX >= 0 && touchY >= 0 && touchX < currentBmp.width && touchY < currentBmp.height) {
                    val replaced = replaceBackgroundColor(
                        currentBmp,
                        touchX,
                        touchY,
                        backdropReplaceColor.toArgb(),
                        toleranceSlider
                    )
                    onUpdateBitmap(replaced)
                    Toast.makeText(context, "Backdrop matching segments replaced!", Toast.LENGTH_SHORT).show()
                }
            }
            // Clear entry to avoid recursive loop triggers
            viewModel.deleteFile(tapTrigger)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Passport Studio Benches", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            // Auto centering
            Button(
                onClick = { executeFaceCenterCrop() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Face, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Auto Face Centering Crop (Native AI)", fontWeight = FontWeight.Bold)
            }
            
            Text(
                "Detects face geometry via native engine, centers eyes horizontally, and crops optimized portrait padding.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.61f)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

            // Chroma Key Background replacer
            Text("Backdrop Chroma Keys Replacer", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "1. Choose background replacement paint color.\n" +
                "2. Tap ANYWHERE inside the dark preview photo on the background paint you wish to replace.\n" +
                "3. Adjust the tolerance threshold slider to fine-tune matching values.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(0.24f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )

            // Background paint buttons selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorSelectorButton("White Backdrop", Color.White, current = backdropReplaceColor) { backdropReplaceColor = Color.White }
                ColorSelectorButton("Off Blue", Color(0xFF0D47A1), current = backdropReplaceColor) { backdropReplaceColor = Color(0xFF0D47A1) }
                ColorSelectorButton("Classic Red", Color(0xFFB71C1C), current = backdropReplaceColor) { backdropReplaceColor = Color(0xFFB71C1C) }
                ColorSelectorButton("Light Grey", Color(0xFFE0E0E0), current = backdropReplaceColor) { backdropReplaceColor = Color(0xFFE0E0E0) }
            }

            // Custom hex backdrop color picker (using dynamic hue slider)
            var hueValue by remember { mutableStateOf(120f) } // 0 to 360
            LaunchedEffect(hueValue) {
                // Generate a pretty dynamic color from Hue slider
                val hsvColor = Color.hsv(hueValue, 0.9f, 0.9f)
                backdropReplaceColor = hsvColor
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Select Custom Color Hue Panel:", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                Slider(
                    value = hueValue,
                    onValueChange = { hueValue = it },
                    valueRange = 0f..360f,
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(backdropReplaceColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Backdrop replacement tolerance threshold: ${toleranceSlider.toInt()}/100", fontSize = 12.sp)
                Slider(
                    value = toleranceSlider,
                    onValueChange = { toleranceSlider = it },
                    valueRange = 10f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ColorSelectorButton(
    label: String,
    target: Color,
    current: Color,
    onClick: () -> Unit
) {
    val isSelected = target.toArgb() == current.toArgb()
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = target.copy(0.15f),
            contentColor = if (target == Color.White) MaterialTheme.colorScheme.onSurface else target
        )
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(target).border(0.5.dp, Color.Gray, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// Flood fill-distance chroma replacement
fun replaceBackgroundColor(bitmap: Bitmap, targetX: Int, targetY: Int, replacementColor: Int, tolerance: Float): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    
    val targetColor = pixels[targetY * width + targetX]
    val targetA = AndroidColor.alpha(targetColor)
    val targetR = AndroidColor.red(targetColor)
    val targetG = AndroidColor.green(targetColor)
    val targetB = AndroidColor.blue(targetColor)
    
    // Weighted color metric distance factor
    val limitSquare = (tolerance * tolerance * 4f)
    
    for (i in pixels.indices) {
        val c = pixels[i]
        val r = AndroidColor.red(c)
        val g = AndroidColor.green(c)
        val b = AndroidColor.blue(c)
        
        val dR = r - targetR
        val dG = g - targetG
        val dB = b - targetB
        
        // Euclidean RGB distance squared
        val distance = (dR * dR + dG * dG + dB * dB).toFloat()
        if (distance <= limitSquare) {
            pixels[i] = replacementColor
        }
    }
    
    val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    dest.setPixels(pixels, 0, width, 0, 0, width, height)
    return dest
}

// ==========================================
// 5th Tab Component: Save, PDF, Share & Print
// ==========================================
@Composable
fun ExportTab(
    currentBmp: Bitmap?,
    scaleQuality: Float,
    onQualityChange: (Float) -> Unit,
    viewModel: DocFusionViewModel
) {
    if (currentBmp == null) {
        NoImagePrompt()
        return
    }

    val context = LocalContext.current
    var fileNameText by remember { mutableStateOf("studio_photo_${System.currentTimeMillis() / 1000}") }
    var exportFormat by remember { mutableStateOf("JPEG") } // "JPEG", "PNG", "PDF"

    fun performExportSave() {
        val actualExt = when (exportFormat) {
            "PNG" -> "png"
            "PDF" -> "pdf"
            else -> "jpg"
        }
        val targetName = "${fileNameText.trim()}.${actualExt}"
        val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val outFile = File(documentsDir, targetName)

        try {
            if (exportFormat == "PDF") {
                // Save output as a high-fidelity PDF page
                val pdfDoc = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(currentBmp.width, currentBmp.height, 1).create()
                val page = pdfDoc.startPage(pageInfo)
                
                page.canvas.drawColor(AndroidColor.WHITE)
                page.canvas.drawBitmap(currentBmp, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                pdfDoc.finishPage(page)
                
                FileOutputStream(outFile).use { fos ->
                    pdfDoc.writeTo(fos)
                }
                pdfDoc.close()
                
                viewModel.insertManualHistoryEntry(targetName, outFile.absolutePath, "PDF", outFile.length())
            } else if (exportFormat == "PNG") {
                // Save as PNG lossless format
                FileOutputStream(outFile).use { fos ->
                    currentBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                viewModel.insertManualHistoryEntry(targetName, outFile.absolutePath, "Images", outFile.length())
            } else {
                // Save as JPEG lossy format
                FileOutputStream(outFile).use { fos ->
                    currentBmp.compress(Bitmap.CompressFormat.JPEG, scaleQuality.toInt(), fos)
                }
                viewModel.insertManualHistoryEntry(targetName, outFile.absolutePath, "Images", outFile.length())
            }

            Toast.makeText(context, "Saved successfully into File Hub: $targetName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // Share Sheet Launcher
    fun performDirectShare() {
        val cachedImageFile = File(context.cacheDir, "studio_shared_${System.currentTimeMillis() / 1000}.png")
        try {
            FileOutputStream(cachedImageFile).use { fos ->
                currentBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            
            // Share using content providers safely
            val providerAuthority = "${context.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, providerAuthority, cachedImageFile)
            
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share DocFusion Photo Studio File"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error launching share sheet", Toast.LENGTH_SHORT).show()
        }
    }

    // Print helper trigger
    fun performPrintImage() {
        try {
            val printHelper = PrintHelper(context)
            printHelper.scaleMode = PrintHelper.SCALE_MODE_FIT
            printHelper.printBitmap("DocFusion Studio Print File", currentBmp)
            Toast.makeText(context, "Contacting print spooler service...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Print service failed", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Publish & Save Studio Output", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            // Filename tf
            OutlinedTextField(
                value = fileNameText,
                onValueChange = { fileNameText = it },
                label = { Text("Output Document Filename") },
                placeholder = { Text("Enter filename...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { fileNameText = "photo_${System.currentTimeMillis() / 1000}" }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate name")
                    }
                },
                shape = RoundedCornerShape(8.dp)
            )

            // Dynamic format selector
            Text("Choose Target Export Format:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("JPEG", "PNG", "PDF").forEach { format ->
                    val isSelected = exportFormat == format
                    Button(
                        onClick = { exportFormat = format },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(format, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Quality factor for JPEG compression
            if (exportFormat == "JPEG") {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("JPEG Compression Quality Scale: ${scaleQuality.toInt()}%", fontSize = 12.sp)
                    Slider(
                        value = scaleQuality,
                        onValueChange = { onQualityChange(it) },
                        valueRange = 40f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "90%+ maintains crisp details for physical visa or badge printing, while lower values offer aggressive cell size shrinkage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

            // Action CTAs Matrix Layout
            Button(
                onClick = { performExportSave() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compile & Save to Device Hub", fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { performDirectShare() },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share Image", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { performPrintImage() },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Print Photo", fontSize = 12.sp)
                }
            }
        }
    }
}

// Placeholder for missing images
@Composable
fun NoImagePrompt() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.InsertPhoto, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.44f))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Active Picture Canvas is Empty", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Please complete the first tab ('Source') by uploading a file or capturing a camera lens portrait to unlock studio panels.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        }
    }
}

// Helper Data classes and button components
data class TabItem(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun TemplateButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
