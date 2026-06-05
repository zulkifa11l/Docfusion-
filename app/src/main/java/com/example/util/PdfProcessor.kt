package com.example.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import kotlin.math.max
import kotlin.math.min

object PdfProcessor {

    data class PdfOptions(
        val pageSize: String = "A4", // "A4", "Letter", "Passport", "ID Card", "Original"
        val compressQuality: Int = 90, // 0 to 100
        val isGrayscale: Boolean = false
    )

    /**
     * Converts a list of Images into a single beautifully-wrapped PDF Document.
     */
    fun imagesToPdf(context: Context, imageFiles: List<File>, outputFile: File, options: PdfOptions) {
        val pdfDocument = PdfDocument()

        for ((index, file) in imageFiles.withIndex()) {
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue

            // 1. Scale down extremely large images if compression is active to save lots of space
            val scaledBitmap = if (options.compressQuality < 100) {
                val maxDim = if (options.compressQuality <= 35) 900 else if (options.compressQuality <= 65) 1300 else 1800
                if (originalBitmap.width > maxDim || originalBitmap.height > maxDim) {
                    val scale = maxDim.toFloat() / max(originalBitmap.width, originalBitmap.height)
                    Bitmap.createScaledBitmap(
                        originalBitmap,
                        (originalBitmap.width * scale).toInt(),
                        (originalBitmap.height * scale).toInt(),
                        true
                    )
                } else {
                    originalBitmap
                }
            } else {
                originalBitmap
            }

            // 2. Apply Grayscale if requested
            val processedBitmap = if (options.isGrayscale) {
                convertToGrayscale(scaledBitmap)
            } else {
                scaledBitmap
            }

            // 3. Apply quality compression to the processed bitmap if quality < 100
            val finalBitmap = if (options.compressQuality < 100) {
                val bos = ByteArrayOutputStream()
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, options.compressQuality, bos)
                val bytes = bos.toByteArray()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                processedBitmap
            }

            // Determine dimensions based on selected options
            val (pageWidth, pageHeight) = when (options.pageSize) {
                "Passport" -> Pair(144, 144) // 2x2 inches (at 72 dpi)
                "ID Card" -> Pair(243, 153)  // 3.375 x 2.125 inches (CR80)
                "Letter" -> Pair(612, 792)   // 8.5 x 11 inches
                "Original" -> Pair(finalBitmap.width, finalBitmap.height)
                else -> Pair(595, 842)       // A4 size: 595 x 842 pt
            }

            // Create page info and start a page
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Fill page background with white
            canvas.drawColor(Color.WHITE)

            // Scaled bitmap drawing which fits in canvas dimensions with margin
            val margin = 16f
            val maxDrawWidth = pageWidth - (margin * 2)
            val maxDrawHeight = pageHeight - (margin * 2)

            val scale = min(
                maxDrawWidth / finalBitmap.width.toFloat(),
                maxDrawHeight / finalBitmap.height.toFloat()
            )

            val drawWidth = finalBitmap.width * scale
            val drawHeight = finalBitmap.height * scale

            val left = (pageWidth - drawWidth) / 2f
            val top = (pageHeight - drawHeight) / 2f

            val destRect = RectF(left, top, left + drawWidth, top + drawHeight)
            val paint = Paint().apply {
                isFilterBitmap = true
                isAntiAlias = true
            }

            canvas.drawBitmap(finalBitmap, null, destRect, paint)
            pdfDocument.finishPage(page)

            // Recycle intermediate temporary bitmaps safely to prevent OOM
            if (finalBitmap != processedBitmap) {
                finalBitmap.recycle()
            }
            if (processedBitmap != scaledBitmap) {
                processedBitmap.recycle()
            }
            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()
    }

