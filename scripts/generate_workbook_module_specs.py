#!/usr/bin/env python3
"""Generate documentation-first migration modules from the workbook.

The workbook is an input snapshot and is never copied into a generated module.
The generator deliberately records compatibility claims as unverified until a
later implementation phase pins official release notes, tags, commits, and
real-repository fixtures.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import html
import json
import re
from collections import Counter, defaultdict
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Iterable
from zipfile import ZipFile
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_WORKBOOK = ROOT / "开源软件升级.xlsx"
SPREADSHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
KNOWN_WORKBOOK_SHA256 = (
    "17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309"
)

LANGUAGE_CLASS = {
    "java": "java",
    "nodejs": "nodejs",
    "go": "go",
    "python": "python",
    "other": "other",
    "unknown": "other",
    "c/c++": "other",
}

# Workbook inspection found exactly these unambiguous Java aliases: the bare
# artifact and the Maven coordinate have the same language and target. No
# other same-looking name is merged without fixed package identity evidence.
SAFE_JAVA_ALIASES = {
    ("bcpkix-jdk18on", "1.81.1"): "org.bouncycastle:bcpkix-jdk18on",
    ("bcprov-jdk18on", "1.84"): "org.bouncycastle:bcprov-jdk18on",
    ("jetty-http", "12.0.34"): "org.eclipse.jetty:jetty-http",
    ("junrar", "7.5.10"): "com.github.junrar:junrar",
    ("kafka-clients", "4.1.2"): "org.apache.kafka:kafka-clients",
    ("log4j-1.2-api", "2.25.5"): (
        "org.apache.logging.log4j:log4j-1.2-api"
    ),
    ("log4j-core", "2.25.5"): "org.apache.logging.log4j:log4j-core",
    ("logback-core", "1.5.34"): "ch.qos.logback:logback-core",
    ("netty-codec-http", "4.1.136.Final"): "io.netty:netty-codec-http",
    ("netty-handler", "4.1.136.Final"): "io.netty:netty-handler",
    ("spring-boot", "3.5.15"): "org.springframework.boot:spring-boot",
    ("spring-boot-starter-actuator", "3.5.15"): (
        "org.springframework.boot:spring-boot-starter-actuator"
    ),
    ("spring-expression", "6.2.19"): (
        "org.springframework:spring-expression"
    ),
    ("spring-kafka", "3.3.15"): "org.springframework.kafka:spring-kafka",
    ("spring-security-core", "6.5.11"): (
        "org.springframework.security:spring-security-core"
    ),
    ("spring-security-web", "6.5.11"): (
        "org.springframework.security:spring-security-web"
    ),
    ("spring-web", "6.2.19"): "org.springframework:spring-web",
    ("spring-webflux", "6.2.19"): "org.springframework:spring-webflux",
    ("spring-webmvc", "6.2.19"): "org.springframework:spring-webmvc",
    ("tomcat-embed-core", "10.1.57"): (
        "org.apache.tomcat.embed:tomcat-embed-core"
    ),
    ("zookeeper", "3.8.6"): "org.apache.zookeeper:zookeeper",
}

# User directives supplement visibly truncated workbook cells without rewriting
# the Excel snapshot. They are task boundaries, not official compatibility
# evidence, and therefore cannot by themselves authorize AUTO.
TASK_DIRECTIVES = {
    (
        "org.apache.tomcat.embed:tomcat-embed-core",
        "10.1.57",
    ): [
        {
            "id": "U-001",
            "status": "user_directive",
            "sourceVersions": ["11.0.18", "11.0.21"],
            "relation": "conflict",
            "action": "mark",
            "statement": (
                "用户提供的完整在用版本清单表明，Excel 的截断单元格中还包含 "
                "11.0.18 和 11.0.21。10.1.57 低于 11.x，因此这两项不是升级边；"
                "必须保持原样并标记目标版本冲突（禁止降级）。"
            ),
        }
    ]
}

BUCKET_RULES = {
    "B0_无需升级": (
        "版本身份 / 所有权",
        "先区分源目标确实相同与版本无法解析；前者保持 NOOP，后者在真实 owner 上 MARK，"
        "不得把“可忽略”当作已经证明兼容。",
    ),
    "B1_Patch直升": (
        "补丁行为 / 安全 / 回归",
        "固定官方补丁说明和制品身份后才允许版本 AUTO；仍需验证安全修复、默认行为、"
        "序列化与协议回归。",
    ),
    "B2_Minor单包": (
        "弃用 / 默认值 / 配置 / 运行时",
        "同一主版本不等于绝对兼容；核查弃用删除、默认值、运行时基线、传递依赖和配置，"
        "只自动处理有固定上游证据的一一对应修改。",
    ),
    "B3_Minor联动": (
        "依赖族对齐 / BOM / 平台",
        "建立联动成员图并迁移 BOM、platform、parent 或共享 property 的真实 owner；"
        "禁止只改一个叶子依赖造成二进制或运行时漂移。",
    ),
    "B4_Major单包": (
        "公开 API / 配置 / 默认行为 / 运行时",
        "建立跨主版本兼容矩阵；覆盖删除或重命名 API、配置键和默认值、运行时基线、"
        "模块系统、数据格式与回滚，AUTO 仅限已证明等价的确定性修改。",
    ),
    "B5_Major联动": (
        "跨主版本 API + 依赖族联动",
        "同时执行主版本兼容矩阵和联动成员图；优先迁移 BOM/平台 owner，逐成员验证"
        "二进制、配置、协议和部署兼容性。",
    ),
    "B6_Multi-major单包": (
        "多主版本 API / 数据 / 协议 / 工具链",
        "按每个中间主版本逐跳建立证据和回归门禁，不把多跳升级伪装成一次兼容升级；"
        "需要分阶段处理源码、配置、数据、协议、运行时与回滚。",
    ),
    "B7": (
        "未定义",
        "工作簿没有定义 B7；若后续出现必须阻塞，等待分类规则，禁止自行发明语义。",
    ),
    "B8_语义不兼容": (
        "行为契约 / 语义 / 真实用例",
        "必须固定官方源码或发布说明并建立行为契约；语义敏感项默认 MARK/MANUAL，"
        "只有上下文无歧义且可证明等价的修改才能 AUTO。",
    ),
}

LANGUAGE_RULES = {
    "java": [
        "确认规范 Maven 坐标、relocation 关系，以及 parent/BOM/property/platform 的真实版本 owner。",
        "覆盖 Maven 与 Gradle；核查 JDK/字节码基线、包名和公开 API、反射、注解处理与 ServiceLoader。",
        "核查 JPMS/OSGi、shade/native-image、序列化/缓存/数据库数据，以及配置文件和框架联动。",
    ],
    "nodejs": [
        "确认规范 npm 包名；覆盖 package.json 的 workspace、dependencies/dev/peer/optional owner 与锁文件。",
        "核查 Node.js/TypeScript/框架 peer 基线、ESM/CJS 与 exports、类型声明、深导入和构建测试工具。",
        "核查浏览器/SSR、模板与样式、运行时默认值、bundler tree-shaking、配置和持久化数据。",
    ],
    "go": [
        "确认 Go module/import path；覆盖 go.mod 的 require/replace/exclude、vendor、workspace 和生成代码。",
        "核查语义导入主版本后缀、最低 Go/toolchain、build tags、cgo、context/error/API 和并发语义。",
        "核查 wire/config/持久化格式、代码生成器版本、跨模块最小版本选择和回滚。",
    ],
    "python": [
        "区分 PyPI distribution、import package 与命令入口；覆盖 requirements、constraints、pyproject 和锁文件。",
        "核查最低 Python、extras、同步/异步 API、typing、配置/环境变量、插件入口和依赖解析。",
        "核查 wheel/本地扩展 ABI、序列化/数据库数据、网络协议、部署镜像与回滚。",
    ],
    "other": [
        "先确认它是系统包、二进制、容器、服务、C/C++ 库、模型还是工具链；身份未确认前不生成猜测式 AUTO。",
        "核查包管理器 owner、ABI/SONAME、编译器和操作系统基线、动态链接、配置、数据与协议。",
        "对厂商后缀、不可解析版本和跨发布线目标保持 NOOP + MARK，并要求制品级验证和回滚方案。",
    ],
}


@dataclass(frozen=True)
class Edge:
    excel_row: int
    sequence: str
    software: str
    language: str
    source: str
    target: str
    services: str
    bucket: str
    difficulty: str
    note: str

    @property
    def language_class(self) -> str:
        key = self.language.lower()
        if key not in LANGUAGE_CLASS:
            raise ValueError(
                f"Unknown workbook language {self.language!r} "
                f"at Excel row {self.excel_row}"
            )
        return LANGUAGE_CLASS[key]


@dataclass
class ModuleSpec:
    identity: str
    ecosystem: str
    raw_language: str
    target: str
    edges: list[Edge]
    module_slug: str = ""
    readme_existed: bool = False
    pom_existed: bool = False
    manifest_existed: bool = False
    implementation_modules: tuple[str, ...] = ()

    @property
    def module_name(self) -> str:
        return f"migration-spec-{self.ecosystem}-{self.module_slug}"

    @property
    def module_dir(self) -> Path:
        return ROOT / "catalog" / self.ecosystem / self.module_slug


def cell_value(cell: ElementTree.Element, shared_strings: list[str]) -> str:
    value = cell.find(f"{{{SPREADSHEET_NS}}}v")
    raw = "" if value is None else value.text or ""
    cell_type = cell.attrib.get("t")
    if cell_type == "s" and raw:
        return shared_strings[int(raw)]
    if cell_type == "inlineStr":
        return "".join(
            text.text or ""
            for text in cell.iter(f"{{{SPREADSHEET_NS}}}t")
        )
    return raw


def parse_workbook(path: Path) -> list[Edge]:
    with ZipFile(path) as archive:
        strings_root = ElementTree.fromstring(
            archive.read("xl/sharedStrings.xml")
        )
        shared_strings = [
            "".join(
                text.text or ""
                for text in item.iter(f"{{{SPREADSHEET_NS}}}t")
            )
            for item in strings_root.findall(f"{{{SPREADSHEET_NS}}}si")
        ]
        sheet = ElementTree.fromstring(
            archive.read("xl/worksheets/sheet1.xml")
        )

    edges: list[Edge] = []
    rows = sheet.findall(
        f".//{{{SPREADSHEET_NS}}}sheetData/{{{SPREADSHEET_NS}}}row"
    )
    if not rows:
        raise ValueError("Workbook sheet1 has no rows")
    header_values: dict[str, str] = {}
    for cell in rows[0].findall(f"{{{SPREADSHEET_NS}}}c"):
        reference = cell.attrib.get("r", "")
        column = "".join(
            character for character in reference if character.isalpha()
        )
        header_values[column] = cell_value(cell, shared_strings).strip()
    expected_headers = {
        "A": "序号",
        "B": "软件名称",
        "C": "语言",
        "D": "原始版本",
        "E": "目标版本",
        "F": "涉及微服务数",
        "G": "分桶",
        "H": "升级难度",
        "I": "备注",
    }
    if header_values != expected_headers:
        raise ValueError(
            f"Unexpected workbook headers: {header_values!r}"
        )
    for row in rows[1:]:
        values: dict[str, str] = {}
        for cell in row.findall(f"{{{SPREADSHEET_NS}}}c"):
            reference = cell.attrib.get("r", "")
            column = "".join(character for character in reference if character.isalpha())
            values[column] = cell_value(cell, shared_strings).strip()
        if not values.get("B"):
            continue
        edges.append(
            Edge(
                excel_row=int(row.attrib["r"]),
                sequence=values.get("A", ""),
                software=values["B"],
                language=values.get("C", "unknown"),
                source=values.get("D", ""),
                target=values.get("E", ""),
                services=values.get("F", ""),
                bucket=values.get("G", ""),
                difficulty=values.get("H", ""),
                note=values.get("I", ""),
            )
        )
    return edges


def safe_slug(value: str, maximum: int = 96) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", value.strip().lower()).strip("-")
    slug = slug or "software"
    if len(slug) <= maximum:
        return slug
    digest = hashlib.sha256(slug.encode("utf-8")).hexdigest()[:12]
    return f"{slug[:maximum - 13].rstrip('-')}-{digest}"


def canonical_identity(edge: Edge) -> str:
    software = edge.software.strip()
    if edge.language_class == "java":
        return SAFE_JAVA_ALIASES.get(
            (software, edge.target),
            software,
        )
    return software


def identity_slug(identity: str, ecosystem: str, raw_language: str) -> str:
    if ecosystem == "java":
        prefix = "maven-" if ":" in identity else "bare-"
        return safe_slug(prefix + identity)
    if ecosystem == "python":
        # PEP 503 canonicalization is a useful directory identity, while the
        # exact workbook spelling remains in README/migration.yaml.
        return safe_slug("pypi-" + re.sub(r"[-_.]+", "-", identity.lower()))
    if ecosystem == "go":
        # Keep the entire module/import path, including semantic /vN suffixes.
        return safe_slug(identity)
    if ecosystem == "nodejs":
        return safe_slug(identity.removeprefix("@"))
    # Preserve the workbook language as an explicit qualifier because this
    # catalog class contains system packages, C/C++, unknowns, and tools.
    return safe_slug(f"{raw_language}-{identity}")


def version_slug(version: str) -> str:
    return safe_slug(version, maximum=48)


def build_module_specs(edges: list[Edge]) -> list[ModuleSpec]:
    by_identity: dict[tuple[str, str, str], list[Edge]] = defaultdict(list)
    for edge in edges:
        key = (
            canonical_identity(edge),
            edge.language.strip().lower() or "unknown",
            edge.target,
        )
        by_identity[key].append(edge)

    specs: list[ModuleSpec] = []
    for (identity, raw_language, target), group_edges in sorted(
        by_identity.items()
    ):
        ecosystem = group_edges[0].language_class
        specs.append(
            ModuleSpec(
                identity=identity,
                ecosystem=ecosystem,
                raw_language=raw_language,
                target=target,
                edges=sorted(group_edges, key=lambda edge: edge.excel_row),
            )
        )

    by_base: dict[tuple[str, str], int] = defaultdict(int)
    for spec in specs:
        base = identity_slug(
            spec.identity,
            spec.ecosystem,
            spec.raw_language,
        )
        by_base[(spec.ecosystem, base)] += 1

    drafts: list[tuple[ModuleSpec, str]] = []
    for spec in specs:
        base = identity_slug(
            spec.identity,
            spec.ecosystem,
            spec.raw_language,
        )
        candidate = base
        if by_base[(spec.ecosystem, base)] > 1:
            candidate = safe_slug(
                f"{base}-to-{version_slug(spec.target)}",
                maximum=112,
            )
        drafts.append((spec, candidate))

    draft_counts = Counter(
        (spec.ecosystem, candidate) for spec, candidate in drafts
    )
    used_paths: set[tuple[str, str]] = set()
    for spec, candidate in drafts:
        if draft_counts[(spec.ecosystem, candidate)] > 1:
            digest_input = "|".join(
                [
                    spec.identity,
                    spec.raw_language,
                    spec.target,
                ]
            )
            digest = hashlib.sha256(
                digest_input.encode("utf-8")
            ).hexdigest()[:12]
            candidate = safe_slug(f"{candidate}-{digest}", maximum=112)
        path_key = (spec.ecosystem, candidate)
        if path_key in used_paths:
            raise ValueError(f"Unable to assign unique module slug for {spec}")
        spec.module_slug = candidate
        used_paths.add(path_key)
        spec.implementation_modules = find_implementation_modules(spec)
    return specs


@lru_cache(maxsize=1)
def active_reactor_modules() -> frozenset[str]:
    root = ElementTree.parse(ROOT / "pom.xml").getroot()
    return frozenset(
        (element.text or "").strip()
        for element in root.iter()
        if element.tag.endswith("}module") and (element.text or "").strip()
    )


def find_implementation_modules(spec: ModuleSpec) -> tuple[str, ...]:
    # Do not infer implementations from a shared artifact suffix: that caused
    # cross-ecosystem false positives (for example Java/Node/Go "bson"). Until
    # modules declare canonical catalog IDs, only the 21 explicit Java alias
    # groups can be linked deterministically.
    names = {edge.software for edge in spec.edges}
    candidates = {
        f"rewrite-{safe_slug(bare)}-upgrade"
        for (bare, target), coordinate in SAFE_JAVA_ALIASES.items()
        if spec.target == target
        and spec.identity == coordinate
        and {bare, coordinate}.issubset(names)
    }
    return tuple(sorted(candidates & active_reactor_modules()))


def markdown(value: str) -> str:
    return value.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "<br>")


def inline_code(value: str) -> str:
    return f"`{value.replace('`', 'ˋ')}`"


def leading_version(value: str) -> tuple[int, ...] | None:
    match = re.match(r"^[vV]?(\d+(?:\.\d+)*)", value.strip())
    if not match:
        return None
    return tuple(int(part) for part in match.group(1).split("."))


def compare_versions(left: tuple[int, ...], right: tuple[int, ...]) -> int:
    length = max(len(left), len(right))
    padded_left = left + (0,) * (length - len(left))
    padded_right = right + (0,) * (length - len(right))
    return (padded_left > padded_right) - (padded_left < padded_right)


def edge_disposition(edge: Edge) -> tuple[str, str, str]:
    if edge.source == edge.target:
        return (
            "same",
            "noop",
            "源与目标逐字相同；保持 NOOP，不把相同行伪装成升级。",
        )
    if any(token in edge.source for token in ("...", "…", "（共", "(共")):
        return (
            "unknown",
            "mark",
            "源版本单元格是截断或聚合显示，不是可执行配方的原子版本白名单。",
        )
    source_version = leading_version(edge.source)
    target_version = leading_version(edge.target)
    if source_version is None or target_version is None:
        return (
            "unknown",
            "mark",
            "当前只做保守数字前缀比较；版本体系或制品身份未验证，禁止猜测式 AUTO。",
        )
    comparison = compare_versions(source_version, target_version)
    if comparison > 0:
        return (
            "conflict",
            "mark",
            "保守数字比较显示源版本高于目标；保持原文并标记目标版本冲突（禁止降级）。",
        )
    if comparison < 0:
        return (
            "upgrade-candidate",
            "mark",
            "表格方向看似升级，但制品身份和官方兼容证据未固定；当前仅作为候选边。",
        )
    return (
        "unknown",
        "mark",
        "数字前缀相同但限定符或版本文字不同；未证明顺序前保持原样并 MARK。",
    )


def readable_names(spec: ModuleSpec) -> str:
    names = sorted({edge.software for edge in spec.edges})
    if len(names) <= 3:
        return " / ".join(names)
    return f"{' / '.join(names[:3])} 等 {len(names)} 个表格标识"


def render_readme(spec: ModuleSpec, workbook_hash: str) -> str:
    names = sorted({edge.software for edge in spec.edges})
    raw_languages = sorted({edge.language for edge in spec.edges})
    buckets = sorted({edge.bucket for edge in spec.edges})
    difficulties = sorted({edge.difficulty for edge in spec.edges})
    max_services = max(
        (int(edge.services) for edge in spec.edges if edge.services.isdigit()),
        default=0,
    )

    facts = "\n".join(
        "| {row} | {sequence} | {software} | {language} | {source} | {target} | "
        "{services} | {bucket} | {difficulty} | {relation}/{action} | {note} |".format(
            row=edge.excel_row,
            sequence=markdown(edge.sequence),
            software=inline_code(markdown(edge.software)),
            language=markdown(edge.language),
            source=inline_code(markdown(edge.source)),
            target=inline_code(markdown(edge.target)),
            services=markdown(edge.services),
            bucket=markdown(edge.bucket),
            difficulty=markdown(edge.difficulty),
            relation=edge_disposition(edge)[0],
            action=edge_disposition(edge)[1],
            note=markdown(edge.note),
        )
        for edge in spec.edges
    )

    incompatibility_groups: dict[tuple[str, str, str], list[str]] = defaultdict(list)
    for edge in spec.edges:
        relation, action, reason = edge_disposition(edge)
        incompatibility_groups[
            (edge.bucket, edge.difficulty, edge.note)
        ].append(
            f"Excel #{edge.excel_row} {edge.source} "
            f"[{relation}/{action}: {reason}]"
        )
    incompatibilities: list[str] = []
    for index, ((bucket, difficulty, note), sources) in enumerate(
        sorted(incompatibility_groups.items()), start=1
    ):
        dimension, treatment = BUCKET_RULES.get(
            bucket,
            (
                "未定义分类",
                "保持 NOOP + MARK，等待项目负责人定义分类与官方证据。",
            ),
        )
        source_list = "<br>".join(
            markdown(source) for source in sorted(set(sources))
        )
        incompatibilities.append(
            f"| C-{index:03d} | {markdown(dimension)} | {source_list} → "
            f"{inline_code(spec.target)} | {markdown(note)} | "
            f"`UNVERIFIED` | {markdown(treatment)} |"
        )

    language_rules = "\n".join(
        f"- {rule}" for rule in LANGUAGE_RULES[spec.ecosystem]
    )
    sources = ", ".join(
        inline_code(source)
        for source in sorted({edge.source for edge in spec.edges})
    )
    upgrade_candidates = sorted(
        {
            edge.source
            for edge in spec.edges
            if edge_disposition(edge)[0] == "upgrade-candidate"
        }
    )
    noop_versions = sorted(
        {
            edge.source
            for edge in spec.edges
            if edge_disposition(edge)[0] == "same"
        }
    )
    conflict_versions = sorted(
        {
            edge.source
            for edge in spec.edges
            if edge_disposition(edge)[0] == "conflict"
        }
    )
    unresolved_versions = sorted(
        {
            edge.source
            for edge in spec.edges
            if edge_disposition(edge)[0] == "unknown"
        }
    )
    coordinates = "<br>".join(inline_code(name) for name in names)
    implementation_modules = (
        ", ".join(inline_code(name) for name in spec.implementation_modules)
        if spec.implementation_modules
        else "`NONE`（尚无已识别的顶层实现模块）"
    )
    task_directives = TASK_DIRECTIVES.get(
        (spec.identity, spec.target),
        [],
    )
    task_directive_section = ""
    if task_directives:
        directive_rows = "\n".join(
            "| {id} | {versions} | {relation}/{action} | {statement} |".format(
                id=directive["id"],
                versions=", ".join(
                    inline_code(version)
                    for version in directive["sourceVersions"]
                ),
                relation=directive["relation"],
                action=directive["action"],
                statement=directive["statement"],
            )
            for directive in task_directives
        )
        task_directive_section = f"""

