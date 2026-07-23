# JSON-java (`org.json:json`) → 20250107

This module implements the migration contract from `开源软件升级.xlsx`; it is not a generic latest-version recipe.

## Workbook contract

| Excel rows | Exact sources | Target |
|---|---|---|
| 456–462 | `20160810`, `20180813`, `20190722`, `20200518`, `20220320`, `20230227`, `3.11.2` | `20250107` |

`3.11.2` is intentionally retained because it is an exact workbook value even though it is not an upstream JSON-java release tag. AUTO scope must follow the approved inventory rather than silently “correcting” data.

```yaml
activeRecipes:
  - com.huawei.clouds.openrewrite.orgjson.MigrateOrgJsonTo20250107
```

Use `UpgradeOrgJsonTo20250107` for dependency-only work. `MigrateOrgJsonTo20250107` reuses that public upgrade recipe first.

## Executable specification

| Concern | Action | Implementation / tests |
|---|---|---|
| Seven exact Maven/Gradle versions | **AUTO** to `20250107` only for an owned ordinary JAR | `UpgradeSelectedOrgJsonDependency`, `UpgradeOrgJsonDependencyTest` |
| Maven root/profile properties | **AUTO** only for one definition used exclusively by matching version elements | property positive and shared/duplicate/attribute negative tests |
| Shared, duplicate or external Maven version ownership | **MARK** the matching dependency instead of silently skipping it | shared/duplicate/profile ownership build-risk tests |
| Deprecated `XMLParserConfiguration` constructors | **AUTO** to an equivalent immutable builder chain | `MigrateXmlParserConfigurationConstructorsTest` |
| Parser syntax, duplicate keys and nesting | **MARK** exact `JSONObject`/`JSONArray` text constructors and tokener operations | `OrgJsonJavaRisksTest` |
| Map/bean reflection, null/complex keys and cycles | **MARK** attributed constructors and graph mutations | Java risk tests |
| Numeric coercion and precision | **MARK** exact get/opt/conversion methods | Java risk tests |
| JSON Pointer 2021 behavior fix | **MARK** construction and query operations | Java risk tests |
| XML/JSONML conversion | **MARK** conversion/token operations as a security/data boundary | HaeBang-derived and focused fixtures |
| Equality and serialized bytes | **MARK** `similar`, writer and serialization methods | Java risk tests |
| Java 8 baseline | **MARK** owned Maven compiler settings and top-level Gradle Java settings below 8 | `OrgJsonBuildRisksTest` |
| Parent/BOM/catalog/dynamic versions, variants and other fixed versions | **MARK**, never guessed | build-risk tests |
| Shade/Shadow/Bnd/native image | **MARK** only when an in-scope dependency is visible | build-risk tests |
| Plugin dependencies, custom XML owners, nested Gradle DSL/constraints and generated parents | **NOOP** | strict negative tests |

Example deterministic migration:

```java
// before
new XMLParserConfiguration(keepStrings, cdataName, convertNil);

// after
new XMLParserConfiguration()
    .withKeepStrings(keepStrings)
    .withcDataTagName(cdataName)
    .withConvertNilAttributeToNull(convertNil);
```

The target constructors themselves document the builder replacement. The recipe requires type attribution, preserves argument order/evaluation count and supports `null` CDATA names; the no-argument constructor and existing builder chains remain unchanged.

## Behavior changes requiring review

