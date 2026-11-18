"""调度方案解析 / 风险分析 / 导入校验。

这里是最值钱的一块：把 Scene 的 profile.json 展开成「到底会往哪些路径写什么值」，
再拿目标机的真实频点表和真实路径去比对，做到不盲导入。
"""

from __future__ import annotations

import re

# 频率 token 的合法区间（4~7 位，覆盖 0.4GHz～4.0GHz）
FREQ_MIN = 100000
FREQ_MAX = 4000000

# 只允许写系统侧路径，任何相对路径 / 带 .. 的一律拦
ALLOWED_PREFIX = ("/sys/", "/proc/", "/dev/", "/data/", "/cache/")

POLICY_RE = re.compile(r"(?:policy|cpu)(\d+)")

# 风险规则：不仅看路径，还要看「写成了什么值」。
# 实测 Scene 自带方案里 disable_cpufreq_limit 既会被写 1（关限制）也会被写 0（恢复），
# 只看路径会误报。
RISK_RULES = [
    (re.compile(r"disable_cpufreq_limit"), lambda v: v == "1",
     "关闭内核 CPU 频率限制器（温度/功耗保护失效）", "high"),
    (re.compile(r"perfmgr_enable"), lambda v: v == "0",
     "关闭 perfmgr 性能管理器", "high"),
    (re.compile(r"glk_disable"), lambda v: v == "1",
     "关闭 GLK 游戏内核限制", "high"),
    (re.compile(r"glk_freq_limit"), lambda v: v == "0",
     "关闭 GLK 频率限制", "medium"),
    (re.compile(r"thermal"), lambda v: True,
     "写入温控节点，影响过热降频策略", "high"),
    (re.compile(r"sched_fmax_cap"), lambda v: True,
     "改写调度最大频率上限", "medium"),
]


def policy_of(name: str, path: str | None = None) -> str | None:
    """从 $alias 名或 sysfs 路径里推断属于哪个 policy。"""
    for src in (path or "", name):
        m = POLICY_RE.search(src or "")
        if m:
            return f"policy{m.group(1)}"
    return None


def is_freq_token(tok: str) -> bool:
    if not re.fullmatch(r"[#^@$]?\d{5,8}", tok or ""):
        return False
    return FREQ_MIN <= int(tok.lstrip("#^@$")) <= FREQ_MAX


def strip_flag(tok: str) -> str:
    return (tok or "").lstrip("#^@$")


# ------------------------------------------------------------ 展开写入清单


def expand(profile: dict) -> list[dict]:
    """把 presets + schemes 递归展开成具体的写操作。

    返回 [{path|alias, value, source, op?}]；Scene 原生操作（@governor/@cpuset 等）
    保留为 op 记录，便于预览与统计。
    """
    aliases: dict = profile.get("alias", {}) or {}
    presets: dict = profile.get("presets", {}) or {}
    out: list[dict] = []

    def resolve_target(t: str) -> tuple[str, str | None]:
        if t.startswith("$"):
            key = t[1:]
            return aliases.get(key, f"<missing_alias:{key}>"), key
        return t, None

    def run_ops(ops, source: str, depth: int = 0, seen: frozenset = frozenset()):
        if depth > 6:
            return
        for item in ops or []:
            if not isinstance(item, list) or not item:
                continue
            head = str(item[0])
            if head == "@preset":
                name = item[1] if len(item) > 1 else ""
                if name in presets and name not in seen:
                    run_ops(presets[name], f"{source}>@preset:{name}", depth + 1,
                            seen | {name})
                continue
            if head == "@values":
                if len(item) < 3:
                    continue
                name = item[1]
                values = item[2:]
                targets = presets.get(name)
                if not isinstance(targets, list):
                    out.append({"op": "@values", "args": values, "source": source,
                                "note": f"未知 preset:{name}"})
                    continue
                for i, t in enumerate(targets):
                    if i >= len(values) or not isinstance(t, list) or not t:
                        continue
                    path, alias = resolve_target(str(t[0]))
                    out.append({"path": path, "alias": alias,
                                "value": str(values[i]), "source": f"{source}>@values:{name}"})
                continue
            if head.startswith("@"):
                out.append({"op": head, "args": [str(x) for x in item[1:]],
                            "source": source})
                continue
            if len(item) >= 2:
                path, alias = resolve_target(head)
                out.append({"path": path, "alias": alias,
                            "value": str(item[1]), "source": source})

    for name, ops in presets.items():
        run_ops(ops, f"preset:{name}", seen=frozenset({name}))
    for scheme, phases in (profile.get("schemes") or {}).items():
        if not isinstance(phases, dict):
            continue
        for phase, ops in phases.items():
            run_ops(ops, f"scheme:{scheme}/{phase}")
    return out


