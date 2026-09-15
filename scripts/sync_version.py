#!/usr/bin/env python3
"""同步 / 校验版本号，避免多处副本脱节。

版本号写在三个地方，历史上出现过脱节（版本文件停在旧版本，导致应用内更新永远不触发）：

  1. app/build.gradle.kts              —— 唯一权威来源
  2. docs/version.json                 —— 老客户端过渡 + 官网兜底数据源，见下
  3. app/release/output-metadata.json  —— 归档产物的元数据

关于 docs/version.json：它有两个用途，都不能删 ——

  ① 服务「还在跑 1.5.0 之前版本」的已安装客户端：它们仍会请求
     https://os233.github.io/WaterReminder/version.json（Pages 源设为 master 的 /docs 目录，
     该 URL 正好映射到 docs/version.json）。
  ② 官网（docs/assets/site.js）的静态兜底数据源：未认证的 Releases API 只有 60 次/小时，
     且配额按出口 IP 共享，共用网络下容易被别人的请求用光，API 失败时官网会退回来读它。
     所以它的 changelog 要认真写 —— API 失败时官网直接拿它当更新说明显示。

用法：

    python scripts/sync_version.py            # 按源码版本同步 2 和 3
    python scripts/sync_version.py --check    # 只校验，不写文件（CI 用）
    python scripts/sync_version.py --expect-tag v1.3.0   # 断言 tag 与 versionName 一致

只依赖标准库。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GRADLE_FILE = ROOT / "app" / "build.gradle.kts"
VERSION_JSON = ROOT / "docs" / "version.json"
METADATA_JSON = ROOT / "app" / "release" / "output-metadata.json"
RELEASE_DIR = ROOT / "app" / "release"


def rel(path: Path) -> str:
    """相对仓库根目录的路径，统一用正斜杠（Windows / Linux 输出一致）。"""
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return path.as_posix()


def read_source_version() -> tuple[int, str]:
    """从 app/build.gradle.kts 的 defaultConfig 里取 versionCode / versionName。"""
    text = GRADLE_FILE.read_text(encoding="utf-8")
    code = re.search(r"\bversionCode\s*=\s*(\d+)", text)
    name = re.search(r'\bversionName\s*=\s*"([^"]+)"', text)
    if not code or not name:
        sys.exit(f"[错误] 无法从 {rel(GRADLE_FILE)} 解析 versionCode / versionName")
    return int(code.group(1)), name.group(1)


def apk_file_name(version_name: str) -> str:
    return f"WaterReminder_v{version_name}_release.apk"


def rewrite_apk_url(url: str, version_name: str) -> str:
    """把 apkUrl 的版本段换成目标版本，同时兼容 Pages 与 GitHub Releases 两种形态。"""
    file_name = apk_file_name(version_name)
    if "/releases/download/" in url:
        head = url.split("/releases/download/")[0]
        return f"{head}/releases/download/v{version_name}/{file_name}"
    return url.rsplit("/", 1)[0] + "/" + file_name


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def dump_json(path: Path, data: dict) -> None:
    # newline="\n"：Windows 上文本模式默认会把 \n 写成 CRLF，而 .gitattributes 约定文本一律 LF。
    # 不加这个，每次同步后工作区副本都会变成 CRLF（入库虽会被规范化，但工作区与约定不一致）。
    path.write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def cmd_sync() -> int:
    code, name = read_source_version()
    vj = rel(VERSION_JSON)
    info = load_json(VERSION_JSON)
    changed: list[str] = []

    old_code = info.get("versionCode")
    if old_code is not None and code < int(old_code):
        sys.exit(
            f"[错误] {vj} 的 versionCode={old_code} 比源码的 {code} 还新，"
            "拒绝回退。请先确认 app/build.gradle.kts 的版本号。"
        )

    for key, value in (("versionCode", code), ("versionName", name)):
        if info.get(key) != value:
            changed.append(f"{vj}: {key} {info.get(key)!r} → {value!r}")
            info[key] = value

    url = info.get("apkUrl", "")
    new_url = rewrite_apk_url(url, name)
    if new_url != url:
        changed.append(f"{vj}: apkUrl → {new_url}")
        info["apkUrl"] = new_url

    if changed:
        dump_json(VERSION_JSON, info)

    if METADATA_JSON.exists():
        meta = load_json(METADATA_JSON)
        elements = meta.get("elements") or []
        if elements:
            element = elements[0]
            meta_changed = False
            for key, value in (
                ("versionCode", code),
                ("versionName", name),
                ("outputFile", apk_file_name(name)),
            ):
                if element.get(key) != value:
                    changed.append(f"{rel(METADATA_JSON)}: {key} {element.get(key)!r} → {value!r}")
                    element[key] = value
                    meta_changed = True
            if meta_changed:
                dump_json(METADATA_JSON, meta)

    print(f"源码版本：versionCode={code} versionName={name}")
    if changed:
        print("已同步：")
        for line in changed:
            print("  -", line)
    else:
        print("版本号已经一致，无需改动。")
    return 0


def cmd_check() -> int:
    """校验不变量。注意：允许版本文件落后于源码（开发中版本号先升是正常的），
    但不允许它超前，也不允许 apkUrl 指向一个不存在的产物。"""
    code, name = read_source_version()
    vj = rel(VERSION_JSON)
    problems: list[str] = []

    info = load_json(VERSION_JSON)
    vj_code = info.get("versionCode")
    vj_name = info.get("versionName")
    apk_url = info.get("apkUrl", "")

    if not isinstance(vj_code, int) or vj_code <= 0:
        problems.append(f"{vj} 的 versionCode 非法：{vj_code!r}")
    elif vj_code > code:
        problems.append(
            f"{vj} 的 versionCode={vj_code} 比源码的 {code} 还新"
            "（多半是升了它却忘了升 app/build.gradle.kts）"
        )

    if not vj_name or not re.fullmatch(r"\d+\.\d+\.\d+", str(vj_name)):
        problems.append(f"{vj} 的 versionName 非法：{vj_name!r}（期望 x.y.z）")

    if not apk_url.startswith("https://"):
        problems.append(f"apkUrl 必须是 https：{apk_url!r}")
    else:
        expected = apk_file_name(str(vj_name))
        actual = apk_url.rsplit("/", 1)[-1]
        if actual != expected:
            problems.append(
                f"apkUrl 文件名与 versionName 不匹配：URL 里是 {actual}，应为 {expected}"
            )
        if "/releases/download/" in apk_url:
            print("[提示] apkUrl 指向 GitHub Releases，请确认该 Release 已上传对应 asset。")
        elif not (RELEASE_DIR / actual).exists():
            problems.append(
                f"apkUrl 指向的 {rel(RELEASE_DIR / actual)} 不存在"
                "（Pages 上会 404，老版本客户端的更新会失败）"
            )

    if METADATA_JSON.exists():
        elements = load_json(METADATA_JSON).get("elements") or []
        if elements:
            element = elements[0]
            if element.get("versionCode") != vj_code or element.get("versionName") != vj_name:
                problems.append(
                    f"{rel(METADATA_JSON)} 的版本号（{element.get('versionCode')} / "
                    f"{element.get('versionName')}）与 {vj}（{vj_code} / {vj_name}）不一致"
                )

    print(f"源码版本：versionCode={code} versionName={name}")
    print(f"{vj}：versionCode={vj_code} versionName={vj_name}")

    if problems:
        print("\n[校验失败]")
        for p in problems:
            print("  -", p)
        return 1

    print("版本号校验通过。")
    return 0


def cmd_expect_tag(tag: str) -> int:
    _, name = read_source_version()
    expected = f"v{name}"
    if tag != expected:
        print(
            f"[错误] tag {tag} 与 app/build.gradle.kts 的 versionName {name} 不一致"
            f"（应为 {expected}），APK 文件名与 Release asset 会对不上。"
        )
        return 1
    print(f"tag {tag} 与 versionName {name} 一致。")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="同步 / 校验版本文件与源码版本号")
    parser.add_argument("--check", action="store_true", help="只校验不写文件")
    parser.add_argument("--expect-tag", metavar="TAG", help="断言 tag 与 versionName 一致")
    args = parser.parse_args()

    if args.expect_tag:
        return cmd_expect_tag(args.expect_tag)
    return cmd_check() if args.check else cmd_sync()


if __name__ == "__main__":
    sys.exit(main())
