"""调度快照 A/B：一键保存当前内核调度状态、一键恢复。

这是玩家最痛的「调完忘记恢复」。快照存的是 sysfs 真实值，不依赖 Scene 自己在跑，
所以哪怕 Scene 被系统杀了、重启了，也能把机器拉回日常状态。
"""

from __future__ import annotations

import glob
import json
import os
import shlex
import time

import analyze
import sceneread
import shell

SNAP_DIR = "/sdcard/DK/scenebox/data/snapshots"

# 与方案无关、但恢复时必须一起兜住的节点（不同 SoC 上存在哪个就抓哪个）
BASE_GLOBS = [
    "/sys/devices/system/cpu/cpufreq/policy*/scaling_governor",
    "/sys/devices/system/cpu/cpufreq/policy*/scaling_min_freq",
    "/sys/devices/system/cpu/cpufreq/policy*/scaling_max_freq",
    "/sys/devices/system/cpu/cpufreq/policy*/walt/hispeed_freq",
    "/sys/devices/system/cpu/cpufreq/policy*/walt/target_loads",
    "/sys/devices/system/cpu/cpufreq/boost",
    "/sys/devices/system/cpu/cpu*/core_ctl/min_cpus",
    "/sys/devices/system/cpu/cpu*/core_ctl/max_cpus",
    "/sys/devices/system/cpu/cpu*/core_ctl/enable",
    "/proc/sys/walt/sched_boost",
    "/proc/sys/walt/input_boost/sched_boost_on_input",
    "/proc/sys/kernel/sched_util_clamp_min",
    "/proc/sys/kernel/sched_util_clamp_max",
    "/sys/module/perfmgr/parameters/perfmgr_enable",
    "/sys/module/migt/parameters/glk_disable",
    "/proc/game_opt/disable_cpufreq_limit",
    "/sys/class/thermal/thermal_message/cpu_limits",
    "/sys/class/thermal/thermal_message/temp_state",
    "/sys/class/thermal/thermal_message/cpu_nolimit_temp",
    "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
    "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
    "/sys/module/msm_performance/parameters/cpu_max_freq",
    "/sys/module/msm_performance/parameters/cpu_min_freq",
    "/sys/module/task_turbo/parameters/feats",
]


def tracked_paths(profile: dict | None) -> list[str]:
    paths: set[str] = set()
    for pat in BASE_GLOBS:
        paths.update(glob.glob(pat))
    if profile:
        for p in (profile.get("alias") or {}).values():
            if isinstance(p, str) and p.startswith("/"):
                paths.add(p)
        for w in analyze.expand(profile):
            p = w.get("path")
            if p and p.startswith("/") and "<missing" not in p:
                paths.add(p)
    return sorted(paths)


def read_many(paths: list[str]) -> dict[str, str]:
    """一次 shell 批量读，避免几百次进程启动。"""
    if not paths:
        return {}
    script = (
        "for p in " + " ".join(shlex.quote(p) for p in paths) + "; do "
        'if [ -f "$p" ]; then v=$(cat "$p" 2>/dev/null | tr "\\n" " "); '
        'printf "%s\\t%s\\n" "$p" "$v"; fi; done'
    )
    code, out = shell.run_root(script, timeout=40)
    vals: dict[str, str] = {}
    if code != 0:
        return vals
    for line in out.splitlines():
        if "\t" in line:
            p, v = line.split("\t", 1)
            vals[p.strip()] = v.strip()
    return vals


def save(label: str, note: str = "") -> dict:
    profile = sceneread.read_profile()
    paths = tracked_paths(profile)
    vals = read_many(paths)
    snap = {
        "label": label,
        "note": note,
        "saved_at": time.strftime("%Y-%m-%dT%H:%M:%S%z", time.localtime()),
        "device": sceneread.fingerprint()["device"],
        "count": len(vals),
        "values": vals,
    }
    os.makedirs(SNAP_DIR, exist_ok=True)
    path = os.path.join(SNAP_DIR, f"{label}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(snap, f, ensure_ascii=False, indent=1)
    snap["_path"] = path
    return snap


def load(label: str) -> dict:
    path = os.path.join(SNAP_DIR, f"{label}.json")
    if not os.path.exists(path):
        raise FileNotFoundError(f"快照不存在：{path}")
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def list_snapshots() -> list[dict]:
    out = []
    for p in sorted(glob.glob(os.path.join(SNAP_DIR, "*.json"))):
        try:
            with open(p, encoding="utf-8") as f:
                d = json.load(f)
            out.append({"label": d.get("label"), "saved_at": d.get("saved_at"),
                        "note": d.get("note", ""), "count": d.get("count", 0),
                        "path": p})
        except Exception:
            continue
    return out


def restore(label: str, dry_run: bool = False) -> dict:
    snap = load(label)
    vals: dict[str, str] = snap.get("values", {})
    todo = [(p, v) for p, v in vals.items() if v != ""]
    if dry_run:
        return {"label": label, "total": len(todo), "written": 0, "failed": [],
                "dry_run": True}

    ok, fail = 0, []
    # 分批写，单次 shell 内完成，速度快且原子性更好
    CHUNK = 40
    for i in range(0, len(todo), CHUNK):
        part = todo[i:i + CHUNK]
        cmds = []
        for p, v in part:
            q, qv = shlex.quote(p), shlex.quote(v)
            cmds.append(
                f'if [ -e {q} ]; then chmod 0664 {q} 2>/dev/null; '
                f'echo {qv} > {q} 2>/dev/null && echo "OK {q}" || echo "FAIL {q}"; '
                f'else echo "MISS {q}"; fi'
            )
        code, out = shell.run_root("; ".join(cmds), timeout=40)
        for line in out.splitlines():
            if line.startswith("OK "):
                ok += 1
            elif line.startswith(("FAIL ", "MISS ")):
                fail.append(line[5:])

    # 回读校验
    after = read_many([p for p, _ in todo])
    mismatch = [p for p, v in todo if p in after and after[p] != v]
    return {"label": label, "total": len(todo), "written": ok, "failed": fail,
            "mismatch": mismatch[:20], "checked": len(after)}


def diff(label_a: str, label_b: str) -> list[dict]:
    a, b = load(label_a), load(label_b)
    va, vb = a.get("values", {}), b.get("values", {})
    rows = []
    for p in sorted(set(va) | set(vb)):
        if va.get(p) != vb.get(p):
            rows.append({"path": p, "a": va.get(p, "<无>"), "b": vb.get(p, "<无>")})
    return rows
