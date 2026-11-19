package app.scenebox.hud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import app.scenebox.MainActivity
import app.scenebox.core.AutoSwitch
import app.scenebox.core.DeviceFingerprint
import app.scenebox.core.DisplayControl
import app.scenebox.core.RecipeStore
import app.scenebox.core.SuShell
import app.scenebox.core.SysfsApply

/**
 * 悬浮控制（Scene 风格：收起是小胶囊，点开是面板）。
 *
 * 只写 sysfs 与系统设置，不注入游戏、不读其他应用内存。
 */
class HudService : Service() {

    private lateinit var windowManager: WindowManager
    private var current: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false
    private var modeName = "均衡"
    private val main = Handler(Looper.getMainLooper())
    private var lastPkg = ""

    private val presets = linkedMapOf(
        "省电" to listOf(
            "cpu_max_pct_p0" to "55", "cpu_max_pct_p1" to "55",
            "cpu_max_pct_p2" to "55", "cpu_max_pct_p3" to "55",
        ),
        "均衡" to listOf(
            "cpu_max_pct_p0" to "78", "cpu_max_pct_p1" to "78",
            "cpu_max_pct_p2" to "78", "cpu_max_pct_p3" to "78",
        ),
        "性能" to listOf(
            "cpu_max_pct_p0" to "100", "cpu_max_pct_p1" to "100",
            "cpu_max_pct_p2" to "100", "cpu_max_pct_p3" to "100",
            "governor" to "schedutil",
        ),
        "极限" to listOf(
            "cpu_max_pct_p0" to "100", "cpu_max_pct_p1" to "100",
            "cpu_max_pct_p2" to "100", "cpu_max_pct_p3" to "100",
            "governor" to "schedutil", "limits" to "off",
        ),
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        render()
        main.postDelayed(autoTask, 5000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_EXPAND -> {
                expanded = true
                render()
            }
            else -> if (current == null) render()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        current?.let { runCatching { windowManager.removeView(it) } }
        current = null
        super.onDestroy()
    }

    // ---------- 渲染 ----------

    private fun render() {
        if (!Settings.canDrawOverlays(this)) {
            notifyNoPermission()
            return
        }
        val view = if (expanded) buildPanel() else buildPill()
        val lp = params ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 260
        }
        if (current == null) {
            runCatching { windowManager.addView(view, lp) }
        } else {
            runCatching {
                windowManager.removeView(current)
                windowManager.addView(view, lp)
            }
        }
        params = lp
        current = view
        makeDraggable(view, lp)
    }

