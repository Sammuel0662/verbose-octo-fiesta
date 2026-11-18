""".sbox 配置包：构建 / 读写 / 校验和。"""

from __future__ import annotations

import hashlib
import json
import os
import re
import secrets
import time

import analyze
import sceneread
import shell

FORMAT = "sbox"
FORMAT_VERSION = 1
GENERATOR = "SceneBox 0.1.0 (engine)"

COMPONENTS = ["profile", "app_policy", "games", "refresh_rate", "per_app", "thermal"]


def _now_iso() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S%z", time.localtime())


def canonical(bundle: dict) -> str:
    body = {k: v for k, v in bundle.items() if k != "checksum"}
    return json.dumps(body, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def checksum(bundle: dict) -> str:
    return hashlib.sha256(canonical(bundle).encode("utf-8")).hexdigest()


def slug(text: str, limit: int = 24) -> str:
    s = re.sub(r"[^0-9A-Za-z\u4e00-\u9fff._-]+", "-", (text or "").strip())
    return s.strip("-")[:limit] or "sbox"


def build(name: str, author: str = "", description: str = "",
          game: str = "", style: str = "",
          components: dict | None = None) -> dict:
    fp = sceneread.fingerprint()
    profile = sceneread.read_profile()
    powercfg = sceneread.read_prefs("powercfg")
    games_pref = sceneread.read_prefs("games")
    refresh = sceneread.read_refresh_rate()
    per_app = sceneread.read_per_app()
    thermal = sceneread.read_thermal_presets()

    if components is None:
        components = {c: True for c in COMPONENTS}
        # 体积大且机型强相关的默认不带
        components["thermal"] = False
        components["per_app"] = False

    payload = {
        "profile": profile if components.get("profile") else None,
        "app_policy": {k: v for k, v in powercfg.items()} if components.get("app_policy") else {},
        "games": [k for k, v in games_pref.items() if v] if components.get("games") else [],
        "refresh_rate": refresh if components.get("refresh_rate") else [],
        "per_app": per_app if components.get("per_app") else [],
        "thermal": thermal if components.get("thermal") else [],
        "components": components,
    }

    bundle = {
        "format": FORMAT,
        "format_version": FORMAT_VERSION,
        "id": f"sbox_{time.strftime('%Y%m%d_%H%M%S')}_{secrets.token_hex(3)}",
        "meta": {
            "name": name,
            "author": author,
            "description": description,
            "created_at": _now_iso(),
            "generator": GENERATOR,
            "tags": {
                "game": [game] if game else [],
                "style": [style] if style else [],
                "soc": [fp["soc"]["model"]] if fp["soc"]["model"] else [],
                "device": [fp["device"]["model"]] if fp["device"]["model"] else [],
            },
        },
        "target": fp,
        "payload": payload,
        "analysis": analyze.analyze(profile),
    }
    bundle["checksum"] = {"algo": "sha256", "value": checksum(bundle)}
    return bundle


def save(bundle: dict, out_dir: str) -> str:
    os.makedirs(out_dir, exist_ok=True)
    tags = (bundle.get("meta") or {}).get("tags") or {}
    parts = [p for p in (tags.get("game") or [None])[:1] + (tags.get("style") or [None])[:1] if p]
    dev = (bundle.get("target", {}).get("device") or {}).get("model", "")
    stamp = time.strftime("%Y%m%d-%H%M")
    fname = "-".join(parts + [x for x in (dev, stamp) if x])
    path = os.path.join(out_dir, f"{slug(fname, 64)}.sbox")
    n = 1
    while os.path.exists(path):
        n += 1
        path = os.path.join(out_dir, f"{slug(fname, 64)}-{n}.sbox")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(bundle, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return path


def load(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        raw = f.read()
    bundle = json.loads(raw)
    if bundle.get("format") != FORMAT:
        raise ValueError(f"不是 .sbox 文件：format={bundle.get('format')!r}")
    if int(bundle.get("format_version", 0)) > FORMAT_VERSION:
        raise ValueError("配置包版本比当前引擎新，请升级 SceneBox")
    return bundle


def verify_integrity(bundle: dict) -> tuple[bool, str]:
    want = (bundle.get("checksum") or {}).get("value")
    if not want:
        return True, "无校验和（可接受）"
    got = checksum(bundle)
    return (want == got), ("校验和一致" if want == got else "校验和不一致：文件被改动过")


# --------------------------------------------------------------- 预览渲染

def render_preview(bundle: dict) -> str:
    b = bundle
    meta, tgt, pl = b.get("meta", {}), b.get("target", {}), b.get("payload", {})
    an = b.get("analysis", {})
    dev, soc, osi = tgt.get("device", {}), tgt.get("soc", {}), tgt.get("os", {})
    comps = pl.get("components", {})
    lines = []
    add = lines.append

    add(f"■ {meta.get('name') or '(未命名)'}   [{b.get('id','')}]")
    if meta.get("description"):
        add(f"  {meta['description']}")
    tags = meta.get("tags", {})
    tag_txt = " ".join(f"#{x}" for k in ("game", "style", "soc", "device")
                       for x in (tags.get(k) or []))
    if tag_txt:
        add(f"  标签 {tag_txt}")
    add(f"  作者 {meta.get('author') or '匿名'} · {meta.get('created_at','')}")
    add("")
    add(f"■ 适配信息  {dev.get('brand','')} {dev.get('model','')} / "
        f"{soc.get('platform','')} {soc.get('model','')} / Android {osi.get('android','')}")
    add(f"  Scene {tgt.get('scene',{}).get('daemon','')} · 生成于 {meta.get('generator','')}")
    add("")

    st = an.get("stats", {})
    add(f"■ 方案规模  预设 {st.get('presets',0)} 组 / 档位 {st.get('schemes',0)} 档 / "
        f"展开写入 {st.get('writes',0)} 条 ({st.get('paths',0)} 个节点)")
    profile = pl.get("profile") or {}
    for scheme, phases in (profile.get("schemes") or {}).items():
        if isinstance(phases, dict):
            add(f"    {scheme:12s} 阶段: {', '.join(phases.keys())}")
    add("")

    add("■ 组件")
    names = {"profile": "调度方案(profile.json)", "app_policy": "每应用档位映射",
             "games": "游戏列表", "refresh_rate": "帧率覆盖", "per_app": "每应用设置",
             "thermal": "温控预设"}
    for k in COMPONENTS:
        if comps.get(k):
            v = pl.get(k)
            n = len(v) if isinstance(v, (list, dict)) else 0
            add(f"    ✓ {names[k]:22s} {n} 项")
        else:
            add(f"    · {names[k]:22s} （未包含）")
    add("")

    risk = an.get("risk", "unknown")
    icon = {"high": "🔴", "medium": "🟡", "low": "🟢"}.get(risk, "⚪")
    add(f"■ 风险评估  {icon} {risk.upper()}")
    for it in an.get("items", [])[:12]:
        add(f"    [{it['level']:7s}] {it['message']}")
    if len(an.get("items", [])) > 12:
        add(f"    …… 另有 {len(an['items']) - 12} 项")
    return "\n".join(lines)
