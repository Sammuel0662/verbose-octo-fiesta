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
import app.scenebox.core.DeviceFingerprint
import app.scenebox.core.DisplayControl
import app.scenebox.core.SysfsApply

/**
 * 悬浮控制面板。
 *
 * 点通知展开 App；在 App 里开启后，桌面上会有一个可拖动的小面板，
 * 直接切调度档（省电/均衡/性能/极限）和刷新率，不用回 App。
 *
 * 只写 sysfs 与系统设置，不注入游戏、不读其他应用内存。
 */
class HudService : Service() {

    private lateinit var windowManager: WindowManager
    private var panel: View? = null
    private var statusView: TextView? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        super.onDestroy()
    }

    // ---------- 悬浮面板 ----------

    private fun showPanel() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(22f)
                setColor(Color.parseColor("#EE10161B"))
                setStroke(dp(1f).toInt(), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dp(14f).toInt(), dp(10f).toInt(), dp(14f).toInt(), dp(12f).toInt())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label("SceneBox 控制", 13f, Color.WHITE, bold = true), weight(1f))
        header.addView(label("✕", 14f, Color.parseColor("#9AA5AD")).apply {
            setPadding(dp(10f).toInt(), dp(4f).toInt(), dp(4f).toInt(), dp(4f).toInt())
            setOnClickListener { stopSelf() }
        })
        root.addView(header)

        val status = label("点档位直接生效", 10f, Color.parseColor("#8B979F"))
        status.setPadding(0, dp(2f).toInt(), 0, dp(6f).toInt())
        statusView = status
        root.addView(status)

        // 模式行
        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val presets = listOf(
            "省电" to listOf("cpu_max_pct_p0" to "55", "cpu_max_pct_p1" to "55",
                "cpu_max_pct_p2" to "55", "cpu_max_pct_p3" to "55"),
            "均衡" to listOf("cpu_max_pct_p0" to "78", "cpu_max_pct_p1" to "78",
                "cpu_max_pct_p2" to "78", "cpu_max_pct_p3" to "78"),
            "性能" to listOf("cpu_max_pct_p0" to "100", "cpu_max_pct_p1" to "100",
                "cpu_max_pct_p2" to "100", "cpu_max_pct_p3" to "100",
                "governor" to "schedutil"),
            "极限" to listOf("cpu_max_pct_p0" to "100", "cpu_max_pct_p1" to "100",
                "cpu_max_pct_p2" to "100", "cpu_max_pct_p3" to "100",
                "governor" to "schedutil", "limits" to "off"),
        )
        for ((name, patch) in presets) {
            modes.addView(chip(name) {
                setStatus("正在应用 $name…")
                Thread {
                    val fp = DeviceFingerprint.collect()
                    val result = SysfsApply.apply(patch, fp)
                    setStatus(if (result.ok) "$name 已生效" else "$name 失败：${result.message.take(24)}")
                }.start()
            }, weight(1f))
        }
        root.addView(modes)

        // 刷新率行
        val hzRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (hz in listOf(60, 90, 120)) {
            hzRow.addView(chip("${hz}Hz") {
                setStatus("正在锁定 ${hz}Hz…")
                Thread {
                    val msg = DisplayControl.setHz(hz)
                    setStatus(msg.take(22))
                }.start()
            }, weight(1f))
        }
        hzRow.addView(chip("自动") {
            setStatus("正在恢复自动…")
            Thread { setStatus(DisplayControl.reset().take(22)) }.start()
        }, weight(1f))
        root.addView(hzRow)

        val params = WindowManager.LayoutParams(
            dp(260f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24f).toInt()
            y = dp(220f).toInt()
        }

        makeDraggable(root, params)
        runCatching { windowManager.addView(root, params) }
        panel = root
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (dx * dx + dy * dy > 400) {
                        params.x = startX + dx
                        params.y = startY + dy
                        runCatching { windowManager.updateViewLayout(view, params) }
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    private fun setStatus(text: String) {
        main.post { statusView?.text = text }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun chip(text: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#7CE38B"))
            background = GradientDrawable().apply {
                cornerRadius = dp(14f)
                setColor(Color.parseColor("#1F2A31"))
                setStroke(dp(1f).toInt(), Color.parseColor("#2E3C45"))
            }
            setPadding(0, dp(7f).toInt(), 0, dp(7f).toInt())
            setOnClickListener { onClick() }
        }

    private fun weight(value: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, value).apply {
            setMargins(dp(3f).toInt(), dp(3f).toInt(), dp(3f).toInt(), dp(3f).toInt())
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
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, HudService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SceneBox 悬浮控制运行中")
            .setContentText("点按打开 App；悬浮面板可切档位与刷新率")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "停止", stop).build())
            .build()
    }

    companion object {
        const val CHANNEL_ID = "scenebox_hud"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "app.scenebox.STOP_HUD"

        fun start(context: Context) {
            val intent = Intent(context, HudService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HudService::class.java))
        }
    }
}
