"""特权通道抽象层。

设计原则（对应 App 里的实现）：
- 直接以 root 运行时，读写走本进程；
- 以普通 App uid 运行时，受限路径回落到 `su -c`（未来 App 里换成 Shizuku/rish 通道，
  接口保持不变即可）。

这样引擎既能当 root CLI 跑，也能模拟「普通 App + 特权通道」的真实运行形态。
"""

from __future__ import annotations

import os
import shlex
import subprocess

SU_BIN = "su"
_TIMEOUT = 15


def is_root() -> bool:
    return os.geteuid() == 0


def su_available() -> bool:
    try:
        r = subprocess.run(
            [SU_BIN, "-c", "id -u"], capture_output=True, text=True, timeout=_TIMEOUT
        )
        return r.returncode == 0 and r.stdout.strip() == "0"
    except Exception:
        return False


def run_root(cmd: str, timeout: int = _TIMEOUT) -> tuple[int, str]:
    """以 root 执行一条 shell 命令，返回 (exit_code, stdout)。"""
    if is_root():
        r = subprocess.run(
            ["/system/bin/sh", "-c", cmd], capture_output=True, text=True, timeout=timeout
        )
    else:
        r = subprocess.run(
            [SU_BIN, "-c", cmd], capture_output=True, text=True, timeout=timeout
        )
    return r.returncode, (r.stdout or "")


def read_text(path: str, default: str | None = None) -> str | None:
    """读文本文件；权限不足时自动走 su。"""
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            return f.read()
    except (PermissionError, OSError):
        code, out = run_root(f"cat {shlex.quote(path)} 2>/dev/null")
        if code == 0 and out != "":
            return out
        return default


def read_bytes(path: str) -> bytes | None:
    try:
        with open(path, "rb") as f:
            return f.read()
    except (PermissionError, OSError):
        code, out = run_root(f"cat {shlex.quote(path)} 2>/dev/null | base64")
        if code == 0 and out.strip():
            import base64 as _b64

            return _b64.b64decode(out.strip())
        return None


def write_text(path: str, value: str) -> bool:
    """写 sysfs / 配置文件。sysfs 常需要先 chmod，失败则整体走 su。"""
    try:
        with open(path, "w", encoding="utf-8") as f:
            f.write(value)
        return True
    except (PermissionError, OSError):
        cmd = (
            f"chmod 0664 {shlex.quote(path)} 2>/dev/null; "
            f"echo {shlex.quote(value)} > {shlex.quote(path)}"
        )
        code, _ = run_root(cmd)
        return code == 0


def exists(path: str) -> bool:
    if os.path.exists(path):
        return True
    code, out = run_root(f"test -e {shlex.quote(path)} && echo yes")
    return out.strip() == "yes"


def glob_root(pattern: str) -> list[str]:
    import glob

    hits = glob.glob(pattern)
    if hits:
        return sorted(hits)
    code, out = run_root(f"ls -d {pattern} 2>/dev/null")
    return sorted([x for x in out.splitlines() if x.strip()])


def getprop(key: str, default: str = "") -> str:
    code, out = run_root(f"getprop {shlex.quote(key)} 2>/dev/null")
    v = out.strip() if code == 0 else ""
    return v or default
