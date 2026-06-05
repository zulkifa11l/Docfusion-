package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.ui.viewinterop.AndroidView
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.example.ui.viewmodel.DocFusionViewModel
import com.example.util.DocxProcessor
import com.example.util.PdfProcessor
import com.example.util.getFileFromUri
import com.example.util.resolveFileName
import java.io.File

class OCRJavaScriptInterface(
    private val onProgress: (Int) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    @android.webkit.JavascriptInterface
    fun onProgress(percentage: Int) {
        onProgress(percentage)
    }

    @android.webkit.JavascriptInterface
    fun onStatus(status: String) {
        onStatus(status)
    }

    @android.webkit.JavascriptInterface
    fun onResult(text: String) {
        onResult(text)
    }

    @android.webkit.JavascriptInterface
    fun onError(error: String) {
        onError(error)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    viewModel: DocFusionViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPhotoName by remember { mutableStateOf("") }
    
    // Configurable OCR engine option: Tesseract (Local) or Gemini (AI Cloud)
    var ocrEngineMode by remember { mutableStateOf("Tesseract") } 
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var ocrProgressPercentage by remember { mutableIntStateOf(0) }
    var ocrStatusMsg by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    fun runLocalOcr(bitmap: Bitmap) {
        viewModel.isOcrLoading = true
        viewModel.extractedText = ""
        ocrProgressPercentage = 0
        ocrStatusMsg = "Preparing image layers..."
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                val byteArray = outputStream.toByteArray()
                val base64String = "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
                withContext(Dispatchers.Main) {
                    webViewRef?.evaluateJavascript("runOcr('$base64String')", null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    viewModel.isOcrLoading = false
                    viewModel.extractedText = "Preparation Error: ${e.localizedMessage}"
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedPhotoUri = uri
            selectedPhotoName = resolveFileName(context, uri)
            
            val file = getFileFromUri(context, uri, selectedPhotoName)
            if (file != null) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    if (ocrEngineMode == "Tesseract") {
                        runLocalOcr(bitmap)
                    } else {
                        viewModel.extractTextFromDocImage(bitmap)
                    }
                }
                file.delete()
            }
        }
    }

    LaunchedEffect(viewModel.scaffoldCapturedBitmap) {
        viewModel.scaffoldCapturedBitmap?.let { bmp ->
            if (ocrEngineMode == "Tesseract") {
                runLocalOcr(bmp)
            } else {
                viewModel.extractTextFromDocImage(bmp)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI OCR & Text Recognition", fontWeight = FontWeight.Bold) },
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
            // OCR Engine Selector Row
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.35f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val activeColor = MaterialTheme.colorScheme.primaryContainer
                    val activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    val inactiveColor = Color.Transparent
                    val inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant

                    Button(
                        onClick = { ocrEngineMode = "Tesseract" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (ocrEngineMode == "Tesseract") activeColor else inactiveColor,
                            contentColor = if (ocrEngineMode == "Tesseract") activeContentColor else inactiveContentColor
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Tesseract (Local JS)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("Offline-ready engine", fontSize = 9.sp)
                        }
                    }

                    Button(
                        onClick = { ocrEngineMode = "Gemini" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (ocrEngineMode == "Gemini") activeColor else inactiveColor,
                            contentColor = if (ocrEngineMode == "Gemini") activeContentColor else inactiveContentColor
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Gemini AI (Cloud)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("Premium accuracy", fontSize = 9.sp)
                        }
                    }
                }
            }
            
            // Upload button if no file is selected yet
            if (selectedPhotoUri == null && viewModel.scaffoldCapturedBitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.24f), RoundedCornerShape(16.dp))
                        .clickable { photoPickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Upload Document Photo", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Extract text with selected OCR engine", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (viewModel.scaffoldCapturedBitmap != null) "Camera Scanned Document" else selectedPhotoName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        selectedPhotoUri = null
                        selectedPhotoName = ""
                        viewModel.scaffoldCapturedBitmap = null
                        viewModel.extractedText = ""
                    }) {
                        Text("Clear Image")
                    }
                }
            }

            // Results Container
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (viewModel.isOcrLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (ocrEngineMode == "Tesseract") {
                                    if (ocrProgressPercentage > 0) "Recognizing characters: $ocrProgressPercentage%" else if (ocrStatusMsg.isNotEmpty()) ocrStatusMsg else "Initializing Tesseract engine..."
                                } else {
                                    "Scanning document layers with AI..."
                                },
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (ocrEngineMode == "Tesseract") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "(Requires internet on first launch to fetch layers)",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    } else if (viewModel.extractedText.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No transcription completed. Select or capture an image card above.",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        // Displaying OCR result
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Extracted OCR Text", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Docfusion OCR", viewModel.extractedText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = viewModel.extractedText,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Quick Export Buttons Panel (Enabled only when text is transcribed!)
            if (viewModel.extractedText.isNotEmpty() && !viewModel.isOcrLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            val temp = File(context.cacheDir, "temp_ocr.txt").apply { writeText(viewModel.extractedText) }
                            viewModel.performConversion("Text to PDF", temp) {
                                temp.delete()
                                Toast.makeText(context, "Transcribed PDF saved to Documents!", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val temp = File(context.cacheDir, "temp_ocr.txt").apply { writeText(viewModel.extractedText) }
                            viewModel.performConversion("Text to Word", temp) {
                                temp.delete()
                                Toast.makeText(context, "Transcribed Word (.docx) saved to file list!", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Article, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Word", fontSize = 12.sp)
                    }

                    // Save to Notes system
                    Button(
                        onClick = {
                            viewModel.saveNote(
                                title = "OCR Scanned Note",
                                content = viewModel.extractedText,
                                tags = "OCR Scan"
                            ) { id ->
                                Toast.makeText(context, "Saved to Notes successfully!", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Note", fontSize = 12.sp)
                    }
                }
            }

            // Hidden WebView for Tesseract local OCR
            Box(modifier = Modifier.size(0.dp)) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            addJavascriptInterface(
                                OCRJavaScriptInterface(
                                    onProgress = { p -> ocrProgressPercentage = p },
                                    onStatus = { s -> ocrStatusMsg = s },
                                    onResult = { r -> 
                                        coroutineScope.launch {
                                            viewModel.extractedText = r
                                            viewModel.isOcrLoading = false
                                        }
                                    },
                                    onError = { e -> 
                                        coroutineScope.launch {
                                            viewModel.extractedText = "OCR Failed: $e"
                                            viewModel.isOcrLoading = false
                                            Toast.makeText(context, e, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                ),
                                "AndroidOCR"
                            )
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                }
                            }
                            val htmlString = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <script src="https://cdn.jsdelivr.net/npm/tesseract.js@5/dist/tesseract.min.js"></script>
                                <script>
                                    window.onload = function() {
                                        if (typeof Tesseract !== 'undefined') {
                                            AndroidOCR.onStatus("Local Tesseract Engine Loaded & Ready");
                                        } else {
                                            AndroidOCR.onError("Failed to load Tesseract JS from CDN.");
                                        }
                                    }
                                    function runOcr(base64Image) {
                                        try {
                                            AndroidOCR.onStatus("Initializing OCR engine...");
                                            Tesseract.recognize(
                                                base64Image,
                                                'eng',
                                                {
                                                    logger: m => {
                                                        if (m.status === 'recognizing text') {
                                                            var percentage = Math.round(m.progress * 100);
                                                            AndroidOCR.onProgress(percentage);
                                                        } else {
                                                            AndroidOCR.onStatus("Status: " + m.status);
                                                        }
                                                    }
                                                }
                                            ).then(function(res) {
                                                AndroidOCR.onResult(res.data.text || "No text could be extracted.");
                                            }).catch(function(err) {
                                                AndroidOCR.onError("OCR Engine Error: " + err.message);
                                            });
                                        } catch(e) {
                                            AndroidOCR.onError("Script Error: " + e.message);
                                        }
                                    }
                                </script>
                            </head>
                            <body style="background:transparent; margin:0; padding:10px;">
                                <div style="font-size:12px; color:#aaa;">Local Tesseract.js engine active.</div>
                            </body>
                            </html>
                            """.trimIndent()
                            loadDataWithBaseURL("https://localhost", htmlString, "text/html", "UTF-8", null)
                            webViewRef = this
                        }
                    }
                )
            }
        }
    }
}
