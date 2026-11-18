#!/usr/bin/env python3
"""SceneBox 引擎 CLI（原型）。

  su -c "python3 cli.py doctor"
  su -c "python3 cli.py export --name '三角洲 均衡' --game 三角洲行动 --style balance"
  su -c "python3 cli.py preview out/xxx.sbox"
  su -c "python3 cli.py validate out/xxx.sbox"
  su -c "python3 cli.py snapshot save B --note 日常默认"
  su -c "python3 cli.py snapshot restore B"
"""

from __future__ import annotations

import argparse
import json
import os
import sys

import analyze
import sboxfile
import sceneread
import shell
import snapshot

DEFAULT_OUT = "/sdcard/DK/scenebox/out"

LEVEL_ICON = {"block": "⛔", "confirm": "⚠️ ", "warn": "🟡", "info": "ℹ️ "}


def cmd_doctor(a) -> int:
    print("=== SceneBox 环境自检 ===")
    print(f"root        : {shell.is_root()} (su 可用: {shell.su_available()})")
    d = sceneread.data_dir()
    print(f"Scene 数据  : {d or '未找到（未安装 Scene 或无权限）'}")
    fp = sceneread.fingerprint()
    print(f"机型        : {fp['device']['brand']} {fp['device']['model']} "
          f"({fp['device']['device']})")
    print(f"芯片        : {fp['soc']['manufacturer']} {fp['soc']['model']} "
          f"platform={fp['soc']['platform']}")
    print(f"系统        : Android {fp['os']['android']} (SDK {fp['os']['sdk']}) {fp['os']['rom']}")
    print(f"Scene       : daemon {fp['scene']['daemon']} / "
          f"配置版本 {fp['scene']['cloud_profile_version']} / {fp['scene']['working_mode']}")
    print(f"CPU policy  : {len(fp['cpu']['policies'])} 组")
    for k, v in fp["cpu"]["policies"].items():
        print(f"  {k:8s} {v['min']:>8d} ~ {v['max']:>8d}  ({len(v['avail'])} 个频点)")

    prof = sceneread.read_profile()
    print(f"调度方案    : {'有' if prof else '无'}"
          + (f" platform={prof.get('platform')} name={prof.get('platform_name')}" if prof else ""))
    if prof:
        an = analyze.analyze(prof)
        print(f"  presets={an['stats']['presets']} schemes={an['stats']['schemes']} "
              f"展开写入={an['stats']['writes']} 风险={an['risk']}")
    print(f"每应用档位  : {len(sceneread.read_prefs('powercfg'))} 条")
    print(f"游戏列表    : {len([1 for v in sceneread.read_prefs('games').values() if v])} 个")
    print(f"帧率覆盖    : {len(sceneread.read_refresh_rate())} 条")
    print(f"温控预设    : {len(sceneread.read_thermal_presets())} 套")
    return 0


def cmd_export(a) -> int:
    comps = None
    if a.only:
        comps = {c: c in a.only for c in sboxfile.COMPONENTS}
    elif a.skip:
        comps = {c: True for c in sboxfile.COMPONENTS}
        for s in a.skip:
            comps[s] = False
    bundle = sboxfile.build(name=a.name or "本机 Scene 调度备份", author=a.author or "",
                            description=a.desc or "", game=a.game or "",
                            style=a.style or "", components=comps)
    path = sboxfile.save(bundle, a.out or DEFAULT_OUT)
    an = bundle["analysis"]
    print(f"✅ 已导出 {path}")
    print(f"   包含写入 {an['stats']['writes']} 条 / 风险 {an['risk']} "
          f"/ 分析项 {len(an['items'])}")
    if an["risk"] == "high":
        for it in an["items"][:5]:
            print(f"   {LEVEL_ICON.get(it['level'],'')} {it['message']}")
    if a.preview:
        print()
        print(sboxfile.render_preview(bundle))
    return 0


def cmd_preview(a) -> int:
    bundle = sboxfile.load(a.file)
    ok, msg = sboxfile.verify_integrity(bundle)
    print(sboxfile.render_preview(bundle))
    print(f"\n■ 完整性  {'✅' if ok else '❌'} {msg}")
    if a.writes:
        print("\n■ 展开后的写入清单（前 40 条）")
        writes = analyze.expand(bundle.get("payload", {}).get("profile") or {})
        for w in writes[:40]:
            if "path" in w:
                print(f"    {w['source']:32s} {w['path']} <- {w['value']}")
            else:
                print(f"    {w['source']:32s} {w['op']} {' '.join(w.get('args', []))}")
        if len(writes) > 40:
            print(f"    …… 共 {len(writes)} 条")
    return 0 if ok else 4


