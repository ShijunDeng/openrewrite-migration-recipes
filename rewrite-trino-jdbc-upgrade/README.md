# Trino JDBC 418 → 453 migration

This module implements workbook row 601 for `io.trino:trino-jdbc`. It is deliberately narrower than a generic version bump: the public upgrade recipe accepts only exact version `418`, while the recommended migration recipe also applies deterministic source changes and emits review markers for decisions that require application knowledge.

## Recipes

| Recipe | Purpose |
| --- | --- |
| `com.huawei.clouds.openrewrite.trinojdbc.UpgradeTrinoJdbcTo453` | Upgrade an owned, ordinary `io.trino:trino-jdbc:418` declaration to `453`. |
| `com.huawei.clouds.openrewrite.trinojdbc.MigrateTrinoJdbcTo453` | Run the strict dependency upgrade, deterministic Java/URL migration, then Java and build risk searches. |

The dependency recipe handles Maven project/profile dependencies and dependency management plus top-level Gradle Groovy/Kotlin dependency declarations. A Maven property changes only when it has one visible definition and every reference belongs to the selected Trino dependency. It intentionally leaves versionless/BOM/catalog ownership, composite or dynamic versions, classifiers, non-JAR types, nested Gradle DSLs, generated trees and every source version except `418` unchanged.
Dynamic Gradle string templates are not guessed: the exact standard coordinate is marked as externally owned, while a
template with a classifier or artifact suffix is marked as a variant and cannot activate companion build audits.

## Automated incompatibility handling

