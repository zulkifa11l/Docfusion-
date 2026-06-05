package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.DocFusionViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class DrawStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val isHighlight: Boolean = false
)

data class TextAnnotation(
    val text: String,
    val position: Offset,
    val color: Color,
    val size: Float,
    val id: Long = System.currentTimeMillis()
)

data class SignAnnotation(
    val text: String,
    val position: Offset,
    val color: Color,
    val id: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    viewModel: DocFusionViewModel,
    pdfPath: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(pdfPath) { File(pdfPath) }
    
    if (!file.exists()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Error: Physical file not found.", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onNavigateBack) { Text("Go Back") }
            }
        }
        return
    }

    // PDF Renderer state management
    var pageCount by remember { mutableStateOf(0) }
    var activePageIndex by remember { mutableStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(false) }

    // Read pages and extract stats
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    
    fun renderPage(pageIdx: Int) {
        if (pageIdx < 0) return
        isRendering = true
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            if (pfd != null) {
                renderer = PdfRenderer(pfd!!)
                pageCount = renderer!!.pageCount
                val pageIndexToRender = if (pageIdx >= pageCount) pageCount - 1 else pageIdx
                
                if (pageCount > 0 && pageIndexToRender >= 0) {
                    val page = renderer!!.openPage(pageIndexToRender)
                    
                    // Render page bitmap
                    val targetW = (page.width * 1.5).toInt()
                    val targetH = (page.height * 1.5).toInt()
                    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    
                    pageBitmap = bmp
                }
                renderer!!.close()
                pfd!!.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error rendering page: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        } finally {
            isRendering = false
        }
    }

    // Load initial page
    LaunchedEffect(pdfPath) {
        renderPage(0)
    }

    // Annotation Tools state
    var selectedTool by remember { mutableStateOf("doodle") } // "doodle", "highlight", "text", "signature"
    var activeColor by remember { mutableStateOf(Color.Red) }
    var brushWidth by remember { mutableStateOf(5f) }
    var highlightBrushWidth by remember { mutableStateOf(24f) }
    
    // Multi-page annotations stores
    val pagesDoodles = remember { mutableStateMapOf<Int, MutableList<DrawStroke>>() }
    val pagesTexts = remember { mutableStateMapOf<Int, MutableList<TextAnnotation>>() }
    val pagesSigns = remember { mutableStateMapOf<Int, MutableList<SignAnnotation>>() }

    // Active tool state caches
    var activeStrokePoints = remember { mutableStateListOf<Offset>() }
    
    // Popup states
    var showTextDialog by remember { mutableStateOf<Offset?>(null) }
    var inputTextVal by remember { mutableStateOf("") }
    var inputFontSize by remember { mutableStateOf(16f) }

    var showSignDialog by remember { mutableStateOf<Offset?>(null) }
    var signUiText by remember { mutableStateOf("Signed") }

    // Dialog to generate preset vector signature
    var showSignatureCreatorDialog by remember { mutableStateOf(false) }
    var generatedSignatureName by remember { mutableStateOf("") }
    var signaturePaletteColor by remember { mutableStateOf(Color.Blue) }

    // Save process loading state
    var isSavingNewPdf by remember { mutableStateOf(false) }

    fun undoLastDoodle() {
        val list = pagesDoodles[activePageIndex]
        if (list != null && list.isNotEmpty()) {
            list.removeAt(list.size - 1)
        }
    }

    fun clearAllOnPage() {
        pagesDoodles[activePageIndex] = mutableListOf()
        pagesTexts[activePageIndex] = mutableListOf()
        pagesSigns[activePageIndex] = mutableListOf()
    }

    // Full PDF save compilation procedure
    fun compileAndSaveOverloadedPdf() {
        isSavingNewPdf = true
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var inputPfd: ParcelFileDescriptor? = null
            var pdfReader: PdfRenderer? = null
            val pdfOutput = PdfDocument()
            try {
                inputPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfReader = PdfRenderer(inputPfd)
                val totalCount = pdfReader.pageCount

                for (idx in 0 until totalCount) {
                    val page = pdfReader.openPage(idx)
                    val w = page.width
                    val h = page.height

                    val pageInfo = PdfDocument.PageInfo.Builder(w, h, idx + 1).create()
                    val newPage = pdfOutput.startPage(pageInfo)
                    val canvas = newPage.canvas

                    // Render original page to temporary bitmap at original size
                    val pageBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val bmpCanvas = android.graphics.Canvas(pageBmp)
                    bmpCanvas.drawColor(android.graphics.Color.WHITE)
                    page.render(pageBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    // Draw the base page image to target PDF Canvas container
                    canvas.drawBitmap(pageBmp, 0f, 0f, null)
                    pageBmp.recycle()

                    // Compute scaling variables (since UI display handles different dimensions)
                    // We render UI at pageW * 1.5, we must map drawing points to 1.0 original scale
                    val ratioX = 1f / 1.5f
                    val ratioY = 1f / 1.5f

                    // 1. Draw drawings/doodles
                    val doodles = pagesDoodles[idx] ?: emptyList()
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.STROKE
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    for (stroke in doodles) {
                        paint.color = stroke.color.toArgb()
                        paint.strokeWidth = stroke.width * ratioX
                        
                        val path = android.graphics.Path()
                        if (stroke.points.isNotEmpty()) {
                            val fPoint = stroke.points.first()
                            path.moveTo(fPoint.x * ratioX, fPoint.y * ratioY)
                            for (p in stroke.points.drop(1)) {
                                path.lineTo(p.x * ratioX, p.y * ratioY)
                            }
                            canvas.drawPath(path, paint)
                        }
                    }

                    // 2. Draw static text annotations
                    val texts = pagesTexts[idx] ?: emptyList()
                    val textPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                    }
                    for (textAnn in texts) {
                        textPaint.color = textAnn.color.toArgb()
                        textPaint.textSize = textAnn.size * ratioX
                        canvas.drawText(textAnn.text, textAnn.position.x * ratioX, textAnn.position.y * ratioY, textPaint)
                    }

                    // 3. Draw signature annotations
                    val signs = pagesSigns[idx] ?: emptyList()
                    val signPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f * ratioX
                    }
                    val signTextPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textSize = 20f * ratioX
                        isUnderlineText = true
                    }
                    for (signAnn in signs) {
                        signPaint.color = signAnn.color.toArgb()
                        signTextPaint.color = signAnn.color.toArgb()
                        
                        val x = signAnn.position.x * ratioX
                        val y = signAnn.position.y * ratioY
                        
                        // Draw decorative signature curves around placed coordinate
                        canvas.drawText(signAnn.text, x, y, signTextPaint)
                        val curvePath = android.graphics.Path().apply {
                            moveTo(x - 10f * ratioX, y + 5f * ratioY)
                            quadTo(x + 50f * ratioX, y + 15f * ratioY, x + 110f * ratioX, y + 2f * ratioY)
                        }
                        canvas.drawPath(curvePath, signPaint)
                    }

                    pdfOutput.finishPage(newPage)
                }

                pdfReader.close()
                inputPfd.close()

                // Save compile file onto a new history output
                val d = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "Annotated_${file.nameWithoutExtension}_${System.currentTimeMillis() / 1000}.pdf")
                FileOutputStream(d).use { fos ->
                    pdfOutput.writeTo(fos)
                }
                pdfOutput.close()

                // Register file in history database
                viewModel.insertManualHistoryEntry(
                    name = d.name,
                    absolutePath = d.absolutePath,
                    category = "PDF",
                    size = d.length()
                )

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isSavingNewPdf = false
                    Toast.makeText(context, "Successfully saved annotated PDF as ${d.name}", Toast.LENGTH_LONG).show()
                    onNavigateBack()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isSavingNewPdf = false
                    Toast.makeText(context, "Error compiling annotations: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PDF Annotation Hub", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { compileAndSaveOverloadedPdf() }, enabled = !isSavingNewPdf) {
                        if (isSavingNewPdf) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Save Changes", tint = MaterialTheme.colorScheme.primary)
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
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            // Document navigation controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (activePageIndex > 0) {
                            activePageIndex--
                            renderPage(activePageIndex)
                        }
                    },
                    enabled = activePageIndex > 0
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Prev page")
                }

                Text(
                    text = "Page ${if (pageCount == 0) 0 else activePageIndex + 1} of $pageCount",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                IconButton(
                    onClick = {
                        if (activePageIndex < pageCount - 1) {
                            activePageIndex++
                            renderPage(activePageIndex)
                        }
                    },
                    enabled = activePageIndex < pageCount - 1
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next page")
                }
            }

            // Quick Canvas utility functions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { undoLastDoodle() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Undo stroke", fontSize = 11.sp)
                }

                Button(
                    onClick = { clearAllOnPage() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear page", fontSize = 11.sp)
                }
            }

            // The Rendered PDF page interactive Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (isRendering) {
                    CircularProgressIndicator()
                } else if (pageBitmap != null) {
                    val bmp = pageBitmap!!
                    
                    Box(
                        modifier = Modifier
                            .width(bmp.width.dp / 1.5f)
                            .height(bmp.height.dp / 1.5f)
                            .pointerInput(selectedTool) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        if (selectedTool == "text") {
                                            inputTextVal = ""
                                            showTextDialog = offset
                                        } else if (selectedTool == "signature") {
                                            if (generatedSignatureName.isNotEmpty()) {
                                                val list = pagesSigns.getOrPut(activePageIndex) { mutableListOf() }
                                                list.add(
                                                    SignAnnotation(
                                                        text = generatedSignatureName,
                                                        position = offset,
                                                        color = signaturePaletteColor
                                                    )
                                                )
                                            } else {
                                                showSignatureCreatorDialog = true
                                            }
                                        }
                                    }
                                )
                            }
                            .pointerInput(selectedTool) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        if (selectedTool == "doodle" || selectedTool == "highlight") {
                                            activeStrokePoints.clear()
                                            activeStrokePoints.add(offset)
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (selectedTool == "doodle" || selectedTool == "highlight") {
                                            activeStrokePoints.add(change.position)
                                        }
                                    },
                                    onDragEnd = {
                                        if ((selectedTool == "doodle" || selectedTool == "highlight") && activeStrokePoints.isNotEmpty()) {
                                            val list = pagesDoodles.getOrPut(activePageIndex) { mutableListOf() }
                                            val isHighlight = selectedTool == "highlight"
                                            list.add(
                                                DrawStroke(
                                                    points = activeStrokePoints.toList(),
                                                    color = if (isHighlight) activeColor.copy(alpha = 0.35f) else activeColor,
                                                    width = if (isHighlight) highlightBrushWidth else brushWidth,
                                                    isHighlight = isHighlight
                                                )
                                            )
                                            activeStrokePoints.clear()
                                        }
                                    }
                                )
                            }
                    ) {
                        // 1. Draw base page bitmap
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Active PDF Page Content",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        // 2. Draw active temporary stroke and saved strokes
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Render saved doodles
                            val savedDoodles = pagesDoodles[activePageIndex] ?: emptyList()
                            for (stroke in savedDoodles) {
                                if (stroke.points.size > 1) {
                                    val path = Path().apply {
                                        moveTo(stroke.points.first().x, stroke.points.first().y)
                                        for (p in stroke.points.drop(1)) {
                                            lineTo(p.x, p.y)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = stroke.color,
                                        style = Stroke(
                                            width = stroke.width,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            // Render currently dragging stroke
                            if ((selectedTool == "doodle" || selectedTool == "highlight") && activeStrokePoints.size > 1) {
                                val currentPath = Path().apply {
                                    moveTo(activeStrokePoints.first().x, activeStrokePoints.first().y)
                                    for (p in activeStrokePoints.drop(1)) {
                                        lineTo(p.x, p.y)
                                    }
                                }
                                val color = if (selectedTool == "highlight") activeColor.copy(alpha = 0.35f) else activeColor
                                val width = if (selectedTool == "highlight") highlightBrushWidth else brushWidth
                                drawPath(
                                    path = currentPath,
                                    color = color,
                                    style = Stroke(
                                        width = width,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        // 3. Render Text Annotations
                        val pageTextItems = pagesTexts[activePageIndex] ?: emptyList()
                        pageTextItems.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .offset(x = item.position.x.dp / 1.5f, y = item.position.y.dp / 1.5f)
                                    .clickable {
                                        // clicking lets user delete it
                                        val list = pagesTexts[activePageIndex]
                                        list?.remove(item)
                                    }
                                    .background(Color.White.copy(alpha = 0.5f))
                                    .padding(2.dp)
                            ) {
                                Text(
                                    text = item.text,
                                    color = item.color,
                                    fontSize = item.size.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // 4. Render Signature Annotations
                        val pageSignItems = pagesSigns[activePageIndex] ?: emptyList()
                        pageSignItems.forEach { sign ->
                            Box(
                                modifier = Modifier
                                    .offset(x = sign.position.x.dp / 1.5f, y = sign.position.y.dp / 1.5f)
                                    .clickable {
                                        val list = pagesSigns[activePageIndex]
                                        list?.remove(sign)
                                    }
                                    .background(Color.White.copy(alpha = 0.6f))
                                    .padding(4.dp)
                                    .border(1.dp, sign.color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = sign.text,
                                        color = sign.color,
                                        fontSize = 15.sp,
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(60.dp)
                                            .height(1.dp)
                                            .background(sign.color)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // BOTTOM TOOLBOX FOR ANNOTATION OPTIONS
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Tool Selection Icons segment
                    Text("Annotation Toolbar", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { selectedTool = "doodle" },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selectedTool == "doodle") MaterialTheme.colorScheme.primaryContainer 
                                    else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Brush, contentDescription = "Pencil Doodle", tint = if (selectedTool == "doodle") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text("Pencil", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(
                            onClick = { selectedTool = "highlight" },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selectedTool == "highlight") MaterialTheme.colorScheme.primaryContainer 
                                    else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.BorderColor, contentDescription = "Highlight PDF Content", tint = if (selectedTool == "highlight") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text("Highlight", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(
                            onClick = { selectedTool = "text" },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selectedTool == "text") MaterialTheme.colorScheme.primaryContainer 
                                    else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.TextFields, contentDescription = "Add Text Annotation", tint = if (selectedTool == "text") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text("Add Text", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(
                            onClick = { 
                                selectedTool = "signature"
                                if (generatedSignatureName.isEmpty()) {
                                    showSignatureCreatorDialog = true
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selectedTool == "signature") MaterialTheme.colorScheme.primaryContainer 
                                    else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Gesture, contentDescription = "Stamp Signature", tint = if (selectedTool == "signature") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text("Sign", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 2. Color Palette circle selections (Active for doodle, text & sign)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Theme Color:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val colors = listOf(Color.Red, Color.Blue, Color.Green, Color.Black, Color(0xFFE91E63))
                            colors.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            width = if (activeColor == col) 2.dp else 0.dp,
                                            color = if (activeColor == col) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { activeColor = col }
                                )
                            }
                        }
                    }

                    // 3. Pencil/Highlighter brush width slider
                    if (selectedTool == "doodle") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Pencil Size", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = brushWidth,
                                onValueChange = { brushWidth = it },
                                valueRange = 2f..25f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${brushWidth.toInt()}pt", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (selectedTool == "highlight") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Highlight Size", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = highlightBrushWidth,
                                onValueChange = { highlightBrushWidth = it },
                                valueRange = 10f..60f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${highlightBrushWidth.toInt()}pt", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (selectedTool == "signature" && generatedSignatureName.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showSignatureCreatorDialog = true },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Create, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("Stamp preset: \"$generatedSignatureName\" (Tap here to edit signature name)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // TEXT ANNOTATION ADD DIALOG
        if (showTextDialog != null) {
            val offset = showTextDialog!!
            AlertDialog(
                onDismissRequest = { showTextDialog = null },
                title = { Text("Add Text Annotation", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = inputTextVal,
                            onValueChange = { inputTextVal = it },
                            placeholder = { Text("E.g. Approved, Paid, Checked") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Font Size:")
                            Slider(
                                value = inputFontSize,
                                onValueChange = { inputFontSize = it },
                                valueRange = 10f..32f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${inputFontSize.toInt()}sp", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (inputTextVal.isNotBlank()) {
                                val list = pagesTexts.getOrPut(activePageIndex) { mutableListOf() }
                                list.add(
                                    TextAnnotation(
                                        text = inputTextVal.trim(),
                                        position = offset,
                                        color = activeColor,
                                        size = inputFontSize
                                    )
                                )
                                showTextDialog = null
                            }
                        }
                    ) {
                        Text("Add text")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTextDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // SIGNATURE CREATION PRESET DIALOG
        if (showSignatureCreatorDialog) {
            AlertDialog(
                onDismissRequest = { showSignatureCreatorDialog = false },
                title = { Text("Set Custom Signature Stamp", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Type your name or initials to generate an elegant vector signature stamp:", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = generatedSignatureName,
                            onValueChange = { generatedSignatureName = it },
                            placeholder = { Text("Your Signature Initials") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Select Ink Color:")
                            val signColors = listOf(Color.Blue, Color(0xFF0D47A1), Color.Black, Color(0xFF311B92))
                            signColors.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            width = if (signaturePaletteColor == col) 2.dp else 0.dp,
                                            color = if (signaturePaletteColor == col) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { signaturePaletteColor = col }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (generatedSignatureName.isNotBlank()) {
                                showSignatureCreatorDialog = false
                            }
                        }
                    ) {
                        Text("Confirm Stamp")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSignatureCreatorDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
