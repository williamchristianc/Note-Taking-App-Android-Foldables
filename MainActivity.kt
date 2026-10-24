package app.folio

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FolioApp() }
    }
}

@Composable
fun FolioTheme(dark: Boolean, content: @Composable () -> Unit) {
    val cs = if (dark) darkColorScheme(
        primary = Color(0xFFE0B58A), background = Color(0xFF17140F), surface = Color(0xFF1F1B15),
        onBackground = Color(0xFFEDE4D6), onSurface = Color(0xFFEDE4D6), surfaceVariant = Color(0xFF2A241C),
        secondaryContainer = Color(0xFF3A3024)
    ) else lightColorScheme(
        primary = Color(0xFF7A4A2B), background = Color(0xFFF4EEE3), surface = Color(0xFFFAF6EE),
        onBackground = Color(0xFF2A2118), onSurface = Color(0xFF2A2118), surfaceVariant = Color(0xFFEAE1D1),
        secondaryContainer = Color(0xFFE6D5BC)
    )
    MaterialTheme(colorScheme = cs, content = content)
}

@Composable
fun FolioApp(vm: FolioVM = viewModel()) {
    val loaded by vm.settings.collectAsState()
    val st = loaded
    if (st == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val dark = when (st.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val act = LocalContext.current as Activity

    LaunchedEffect(st.immersive) {
        val c = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (st.immersive) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    var screen by rememberSaveable { mutableStateOf("library") }
    var nbId by rememberSaveable { mutableLongStateOf(0L) }
    var startPage by rememberSaveable { mutableIntStateOf(-1) }
    var editId by rememberSaveable { mutableLongStateOf(0L) }

    BackHandler(enabled = st.onboarded && screen != "library") { screen = "library" }

    FolioTheme(dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!st.onboarded) {
                WelcomeScreen {
                    vm.setPref(K.onboarded, true)
                    editId = 0L
                    screen = "edit"
                }
            } else when (screen) {
                "edit" -> EditScreen(vm, editId, onClose = { screen = "library" }, onCreated = { id ->
                    nbId = id; startPage = -1; vm.setPref(K.last, id); screen = "notebook"
                })
                "notebook" -> key(nbId) { NotebookScreen(vm, st, nbId, startPage, onBack = { screen = "library" }) }
                "search" -> SearchScreen(vm, onOpen = { id, p ->
                    nbId = id; startPage = p; vm.setPref(K.last, id); screen = "notebook"
                }, onBack = { screen = "library" })
                "settings" -> SettingsScreen(vm, st, onBack = { screen = "library" })
                else -> LibraryScreen(
                    vm, st,
                    onOpen = { id -> nbId = id; startPage = -1; vm.setPref(K.last, id); screen = "notebook" },
                    onNew = { editId = 0L; screen = "edit" },
                    onEdit = { id -> editId = id; screen = "edit" },
                    onSearch = { screen = "search" },
                    onSettings = { screen = "settings" }
                )
            }
        }
    }
}
