# ZooKeeper 3.8.6 migration recipes

This module migrates approved `org.apache.zookeeper:zookeeper` inputs to `3.8.6`. It is intentionally
not a version-only recipe: deterministic API and audit-configuration renames are automated, while
data safety, ensemble topology, transport security, logging-provider, embedded-server and generated
protocol decisions are surfaced as precise OpenRewrite markers.

The governing rule is **upgrade only**. ZooKeeper `3.9.3` and `3.9.4` are newer than the supplied
`3.8.6` target. They are target conflicts, not migration paths: the recipe leaves them byte-for-byte
unchanged and emits the exact prefix `目标版本冲突（禁止降级）`.

## Recipes

| Recipe | Mode | Contract |
| --- | --- | --- |
| `com.huawei.clouds.openrewrite.zookeeper.UpgradeZooKeeperTo3_8_6` | AUTO | Strict dependency-only entry point. |
| `com.huawei.clouds.openrewrite.zookeeper.MigrateZooKeeperTo3_8_6` | AUTO + MARK | Recommended migration, deterministic rewrites plus review markers. |
| `com.huawei.clouds.openrewrite.zookeeper.UpgradeSelectedZooKeeperDependency` | AUTO | Changes only the five approved lower fixed versions in safe local declarations. |
| `com.huawei.clouds.openrewrite.zookeeper.MigrateDeterministicZooKeeperApis` | AUTO | Official `ChangeMethodName` and `ChangeType` composition. |
| `com.huawei.clouds.openrewrite.zookeeper.MigrateDeterministicZooKeeperConfiguration` | AUTO | Official exact Properties change plus the equivalent structured YAML change. |
| `com.huawei.clouds.openrewrite.zookeeper.FindZooKeeper386BuildRisks` | MARK | Version ownership, downgrade conflicts, Jute, logging, transport and packaging risks. |
| `com.huawei.clouds.openrewrite.zookeeper.FindZooKeeper386SourceRisks` | MARK | Client, quorum, server, persistence, TLS/SASL, audit and Jute source decisions. |
| `com.huawei.clouds.openrewrite.zookeeper.FindZooKeeperConfigurationRisks` | MARK | Structured ZooKeeper operational configuration decisions. |

`AUTO` means the repository can be changed without inventing application policy. `MARK` means the
source remains semantically unchanged and receives a searchable `SearchResult` comment explaining
the evidence still required.

## Exact version policy

The current high-priority task is an allow-list, not a semver range. The catalog also retains two
vendor-suffixed workbook values that are outside this task's approved source set.

| Workbook version | Target | Action | Reason |
| --- | --- | --- | --- |
| `3.4.14` | `3.8.6` | AUTO | Approved lower version; rolling and persistence review is still required. |
| `3.5.6-hw-ei-302002` | `3.8.6` | NOOP + MARK | Vendor build remains outside the approved high-priority source set. |
| `3.6.0` | `3.8.6` | AUTO | Approved lower version. |
| `3.6.3-hw-ei-312002` | `3.8.6` | NOOP + MARK | Vendor build remains outside the approved high-priority source set. |
| `3.7.1` | `3.8.6` | AUTO | Approved lower version. |
| `3.8.3` | `3.8.6` | AUTO | Approved lower patch. |
| `3.8.4` | `3.8.6` | AUTO | Approved lower patch. |
| `3.9.3` | `3.8.6` | NOOP + MARK | `目标版本冲突（禁止降级）`; an approved forward 3.9 target is required. |
| `3.9.4` | `3.8.6` | NOOP + MARK | `目标版本冲突（禁止降级）`; an approved forward 3.9 target is required. |
| `3.8.6` | `3.8.6` | NOOP | Already at target. |
| every other fixed version | — | NOOP + MARK | Outside the approved source set; the recipe does not silently expand scope. |
| versionless/range/dynamic/catalog/external owner | — | NOOP + MARK | The real version owner must be migrated. |

The evidence is pinned to immutable Apache ZooKeeper source commits:

