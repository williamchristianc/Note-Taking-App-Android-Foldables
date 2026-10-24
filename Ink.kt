package app.folio

import org.json.JSONArray
import org.json.JSONObject

/** One vector stroke. Coordinates and width are normalised by page width, so they scale on fold/unfold. */
class InkStroke(val color: Int, val width: Float, val hl: Boolean, val pts: FloatArray)

object InkIO {
    fun parse(s: String): List<InkStroke> = try {
        val a = JSONArray(s)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val p = o.getJSONArray("p")
            InkStroke(o.getInt("c"), o.getDouble("w").toFloat(), o.optBoolean("h"),
                FloatArray(p.length()) { i -> p.getDouble(i).toFloat() })
        }
    } catch (e: Exception) { emptyList() }

    fun write(l: List<InkStroke>): String {
        val a = JSONArray()
        l.forEach { s ->
            val o = JSONObject()
            o.put("c", s.color); o.put("w", s.width.toDouble()); o.put("h", s.hl)
            val p = JSONArray()
            s.pts.forEach { p.put(Math.round(it * 10000f) / 10000.0) }
            o.put("p", p)
            a.put(o)
        }
        return a.toString()
    }
}

class Geom(val lines: FloatArray, val dots: FloatArray)

object Paper {
    val names = listOf("Blank", "Lined", "Dotted", "Graph", "Cornell notes", "Daily journal")

    fun geom(style: Int, w: Float, h: Float, s: Float): Geom {
        val l = ArrayList<Float>()
        val d = ArrayList<Float>()
        fun ln(a: Float, b: Float, c: Float, e: Float) { l.add(a); l.add(b); l.add(c); l.add(e) }
        val top = s * 1.5f
        when (style) {
            1 -> { var y = top; while (y < h - s * 0.5f) { ln(0f, y, w, y); y += s } }
            2 -> {
                var y = top
                while (y < h - s * 0.5f) {
                    var x = s
                    while (x < w - s * 0.5f) { d.add(x); d.add(y); x += s }
                    y += s
                }
            }
            3 -> {
                var y = top; while (y < h) { ln(0f, y, w, y); y += s }
                var x = s; while (x < w) { ln(x, 0f, x, h); x += s }
            }
            4 -> {
                val bottom = h - s * 4
                var y = top; while (y < bottom) { ln(0f, y, w, y); y += s }
                ln(w * 0.3f, 0f, w * 0.3f, bottom); ln(0f, bottom, w, bottom)
            }
            5 -> {
                ln(s, s * 3f, w - s, s * 3f)
                var y = s * 4.5f; while (y < h - s * 0.5f) { ln(s, y, w - s, y); y += s }
            }
        }
        return Geom(l.toFloatArray(), d.toFloatArray())
    }
}

val PaperInt = 0xFFFBF6EA.toInt()
val LineInt = 0x553B5B7A
val InkInt = 0xFF1F2430.toInt()
val InkColors = listOf(0xFF1F2430, 0xFF1D4ED8, 0xFFC62828, 0xFF2E7D32, 0xFF6D4C41, 0xFF6A1B9A, 0xFFE8A200).map { it.toInt() }
