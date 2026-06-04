package com.example.data

import kotlinx.coroutines.flow.Flow

class DocFusionRepository(
    private val historyDao: HistoryDao,
    private val noteDao: NoteDao
) {
    val allHistory: Flow<List<HistoryEntry>> = historyDao.getAllHistory()
    val favorites: Flow<List<HistoryEntry>> = historyDao.getFavorites()
    
    fun getHistoryBySecurity(secure: Boolean): Flow<List<HistoryEntry>> = 
        historyDao.getHistoryBySecurity(secure)

    suspend fun insertHistoryEntry(entry: HistoryEntry): Long = 
        historyDao.insertEntry(entry)

    suspend fun setFavStatus(id: Long, isFavorite: Boolean) = 
        historyDao.setFavorite(id, isFavorite)

    suspend fun setSecureStatus(id: Long, isSecure: Boolean) = 
        historyDao.setSecure(id, isSecure)

    suspend fun deleteHistoryEntry(entry: HistoryEntry) = 
        historyDao.deleteEntry(entry)

    suspend fun deleteHistoryByPath(path: String) = 
        historyDao.deleteByPath(path)

    // Notes Section
    fun getNotesBySecurity(secure: Boolean): Flow<List<Note>> = 
        noteDao.getNotesBySecurity(secure)

    suspend fun getNoteById(id: Long): Note? = 
        noteDao.getNoteById(id)

    suspend fun insertNote(note: Note): Long = 
        noteDao.insertNote(note)

    suspend fun deleteNote(note: Note) = 
        noteDao.deleteNote(note)
}
