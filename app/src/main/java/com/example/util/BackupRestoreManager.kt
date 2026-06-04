package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.DocFusionRepository
import com.example.data.HistoryEntry
import com.example.data.Note
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupRestoreManager {
    private const val TAG = "BackupRestoreManager"

    fun exportBackup(
        context: Context,
        historyList: List<HistoryEntry>,
        noteList: List<Note>,
        displayName: String
    ): Uri? {
        try {
            val tempZipFile = File(context.cacheDir, "docfusion_backup_temp.zip")
            if (tempZipFile.exists()) tempZipFile.delete()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZipFile))).use { zos ->
                // 1. Serialize history list to JSON and write to ZIP
                val historyJson = historyEntriesToJson(historyList)
                addStringToZip(zos, historyJson, "history.json")

                // 2. Serialize notes list to JSON and write to ZIP
                val notesJson = notesToJson(noteList)
                addStringToZip(zos, notesJson, "notes.json")

                // 3. Zip physical files from HistoryEntries
                for (entry in historyList) {
                    val file = File(entry.path)
                    if (file.exists() && file.isFile) {
                        addFileToZip(zos, file, "files/${file.name}")
                    }
                }

                // 4. Zip audio files from Notes
                for (note in noteList) {
                    if (note.audioPath != null) {
                        val file = File(note.audioPath)
                        if (file.exists() && file.isFile) {
                            addFileToZip(zos, file, "files/${file.name}")
                        }
                    }
                }
            }

            // Export compiled zip to Downloads/Docfusion
            val exportedUri = exportFileToDownloads(context, tempZipFile, displayName)
            tempZipFile.delete()
            return exportedUri
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting backup", e)
            return null
        }
    }

    suspend fun importBackup(
        context: Context,
        backupZipUri: Uri,
        repository: DocFusionRepository
    ): Boolean {
        var tempExtractDir: File? = null
        var tempZipFile: File? = null
        try {
            // 1. Copy the Zip file from Uri to cache
            tempZipFile = File(context.cacheDir, "imported_backup_temp.zip")
            if (tempZipFile.exists()) tempZipFile.delete()

            context.contentResolver.openInputStream(backupZipUri).use { input ->
                if (input == null) return false
                tempZipFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // 2. Extract ZIP
            tempExtractDir = File(context.cacheDir, "extracted_backup_temp")
            if (tempExtractDir.exists()) tempExtractDir.deleteRecursively()
            tempExtractDir.mkdirs()

            ZipInputStream(BufferedInputStream(FileInputStream(tempZipFile))).use { zis ->
                var zipEntry = zis.nextEntry
                while (zipEntry != null) {
                    val destFile = File(tempExtractDir, zipEntry.name)
                    destFile.parentFile?.mkdirs()
                    if (!zipEntry.isDirectory) {
                        destFile.outputStream().use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    zipEntry = zis.nextEntry
                }
            }

            // 3. Parse history.json
            val historyJsonFile = File(tempExtractDir, "history.json")
            if (historyJsonFile.exists()) {
                val historyJson = historyJsonFile.readText(Charsets.UTF_8)
                val restoredEntries = jsonToHistoryEntries(historyJson)
                for (entry in restoredEntries) {
                    val origFile = File(entry.path)
                    val backupFileInZip = File(tempExtractDir, "files/${origFile.name}")
                    
                    val finalPath = if (backupFileInZip.exists()) {
                        val internalTarget = File(context.filesDir, origFile.name)
                        val resolvedTarget = resolveUniqueFile(internalTarget)
                        backupFileInZip.inputStream().use { input ->
                            resolvedTarget.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        resolvedTarget.absolutePath
                    } else {
                        entry.path // Fallback if file not packaged
                    }

                    val updatedEntry = entry.copy(path = finalPath)
                    repository.insertHistoryEntry(updatedEntry)
                }
            }

            // 4. Parse notes.json
            val notesJsonFile = File(tempExtractDir, "notes.json")
            if (notesJsonFile.exists()) {
                val notesJson = notesJsonFile.readText(Charsets.UTF_8)
                val restoredNotes = jsonToNotes(notesJson)
                for (note in restoredNotes) {
                    var finalAudioPath: String? = null
                    if (note.audioPath != null) {
                        val origAudioFile = File(note.audioPath)
                        val backupAudioInZip = File(tempExtractDir, "files/${origAudioFile.name}")
                        
                        if (backupAudioInZip.exists()) {
                            val internalTarget = File(context.filesDir, origAudioFile.name)
                            val resolvedTarget = resolveUniqueFile(internalTarget)
                            backupAudioInZip.inputStream().use { input ->
                                resolvedTarget.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            finalAudioPath = resolvedTarget.absolutePath
                        } else {
                            finalAudioPath = note.audioPath
                        }
                    }

                    val updatedNote = note.copy(audioPath = finalAudioPath)
                    repository.insertNote(updatedNote)
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error importing backup", e)
            return false
        } finally {
            // Cleanup temp files
            try {
                tempZipFile?.delete()
                tempExtractDir?.deleteRecursively()
            } catch (ex: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    private fun resolveUniqueFile(file: File): File {
        if (!file.exists()) return file
        val baseName = file.nameWithoutExtension
        val ext = file.extension
        val parent = file.parentFile
        var counter = 1
        var candidate = File(parent, "$baseName-$counter.$ext")
        while (candidate.exists()) {
            counter++
            candidate = File(parent, "$baseName-$counter.$ext")
        }
        return candidate
    }

    private fun addStringToZip(zos: ZipOutputStream, content: String, entryName: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryPath: String) {
        val entry = ZipEntry(entryPath)
        try {
            zos.putNextEntry(entry)
            file.inputStream().use { input ->
                input.copyTo(zos)
            }
            zos.closeEntry()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to zip file: ${file.absolutePath}", e)
        }
    }

    private fun historyEntriesToJson(list: List<HistoryEntry>): String {
        val array = JSONArray()
        for (entry in list) {
            val obj = JSONObject()
            obj.put("name", entry.name)
            obj.put("path", entry.path)
            obj.put("category", entry.category)
            obj.put("timestamp", entry.timestamp)
            obj.put("fileSize", entry.fileSize)
            obj.put("isFavorite", entry.isFavorite)
            obj.put("isSecure", entry.isSecure)
            array.put(obj)
        }
        return array.toString()
    }

    private fun jsonToHistoryEntries(jsonStr: String): List<HistoryEntry> {
        val list = mutableListOf<HistoryEntry>()
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                HistoryEntry(
                    id = 0,
                    name = obj.getString("name"),
                    path = obj.getString("path"),
                    category = obj.getString("category"),
                    timestamp = obj.getLong("timestamp"),
                    fileSize = obj.getLong("fileSize"),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    isSecure = obj.optBoolean("isSecure", false)
                )
            )
        }
        return list
    }

    private fun notesToJson(list: List<Note>): String {
        val array = JSONArray()
        for (note in list) {
            val obj = JSONObject()
            obj.put("title", note.title)
            obj.put("content", note.content)
            obj.put("timestamp", note.timestamp)
            if (note.audioPath != null) {
                obj.put("audioPath", note.audioPath)
            } else {
                obj.put("audioPath", JSONObject.NULL)
            }
            obj.put("durationSeconds", note.durationSeconds)
            obj.put("isSecure", note.isSecure)
            array.put(obj)
        }
        return array.toString()
    }

    private fun jsonToNotes(jsonStr: String): List<Note> {
        val list = mutableListOf<Note>()
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val audioPath = if (obj.isNull("audioPath")) null else obj.getString("audioPath")
            list.add(
                Note(
                    id = 0,
                    title = obj.getString("title"),
                    content = obj.getString("content"),
                    timestamp = obj.getLong("timestamp"),
                    audioPath = audioPath,
                    durationSeconds = obj.optInt("durationSeconds", 0),
                    isSecure = obj.optBoolean("isSecure", false)
                )
            )
        }
        return list
    }
}
