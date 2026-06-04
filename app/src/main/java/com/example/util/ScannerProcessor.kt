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
     * Real Sobel image gradient algorithm to find the document's quadrilateral boundaries.
     * Automatically extracts page corners by evaluating highest-contrast edge candidates.
     */
    fun detectDocumentEdges(src: Bitmap): List<PointF> {
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        
        try {
            // Downscale to 120x120 pixels for high performance analysis & noise suppression
            val scanW = 120
            val scanH = (src.height * (120f / src.width)).toInt().coerceAtLeast(120)
            val scaled = Bitmap.createScaledBitmap(src, scanW, scanH, false)
            if (scaled != null) {
                val gray = IntArray(scanW * scanH)
                for (y in 0 until scanH) {
                    for (x in 0 until scanW) {
                        val color = scaled.getPixel(x, y)
                        val r = (color shr 16) and 0xFF
                        val g = (color shr 8) and 0xFF
                        val b = color and 0xFF
                        gray[y * scanW + x] = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                    }
                }
                
                // Sobel Filter kernels
                val edgeIntensity = FloatArray(scanW * scanH)
                var maxIntensity = 0f
                for (y in 1 until scanH - 1) {
                    for (x in 1 until scanW - 1) {
                        val gx = (
                            -gray[(y-1)*scanW + (x-1)] + gray[(y-1)*scanW + (x+1)] +
                            -2 * gray[y*scanW + (x-1)] + 2 * gray[y*scanW + (x+1)] +
                            -gray[(y+1)*scanW + (x-1)] + gray[(y+1)*scanW + (x+1)]
                        ).toFloat()
                        
                        val gy = (
                            -gray[(y-1)*scanW + (x-1)] - 2 * gray[(y-1)*scanW + x] - gray[(y-1)*scanW + (x+1)] +
                            gray[(y+1)*scanW + (x-1)] + 2 * gray[(y+1)*scanW + x] + gray[(y+1)*scanW + (x+1)]
                        ).toFloat()
                        
                        val mag = kotlin.math.sqrt(gx * gx + gy * gy)
                        edgeIntensity[y * scanW + x] = mag
                        if (mag > maxIntensity) {
                            maxIntensity = mag
                        }
                    }
                }
                
                // Threshold for identifying candidate edge pixels (15% of max gradient, min 25)
                val threshold = (maxIntensity * 0.15f).coerceAtLeast(25f)
                val edgePoints = mutableListOf<Point>()
                for (y in 1 until scanH - 1) {
                    for (x in 1 until scanW - 1) {
                        if (edgeIntensity[y * scanW + x] > threshold) {
                            edgePoints.add(Point(x, y))
                        }
                    }
                }
                
                scaled.recycle() // release temporary scaled bitmap
                
                if (edgePoints.isNotEmpty()) {
                    var tlPoint = Point(0, 0)
                    var trPoint = Point(scanW - 1, 0)
                    var brPoint = Point(scanW - 1, scanH - 1)
                    var blPoint = Point(0, scanH - 1)
                    
                    var minTlDist = Float.MAX_VALUE
                    var minTrDist = Float.MAX_VALUE
                    var minBrDist = Float.MAX_VALUE
                    var minBlDist = Float.MAX_VALUE
                    
                    for (p in edgePoints) {
                        // Top-Left corner: closest to (0, 0)
                        val dTl = (p.x * p.x + p.y * p.y).toFloat()
                        if (dTl < minTlDist) {
                            minTlDist = dTl
                            tlPoint = p
                        }
                        
                        // Top-Right corner: closest to (scanW - 1, 0)
                        val dTr = ((scanW - 1 - p.x) * (scanW - 1 - p.x) + p.y * p.y).toFloat()
                        if (dTr < minTrDist) {
                            minTrDist = dTr
                            trPoint = p
                        }
                        
                        // Bottom-Right corner: closest to (scanW - 1, scanH - 1)
                        val dBr = ((scanW - 1 - p.x) * (scanW - 1 - p.x) + (scanH - 1 - p.y) * (scanH - 1 - p.y)).toFloat()
                        if (dBr < minBrDist) {
                            minBrDist = dBr
                            brPoint = p
                        }
                        
                        // Bottom-Left corner: closest to (0, scanH - 1)
                        val dBl = (p.x * p.x + (scanH - 1 - p.y) * (scanH - 1 - p.y)).toFloat()
                        if (dBl < minBlDist) {
                            minBlDist = dBl
                            blPoint = p
                        }
                    }
                    
                    // Map the scaled corners back to full original resolution
                    val scaleX = w / scanW
                    val scaleY = h / scanH
                    
                    val tl = PointF(tlPoint.x * scaleX, tlPoint.y * scaleY)
                    val tr = PointF(trPoint.x * scaleX, trPoint.y * scaleY)
                    val br = PointF(brPoint.x * scaleX, brPoint.y * scaleY)
                    val bl = PointF(blPoint.x * scaleX, blPoint.y * scaleY)
                    
                    // Verify if the bounding size class is reasonable (at least 20% width/height of preview)
                    val minW = w * 0.2f
                    val minH = h * 0.2f
                    if ((tr.x - tl.x) > minW && (br.x - bl.x) > minW && (bl.y - tl.y) > minH && (br.y - tr.y) > minH) {
                        return listOf(tl, tr, br, bl)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return listOf(
            PointF(w * 0.12f, h * 0.12f), // Top-left
            PointF(w * 0.88f, h * 0.12f), // Top-right
            PointF(w * 0.88f, h * 0.88f), // Bottom-right
            PointF(w * 0.12f, h * 0.88f)  // Bottom-left
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
