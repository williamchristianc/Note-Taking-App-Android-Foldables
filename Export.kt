package app.folio

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

object Exporter {
    fun pdf(nb: Notebook, pages: List<Page>, out: OutputStream) {
        val w = 595
        val h = 842
        val s = 27f
        val doc = PdfDocument()
        val list = if (pages.isEmpty()) listOf(Page(notebookId = nb.id, position = 0)) else pages
        list.forEachIndexed { i, p ->
            val pg = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
            val c = pg.canvas
            c.drawColor(PaperInt)
            val g = Paper.geom(nb.paper, w.toFloat(), h.toFloat(), s)
            val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LineInt; strokeWidth = 0.6f }
            c.drawLines(g.lines, lp)
            var k = 0
            while (k < g.dots.size - 1) { c.drawCircle(g.dots[k], g.dots[k + 1], 1.2f, lp); k += 2 }

            if (p.text.isNotEmpty()) {
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = InkInt; textSize = 15f; typeface = Typeface.SERIF
                }
                val sl = StaticLayout.Builder.obtain(p.text, 0, p.text.length, tp, w - 70)
                    .setLineSpacing(s - tp.fontSpacing, 1f).build()
                c.save(); c.translate(40f, s * 1.5f - 14f); sl.draw(c); c.restore()
            }

            InkIO.parse(p.strokes).forEach { st ->
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = st.color
                    alpha = if (st.hl) 90 else 255
                    strokeWidth = st.width * w
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val q = st.pts
                if (q.size < 4) {
                    if (q.size >= 2) {
                        sp.style = Paint.Style.FILL
                        c.drawCircle(q[0] * w, q[1] * w, st.width * w / 2f, sp)
                    }
                } else {
                    val path = Path()
                    path.moveTo(q[0] * w, q[1] * w)
                    var j = 2
                    while (j < q.size - 3) {
                        path.quadTo(q[j] * w, q[j + 1] * w, (q[j] + q[j + 2]) / 2f * w, (q[j + 1] + q[j + 3]) / 2f * w)
                        j += 2
                    }
                    path.lineTo(q[q.size - 2] * w, q[q.size - 1] * w)
                    c.drawPath(path, sp)
                }
            }
            doc.finishPage(pg)
        }
        doc.writeTo(out)
        doc.close()
    }

    fun markdown(nb: Notebook, pages: List<Page>): String {
        val sb = StringBuilder("# ${nb.title}\n\n")
        pages.forEachIndexed { i, p ->
            sb.append("## Page ${i + 1}\n\n")
            sb.append(if (p.text.isBlank()) "_(blank)_" else p.text)
            if (p.strokes != "[]") sb.append("\n\n_(handwriting is not included in Markdown)_")
            sb.append("\n\n")
        }
        return sb.toString()
    }
}
