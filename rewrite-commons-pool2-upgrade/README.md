# Apache Commons Pool 2 → 2.13.1 migration

This module upgrades only the six `org.apache.commons:commons-pool2` source versions selected by `开源软件升级.xlsx` and performs source-level compatibility work. It is not a generic “upgrade every Pool 2 version” recipe.

## Workbook contract

| Excel row | Source | Target |
|---:|---:|---:|
| 2607 | 2.10.0 | 2.13.1 |
| 2608 | 2.11.1 | 2.13.1 |
| 2609 | 2.6.0 | 2.13.1 |
| 2610 | 2.8.0 | 2.13.1 |
| 2611 | 2.8.1 | 2.13.1 |
| 2612 | 2.9.0 | 2.13.1 |

The public upgrade recipe contains exactly one strict implementation recipe. The recommended migration recipe reuses that public recipe as its first step.

```yaml
activeRecipes:
  - com.huawei.clouds.openrewrite.commonspool2.MigrateCommonsPool2To2_13_1
```

Use `com.huawei.clouds.openrewrite.commonspool2.UpgradeCommonsPool2To2_13_1` when dependency-only changes are required.

## Executable behavior

| Compatibility concern | Recipe action | Verification |
|---|---|---|
| Exact six-version Maven/Gradle upgrade | **AUTO**: change to `2.13.1` only for owned, ordinary JAR declarations | `UpgradeCommonsPool2DependencyTest` |
| Root/profile Maven property ownership | **AUTO** only for one definition used exclusively by Pool 2 version elements; profile shadowing is respected | property positive/negative tests |
| Deprecated long-millisecond setters | **AUTO**: `setMaxWaitMillis`, `setEvictorShutdownTimeoutMillis`, min/soft-min idle and eviction-run setters become the canonical `Duration.ofMillis(...)` calls | `MigrateCommonsPool2DurationSettersTest` |
| Borrow deadlines, fairness and exhaustion | **MARK** exact Pool methods for finite/indefinite wait and contention review | `CommonsPool2JavaRisksTest` |
| `addObject`, invalidate/clear capacity reuse, concurrent return/invalidate | **MARK** exact operations; policy cannot be inferred safely | capacity and Jedis-derived fixtures |
| Validation/factory lifecycle and `DestroyMode` | **MARK** validation settings and custom factories | Java risk fixtures |
| Evictor, idle boundaries and negative durations | **MARK** exact eviction settings and custom policies | Java risk fixtures |
| Abandoned-object removal and replacement capacity | **MARK** calls and method references, including DBCP-style callbacks | DBCP-derived fixture |
| Timing precision/statistics | **MARK** legacy millis observations and detailed/message statistics | Java risk fixtures |
| Java 7 → Java 8 minimum | **MARK** owned Maven compiler properties/plugin settings and root Gradle compatibility | `CommonsPool2BuildRisksTest` |
| Parent/BOM/catalog/dynamic owner, variants and non-workbook versions | **MARK**, never guessed | build ownership fixtures |
| Shade/Shadow/Bnd/OSGi packaging | **MARK** only when an in-scope dependency is visible | build packaging fixtures |
| Plugin dependencies, custom XML owners, nested Gradle DSL, constraints, generated/cache parents | **NOOP** | strict negative fixtures |

For example, the old call:

```java
poolConfig.setMaxWaitMillis(2000L);
```

becomes:

```java
poolConfig.setMaxWait(Duration.ofMillis(2000L));
```

`Duration.ofMillis` deliberately preserves the old unit, literal sign and expression evaluation count. The recipe requires type attribution and does not transform an application method that merely has the same name.

## Incompatible and behavior-sensitive changes

The migration spans every release after the selected sources, not just the target patch:

- **Java baseline:** 2.6.0 targeted Java 7; 2.7.0 and later require Java 8. Align compiler, test and production runtimes before deploying 2.13.1.
- **Duration/Instant APIs:** 2.10–2.12 introduced Java time APIs and deprecated many millis methods. Pool timing state gained nanosecond precision, and active/idle duration calculations received negative-time fixes. Update monitoring thresholds and unit assertions.
- **Borrow deadlines:** 2.12.1 fixed `borrowObject(Duration)` ignoring its argument. 2.13.0 fixed spurious wakeups and keyed/non-keyed maximum-wait overruns. Tests that relied on an accidental longer wait can change.
- **Capacity and wakeups:** 2.12.0 made capacity freed by invalidation/clear available to waiting borrowers. 2.13.0 makes `addObject` return when capacity is unavailable and respect `maxIdle`; 2.13.1 fixes the unlimited (`maxIdle < 0`) regression.
- **Replacement behavior:** 2.13.0 attempts replacement after invalidation and abandoned-object removal. Factory callback counts and failure paths can therefore change.
- **Same-object races:** 2.13.0 prevents counter/collection corruption when callers concurrently return and invalidate the same identity. Such client behavior remains unsafe and should be removed.
- **Eviction:** shared evictor cleanup, abandoned pools, negative-duration normalization, soft/min idle thresholds and custom policy loading changed. Exercise zero, negative, disabled and exact-boundary values.
- **Exceptions and diagnostics:** abandoned cleanup now routes failures through swallowed-exception handling rather than standard error. Detailed statistics and exception-message statistics have cost and observability implications.
- **Lifecycle:** base pools implement `AutoCloseable`; confirm ownership and shutdown order rather than mechanically introducing try-with-resources around shared pools.
- **Packaging:** retain `Automatic-Module-Name: org.apache.commons.pool2`, OSGi metadata and the optional `net.sf.cglib.proxy` import when shading or rebundling.

