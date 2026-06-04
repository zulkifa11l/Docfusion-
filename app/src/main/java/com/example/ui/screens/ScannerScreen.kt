package com.example.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.viewmodel.DocFusionViewModel
import com.example.util.PdfProcessor
import com.example.util.ScannerProcessor
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: DocFusionViewModel,
    onNavigateBack: () -> Unit,
    onOcrScanned: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // States: "CAPTURE", "CROP", "FILTER"
    var scannerState by remember { mutableStateOf("CAPTURE") }

    // Mode: "Document", "ID Card"
    var scanningCategory by remember { mutableStateOf("Document") }
    var idCardModeType by remember { mutableStateOf("CNIC") } // CNIC, Passport, License

    // Permissions
    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { hasCameraPermission = it }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // CameraX parameters
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    // Memory cache for active scans
    var activeRawCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var interactiveCropPoints by remember { mutableStateOf<List<PointF>>(emptyList()) }
    var activeFilteredBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Dynamic pages count
    val pagesList by viewModel.scanPages

    fun handleCapture() {
        val capture = imageCapture ?: return
        val cacheFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(cacheFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val originalBmp = BitmapFactory.decodeFile(cacheFile.absolutePath) ?: return
                    
                    // Cleanup cache file
                    cacheFile.delete()

                    activeRawCapturedBitmap = originalBmp
                    interactiveCropPoints = ScannerProcessor.detectDocumentEdges(originalBmp)
                    
                    if (scanningCategory == "ID Card") {
                        // ID cards directly skip cropping or auto-align and jump straight to filter compiles
                        val croppedBmp = ScannerProcessor.applyPerspectiveCorrection(originalBmp, interactiveCropPoints)
                        activeFilteredBitmap = ScannerProcessor.applyMagicEnhance(croppedBmp)
                        scannerState = "FILTER"
                    } else {
                        scannerState = "CROP"
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "$scanningCategory Scanner", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (scannerState != "CAPTURE") {
                            scannerState = "CAPTURE"
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (scannerState == "CAPTURE" && scanningCategory == "Document" && pagesList.isNotEmpty()) {
                        Button(
                            onClick = {
                                viewModel.saveScanGroupAsPdf("ScanDoc_${System.currentTimeMillis()}", PdfProcessor.PdfOptions()) {
                                    onNavigateBack()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Done (${pagesList.size})", fontSize = 14.sp)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            when (scannerState) {
                "CAPTURE" -> {
                    if (hasCameraPermission) {
                        // 1. Camera Live View
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                }.also { previewView ->
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        imageCapture = ImageCapture.Builder()
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                            .build()

                                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview,
                                                imageCapture
                                            )
                                        } catch (exc: Exception) {
                                            exc.printStackTrace()
                                        }
                                    }, ContextCompat.getMainExecutor(context))
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // 2. ID Card guide outline overlays
                        if (scanningCategory == "ID Card") {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (viewModel.isScanningBack) "SCAN CARD BACK" else "SCAN CARD FRONT",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier
                                        .background(Color.Black.copy(0.6f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                                Spacer(modifier = Modifier.height(30.dp))
                                
                                // Beautiful floating outline ID Frame
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                        .background(Color.Transparent)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Align $idCardModeType frame perfectly inside",
                                            color = Color.White.copy(0.6f),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Capture Action panel bottom
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(0.75f))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Sub-category selector Document vs ID Card
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                FilterChip(
                                    selected = scanningCategory == "Document",
                                    onClick = { scanningCategory = "Document" },
                                    label = { Text("Document") },
                                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.White)
                                )
                                FilterChip(
                                    selected = scanningCategory == "ID Card",
                                    onClick = { scanningCategory = "ID Card" },
                                    label = { Text("ID Card Cards") },
                                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.White)
                                )
                            }

                            if (scanningCategory == "ID Card") {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    listOf("CNIC", "Passport", "Driving License").forEach { type ->
                                        val isSelected = idCardModeType == type
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray)
                                                .clickable { idCardModeType = type }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = type,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            // Horizontal list of Multi-page Thumbnails captured
                            if (scanningCategory == "Document" && pagesList.isNotEmpty()) {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(pagesList) { idx, bmp ->
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                                        ) {
                                            androidx.compose.foundation.Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .align(Alignment.TopEnd)
                                                    .background(Color.Red, CircleShape)
                                                    .clickable {
                                                        val list = viewModel.scanPages.value.toMutableList()
                                                        list.removeAt(idx)
                                                        viewModel.scanPages.value = list
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // Capture Trigger Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(modifier = Modifier.size(36.dp))
                                
                                IconButton(
                                    onClick = { handleCapture() },
                                    modifier = Modifier
                                        .size(72.dp)
                                        .border(4.dp, Color.White, CircleShape)
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                ) {
                                    Icon(Icons.Default.Camera, contentDescription = "Capture", tint = Color.Black, modifier = Modifier.size(32.dp))
                                }

                                if (scanningCategory == "Document") {
                                    IconButton(
                                        onClick = { onOcrScanned() },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(Color.DarkGray, CircleShape)
                                    ) {
                                        Icon(Icons.Default.TextSnippet, contentDescription = "Direct OCR", tint = Color.White)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(44.dp))
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Camera permission requested! Please enable in App Settings.", color = Color.White)
                        }
                    }
                }

                "CROP" -> {
                    // Intelligent Draggable Corner Node Cropper
                    val original = activeRawCapturedBitmap
                    if (original != null) {
                        var cPoints = interactiveCropPoints
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(original.width.toFloat() / original.height.toFloat())
                            ) {
                                val canvasWidth = maxWidth.value
                                val canvasHeight = maxHeight.value

                                // Renders the captured scan frame
                                androidx.compose.foundation.Image(
                                    bitmap = original.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )

                                // Interactive Corner Handles
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val touchPos = change.position
                                                
                                                // Scaling coordinates from DP canvas to raw image points
                                                val imageTouchX = (touchPos.x / size.width) * original.width
                                                val imageTouchY = (touchPos.y / size.height) * original.height

                                                // Find closest segment point
                                                var closestIndex = -1
                                                var minDistance = Float.MAX_VALUE
                                                for ((i, pt) in cPoints.withIndex()) {
                                                    val dist = Math.hypot((pt.x - imageTouchX).toDouble(), (pt.y - imageTouchY).toDouble()).toFloat()
                                                    if (dist < minDistance) {
                                                        minDistance = dist
                                                        closestIndex = i
                                                    }
                                                }

                                                if (closestIndex != -1 && minDistance < 150f) {
                                                    val updatedList = cPoints.toMutableList()
                                                    updatedList[closestIndex] = PointF(
                                                        imageTouchX.coerceIn(0f, original.width.toFloat()),
                                                        imageTouchY.coerceIn(0f, original.height.toFloat())
                                                    )
                                                    cPoints = updatedList
                                                    interactiveCropPoints = updatedList
                                                }
                                            }
                                        }
                                ) {
                                    // Draws crop outline bounding quadrilaterals
                                    val path = androidx.compose.ui.graphics.Path().apply {
                                        if (cPoints.size == 4) {
                                            moveTo((cPoints[0].x / original.width) * size.width, (cPoints[0].y / original.height) * size.height)
                                            lineTo((cPoints[1].x / original.width) * size.width, (cPoints[1].y / original.height) * size.height)
                                            lineTo((cPoints[2].x / original.width) * size.width, (cPoints[2].y / original.height) * size.height)
                                            lineTo((cPoints[3].x / original.width) * size.width, (cPoints[3].y / original.height) * size.height)
                                            close()
                                        }
                                    }
                                    drawPath(path = path, color = Color(0xFF2196F3), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))

                                    // Render circles on coordinates
                                    for (pt in cPoints) {
                                        val x = (pt.x / original.width) * size.width
                                        val y = (pt.y / original.height) * size.height
                                        drawCircle(Color(0xFF2196F3), radius = 22f, center = androidx.compose.ui.geometry.Offset(x, y))
                                        drawCircle(Color.White, radius = 10f, center = androidx.compose.ui.geometry.Offset(x, y))
                                    }
                                }
                            }

                            // Application Action controls
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = { scannerState = "CAPTURE" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                ) {
                                    Text("Retake", color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        // Execute structural perspective correction warping
                                        val cropped = ScannerProcessor.applyPerspectiveCorrection(original, cPoints)
                                        activeFilteredBitmap = cropped
                                        scannerState = "FILTER"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Done Crop")
                                }
                            }
                        }
                    }
                }

                "FILTER" -> {
                    val filtered = activeFilteredBitmap
                    if (filtered != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Page Scans Result Preview", color = Color.White, fontWeight = FontWeight.Bold)

                            // Renders filtered canvas
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = filtered.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            // Filter modes toolbar carousel selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.DarkGray.copy(0.4f), RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceSpaceAround
                            ) {
                                val modes = listOf("Original", "Magic Color", "Black & White", "Grayscale")
                                for (mode in modes) {
                                    val isSel = viewModel.selectedScanMode == mode
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable {
                                                viewModel.selectedScanMode = mode
                                                activeRawCapturedBitmap?.let { raw ->
                                                    val cropped = ScannerProcessor.applyPerspectiveCorrection(raw, interactiveCropPoints)
                                                    activeFilteredBitmap = when (mode) {
                                                        "Magic Color" -> ScannerProcessor.applyMagicEnhance(cropped)
                                                        "Black & White" -> ScannerProcessor.applyMonochromeThreshold(cropped)
                                                        "Grayscale" -> ScannerProcessor.applyGrayscale(cropped)
                                                        else -> cropped
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(mode, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = { scannerState = "CROP" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                ) {
                                    Text("Back Crop", color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        if (scanningCategory == "ID Card") {
                                            if (!viewModel.isScanningBack) {
                                                viewModel.idCardFrontBitmap = activeFilteredBitmap
                                                viewModel.isScanningBack = true
                                                scannerState = "CAPTURE"
                                            } else {
                                                viewModel.idCardBackBitmap = activeFilteredBitmap
                                                viewModel.mergeAndProcessIdCard(idCardModeType) {
                                                    onNavigateBack()
                                                }
                                            }
                                        } else {
                                            // Document section: add to page collector list
                                            val currentPages = viewModel.scanPages.value.toMutableList()
                                            currentPages.add(filtered)
                                            viewModel.scanPages.value = currentPages
                                            
                                            scannerState = "CAPTURE"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(
                                        text = if (scanningCategory == "ID Card") {
                                            if (!viewModel.isScanningBack) "Scan Back Side" else "Compile ID PDF"
                                        } else "Save Page"
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

// Custom Arrangement helper for simpler layout
private val Arrangement.SpaceSpaceAround: Arrangement.HorizontalOrVertical
    get() = Arrangement.SpaceAround
