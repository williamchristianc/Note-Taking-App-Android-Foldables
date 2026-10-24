package app.folio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max

class FolioVM(app: Application) : AndroidViewModel(app) {
    private val db = FolioDb.get(app)
    private val dao = db.dao()
    private val ctx get() = getApplication<Application>()
    val prefs = Prefs(app)

    val settings: StateFlow<AppSettings?> = prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val notebooks: StateFlow<List<Notebook>> = dao.notebooks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun notebook(id: Long) = dao.notebook(id)
    fun pages(id: Long) = dao.pages(id)

    fun <T> setPref(k: Preferences.Key<T>, v: T) { viewModelScope.launch { prefs.set(k, v) } }

    fun create(nb: Notebook, done: (Long) -> Unit) = viewModelScope.launch {
        val id = dao.insert(nb)
        dao.insertPage(Page(notebookId = id, position = 0))
        dao.insertPage(Page(notebookId = id, position = 1))
        done(id)
    }

    fun update(nb: Notebook) { viewModelScope.launch { dao.update(nb.copy(updatedAt = System.currentTimeMillis())) } }
    fun setStatus(nb: Notebook, s: Int) { viewModelScope.launch { dao.update(nb.copy(status = s)) } }
    fun deleteForever(nb: Notebook) { viewModelScope.launch { dao.deleteNotebook(nb.id) } }

    fun duplicate(nb: Notebook) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = dao.insert(nb.copy(id = 0, title = nb.title + " copy", pinned = false, status = 0,
                sortOrder = now, createdAt = now, updatedAt = now))
            dao.insertPages(dao.pagesOnce(nb.id).map { it.copy(id = 0, notebookId = id) })
        }
    }

    fun swapOrder(a: Notebook, b: Notebook) {
        viewModelScope.launch {
            dao.update(a.copy(sortOrder = b.sortOrder))
            dao.update(b.copy(sortOrder = a.sortOrder))
        }
    }

    fun addPage(nbId: Long, count: Int) { viewModelScope.launch { dao.insertPage(Page(notebookId = nbId, position = count)) } }

    fun deletePage(p: Page) {
        viewModelScope.launch {
            dao.deletePage(p.id)
            dao.shiftDown(p.notebookId, p.position)
        }
    }

    fun savePage(pageId: Long, nbId: Long, text: String, strokes: String) {
        viewModelScope.launch {
            val t = System.currentTimeMillis()
            dao.savePage(pageId, text, strokes, t)
            dao.touch(nbId, t)
        }
    }

    fun setLastPage(nbId: Long, page: Int) { viewModelScope.launch { dao.setLastPage(nbId, page) } }

    suspend fun search(q: String): Pair<List<Notebook>, List<Page>> {
        val nbs = notebooks.value.filter { it.status != 2 }
        val ids = nbs.map { it.id }.toSet()
        val hitNb = nbs.filter { it.title.contains(q, ignoreCase = true) }
        val hitP = dao.searchPages(q).filter { it.notebookId in ids }
        return hitNb to hitP
    }

    suspend fun importCover(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val cr = ctx.contentResolver
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (max(o.outWidth, o.outHeight) / s > 1600) s *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = s }
            val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
                ?: return@withContext null
            val dir = File(ctx.filesDir, "covers").apply { mkdirs() }
            val f = File(dir, "c_${System.currentTimeMillis()}.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            f.absolutePath
        } catch (e: Exception) { null }
    }

    suspend fun exportPdf(nb: Notebook, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val ps = dao.pagesOnce(nb.id)
            ctx.contentResolver.openOutputStream(uri)!!.use { Exporter.pdf(nb, ps, it) }
            true
        } catch (e: Exception) { false }
    }

    suspend fun exportMarkdown(nb: Notebook, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val md = Exporter.markdown(nb, dao.pagesOnce(nb.id))
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(md.toByteArray()) }
            true
        } catch (e: Exception) { false }
    }

    suspend fun backup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject().put("version", 1)
            val na = JSONArray()
            dao.allNotebooks().forEach { n ->
                val o = JSONObject()
                o.put("id", n.id); o.put("title", n.title); o.put("material", n.material); o.put("color", n.color)
                o.put("paper", n.paper); o.put("titleFont", n.titleFont); o.put("band", n.band); o.put("ribbon", n.ribbon)
                o.put("pinned", n.pinned); o.put("status", n.status); o.put("sortOrder", n.sortOrder)
                o.put("createdAt", n.createdAt); o.put("updatedAt", n.updatedAt); o.put("lastPage", n.lastPage)
                n.coverImage?.let { path ->
                    val f = File(path)
                    if (f.exists()) o.put("cover", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
                }
                na.put(o)
            }
            val pa = JSONArray()
            dao.allPages().forEach { p ->
                pa.put(JSONObject().put("id", p.id).put("notebookId", p.notebookId).put("position", p.position)
                    .put("text", p.text).put("strokes", p.strokes).put("updatedAt", p.updatedAt))
            }
            root.put("notebooks", na).put("pages", pa)
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(root.toString().toByteArray()) }
            true
        } catch (e: Exception) { false }
    }

    suspend fun restore(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val txt = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            val root = JSONObject(txt)
            val na = root.getJSONArray("notebooks")
            val pa = root.getJSONArray("pages")
            val dir = File(ctx.filesDir, "covers").apply { mkdirs() }
            val nl = (0 until na.length()).map { i ->
                val o = na.getJSONObject(i)
                var path: String? = null
                if (o.has("cover")) {
                    val f = File(dir, "r_${o.getLong("id")}_${System.currentTimeMillis()}.jpg")
                    f.writeBytes(Base64.decode(o.getString("cover"), Base64.DEFAULT))
                    path = f.absolutePath
                }
                Notebook(
                    id = o.getLong("id"), title = o.getString("title"), material = o.getInt("material"),
                    color = o.getInt("color"), paper = o.getInt("paper"), titleFont = o.getInt("titleFont"),
                    band = o.getBoolean("band"), ribbon = o.getBoolean("ribbon"), coverImage = path,
                    pinned = o.getBoolean("pinned"), status = o.getInt("status"), sortOrder = o.getLong("sortOrder"),
                    createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), lastPage = o.getInt("lastPage")
                )
            }
            val pl = (0 until pa.length()).map { i ->
                val o = pa.getJSONObject(i)
                Page(id = o.getLong("id"), notebookId = o.getLong("notebookId"), position = o.getInt("position"),
                    text = o.getString("text"), strokes = o.getString("strokes"), updatedAt = o.getLong("updatedAt"))
            }
            db.withTransaction {
                dao.clearNotebooks()
                dao.insertNotebooks(nl)
                dao.insertPages(pl)
            }
            true
        } catch (e: Exception) { false }
    }
}