    /**
     * Converts an existing PDF into a list of JPEG Images, rendered page by page.
     */
    fun pdfToImages(inputFile: File, outputDir: File, quality: Int = 90): List<File> {
        val resultList = mutableListOf<File>()
        if (!inputFile.exists()) return resultList

        try {
            val fileDescriptor = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)

                // High fidelity bitmap scaling (using a 2.5x multiplier for high-quality export)
                val width = (page.width * 2.5f).toInt()
                val height = (page.height * 2.5f).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                // Set canvas background to white before rendering
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val imageFile = File(outputDir, "${inputFile.nameWithoutExtension}_page_${i + 1}.jpg")
                FileOutputStream(imageFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
                }
                bitmap.recycle()
                resultList.add(imageFile)
            }
            renderer.close()
            fileDescriptor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultList
    }

    /**
     * Generates a PDF containing lines of beautifully formatted text.
     */
    fun textToPdf(text: String, outputFile: File) {
        val pdfDocument = PdfDocument()
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val margin = 40f
        val pageHeight = 842 // A4 Height
        val pageWidth = 595  // A4 Width
        val contentWidth = pageWidth - (margin * 2)

        val paragraphs = text.split("\n")
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        var yOffset = margin + 30f

        // Draw basic header
        canvas.drawText("Docfusion Created Document", margin, yOffset, titlePaint)
        yOffset += 40f

        for (paragraph in paragraphs) {
            val words = paragraph.split(" ")
            var lineBuilder = StringBuilder()

            for (word in words) {
                val testLine = if (lineBuilder.isEmpty()) word else "${lineBuilder} $word"
                val testWidth = textPaint.measureText(testLine)

                if (testWidth > contentWidth) {
                    // Draw current line
                    if (yOffset > pageHeight - margin) {
                        pdfDocument.finishPage(page)
                        pageNum++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        canvas.drawColor(Color.WHITE)
                        yOffset = margin
                    }
                    canvas.drawText(lineBuilder.toString(), margin, yOffset, textPaint)
                    yOffset += 18f
                    lineBuilder = StringBuilder(word)
                } else {
                    lineBuilder = StringBuilder(testLine)
                }
            }

            // Draw remaining line
            if (lineBuilder.isNotEmpty()) {
                if (yOffset > pageHeight - margin) {
                    pdfDocument.finishPage(page)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    yOffset = margin
                }
                canvas.drawText(lineBuilder.toString(), margin, yOffset, textPaint)
                yOffset += 24f // Double space after paragraph
            }
        }

        pdfDocument.finishPage(page)
        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()
    }

    /**
     * Merges several existing PDF files into a single combined PDF Document.
     */
    fun mergePdfs(inputFiles: List<File>, outputFile: File) {
        val pdfDocument = PdfDocument()
        var pageIndex = 1

        for (file in inputFiles) {
            if (!file.exists()) continue
            try {
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fileDescriptor)

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)

                    // Re-draw page to new pdf page using high dpi bitmaps
                    val width = page.width
                    val height = page.height
                    
                    val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageIndex++).create()
                    val newPage = pdfDocument.startPage(pageInfo)
                    
                    val bitmap = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888)
                    val bitmapCanvas = Canvas(bitmap)
                    bitmapCanvas.drawColor(Color.WHITE)
                    
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    // Draw bitmap on newPage's canvas
                    newPage.canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
                    pdfDocument.finishPage(newPage)
                    bitmap.recycle()
                }
                renderer.close()
                fileDescriptor.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()
    }

    /**
     * Standard compressor that rescales high-resolution PDF pages down.
     */
    fun compressPdf(inputFile: File, outputFile: File, qualityPercent: Int = 60) {
        if (!inputFile.exists()) return
        val pdfDocument = PdfDocument()
        
        try {
            val fileDescriptor = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)
            
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                
                // Downscale page dimensions to compress size (max out at 1000px width/height)
                val scale = min(1.0f, 1000f / max(page.width, page.height))
                val targetW = (page.width * scale).toInt()
                val targetH = (page.height * scale).toInt()
                
                val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, i + 1).create()
                val newPage = pdfDocument.startPage(pageInfo)
                
                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val bitmapCanvas = Canvas(bitmap)
                bitmapCanvas.drawColor(Color.WHITE)
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                // Compress bitmap with target quality before writing
                val compressedStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, qualityPercent, compressedStream)
                val compressedBytes = compressedStream.toByteArray()
                val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
                
                newPage.canvas.drawBitmap(compressedBitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                pdfDocument.finishPage(newPage)
                
                bitmap.recycle()
                compressedBitmap.recycle()
            }
            
            renderer.close()
            fileDescriptor.close()
            
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Splits PDF pages by extracting specific pages into a custom targeted list.
     */
    fun splitPdf(inputFile: File, outputFile: File, targetPages: IntArray) {
        if (!inputFile.exists()) return
        val pdfDocument = PdfDocument()
        var newPageIndex = 1
        
        try {
            val fileDescriptor = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)
            
            for (idx in targetPages) {
                if (idx < 0 || idx >= renderer.pageCount) continue
                val page = renderer.openPage(idx)
                
                val width = page.width
                val height = page.height
                
                val pageInfo = PdfDocument.PageInfo.Builder(width, height, newPageIndex++).create()
                val newPage = pdfDocument.startPage(pageInfo)
                
                val bitmap = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888)
                val bitmapCanvas = Canvas(bitmap)
                bitmapCanvas.drawColor(Color.WHITE)
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                newPage.canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
                pdfDocument.finishPage(newPage)
                bitmap.recycle()
            }
            
            renderer.close()
            fileDescriptor.close()
            
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Deletes specified page numbers and writes the clean items out.
     */
    fun deletePages(inputFile: File, outputFile: File, pagesToDelete: Set<Int>) {
        if (!inputFile.exists()) return
        try {
            val fileDescriptor = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)
            val remainingPages = mutableListOf<Int>()
            for (i in 0 until renderer.pageCount) {
                // Pages are 1-based in primary human UI
                if (!pagesToDelete.contains(i + 1)) {
                    remainingPages.add(i)
                }
            }
            renderer.close()
            fileDescriptor.close()
            
            splitPdf(inputFile, outputFile, remainingPages.toIntArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Reorders pages by specific index lists.
     */
    fun reorderPages(inputFile: File, outputFile: File, customOrder: List<Int>) {
        // Custom order has 1-based human indexing, convert to 0-based index array
        val targetIndices = customOrder.map { it - 1 }.toIntArray()
        splitPdf(inputFile, outputFile, targetIndices)
    }

    /**
     * Adds a beautiful, transparent watermark overlay on all pages in a PDF file.
     */
    fun addWatermark(inputFile: File, outputFile: File, watermarkText: String) {
        if (!inputFile.exists()) return
        val pdfDocument = PdfDocument()
        
        try {
            val fileDescriptor = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)
            
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val width = page.width
                val height = page.height
                
                val pageInfo = PdfDocument.PageInfo.Builder(width, height, i + 1).create()
                val newPage = pdfDocument.startPage(pageInfo)
                
                val bitmap = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888)
                val bitmapCanvas = Canvas(bitmap)
                bitmapCanvas.drawColor(Color.WHITE)
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                val canvas = newPage.canvas
                // Draw normal page
                canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
                
                // Draw Watermark Overlay
                canvas.save()
                canvas.rotate(-45f, width / 2f, height / 2f)
                
                val watermarkPaint = Paint().apply {
                    color = Color.RED
                    alpha = 40 // Transparent overlay
                    textSize = (width / 10f).coerceIn(24f, 64f)
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                
                canvas.drawText(watermarkText, width / 2f, height / 2f, watermarkPaint)
                canvas.restore()
                
                pdfDocument.finishPage(newPage)
                bitmap.recycle()
            }
            
            renderer.close()
            fileDescriptor.close()
            
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Overlays a high quality signature graphic onto the bottom of all or last PDF page.
     */
    fun addSignature(inputFile: File, outputFile: File, signatureBitmap: Bitmap, posXPercent: Float = 0.7f, posYPercent: Float = 0.85f) {
        if (!inputFile.exists()) return
        val pdfDocument = PdfDocument()
        
        try {
            val fileDescriptor = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)
            
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val width = page.width
                val height = page.height
                
                val pageInfo = PdfDocument.PageInfo.Builder(width, height, i + 1).create()
                val newPage = pdfDocument.startPage(pageInfo)
                
                val bitmap = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888)
                val bitmapCanvas = Canvas(bitmap)
                bitmapCanvas.drawColor(Color.WHITE)
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                val canvas = newPage.canvas
                // Draw original page
                canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
                
                // Only sign on the last page by default, or all pages if desired
                if (i == renderer.pageCount - 1) {
                    val sigW = (width * 0.25f).toInt()
                    val sigH = (signatureBitmap.height * (sigW.toFloat() / signatureBitmap.width)).toInt()
                    
                    val left = width * posXPercent
                    val top = height * posYPercent
                    
                    val destRect = RectF(left, top, left + sigW, top + sigH)
                    val paint = Paint().apply {
                        isFilterBitmap = true
                        isAntiAlias = true
                    }
                    
                    canvas.drawBitmap(signatureBitmap, null, destRect, paint)
                }
                
                pdfDocument.finishPage(newPage)
                bitmap.recycle()
            }
            
            renderer.close()
            fileDescriptor.close()
            
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Rotates all pages by a specific degree angle (e.g. 90, 180, 270).
     */
    fun rotatePages(inputFile: File, outputFile: File, rotationDegrees: Float) {
        if (!inputFile.exists()) return
        val pdfDocument = PdfDocument()
        
        try {
            val fileDescriptor = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)
            
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                
                // When rotating 90 or 270, swap width & height
                val isSwapped = rotationDegrees == 90f || rotationDegrees == 270f || rotationDegrees == -90f
                val w = if (isSwapped) page.height else page.width
                val h = if (isSwapped) page.width else page.height
                
                val pageInfo = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
                val newPage = pdfDocument.startPage(pageInfo)
                
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                val bitmapCanvas = Canvas(bitmap)
                bitmapCanvas.drawColor(Color.WHITE)
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                val canvas = newPage.canvas
                canvas.save()
                
                // Shift and rotate
                canvas.translate(w / 2f, h / 2f)
                canvas.rotate(rotationDegrees)
                
                val destRect = RectF(-page.width / 2f, -page.height / 2f, page.width / 2f, page.height / 2f)
                canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
                
                pdfDocument.finishPage(newPage)
                bitmap.recycle()
            }
            
            renderer.close()
            fileDescriptor.close()
            
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Robust AES-CBC encrypts standard files to execute lock configurations.
     */
    fun encryptDecryptFile(context: Context, source: File, destination: File, keyString: String, encrypt: Boolean) {
        try {
            val keyBytes = keyString.padEnd(16, 'x').take(16).toByteArray()
            val secretKeySpec = SecretKeySpec(keyBytes, "AES")
            val iv = ByteArray(16) { 0x00.toByte() } // Zero IV
            val ivParameterSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            if (encrypt) {
                cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
            } else {
                cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
            }

            val fis = FileInputStream(source)
            val fos = FileOutputStream(destination)
            
            val inputBuffer = ByteArray(4096)
            var bytesRead: Int
            while (fis.read(inputBuffer).also { bytesRead = it } != -1) {
                val outputBuffer = cipher.update(inputBuffer, 0, bytesRead)
                if (outputBuffer != null) {
                    fos.write(outputBuffer)
                }
            }
            val finalBuffer = cipher.doFinal()
            if (finalBuffer != null) {
                fos.write(finalBuffer)
            }
            
            fis.close()
            fos.flush()
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun convertToGrayscale(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }
}
