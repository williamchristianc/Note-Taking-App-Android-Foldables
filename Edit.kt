@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.folio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun EditScreen(vm: FolioVM, id: Long, onClose: () -> Unit, onCreated: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val existing = remember(id) { vm.notebooks.value.firstOrNull { it.id == id } }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var material by rememberSaveable { mutableIntStateOf(existing?.material ?: 0) }
    var color by rememberSaveable { mutableIntStateOf(existing?.color ?: Swatches[0]) }
    var paper by rememberSaveable { mutableIntStateOf(existing?.paper ?: 1) }
    var tfont by rememberSaveable { mutableIntStateOf(existing?.titleFont ?: 0) }
    var band by rememberSaveable { mutableStateOf(existing?.band ?: true) }
    var ribbon by rememberSaveable { mutableStateOf(existing?.ribbon ?: true) }
    var image by rememberSaveable { mutableStateOf(existing?.coverImage) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { vm.importCover(uri)?.let { image = it } }
    }

    val preview = Notebook(
        id = id, title = title.ifBlank { "Untitled" }, material = material, color = color, paper = paper,
        titleFont = tfont, band = band, ribbon = ribbon, coverImage = image
    )

    fun save() {
        val t = title.trim().ifBlank { "Untitled" }
        if (existing != null) {
            vm.update(existing.copy(title = t, material = material, color = color, paper = paper,
                titleFont = tfont, band = band, ribbon = ribbon, coverImage = image))
            onClose()
        } else {
            vm.create(Notebook(title = t, material = material, color = color, paper = paper,
                titleFont = tfont, band = band, ribbon = ribbon, coverImage = image)) { onCreated(it) }
        }
    }

    @Composable
    fun Options() {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Cover material", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Materials.forEachIndexed { i, m -> FilterChip(selected = material == i, onClick = { material = i }, label = { Text(m) }) }
            }
            Text("Cover colour", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Swatches.forEach { c ->
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                        .border(if (color == c) 3.dp else 1.dp,
                            if (color == c) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f), CircleShape)
                        .clickable(onClickLabel = "Select colour") { color = c })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ picker.launch("image/*") }) { Text("Custom cover image") }
                if (image != null) TextButton({ image = null }) { Text("Remove image") }
            }
            Text("Title typeface", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TitleFonts.forEachIndexed { i, f -> FilterChip(selected = tfont == i, onClick = { tfont = i }, label = { Text(f) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Elastic band", Modifier.weight(1f)); Switch(band, { band = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bookmark ribbon", Modifier.weight(1f)); Switch(ribbon, { ribbon = it })
            }
            Text("Paper", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Paper.names.forEachIndexed { i, n -> FilterChip(selected = paper == i, onClick = { paper = i }, label = { Text(n) }) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) "New notebook" else "Edit notebook") },
                navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton({ save() }) { Text("Save") } }
            )
        }
    ) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
            if (maxWidth >= 640.dp) {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        CoverArt(preview, Modifier.fillMaxHeight(0.9f).aspectRatio(0.72f))
                    }
                    Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState())) { Options() }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CoverArt(preview, Modifier.fillMaxWidth(0.5f).aspectRatio(0.72f))
                    Options()
                }
            }
        }
    }
}