# ------------------------------------------------------------ 风险分析


def analyze(profile: dict | None) -> dict:
    items: list[dict] = []
    stats = {"writes": 0, "paths": 0, "presets": 0, "schemes": 0, "ops": 0}
    if not profile:
        return {"risk": "unknown", "items": [{"level": "info", "code": "NO_PROFILE",
                                              "message": "设备上没有找到 Scene 调度方案"}],
                "stats": stats}

    writes = expand(profile)
    stats["writes"] = sum(1 for w in writes if "path" in w)
    stats["ops"] = sum(1 for w in writes if "op" in w)
    stats["paths"] = len({w["path"] for w in writes if "path" in w})
    stats["presets"] = len(profile.get("presets") or {})
    stats["schemes"] = len(profile.get("schemes") or {})

    seen_codes: set[str] = set()

    def add(level: str, code: str, message: str):
        key = f"{level}:{code}:{message}"
        if key in seen_codes:
            return
        seen_codes.add(key)
        items.append({"level": level, "code": code, "message": message})

    # 1. 危险写入（同一节点只报一次）
    hits: dict[str, tuple[str, str]] = {}
    for w in writes:
        p = w.get("path")
        if not p or p.startswith("<missing"):
            continue
        for pat, pred, why, lvl in RISK_RULES:
            if pat.search(p) and pred(strip_flag(w["value"])):
                hits.setdefault(p, (lvl, f"{why}：{p} <- {w['value']}"))
                break
    for lvl, msg in hits.values():
        add(lvl, "RISK_PATH", msg)

    # 2. 路径合法性 + 悬空别名
    dangling: set[str] = set()
    for w in writes:
        p = w.get("path")
        if not p:
            continue
        if p.startswith("<missing_alias:"):
            dangling.add(p[len("<missing_alias:"):-1])
            continue
        if not p.startswith("/") or ".." in p:
            add("block", "BAD_PATH", f"非法路径（相对路径/目录穿越）：{p}")
        elif not p.startswith(ALLOWED_PREFIX):
            add("warn", "UNUSUAL_PATH", f"非常规系统路径：{p}")
    if dangling:
        add("warn", "MISSING_ALIAS",
            f"方案引用了 {len(dangling)} 个未在 alias 中定义的别名"
            f"（{', '.join(sorted(dangling)[:5])}），Scene daemon 会用内置默认值补齐")

    # 3. 判定
    levels = {i["level"] for i in items}
    if "block" in levels or any(i["level"] == "high" for i in items):
        risk = "high"
    elif "warn" in levels or "medium" in levels:
        risk = "medium"
    else:
        risk = "low"

    if not items:
        add("info", "OK", "未发现危险写入")
    return {"risk": risk, "items": items, "stats": stats}


# ------------------------------------------------------------ 目标机校验


def _local_tables(fp: dict) -> dict[str, dict]:
    return {k: v for k, v in (fp.get("cpu", {}).get("policies") or {}).items()}


def _all_freqs(tables: dict[str, dict]) -> set[int]:
    s: set[int] = set()
    for t in tables.values():
        s.update(t.get("avail") or [])
        if t.get("max"):
            s.add(int(t["max"]))
    return s


