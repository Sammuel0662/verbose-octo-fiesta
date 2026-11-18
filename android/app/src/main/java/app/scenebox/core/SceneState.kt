package app.scenebox.core

/**
 * 判断 Scene 当前会不会"真的下发调度"。
 *
 * 实测（本机 Scene 8.1.5）：
 *  - 进程在跑不代表会下发，daemon 日志里 `auto.enable false` 才是关键；
 *  - 关着的时候，往 profile.json 里写东西没有任何东西会去执行。
 */
object SceneState {

    data class State(val daemonAlive: Boolean, val autoEnabled: Boolean) {
        val willApply: Boolean get() = daemonAlive && autoEnabled
    }

    fun detect(): State {
        val pid = SuShell.exec("pidof scene-daemon", 6L).text()
        val alive = pid.isNotEmpty() && pid.all { it.isDigit() }

        val log = SuShell.execHost(
            "grep -o 'auto\\.enable [a-z]*' /data/data/com.omarea.vtools/files/daemon.log 2>/dev/null | tail -1",
            10L,
        ).text()
        val auto = log.contains("auto.enable true")
        return State(alive, auto)
    }
}
