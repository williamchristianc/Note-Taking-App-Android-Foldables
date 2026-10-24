@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.folio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

private fun fmt(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

@Composable
private fun MI(label: String, dismiss: () -> Unit, f: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = { dismiss(); f() })
}

@Composable
private fun NbMenu(
    nb: Notebook, idx: Int, list: List<Notebook>, manual: Boolean, vm: FolioVM,
    expanded: Boolean, dismiss: () -> Unit, onOpen: () -> Unit, onEdit: () -> Unit
) {
    DropdownMenu(expanded, dismiss) {
        when (nb.status) {
            0 -> {
                MI("Open", dismiss, onOpen)
                MI("Edit cover & details", dismiss, onEdit)
                MI(if (nb.pinned) "Unpin" else "Pin to top", dismiss) { vm.update(nb.copy(pinned = !nb.pinned)) }
                MI("Duplicate", dismiss) { vm.duplicate(nb) }
                if (manual && idx > 0) MI("Move earlier", dismiss) { vm.swapOrder(nb, list[idx - 1]) }
                if (manual && idx < list.size - 1) MI("Move later", dismiss) { vm.swapOrder(nb, list[idx + 1]) }
                MI("Archive", dismiss) { vm.setStatus(nb, 1) }
                MI("Move to trash", dismiss) { vm.setStatus(nb, 2) }
            }
            1 -> {
                MI("Restore to library", dismiss) { vm.setStatus(nb, 0) }
                MI("Move to trash", dismiss) { vm.setStatus(nb, 2) }
            }
            else -> {
                MI("Restore", dismiss) { vm.setStatus(nb, 0) }
                MI("Delete forever", dismiss) { vm.deleteForever(nb) }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    vm: FolioVM, st: AppSettings,
    onOpen: (Long) -> Unit, onNew: () -> Unit, onEdit: (Long) -> Unit,
    onSearch: () -> Unit, onSettings: () -> Unit
) {
    val all by vm.notebooks.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    val cmp: Comparator<Notebook> = when (st.sort) {
        1 -> compareBy<Notebook> { it.title.lowercase() }
        2 -> compareByDescending<Notebook> { it.updatedAt }
        3 -> compareByDescending<Notebook> { it.createdAt }
        else -> compareBy<Notebook> { it.sortOrder }
    }
    val list = all.filter { it.status == tab }.sortedWith(compareByDescending<Notebook> { it.pinned }.then(cmp))
    val last = all.firstOrNull { it.id == st.last && it.status == 0 }
    val manual = st.sort == 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folio", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onSearch) { Icon(Icons.Default.Search, "Search") }
                    Box {
                        TextButton({ sortMenu = true }) { Text("Sort") }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            listOf("Manual order", "Title", "Last activity", "Date created").forEachIndexed { i, l ->
                                DropdownMenuItem(
                                    text = { Text(if (st.sort == i) "✓ $l" else l) },
                                    onClick = { sortMenu = false; vm.setPref(K.sort, i) })
                            }
                        }
                    }
                    TextButton({ vm.setPref(K.grid, !st.grid) }) { Text(if (st.grid) "List" else "Gallery") }
                    IconButton(onSettings) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        },
        floatingActionButton = {
            if (tab == 0) ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New notebook") })
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Library", "Archive", "Trash").forEachIndexed { i, l ->
                    FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(l) })
                }
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        when (tab) { 0 -> "No notebooks yet.\nTap “New notebook” to create one."; 1 -> "Nothing archived."; else -> "Trash is empty." },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            } else if (st.grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (tab == 0 && last != null) item(span = { GridItemSpan(maxLineSpan) }) { ContinueCard(last) { onOpen(last.id) } }
                    items(list, key = { it.id }) { nb ->
                        val idx = list.indexOf(nb)
                        var menu by remember { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth()) {
                            CoverArt(
                                nb,
                                Modifier.fillMaxWidth().aspectRatio(0.72f).combinedClickable(
                                    onClickLabel = "Open ${nb.title}", onClick = { onOpen(nb.id) }, onLongClick = { menu = true })
                            )
                            Box(Modifier.fillMaxWidth().height(6.dp).background(
                                Brush.verticalGradient(listOf(Color(0xFF8B6B4A), Color(0xFF5E4630)))))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                                    Text((if (nb.pinned) "★ " else "") + nb.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium)
                                    Text(fmt(nb.updatedAt), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                Box {
                                    IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Notebook options") }
                                    NbMenu(nb, idx, list, manual, vm, menu, { menu = false }, { onOpen(nb.id) }, { onEdit(nb.id) })
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp)) {
                    if (tab == 0 && last != null) item { ContinueCard(last) { onOpen(last.id) }; Spacer(Modifier.height(8.dp)) }
                    items(list, key = { it.id }) { nb ->
                        val idx = list.indexOf(nb)
                        var menu by remember { mutableStateOf(false) }
                        Row(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClickLabel = "Open ${nb.title}", onClick = { onOpen(nb.id) }, onLongClick = { menu = true })
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverArt(nb, Modifier.width(56.dp).height(78.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text((if (nb.pinned) "★ " else "") + nb.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium)
                                Text("${Paper.names[nb.paper]} • ${fmt(nb.updatedAt)}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Box {
                                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Notebook options") }
                                NbMenu(nb, idx, list, manual, vm, menu, { menu = false }, { onOpen(nb.id) }, { onEdit(nb.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(nb: Notebook, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(nb, Modifier.width(48.dp).height(66.dp))
            Column(Modifier.padding(start = 14.dp)) {
                Text("Continue writing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("${nb.title} • page ${nb.lastPage + 1}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
