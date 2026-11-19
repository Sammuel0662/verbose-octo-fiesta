package app.scenebox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
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
import app.scenebox.core.ActionLog
import app.scenebox.core.CapabilityProbe
import app.scenebox.core.DeviceInfoFull
import app.scenebox.core.Diagnostics
import app.scenebox.core.DisplayControl
import app.scenebox.core.AppGovernance
import app.scenebox.core.AutoSwitch
import app.scenebox.core.RecipeStore
import app.scenebox.core.SampleStore
import app.scenebox.hud.HudService
import app.scenebox.core.DeviceFingerprint
import app.scenebox.core.ShizukuBridge
import app.scenebox.sbox.ExportService
import app.scenebox.sbox.CleanupService
import app.scenebox.sbox.ImportService
import app.scenebox.core.PatchTextParser
import app.scenebox.core.SceneFiles
import app.scenebox.core.SnapshotService
import app.scenebox.core.FpsSampler
import app.scenebox.core.LiveStatus
import app.scenebox.core.NodeBrowser
import app.scenebox.core.PerfMonitor
import app.scenebox.core.SceneState
import app.scenebox.core.SysfsApply
import app.scenebox.core.ProfileService
import app.scenebox.core.SystemReset
import app.scenebox.core.SuShell
import app.scenebox.sbox.SboxBundle
import app.scenebox.sbox.SboxStore
import app.scenebox.ui.GuideScreen
import app.scenebox.ui.HomeScreen
import app.scenebox.ui.MainTabs
import app.scenebox.ui.MonitorScreen
import app.scenebox.ui.AppsScreen
import app.scenebox.ui.NodeScreen
import app.scenebox.ui.ScheduleEditorScreen
import app.scenebox.ui.SettingsScreen
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
        // 边到边：让内容延伸到状态栏/手势条后面，底部不再留一条黑框
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.parseColor("#0B0F12")
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            // 系统默认会在导航栏后面画一层无对比度遮罩，看起来就是"黑框"
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
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
    var liveStatus by remember { mutableStateOf("") }
    var sampling by remember { mutableStateOf(false) }
    var sampleCount by remember { mutableStateOf(0) }
    var sampleFps by remember { mutableStateOf(listOf<Int>()) }
    var sampleSummary by remember { mutableStateOf("") }
    var sampleTemps by remember { mutableStateOf(listOf<Float>()) }
    var customText by remember { mutableStateOf("") }
    var customBusy by remember { mutableStateOf(false) }
    var customMessage by remember { mutableStateOf("") }
    var deviceInfo by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var checks by remember { mutableStateOf(listOf<Diagnostics.Check>()) }
    var diagBusy by remember { mutableStateOf(false) }
    var logLines by remember { mutableStateOf(listOf<String>()) }
    var logVersion by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(0) }
    var hzModes by remember { mutableStateOf(listOf<Int>()) }
    var hzMessage by remember { mutableStateOf("") }
    var nodes by remember { mutableStateOf(listOf<NodeBrowser.Node>()) }
    var nodeLoading by remember { mutableStateOf(false) }
    var nodeMessage by remember { mutableStateOf("") }
    var showSchedule by remember { mutableStateOf(false) }
    var scheduleMessage by remember { mutableStateOf("") }
    var scheduleBusy by remember { mutableStateOf(false) }
    var governors by remember { mutableStateOf(listOf<String>()) }
    var currentGovernor by remember { mutableStateOf("") }
    var hudOn by remember { mutableStateOf(false) }
    var hudMessage by remember { mutableStateOf("") }
    var autoOn by remember { mutableStateOf(false) }
    var autoRules by remember { mutableStateOf("") }
    var recipes by remember { mutableStateOf(listOf<RecipeStore.Recipe>()) }
    var showApps by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(listOf<String>()) }
    var appsLoading by remember { mutableStateOf(false) }
    var appsMessage by remember { mutableStateOf("") }
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
        ActionLog.append(context, if (save) "快照保存" else "快照恢复", "$label → ${result.message.take(50)}")
        logVersion++
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
    val autoCustom = remember { activityIntent?.getStringExtra("custom_patch") }
    val autoApplyFirst = remember { activityIntent?.getBooleanExtra("apply_first", false) ?: false }
    var autoApplied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        probe(prefs.getBoolean("try_root", false))
        refreshFiles()
    }

    // 测试/自动化入口：am start ... --ez export_now true
    LaunchedEffect(autoCustom, capability, fingerprint) {
        if (!autoCustom.isNullOrBlank() && !autoApplied && capability?.canWriteSysfs == true) {
            autoApplied = true
            customText = autoCustom
            customBusy = true
            customMessage = withContext(Dispatchers.IO) { deployCustomPatch(autoCustom, fingerprint) }
            customBusy = false
        }
    }

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
        plazaMessage = withContext(Dispatchers.IO) {
            if (SceneState.detect().willApply) {
                val raw = SceneFiles.profile(SuShell)
                if (raw == null) "❌ 读不到 Scene 的 profile（需要 root）"
                else {
                    val patched = ProfileService.patch(raw, item.patch, fpNow)
                    val written = ImportService.writeProfile(patched.profile)
                    if (!written.ok) "❌ ${written.message}"
                    else "✅ 已写入 Scene 方案：${patched.changes.take(3).joinToString("、")}"
                }
            } else {
                val r = SysfsApply.apply(item.patch, fpNow)
                if (r.ok) "✅ ${r.message}" else "❌ ${r.message}"
            }
        }
    }

    fun startSampling() {
        sampling = true
        sampleCount = 0
        sampleFps = emptyList()
        sampleSummary = ""
        scope.launch {
            val pkg = withContext(Dispatchers.IO) { FpsSampler.foregroundPackage() }
            if (pkg == null) {
                sampling = false
                sampleSummary = "读不到前台应用，无法采样"
                return@launch
            }
            val fpsList = mutableListOf<Int>()
            val tempList = mutableListOf<Float>()
            var lastFrames = withContext(Dispatchers.IO) { FpsSampler.totalFrames(pkg) }
            var lastAt = System.currentTimeMillis()
            repeat(30) { index ->
                kotlinx.coroutines.delay(1000)
                val now = System.currentTimeMillis()
                val frames = withContext(Dispatchers.IO) { FpsSampler.totalFrames(pkg) }
                val temp = FpsSampler.temperature(context)
                val fps = if (lastFrames >= 0 && frames >= lastFrames) {
                    (frames - lastFrames).toInt()
                } else 0
                lastFrames = frames
                lastAt = now
                fpsList += fps
                tempList += temp
                sampleFps = fpsList.toList()
                sampleCount = index + 1
            }
            sampling = false
            val valid = fpsList.filter { it in 1..240 }
            val lowFps = withContext(Dispatchers.IO) { FpsSampler.onePercentLow(pkg) }
            val stats = withContext(Dispatchers.IO) { FpsSampler.frameStats(pkg) }
            val governor = withContext(Dispatchers.IO) {
                SuShell.exec("cat /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null", 8L)
                    .text().trim()
            }
            val maxFreqs = withContext(Dispatchers.IO) {
                SuShell.exec(
                    "for p in 0 2 5 7; do cat /sys/devices/system/cpu/cpufreq/policy\$p/scaling_max_freq 2>/dev/null; done",
                    8L,
                ).text().trim().lines().joinToString("/")
            }
            val record = SampleStore.Record(
                id = "s" + System.currentTimeMillis(),
                time = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
                    .format(java.util.Date()),
                pkg = pkg,
                avgFps = if (valid.isEmpty()) 0 else valid.average().toInt(),
                lowFps = lowFps,
                jankPercent = stats?.jankPercent ?: 0f,
                maxTemp = tempList.maxOrNull() ?: 0f,
                minTemp = tempList.minOrNull() ?: 0f,
                governor = governor,
                maxFreqs = maxFreqs,
            )
            withContext(Dispatchers.IO) { SampleStore.save(context, record) }
            val compare = withContext(Dispatchers.IO) { SampleStore.compareWithPrevious(context, record) }
            sampleSummary = buildString {
                append("前台 $pkg")
                append("\n帧率 ")
                append(
                    if (valid.isEmpty()) "无有效样本（可能画面静止）"
                    else "均值 ${record.avgFps} / 峰值 ${valid.maxOrNull()} / 最低 ${valid.minOrNull()} FPS"
                )
                if (lowFps > 0) append("\n1% low $lowFps FPS")
                if (stats != null) {
                    append("\njank ${stats.jankPercent}%  帧耗时 P50 ${stats.p50}ms / P90 ${stats.p90}ms")
                }
                if (tempList.isNotEmpty()) {
                    append("\n温度 ${tempList.first()}℃ → ${tempList.last()}℃，最高 ${record.maxTemp}℃")
                }
                append("\n方案 governor=$governor 上限=$maxFreqs")
                append("\n\n$compare")
            }
            ActionLog.append(context, "采样", "$pkg 均值${record.avgFps} 1%low$lowFps 最高温${record.maxTemp}℃")
            logVersion++
        }
    }

    // 实时状态：每 3 秒刷新一次频率/温度，便于观察应用方案前后的变化
    LaunchedEffect(logVersion) {
        logLines = withContext(Dispatchers.IO) { ActionLog.recent(context, 12) }
    }

    LaunchedEffect(Unit) {
        autoOn = AutoSwitch.enabled(context)
        autoRules = AutoSwitch.rulesText(context)
        recipes = withContext(Dispatchers.IO) { RecipeStore.list(context) }
        currentGovernor = withContext(Dispatchers.IO) {
            SuShell.exec("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null", 8L).text().trim()
        }
        governors = withContext(Dispatchers.IO) {
            SuShell.exec(
                "cat /sys/devices/system/cpu/cpufreq/policy0/scaling_available_governors 2>/dev/null",
                8L,
            ).text().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        }
        hzModes = DisplayControl.modes(context)
        val info = withContext(Dispatchers.IO) { DeviceInfoFull.collect(context) }
        deviceInfo = info.lines()
    }

    LaunchedEffect(fingerprint, tab) {
        if (tab != 2) return@LaunchedEffect
        val fpNow = fingerprint ?: return@LaunchedEffect
        while (true) {
            liveStatus = withContext(Dispatchers.IO) {
                LiveStatus.read(context, fpNow).text() + "\n" + PerfMonitor.read(fpNow)
            }
            kotlinx.coroutines.delay(3000)
        }
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

    BackHandler(
        enabled = showGuide || showSchedule || showPlaza || opened != null || tab != 0,
    ) {
        when {
            showGuide -> showGuide = false
            showSchedule -> showSchedule = false
            showPlaza -> showPlaza = false
            opened != null -> {
                opened = null
                importMessage = ""
            }
            tab != 0 -> tab = 0
        }
    }

    if (!noticeAccepted) {
        StartupNoticeDialog(onAccept = { noticeAccepted = true })
    }

    if (showGuide) {
        GuideScreen(onBack = { showGuide = false })
        return
    }

    if (showApps) {
        AppsScreen(
            apps = apps,
            loading = appsLoading,
            message = appsMessage,
            onRefresh = {
                appsLoading = true
                scope.launch {
                    apps = withContext(Dispatchers.IO) { AppGovernance.listUserApps().map { it.pkg } }
                    appsLoading = false
                    appsMessage = "已加载 ${apps.size} 个应用"
                }
            },
            onSuspend = { pkg ->
                scope.launch {
                    appsMessage = withContext(Dispatchers.IO) { AppGovernance.suspend(pkg) }
                    ActionLog.append(context, "治理", appsMessage)
                    logVersion++
                }
            },
            onUnsuspend = { pkg ->
                scope.launch {
                    appsMessage = withContext(Dispatchers.IO) { AppGovernance.unsuspend(pkg) }
                }
            },
            onStandby = { pkg, bucket ->
                scope.launch {
                    appsMessage = withContext(Dispatchers.IO) { AppGovernance.setStandby(pkg, bucket) }
                }
            },
            onBack = { showApps = false },
        )
        return
    }

    if (showSchedule) {
        ScheduleEditorScreen(
            policies = fingerprint?.policies ?: emptyList(),
            governors = governors,
            currentGovernor = currentGovernor,
            recipes = recipes,
            onSaveRecipe = { name, body ->
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { RecipeStore.save(context, name, body) }
                    recipes = withContext(Dispatchers.IO) { RecipeStore.list(context) }
                    scheduleMessage = if (ok) "已保存配方：$name" else "保存失败"
                    ActionLog.append(context, "配方", "保存 $name")
                    logVersion++
                }
            },
            onApplyRecipe = { name ->
                scope.launch {
                    val body = withContext(Dispatchers.IO) {
                        RecipeStore.list(context).firstOrNull { it.name == name }?.body
                    }
                    if (body != null) {
                        scheduleMessage = withContext(Dispatchers.IO) {
                            val fpNow = fingerprint
                            if (fpNow == null) "❌ 缺少频点表"
                            else deployPatchList(RecipeStore.decode(body), fpNow)
                        }
                    }
                }
            },
            onDeleteRecipe = { name ->
                scope.launch {
                    withContext(Dispatchers.IO) { RecipeStore.delete(context, name) }
                    recipes = withContext(Dispatchers.IO) { RecipeStore.list(context) }
                }
            },
            message = scheduleMessage,
            busy = scheduleBusy,
            onBack = { showSchedule = false },
            onApply = { patch, summary ->
                scheduleBusy = true
                scheduleMessage = "应用中…"
                scope.launch {
                    val fpNow = fingerprint
                    val result = withContext(Dispatchers.IO) {
                        if (fpNow == null) "❌ 缺少频点表"
                        else deployPatchList(patch, fpNow)
                    }
                    scheduleBusy = false
                    scheduleMessage = result
                    ActionLog.append(context, "逐项", "$summary → ${result.take(50)}")
                    logVersion++
                }
            },
        )
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
                    val message = withContext(Dispatchers.IO) {
                        if (fpNow == null) return@withContext "❌ 缺少本机频点表"
                        if (SceneState.detect().willApply) {
                            // Scene 会下发：写 profile 补丁，避免被它覆盖
                            val raw = SceneFiles.profile(SuShell)
                                ?: return@withContext "❌ 读不到 Scene 的 profile（需要 root）"
                            val patched = ProfileService.patch(raw, item.patch, fpNow)
                            if (patched.changes.isEmpty()) return@withContext "❌ 没有可应用的项"
                            val written = ImportService.writeProfile(patched.profile)
                            if (!written.ok) return@withContext "❌ ${written.message}"
                            "✅ 已写入 Scene 方案（Scene 会自动下发）：" +
                                "${patched.changes.take(3).joinToString("、")}" +
                                "\n原配置已备份：${written.backupName}"
                        } else {
                            // Scene 自动调度关着：直接写内核，写了就生效
                            val r = SysfsApply.apply(item.patch, fpNow)
                            if (!r.ok) "❌ ${r.message}"
                            else "✅ ${r.message}\n（Scene 自动调度未启用，已直接写内核）"
                        }
                    }
                    plazaApplying = false
                    plazaMessage = message
                    ActionLog.append(context, "广场", "${item.name} → ${message.take(50)}")
                    logVersion++
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
    MainTabs(
        tab = tab,
        onTabChange = { tab = it },
        home = {
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
        liveStatus = liveStatus,
        sampling = sampling,
        sampleCount = sampleCount,
        sampleFps = sampleFps,
        sampleSummary = sampleSummary,
        deviceInfo = deviceInfo,
        checks = checks,
        diagBusy = diagBusy,
        onRunDiagnostics = {
            diagBusy = true
            scope.launch {
                val fpNow = fingerprint
                checks = withContext(Dispatchers.IO) {
                    Diagnostics.run(context, fpNow, capability)
                }
                diagBusy = false
                ActionLog.append(context, "检测", "运行环境自检 ${checks.count { it.ok }}/${checks.size} 通过")
                logVersion++
            }
        },
        logLines = logLines,
        onRefreshLog = { logVersion++ },
        onClearLog = {
            scope.launch {
                withContext(Dispatchers.IO) { ActionLog.clear(context) }
                logVersion++
            }
        },
        onOpenSchedule = { showSchedule = true },
        onStartSample = { startSampling() },
        onRefreshStatus = {
            val fpNow = fingerprint
            if (fpNow != null) {
                scope.launch {
                    liveStatus = withContext(Dispatchers.IO) {
                LiveStatus.read(context, fpNow).text() + "\n" + PerfMonitor.read(fpNow)
            }
                }
            }
        },
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
        },
        nodes = {
            NodeScreen(
                groups = NodeBrowser.groups,
                nodes = nodes,
                loading = nodeLoading,
                message = nodeMessage,
                onLoadGroup = { prefix ->
                    nodeLoading = true
                    nodeMessage = ""
                    scope.launch {
                        nodes = withContext(Dispatchers.IO) { NodeBrowser.list(prefix) }
                        nodeLoading = false
                        nodeMessage = if (nodes.isEmpty()) "这一类没读到可写节点"
                        else "已加载 ${nodes.size} 个节点：" + prefix
                    }
                },
                onApply = { path, value ->
                    scope.launch {
                        val fpNow = fingerprint
                        if (fpNow == null) {
                            nodeMessage = "缺少频点表"
                        } else {
                            val r = withContext(Dispatchers.IO) {
                                SysfsApply.apply(listOf("raw:$path" to value), fpNow)
                            }
                            nodeMessage = (if (r.ok) "✅ " else "❌ ") + r.message
                            ActionLog.append(context, "节点", "$path = $value → ${r.message.take(40)}")
                            logVersion++
                        }
                    }
                },
            )
        },
        monitor = {
            MonitorScreen(
                cap = capability,
                fp = fingerprint,
                liveStatus = liveStatus,
                onRefreshStatus = {
                    val fpNow = fingerprint
                    if (fpNow != null) {
                        scope.launch {
                            liveStatus = withContext(Dispatchers.IO) {
                                LiveStatus.read(context, fpNow).text() + "\n" + PerfMonitor.read(fpNow)
                            }
                        }
                    }
                },
                sampling = sampling,
                sampleCount = sampleCount,
                sampleFps = sampleFps,
                sampleSummary = sampleSummary,
                onStartSample = { startSampling() },
                checks = checks,
                diagBusy = diagBusy,
                onRunDiagnostics = {
                    diagBusy = true
                    scope.launch {
                        val fpNow = fingerprint
                        checks = withContext(Dispatchers.IO) { Diagnostics.run(context, fpNow, capability) }
                        diagBusy = false
                        ActionLog.append(context, "检测", "环境自检 ${checks.count { it.ok }}/${checks.size} 通过")
                        logVersion++
                    }
                },
                logLines = logLines,
                onRefreshLog = { logVersion++ },
                onClearLog = {
                    scope.launch {
                        withContext(Dispatchers.IO) { ActionLog.clear(context) }
                        logVersion++
                    }
                },
            )
        },
        settings = {
            SettingsScreen(
                fp = fingerprint,
                deviceInfo = deviceInfo,
                autoOn = autoOn,
                autoRules = autoRules,
                onToggleAuto = { on ->
                    autoOn = on
                    AutoSwitch.setEnabled(context, on)
                    ActionLog.append(context, "自动切换", if (on) "开启" else "关闭")
                    logVersion++
                },
                onSaveAutoRules = { text ->
                    AutoSwitch.saveRules(context, text)
                    autoRules = text
                    autoOn = true
                    AutoSwitch.setEnabled(context, true)
                },
                onOpenApps = {
                    showApps = true
                    appsLoading = true
                    scope.launch {
                        apps = withContext(Dispatchers.IO) { AppGovernance.listUserApps().map { it.pkg } }
                        appsLoading = false
                    }
                },
                hudOn = hudOn,
                hudMessage = hudMessage,
                onToggleHud = {
                    val ctx = context
                    if (!android.provider.Settings.canDrawOverlays(ctx)) {
                        hudMessage = "需要「悬浮窗」权限，已跳转设置"
                        runCatching {
                            ctx.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + ctx.packageName),
                                ),
                            )
                        }
                    } else if (hudOn) {
                        HudService.stop(ctx)
                        hudOn = false
                        hudMessage = "已关闭悬浮控制"
                        ActionLog.append(ctx, "悬浮窗", "关闭")
                        logVersion++
                    } else {
                        HudService.start(ctx)
                        hudOn = true
                        hudMessage = "已开启，桌面上可拖动的小面板"
                        ActionLog.append(ctx, "悬浮窗", "开启")
                        logVersion++
                    }
                },
                hzModes = hzModes,
                currentHz = DisplayControl.current(context),
                hzMessage = hzMessage,
                onSetHz = { hz ->
                    scope.launch {
                        hzMessage = withContext(Dispatchers.IO) { DisplayControl.setHz(hz) }
                        ActionLog.append(context, "刷新率", "锁定 ${hz}Hz → $hzMessage")
                        logVersion++
                    }
                },
                onResetHz = {
                    scope.launch {
                        hzMessage = withContext(Dispatchers.IO) { DisplayControl.reset() }
                        logVersion++
                    }
                },
                onOpenGuide = { showGuide = true },
                onClearLog = {
                    scope.launch {
                        withContext(Dispatchers.IO) { ActionLog.clear(context) }
                        logVersion++
                    }
                },
            )
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

