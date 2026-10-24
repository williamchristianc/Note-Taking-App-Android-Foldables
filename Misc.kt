@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.folio

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun toast(c: Context, m: String) { Toast.makeText(c, m, Toast.LENGTH_SHORT).show() }

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CoverArt(Notebook(title = "Folio", material = 0, color = Swatches[0]), Modifier.fillMaxWidth(0.5f).widthIn(max = 260.dp).aspectRatio(0.72f))
        Spacer(Modifier.height(28.dp))
        Text("Your notebook, always with you.", style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Real-feeling covers, typed pages and handwriting. Everything stays on your device: no account, no cloud.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.height(28.dp))
        Button(onStart) { Text("Create my first notebook") }
    }
}

private fun snippet(t: String, q: String): String {
    val i = t.indexOf(q, ignoreCase = true)
    if (i < 0) return t.take(100)
    return t.substring(maxOf(0, i - 30), minOf(t.length, i + 90)).replace('\n', ' ')
}

@Composable
fun SearchScreen(vm: FolioVM, onOpen: (Long, Int) -> Unit, onBack: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var res by remember { mutableStateOf<Pair<List<Notebook>, List<Page>>>(emptyList<Notebook>() to emptyList()) }
    val all by vm.notebooks.collectAsState()
    LaunchedEffect(q) {
        if (q.isBlank()) res = emptyList<Notebook>() to emptyList()
        else { delay(200); res = vm.search(q.trim()) }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = {
                TextField(q, { q = it }, placeholder = { Text("Search notebooks and pages") }, singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth())
            },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(res.first, key = { "n${it.id}" }) { n ->
                ListItem(headlineContent = { Text(n.title) }, supportingContent = { Text("Notebook") },
                    modifier = Modifier.clickable { onOpen(n.id, -1) })
            }
            items(res.second, key = { "p${it.id}" }) { p ->
                val n = all.firstOrNull { it.id == p.notebookId }
                ListItem(headlineContent = { Text(n?.title ?: "Notebook") },
                    supportingContent = { Text("Page ${p.position + 1}: " + snippet(p.text, q.trim()), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.clickable { onOpen(p.notebookId, p.position) })
            }
            if (q.isNotBlank() && res.first.isEmpty() && res.second.isEmpty()) {
                item { Text("No results.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChange)
    }
}

@Composable
fun SettingsScreen(vm: FolioVM, st: AppSettings, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val backupL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { toast(ctx, if (vm.backup(uri)) "Backup saved" else "Backup failed") }
    }
    val restoreL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, l ->
                    FilterChip(selected = st.theme == i, onClick = { vm.setPref(K.theme, i) }, label = { Text(l) })
                }
            }
            Text("Page font", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FontNames.forEachIndexed { i, l ->
                    FilterChip(selected = st.font == i, onClick = { vm.setPref(K.font, i) }, label = { Text(l) })
                }
            }
            SwitchRow("Immersive mode (hide system bars)", st.immersive) { vm.setPref(K.immersive, it) }
            SwitchRow("Reduce motion", st.reduce) { vm.setPref(K.reduce, it) }
            SwitchRow("Haptic feedback", st.haptics) { vm.setPref(K.haptics, it) }
            SwitchRow("Draw with finger (off = stylus only)", st.finger) { vm.setPref(K.finger, it) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Backup & restore", style = MaterialTheme.typography.titleMedium)
            Text("A backup is one file with all notebooks, pages, handwriting and cover images. Save it anywhere using the system file picker.",
                style = MaterialTheme.typography.bodySmall)
            Button({
                backupL.launch("folio-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json")
            }) { Text("Create backup") }
            OutlinedButton({ restoreL.launch(arrayOf("*/*")) }) { Text("Restore from backup") }
            Text("PDF and Markdown export are in each notebook’s ⋮ menu.", style = MaterialTheme.typography.bodySmall)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text("Folio 0.1.0. Works fully offline. No account, no analytics, no cloud sync.", style = MaterialTheme.typography.bodySmall)
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = { Text("This replaces all current notebooks with the contents of the backup.") },
            confirmButton = {
                TextButton({
                    pendingRestore = null
                    scope.launch { toast(ctx, if (vm.restore(uri)) "Backup restored" else "Restore failed: not a valid Folio backup") }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton({ pendingRestore = null }) { Text("Cancel") } }
        )
    }
}