Trino commit [`b575d7a`](https://github.com/trinodb/trino/commit/b575d7af6ef66efdae441a45c1427c951d209dcd) made an equivalent spelling change:

| 418 source | 453 result | Safety condition |
| --- | --- | --- |
| `TrinoConnection.isUseLegacyPreparedStatements()` | `TrinoConnection.useExplicitPrepare()` | Receiver must be type-attributed as `io.trino.jdbc.TrinoConnection`, invocation must have no arguments. The target returns primitive `boolean`. |
| `jdbc:trino:...?legacyPreparedStatements=value` | `jdbc:trino:...?explicitPrepare=value` | Only the exact query key in a literal Trino JDBC URL changes; value, order and fragment remain byte-for-byte unchanged. |

These transformations preserve the boolean meaning; they do not invert it. The second change is relevant to the 431 latency option: `explicitPrepare=false` permits `EXECUTE IMMEDIATE` for compatible servers.

## Marked incompatibilities

Markers are executable OpenRewrite search results, not README-only guidance. They identify the exact syntax requiring a decision:

- `TrinoDriverUri.create(String, Properties)` was public in 418 but has no public equivalent in 453. Migrate callers to `Driver`/`DriverManager`; the recipe marks the removed call rather than guessing lifecycle or validation behavior.
- JDBC URL literals are marked for security and connection-property review. Across 421–453 the driver added `hostnameInCertificate`, constrained Kerberos delegation, `timezone`, `explicitPrepare`, `assumeNullCatalogMeansCurrent`, session authorization, OS-keystore certificates, SQL PATH and default HTTP/HTTPS ports.
- Prepared-statement creation, parameter metadata and batch execution are marked for both explicit-prepare modes, server compatibility, latency, complex values and failure semantics.
- `DatabaseMetaData` catalog calls are marked because 450 introduced `assumeNullCatalogMeansCurrent`; `null`, empty, current and explicit catalogs must be regression-tested.
- temporal/typed `ResultSet` reads are marked for timezone, `LocalDateTime`, decimal, array/row, null and boundary-value verification.
- session context calls are marked to catch pooled-connection leakage and to decide how session user and SQL PATH are set/reset.
- cancellation, close, abort and timeout calls are marked for HTTP-client races, partial results and instrumentation behavior.
- query progress/statistics consumers are marked because exposed metrics evolved, including response/written bytes.
- direct use of `io.trino.jdbc.$internal` and subclasses of concrete implementation classes are marked: the driver shades its implementation dependencies and that namespace is not a compatibility API.
- builds are marked for versionless/external/shared ownership, variants, source versions outside `418`, Java below 8, and shade/assembly/bundle/native-image rules that can lose `META-INF/services/java.sql.Driver` or break the driver's own relocation.

No marker is emitted merely for a same-named application method. Build inspection uses the same root/profile and top-level Gradle ownership boundaries as the upgrade recipe.

## Evidence and fixtures

The compatibility inventory is based on immutable upstream material:

- [Trino 418 source](https://github.com/trinodb/trino/tree/368d50420328572185fcb3edd689ecadb16f6d27/client/trino-jdbc) and [Trino 453 source](https://github.com/trinodb/trino/tree/1e08da8df6d5b848af7922fd19955e4741cdb26e/client/trino-jdbc)
- [453 JDBC POM and shading configuration](https://github.com/trinodb/trino/blob/1e08da8df6d5b848af7922fd19955e4741cdb26e/client/trino-jdbc/pom.xml)
- JDBC release notes for [421](https://github.com/trinodb/trino/blob/1e08da8df6d5b848af7922fd19955e4741cdb26e/docs/src/main/sphinx/release/release-421.md), [423](https://github.com/trinodb/trino/blob/1e08da8df6d5b848af7922fd19955e4741cdb26e/docs/src/main/sphinx/release/release-423.md), [424](https://github.com/trinodb/trino/blob/1e08da8df6d5b848af7922fd19955e4741cdb26e/docs/src/main/sphinx/release/release-424.md), [430](https://github.com/trinodb/trino/blob/1e08da8df6d5b848af7922fd19955e4741cdb26e/docs/src/main/sphinx/release/release-430.md), [431](https://github.com/trinodb/trino/blob/1e08da8df6d5b848af7922fd19955e4741cdb26e/docs/src/main/sphinx/release/release-431.md), [450](https://github.com/trinodb/trino/blob/1e08da8df6d5b848af7922fd19955e4741cdb26e/docs/src/main/sphinx/release/release-450.md) and [453](https://github.com/trinodb/trino/blob/1e08da8df6d5b848af7922fd19955e4741cdb26e/docs/src/main/sphinx/release/release-453.md)
- real integration shapes extracted from [Apache Flink JDBC Connector commit `7c0710d`](https://github.com/apache/flink-connector-jdbc/tree/7c0710d98c013347776e5162aa8f827d53f4917b/flink-connector-jdbc-trino), including its Trino URL recognition, prepared-statement fixture and provided Maven dependency
- test structure follows the immutable [OpenRewrite `rewrite-test` examples](https://github.com/openrewrite/rewrite/tree/b3008cc4a57de9b9c29d973eab0fc89ce71339a6/rewrite-test/src/main/java/org/openrewrite/test)

`trino-jdbc-453.jar` has class-file major version 52, so Java 8 is the verified driver bytecode floor. The application may impose a newer baseline independently.

## Rollout

1. Run `UpgradeTrinoJdbcTo453` alone and resolve ownership markers before source migration.
2. Run `MigrateTrinoJdbcTo453`; commit deterministic changes separately from marker-driven application changes.
3. Test against representative old and new Trino servers with both prepared-statement modes; include authentication/TLS, metadata discovery, timezone/temporal values, arrays/rows, cancellation and pooling reset fixtures.
4. Inspect the packaged artifact for exactly one usable `java.sql.Driver` provider and no application dependency on `$internal` classes.
5. Remove markers only after the associated regression or policy decision is recorded.

Local verification:

```bash
mvn -q -f rewrite-trino-jdbc-upgrade/pom.xml clean verify
```

The module currently runs 86 tests, including immutable public-repository fixture shapes, strict positive/negative
ownership matrices, indirect Maven Java properties, Groovy/Kotlin dynamic coordinates, nested-project isolation,
marker precision and two-cycle idempotence.