private suspend fun deployCustomPatch(text: String, fingerprint: DeviceFingerprint?): String {
        val fpNow = fingerprint ?: return "❌ 缺少本机频点表"
        val parsed = PatchTextParser.parse(text, fpNow)
        if (parsed.patch.isEmpty()) {
            return "❌ ${parsed.errors.firstOrNull() ?: "没有可部署的项"}"
        }
        val base = if (SceneState.detect().willApply) {
            val raw = SceneFiles.profile(SuShell)
            if (raw == null) {
                "❌ 读不到 Scene 的 profile（需要 root）"
            } else {
                val patched = ProfileService.patch(raw, parsed.patch, fpNow)
                val written = ImportService.writeProfile(patched.profile)
                if (written.ok) "✅ 已写入 Scene 方案：${parsed.notes.joinToString("、")}"
                else "❌ ${written.message}"
            }
        } else {
            val r = SysfsApply.apply(parsed.patch, fpNow)
            if (r.ok) "✅ ${r.message}" else "❌ ${r.message}"
        }
        val warn = if (parsed.errors.isEmpty()) "" else "\n⚠ ${parsed.errors.joinToString("；")}"
        return base + warn
    }

private suspend fun deployPatchList(patch: List<Pair<String, String>>, fp: DeviceFingerprint): String {
    if (patch.isEmpty()) return "❌ 没有要应用的项"
    return if (SceneState.detect().willApply) {
        val raw = SceneFiles.profile(SuShell)
        if (raw == null) "❌ 读不到 Scene 的 profile（需要 root）"
        else {
            val patched = ProfileService.patch(raw, patch, fp)
            val written = ImportService.writeProfile(patched.profile)
            if (written.ok) "✅ 已写入 Scene 方案：${patched.changes.take(4).joinToString("、")}"
            else "❌ ${written.message}"
        }
    } else {
        val r = SysfsApply.apply(patch, fp)
        if (r.ok) "✅ ${r.message}" else "❌ ${r.message}"
    }
}