def validate(bundle: dict, fp: dict) -> dict:
    """导入前强校验。返回 {verdict: pass|confirm|block, issues: [...]}"""
    issues: list[dict] = []

    def add(level: str, code: str, message: str, fix: str = ""):
        issues.append({"level": level, "code": code, "message": message, "fix": fix})

    tgt = bundle.get("target", {})
    payload = bundle.get("payload", {})
    profile = payload.get("profile") or {}

    # --- 机型 / 芯片 / 系统 ---
    b_soc = (tgt.get("soc") or {})
    l_soc = (fp.get("soc") or {})
    if b_soc.get("platform") and l_soc.get("platform") and b_soc["platform"] != l_soc["platform"]:
        add("block", "SOC_MISMATCH",
            f"芯片平台不匹配：模板为 {b_soc.get('platform')}/{b_soc.get('model') or '?'}，"
            f"本机为 {l_soc.get('platform')}/{l_soc.get('model') or '?'}",
            "跨平台导入会写入本机不存在或语义不同的 sysfs 节点，必须拦截")
    elif b_soc.get("model") and l_soc.get("model") and b_soc["model"] != l_soc["model"]:
        add("warn", "SOC_MODEL_DIFF",
            f"同平台但芯片型号不同：模板 {b_soc['model']} / 本机 {l_soc['model']}",
            "频点表可能不同，导入后建议复查频率上限")

    b_dev = (tgt.get("device") or {})
    l_dev = (fp.get("device") or {})
    if b_dev.get("model") and l_dev.get("model") and b_dev["model"] != l_dev["model"]:
        add("warn", "DEVICE_DIFF",
            f"机型不同：模板 {b_dev.get('brand','')} {b_dev['model']} / "
            f"本机 {l_dev.get('brand','')} {l_dev.get('model','')}")

    b_os = (tgt.get("os") or {})
    l_os = (fp.get("os") or {})
    if b_os.get("android") and l_os.get("android") and b_os["android"] != l_os["android"]:
        add("warn", "ANDROID_DIFF",
            f"Android 版本不同：模板 {b_os['android']} / 本机 {l_os['android']}",
            "跨版本节点位置可能变化，缺失节点会被跳过")

    # --- 路径可用性 ---
    aliases = profile.get("alias") or {}
    missing = [k for k, v in aliases.items() if not v or ".." in str(v)]
    if missing:
        add("block", "BAD_ALIAS", f"方案内存在非法别名：{', '.join(missing[:5])}")

    # --- 频点表比对 ---
    tables = _local_tables(fp)
    if tables and profile:
        local_all = _all_freqs(tables)
        local_max = max((t.get("max") or 0) for t in tables.values())
        unknown: list[int] = []
        above: list[int] = []
        for w in expand(profile):
            if "path" not in w:
                continue
            v = w["value"]
            if not is_freq_token(v):
                continue
            n = int(strip_flag(v))
            if n > local_max:
                above.append(n)
            elif n not in local_all:
                unknown.append(n)
        if above:
            add("warn", "FREQ_ABOVE",
                f"{len(above)} 处频率高于本机上限（{local_max}），实际会被内核钳制："
                f"{', '.join(str(x) for x in sorted(set(above))[:4])}",
                "不会变砖，但配置不会达到作者描述的效果")
        if unknown:
            add("warn", "FREQ_UNKNOWN",
                f"{len(unknown)} 处频率不在本机频点表中，将被就近钳制："
                f"{', '.join(str(x) for x in sorted(set(unknown))[:4])}")

    # --- 风险复核 ---
    an = bundle.get("analysis") or analyze(profile)
    if an.get("risk") == "high":
        highs = [i["message"] for i in an.get("items", []) if i.get("level") == "high"]
        add("confirm", "AGGRESSIVE",
            f"该方案含 {len(highs)} 项高风险写入（关闭限制器/改温控），可能导致过热锁核",
            "需用户明确确认后再执行")

    # --- 组件完整性 ---
    comps = (payload.get("components") or {})
    for k, enabled in comps.items():
        if not enabled:
            continue
        val = payload.get(k)
        if val in (None, [], {}, ""):
            add("info", "EMPTY_COMPONENT", f"组件 {k} 标记启用但内容为空，导入时跳过")

    levels = {i["level"] for i in issues}
    if "block" in levels:
        verdict = "block"
    elif "confirm" in levels:
        verdict = "confirm"
    else:
        verdict = "pass"
    return {"verdict": verdict, "issues": issues}
