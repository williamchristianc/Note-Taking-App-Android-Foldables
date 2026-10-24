package app.folio

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notebooks")
data class Notebook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val material: Int = 0,
    val color: Int = 0xFF7A4A2B.toInt(),
    val paper: Int = 1,
    val titleFont: Int = 0,
    val band: Boolean = true,
    val ribbon: Boolean = true,
    val coverImage: String? = null,
    val pinned: Boolean = false,
    val status: Int = 0, // 0 active, 1 archived, 2 trash
    val sortOrder: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastPage: Int = 0
)

@Entity(
    tableName = "pages",
    indices = [Index("notebookId")],
    foreignKeys = [ForeignKey(
        entity = Notebook::class,
        parentColumns = ["id"],
        childColumns = ["notebookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Page(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val position: Int,
    val text: String = "",
    val strokes: String = "[]",
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface FolioDao {
    @Query("SELECT * FROM notebooks") fun notebooks(): Flow<List<Notebook>>
    @Query("SELECT * FROM notebooks WHERE id = :id") fun notebook(id: Long): Flow<Notebook?>
    @Insert suspend fun insert(n: Notebook): Long
    @Update suspend fun update(n: Notebook)
    @Query("DELETE FROM notebooks WHERE id = :id") suspend fun deleteNotebook(id: Long)
    @Query("UPDATE notebooks SET lastPage = :p WHERE id = :id") suspend fun setLastPage(id: Long, p: Int)
    @Query("UPDATE notebooks SET updatedAt = :t WHERE id = :id") suspend fun touch(id: Long, t: Long)

    @Query("SELECT * FROM pages WHERE notebookId = :id ORDER BY position") fun pages(id: Long): Flow<List<Page>>
    @Query("SELECT * FROM pages WHERE notebookId = :id ORDER BY position") suspend fun pagesOnce(id: Long): List<Page>
    @Insert suspend fun insertPage(p: Page): Long
    @Query("DELETE FROM pages WHERE id = :id") suspend fun deletePage(id: Long)
    @Query("UPDATE pages SET position = position - 1 WHERE notebookId = :nb AND position > :pos")
    suspend fun shiftDown(nb: Long, pos: Int)
    @Query("UPDATE pages SET text = :t, strokes = :s, updatedAt = :u WHERE id = :id")
    suspend fun savePage(id: Long, t: String, s: String, u: Long)
    @Query("SELECT * FROM pages WHERE text LIKE '%' || :q || '%' LIMIT 100")
    suspend fun searchPages(q: String): List<Page>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPages(p: List<Page>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNotebooks(n: List<Notebook>)
    @Query("SELECT * FROM notebooks") suspend fun allNotebooks(): List<Notebook>
    @Query("SELECT * FROM pages") suspend fun allPages(): List<Page>
    @Query("DELETE FROM notebooks") suspend fun clearNotebooks()
}

@Database(entities = [Notebook::class, Page::class], version = 1, exportSchema = false)
abstract class FolioDb : RoomDatabase() {
    abstract fun dao(): FolioDao

    companion object {
        @Volatile private var inst: FolioDb? = null
        fun get(c: Context): FolioDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, FolioDb::class.java, "folio.db")
                .build().also { inst = it }
        }
    }
}