## 用户任务边界补充

本节补充用户对截断 Excel 单元格给出的明确版本边界。它属于 `USER_DIRECTIVE`，
不是官方兼容性证据，因此只能收紧 AUTO，不能放宽 AUTO。

| ID | 补充源版本 | 方向/动作 | 任务边界 |
| --- | --- | --- | --- |
{directive_rows}
"""

    def version_list(values: list[str]) -> str:
        if not values:
            return "`NONE`"
        return ", ".join(inline_code(value) for value in values)

    return f"""# {readable_names(spec)} 升级规格

> 规格状态：`COMPLETE`；证据状态：`PENDING`；自动化状态：`CATALOG_ONLY`。
> 本 README 已完成工作簿事实、禁止降级边界、不兼容点分类和后续配方验收契约；
> 它不声称尚未固定官方证据的具体 API 已得到确认。
> catalog 本身不包含配方代码；现有候选实现也将在全量规格覆盖完成后逐模块核验和完善。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/{spec.ecosystem}/{spec.module_slug}` |
| Maven artifactId | `{spec.module_name}` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | {coordinates} |
| Catalog canonical identity | `{spec.identity}`（`UNVERIFIED`，只用于避免目录碰撞） |
| 归一语言类 | `{spec.ecosystem}` |
| Excel 原始语言 | {", ".join(inline_code(language) for language in raw_languages)} |
| 目标版本 | `{spec.target}` |
| Excel 迁移边 | {len(spec.edges)} |
| 涉及微服务数 | 最大可见值 `{max_services}`；不同版本行不累加 |
| 分桶 | {", ".join(inline_code(bucket) for bucket in buckets)} |
| 难度 | {", ".join(inline_code(value) for value in difficulties)} |
| 工作簿 SHA-256 | `{workbook_hash}` |
| 候选实现模块 | {implementation_modules} |

## Excel 事实快照

本节逐字记录表格，不把自动分桶、难度或备注提升为官方兼容性结论。厂商后缀、
截断显示、无法解析值和疑似跨发布线目标均原样保留。

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 保守方向/动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
{facts}

## 升级方向与禁止降级

- 表格原始源版本记录（不是 AUTO 白名单）：{sources}。
- 升级候选边：{version_list(upgrade_candidates)}；在 E-001～E-003 完成前仍保持 `MARK`。
- 相同版本 NOOP：{version_list(noop_versions)}。
- 潜在降级冲突：{version_list(conflict_versions)}。
- 截断、聚合或无法可靠比较：{version_list(unresolved_versions)}。
- 任何高于目标的版本、更新发布线或无法可靠比较的厂商版本必须保持字节级不变，并在
  真实依赖 owner 上标记 `目标版本冲突（禁止降级）`；本项目不存在回退路径。
- 表外低版本、动态版本、范围、变量、BOM/platform、parent、catalog、workspace、
  constraints 和锁文件不能被猜测式改写；应定位并迁移真正的版本 owner。
- 若同一模块列出多个坐标或别名，配方必须分别证明身份；在官方 relocation 证据固定前，
  不得因为 artifact 名相同而跨 group、生态或发行渠道改坐标。
{task_directive_section}

## 不兼容点规格

| ID | 维度 | 适用迁移边 | Excel 提示 | 官方确认事实 | 处置契约 |
| --- | --- | --- | --- | --- | --- |
{chr(10).join(incompatibilities)}

`UNVERIFIED` 表示 Excel 提示已进入规格，但尚未用不可变的官方 tag/commit、发布说明和
制品元数据完成验证。此时允许 README 和精确 MARK 设计，不允许据此发明 API AUTO。

### `{spec.ecosystem}` 生态最低核查项

{language_rules}

## 证据台账

| Claim ID | 待证明事项 | 状态 | 固定官方证据 | 形成 AUTO 的条件 |
| --- | --- | --- | --- | --- |
| E-001 | 包/坐标身份、源版本和目标制品身份 | `UNVERIFIED` | 后续固定官网、registry/repository 元数据与 SHA | 身份无歧义且目标确为升级 |
| E-002 | 每条迁移边的 API、配置和默认行为变化 | `UNVERIFIED` | 后续固定 release notes、迁移指南、tag/commit diff | 存在一一对应且语义等价的变换 |
| E-003 | 真实工程中的用法和负例 | `UNVERIFIED` | 后续固定真实仓库 commit、路径、许可证与裁剪说明 | 正例、负例和上下文边界均可复现 |

真实仓库只能证明“用法存在”，不能替代官方兼容性证据。推断必须显式标为
`INFERENCE`；只有固定上游证据支持的事实才能改为 `VERIFIED`。

## 后续 OpenRewrite 配方契约

### AUTO

- 当前阶段 AUTO 白名单为空；只有 E-001～E-003 变为 `VERIFIED` 后，升级候选边才可逐项进入；
- 只处理经验证的原子源版本、明确坐标和当前文件拥有的标准依赖声明；
- 更高版本永不降级，表外版本、变体和外部 owner 永不猜测；
- 只实现有官方源码证明、上下文无歧义、行为等价且可幂等运行的 AST/配置修改；
- 保留 scope、classifier/type、optional、exclusions、workspace/profile 和相邻内容。

### MARK

- 在具体依赖、属性、BOM/platform、调用、类型、配置键或资源节点标记未决事项；
- marker 必须说明业务 owner 需要作出的决定、所需证据和验收方法；
- 不用文件级泛化告警代替精确定位，也不把 README 文字伪装成已执行迁移。

### MANUAL

- 运行时流量、安全策略、数据和 wire format、集群滚动策略、原生 ABI、性能容量、
  外部服务兼容性与回滚均由业务证据决定；
- 无法通过静态上下文证明安全的语义变换保持原样。

## 测试与真实用例验收

- 每个经验证的升级候选源版本才要求 AUTO 正例；目标/相同行为 NOOP；
- 冲突、未知、截断和聚合版本保持不变并 MARK；所有更高版本和更高发布线验证禁止降级；
- 覆盖对应生态的直接声明、共享 owner、BOM/platform/workspace、动态值、范围、锁文件和变体；
- 覆盖同名业务符号、相似坐标、注释/字符串、生成目录、缓存和安装产物负例；
- 每项 AUTO 有 before/after、类型或结构归因、两轮幂等和 aggregate 顺序测试；
- 固定真实仓库 commit 与文件路径，记录裁剪内容；真实夹具不能取代官方差异证据；
- 最终执行编译、单元/集成、行为、安全、性能、数据兼容、部署和回滚门禁。

## 当前阶段结论

本模块的不兼容点文档规格已经建立；官方证据、真实仓库夹具和可执行配方属于下一阶段。
在 E-001～E-003 完成前，除严格版本所有权和禁止降级守卫外，不批准猜测式 AUTO。
"""


def render_pom(spec: ModuleSpec) -> str:
    display = readable_names(spec)
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.huawei.clouds.openrewrite</groupId>
    <artifactId>migration-catalog-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>
  <artifactId>{html.escape(spec.module_name)}</artifactId>
  <packaging>pom</packaging>
  <name>{html.escape(display)} migration specification</name>
  <description>Documentation-first incompatibility specification for the workbook migration to {html.escape(spec.target)}.</description>
  <properties>
    <migration.spec.phase>documentation</migration.spec.phase>
  </properties>
</project>
"""


def render_catalog_parent() -> str:
    return """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.huawei.clouds.openrewrite</groupId>
    <artifactId>openrewrite-migration-recipes</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>
  <artifactId>migration-catalog-parent</artifactId>
  <packaging>pom</packaging>
  <name>Migration specification catalog parent</name>
  <description>Non-reactor parent for documentation-first migration specifications.</description>
</project>
"""


def render_manifest(spec: ModuleSpec, workbook_hash: str) -> str:
    edges = []
    incompatibilities = []
    auto_candidates: list[str] = []
    noop_versions: list[str] = []
    conflict_versions: list[str] = []
    unresolved_versions: list[str] = []
    for index, edge in enumerate(spec.edges, start=1):
        relation, action, reason = edge_disposition(edge)
        atomic = not any(
            token in edge.source for token in ("...", "…", "（共", "(共")
        )
        if relation == "upgrade-candidate":
            auto_candidates.append(edge.source)
        elif relation == "same":
            noop_versions.append(edge.source)
        elif relation == "conflict":
            conflict_versions.append(edge.source)
        else:
            unresolved_versions.append(edge.source)
        dimension, treatment = BUCKET_RULES.get(
            edge.bucket,
            (
                "未定义分类",
                "保持 NOOP + MARK，等待项目负责人定义分类与官方证据。",
            ),
        )
        edges.append(
            {
                "excelRow": edge.excel_row,
                "sequence": edge.sequence,
                "software": edge.software,
                "rawLanguage": edge.language,
                "source": edge.source,
                "target": edge.target,
                "services": edge.services,
                "bucket": edge.bucket,
                "difficulty": edge.difficulty,
                "note": edge.note,
                "sourceAtomic": atomic,
                "direction": {
                    "relation": relation,
                    "action": action,
                    "comparator": "conservative-numeric-prefix-v1",
                    "reason": reason,
                },
            }
        )
        incompatibilities.append(
            {
                "id": f"C-{index:03d}",
                "edgeRows": [edge.excel_row],
                "dimension": dimension,
                "excelNoteStatus": "table_fact",
                "evidenceStatus": "unverified",
                "disposition": action,
                "treatment": treatment,
                "recipe": None,
                "tests": [],
            }
        )

    document = {
        "$schema": "../../schema/migration.schema.json",
        "schemaVersion": 1,
        "module": {
            "id": spec.module_name,
            "catalogPath": (
                f"catalog/{spec.ecosystem}/{spec.module_slug}"
            ),
            "languageProfile": spec.ecosystem,
            "rawIdentifiers": sorted(
                {edge.software for edge in spec.edges}
            ),
            "rawLanguages": sorted(
                {edge.language for edge in spec.edges}
            ),
            "canonicalIdentity": {
                "value": spec.identity,
                "status": "unverified",
                "evidence": [],
            },
            "targetVersion": spec.target,
            "candidateImplementationModules": list(
                spec.implementation_modules
            ),
        },
        "workbook": {
            "sha256": workbook_hash,
            "edges": edges,
        },
        "taskDirectives": TASK_DIRECTIVES.get(
            (spec.identity, spec.target),
            [],
        ),
        "policy": {
            "allowDowngrade": False,
            "conflictMarker": "目标版本冲突（禁止降级）",
            "autoSourceWhitelist": [],
            "upgradeCandidatesPendingEvidence": sorted(
                set(auto_candidates)
            ),
            "noopVersions": sorted(set(noop_versions)),
            "conflictVersions": sorted(set(conflict_versions)),
            "unresolvedVersions": sorted(set(unresolved_versions)),
        },
        "evidence": [
            {
                "id": "E-001",
                "claim": "制品身份、源版本与目标制品身份",
                "status": "unverified",
                "officialSources": [],
                "realFixtures": [],
            },
            {
                "id": "E-002",
                "claim": "迁移边的 API、配置与默认行为变化",
                "status": "unverified",
                "officialSources": [],
                "realFixtures": [],
            },
            {
                "id": "E-003",
                "claim": "真实工程用法、负例与行为验收",
                "status": "unverified",
                "officialSources": [],
                "realFixtures": [],
            },
        ],
        "incompatibilities": incompatibilities,
        "status": {
            "spec": "complete",
            "evidence": "pending",
            "automation": "catalog-only",
        },
    }
    # JSON is a strict YAML 1.2 subset. Using it avoids an optional PyYAML
    # dependency while keeping the manifest consumable by YAML tooling.
    return json.dumps(document, ensure_ascii=False, indent=2) + "\n"


def render_schema() -> str:
    schema = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": "migration.schema.json",
        "title": "Documentation-first migration specification",
        "type": "object",
        "required": [
            "schemaVersion",
            "module",
            "workbook",
            "policy",
            "evidence",
            "incompatibilities",
            "status",
        ],
        "properties": {
            "schemaVersion": {"const": 1},
            "module": {
                "type": "object",
                "required": [
                    "id",
                    "catalogPath",
                    "languageProfile",
                    "rawIdentifiers",
                    "canonicalIdentity",
                    "targetVersion",
                ],
            },
            "workbook": {
                "type": "object",
                "required": ["sha256", "edges"],
            },
            "policy": {
                "type": "object",
                "required": [
                    "allowDowngrade",
                    "conflictMarker",
                    "autoSourceWhitelist",
                ],
            },
            "evidence": {"type": "array"},
            "incompatibilities": {"type": "array"},
            "status": {
                "type": "object",
                "required": ["spec", "evidence", "automation"],
            },
        },
    }
    return json.dumps(schema, ensure_ascii=False, indent=2) + "\n"


def render_catalog_readme(
    specs: list[ModuleSpec],
    edges: list[Edge],
    workbook_hash: str,
) -> str:
    counts = Counter(spec.ecosystem for spec in specs)
    count_lines = "\n".join(
        f"- `{ecosystem}`：{counts[ecosystem]} 个规格模块"
        for ecosystem in sorted(counts)
    )
    return f"""# Migration specification catalog

该目录保存 `开源软件升级.xlsx` 的文档优先迁移规格，不保存工作簿本体。

- 工作簿 SHA-256：`{workbook_hash}`
- Excel 数据行：{len(edges)}
- 规格模块：{len(specs)}
- 每个叶子模块固定包含 `README.md`、`migration.yaml` 和文档型 `pom.xml`。
- `catalog/pom.xml` 是非聚合 parent，不含 `<modules>`；catalog 不进入默认 Maven reactor。
- 规格、证据和自动化状态相互独立；README 完成不表示配方已经实现。

{count_lines}

完整映射见 [`docs/workbook-module-index.md`](../docs/workbook-module-index.md)，
机器可读映射见 [`docs/workbook-module-index.csv`](../docs/workbook-module-index.csv)。
"""


def render_index(
    specs: list[ModuleSpec],
    edges: list[Edge],
    workbook_hash: str,
) -> str:
    raw_names = {edge.software for edge in edges}
    rows = []
    for spec in specs:
        languages = ", ".join(sorted({edge.language for edge in spec.edges}))
        names = "<br>".join(
            inline_code(name) for name in sorted({edge.software for edge in spec.edges})
        )
        implementation = (
            "<br>".join(
                inline_code(name) for name in spec.implementation_modules
            )
            if spec.implementation_modules
            else "`NONE`"
        )
        rows.append(
            f"| [{spec.module_name}](../catalog/{spec.ecosystem}/"
            f"{spec.module_slug}/README.md) | "
            f"{names} | `{spec.ecosystem}` ({markdown(languages)}) | "
            f"`{markdown(spec.target)}` | {len(spec.edges)} | "
            f"`COMPLETE/PENDING/CATALOG_ONLY` | {implementation} |"
        )
    return f"""# 开源软件升级工作簿模块索引

此索引由 [`scripts/generate_workbook_module_specs.py`](../scripts/generate_workbook_module_specs.py)
从本地工作簿生成。工作簿本身不提交到仓库。

## 覆盖结论

- 工作簿 SHA-256：`{workbook_hash}`
- 数据行：**{len(edges)}**
- 原始软件名称：**{len(raw_names)}**
- 规范模块：**{len(specs)}**
- 每一数据行恰好映射到一个模块；同名但语言或目标冲突的记录拆分为独立模块。
- 仅 21 组 Java 裸 artifact 与同目标 Maven 坐标按显式白名单归入同一规格；
  其 canonical identity 仍为 `UNVERIFIED`，完成官方身份取证前不产生 AUTO。
- 新建文档模块使用 `packaging=pom`，暂不加入根 reactor；进入可执行配方阶段后才改为
  JAR 并逐个加入 reactor，避免近两千个文档占位模块拖慢本地构建和 CI。
- 状态顺序为 `规格/证据/自动化`；本轮统一为
  `COMPLETE/PENDING/CATALOG_ONLY`。
- 候选实现模块只表示目录名和表格标识匹配，后续仍须校验配方覆盖，不等于已经实现。

## 模块映射

| 模块 | Excel 软件标识 | 归一语言（原始语言） | 目标 | 迁移边 | 状态 | 候选实现模块 |
| --- | --- | --- | --- | ---: | --- | --- |
{chr(10).join(rows)}
"""


def write_csv(path: Path, specs: Iterable[ModuleSpec]) -> None:
    with path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(
            [
                "module",
                "software_names",
                "ecosystem",
                "raw_languages",
                "target",
                "edge_count",
                "excel_rows",
                "spec_status",
                "evidence_status",
                "automation_status",
                "candidate_implementation_modules",
            ]
        )
        for spec in specs:
            writer.writerow(
                [
                    spec.module_name,
                    " | ".join(sorted({edge.software for edge in spec.edges})),
                    spec.ecosystem,
                    " | ".join(sorted({edge.language for edge in spec.edges})),
                    spec.target,
                    len(spec.edges),
                    " | ".join(str(edge.excel_row) for edge in spec.edges),
                    "complete",
                    "pending",
                    "catalog-only",
                    " | ".join(spec.implementation_modules),
                ]
            )


def validate_safe_aliases(edges: list[Edge]) -> None:
    by_name: dict[str, set[tuple[str, str]]] = defaultdict(set)
    for edge in edges:
        by_name[edge.software].add(
            (edge.language.strip().lower(), edge.target)
        )
    for (bare, target), coordinate in SAFE_JAVA_ALIASES.items():
        bare_identity = by_name.get(bare, set())
        coordinate_identity = by_name.get(coordinate, set())
        if (
            bare_identity != coordinate_identity
            or len(bare_identity) != 1
            or next(iter(bare_identity))[0] != "java"
            or next(iter(bare_identity))[1] != target
        ):
            raise ValueError(
                "Safe Java alias no longer has one matching language/target "
                f"identity: {bare!r} -> {coordinate!r}; "
                f"bare={bare_identity}, coordinate={coordinate_identity}"
            )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--workbook", type=Path, default=DEFAULT_WORKBOOK)
    parser.add_argument(
        "--apply",
        action="store_true",
        help=(
            "Write missing catalog README/POM/manifest files and generated "
            "indexes."
        ),
    )
    parser.add_argument(
        "--refresh-readme",
        action="append",
        default=[],
        metavar="MODULE",
        help="Regenerate a named module README even when it already exists.",
    )
    parser.add_argument(
        "--refresh-all",
        action="store_true",
        help=(
            "Regenerate every generator-owned catalog README, POM, and "
            "manifest. Use only for intentional catalog-wide template changes."
        ),
    )
    args = parser.parse_args()

    workbook = args.workbook.resolve()
    workbook_hash = hashlib.sha256(workbook.read_bytes()).hexdigest()
    edges = parse_workbook(workbook)
    validate_safe_aliases(edges)
    unknown_buckets = sorted(
        {edge.bucket for edge in edges if edge.bucket not in BUCKET_RULES}
    )
    if unknown_buckets:
        raise ValueError(f"Unknown workbook buckets: {unknown_buckets}")
    unknown_difficulties = sorted(
        {
            edge.difficulty
            for edge in edges
            if edge.difficulty not in {"低", "中", "高"}
        }
    )
    if unknown_difficulties:
        raise ValueError(
            f"Unknown workbook difficulties: {unknown_difficulties}"
        )
    specs = build_module_specs(edges)

    assigned_rows = [edge.excel_row for spec in specs for edge in spec.edges]
    workbook_rows = Counter(edge.excel_row for edge in edges)
    duplicate_input_rows = {
        row: count for row, count in workbook_rows.items() if count != 1
    }
    if duplicate_input_rows:
        raise ValueError(
            f"Workbook Excel row identities are not unique: "
            f"{duplicate_input_rows}"
        )
    assigned_row_counts = Counter(assigned_rows)
    if assigned_row_counts != workbook_rows:
        raise ValueError("Workbook rows were not mapped exactly once")
    if len({spec.module_name for spec in specs}) != len(specs):
        raise ValueError("Generated module names are not unique")
    if len({spec.module_dir for spec in specs}) != len(specs):
        raise ValueError("Generated catalog paths are not unique")
    existing_catalog_dirs = {
        manifest.parent.resolve()
        for manifest in (ROOT / "catalog").glob("*/*/migration.yaml")
    }
    expected_catalog_dirs = {
        spec.module_dir.resolve() for spec in specs
    }
    stale_catalog_dirs = sorted(
        existing_catalog_dirs - expected_catalog_dirs
    )
    if stale_catalog_dirs:
        raise ValueError(
            "Stale catalog modules must be resolved explicitly: "
            + ", ".join(str(path) for path in stale_catalog_dirs[:20])
        )
    if workbook_hash == KNOWN_WORKBOOK_SHA256:
        if len(edges) != 4887 or len(specs) != 1967:
            raise ValueError(
                "Known workbook coverage changed unexpectedly: "
                f"rows={len(edges)}, module_specs={len(specs)}"
            )
        ecosystem_counts = Counter(spec.ecosystem for spec in specs)
        expected_ecosystem_counts = Counter(
            {
                "java": 952,
                "nodejs": 557,
                "go": 265,
                "python": 123,
                "other": 70,
            }
        )
        if ecosystem_counts != expected_ecosystem_counts:
            raise ValueError(
                "Known workbook ecosystem counts changed unexpectedly: "
                f"{dict(ecosystem_counts)}"
            )
        multi_name_specs = {
            frozenset(edge.software for edge in spec.edges)
            for spec in specs
            if len({edge.software for edge in spec.edges}) > 1
        }
        expected_alias_specs = {
            frozenset((bare, coordinate))
            for (bare, _), coordinate in SAFE_JAVA_ALIASES.items()
        }
        if multi_name_specs != expected_alias_specs:
            raise ValueError(
                "Known workbook alias grouping changed unexpectedly"
            )

    refresh = set(args.refresh_readme)
    missing_readmes = 0
    missing_poms = 0
    missing_manifests = 0
    root_pom = ROOT / "pom.xml"
    root_pom_before = hashlib.sha256(root_pom.read_bytes()).hexdigest()
    for spec in specs:
        readme = spec.module_dir / "README.md"
        pom = spec.module_dir / "pom.xml"
        manifest = spec.module_dir / "migration.yaml"
        spec.readme_existed = readme.is_file()
        spec.pom_existed = pom.is_file()
        spec.manifest_existed = manifest.is_file()
        missing_readmes += not spec.readme_existed
        missing_poms += not spec.pom_existed
        missing_manifests += not spec.manifest_existed
        if not args.apply:
            continue
        spec.module_dir.mkdir(parents=True, exist_ok=True)
        if (
            not spec.readme_existed
            or spec.module_name in refresh
            or args.refresh_all
        ):
            readme.write_text(
                render_readme(spec, workbook_hash),
                encoding="utf-8",
            )
        if not spec.pom_existed or args.refresh_all:
            pom.write_text(render_pom(spec), encoding="utf-8")
        if not spec.manifest_existed or args.refresh_all:
            manifest.write_text(
                render_manifest(spec, workbook_hash),
                encoding="utf-8",
            )

    if args.apply:
        catalog = ROOT / "catalog"
        (catalog / "schema").mkdir(parents=True, exist_ok=True)
        (catalog / "pom.xml").write_text(
            render_catalog_parent(),
            encoding="utf-8",
        )
        (catalog / "README.md").write_text(
            render_catalog_readme(specs, edges, workbook_hash),
            encoding="utf-8",
        )
        (catalog / "schema" / "migration.schema.json").write_text(
            render_schema(),
            encoding="utf-8",
        )
        docs = ROOT / "docs"
        docs.mkdir(parents=True, exist_ok=True)
        (docs / "workbook-module-index.md").write_text(
            render_index(specs, edges, workbook_hash),
            encoding="utf-8",
        )
        write_csv(docs / "workbook-module-index.csv", specs)
        root_pom_after = hashlib.sha256(root_pom.read_bytes()).hexdigest()
        if root_pom_after != root_pom_before:
            raise ValueError("Generator changed the root pom.xml")

    print(f"workbook_sha256={workbook_hash}")
    print(f"rows={len(edges)}")
    print(f"raw_software_names={len({edge.software for edge in edges})}")
    print(f"module_specs={len(specs)}")
    print(f"missing_readmes_before_apply={missing_readmes}")
    print(f"missing_poms_before_apply={missing_poms}")
    print(f"missing_manifests_before_apply={missing_manifests}")
    print(f"apply={args.apply}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
