package com.example.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

fun exportFileToDownloads(context: Context, sourceFile: File, displayName: String): Uri? {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, when {
            displayName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            displayName.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            displayName.endsWith(".jpg", ignoreCase = true) || displayName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            displayName.endsWith(".png", ignoreCase = true) -> "image/png"
            displayName.endsWith(".txt", ignoreCase = true) -> "text/plain"
            else -> "*/*"
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Docfusion")
        }
    }
    
    val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } else {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val destFile = File(downloadsDir, displayName)
        try {
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return Uri.fromFile(destFile)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    try {
        val uri = resolver.insert(collectionUri, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return uri
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun getFileFromUri(context: Context, uri: Uri, fileName: String): File? {
    try {
        val destinationFile = File(context.cacheDir, fileName)
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        if (inputStream != null) {
            val outputStream = FileOutputStream(destinationFile)
            val buffer = ByteArray(49152) // 48 KB high-speed buffer
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.close()
            inputStream.close()
            return destinationFile
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun resolveFileName(context: Context, uri: Uri): String {
    var name = ""
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        if (cursor != null) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
            cursor.close()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    if (name.isEmpty()) {
        name = "temp_file_${System.currentTimeMillis()}"
    }
    return name
}
