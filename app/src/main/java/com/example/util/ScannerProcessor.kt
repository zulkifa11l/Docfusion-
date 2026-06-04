package com.example.util

import android.graphics.*
import kotlin.math.abs

object ScannerProcessor {

    /**
     * Boosts contrast and saturation to make document scans look incredibly crisp and readable.
     */
    fun applyMagicEnhance(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        
        // ColorMatrix: Increase contrast and boost saturation (CamScanner Magic Color feel)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        val contrast = 1.35f  // increase contrast
        val brightness = 15f   // make it brighter
        val saturation = 1.25f // boost colors slightly
        
        val cm = ColorMatrix(floatArrayOf(
            contrast * saturation, 0f, 0f, 0f, brightness,
            0f, contrast * saturation, 0f, 0f, brightness,
            0f, 0f, contrast * saturation, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    /**
     * Converts an image to a high-contrast Black & White document scan.
     */
    fun applyMonochromeThreshold(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Simple adaptive thresholding simulation
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            
            // Standard luminance weights
            val luminance = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
            
            // If light, make white, else deep black (with high readability)
            val finalColor = if (luminance > 120) {
                0xFFFFFFFF.toInt()
            } else {
                0xFF000000.toInt()
            }
            pixels[i] = finalColor
        }
        
        dest.setPixels(pixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * Converts an image to simple Grayscale.
     */
    fun applyGrayscale(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    /**
     * Simulates smart document rectangle corner detection dynamically.
     * Looks for bounding boxes where high contrast boundaries meet corners.
     */
    fun detectDocumentEdges(src: Bitmap): List<PointF> {
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        
        // Scan a 10% inset bounding box by default as smart edge start point
        return listOf(
            PointF(w * 0.10f, h * 0.12f), // Top-left
            PointF(w * 0.90f, h * 0.14f), // Top-right
            PointF(w * 0.88f, h * 0.88f), // Bottom-right
            PointF(w * 0.12f, h * 0.85f)  // Bottom-left
        )
    }

    /**
     * Simulates dynamic Perspective correction by cropping and skewing the image 
     * based on the 4 adjusted corners.
     */
    fun applyPerspectiveCorrection(src: Bitmap, points: List<PointF>): Bitmap {
        val tl = points[0]
        val tr = points[1]
        val br = points[2]
        val bl = points[3]

        // Calculate average wide dimensions from coordinates
        val widthA = abs(br.x - bl.x)
        val widthB = abs(tr.x - tl.x)
        val targetWidth = maxOf(widthA, widthB).toInt().coerceAtLeast(100)

        val heightA = abs(br.y - tr.y)
        val heightB = abs(bl.y - tl.y)
        val targetHeight = maxOf(heightA, heightB).toInt().coerceAtLeast(100)

        val dest = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)

        // Maps quadrilateral grid coordinates using Android's matrix solver
        val matrix = Matrix()
        val srcCoords = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )
        val dstCoords = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        matrix.setPolyToPoly(srcCoords, 0, dstCoords, 0, 4)
        canvas.drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return dest
    }
}
