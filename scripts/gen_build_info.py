#!/usr/bin/env python3
"""生成「关于」页的构建信息（NAV-03 / FR-v2.0-11）。

版本 / commit / 构建时间 / 依赖版本必须来自**构建产物**，不能手写进 Java 常量：
本脚本由 scripts/build.sh 在打包前调用，读 pom.xml + package.json + git HEAD，
写出 backend/src/main/resources/build-info.properties（该文件被 .gitignore 忽略）。

未跑构建时文件不存在，BuildInfoService 会显示 source=runtime 并在页面标注，
不伪装成真实构建信息。
"""
from __future__ import annotations

import json
import pathlib
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "backend" / "src" / "main" / "resources" / "build-info.properties"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def git_short_sha() -> str:
    try:
        out = subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"], cwd=ROOT, stderr=subprocess.DEVNULL
        )
        return out.decode().strip() or "unknown"
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def pom_entries() -> tuple[str, list[str]]:
    tree = ET.parse(ROOT / "backend" / "pom.xml")
    root = tree.getroot()
    version = root.findtext("m:version", default="unknown", namespaces=NS)
    parent_version = root.findtext("m:parent/m:version", default="", namespaces=NS)
    props = root.find("m:properties", NS)
    deps: list[str] = [f"robot-autotest:{version}"]
    if parent_version:
        deps.append(f"Spring Boot:{parent_version}")
    if props is not None:
        java_version = props.findtext("m:java.version", default="", namespaces=NS)
        if java_version:
            deps.append(f"Java:{java_version}")
    for node in root.findall("m:dependencies/m:dependency", NS):
        artifact = node.findtext("m:artifactId", default="", namespaces=NS)
        ver = node.findtext("m:version", default="", namespaces=NS)
        if artifact and ver and not ver.startswith("${"):
            deps.append(f"{artifact}:{ver}")
        if len(deps) >= 10:
            break
    return version, deps


def npm_entries() -> list[str]:
    data = json.loads((ROOT / "frontend" / "package.json").read_text(encoding="utf-8"))
    result: list[str] = []
    for section in ("dependencies", "devDependencies"):
        for name, version in data.get(section, {}).items():
            result.append(f"{name}:{version}")
    return result


def main() -> int:
    version, deps = pom_entries()
    deps.extend(npm_entries())
    # 去重保序，避免同名依赖在前后端各出现一次
    seen: set[str] = set()
    unique: list[str] = []
    for item in deps:
        if item not in seen:
            seen.add(item)
            unique.append(item)
    build_time = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    lines = [
        "# 由 scripts/gen_build_info.py 生成，勿手改、勿入库",
        f"app.version={version}",
        f"git.commit={git_short_sha()}",
        f"build.time={build_time}",
        "entry.port=8001",
        "backend.port=8081",
        "dev.port=5173",
        "dependencies=" + "|".join(unique),
    ]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"[build-info] {OUT.relative_to(ROOT)} · version={version} "
          f"commit={git_short_sha()} deps={len(unique)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
