package app.scenebox.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 对比报告导出：把两场采样的曲线和差值画成一张 PNG，存到相册目录，方便直接发帖。
 *
 * 刻意用原生 Canvas 而不是截 Compose 视图——不依赖窗口、尺寸可控、导出稳定。
 */
object ReportExporter {

    data class Sample(
        val label: String,
        val fps: List<Int>,
        val avg: Int,
        val low: Int,
        val jank: Float,
        val maxTemp: Float,
        val governor: String,
        val maxFreqs: String,
    )

    fun export(context: Context, a: Sample, b: Sample, device: String): File? {
        val w = 1080
        val h = 1500
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#0B0F12"))

        val title = Paint().apply {
            color = Color.parseColor("#7CE38B")
            textSize = 46f
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val text = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            isAntiAlias = true
        }
        val accent = Paint().apply {
            color = Color.parseColor("#7CE38B")
            textSize = 28f
            isAntiAlias = true
        }
        val muted = Paint().apply {
            color = Color.parseColor("#9AA5AD")
            textSize = 26f
            isAntiAlias = true
        }
        val lineA = Paint().apply {
            color = Color.parseColor("#7CE38B")
            strokeWidth = 5f
            isAntiAlias = true
            style = Paint.Style.STROKE
        }
        val lineB = Paint().apply {
            color = Color.parseColor("#FF9800")
            strokeWidth = 5f
            isAntiAlias = true
            style = Paint.Style.STROKE
        }

        var y = 90f
        c.drawText("SceneBox 帧率对比", 60f, y, title)
        y += 50f
        c.drawText(device, 60f, y, muted)
        y += 34f
        c.drawText(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()), 60f, y, muted)

        // 曲线区
        val chartLeft = 60f
        val chartTop = y + 60f
        val chartRight = w - 60f
        val chartBottom = chartTop + 460f
        val maxFps = maxOf(a.fps.maxOrNull() ?: 60, b.fps.maxOrNull() ?: 60, 60)

        val grid = Paint().apply {
            color = Color.parseColor("#22FFFFFF")
            strokeWidth = 1f
        }
        for (i in 0..4) {
            val gy = chartBottom - (chartBottom - chartTop) * i / 4f
            c.drawLine(chartLeft, gy, chartRight, gy, grid)
            c.drawText("${(maxFps * i / 4)}", chartLeft - 44f, gy + 8f, muted)
        }

        fun drawSeries(series: List<Int>, paint: Paint) {
            if (series.size < 2) return
            val path = android.graphics.Path()
            series.forEachIndexed { i, v ->
                val x = chartLeft + (chartRight - chartLeft) * i / (series.size - 1).toFloat()
                val vy = chartBottom - (chartBottom - chartTop) * (v.toFloat() / maxFps)
                if (i == 0) path.moveTo(x, vy) else path.lineTo(x, vy)
            }
            c.drawPath(path, paint)
        }
        drawSeries(a.fps, lineA)
        drawSeries(b.fps, lineB)

        // 图例
        y = chartBottom + 50f
        c.drawRect(chartLeft, y - 22f, chartLeft + 36f, y + 4f, lineA)
        c.drawText(a.label, chartLeft + 48f, y, text)
        c.drawRect(chartLeft + 420f, y - 22f, chartLeft + 456f, y + 4f, lineB)
        c.drawText(b.label, chartLeft + 468f, y, text)

        // 差值表
        y += 70f
        fun row(name: String, va: String, vb: String, delta: String) {
            c.drawText(name, 60f, y, muted)
            c.drawText(va, 380f, y, text)
            c.drawText(vb, 640f, y, text)
            c.drawText(delta, 880f, y, accent)
            y += 48f
        }
        c.drawText("指标", 60f, y, muted)
        c.drawText(a.label, 380f, y, muted)
        c.drawText(b.label, 640f, y, muted)
        c.drawText("差值", 880f, y, muted)
        y += 40f
        row("均值 FPS", "${a.avg}", "${b.avg}", "${b.avg - a.avg}")
        row("1% low", "${a.low}", "${b.low}", "${b.low - a.low}")
        row("jank %", String.format("%.2f", a.jank), String.format("%.2f", b.jank), String.format("%+.2f", b.jank - a.jank))
        row("最高温", String.format("%.1f℃", a.maxTemp), String.format("%.1f℃", b.maxTemp), String.format("%+.1f", b.maxTemp - a.maxTemp))

        y += 20f
        c.drawText("方案 A：${a.governor}  上限 ${a.maxFreqs}", 60f, y, muted)
        y += 36f
        c.drawText("方案 B：${b.governor}  上限 ${b.maxFreqs}", 60f, y, muted)
        y += 40f
        c.drawText("由 SceneBox 生成 · 数据来自 gfxinfo 真实帧统计", 60f, y, muted)

        return save(context, bmp)
    }

    private fun save(context: Context, bmp: Bitmap): File? {
        val name = "scenebox_report_" + SimpleDateFormat("MMdd_HHmm", Locale.US).format(Date()) + ".png"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SceneBox")
                }
                val uri: Uri? = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
                )
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    File(Environment.getExternalStorageDirectory(), "Pictures/SceneBox/$name")
                } else null
            } else {
                val dir = File(Environment.getExternalStorageDirectory(), "Pictures/SceneBox")
                dir.mkdirs()
                val file = File(dir, name)
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                file
            }
        } catch (_: Throwable) {
            null
        }
    }
}
