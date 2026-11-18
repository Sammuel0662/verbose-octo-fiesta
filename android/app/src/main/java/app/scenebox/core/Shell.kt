package app.scenebox.core

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * 一次 shell 调用的结果。
 */
data class ShellResult(val code: Int, val out: String) {
    fun text(): String = out.trim()
    fun yes(): Boolean = text() == "Y" || text() == "y"
    fun has(): Boolean = text().isNotEmpty()
}

/**
 * 特权通道抽象。三层实现对应 docs/permissions.md 里的 T0/T1/T2：
 *  - [AppShell]      T0 普通 App，只能读公开节点
 *  - [ShizukuShell]  T1 uid 2000，能读不能写（实测 0/11 可写）
 *  - [SuShell]       T2 uid 0，读写全开
 */
interface ShellChannel {
    val name: String
    fun exec(cmd: String, timeoutSec: Long = 8L): ShellResult

    /**
     * 在「宿主命名空间」里执行。
     *
     * 实测：某些 ROM/模块会把别的 App 的 /data/data/<pkg> 在应用自己的 mount
     * namespace 里整个隐藏掉（表现是 No such file or directory），此时即使拿到
     * root 也读不到 Scene 的配置。nsenter 进 init 的 mount namespace 就能读。
     */
    fun execHost(cmd: String, timeoutSec: Long = 8L): ShellResult = exec(cmd, timeoutSec)
}

/** 单引号转义，用于把整条命令塞进 sh -c '...' */
internal fun shQuote(raw: String): String = "'" + raw.replace("'", "'\\''") + "'"

internal fun execArgv(argv: Array<String>, timeoutSec: Long): ShellResult {
    return try {
        val p = ProcessBuilder(*argv).redirectErrorStream(true).start()
        val done = p.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!done) p.destroy()
        val out = try {
            p.inputStream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            ""
        }
        ShellResult(if (done) p.exitValue() else -1, out)
    } catch (t: Throwable) {
        ShellResult(-1, "")
    }
}

object AppShell : ShellChannel {
    override val name = "app"
    override fun exec(cmd: String, timeoutSec: Long): ShellResult =
        execArgv(arrayOf("sh", "-c", cmd), timeoutSec)
}

object SuShell : ShellChannel {
    override val name = "su"
    override fun exec(cmd: String, timeoutSec: Long): ShellResult =
        execArgv(arrayOf("su", "-c", cmd), timeoutSec)

    override fun execHost(cmd: String, timeoutSec: Long): ShellResult =
        execArgv(
            arrayOf("su", "-c", "nsenter -t 1 -m -- /system/bin/sh -c ${shQuote(cmd)}"),
            timeoutSec,
        )
}

class ShizukuShell : ShellChannel {
    override val name = "shizuku"

    /**
     * Shizuku 13.x 把 `newProcess` 改成了 private（源码里是
     * `private static ShizukuRemoteProcess newProcess(...)`，只加了 @RestrictTo），
     * 公开 API 只剩 getBinder()，而 ShizukuRemoteProcess 的构造器又是包内可见，
     * 没法从外部拼出来。所以这里用反射调私有方法。
     *
     * 失败时返回 null，上层自动降级到 AppShell —— 宁可功能少，不能崩。
     * TODO(v0.3)：改成走 IShizukuService AIDL（api 依赖已经带了 shared 模块）。
     */
    private fun newProcessReflect(cmd: Array<String>): Process? = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        m.isAccessible = true
        m.invoke(null, cmd, null, null) as? Process
    } catch (t: Throwable) {
        null
    }

    override fun exec(cmd: String, timeoutSec: Long): ShellResult {
        val p = newProcessReflect(arrayOf("sh", "-c", cmd)) ?: return ShellResult(-1, "")
        return try {
            val done = p.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!done) p.destroy()
            val out = try {
                p.inputStream.bufferedReader().use { it.readText() }
            } catch (t: Throwable) {
                ""
            }
            ShellResult(if (done) p.exitValue() else -1, out)
        } catch (t: Throwable) {
            ShellResult(-1, "")
        }
    }
}

/**
 * Shizuku 授权流程。注意：必须在 requestPermission 之前注册监听。
 */
object ShizukuBridge {
    private const val REQ_CODE = 3001
    private var pending: ((Boolean) -> Unit)? = null

    private val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQ_CODE) {
            val cb = pending
            pending = null
            cb?.invoke(grantResult == PackageManager.PERMISSION_GRANTED)
        }
    }

    fun alive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun granted(): Boolean = try {
        alive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun request(onResult: (Boolean) -> Unit) {
        if (!alive()) {
            onResult(false)
            return
        }
        if (granted()) {
            onResult(true)
            return
        }
        pending = onResult
        try {
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQ_CODE)
        } catch (t: Throwable) {
            pending = null
            onResult(false)
        }
    }
}
