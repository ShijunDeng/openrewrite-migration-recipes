#!/usr/bin/env python3
"""Validate catalog coverage, facts, no-downgrade policy, and structure."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
from collections import Counter
from pathlib import Path
from xml.etree import ElementTree

import generate_workbook_module_specs as generator


ROOT = Path(__file__).resolve().parents[1]
MAVEN_NS = "http://maven.apache.org/POM/4.0.0"
REQUIRED_README_SECTIONS = (
    "## 模块身份",
    "## Excel 事实快照",
    "## 升级方向与禁止降级",
    "## 不兼容点规格",
    "## 证据台账",
    "## 后续 OpenRewrite 配方契约",
    "### AUTO",
    "### MARK",
    "### MANUAL",
    "## 测试与真实用例验收",
)


def error(errors: list[str], message: str) -> None:
    errors.append(message)


def pom_text(root: ElementTree.Element, path: str) -> str:
    element = root.find(path, {"m": MAVEN_NS})
    return "" if element is None else (element.text or "").strip()


def validate_leaf_pom(
    spec: generator.ModuleSpec,
    path: Path,
    errors: list[str],
) -> str:
    try:
        root = ElementTree.parse(path).getroot()
    except (OSError, ElementTree.ParseError) as exception:
        error(errors, f"{path}: invalid XML: {exception}")
        return ""
    artifact_id = pom_text(root, "m:artifactId")
    if artifact_id != spec.module_name:
        error(
            errors,
            f"{path}: artifactId {artifact_id!r} != {spec.module_name!r}",
        )
    if pom_text(root, "m:packaging") != "pom":
        error(errors, f"{path}: catalog leaf must use packaging=pom")
    if pom_text(root, "m:parent/m:groupId") != (
        "com.huawei.clouds.openrewrite"
    ):
        error(errors, f"{path}: wrong parent groupId")
    if pom_text(root, "m:parent/m:artifactId") != (
        "migration-catalog-parent"
    ):
        error(errors, f"{path}: wrong catalog parent artifactId")
    if pom_text(root, "m:parent/m:relativePath") != "../../pom.xml":
        error(errors, f"{path}: relativePath must be ../../pom.xml")
    for forbidden in ("m:modules", "m:dependencies", "m:build"):
        if root.find(forbidden, {"m": MAVEN_NS}) is not None:
            error(errors, f"{path}: catalog leaf contains {forbidden[2:]}")
    return artifact_id


def expected_edge_fact(edge: generator.Edge) -> dict[str, object]:
    return {
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
    }


def validate_manifest(
    spec: generator.ModuleSpec,
    path: Path,
    workbook_hash: str,
    errors: list[str],
) -> list[int]:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        error(errors, f"{path}: not valid JSON/YAML 1.2: {exception}")
        return []

    module = document.get("module", {})
    if document.get("schemaVersion") != 1:
        error(errors, f"{path}: schemaVersion must be 1")
    if module.get("id") != spec.module_name:
        error(errors, f"{path}: module.id mismatch")
    expected_catalog_path = (
        f"catalog/{spec.ecosystem}/{spec.module_slug}"
    )
    if module.get("catalogPath") != expected_catalog_path:
        error(errors, f"{path}: module.catalogPath mismatch")
    if module.get("languageProfile") != spec.ecosystem:
        error(errors, f"{path}: languageProfile mismatch")
    if module.get("targetVersion") != spec.target:
        error(errors, f"{path}: targetVersion mismatch")
    if sorted(module.get("rawIdentifiers", [])) != sorted(
        {edge.software for edge in spec.edges}
    ):
        error(errors, f"{path}: rawIdentifiers mismatch")

    workbook = document.get("workbook", {})
    if workbook.get("sha256") != workbook_hash:
        error(errors, f"{path}: workbook SHA mismatch")
    actual_edges = workbook.get("edges")
    if not isinstance(actual_edges, list):
        error(errors, f"{path}: workbook.edges must be a list")
        return []
    if len(actual_edges) != len(spec.edges):
        error(errors, f"{path}: edge count mismatch")

    row_ids: list[int] = []
    for expected, actual in zip(spec.edges, actual_edges):
        expected_fact = expected_edge_fact(expected)
        for key, value in expected_fact.items():
            if actual.get(key) != value:
                error(
                    errors,
                    f"{path}: Excel #{expected.excel_row} field {key} "
                    f"{actual.get(key)!r} != {value!r}",
                )
        row_ids.append(actual.get("excelRow"))
        relation, action, _ = generator.edge_disposition(expected)
        direction = actual.get("direction", {})
        if direction.get("relation") != relation:
            error(
                errors,
                f"{path}: Excel #{expected.excel_row} relation mismatch",
            )
        actual_action = direction.get("action")
        if actual_action != action and not (
            relation == "upgrade-candidate" and actual_action == "auto"
        ):
            error(
                errors,
                f"{path}: Excel #{expected.excel_row} action mismatch",
            )
        expected_atomic = not any(
            token in expected.source
            for token in ("...", "…", "（共", "(共")
        )
        if actual.get("sourceAtomic") is not expected_atomic:
            error(
                errors,
                f"{path}: Excel #{expected.excel_row} sourceAtomic mismatch",
            )

    policy = document.get("policy", {})
    if policy.get("allowDowngrade") is not False:
        error(errors, f"{path}: allowDowngrade must be false")
    if policy.get("conflictMarker") != "目标版本冲突（禁止降级）":
        error(errors, f"{path}: exact conflict marker is required")
    categories = {
        key: set(policy.get(key, []))
        for key in (
            "autoSourceWhitelist",
            "upgradeCandidatesPendingEvidence",
            "noopVersions",
            "conflictVersions",
            "unresolvedVersions",
        )
    }
    category_names = tuple(categories)
    for index, left in enumerate(category_names):
        for right in category_names[index + 1 :]:
            overlap = categories[left] & categories[right]
            if overlap:
                error(
                    errors,
                    f"{path}: {left}/{right} overlap: {sorted(overlap)}",
                )
    if categories["autoSourceWhitelist"] & (
        categories["noopVersions"]
        | categories["conflictVersions"]
        | categories["unresolvedVersions"]
    ):
        error(errors, f"{path}: unsafe source entered AUTO whitelist")
    for directive in document.get("taskDirectives", []):
        if directive.get("relation") == "conflict":
            overlap = set(directive.get("sourceVersions", [])) & categories[
                "autoSourceWhitelist"
            ]
            if overlap:
                error(
                    errors,
                    f"{path}: task conflict entered AUTO: {sorted(overlap)}",
                )
    status = document.get("status", {})
    if status.get("spec") != "complete":
        error(errors, f"{path}: spec status is not complete")
    if status.get("automation") == "catalog-only" and categories[
        "autoSourceWhitelist"
    ]:
        error(
            errors,
            f"{path}: catalog-only automation but AUTO whitelist is nonempty",
        )
    auto_edges = {
        edge.get("source")
        for edge in actual_edges
        if edge.get("direction", {}).get("action") == "auto"
    }
    if not auto_edges.issubset(categories["autoSourceWhitelist"]):
        error(
            errors,
            f"{path}: AUTO edge missing from whitelist: "
            f"edges={sorted(auto_edges)}, "
            f"whitelist={sorted(categories['autoSourceWhitelist'])}",
        )
    candidate_sources = {
        edge.get("source")
        for edge in actual_edges
        if edge.get("direction", {}).get("relation")
        == "upgrade-candidate"
    }
    directive_sources: set[str] = set()
    for directive in document.get("taskDirectives", []):
        if (
            directive.get("relation") == "user-supplied-upgrade"
            and directive.get("action") == "auto"
            and directive.get("status") == "verified"
        ):
            if directive.get("targetVersion") != module.get("targetVersion"):
                error(errors, f"{path}: user directive target mismatch")
            for source in directive.get("sourceVersions", []):
                if not isinstance(source, str) or any(
                    token in source for token in ("...", "…", "（共", "(共")
                ):
                    error(errors, f"{path}: user directive source is not atomic")
                else:
                    directive_sources.add(source)
    allowed_auto_sources = candidate_sources | directive_sources
    if not categories["autoSourceWhitelist"].issubset(
        allowed_auto_sources
    ):
        error(
            errors,
            f"{path}: AUTO whitelist contains neither a candidate edge "
            "nor a verified user-supplied source",
        )
    if not directive_sources.issubset(categories["autoSourceWhitelist"]):
        error(errors, f"{path}: verified user directive missing from AUTO")
    if categories["autoSourceWhitelist"] and status.get(
        "automation"
    ) != "implemented":
        error(errors, f"{path}: AUTO whitelist requires automation=implemented")
    if categories["autoSourceWhitelist"] and any(
        claim.get("status") != "verified"
        for claim in document.get("evidence", [])
    ):
        error(errors, f"{path}: AUTO whitelist requires verified evidence claims")
    if status.get("evidence") == "pending":
        for claim in document.get("evidence", []):
            if claim.get("status") == "verified":
                error(
                    errors,
                    f"{path}: pending evidence contains verified claim",
                )
    return row_ids


def validate_readme(
    spec: generator.ModuleSpec,
    path: Path,
    errors: list[str],
) -> None:
    try:
        content = path.read_text(encoding="utf-8")
    except OSError as exception:
        error(errors, f"{path}: unreadable: {exception}")
        return
    for section in REQUIRED_README_SECTIONS:
        if section not in content:
            error(errors, f"{path}: missing section {section!r}")
    for phrase in (
        "规格状态：`COMPLETE`",
        "目标版本冲突（禁止降级）",
    ):
        if phrase not in content:
            error(errors, f"{path}: missing required phrase {phrase!r}")
    documented_status = (
        "证据状态：`PENDING`" in content
        and "自动化状态：`CATALOG_ONLY`" in content
        and "`UNVERIFIED`" in content
    )
    implemented_status = (
        "证据状态：`VERIFIED`" in content
        and "自动化状态：`IMPLEMENTED`" in content
        and "`VERIFIED`" in content
    )
    if not documented_status and not implemented_status:
        error(errors, f"{path}: invalid evidence/automation status pairing")
    for edge in spec.edges:
        pattern = rf"^\|\s*{edge.excel_row}\s*\|"
        if not re.search(pattern, content, flags=re.MULTILINE):
            error(
                errors,
                f"{path}: missing Excel fact row {edge.excel_row}",
            )


def validate_catalog_parent(errors: list[str]) -> None:
    path = ROOT / "catalog" / "pom.xml"
    try:
        root = ElementTree.parse(path).getroot()
    except (OSError, ElementTree.ParseError) as exception:
        error(errors, f"{path}: invalid XML: {exception}")
        return
    if pom_text(root, "m:artifactId") != "migration-catalog-parent":
        error(errors, f"{path}: wrong artifactId")
    if pom_text(root, "m:packaging") != "pom":
        error(errors, f"{path}: packaging must be pom")
    if pom_text(root, "m:parent/m:relativePath") != "../pom.xml":
        error(errors, f"{path}: parent relativePath must be ../pom.xml")
    if root.find("m:modules", {"m": MAVEN_NS}) is not None:
        error(errors, f"{path}: catalog parent must not aggregate modules")


def validate_root_reactor(errors: list[str]) -> None:
    path = ROOT / "pom.xml"
    try:
        root = ElementTree.parse(path).getroot()
    except (OSError, ElementTree.ParseError) as exception:
        error(errors, f"{path}: invalid XML: {exception}")
        return
    modules = [
        (module.text or "").strip()
        for module in root.findall("m:modules/m:module", {"m": MAVEN_NS})
    ]
    catalog_modules = [module for module in modules if "catalog" in module]
    if catalog_modules:
        error(
            errors,
            f"{path}: catalog leaked into root reactor: {catalog_modules}",
        )


def validate_csv(
    specs: list[generator.ModuleSpec],
    errors: list[str],
) -> None:
    path = ROOT / "docs" / "workbook-module-index.csv"
    try:
        with path.open(encoding="utf-8", newline="") as source:
            rows = list(csv.DictReader(source))
    except OSError as exception:
        error(errors, f"{path}: unreadable: {exception}")
        return
    if len(rows) != len(specs):
        error(errors, f"{path}: expected {len(specs)} rows, got {len(rows)}")
    expected = {spec.module_name for spec in specs}
    actual = {row.get("module", "") for row in rows}
    if actual != expected:
        error(errors, f"{path}: module set mismatch")
    markdown_path = ROOT / "docs" / "workbook-module-index.md"
    try:
        content = markdown_path.read_text(encoding="utf-8")
    except OSError as exception:
        error(errors, f"{markdown_path}: unreadable: {exception}")
        return
    for spec in specs:
        expected_link = (
            f"../catalog/{spec.ecosystem}/{spec.module_slug}/README.md"
        )
        if expected_link not in content:
            error(
                errors,
                f"{markdown_path}: missing link {expected_link}",
            )


def spec_from_manifest(
    module_dir: Path,
    errors: list[str],
) -> generator.ModuleSpec | None:
    path = module_dir / "migration.yaml"
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
        module = document["module"]
        raw_edges = document["workbook"]["edges"]
        edges = [
            generator.Edge(
                excel_row=edge["excelRow"],
                sequence=edge["sequence"],
                software=edge["software"],
                language=edge["rawLanguage"],
                source=edge["source"],
                target=edge["target"],
                services=edge["services"],
                bucket=edge["bucket"],
                difficulty=edge["difficulty"],
                note=edge["note"],
            )
            for edge in raw_edges
        ]
        if not edges:
            raise ValueError("manifest has no workbook edges")
        spec = generator.ModuleSpec(
            identity=module["canonicalIdentity"]["value"],
            ecosystem=module["languageProfile"],
            raw_language=edges[0].language.lower(),
            target=module["targetVersion"],
            edges=edges,
            module_slug=module_dir.name,
            implementation_modules=tuple(
                module.get("candidateImplementationModules", [])
            ),
        )
    except (
        OSError,
        KeyError,
        TypeError,
        ValueError,
        json.JSONDecodeError,
    ) as exception:
        error(errors, f"{path}: cannot construct offline spec: {exception}")
        return None
    expected_path = (
        ROOT / "catalog" / spec.ecosystem / spec.module_slug
    ).resolve()
    if module_dir.resolve() != expected_path:
        error(errors, f"{path}: directory/languageProfile mismatch")
    return spec


def validate_offline() -> int:
    errors: list[str] = []
    module_dirs = sorted(
        path.parent
        for path in (ROOT / "catalog").glob("*/*/migration.yaml")
    )
    if len(module_dirs) != 1967:
        error(
            errors,
            f"offline catalog expected 1967 modules, got {len(module_dirs)}",
        )

    specs: list[generator.ModuleSpec] = []
    assigned_rows: list[int] = []
    artifact_ids: list[str] = []
    workbook_hashes: set[str] = set()
    for module_dir in module_dirs:
        spec = spec_from_manifest(module_dir, errors)
        if spec is None:
            continue
        specs.append(spec)
        manifest_path = module_dir / "migration.yaml"
        try:
            manifest_document = json.loads(
                manifest_path.read_text(encoding="utf-8")
            )
            workbook_hashes.add(
                manifest_document["workbook"]["sha256"]
            )
        except (OSError, KeyError, json.JSONDecodeError) as exception:
            error(errors, f"{manifest_path}: cannot read SHA: {exception}")
        for filename in ("README.md", "pom.xml", "migration.yaml"):
            if not (module_dir / filename).is_file():
                error(errors, f"{module_dir}: missing {filename}")
        for forbidden in ("src", "target", "META-INF"):
            if (module_dir / forbidden).exists():
                error(errors, f"{module_dir}: forbidden {forbidden}/")
        if (module_dir / "pom.xml").is_file():
            artifact_ids.append(
                validate_leaf_pom(
                    spec,
                    module_dir / "pom.xml",
                    errors,
                )
            )
        assigned_rows.extend(
            validate_manifest(
                spec,
                manifest_path,
                generator.KNOWN_WORKBOOK_SHA256,
                errors,
            )
        )
        if (module_dir / "README.md").is_file():
            validate_readme(spec, module_dir / "README.md", errors)

    expected_rows = Counter(range(2, 4889))
    if Counter(assigned_rows) != expected_rows:
        error(
            errors,
            "offline catalog must cover Excel rows 2..4888 exactly once",
        )
    if workbook_hashes != {generator.KNOWN_WORKBOOK_SHA256}:
        error(
            errors,
            f"offline workbook SHA set mismatch: {sorted(workbook_hashes)}",
        )
    if len(artifact_ids) != len(set(artifact_ids)):
        error(errors, "offline catalog artifactIds are not unique")
    if len({spec.module_dir for spec in specs}) != len(specs):
        error(errors, "offline catalog paths are not unique")
    validate_catalog_parent(errors)
    validate_root_reactor(errors)
    validate_csv(specs, errors)
    schema_path = ROOT / "catalog" / "schema" / "migration.schema.json"
    try:
        json.loads(schema_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        error(errors, f"{schema_path}: invalid schema JSON: {exception}")

    if errors:
        for message in errors[:100]:
            print(f"ERROR: {message}")
        if len(errors) > 100:
            print(f"ERROR: ... {len(errors) - 100} additional errors")
        print(f"validation_errors={len(errors)}")
        return 1
    print(f"workbook_sha256={generator.KNOWN_WORKBOOK_SHA256}")
    print(f"validated_modules={len(specs)}")
    print(f"validated_rows={len(assigned_rows)}")
    print("mode=offline")
    print("no_downgrade_policy=valid")
    print("root_reactor=unchanged_by_catalog")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--workbook",
        type=Path,
        default=generator.DEFAULT_WORKBOOK,
    )
    parser.add_argument(
        "--offline",
        action="store_true",
        help="Validate committed catalog files without the private workbook.",
    )
    args = parser.parse_args()
    if args.offline:
        return validate_offline()

    workbook = args.workbook.resolve()
    workbook_hash = hashlib.sha256(workbook.read_bytes()).hexdigest()
    edges = generator.parse_workbook(workbook)
    generator.validate_safe_aliases(edges)
    specs = generator.build_module_specs(edges)
    expected_by_path = {
        spec.module_dir.resolve(): spec for spec in specs
    }
    actual_manifests = {
        path.parent.resolve()
        for path in (ROOT / "catalog").glob("*/*/migration.yaml")
    }

    errors: list[str] = []
    missing = sorted(set(expected_by_path) - actual_manifests)
    unexpected = sorted(actual_manifests - set(expected_by_path))
    for path in missing:
        error(errors, f"{path}: missing catalog module")
    for path in unexpected:
        error(errors, f"{path}: unexpected catalog module")

    assigned_rows: list[int] = []
    artifact_ids: list[str] = []
    for module_dir, spec in expected_by_path.items():
        for filename in ("README.md", "pom.xml", "migration.yaml"):
            if not (module_dir / filename).is_file():
                error(errors, f"{module_dir}: missing {filename}")
        for forbidden in ("src", "target", "META-INF"):
            if (module_dir / forbidden).exists():
                error(errors, f"{module_dir}: forbidden {forbidden}/")
        if (module_dir / "pom.xml").is_file():
            artifact_ids.append(
                validate_leaf_pom(
                    spec,
                    module_dir / "pom.xml",
                    errors,
                )
            )
        if (module_dir / "migration.yaml").is_file():
            assigned_rows.extend(
                validate_manifest(
                    spec,
                    module_dir / "migration.yaml",
                    workbook_hash,
                    errors,
                )
            )
        if (module_dir / "README.md").is_file():
            validate_readme(spec, module_dir / "README.md", errors)

    expected_rows = Counter(edge.excel_row for edge in edges)
    if Counter(assigned_rows) != expected_rows:
        error(errors, "Catalog manifests do not cover every Excel row once")
    if len(artifact_ids) != len(set(artifact_ids)):
        error(errors, "Catalog artifactIds are not globally unique")
    validate_catalog_parent(errors)
    validate_root_reactor(errors)
    validate_csv(specs, errors)

    schema_path = ROOT / "catalog" / "schema" / "migration.schema.json"
    try:
        json.loads(schema_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        error(errors, f"{schema_path}: invalid schema JSON: {exception}")

    if errors:
        for message in errors[:100]:
            print(f"ERROR: {message}")
        if len(errors) > 100:
            print(f"ERROR: ... {len(errors) - 100} additional errors")
        print(f"validation_errors={len(errors)}")
        return 1

    print(f"workbook_sha256={workbook_hash}")
    print(f"validated_modules={len(specs)}")
    print(f"validated_rows={len(edges)}")
    print("no_downgrade_policy=valid")
    print("root_reactor=unchanged_by_catalog")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
