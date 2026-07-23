# JUnit Platform Launcher 6.0.1 migration

This module turns the workbook row into an executable, conservative OpenRewrite migration. It does not merely
change a version: it rewrites the removed Launcher APIs whose replacements preserve behavior and places precise
markers on decisions that require build-owner or tool-integration knowledge.

## Workbook contract

| Item | Exact value |
| --- | --- |
| Coordinate | `org.junit.platform:junit-platform-launcher` |
| Accepted source versions | `1.7.2`, `1.8.2` only |
| Target | `6.0.1` |
| Workbook sequence numbers | 692, 693 (physical rows 693, 694) |
| Recipe package | `com.huawei.clouds.openrewrite.junitplatform` |

`UpgradeJUnitPlatformLauncherTo6_0_1` is intentionally strict. It changes only those two source literals in
direct Maven dependencies/dependency management and root Gradle dependency declarations. An exclusive Maven
property is upgraded only when its single definition is visible to the dependency and every reference belongs to
the selected Launcher coordinate. Classifiers, non-JAR types, version catalogs, BOM-owned/versionless declarations,
ranges, dynamic versions, nested Gradle DSL, subproject configuration and generated/cache paths remain unchanged.

## Public recipes

- `com.huawei.clouds.openrewrite.junitplatform.UpgradeJUnitPlatformLauncherTo6_0_1` contains only the strict
  dependency upgrade. It is suitable when source and compatibility migration are managed separately.
- `com.huawei.clouds.openrewrite.junitplatform.MigrateJUnitPlatformLauncherTo6_0_1` is the recommended recipe.
  Its first recipe explicitly reuses the public strict upgrade, followed by deterministic Java rewrites and
  review markers.

## Deterministic source changes (`AUTO`)

| JUnit 1.7.2/1.8.2 form | JUnit 6.0.1 form | Why this is safe |
| --- | --- | --- |
| `new LauncherDiscoveryRequestBuilder()` | `LauncherDiscoveryRequestBuilder.request()` | The factory creates the same empty builder; the constructor is private in 6.0.1. |
| `plan.getChildren(stringId)` | `plan.getChildren(UniqueId.parse(stringId))` | This is the implementation used by the removed String overload; the argument is evaluated once. |
| `new ReportEntry()` | `ReportEntry.from(Map.of())` | Both create a timestamped entry with an empty key/value map; the constructor is private in 6.0.1. |

All Java changes require attributed JUnit types. Same-named application classes and target forms are no-ops. The
visitors exclude generated/install/cache parents and are idempotent across repeated cycles.

## Review markers (`MARK`)

The recommended recipe emits a `SearchResult` at the smallest actionable node instead of guessing:

Companion Java, JUnit-family and test-provider audits run only where a standard, unclassified Launcher dependency is visible in the same Maven owner or root Gradle project. A Launcher variant is still marked itself, but cannot make unrelated build policy appear in scope.

| Marker | Required decision |
| --- | --- |
| `TestPlan.add(TestIdentifier)` | JUnit 6 removed it and there is no public mutation replacement. It already threw in 1.8.2; remove it or redesign the IDE/tool integration around discovery and runtime registration. |
| Java baseline below 17 | Raise the owned Maven compiler property or root Gradle Java compatibility and verify the runtime/toolchain. Indirect owned Maven properties are resolved; external toolchain ownership is left untouched. |
| Platform/Jupiter/Vintage/BOM not at 6.0.1 | JUnit 6 assigns one version to all three families. Align the actual owner and verify dependency convergence. |
| Surefire/Failsafe below 3.0.0 | Upgrade the Maven test provider to a supported 3.x version, then verify discovery, tags, parallel execution, reports and forks. Missing/property-external versions receive a separate owner marker. |
| Launcher version absent/dynamic/shared/outside the workbook | Migrate its property, catalog, platform, BOM, parent or other real owner explicitly. Fixed non-workbook versions are never silently broadened into the whitelist. |
| Classified or non-JAR Launcher artifact | Confirm that the target publishes the required artifact shape before changing it. |