def cmd_validate(a) -> int:
    bundle = sboxfile.load(a.file)
    fp = sceneread.fingerprint()
    res = analyze.validate(bundle, fp)
    v = res["verdict"]
    head = {"pass": "✅ 通过", "confirm": "⚠️ 需要确认", "block": "⛔ 拦截"}[v]
    print(f"=== 导入校验：{head} ===")
    print(f"模板 {bundle.get('meta',{}).get('name','')}  →  本机 {fp['device']['brand']} "
          f"{fp['device']['model']}")
    for it in res["issues"]:
        print(f"{LEVEL_ICON.get(it['level'],'')} [{it['code']}] {it['message']}")
        if it.get("fix"):
            print(f"        ↳ {it['fix']}")
    if not res["issues"]:
        print("（无异常）")
    print(f"\n结论：{ {'pass':'可以直接导入','confirm':'需用户二次确认后导入','block':'禁止导入'}[v] }")
    return {"pass": 0, "confirm": 2, "block": 3}[v]


def cmd_snapshot(a) -> int:
    if a.action == "save":
        snap = snapshot.save(a.label, a.note or "")
        print(f"✅ 快照 {a.label} 已保存：{snap['count']} 个节点")
        print(f"   {snap['_path']}")
        return 0
    if a.action == "list":
        rows = snapshot.list_snapshots()
        if not rows:
            print("（还没有快照）")
        for r in rows:
            print(f"  {r['label']:10s} {r['saved_at']}  {r['count']:4d} 节点  {r['note']}")
        return 0
    if a.action == "restore":
        res = snapshot.restore(a.label, dry_run=a.dry_run)
        if res.get("dry_run"):
            print(f"（dry-run）将恢复 {res['total']} 个节点")
            return 0
        print(f"✅ 恢复 {a.label}：写入 {res['written']}/{res['total']}，"
              f"回读校验 {res['checked']} 个")
        if res["failed"]:
            print(f"   跳过/失败 {len(res['failed'])} 个（节点不存在或只读）："
                  f"{', '.join(x.split('/')[-1] for x in res['failed'][:6])}")
        if res["mismatch"]:
            print(f"   ⚠️  {len(res['mismatch'])} 个节点写入后被内核改回："
                  f"{', '.join('/'.join(x.split('/')[-3:]) for x in res['mismatch'][:6])}")
        return 0
    if a.action == "diff":
        rows = snapshot.diff(a.label, a.label2)
        print(f"=== {a.label} vs {a.label2}：{len(rows)} 处不同 ===")
        for r in rows[:60]:
            print(f"  {r['path']}")
            print(f"    {a.label}: {r['a']}")
            print(f"    {a.label2}: {r['b']}")
        return 0
    return 1


def main(argv=None) -> int:
    p = argparse.ArgumentParser(prog="sb", description="SceneBox 引擎 CLI")
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("doctor", help="环境自检").set_defaults(func=cmd_doctor)

    e = sub.add_parser("export", help="读取 Scene 配置并打包为 .sbox")
    e.add_argument("--name")
    e.add_argument("--author")
    e.add_argument("--desc")
    e.add_argument("--game")
    e.add_argument("--style")
    e.add_argument("--out")
    e.add_argument("--only", nargs="*", help="只包含这些组件")
    e.add_argument("--skip", nargs="*", help="跳过这些组件")
    e.add_argument("--preview", action="store_true")
    e.set_defaults(func=cmd_export)

    pv = sub.add_parser("preview", help="导入前预览")
    pv.add_argument("file")
    pv.add_argument("--writes", action="store_true", help="列出展开后的写入")
    pv.set_defaults(func=cmd_preview)

    v = sub.add_parser("validate", help="按本机强校验")
    v.add_argument("file")
    v.set_defaults(func=cmd_validate)

    s = sub.add_parser("snapshot", help="调度快照 A/B")
    s.add_argument("action", choices=["save", "list", "restore", "diff"])
    s.add_argument("label", nargs="?", default="")
    s.add_argument("label2", nargs="?", default="")
    s.add_argument("--note", default="")
    s.add_argument("--dry-run", action="store_true")
    s.set_defaults(func=cmd_snapshot)

    a = p.parse_args(argv)
    try:
        return a.func(a)
    except FileNotFoundError as ex:
        print(f"❌ {ex}", file=sys.stderr)
        return 1
    except ValueError as ex:
        print(f"❌ {ex}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
