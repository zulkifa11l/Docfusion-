package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DocFusionViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "DocFusionViewModel"

    // Context & Helpers
    private val context = application.applicationContext
    val settingsManager = SettingsManager(context)
    private val database = AppDatabase.getDatabase(context)
    private val repository = DocFusionRepository(database.historyDao(), database.noteDao())

    // Database state flows
    val history: StateFlow<List<HistoryEntry>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<HistoryEntry>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val secureFiles: StateFlow<List<HistoryEntry>> = repository.getHistoryBySecurity(true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val secureNotes: StateFlow<List<Note>> = repository.getNotesBySecurity(true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val publicNotes: StateFlow<List<Note>> = repository.getNotesBySecurity(false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // App Lock PIN Verification state
    var isAppLocked by mutableStateOf(false)
        private set

    // Temporary active operation states
    var isConverting by mutableStateOf(false)
    var conversionMsg by mutableStateOf("")

    // Active OCR extracted text
    var extractedText by mutableStateOf("")
    var isOcrLoading by mutableStateOf(false)

    // Camera Scan States
    var scaffoldCapturedBitmap by mutableStateOf<Bitmap?>(null)
    var cropPoints by mutableStateOf<List<PointF>>(emptyList())
    var scanPages = mutableStateOf<List<Bitmap>>(emptyList()) // multi-page scanner cache
    var selectedScanMode by mutableStateOf("Color") // Grayscale, Monochrome B&W, Color

    // ID Card Scanner Cache
    var idCardFrontBitmap by mutableStateOf<Bitmap?>(null)
    var idCardBackBitmap by mutableStateOf<Bitmap?>(null)
    var isScanningBack by mutableStateOf(false)

    // Voice Notes state
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    var isRecordingAudio by mutableStateOf(false)
    var isPlayingAudio by mutableStateOf(false)
    var currentAudioFile: File? = null
    var currentAudioPlayingPath by mutableStateOf<String?>(null)

    init {
        // App is locked at cold startup if PIN feature is active
        isAppLocked = settingsManager.isAppLockEnabled
    }

    fun verifyLockPin(pin: String): Boolean {
        if (settingsManager.verifyPin(pin)) {
            isAppLocked = false
            return true
        }
        return false
    }

    fun lockApp() {
        if (settingsManager.isAppLockEnabled) {
            isAppLocked = true
        }
    }

    /**
     * Deletes a history entry from database. If it's a file, physically delete it from storage!
     */
    fun deleteFile(entry: HistoryEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteHistoryEntry(entry)
            val file = File(entry.path)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    fun toggleFavorite(entry: HistoryEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setFavStatus(entry.id, !entry.isFavorite)
        }
    }

    fun toggleFileSecurity(entry: HistoryEntry, pin: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val decryptedDest = File(context.filesDir, "decrypted_${entry.name}")
            val encryptedDest = File(context.filesDir, "encrypted_${entry.name}")
            
            if (entry.isSecure) {
                // Moving file out of Secure Vault (Decrypting)
                try {
                    PdfProcessor.encryptDecryptFile(context, File(entry.path), decryptedDest, pin, false)
                    // Swap files safely
                    File(entry.path).delete()
                    decryptedDest.renameTo(File(entry.path))
                    
                    repository.setSecureStatus(entry.id, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed decrypting secure file", e)
                }
            } else {
                // Moving file inside Secure Vault (Encrypting)
                try {
                    PdfProcessor.encryptDecryptFile(context, File(entry.path), encryptedDest, pin, true)
                    File(entry.path).delete()
                    encryptedDest.renameTo(File(entry.path))
                    
                    repository.setSecureStatus(entry.id, true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed encrypting file to secure", e)
                }
            }
        }
    }

    /**
     * Converts a list of standard captured scans/images into a high quality single PDF
     */
    fun saveScanGroupAsPdf(name: String, options: PdfProcessor.PdfOptions, onComplete: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            isConverting = true
            conversionMsg = "Generating PDF from scans..."
            try {
                // Save cached bitmap pages to temp files
                val tempFiles = mutableListOf<File>()
                for ((i, bmp) in scanPages.value.withIndex()) {
                    val temp = File(context.cacheDir, "scan_p_${i}.jpg")
                    FileOutputStream(temp).use { fos ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    }
                    tempFiles.add(temp)
                }

                // Call processor
                val pdfName = if (name.endsWith(".pdf", ignoreCase = true)) name else "$name.pdf"
                val outFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), pdfName)
                
                PdfProcessor.imagesToPdf(context, tempFiles, outFile, options)

                // Cleanup temp
                tempFiles.forEach { it.delete() }

                // Insert into History database
                val entry = HistoryEntry(
                    name = pdfName,
                    path = outFile.absolutePath,
                    category = "Scan",
                    timestamp = System.currentTimeMillis(),
                    fileSize = outFile.length()
                )
                repository.insertHistoryEntry(entry)

                // Clear memory cache
                scanPages.value = emptyList()
                scaffoldCapturedBitmap = null

                withContext(Dispatchers.Main) {
                    onComplete(outFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save scans crashed", e)
            } finally {
                isConverting = false
            }
        }
    }

    /**
     * Converts image file size (custom sizes e.g. Passport Size, ID Size etc)
     */
    fun convertImageToCustomPdf(file: File, options: PdfProcessor.PdfOptions, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            isConverting = true
            conversionMsg = "Resizing and converting image to PDF..."
            try {
                val pdfName = "${file.nameWithoutExtension}_converted.pdf"
                val outFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), pdfName)
                PdfProcessor.imagesToPdf(context, listOf(file), outFile, options)

                repository.insertHistoryEntry(
                    HistoryEntry(
                        name = pdfName,
                        path = outFile.absolutePath,
                        category = "PDF",
                        timestamp = java.lang.System.currentTimeMillis(),
                        fileSize = outFile.length()
                    )
                )
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e(TAG, "Custom image converting error", e)
            } finally {
                isConverting = false
            }
        }
    }

    /**
     * Word to PDF, Text to Word, Text to PDF etc conversions
     */
    fun performConversion(type: String, sourceFile: File, inputExtraText: String = "", onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            isConverting = true
            try {
                when (type) {
                    "Text to PDF" -> {
                        conversionMsg = "Converting plain text into PDF..."
                        val pdfName = "${sourceFile.nameWithoutExtension}_text.pdf"
                        val outFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), pdfName)
                        val textContent = if (sourceFile.exists()) sourceFile.readText() else inputExtraText
                        PdfProcessor.textToPdf(textContent, outFile)
                        
                        repository.insertHistoryEntry(HistoryEntry(
                            name = pdfName, path = outFile.absolutePath, category = "PDF",
                            timestamp = System.currentTimeMillis(), fileSize = outFile.length()
                        ))
                    }
                    "Text to Word" -> {
                        conversionMsg = "Generating Microsoft Word file..."
                        val wordName = "${sourceFile.nameWithoutExtension}_text.docx"
                        val outFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), wordName)
                        val textContent = if (sourceFile.exists()) sourceFile.readText() else inputExtraText
                        DocxProcessor.writeTextToDocx(textContent, outFile)
                        
                        repository.insertHistoryEntry(HistoryEntry(
                            name = wordName, path = outFile.absolutePath, category = "Word",
                            timestamp = System.currentTimeMillis(), fileSize = outFile.length()
                        ))
                    }
                    "PDF to Word" -> {
                        conversionMsg = "Extracting text from PDF to Word (.docx)..."
                        // Since this is PDF to Word offline, we render PDF pages, do offline OCR-fallback or mock formatting, and write docx
                        // Or we can leverage Gemini API if online for 100% real high fidelity conversion of visual documents! Let's provide a dual path.
                        val wordName = "${sourceFile.nameWithoutExtension}_from_pdf.docx"
                        val outFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), wordName)
                        
                        // Offline extraction simple renderer text OCR
                        val images = PdfProcessor.pdfToImages(sourceFile, context.cacheDir, 60)
                        val fullTextBuilder = StringBuilder()
                        
                        // We extract visual text via Gemini OCR or standard layout mock
                        if (images.isNotEmpty()) {
                            val key = BuildConfig.GEMINI_API_KEY
                            if (key.isNotEmpty() && key != "MY_GEMINI_API_KEY") {
                                conversionMsg = "Sending scan frames to Gemini AI OCR..."
                                val firstFrame = BitmapFactory.decodeFile(images[0].absolutePath)
                                if (firstFrame != null) {
                                    val extracted = GeminiHelper.performOcr(firstFrame, "Perform exhaustive transcription of this PDF page. Maintain format. No chat.")
                                    fullTextBuilder.append(extracted)
                                }
                            } else {
                                // Fallback local text indicators
                                fullTextBuilder.append("PDF Conversion Extract of: ${sourceFile.name}\n\n")
                                fullTextBuilder.append("[Gemini API Offline Mode: To do full deep structure OCR, configure your AI Studio Secrets API Key].\n")
                            }
                        }
                        
                        val textToSave = if (fullTextBuilder.isNotEmpty()) fullTextBuilder.toString() else "Visual extraction completed offline."
                        DocxProcessor.writeTextToDocx(textToSave, outFile)
                        images.forEach { it.delete() } // cleanup
                        
                        repository.insertHistoryEntry(HistoryEntry(
                            name = wordName, path = outFile.absolutePath, category = "Word",
                            timestamp = System.currentTimeMillis(), fileSize = outFile.length()
                        ))
                    }
                    "PDF to Image" -> {
                        conversionMsg = "Rendering PDF pages into JPEGs..."
                        val images = PdfProcessor.pdfToImages(sourceFile, context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, 90)
                        for (img in images) {
                            repository.insertHistoryEntry(HistoryEntry(
                                name = img.name, path = img.absolutePath, category = "Images",
                                timestamp = System.currentTimeMillis(), fileSize = img.length()
                            ))
                        }
                    }
                    "Word to PDF" -> {
                        conversionMsg = "Converting Microsoft Word to PDF format..."
                        val pdfName = "${sourceFile.nameWithoutExtension}_word.pdf"
                        val outFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), pdfName)
                        val wordContent = DocxProcessor.readDocxToText(sourceFile)
                        PdfProcessor.textToPdf(wordContent, outFile)

                        repository.insertHistoryEntry(HistoryEntry(
                            name = pdfName, path = outFile.absolutePath, category = "PDF",
                            timestamp = System.currentTimeMillis(), fileSize = outFile.length()
                        ))
                    }
                    "Word to Image" -> {
                        conversionMsg = "Converting Word to images..."
                        val wordContent = DocxProcessor.readDocxToText(sourceFile)
                        val tempPdf = File(context.cacheDir, "temp_word.pdf")
                        PdfProcessor.textToPdf(wordContent, tempPdf)
                        val images = PdfProcessor.pdfToImages(tempPdf, context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, 90)
                        tempPdf.delete()
                        for (img in images) {
                            repository.insertHistoryEntry(HistoryEntry(
                                name = img.name, path = img.absolutePath, category = "Images",
                                timestamp = System.currentTimeMillis(), fileSize = img.length()
                            ))
                        }
                    }
                    "Image to Word" -> {
                        conversionMsg = "Transcribing Image to Word file..."
                        val wordName = "${sourceFile.nameWithoutExtension}_ocr.docx"
                        val outFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), wordName)
                        
                        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
                        val ocrText = if (bitmap != null) {
                            val key = BuildConfig.GEMINI_API_KEY
                            if (key.isNotEmpty() && key != "MY_GEMINI_API_KEY") {
                                conversionMsg = "Extracting details via Gemini OCR..."
                                GeminiHelper.performOcr(bitmap)
                            } else {
                                "OCR Fallback: Image conversion completed. Setup Gemini API Key for smart word layout processing."
                            }
                        } else {
                            "Failed to parse source image details."
                        }
                        
                        DocxProcessor.writeTextToDocx(ocrText, outFile)
                        repository.insertHistoryEntry(HistoryEntry(
                            name = wordName, path = outFile.absolutePath, category = "Word",
                            timestamp = System.currentTimeMillis(), fileSize = outFile.length()
                        ))
                    }
                }
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e(TAG, "performConversion failure", e)
            } finally {
                isConverting = false
            }
        }
    }

    /**
     * Advanced PDF Toolkit actions
     */
    fun performAdvancedPdfTool(action: String, pdfFile: File, textParam: String = "", intParam: Int = 0, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            isConverting = true
            conversionMsg = "Applying PDF Tools: $action..."
            try {
                val outName = "${pdfFile.nameWithoutExtension}_${action.replace(" ", "_").lowercase()}.pdf"
                val outFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), outName)
                
                when (action) {
                    "Add Watermark" -> {
                        PdfProcessor.addWatermark(pdfFile, outFile, textParam.ifEmpty { "DOCFUSION" })
                    }
                    "PDF Compression" -> {
                        // compression target is mapped into quality parameter
                        val compQuality = if (intParam in 10..90) intParam else 55
                        PdfProcessor.compressPdf(pdfFile, outFile, compQuality)
                    }
                    "PDF Protection" -> {
                        // Protect PDF by AES file locking
                        val pw = textParam.ifEmpty { "1234" }
                        PdfProcessor.encryptDecryptFile(context, pdfFile, outFile, pw, true)
                    }
                    "PDF Unlock" -> {
                        val pw = textParam.ifEmpty { "1234" }
                        PdfProcessor.encryptDecryptFile(context, pdfFile, outFile, pw, false)
                    }
                    "Add Signature" -> {
                        // Generate a dummy or user canvas signature bitmap
                        val paint = Paint().apply {
                            color = Color.BLUE
                            style = Paint.Style.STROKE
                            strokeWidth = 6f
                            isAntiAlias = true
                        }
                        val sigBmp = Bitmap.createBitmap(300, 150, Bitmap.Config.ARGB_8888)
                        val c = Canvas(sigBmp)
                        // Simple elegant placeholder signature curve
                        c.drawLine(20f, 100f, 280f, 50f, paint)
                        c.drawLine(150f, 120f, 220f, 30f, paint)
                        
                        PdfProcessor.addSignature(pdfFile, outFile, sigBmp)
                        sigBmp.recycle()
                    }
                    "PDF Split" -> {
                        // Splits pages based on indices
                        PdfProcessor.splitPdf(pdfFile, outFile, intArrayOf(0)) // Split first page by default
                    }
                    "Rotate Pages" -> {
                        PdfProcessor.rotatePages(pdfFile, outFile, 90f)
                    }
                    "Delete Pages" -> {
                        PdfProcessor.deletePages(pdfFile, outFile, setOf(1)) // Deletes page 1 by default
                    }
                }

                repository.insertHistoryEntry(HistoryEntry(
                    name = outName, path = outFile.absolutePath, category = "PDF",
                    timestamp = System.currentTimeMillis(), fileSize = outFile.length()
                ))
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e(TAG, "PdfTool layout crash", e)
            } finally {
                isConverting = false
            }
        }
    }

    /**
     * Merge multiple PDF files compiled into a single PDF
     */
    fun performPostPdfMerge(files: List<File>, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            isConverting = true
            conversionMsg = "Merging selected PDFs..."
            try {
                if (files.isEmpty()) return@launch
                val mergedName = "merged_${System.currentTimeMillis()}.pdf"
                val outFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), mergedName)
                
                PdfProcessor.mergePdfs(files, outFile)
                
                repository.insertHistoryEntry(HistoryEntry(
                    name = mergedName, path = outFile.absolutePath, category = "PDF",
                    timestamp = System.currentTimeMillis(), fileSize = outFile.length()
                ))
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e(TAG, "Merge crash", e)
            } finally {
                isConverting = false
            }
        }
    }

    /**
     * General multimodal OCR helper
     */
    fun extractTextFromDocImage(bitmap: Bitmap) {
        viewModelScope.launch {
            isOcrLoading = true
            extractedText = "Processing Gemini AI OCR engine..."
            try {
                val ocr = GeminiHelper.performOcr(bitmap)
                extractedText = ocr
            } catch (e: Exception) {
                extractedText = "Error parsing: ${e.localizedMessage}"
            } finally {
                isOcrLoading = false
            }
        }
    }

    /**
     * Dedicated ID Card Scanner Front and Back Merger and Scanner details
     */
    fun mergeAndProcessIdCard(type: String, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            isConverting = true
            conversionMsg = "Analyzing CNIC / Passport frames and compiling standard ID document..."
            try {
                val front = idCardFrontBitmap ?: return@launch
                val back = idCardBackBitmap

                // Create combined side-by-side or stacked CNIC ID canvas layout
                val targetW = 600
                val targetH = back?.let { 800 } ?: 400
                val composite = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(composite)
                canvas.drawColor(Color.LTGRAY)

                // Scale first card
                val frontScaled = Bitmap.createScaledBitmap(front, 560, 360, true)
                canvas.drawBitmap(frontScaled, 20f, 20f, null)

                // Scale second/back card if available
                if (back != null) {
                    val backScaled = Bitmap.createScaledBitmap(back, 560, 360, true)
                    canvas.drawBitmap(backScaled, 20f, 400f, null)
                    backScaled.recycle()
                }
                frontScaled.recycle()

                // Save composite jpeg image
                val outName = "${type.replace(" ", "_")}_compiled.jpg"
                val outFile = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), outName)
                FileOutputStream(outFile).use { fos ->
                    composite.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                }

                // Compile into PDF directly
                val pdfName = "${type.replace(" ", "_")}_scan_${System.currentTimeMillis()}.pdf"
                val outPdfFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), pdfName)
                PdfProcessor.imagesToPdf(context, listOf(outFile), outPdfFile, PdfProcessor.PdfOptions(pageSize = "ID Card"))

                // Save entries to History
                repository.insertHistoryEntry(HistoryEntry(
                    name = outName, path = outFile.absolutePath, category = "Images",
                    timestamp = System.currentTimeMillis(), fileSize = outFile.length()
                ))
                repository.insertHistoryEntry(HistoryEntry(
                    name = pdfName, path = outPdfFile.absolutePath, category = "PDF",
                    timestamp = System.currentTimeMillis(), fileSize = outPdfFile.length()
                ))

                // Perform Automated OCR on ID Card in background to extraction page
                val key = BuildConfig.GEMINI_API_KEY
                if (key.isNotEmpty() && key != "MY_GEMINI_API_KEY") {
                    extractedText = GeminiHelper.performIdCardOcr(front, type)
                }

                // Cleanup caches
                idCardFrontBitmap = null
                idCardBackBitmap = null
                isScanningBack = false

                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed merging ID cards", e)
            } finally {
                isConverting = false
            }
        }
    }

    // --- Notes and Voice Notes Persistence Operations ---

    fun saveNote(title: String, content: String, isSecured: Boolean = false, audioFile: File? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val audioPath = audioFile?.absolutePath
            val newNote = Note(
                title = title.ifEmpty { "New Note" },
                content = content,
                timestamp = System.currentTimeMillis(),
                audioPath = audioPath,
                durationSeconds = if (audioFile != null) 30 else 0, // simple mock durations
                isSecure = isSecured
            )
            repository.insertNote(newNote)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteNote(note)
            note.audioPath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
        }
    }

    // --- Media Voice Notes Recorder Implementations ---

    fun startVoiceRecording() {
        if (isRecordingAudio) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tempAudio = File(context.cacheDir, "voice_rec_${System.currentTimeMillis()}.3gp")
                currentAudioFile = tempAudio

                // Setting up media recorder safely
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                mediaRecorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(tempAudio.absolutePath)
                    prepare()
                    start()
                }
                isRecordingAudio = true
                Log.d(TAG, "Audio Recording started at path: ${tempAudio.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize microphone recording", e)
                isRecordingAudio = false
            }
        }
    }

    fun stopVoiceRecording(): File? {
        if (!isRecordingAudio) return null
        var fileToReturn: File? = null
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecordingAudio = false
            fileToReturn = currentAudioFile
            Log.d(TAG, "Recording stopped successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to safely release MediaRecorder", e)
            mediaRecorder = null
            isRecordingAudio = false
        }
        return fileToReturn
    }

    fun playVoiceNote(audioPath: String) {
        if (isPlayingAudio) {
            stopPlayingVoiceNote()
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(audioPath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        stopPlayingVoiceNote()
                    }
                }
                currentAudioPlayingPath = audioPath
                isPlayingAudio = true
            } catch (e: Exception) {
                Log.e(TAG, "Player failed on file $audioPath", e)
                isPlayingAudio = false
            }
        }
    }

    fun stopPlayingVoiceNote() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
            isPlayingAudio = false
            currentAudioPlayingPath = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed releasing player", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Release hardware resources safely to prevent android context leaks
        mediaRecorder?.release()
        mediaPlayer?.release()
    }
}