| Release | Source commit |
| --- | --- |
| `3.4.14` | [`4c25d480e66aadd371de8bd2fd8da255ac140bcf`](https://github.com/apache/zookeeper/tree/4c25d480e66aadd371de8bd2fd8da255ac140bcf) |
| `3.6.0` | [`b4c89dc7f6083829e18fae6e446907ae0b1f22d7`](https://github.com/apache/zookeeper/tree/b4c89dc7f6083829e18fae6e446907ae0b1f22d7) |
| `3.7.1` | [`a2fb57c55f8e59cdd76c34b357ad5181df1258d5`](https://github.com/apache/zookeeper/tree/a2fb57c55f8e59cdd76c34b357ad5181df1258d5) |
| `3.8.3` | [`6ad6d364c7c0bcf0de452d54ebefa3058098ab56`](https://github.com/apache/zookeeper/tree/6ad6d364c7c0bcf0de452d54ebefa3058098ab56) |
| `3.8.4` | [`9316c2a7a97e1666d8f4593f34dd6fc36ecc436c`](https://github.com/apache/zookeeper/tree/9316c2a7a97e1666d8f4593f34dd6fc36ecc436c) |
| `3.8.6` | [`6df26081269769c160c8c3a24929c60c91cd19c3`](https://github.com/apache/zookeeper/tree/6df26081269769c160c8c3a24929c60c91cd19c3) |
| `3.9.3` | [`c26634f34490bb0ea7a09cc51e05ede3b4e320ee`](https://github.com/apache/zookeeper/tree/c26634f34490bb0ea7a09cc51e05ede3b4e320ee) |
| `3.9.4` | [`7246445ec281f3dbf53dc54e970c914f39713903`](https://github.com/apache/zookeeper/tree/7246445ec281f3dbf53dc54e970c914f39713903) |

The requested target is also supported by the
[ZooKeeper 3.8.6 release notes](https://zookeeper.apache.org/doc/r3.8.6/releasenotes.html) and the
[Apache ZooKeeper security page](https://zookeeper.apache.org/security/). The latter identifies
affected 3.8.0–3.8.5 and 3.9.0–3.9.4 lines for the relevant current advisories; that does not grant
this recipe permission to downgrade 3.9.x.

Maven Central 目标制品已重新下载校验：JAR SHA-256
`a00943a87b9c83379267ebda487be1ddb83e9997a03e59f9199338a033b71ed7`，POM SHA-256
`9f6033115da6d167f1b4863606dc976c9d7fc873266279d554bbf1e10e749098`。JAR manifest 的
`Implementation-Build` 与固定目标 commit `6df26081269769c160c8c3a24929c60c91cd19c3` 一致。

## Deterministic automatic changes

### Dependency declarations

The dependency recipe changes an exact approved version to `3.8.6` when all of these are true:

1. the coordinate is exactly `org.apache.zookeeper:zookeeper`;
2. the declaration is a standard unclassified JAR;
3. the version is one of the five AUTO source values;
4. it is a direct project dependency or dependency-management declaration in `pom.xml`, or a
   direct root `dependencies {}` declaration in Gradle Groovy/Kotlin;
5. a Maven property is changed only when it has exactly one definition and every reference in that
   scope exclusively owns the ZooKeeper dependency version.

Supported Gradle forms include string notation, Groovy named-map notation, Groovy map-literal
notation and Kotlin string notation. Nested `buildscript`, `subprojects`, `allprojects`,
`project(...)`, constraints and custom DSL blocks are deliberately not guessed.

The recipe does not change plugin dependencies, version catalogs, external BOMs, classified
artifacts, ZIPs, dynamic versions or shared Maven properties. Those inputs are marked for owner
resolution.

### `FileTxnSnapLog.getDataDir()` → `getDataLogDir()`

ZooKeeper issue ZOOKEEPER-4730 renamed this accessor because it returns the transaction-log
directory, not the snapshot data directory. The fixed implementation commit is
[`b8eb6a301beceae92a60e8be1a8d716a1109c82f`](https://github.com/apache/zookeeper/commit/b8eb6a301beceae92a60e8be1a8d716a1109c82f).

The recipe reuses type-attributed `org.openrewrite.java.ChangeMethodName`, so unrelated business
methods named `getDataDir()` remain untouched. Calls and method references are covered. Definitions
inside a vendored/forked ZooKeeper source tree are not renamed automatically.

Before:

```java
File logDirectory = txnLogFactory.getDataDir();
```

After:

```java
File logDirectory = txnLogFactory.getDataLogDir();
```

The resulting access is still marked as a persistence review point. Renaming an accessor is
deterministic; proving that `dataDir`, `dataLogDir`, disk layout and backups are safe is operational.

### `Log4jAuditLogger` → `Slf4jAuditLogger`

ZooKeeper issue ZOOKEEPER-4427 migrated audit logging to the SLF4J-backed implementation. The fixed
implementation commit is
[`85551f9be5b054fa4aee0636597b12bda2ecb2e8`](https://github.com/apache/zookeeper/commit/85551f9be5b054fa4aee0636597b12bda2ecb2e8).

The Java recipe reuses type-attributed `org.openrewrite.java.ChangeType`:

```java
import org.apache.zookeeper.audit.Log4jAuditLogger;
// becomes
import org.apache.zookeeper.audit.Slf4jAuditLogger;
```

Exact configuration values are also migrated:

```properties
zookeeper.audit.impl.class=org.apache.zookeeper.audit.Slf4jAuditLogger
```

```yaml
zookeeper:
  audit:
    impl:
      class: org.apache.zookeeper.audit.Slf4jAuditLogger
```

Placeholders, expressions, unrelated keys and custom audit implementations remain unchanged.

## Incompatibility and review specification

### Persistence and rolling upgrade

This is the highest-risk boundary for `3.4.14` migrations. Recipe markers cover:

- `FileTxnSnapLog`, transaction-log, snapshot, restore, truncate and direct persistence internals;
- `dataDir`, `dataLogDir`, `snapshot.trust.empty`, `autopurge.*`, `snapCount` and fsync thresholds;
- direct Jute/generated protocol classes that require an aligned `zookeeper-jute:3.8.6`;
- exclusions or shading that can split ZooKeeper from its generated serialization classes.

ZOOKEEPER-3644 fixed a data-loss upgrade condition involving an empty snapshot and the
`snapshot.trust.empty` escape hatch:
[`a5d67c8ffcc18289aa89c892d948ad7c75a317b4`](https://github.com/apache/zookeeper/commit/a5d67c8ffcc18289aa89c892d948ad7c75a317b4).
ZOOKEEPER-3781 documents the temporary nature and follower behavior of that flag:
[`1214d3bf611d153ae8c3987523da01d3d6c82686`](https://github.com/apache/zookeeper/commit/1214d3bf611d153ae8c3987523da01d3d6c82686).

Required evidence before acceptance:

1. take and verify a restorable ensemble backup;
2. inspect every node's latest snapshot and transaction log;
3. record `dataDir`, `dataLogDir`, mount, owner, free-space and retention settings;
4. rehearse restore and rollback on a topology-equivalent clone;
5. run a one-node-at-a-time rolling upgrade while maintaining quorum;
6. validate zxid progression, leader election and client session continuity after each node;
7. do not use `snapshot.trust.empty` as a permanent unexplained setting.

The official
[ZooKeeper dynamic reconfiguration guide](https://zookeeper.apache.org/doc/r3.5.5/zookeeperReconfig.html)
notes that rolling from 3.4 should pass through at least 3.4.6 and recommends including client ports
in `server.N` lines. The approved `3.4.14` source satisfies the minimum version, but topology still
requires explicit review.

### Embedded server and quorum internals

Applications that embed ZooKeeper commonly depend on classes under:

- `org.apache.zookeeper.server.*`;
- `org.apache.zookeeper.server.quorum.*`;
- `org.apache.zookeeper.server.persistence.*`;
- `org.apache.zookeeper.admin.*`.

These are marked because constructors, lifecycle, return types and protected hooks changed across
the source branches. Examples identified by binary/source comparison include `ServerCnxn`
response methods, `QuorumPeerConfig` parsing, `ZooKeeperSaslClient`, `X509Util`,
`SecurityUtils`, `IWatchManager`, `StatsTrack`, response caches and persistence accessors.

Only the two implementation-proven renames above are AUTO. The recipe does not guess a replacement
for an internal method based solely on a similar name. Compile against `3.8.6`, make the application
choice, then test server startup, election, reconnect and shutdown.

### Client sessions, watches, ACLs and transactions

Markers identify:

- ZooKeeper construction, session state, request/reconnect and `close()` lifecycle;
- persistent/recursive watches and watch removal;
- authentication, ACL reads/writes and predefined ACL constants;
- `multi()`/`Transaction` boundaries;
- `ZooKeeperAdmin.reconfigure(...)`.

Acceptance must exercise:

- connection loss before and after request commit;
- session expiry, read-only mode and chroot behavior;
- watch registration/re-registration, duplicate delivery and callback ordering;
- digest, SASL and X.509 identities plus denied operations;
- transaction version checks, result decoding and retry/idempotency;
- dynamic membership changes with quorum maintained.

### TLS, SASL and hostname behavior

`X509Util`, `ZKConfig`, SASL client/server internals and related configuration are marked. Review:

- client and quorum keystore/truststore formats, passwords and reload behavior;
- endpoint identification, hostname and reverse-DNS behavior;
- FIPS mode and provider availability;
- secure/plain ports, port unification and client authentication;
- Kerberos principal canonicalization, login renewal and keytab rotation;
- mixed-version connections during the rolling window.

ZooKeeper 3.8.6 includes ZOOKEEPER-4986, a reverse-DNS TLS fix, in its
[release notes](https://zookeeper.apache.org/doc/r3.8.6/releasenotes.html). A security fix is not
proof that an application's previous hostname assumptions remain correct.

### Logging and audit-provider boundary

The 3.8.6 release line includes the 3.8.5 breaking dependency change to SLF4J `2.0.13` and
Logback `1.3.15`. The release notes warn that mixed older bindings can yield no SLF4J providers.
The build recipe therefore marks direct SLF4J bindings/bridges and Logback pins.

Review the resolved runtime graph, not only the declared POM:

```bash
mvn dependency:tree -Dincludes=org.slf4j,ch.qos.logback,org.apache.logging.log4j
```

Then verify startup logs, audit logs, provider selection, event completeness, secret redaction,
rotation and retention. A successful `ChangeType` does not prove the logging backend is wired.

### Transport overrides, admin server and four-letter-word commands

Direct Netty/Jetty pins are marked because they can replace the transport versions tested by
ZooKeeper 3.8.6. `clientPort`, `secureClientPort`, bind addresses, `portUnification`,
`4lw.commands.whitelist`, `admin.*` and metrics-provider settings are also marked.

Validate the resolved graph against the organization's security targets, then smoke-test:

- plain and TLS client listeners;
- quorum TLS;
- admin-server binding and authorization perimeter;
- enabled/disabled four-letter-word commands;
- metrics and health probes;
- connection limits, timeout and orderly shutdown behavior.

## Official OpenRewrite capability reuse audit

The module is compiled against OpenRewrite Core `8.87.5`, pinned by upstream tag commit
[`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568).
Runtime composition tests inspect the activated recipe tree, not merely YAML text.

| Official capability | Decision | Actual use / reason |
| --- | --- | --- |
| [`ChangeMethodName`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/ChangeMethodName.java) | REUSED | Type-aware `FileTxnSnapLog.getDataDir()` → `getDataLogDir()`, including method references. |
| [`ChangeType`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-java/src/main/java/org/openrewrite/java/ChangeType.java) | REUSED | Type-aware `Log4jAuditLogger` → `Slf4jAuditLogger`. |
| [`ChangePropertyValue`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-properties/src/main/java/org/openrewrite/properties/ChangePropertyValue.java) | REUSED | Exact `zookeeper.audit.impl.class` Properties value. |
| `UpgradeDependencyVersion` | NOT USED | Cannot encode the exact five-version allow-list, preserve/mark 3.9 conflicts and enforce this module's local-owner isolation in one declarative operation. |
| generic YAML `ChangeValue` | NOT USED | A path-only value replacement does not guarantee the old scalar is exactly the retired logger FQCN; the custom visitor closes this small safety gap. |
| ZooKeeper-specific official recipe | UNAVAILABLE | The official catalog/source audit found no ZooKeeper-specific upgrade recipe to compose at this target. |

The current [OpenRewrite recipe catalog](https://docs.openrewrite.org/recipes) has Apache-focused
recipes for several projects but no ZooKeeper-specific `3.8.6` migration. The module therefore
uses official generic primitives wherever their preconditions match and custom code only for:

- strict allow-list dependency ownership;
- exact YAML old-value protection;
- no-downgrade conflict reporting;
- structured build/source/configuration risk discovery.

`FindAuthoredSourceFiles` is a precondition for official building blocks. This prevents a caller
that explicitly supplies `target/`, `build/`, generated, installation, cache or vendor output from
having those files changed.

## Real-repository fixtures

Tests use small license-preserving fragments from immutable commits. They are behavioral fixtures,
not copied current-default-branch examples.

| Repository and fixed source | Why it is included | Expected result |
| --- | --- | --- |
| [Apache ZooKeeper ZOOKEEPER-4730](https://github.com/apache/zookeeper/commit/b8eb6a301beceae92a60e8be1a8d716a1109c82f) | Real `FileTxnSnapLog.getDataDir()` admin use. | Official method recipe changes it to `getDataLogDir()`. |
| [Apache ZooKeeper ZOOKEEPER-4427](https://github.com/apache/zookeeper/commit/85551f9be5b054fa4aee0636597b12bda2ecb2e8) | Real audit implementation transition. | Official type and exact configuration recipes encode the rename. |
| [Apache HBase `HQuorumPeer` at `872616e4...`](https://github.com/apache/hbase/blob/872616e4b45bf2994a63092b272987187bf3e161/hbase-zookeeper/src/main/java/org/apache/hadoop/hbase/zookeeper/HQuorumPeer.java) | Embedded quorum configuration and server lifecycle. | Quorum/reconfiguration and server-internal markers. |
| [Apache NiFi `ZooKeeperStateServer` at `c7c745e8...`](https://github.com/apache/nifi/blob/c7c745e8c8dcc7518b9d03720c85879cf29281be/nifi-framework-bundle/nifi-framework-extensions/nifi-framework-zookeeper-bundle/nifi-framework-zookeeper-state-provider/src/main/java/org/apache/nifi/controller/state/providers/zookeeper/server/ZooKeeperStateServer.java) | Embedded server, `FileTxnSnapLog`, connection factory and TLS utility. | Persistence, server-internal and TLS markers. |
| [Apache Pinot `ZkStarter` at `86535a9b...`](https://github.com/apache/pinot/blob/86535a9b6b3ec5e959bff9fb2d8cc14f59fc68aa/pinot-common/src/main/java/org/apache/pinot/common/utils/ZkStarter.java) | Extends `ZooKeeperServerMain` to expose protected lifecycle. | Server-internal marker; no speculative rewrite. |

All listed repositories use Apache-2.0 licensing. Fixtures retain an ASF license header and include
only the minimum code needed to prove recipe behavior.

## Test and acceptance matrix

The module currently contains **169 automated test invocations**:

- 74 strict dependency tests;
- 32 build-risk/no-downgrade tests;
- 9 official deterministic Java API tests;
- 27 deterministic configuration and structured-risk tests;
- 17 type-attributed source-risk and real-repository tests;
- 10 recommended-composition/runtime-tree tests.

Coverage includes:

- every approved source version in Maven, Gradle Groovy and Gradle Kotlin;
- both vendor-suffixed workbook values unchanged and marked outside the approved priority set;
- `3.9.3` and `3.9.4` unchanged in all three build dialects;
- future fixed versions, outside fixed versions, ranges and dynamic owners;
- root/profile Maven property ownership and ambiguous/shared-property rejection;
- dependency management, plugin scope, variants, lookalikes and nested Gradle DSLs;
- generated/cache/install path exclusion;
- official recipe classes in the runtime activated tree;
- attributed calls, method references, type changes and lookalike rejection;
- exact Properties and dotted/nested YAML values;
- client, watch, ACL, transaction, reconfiguration, persistence, Jute, TLS/SASL and audit markers;
- real Apache ZooKeeper, HBase, NiFi and Pinot fragments;
- multi-recipe ordering and two-cycle idempotency.

Run the module gate independently:

```bash
mvn -f rewrite-zookeeper-upgrade/pom.xml clean verify
```

## Usage

Add the built recipe artifact to the OpenRewrite plugin classpath, then activate the recommended
recipe:

```bash
mvn rewrite:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.zookeeper.MigrateZooKeeperTo3_8_6
```

Use the strict dependency-only recipe when a team is not ready to receive review markers:

```bash
mvn rewrite:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.zookeeper.UpgradeZooKeeperTo3_8_6
```

Recommended review workflow:

1. run the recipe on a clean branch;
2. inspect the resolved dependency graph, especially Jute, SLF4J/Logback, Netty and Jetty;
3. fail review if any `目标版本冲突（禁止降级）` marker exists;
4. classify every persistence, quorum, TLS/SASL, audit and embedded-server marker;
5. compile and run unit/integration tests against ZooKeeper `3.8.6`;
6. rehearse backup, restore, rolling upgrade and rollback on an ensemble clone;
7. deploy one node at a time while watching quorum, zxid, sessions, watches, auth and disk;
8. remove markers only when the supporting evidence is recorded.

## Non-goals

This module does not:

- downgrade any ZooKeeper version;
- choose a forward target for `3.9.3` or `3.9.4`;
- upgrade arbitrary versions outside the supplied allow-list;
- edit external BOMs/version catalogs or ambiguous shared properties;
- rewrite generated/build/vendor/cache/install output;
- infer a semantic replacement for removed server internals;
- mutate snapshots, transaction logs, ensemble membership, credentials or deployed servers;
- assert that a dependency version change alone makes an ensemble safe.

Those boundaries are deliberate: automation is used where the source evidence is deterministic,
and markers preserve the decisions that require real application, data and operational context.