    /** 收起态：一个小胶囊，显示当前档位 */
    private fun buildPill(): View = TextView(this).apply {
        text = "$modeName ▾"
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#7CE38B"))
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = dp(20f)
            setColor(Color.parseColor("#F010161B"))
            setStroke(dp(1f).toInt(), Color.parseColor("#4D7CE38B"))
        }
        setPadding(dp(14f).toInt(), dp(8f).toInt(), dp(14f).toInt(), dp(8f).toInt())
        setOnClickListener {
            expanded = true
            render()
        }
    }

    /** 展开态：档位 + 刷新率 */
    private fun buildPanel(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(22f)
                setColor(Color.parseColor("#F010161B"))
                setStroke(dp(1f).toInt(), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dp(14f).toInt(), dp(10f).toInt(), dp(14f).toInt(), dp(12f).toInt())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = "SceneBox  $modeName"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            TextView(this).apply {
                text = "收起"
                textSize = 11f
                setTextColor(Color.parseColor("#9AA5AD"))
                setPadding(dp(10f).toInt(), dp(4f).toInt(), 0, dp(4f).toInt())
                setOnClickListener {
                    expanded = false
                    render()
                }
            },
        )
        header.addView(
            TextView(this).apply {
                text = "  ✕"
                textSize = 12f
                setTextColor(Color.parseColor("#9AA5AD"))
                setOnClickListener { stopSelf() }
            },
        )
        root.addView(header)

        val modes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8f).toInt(), 0, 0)
        }
        for ((name, patch) in presets) {
            val chip = chip(name, name == modeName)
            chip.setOnClickListener {
                modeName = name
                applyPatchAsync(patch)
                render()
            }
            modes.addView(chip, weight())
        }
        root.addView(modes)

        val hzRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4f).toInt(), 0, 0)
        }
        for (hz in listOf(60, 90, 120)) {
            val chip = chip("${hz}Hz", false)
            chip.setOnClickListener { applyHzAsync(hz) }
            hzRow.addView(chip, weight())
        }
        val auto = chip("自动", false)
        auto.setOnClickListener { applyHzAsync(0) }
        hzRow.addView(auto, weight())
        root.addView(hzRow)

        return root
    }

    private fun chip(text: String, selected: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = 11f
        gravity = Gravity.CENTER
        setTextColor(if (selected) Color.parseColor("#0B0F12") else Color.parseColor("#7CE38B"))
        background = GradientDrawable().apply {
            cornerRadius = dp(14f)
            setColor(if (selected) Color.parseColor("#7CE38B") else Color.parseColor("#1F2A31"))
            setStroke(dp(1f).toInt(), Color.parseColor("#2E3C45"))
        }
        setPadding(0, dp(7f).toInt(), 0, dp(7f).toInt())
    }

    private fun weight(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(3f).toInt(), 0, dp(3f).toInt(), 0)
        }

    private fun makeDraggable(view: View, lp: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (dx * dx + dy * dy > 400) {
                        lp.x = startX + dx
                        lp.y = startY + dy
                        runCatching { windowManager.updateViewLayout(v, lp) }
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    // ---------- 应用 ----------

    private fun applyPatchAsync(patch: List<Pair<String, String>>) {
        Thread {
            val fp = DeviceFingerprint.collect()
            SysfsApply.apply(patch, fp)
        }.start()
    }

    private fun applyHzAsync(hz: Int) {
        Thread {
            if (hz == 0) DisplayControl.reset() else DisplayControl.setHz(hz)
        }.start()
    }

    private fun notifyNoPermission() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("需要悬浮窗权限")
                .setContentText("点这里去开启，然后重新打开悬浮控制")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, 3,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .build(),
        )
    }

    /** 每 4 秒看一眼前台应用，命中规则就套用对应配方 */
    private val autoTask = object : Runnable {
        override fun run() {
            runCatching {
                if (AutoSwitch.enabled(this@HudService)) {
                    val focus = SuShell.exec(
                        "dumpsys window 2>/dev/null | grep -m1 mCurrentFocus", 8L,
                    ).text()
                    val pkg = Regex("([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+)/")
                        .find(focus)?.groupValues?.get(1)
                    if (!pkg.isNullOrBlank() && pkg != lastPkg) {
                        lastPkg = pkg
                        val recipeName = AutoSwitch.match(this@HudService, pkg)
                        if (recipeName != null) {
                            val body = RecipeStore.list(this@HudService)
                                .firstOrNull { it.name == recipeName }?.body
                            if (body != null) {
                                val patch = RecipeStore.decode(body)
                                if (patch.isNotEmpty()) {
                                    val fp = DeviceFingerprint.collect()
                                    SysfsApply.apply(patch, fp)
                                    modeName = recipeName
                                    main.post { render() }
                                }
                            }
                        }
                    }
                }
            }
            main.postDelayed(this, 4000)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    // ---------- 通知 ----------

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "悬浮控制", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "SceneBox 悬浮控制面板"
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val expand = PendingIntent.getService(
            this, 2,
            Intent(this, HudService::class.java).setAction(ACTION_EXPAND),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, HudService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SceneBox 悬浮控制")
            .setContentText("当前档位：$modeName · 点按打开面板")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setContentIntent(expand)
            .addAction(Notification.Action.Builder(null, "打开 App", open).build())
            .addAction(Notification.Action.Builder(null, "停止", stop).build())
            .build()
    }

    companion object {
        const val CHANNEL_ID = "scenebox_hud"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "app.scenebox.STOP_HUD"
        const val ACTION_EXPAND = "app.scenebox.EXPAND_HUD"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, HudService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HudService::class.java))
        }
    }
}
