"""读取 Scene（com.omarea.vtools）本地配置 + 本机指纹。

实测依据（Scene 8.1.5，Redmi 25053RT47C / SM8735 / Android 16）：
  /data/data/com.omarea.vtools/
    files/profile.json          <- 调度方案本体（aliases / presets / schemes），核心
    files/REFRESH_RATE.CONF     <- 每应用帧率覆盖 "pkg min max"
    files/_Games.json           <- 分类级调度覆盖（所有游戏）
    files/miui-thermal/         <- 温控预设（cool/... 每套是一堆 thermal-*.conf）
    files/kr-script/            <- 内核脚本（含 thermal_conf3 温控配置）
    shared_prefs/powercfg.xml   <- 包名/Activity -> 档位（powersave/balance/performance/fast/igoned）
    shared_prefs/games.xml      <- 哪些包被标记为游戏
    shared_prefs/global.xml     <- 全局开关（working_mode、daemon 版本等）
    databases/scene3_config     <- 每应用 alone_light / freeze / screen_orientation 等
"""

from __future__ import annotations

import json
import os
import shutil
import sqlite3
import tempfile
import xml.etree.ElementTree as ET

import shell

PACKAGE = "com.omarea.vtools"
CANDIDATE_DATA_DIRS = [
    f"/data/data/{PACKAGE}",
    f"/data/user/0/{PACKAGE}",
]


def data_dir() -> str | None:
    for d in CANDIDATE_DATA_DIRS:
        if shell.exists(d):
            return d
    return None


# ---------------------------------------------------------------- shared_prefs


def _parse_prefs(xml_text: str) -> dict:
    """解析 Android SharedPreferences XML，保留原始类型。"""
    out: dict = {}
    root = ET.fromstring(xml_text)
    for node in root:
        name = node.get("name")
        if not name:
            continue
        tag = node.tag
        if tag == "string":
            out[name] = node.text or ""
        elif tag == "int":
            out[name] = int(node.get("value", "0"))
        elif tag == "long":
            out[name] = int(node.get("value", "0"))
        elif tag == "boolean":
            out[name] = node.get("value", "false") == "true"
        elif tag == "float":
            out[name] = float(node.get("value", "0"))
        elif tag == "set":
            out[name] = [c.text for c in node if c.text]
    return out


def read_prefs(name: str) -> dict:
    d = data_dir()
    if not d:
        return {}
    txt = shell.read_text(f"{d}/shared_prefs/{name}.xml")
    if not txt:
        return {}
    try:
        return _parse_prefs(txt)
    except ET.ParseError:
        return {}


# ------------------------------------------------------------------ 核心剖面


def read_profile() -> dict | None:
    d = data_dir()
    if not d:
        return None
    txt = shell.read_text(f"{d}/files/profile.json")
    if not txt:
        return None
    try:
        return json.loads(txt)
    except json.JSONDecodeError:
        return None


def read_refresh_rate() -> list[dict]:
    d = data_dir()
    if not d:
        return []
    txt = shell.read_text(f"{d}/files/REFRESH_RATE.CONF", "")
    rows = []
    for line in (txt or "").splitlines():
        parts = line.split()
        if len(parts) == 3:
            try:
                rows.append(
                    {"package": parts[0], "min": int(parts[1]), "max": int(parts[2])}
                )
            except ValueError:
                continue
    return rows


def read_category_overrides() -> dict:
    """files/_Games.json 等分类级覆盖，原样保留（体积很小）。"""
    d = data_dir()
    if not d:
        return {}
    result = {}
    for fn in ("_Games.json", "_Apps.json", "_Launcher.json", "_Camera.json",
               "_Scanner.json", "_Standby.json"):
        txt = shell.read_text(f"{d}/files/{fn}")
        if not txt:
            continue
        try:
            result[fn] = json.loads(txt)
        except json.JSONDecodeError:
            continue
    return result


def read_thermal_presets() -> list[str]:
    d = data_dir()
    if not d:
        return []
    code, out = shell.run_root(
        f"ls -1 {d}/files/miui-thermal 2>/dev/null; ls -1 {d}/files/kr-script/miui 2>/dev/null"
    )
    names = {x.strip() for x in out.splitlines() if x.strip()}
    return sorted(names)


def read_per_app() -> list[dict]:
    """databases/scene3_config 里的每应用设置。先拷到临时目录再用 sqlite 读。"""
    d = data_dir()
    if not d:
        return []
    src = f"{d}/databases/scene3_config"
    if not shell.exists(src):
        return []
    workdir = "/sdcard/DK/scenebox/data/tmp"
    os.makedirs(workdir, exist_ok=True)
    tmpdir = tempfile.mkdtemp(prefix="sbox_db_", dir=workdir)
    dst = os.path.join(tmpdir, "scene3_config.db")
    try:
        code, _ = shell.run_root(f"cat {src} > {dst} && chmod 644 {dst}")
        if code != 0 or not os.path.exists(dst):
            return []
        con = sqlite3.connect(dst)
        con.row_factory = sqlite3.Row
        rows = [dict(r) for r in con.execute("select * from scene_config3")]
        con.close()
        return rows
    except Exception:
        return []
    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)


# ------------------------------------------------------------------ 本机指纹


def cpu_tables() -> dict[str, dict]:
    """policy -> {min,max,avail[],cpuinfo_max}"""
    tables: dict[str, dict] = {}
    for p in shell.glob_root("/sys/devices/system/cpu/cpufreq/policy*"):
        name = os.path.basename(p)
        avail_txt = shell.read_text(f"{p}/scaling_available_frequencies", "") or ""
        avail = []
        for tok in avail_txt.split():
            try:
                avail.append(int(tok))
            except ValueError:
                pass
        if not avail:
            continue

        def _num(f):
            v = (shell.read_text(f"{p}/{f}", "") or "").strip()
            try:
                return int(v)
            except ValueError:
                return 0

        tables[name] = {
            "min": _num("cpuinfo_min_freq") or min(avail),
            "max": _num("cpuinfo_max_freq") or max(avail),
            "avail": avail,
        }
    return tables


def fingerprint() -> dict:
    scene_global = read_prefs("global")
    return {
        "device": {
            "brand": shell.getprop("ro.product.brand"),
            "model": shell.getprop("ro.product.model"),
            "device": shell.getprop("ro.product.device"),
            "manufacturer": shell.getprop("ro.product.manufacturer"),
        },
        "soc": {
            "model": shell.getprop("ro.soc.model"),
            "platform": shell.getprop("ro.board.platform"),
            "manufacturer": shell.getprop("ro.soc.manufacturer"),
        },
        "os": {
            "android": shell.getprop("ro.build.version.release"),
            "sdk": shell.getprop("ro.build.version.sdk"),
            "rom": shell.getprop("ro.miui.ui.version.name")
            or shell.getprop("ro.build.display.id"),
            "kernel": (shell.read_text("/proc/version", "") or "").strip()[:160],
        },
        "scene": {
            "package": PACKAGE,
            "daemon": scene_global.get("daemon_version_name", ""),
            "cloud_profile_version": scene_global.get("CLOUD_PROFILE_VERSION", 0),
            "working_mode": scene_global.get("working_mode", ""),
        },
        "cpu": {"policies": cpu_tables()},
    }
