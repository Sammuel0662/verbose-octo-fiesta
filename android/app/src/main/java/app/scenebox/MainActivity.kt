package app.scenebox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.scenebox.community.PlazaRepo
import app.scenebox.core.Capability
import app.scenebox.core.CapabilityProbe
import app.scenebox.core.DeviceFingerprint
import app.scenebox.core.ShizukuBridge
import app.scenebox.sbox.ExportService
import app.scenebox.sbox.CleanupService
import app.scenebox.sbox.ImportService
import app.scenebox.core.SceneFiles
import app.scenebox.core.SnapshotService
import app.scenebox.core.PatchService
import app.scenebox.core.SystemReset
import app.scenebox.core.SuShell
import app.scenebox.sbox.SboxBundle
import app.scenebox.sbox.SboxStore
import app.scenebox.ui.GuideScreen
import app.scenebox.ui.HomeScreen
import app.scenebox.ui.PlazaScreen
import app.scenebox.ui.SceneBoxTheme
import app.scenebox.ui.StartupNoticeDialog
import app.scenebox.ui.PreviewScreen
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SceneBoxTheme {
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
    var exports by remember { mutableStateOf(listOf<File>()) }
    var downloads by remember { mutableStateOf(listOf<File>()) }
    var opened by remember { mutableStateOf<Pair<String, SboxBundle>?>(null) }
    var snapshotMessage by remember { mutableStateOf("") }
    var importBusy by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf("") }
    var cleanupMessage by remember { mutableStateOf("") }
    var importOk by remember { mutableStateOf<ImportService.Result?>(null) }
    var importOkName by remember { mutableStateOf("") }
    var showGuide by remember { mutableStateOf(false) }
    var showPlaza by remember { mutableStateOf(false) }
    var plazaIndex by remember { mutableStateOf<PlazaRepo.Index?>(null) }
    var plazaLoading by remember { mutableStateOf(false) }
    var plazaMessage by remember { mutableStateOf("") }
    var plazaApplying by remember { mutableStateOf(false) }
    var noticeAccepted by remember { mutableStateOf(false) }

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
        exports = SboxStore.list(context)
        downloads = SboxStore.listDownloads(context)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val copied = SboxStore.copyIn(context, uri)
            refreshFiles()
            if (copied != null) {
                runCatching { SboxStore.load(copied) }.onSuccess { opened = copied.name to it }
            }
        }
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
        if (result.ok) {
            importOkName = bundle.meta.name
            importOk = result
        }
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
    val autoSystemReset = remember { activityIntent?.getBooleanExtra("system_reset", false) ?: false }
    val autoApplyFirst = remember { activityIntent?.getBooleanExtra("apply_first", false) ?: false }
    var autoApplied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        probe(prefs.getBoolean("try_root", false))
        refreshFiles()
    }

    // 测试/自动化入口：am start ... --ez export_now true
    // 自动化：--ez apply_first true 应用广场第一个方案（便于验证）
    // 依赖 capability + fingerprint + plazaIndex 三者都就绪，缺一个就直接返回，
    // 不提前置位 autoApplied，避免"第一条到达时抢跑、后面不再重试"。
    LaunchedEffect(autoApplyFirst, capability, fingerprint, plazaIndex) {
        if (!autoApplyFirst || autoApplied) return@LaunchedEffect
        if (capability?.canWriteSysfs != true) return@LaunchedEffect
        val fpNow = fingerprint ?: return@LaunchedEffect
        val index = plazaIndex
        if (index == null) {
            if (!plazaLoading) {
                plazaLoading = true
                val fetched = withContext(Dispatchers.IO) { runCatching { PlazaRepo.fetch() } }
                plazaLoading = false
                fetched.onSuccess { plazaIndex = it }
                    .onFailure { plazaMessage = "❌ ${it.message ?: "索引加载失败"}" }
            }
            return@LaunchedEffect
        }
        val item = index.categories.firstOrNull()?.items?.firstOrNull() ?: return@LaunchedEffect
        autoApplied = true
        plazaMessage = "正在应用 ${item.name}…"
        val result = withContext(Dispatchers.IO) { PatchService.apply(item.patch, fpNow) }
        plazaMessage = if (result.ok) "✅ ${result.message}" else "❌ ${result.message}"
    }

    LaunchedEffect(exports, downloads) {
        val name = autoOpen
        val all = exports + downloads
        if (!name.isNullOrBlank() && opened == null && all.isNotEmpty()) {
            val file = all.firstOrNull { it.name == name } ?: all.first()
            runCatching { SboxStore.load(file) }.onSuccess { opened = file.name to it }
        }
    }

    // 自动化入口：先 open_file 打开包，再 import_now 直接导入
    // 注意 key 必须带上 capability：能力探测晚于文件加载时，只监听 opened 会漏掉
    LaunchedEffect(opened, capability) {
        val current = opened ?: return@LaunchedEffect
        if (autoImport && importMessage.isBlank() && capability?.canApplyConfig == true) {
            runImport(current.second)
        }
    }

    LaunchedEffect(capability) {
        val cap = capability ?: return@LaunchedEffect
        if (autoExport && cap.canExportScene && exportMessage.isBlank()) runExport()
        if (autoSystemReset && cap.canSnapshotRestore && cleanupMessage.isBlank()) {
            val fpNow = fingerprint
            if (fpNow != null) {
                cleanupMessage = withContext(Dispatchers.IO) { SystemReset.apply(fpNow).message }
            }
        }
        if (!autoSnapSave.isNullOrBlank() && cap.canSnapshotRestore && snapshotMessage.isBlank()) {
            runSnapshot(true, autoSnapSave)
        }
        if (!autoSnapRestore.isNullOrBlank() && cap.canSnapshotRestore && snapshotMessage.isBlank()) {
            runSnapshot(false, autoSnapRestore)
        }
    }

    if (!noticeAccepted) {
        StartupNoticeDialog(onAccept = { noticeAccepted = true })
    }

    if (showGuide) {
        GuideScreen(onBack = { showGuide = false })
        return
    }

    if (showPlaza) {
        PlazaScreen(
            index = plazaIndex,
            loading = plazaLoading,
            message = plazaMessage,
            onRefresh = {
                plazaLoading = true
                plazaMessage = ""
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { PlazaRepo.fetch() }
                    }
                    plazaLoading = false
                    result.onSuccess { plazaIndex = it }
                        .onFailure { plazaMessage = "❌ 加载失败：${it.message ?: "未知错误"}" }
                }
            },
            applying = plazaApplying,
            canApply = capability?.canWriteSysfs == true,
            onApply = { item ->
                val fpNow = fingerprint
                plazaApplying = true
                plazaMessage = "正在应用 ${item.name}…"
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        if (fpNow == null) PatchService.Result(false, "缺少本机频点表")
                        else PatchService.apply(item.patch, fpNow)
                    }
                    plazaApplying = false
                    plazaMessage = if (result.ok) "✅ ${result.message}" else "❌ ${result.message}"
                }
            },
            onDownload = { item ->
                plazaMessage = "正在下载 ${item.name}…"
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { PlazaRepo.download(context, item) }
                    }
                    result.onSuccess { file ->
                        refreshFiles()
                        plazaMessage = "✅ 已下载，正在打开预览"
                        runCatching { SboxStore.load(file) }.onSuccess {
                            opened = file.name to it
                            showPlaza = false
                        }
                    }.onFailure { plazaMessage = "❌ 下载失败：${it.message}" }
                }
            },
            onOpenGuide = { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
            onBack = { showPlaza = false },
        )
        return
    }

    LaunchedEffect(showPlaza) {
        if (showPlaza && plazaIndex == null && !plazaLoading) {
            plazaLoading = true
            val result = withContext(Dispatchers.IO) { runCatching { PlazaRepo.fetch() } }
            plazaLoading = false
            result.onSuccess { plazaIndex = it }.onFailure { plazaMessage = "❌ 加载失败：${it.message ?: "未知错误"}" }
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
        onOpenGuide = { showGuide = true },
        onOpenPlaza = {
            showPlaza = true
            if (plazaIndex == null && !plazaLoading) {
                plazaLoading = true
                plazaMessage = ""
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { PlazaRepo.fetch() } }
                    plazaLoading = false
                    result.onSuccess { plazaIndex = it }
                        .onFailure { plazaMessage = "❌ 加载失败：${it.message ?: "未知错误"}" }
                }
            }
        },
        cleanupMessage = cleanupMessage,
        onCleanup = { action ->
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    when (action) {
                        "downloads" -> CleanupService.clearDownloads(context)
                        "snapshots" -> CleanupService.clearSnapshots(context)
                        "system" -> {
                            val fpNow = fingerprint
                            if (fpNow == null) {
                                CleanupService.Result(false, "缺少本机指纹，无法还原")
                            } else {
                                val r = SystemReset.apply(fpNow)
                                CleanupService.Result(r.ok, r.message)
                            }
                        }
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
        exports = exports,
        downloads = downloads,
        onImportFile = { filePicker.launch(arrayOf("*/*")) },
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
            importedOk = importOk != null,
            importBackup = importOk?.backupName ?: "",
            importSize = importOk?.sizeText ?: "",
            onRollback = {
                importOk = null
                opened = null
                scope.launch {
                    val r = withContext(Dispatchers.IO) { CleanupService.restoreLatestBackup() }
                    cleanupMessage = r.message
                }
            },
            onBack = {
                opened = null
                importMessage = ""
            },
            onImport = { scope.launch { runImport(current.second) } },
        )
    }
}