The recipe marks these sites because workload policy, factory side effects, locking, acceptable latency and operational thresholds cannot be derived from syntax.

## Scope guarantees

AUTO dependency updates require all of the following:

1. the exact group/artifact and one of the six workbook source versions;
2. an ordinary JAR without classifier/type/extension variant;
3. a root or direct profile Maven dependency/dependency-management owner, or a top-level Gradle `dependencies` block;
4. a literal version, or a uniquely defined Maven property referenced only by matching Pool 2 version elements;
5. a source path outside generated, build, cache and installation directories.

An empty root path such as `pom.xml` or `build.gradle` is eligible. A leaf named `install.gradle` or `install.java` is eligible; only generated/installation **parent directories** are excluded. One-level nested owners such as Maven plugin dependencies, `<company><dependencies>`, `custom { dependencies }`, `project { dependencies }` and `constraints` are explicit negative cases.

## Validation plan

After applying the recipe:

1. compile and test on the production Java runtime (8 or newer);
2. run saturation tests for FIFO/LIFO, fairness, finite/negative waits, interrupts and spurious wakeups;
3. race borrow/return/invalidate/clear/add operations while asserting idle/active/created/destroyed counters;
4. inject factory exceptions into create, activate, validate, passivate and destroy paths;
5. test abandoned cleanup on borrow and maintenance, with replacement failures and swallowed-exception listeners;
6. exercise exact eviction thresholds, disabled/zero/negative durations and custom policies;
7. compare latency/statistics dashboards and exception text;
8. inspect shaded JAR manifest, JPMS name, OSGi imports/exports and service resources.

## Fixed references and extracted fixtures

- Official Commons Pool target history and compatibility notes: [`changes.xml` at `33078499af82dc1bdef3680916fd0a5b9322bab3`](https://github.com/apache/commons-pool/blob/33078499af82dc1bdef3680916fd0a5b9322bab3/src/changes/changes.xml).
- Official target build metadata (Java 8, module and OSGi settings): [`pom.xml` at the same target commit](https://github.com/apache/commons-pool/blob/33078499af82dc1bdef3680916fd0a5b9322bab3/pom.xml).
- The Duration AUTO fixture is based on Spring Data Redis's real starvation configuration: [`JedisTransactionalConnectionStarvationTest` at `6a016a8…`](https://github.com/spring-projects/spring-data-redis/blob/6a016a8f63a09065e4dba8835de11881061b4181/src/test/java/org/springframework/data/redis/connection/jedis/JedisTransactionalConnectionStarvationTest.java).
- Borrow/return/invalidate risk coverage is based on Jedis's pool wrapper: [`Pool.java` at `9ee0636…`](https://github.com/redis/jedis/blob/9ee063688ff88c3f156d595b18aed84f2aa2a3ec/src/main/java/redis/clients/jedis/util/Pool.java).
- Abandoned-policy method-reference coverage is based on Commons DBCP: [`BasicDataSource.java` at `91c061b…`](https://github.com/apache/commons-dbcp/blob/91c061be12b2a8a5b2e92567e567511ddd968386/src/main/java/org/apache/commons/dbcp2/BasicDataSource.java).
- Test style follows immutable OpenRewrite recipe-test examples: [`rewrite` at `b3008cc4a57de9b9c29d973eab0fc89ce71339a6`](https://github.com/openrewrite/rewrite/tree/b3008cc4a57de9b9c29d973eab0fc89ce71339a6/rewrite-test/src/main/java/org/openrewrite/test).

Source release tags were also resolved to immutable commits during research: 2.6.0 `3b3ea3df6281dcc9cae878774956a99fe20c2d75`, 2.8.0 `cfb886a6048071fb214fe00861438035f08f288b`, 2.8.1 `3fb7df661d521d57297d6aafa2d2d57356ae4adf`, 2.9.0 `e855619858edd5aae1a6b49788bb7212eb77ec23`, 2.10.0 `48c289d95c2374ee11e3276a8bcb93b7f99015be`, and 2.11.1 `abb1a0797b406566f0214c688871ab7e8fdc2601`. The module currently has 88 tests covering the AUTO, MARK and NOOP contract above.
