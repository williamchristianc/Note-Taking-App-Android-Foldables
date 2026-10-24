@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, FlowPreview::class)

package app.folio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

val DeskColor = Color(0xFF2B241C)
val FontNames = listOf("Serif", "Sans", "Handwritten", "Mono")
fun fontFamilyFor(i: Int): FontFamily = when (i) {
    0 -> FontFamily.Serif; 1 -> FontFamily.SansSerif; 2 -> FontFamily.Cursive; else -> FontFamily.Monospace
}

class Tools(val mode: Int, val color: Int, val wMul: Float, val font: FontFamily, val fingerDraw: Boolean)

fun DrawScope.drawPaper(style: Int, s: Float, c: Color) {
    val g = Paper.geom(style, size.width, size.height, s)
    var i = 0
    while (i < g.lines.size - 3) {
        drawLine(c, Offset(g.lines[i], g.lines[i + 1]), Offset(g.lines[i + 2], g.lines[i + 3]), 1f); i += 4
    }
    i = 0
    while (i < g.dots.size - 1) { drawCircle(c, 2f, Offset(g.dots[i], g.dots[i + 1])); i += 2 }
}

fun DrawScope.drawInk(pts: FloatArray, c: Color, w: Float, hl: Boolean, scale: Float) {
    val col = if (hl) c.copy(alpha = 0.35f) else c
    val sw = w * scale
    if (pts.size < 4) {
        if (pts.size >= 2) drawCircle(col, sw / 2f, Offset(pts[0] * scale, pts[1] * scale))
        return
    }
    val p = Path()
    p.moveTo(pts[0] * scale, pts[1] * scale)
    var i = 2
    while (i < pts.size - 3) {
        p.quadraticBezierTo(pts[i] * scale, pts[i + 1] * scale,
            (pts[i] + pts[i + 2]) / 2f * scale, (pts[i + 1] + pts[i + 3]) / 2f * scale)
        i += 2
    }
    p.lineTo(pts[pts.size - 2] * scale, pts[pts.size - 1] * scale)
    drawPath(p, col, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
fun NotebookScreen(vm: FolioVM, st: AppSettings, id: Long, startPage: Int, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val nbState by remember(id) { vm.notebook(id) }.collectAsState(initial = null)
    val pages by remember(id) { vm.pages(id) }.collectAsState(initial = emptyList())
    var opened by rememberSaveable(id) { mutableStateOf(startPage >= 0) }
    val anim = remember(id) { Animatable(if (opened) 1f else 0f) }
    val nb = nbState
    if (nb == null) {
        Box(Modifier.fillMaxSize().background(DeskColor))
        return
    }
    val sp = if (startPage >= 0) startPage else nb.lastPage

    fun open() {
        if (opened) return
        opened = true
        if (st.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { anim.animateTo(1f, tween(if (st.reduce) 120 else 900, easing = FastOutSlowInEasing)) }
    }

    Box(Modifier.fillMaxSize().background(DeskColor)) {
        if (opened && pages.isNotEmpty()) Reader(vm, st, nb, pages, sp, onBack)
        if (anim.value < 1f) {
            Box(
                Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = -anim.value * 170f
                        cameraDistance = 18f * density
                        alpha = (1f - (anim.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    }
                    .clickable(onClickLabel = "Open notebook", indication = null,
                        interactionSource = remember { MutableInteractionSource() }) { open() }
                    .pointerInput(Unit) { detectHorizontalDragGestures { _, d -> if (d < -10f) open() } }
            ) {
                CoverArt(nb, Modifier.fillMaxSize())
                IconButton(onBack, Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.25f), CircleShape)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to library", tint = Color.White)
                }
                val fg = if (Color(nb.color).luminance() < 0.5f || nb.coverImage != null) Color.White else Color(0xFF2A2118)
                Text("Swipe or tap to open", Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                    color = fg.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Reader(vm: FolioVM, st: AppSettings, nb: Notebook, pages: List<Page>, startPage: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by rememberSaveable(nb.id) { mutableIntStateOf(max(0, startPage)) }
    var chrome by rememberSaveable { mutableStateOf(true) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var color by rememberSaveable { mutableIntStateOf(InkColors[0]) }
    var wMul by rememberSaveable { mutableFloatStateOf(1f) }
    var showToc by remember { mutableStateOf(false) }
    var showPaper by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableIntStateOf(-1) }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch { toast(ctx, if (vm.exportPdf(nb, uri)) "PDF exported" else "Export failed") }
    }
    val mdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) scope.launch { toast(ctx, if (vm.exportMarkdown(nb, uri)) "Markdown exported" else "Export failed") }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPage = maxWidth >= 640.dp
        val per = if (twoPage) 2 else 1
        val spreads = max(1, (pages.size + per - 1) / per)
        val pager = rememberPagerState(initialPage = (current / per).coerceIn(0, spreads - 1)) { spreads }

        LaunchedEffect(per, pager) {
            pager.scrollToPage((current / per).coerceIn(0, spreads - 1))
            snapshotFlow { pager.settledPage }.collect {
                current = it * per
                vm.setLastPage(nb.id, current)
            }
        }

        fun go(spread: Int) {
            val t = spread.coerceIn(0, pager.pageCount - 1)
            scope.launch { if (st.reduce) pager.scrollToPage(t) else pager.animateScrollToPage(t) }
        }

        val tools = Tools(mode, color, wMul, fontFamilyFor(st.font), st.finger)

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            AnimatedVisibility(chrome) {
                Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to library") }
                        Column(Modifier.weight(1f)) {
                            Text(nb.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                            val a = current + 1
                            val b = min(current + per, pages.size)
                            Text(if (twoPage && b > a) "Pages $a–$b of ${pages.size}" else "Page $a of ${pages.size}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton({ go(pager.currentPage - 1) }, enabled = pager.currentPage > 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous page")
                        }
                        IconButton({ go(pager.currentPage + 1) }, enabled = pager.currentPage < spreads - 1) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next page")
                        }
                        IconButton({ showToc = true }) { Icon(Icons.Default.Menu, "Pages list") }
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Paper style…") }, onClick = { menu = false; showPaper = true })
                                DropdownMenuItem(text = { Text("Export as PDF") }, onClick = { menu = false; pdfLauncher.launch("${nb.title}.pdf") })
                                DropdownMenuItem(text = { Text("Export as Markdown") }, onClick = { menu = false; mdLauncher.launch("${nb.title}.md") })
                                DropdownMenuItem(text = { Text("Distraction-free mode") }, onClick = { menu = false; chrome = false })
                                DropdownMenuItem(text = { Text("Delete page ${current + 1}") }, onClick = { menu = false; confirmDel = current })
                                if (twoPage && current + 1 < pages.size)
                                    DropdownMenuItem(text = { Text("Delete page ${current + 2}") }, onClick = { menu = false; confirmDel = current + 1 })
                            }
                        }
                    }
                }
            }

            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), userScrollEnabled = mode == 0) { index ->
                Box(
                    Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp).graphicsLayer {
                        if (!st.reduce) {
                            val off = (pager.currentPage - index) + pager.currentPageOffsetFraction
                            rotationY = off * -14f
                            alpha = 1f - min(abs(off) * 0.4f, 0.6f)
                            cameraDistance = 16f * density
                        }
                    }
                ) {
                    Row(Modifier.fillMaxSize().shadow(8.dp, RoundedCornerShape(4.dp))) {
                        PageSlot(index * per, pages, nb, tools, vm) { vm.addPage(nb.id, pages.size) }
                        if (twoPage) {
                            Box(Modifier.width(18.dp).fillMaxHeight().background(Brush.horizontalGradient(
                                listOf(Color(0xFFEFE6D2), Color(0xFFBFB190), Color(0xFFEFE6D2)))))
                            PageSlot(index * per + 1, pages, nb, tools, vm) { vm.addPage(nb.id, pages.size) }
                        }
                    }
                }
            }

            AnimatedVisibility(chrome) {
                Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Type", "Pen", "Marker", "Eraser").forEachIndexed { i, l ->
                            FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(l) })
                        }
                        InkColors.forEach { c ->
                            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                                .border(if (color == c) 3.dp else 1.dp,
                                    if (color == c) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f), CircleShape)
                                .clickable(onClickLabel = "Select ink colour") {
                                    color = c
                                    if (mode == 0 || mode == 3) mode = 1
                                })
                        }
                        listOf("S" to 0.6f, "M" to 1f, "L" to 1.8f).forEach { (l, v) ->
                            FilterChip(selected = wMul == v, onClick = { wMul = v }, label = { Text(l) })
                        }
                        IconButton({
                            vm.addPage(nb.id, pages.size)
                            scope.launch { delay(200); pager.animateScrollToPage(pager.pageCount - 1) }
                        }) { Icon(Icons.Default.Add, "Add page") }
                    }
                }
            }
        }

        if (!chrome) {
            FilledTonalIconButton({ chrome = true },
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp).alpha(0.75f)) {
                Icon(Icons.Default.Menu, "Show toolbars")
            }
        }

        if (showToc) {
            AlertDialog(
                onDismissRequest = { showToc = false },
                title = { Text("Pages") },
                text = {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        itemsIndexed(pages) { i, p ->
                            ListItem(
                                headlineContent = { Text("Page ${i + 1}") },
                                supportingContent = {
                                    val t = p.text.trim().lineSequence().firstOrNull().orEmpty()
                                    Text(when {
                                        t.isNotEmpty() -> t
                                        p.strokes != "[]" -> "(handwriting)"
                                        else -> "Blank"
                                    }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                modifier = Modifier.clickable { showToc = false; go(i / per) }
                            )
                        }
                    }
                },
                confirmButton = { TextButton({ showToc = false }) { Text("Close") } }
            )
        }
        if (showPaper) {
            AlertDialog(
                onDismissRequest = { showPaper = false },
                title = { Text("Paper style") },
                text = {
                    Column {
                        Paper.names.forEachIndexed { i, n ->
                            TextButton({ vm.update(nb.copy(paper = i)); showPaper = false }, Modifier.fillMaxWidth()) {
                                Text(if (nb.paper == i) "✓ $n" else n, Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                            }
                        }
                    }
                },
                confirmButton = { TextButton({ showPaper = false }) { Text("Close") } }
            )
        }
        if (confirmDel >= 0) {
            AlertDialog(
                onDismissRequest = { confirmDel = -1 },
                title = { Text("Delete page ${confirmDel + 1}?") },
                text = { Text("Its text and handwriting will be removed.") },
                confirmButton = {
                    TextButton({
                        val p = pages.getOrNull(confirmDel)
                        if (p != null && pages.size > 1) vm.deletePage(p) else toast(ctx, "A notebook needs at least one page")
                        confirmDel = -1
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton({ confirmDel = -1 }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun RowScope.PageSlot(idx: Int, pages: List<Page>, nb: Notebook, tools: Tools, vm: FolioVM, onAdd: () -> Unit) {
    val p = pages.getOrNull(idx)
    if (p != null) {
        key(p.id) { PageView(p, nb, tools, idx + 1, vm, Modifier.weight(1f).fillMaxHeight()) }
    } else {
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(PaperInt)), contentAlignment = Alignment.Center) {
            TextButton(onAdd) { Text("Add page") }
        }
    }
}

@Composable
private fun PageView(page: Page, nb: Notebook, tools: Tools, number: Int, vm: FolioVM, modifier: Modifier) {
    val dens = LocalDensity.current
    val focus = LocalFocusManager.current
    var text by rememberSaveable(page.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(page.text)) }
    val strokes = remember(page.id) { mutableStateListOf<InkStroke>().apply { addAll(InkIO.parse(page.strokes)) } }
    val undo = remember(page.id) { mutableStateListOf<List<InkStroke>>() }
    val redo = remember(page.id) { mutableStateListOf<List<InkStroke>>() }
    val live = remember(page.id) { ArrayList<Float>() }
    var liveVer by remember(page.id) { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    val ink = Color(InkInt)

    LaunchedEffect(tools.mode) { if (tools.mode != 0) focus.clearFocus() }
    LaunchedEffect(page.id) {
        snapshotFlow { text.text to strokes.toList() }.drop(1).debounce(500).collect {
            vm.savePage(page.id, nb.id, it.first, InkIO.write(it.second))
        }
    }
    DisposableEffect(page.id) {
        onDispose { vm.savePage(page.id, nb.id, text.text, InkIO.write(strokes.toList())) }
    }

    fun push() {
        undo.add(strokes.toList()); redo.clear()
        if (undo.size > 100) undo.removeAt(0)
    }
    fun doUndo() {
        if (undo.isEmpty()) return
        redo.add(strokes.toList())
        val s = undo.removeAt(undo.lastIndex)
        strokes.clear(); strokes.addAll(s)
    }
    fun doRedo() {
        if (redo.isEmpty()) return
        undo.add(strokes.toList())
        val s = redo.removeAt(redo.lastIndex)
        strokes.clear(); strokes.addAll(s)
    }

    BoxWithConstraints(modifier.background(Color(PaperInt))) {
        val boxH = maxHeight
        val spacingPx = with(dens) { 28.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) { drawPaper(nb.paper, spacingPx, Color(LineInt)) }

        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            BasicTextField(
                value = text, onValueChange = { text = it },
                textStyle = TextStyle(color = ink, fontFamily = tools.font, fontSize = 17.sp,
                    lineHeight = with(dens) { 28.dp.toSp() }),
                cursorBrush = SolidColor(ink),
                modifier = Modifier.fillMaxWidth().heightIn(min = boxH)
                    .padding(start = 26.dp, end = 18.dp, top = 22.dp, bottom = 44.dp)
            )
        }

        val inkOn = tools.mode != 0
        val gesture = if (!inkOn) Modifier else Modifier.pointerInput(tools.mode, tools.color, tools.wMul, tools.fingerDraw) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val stylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                if (!stylus && !tools.fingerDraw) return@awaitEachGesture
                val eraser = tools.mode == 3 || down.type == PointerType.Eraser
                val w = size.width.toFloat()
                val r = with(dens) { 14.dp.toPx() } / w
                var erased = false
                fun hit(p: Offset) {
                    val nx = p.x / w
                    val ny = p.y / w
                    val found = strokes.filter { s ->
                        var i = 0
                        var h = false
                        while (i < s.pts.size - 1) {
                            val dx = s.pts[i] - nx
                            val dy = s.pts[i + 1] - ny
                            if (dx * dx + dy * dy < r * r) { h = true; break }
                            i += 2
                        }
                        h
                    }
                    if (found.isNotEmpty()) {
                        if (!erased) { push(); erased = true }
                        strokes.removeAll(found)
                    }
                }
                down.consume()
                if (eraser) hit(down.position) else {
                    live.clear(); live.add(down.position.x / w); live.add(down.position.y / w); liveVer++
                }
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) { ev.changes.forEach { it.consume() }; break }
                    if (eraser) {
                        ch.historical.forEach { hit(it.position) }
                        hit(ch.position)
                    } else {
                        ch.historical.forEach { live.add(it.position.x / w); live.add(it.position.y / w) }
                        live.add(ch.position.x / w); live.add(ch.position.y / w); liveVer++
                    }
                    ev.changes.forEach { it.consume() }
                }
                if (!eraser && live.size >= 2) {
                    push()
                    val base = if (tools.mode == 2) 0.032f else 0.0045f
                    strokes.add(InkStroke(tools.color, base * tools.wMul, tools.mode == 2, live.toFloatArray()))
                    live.clear(); liveVer++
                }
            }
        }
        Canvas(Modifier.fillMaxSize().then(gesture)) {
            strokes.forEach { drawInk(it.pts, Color(it.color), it.width, it.hl, size.width) }
            if (liveVer >= 0 && live.size >= 2) {
                val base = if (tools.mode == 2) 0.032f else 0.0045f
                drawInk(live.toFloatArray(), Color(tools.color), base * tools.wMul, tools.mode == 2, size.width)
            }
        }

        if (inkOn) {
            Row(Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                TextButton({ doUndo() }, enabled = undo.isNotEmpty()) { Text("Undo") }
                TextButton({ doRedo() }, enabled = redo.isNotEmpty()) { Text("Redo") }
            }
        }
        Text("$number", Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            color = ink.copy(alpha = 0.45f), fontSize = 12.sp)
    }
}
