package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_entries")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val path: String,
    val category: String, // "PDF", "Word", "Scan", "Text", "Images"
    val timestamp: Long,
    val fileSize: Long,
    val isFavorite: Boolean = false,
    val isSecure: Boolean = false
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val timestamp: Long,
    val audioPath: String? = null, // Path to recording if voice note
    val durationSeconds: Int = 0,
    val isSecure: Boolean = false
)