- **Java runtime:** `20230618` was the final Java 6-compatible release; `20231013+` requires Java 8.
- **Map keys:** since `20180813`, a null key passed to `JSONObject(Map)` throws. Later releases also reject complex object/array keys and detect recursive graphs.
- **Parser depth:** object, array, XML and JSONML paths gained bounded nesting and convert stack overflow conditions to `JSONException`. Test the boundary and any deliberately unlimited configuration.
- **Strict and duplicate-key parsing:** 2024 added opt-in strict parsing and configurable duplicate-key behavior. Default parsing remains permissive in important ways, so explicitly decide the accepted grammar for trust boundaries.
- **Numbers:** fixes and reversions affected leading zeroes, exponentials, precision, concrete `Number` types and `getLong`/`optLong` conversions. Assert types and values, not only string shape.
- **JSON Pointer:** the `20210307` release calls out a potentially breaking JSON Pointer fix. Test `~0`, `~1`, `/`, empty tokens, URI fragments, arrays and invalid indices.
- **XML:** coercion, `xsi:nil`, `xsi:type`, forced lists, whitespace trimming, CDATA keys, empty elements and depth are configurable and changed across this span. Treat XML-to-JSON as an input-validation boundary.
- **Reflection:** `new JSONObject(bean)` observes getters and `JSONPropertyName`/`JSONPropertyIgnore`; JPMS access and native-image reflection configuration can change the visible JSON surface.
- **Equality/serialization:** `similar()` received recursive/numeric fixes. Escaping, indentation, invalid `JSONString` output and numeric formatting can change exact bytes used by signatures, caches or protocols.
- **Resource ownership:** newer `JSONTokener` exposes closing behavior. Do not infer stream ownership from syntax; test it explicitly.

## Scope guarantees

An AUTO dependency edit requires the exact coordinate and source whitelist, no classifier/type/extension, a direct root/profile Maven dependency or dependency management owner (or a top-level Gradle `dependencies` block), and a non-generated parent path. `pom.xml`, `build.gradle`, `install.gradle` and `install.java` at the root are eligible. Maven plugin dependencies, `<company><dependencies>`, `custom { dependencies }`, `constraints`, `project { dependencies }`, cache/build/generated/installation parents and dynamic/catalog coordinates are not rewritten.

## Validation checklist

1. compile and run on the production Java 8+ runtime;
2. replay accepted and rejected JSON corpora, duplicate keys and exact parser exceptions;
3. test nesting at limit − 1, limit and limit + 1, plus recursive Maps/Collections;
4. assert exact numeric value and Java `Number` subtype for boundary inputs;
5. replay JSON Pointer escape/URI/index cases;
6. replay hostile and representative XML with all configured conversion options;
7. snapshot serialized bytes where downstream systems compare/sign them;
8. inspect shaded JAR module/OSGi metadata and native-image reflection coverage.

## Fixed references and extracted fixtures

- Official release history: [`docs/RELEASES.md` at target `324090a87609d0abfa100f8e1cd5c4dfa97c2ba3`](https://github.com/stleary/JSON-java/blob/324090a87609d0abfa100f8e1cd5c4dfa97c2ba3/docs/RELEASES.md).
- Official target builder contract: [`XMLParserConfiguration.java` at the same commit](https://github.com/stleary/JSON-java/blob/324090a87609d0abfa100f8e1cd5c4dfa97c2ba3/src/main/java/org/json/XMLParserConfiguration.java).
- Real XML conversion fixture: [`HaeBangProject/HAEBANG` `MapService.java` at `087c93b667006612803721c4d9c27da31f081d98`](https://github.com/HaeBangProject/HAEBANG/blob/087c93b667006612803721c4d9c27da31f081d98/src/main/java/com/haebang/haebang/service/MapService.java).
- Test conventions: [`openrewrite/rewrite` at `b3008cc4a57de9b9c29d973eab0fc89ce71339a6`](https://github.com/openrewrite/rewrite/tree/b3008cc4a57de9b9c29d973eab0fc89ce71339a6/rewrite-test/src/main/java/org/openrewrite/test).

Resolved upstream source tag commits: `20160810` `37582a44ada8e5bbe6d987f41d3f834aaf28934c`, `20180813` `37f5bf28e96d9dee03a4b97728cc2976f9c248c7`, `20190722` `5b845f28cfdc26919b1416ec2067da4a0753aeb6`, `20200518` `8e5b516f2bab9b81098ef57a7e84076c28441428`, `20220320` `c0a1d5f741154948e9201e99876bb4bb131110b5`, and `20230227` `47fb49b6a871cd1652870e33e89cbff082bb0ee1`.

The module currently has 72 tests covering the exact workbook whitelist, Maven/Gradle ownership boundaries, public recommended-recipe execution, AUTO transformations, precise source/build markers, generated-path exclusions, fixed real-repository fixtures and two-cycle idempotence.
