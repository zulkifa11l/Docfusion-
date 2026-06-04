package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavorites(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries WHERE isSecure = :secure ORDER BY timestamp DESC")
    fun getHistoryBySecurity(secure: Boolean): Flow<List<HistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: HistoryEntry): Long

    @Query("UPDATE history_entries SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE history_entries SET isSecure = :isSecure WHERE id = :id")
    suspend fun setSecure(id: Long, isSecure: Boolean)

    @Delete
    suspend fun deleteEntry(entry: HistoryEntry)

    @Query("DELETE FROM history_entries WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("UPDATE history_entries SET tags = :tags WHERE id = :id")
    suspend fun updateHistoryTags(id: Long, tags: String?)

    @Query("SELECT * FROM history_entries")
    suspend fun getAllHistoryList(): List<HistoryEntry>
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isSecure = :secure ORDER BY timestamp DESC")
    fun getNotesBySecurity(secure: Boolean): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesList(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Query("UPDATE notes SET tags = :tags WHERE id = :id")
    suspend fun updateNoteTags(id: Long, tags: String?)

    @Delete
    suspend fun deleteNote(note: Note)
}

@Database(entities = [HistoryEntry::class, Note::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "docfusion_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
