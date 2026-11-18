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
import app.scenebox.sbox.SboxBundle
import app.scenebox.sbox.SboxStore
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

    val autoExport = remember {
        (context as? android.app.Activity)?.intent?.getBooleanExtra("export_now", false) ?: false
    }

    LaunchedEffect(Unit) {
        probe(prefs.getBoolean("try_root", false))
        refreshFiles()
    }

    // 测试/自动化入口：am start ... --ez export_now true
    LaunchedEffect(capability) {
        if (autoExport && capability?.canExportScene == true && exportMessage.isBlank()) {
            runExport()
        }
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
            onBack = { opened = null },
        )
    }
}
