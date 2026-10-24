package app.folio

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Materials = listOf("Leather", "Linen", "Recycled paper", "Hardcover", "Minimal")
val TitleFonts = listOf("Serif", "Sans", "Script")
val Swatches = listOf(0xFF7A4A2B, 0xFF2F4B3A, 0xFF1F3552, 0xFF7B2D3B, 0xFF3A3A3F, 0xFFC9A66B, 0xFFD9CFC0, 0xFF5B4B8A).map { it.toInt() }

fun titleFamily(i: Int): FontFamily = when (i) { 0 -> FontFamily.Serif; 1 -> FontFamily.SansSerif; else -> FontFamily.Cursive }

fun loadCover(path: String): ImageBitmap? = try { BitmapFactory.decodeFile(path)?.asImageBitmap() } catch (e: Exception) { null }

private fun DrawScope.texture(m: Int, base: Color, seed: Long) {
    val r = java.util.Random(seed)
    val hi = lerp(base, Color.White, 0.08f)
    val lo = lerp(base, Color.Black, 0.14f)
    drawRect(Brush.verticalGradient(listOf(hi, base, lo)))
    val n = (size.width * size.height / 900f).toInt().coerceIn(120, 1800)
    when (m) {
        0 -> repeat(n) {
            drawCircle(
                if (r.nextBoolean()) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.08f),
                0.6f + r.nextFloat() * 1.8f,
                Offset(r.nextFloat() * size.width, r.nextFloat() * size.height)
            )
        }
        1 -> {
            var x = 0f
            while (x < size.width) { drawLine(Color.Black.copy(alpha = 0.07f), Offset(x, 0f), Offset(x, size.height), 1f); x += 4f }
            var y = 0f
            while (y < size.height) { drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, y), Offset(size.width, y), 1f); y += 4f }
        }
        2 -> {
            drawRect(Color(0xFFD8C9AE).copy(alpha = 0.35f))
            repeat(n) {
                drawCircle(Color(0xFF6B5A3E).copy(alpha = 0.25f), 0.5f + r.nextFloat() * 1.2f,
                    Offset(r.nextFloat() * size.width, r.nextFloat() * size.height))
            }
            repeat(n / 4) {
                val x = r.nextFloat() * size.width
                val y = r.nextFloat() * size.height
                drawLine(Color(0xFF8A7A5C).copy(alpha = 0.25f), Offset(x, y), Offset(x + r.nextFloat() * 10f, y + r.nextFloat() * 3f), 1f)
            }
        }
        3 -> {
            drawRect(Color.Black.copy(alpha = 0.12f), Offset(0f, 0f), Size(size.width * 0.1f, size.height))
            drawLine(Color.White.copy(alpha = 0.18f), Offset(size.width * 0.1f, 0f), Offset(size.width * 0.1f, size.height), 2f)
        }
        else -> {
            val inset = size.width * 0.06f
            drawRect(Color.White.copy(alpha = 0.35f), Offset(inset, inset),
                Size(size.width - 2 * inset, size.height - 2 * inset), style = Stroke(1.5f))
        }
    }
}

@Composable
fun CoverArt(nb: Notebook, modifier: Modifier = Modifier) {
    val base = Color(nb.color)
    val dark = base.luminance() < 0.5f
    val fg = if (dark) Color(0xFFF3E9D6) else Color(0xFF2A2118)
    val bmp = remember(nb.coverImage) { nb.coverImage?.let { loadCover(it) } }
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp)
    Box(modifier) {
        // paper block peeking out at the right and bottom edges
        Box(
            Modifier.fillMaxSize().padding(start = 6.dp, top = 5.dp)
                .background(Color(0xFFF3ECDD), RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))
        )
        BoxWithConstraints(Modifier.fillMaxSize().padding(end = 6.dp, bottom = 5.dp).shadow(6.dp, shape).clip(shape)) {
            val fs = (maxWidth.value * 0.085f).coerceIn(9f, 46f)
            if (bmp != null) {
                Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
            } else {
                Canvas(Modifier.fillMaxSize()) { texture(nb.material, base, nb.color.toLong() xor (nb.material * 7919L)) }
            }
            Canvas(Modifier.fillMaxSize()) {
                val sw = size.width * 0.09f
                drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent), 0f, sw),
                    size = Size(sw, size.height))
                drawLine(Color.Black.copy(alpha = 0.25f), Offset(size.width * 0.07f, 0f), Offset(size.width * 0.07f, size.height), 2f)
                if (nb.band) {
                    val bx = size.width * 0.84f
                    drawRect(Color.Black.copy(alpha = 0.45f), Offset(bx, 0f), Size(size.width * 0.025f, size.height))
                    drawRect(Color.White.copy(alpha = 0.12f), Offset(bx, 0f), Size(size.width * 0.006f, size.height))
                }
                if (nb.ribbon) {
                    val x0 = size.width * 0.62f
                    val rw = size.width * 0.045f
                    val y0 = size.height * 0.86f
                    val p = Path()
                    p.moveTo(x0, y0); p.lineTo(x0 + rw, y0); p.lineTo(x0 + rw, size.height)
                    p.lineTo(x0 + rw / 2f, size.height - rw * 0.8f); p.lineTo(x0, size.height); p.close()
                    drawPath(p, Color(0xFF9E2A2B))
                }
            }
            val title = nb.title
            if (bmp == null && nb.material != 4) {
                val ls = RoundedCornerShape(3.dp)
                Box(
                    Modifier.align(Alignment.Center).fillMaxWidth(0.62f)
                        .background(Color(0xFFF3ECDD).copy(alpha = 0.92f), ls)
                        .border(1.dp, Color(0xFF8A7A5C), ls)
                        .padding(vertical = (fs * 0.6f).dp, horizontal = 6.dp)
                ) {
                    Text(title, Modifier.fillMaxWidth(), color = Color(0xFF2A2118), fontFamily = titleFamily(nb.titleFont),
                        fontSize = fs.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(title, Modifier.align(Alignment.BottomStart).padding(start = (fs * 1.6f).dp, end = (fs * 1.6f).dp, bottom = (fs * 1.2f).dp),
                    color = if (bmp != null) Color.White else fg, fontFamily = titleFamily(nb.titleFont),
                    fontSize = (fs * 1.1f).sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