JUnit 6 also adopts JSpecify annotations. Nullness diagnostics depend on each project's compiler/IDE and policy, so
the recipe does not insert suppressions or change application null contracts. Treat new diagnostics as source-level
review items after the deterministic migration.

The 6.0 release notes list `TestPlan.getTestIdentifier(String)` among removed APIs, while the fixed 6.0.1 source
still exposes it as a deprecated Gradle-consumer bridge. This module follows the target bytecode/source contract and
does not rewrite an API that remains callable in 6.0.1.

## Running the recipe

```yaml
activeRecipes:
  - com.huawei.clouds.openrewrite.junitplatform.MigrateJUnitPlatformLauncherTo6_0_1
```

Review every marker, resolve dependency convergence, compile and execute the full test suite on Java 17 or newer,
and confirm the IDE/build-tool Launcher integration. Running the recipe twice must produce no second-cycle changes.

## Evidence and fixture provenance

Official facts are pinned rather than linked to moving branches:

- JUnit 5.7.2 source tag commit
  [`f8d83151d84b3682e143b26c22b0155eb137bf83`](https://github.com/junit-team/junit-framework/tree/f8d83151d84b3682e143b26c22b0155eb137bf83).
- JUnit 5.8.2 source tag commit
  [`f58cd419755846f1476e8d15783438de8d7aede4`](https://github.com/junit-team/junit-framework/tree/f58cd419755846f1476e8d15783438de8d7aede4).
- JUnit 6.0.1 source/release commit
  [`d774b9ccc8550701fd6362c43f92611911da3e2b`](https://github.com/junit-team/junit-framework/tree/d774b9ccc8550701fd6362c43f92611911da3e2b),
  including the fixed
  [6.0.0 breaking-change list](https://github.com/junit-team/junit-framework/blob/d774b9ccc8550701fd6362c43f92611911da3e2b/documentation/src/docs/asciidoc/release-notes/release-notes-6.0.0.adoc).
- Fixed JUnit wiki migration guide
  [`39ae298eeb8c0bc4e2ef229608741a9a3eaaeb74`](https://github.com/junit-team/junit-framework.wiki/blob/39ae298eeb8c0bc4e2ef229608741a9a3eaaeb74/Upgrading-to-JUnit-6.0.md).
- OpenRewrite dependency-upgrade test style at fixed commit
  [`decb8dbb2b5b726f8815efc51c85c34a60268bb0`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java).

Reduced real-repository fixtures are documented in their tests and pinned here:

- The removed builder constructor pattern comes from
  [ausbin/circuitsim-grader-template@c3ae77e](https://github.com/ausbin/circuitsim-grader-template/blob/c3ae77e1db67f24ff370a1df03d33989ab2350d2/src/main/java/edu/gatech/cs2110/circuitsim/launcher/TesterLauncher.java#L146-L148).
- The maintained builder factory pattern comes from
  [Atmosphere/atmosphere@b8eb50c](https://github.com/Atmosphere/atmosphere/blob/b8eb50c4aa3e3c5d22c7c120d9a60b586d10acd1/generator/ComposeGeneratorTest.java#L47-L50).
- The maintained `UniqueId.parse(...)` traversal form comes from
  [strimzi/strimzi-kafka-operator@92c73f7](https://github.com/strimzi/strimzi-kafka-operator/blob/92c73f7518654b165142cfae3590797dcc947b75/systemtest/src/main/java/io/strimzi/systemtest/listeners/ExecutionListener.java#L63-L65).
- The common `TestIdentifier` traversal shape is represented by
  [JetBrains/intellij-community@3394a1d](https://github.com/JetBrains/intellij-community/blob/3394a1d40c04cda98c59fc52f6f4d0facdd6bd51/plugins/junit5_rt/src/com/intellij/junit5/JUnit5TestExecutionListener.java#L62-L68).

The test suite additionally covers every workbook source, exact whitelist identity, root/profile property ownership,
dependency management, Groovy and Kotlin Gradle notation, generated paths, variants, dynamic/outside versions,
recipe composition, real source shapes, target no-ops, marker precision and two-cycle idempotence.
It currently contains 103 tests.
