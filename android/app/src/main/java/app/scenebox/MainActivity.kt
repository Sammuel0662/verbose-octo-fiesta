package app.scenebox

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.scenebox.core.Capability
import app.scenebox.core.CapabilityProbe
import app.scenebox.core.DeviceFingerprint
import app.scenebox.core.ShizukuBridge
import app.scenebox.sbox.ExportService
import app.scenebox.sbox.CleanupService
import app.scenebox.sbox.ImportService
import app.scenebox.core.SceneFiles
import app.scenebox.core.SnapshotService
import app.scenebox.core.SuShell
import app.scenebox.sbox.SboxBundle
import app.scenebox.sbox.SboxStore
import app.scenebox.ui.GuideScreen
import app.scenebox.ui.HomeScreen
import app.scenebox.ui.PreviewScreen
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Root()
                }
            }
        }
    }
}

@Composable
private fun Root() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("scenebox", Context.MODE_PRIVATE) }

    var capability by remember { mutableStateOf<Capability?>(null) }
    var fingerprint by remember { mutableStateOf<DeviceFingerprint?>(null) }
    var busy by remember { mutableStateOf(true) }
    // 记住上次是否做过 root 探测：已授权的用户重启 App 后不必再点一次
    var lastTryRoot by remember { mutableStateOf(prefs.getBoolean("try_root", false)) }
    var exportBusy by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf("") }
    var files by remember { mutableStateOf(listOf<File>()) }
    var opened by remember { mutableStateOf<Pair<String, SboxBundle>?>(null) }
    var snapshotMessage by remember { mutableStateOf("") }
    var importBusy by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf("") }
    var cleanupMessage by remember { mutableStateOf("") }
    var showGuide by remember { mutableStateOf(false) }
    var noticeDismissed by remember { mutableStateOf(prefs.getBoolean("notice_v030", false)) }

    fun probe(tryRoot: Boolean) {
        lastTryRoot = tryRoot
        prefs.edit().putBoolean("try_root", tryRoot).apply()
        busy = true
        scope.launch {
            val fp = withContext(Dispatchers.IO) { DeviceFingerprint.collect() }
            val cap = withContext(Dispatchers.IO) { CapabilityProbe.probe(context, tryRoot) }
            fingerprint = fp
            capability = cap
            busy = false
        }
    }

    fun refreshFiles() {
        files = SboxStore.list(context)
    }

    suspend fun runSnapshot(save: Boolean, label: String) {
        val capNow = capability ?: return
        if (!capNow.canSnapshotRestore) return
        val profile = withContext(Dispatchers.IO) { SceneFiles.profile(SuShell) }
        val result = withContext(Dispatchers.IO) {
            if (save) SnapshotService.save(context, SuShell, label, profile)
            else SnapshotService.restore(context, SuShell, label)
        }
        snapshotMessage = result.message
    }

    suspend fun runImport(bundle: SboxBundle) {
        val capNow = capability ?: return
        importBusy = true
        importMessage = ""
        val result = withContext(Dispatchers.IO) {
            ImportService.apply(bundle, fingerprint, capNow)
        }
        importBusy = false
        importMessage = result.message
    }

    suspend fun runExport() {
        val capNow = capability ?: return
        val fpNow = fingerprint ?: return
        if (!capNow.canExportScene) return
        exportBusy = true
        exportMessage = ""
        val result = withContext(Dispatchers.IO) {
            ExportService.export(
                context = context,
                fingerprint = fpNow,
                name = "本机 Scene 调度备份",
                game = "",
                style = "balance",
                description = "由 SceneBox 导出",
            )
        }
        exportBusy = false
        exportMessage = result.message
        refreshFiles()
    }

    val activityIntent = remember { (context as? android.app.Activity)?.intent }
    val autoExport = remember { activityIntent?.getBooleanExtra("export_now", false) ?: false }
    val autoSnapSave = remember { activityIntent?.getStringExtra("snap_save") }
    val autoSnapRestore = remember { activityIntent?.getStringExtra("snap_restore") }
    val autoOpen = remember { activityIntent?.getStringExtra("open_file") }
    val autoImport = remember { activityIntent?.getBooleanExtra("import_now", false) ?: false }

    LaunchedEffect(Unit) {
        probe(prefs.getBoolean("try_root", false))
        refreshFiles()
    }

    // 测试/自动化入口：am start ... --ez export_now true
    LaunchedEffect(files) {
        val name = autoOpen
        if (!name.isNullOrBlank() && opened == null && files.isNotEmpty()) {
            val file = files.firstOrNull { it.name == name } ?: files.first()
            runCatching { SboxStore.load(file) }.onSuccess { opened = file.name to it }
        }
    }

    // 自动化入口：先 open_file 打开包，再 import_now 直接导入
    LaunchedEffect(opened) {
        val current = opened ?: return@LaunchedEffect
        if (autoImport && importMessage.isBlank() && capability?.canApplyConfig == true) {
            runImport(current.second)
        }
    }

    LaunchedEffect(capability) {
        val cap = capability ?: return@LaunchedEffect
        if (autoExport && cap.canExportScene && exportMessage.isBlank()) runExport()
        if (!autoSnapSave.isNullOrBlank() && cap.canSnapshotRestore && snapshotMessage.isBlank()) {
            runSnapshot(true, autoSnapSave)
        }
        if (!autoSnapRestore.isNullOrBlank() && cap.canSnapshotRestore && snapshotMessage.isBlank()) {
            runSnapshot(false, autoSnapRestore)
        }
    }

    if (showGuide) {
        GuideScreen(onBack = { showGuide = false })
        return
    }

    val current = opened
    if (current == null) {
    HomeScreen(
        cap = capability,
        fp = fingerprint,
        busy = busy,
        exportBusy = exportBusy,
        exportMessage = exportMessage,
        onExport = { scope.launch { runExport() } },
        onRequestShizuku = {
            ShizukuBridge.request { granted ->
                if (granted) {
                    scope.launch { probe(lastTryRoot) }
                }
            }
        },
        onProbeRoot = { probe(true) },
        onRefresh = { probe(lastTryRoot) },
        noticeDismissed = noticeDismissed,
        onDismissNotice = {
            noticeDismissed = true
            prefs.edit().putBoolean("notice_v030", true).apply()
        },
        onOpenGuide = { showGuide = true },
        cleanupMessage = cleanupMessage,
        onCleanup = { action ->
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    when (action) {
                        "exports" -> CleanupService.clearExports(context)
                        "snapshots" -> CleanupService.clearSnapshots(context)
                        else -> CleanupService.restoreLatestBackup()
                    }
                }
                cleanupMessage = result.message
                refreshFiles()
            }
        },
        snapshotMessage = snapshotMessage,
        onSnapshotSave = { label -> scope.launch { runSnapshot(true, label) } },
        onSnapshotRestore = { label -> scope.launch { runSnapshot(false, label) } },
        files = files,
        onOpenFile = { file ->
            runCatching { SboxStore.load(file) }.onSuccess { bundle ->
                opened = file.name to bundle
            }
        },
    )
    } else {
        PreviewScreen(
            bundle = current.second,
            fingerprint = fingerprint,
            fileName = current.first,
            canImport = capability?.canApplyConfig == true,
            importBusy = importBusy,
            importMessage = importMessage,
            onBack = {
                opened = null
                importMessage = ""
            },
            onImport = { scope.launch { runImport(current.second) } },
        )
    }
}
