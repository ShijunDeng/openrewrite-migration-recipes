# Tomcat Catalina 10.1.56 migration recipes

This module migrates `org.apache.tomcat:tomcat-catalina` to `10.1.56`. It is deliberately more than a version edit: deterministic Servlet/EL and listener-configuration incompatibilities are rewritten, while decisions whose correct answer depends on traffic, security policy, native libraries, cluster topology, or custom Tomcat internals are left in place with precise OpenRewrite markers.

## Recipes

| Recipe | Mode | What it does |
| --- | --- | --- |
| `com.huawei.clouds.openrewrite.tomcatcatalina.FindTomcatCatalinaBranchTransitionRisks` | MARK | Preserves the Tomcat 9 namespace boundary and reports every fixed source above 10.1.56 as `目标版本冲突（禁止降级）`; higher versions are never edited. |
| `com.huawei.clouds.openrewrite.tomcatcatalina.UpgradeTomcatCatalinaTo10_1_56` | AUTO/MARK | Strict approved-version dependency upgrade plus the branch-transition guard. |
| `com.huawei.clouds.openrewrite.tomcatcatalina.MigrateTomcat9JakartaApiDependencies` | AUTO | Directly composes the official dependency leaves for `javax.servlet-api`→`jakarta.servlet-api:6.0.0` and `javax.el-api`→`jakarta.el-api:5.0.1`; newer Jakarta API lines are never lowered. |
| `com.huawei.clouds.openrewrite.tomcatcatalina.MigrateTomcat9JakartaNamespaces` | AUTO | Directly composes two official Core `ChangePackage` leaves for type-attributed Servlet/EL Java source, while refusing to manufacture removed Servlet 6 types. |
| `com.huawei.clouds.openrewrite.tomcatcatalina.MigrateTomcatCatalina101Java` | AUTO | Reuses the safe leaves from the official Servlet 6 recipe plus the official EL 5 and RFC 6265 recipes; the return-type-unsafe `getValueNames()` aggregate step is deliberately excluded. |
| `com.huawei.clouds.openrewrite.tomcatcatalina.MigrateTomcatCatalina101Configuration` | AUTO | Removes six obsolete `JreMemoryLeakPreventionListener` attributes whose Java-8 leak workarounds disappeared on the Java-11 baseline. |
| `com.huawei.clouds.openrewrite.tomcatcatalina.FindTomcatCatalinaBuildRisks` | MARK | Marks unresolved/external versions, variants, Java below 11, Catalina-family misalignment and the now-interim 10.1.56 security target. |
| `com.huawei.clouds.openrewrite.tomcatcatalina.FindTomcatCatalinaJavaRisks` | MARK | Marks removed Servlet APIs, obsolete overrides, internal/ APR APIs, cookie assumptions and case-insensitive HTTP-method logic. |
| `com.huawei.clouds.openrewrite.tomcatcatalina.FindTomcatCatalinaConfigurationRisks` | MARK | Marks parameter-limit, URI, APR, DIGEST, ETag, cluster and descriptor decisions. |
| `com.huawei.clouds.openrewrite.tomcatcatalina.FindTomcatCatalinaResourceRisks` | MARK | Marks `META-INF/services`, manifest and configuration strings that still name Javax Servlet/EL contracts. |
| `com.huawei.clouds.openrewrite.tomcatcatalina.MigrateTomcatCatalinaTo10_1_56` | RECOMMENDED | Guards branch transitions, applies deterministic AUTO changes, then reports the remaining build/source/configuration decisions. |

`SearchResult` comments are review findings, not edits that claim to solve the marked issue. Run the recommended recipe, review every marker, add deployment-specific changes and tests, then remove or suppress findings only with evidence.

## Strict remediation boundary

The workbook contains exactly eight approved AUTO inputs for `tomcat-catalina`:

```text
10.1.40, 10.1.47, 10.1.48, 10.1.52,
9.0.98, 9.0.105, 9.0.115, 9.0.117
```

All eight resolve to the supplied `10.1.56` target. This module does not define any rollback path. `10.1.57`, every Tomcat 11 version, and any other fixed version above the target are deliberately excluded from AUTO, remain byte-for-byte unchanged, and receive the exact marker `目标版本冲突（禁止降级）`. `10.0.27` and every other table-external lower version are strict NOOP/MARK inputs rather than an expanded whitelist.

The supplied target is now an interim security target. Apache reports that CVE-2026-59083 and CVE-2026-59084 affect 10.1.56 and are fixed in 10.1.57. The recipe follows the approved workbook target rather than silently changing scope, but it marks each resulting 10.1.56 dependency so the owner must approve 10.1.57 or a later supported release.

AUTO ownership rules are also strict:

- Maven: direct root/profile dependencies and dependency management; a property is changed only when it has exactly one visible owner and every reference owned by that property is this exact dependency version.
- Gradle Groovy/Kotlin: literal or supported map notation in the root `dependencies` block only.
- Classifiers, non-JAR types, ranges, dynamic versions, version catalogs, platforms/BOM owners, interpolation, nested `subprojects`/`allprojects`, plugin/buildscript dependencies and generated/cache trees are not guessed.
- A higher major/minor source is never rewritten to a lower target, even if a data row requests it.

## Implemented incompatibility handling

### Deterministic AUTO changes

| Old source/configuration | New source/configuration | Reason the edit is safe |
| --- | --- | --- |
| `javax.servlet:javax.servlet-api` | `jakarta.servlet:jakarta.servlet-api:6.0.0` | Official dependency relocation with the exact Tomcat 10.1 Servlet baseline. |
| `javax.el:javax.el-api` | `jakarta.el:jakarta.el-api:5.0.1` | Official dependency relocation with the exact Tomcat 10.1 EL baseline. |
| Type-attributed `javax.servlet.*` / `javax.el.*` Java types | `jakarta.servlet.*` / `jakarta.el.*` | Tomcat 9→10 is the Java EE→Jakarta EE namespace transition; comments and string literals are not rewritten. |
| `HttpServletRequest.isRequestedSessionIdFromUrl()` | `isRequestedSessionIdFromURL()` | The removed method was the deprecated spelling of the same API. |
| `HttpServletResponse.encodeUrl(s)` | `encodeURL(s)` | Direct non-deprecated equivalent. |
| `HttpServletResponse.encodeRedirectUrl(s)` | `encodeRedirectURL(s)` | Direct non-deprecated equivalent. |
| `HttpSession.getValue(k)` | `getAttribute(k)` | Servlet compatibility delegate. |
| `HttpSession.putValue(k,v)` | `setAttribute(k,v)` | Servlet compatibility delegate with the same binding-listener contract. |
| `HttpSession.removeValue(k)` | `removeAttribute(k)` | Servlet compatibility delegate. |
| `HttpServletResponse.setStatus(code,message)` | `setStatus(code)` | Servlet 6 removed reason phrases; the status code is preserved and the obsolete argument is deleted. |
| `ServletRequest.getRealPath(path)` | `getServletContext().getRealPath(path)` | Official migration preserves the ServletContext-owned path lookup. |
| `ServletContext.log(exception,message)` | `log(message,exception)` | Servlet 5 exposed both overloads; Servlet 6 removed the deprecated argument order. |
| `UnavailableException(servlet,message)` / `(seconds,servlet,message)` | `(message)` / `(message,seconds)` | Official recipe removes obsolete retained-Servlet state while preserving the message and unavailable period. |
| `MethodExpression.isParmetersProvided()` | `isParametersProvided()` | Correctly-spelled method already existed and the typo was removed by EL 5. |
| Removed `Cookie` / `SessionCookieConfig` comment and version invocations | Statement removed | Servlet 6 aligns with RFC 6265; setters have no effect and getters return fixed values. |
| Six removed `JreMemoryLeakPreventionListener` XML attributes | Attribute removed | Their setters and Java-8 workarounds were removed; there is no replacement setting on Java 11. |

These Java edits reuse the safe runtime-expanded leaves of the official
`RemovalsServletJakarta10` recipe and directly activate the official
`RemovedIsParmetersProvidedMethod` and `ServletCookieBehaviorChangeRFC6265`
recipes; this module no longer maintains duplicate local visitors for them.
The broad Servlet aggregate itself is not activated: its
`getValueNames()`→`getAttributeNames()` step changes `String[]` to
`Enumeration<String>` without rewriting callers, so a mechanical rename can make
previously valid code fail compilation. Rewrites require OpenRewrite type
attribution. A same-named business method is never rewritten. Obsolete interface
implementations are marked instead of blindly renamed because a class normally
already implements the replacement method and a rename could create a duplicate
declaration.

For Tomcat 9 source, a compilation unit using `SingleThreadModel`, `HttpSessionContext`, or `HttpUtils` is intentionally not namespace-rewritten. Those types do not exist in Servlet 6; the risk recipe marks them, the developer replaces the behavior, and a later recipe cycle can safely migrate the rest of that unit. This prevents an apparently successful edit from creating nonexistent `jakarta.*` types.

### Decisions kept as MARK

- Java 11 is a hard runtime baseline; AUTO cannot know every CI image, test worker, container base image or launch script.
- Tomcat 9→10.1 crosses Servlet 4→6 and Java EE→Jakarta EE. The Java namespace is automated where type-safe; explicit `javax.servlet`/`javax.el` dependencies, descriptors, service providers, reflection strings, JSP/WebSocket libraries and framework integrations are marked for coordinated migration.
- Servlet 6 removed deprecated types and interface declarations that do not have a safe syntax-only replacement, including `SingleThreadModel`, `HttpUtils`, `HttpSessionContext`, `getSessionContext()`, `getValueNames()` and obsolete implementations of removed methods. In particular, `getValueNames()` returned `String[]`, while `getAttributeNames()` returns `Enumeration<String>`; adapting iteration, ordering and array consumers requires owner review. Safe official call-site leaves run first; declarations and return-type-changing behavior remain MARK.
- Tomcat's `org.apache.catalina`, `org.apache.coyote` and `org.apache.tomcat` internals are broadly but not binary compatible. Custom Valves, Realms, Connectors, class loaders and embedded-container extensions require JavaDoc/API review.
- The APR connector and most legacy JNI surface were removed. NIO/NIO2 selection plus OpenSSL/Tomcat Native is an operational migration, not a class-name substitution.
- Connector `maxParameterCount` default fell from 10,000 to 1,000 in 10.1.8. Restoring 10,000 automatically would erase a security control.
- URI decoding/normalization was clarified and later tightened, including NULL-byte rejection in 10.1.55. Encoded separators and proxy normalization require integration tests.
- HTTP methods are compared case-sensitively from 10.1.47. Case-insensitive application branches are marked.
- Strong default-servlet ETags changed SHA-1→SHA-256 in 10.1.46 when enabled; cache identity changes are expected.
- The higher 10.1.57 release requires a valid RFC 7616 DIGEST `qop`; a future security-target approval must include client/algorithm/credential interoperability tests.
- `EncryptInterceptor` changed its wire data in 10.1.56. An upgrade crossing that boundary requires a full-cluster stop/restart; rolling mixed versions fail.
- Catalina sibling modules under `org.apache.tomcat` must resolve to the same release; BOM/catalog/parent owners remain explicit work.
- A Servlet 4/5 deployment descriptor is marked for schema review. A Servlet 6.1 descriptor is also marked because it indicates that the supplied 10.1 target is invalid for a Tomcat 11 source; it is never silently lowered.

## Usage

Build and test this module:

```bash
mvn -f rewrite-tomcat-catalina-upgrade/pom.xml clean verify
```

The module currently executes 301 tests with zero failures. They include every approved source literal in direct Maven, exclusive-property Maven, Gradle Groovy and Gradle Kotlin forms; exact Servlet/EL dependency relocation; higher-patch and higher-major no-downgrade guards; runtime expansion of adopted and rejected official recipe trees; pinned artifact hashes; real-repository fixtures; positive, negative, lookalike, owner, overload, generated-path, marker-survival, aggregate-order and two-cycle idempotence cases.

After publishing the recipe artifact on the OpenRewrite runtime classpath, activate:

```text
com.huawei.clouds.openrewrite.tomcatcatalina.MigrateTomcatCatalinaTo10_1_56
```

Run it on a clean branch, inspect the diff and every `~~(...)~~>` marker, compile with Java 11+ and Tomcat 10.1.56, then exercise HTTP parsing, session/authentication, TLS/native, cache, proxy and cluster tests. AUTO intentionally does not wait for a remote CI run.

## Evidence and fixtures

Primary fixed sources used for the specification and tests:

- [Tomcat 10 migration guide](https://tomcat.apache.org/migration-10.html): the Tomcat 9→10 `javax.*`→`jakarta.*` transition and Servlet 4→5 boundary.
- [Tomcat 10.1 migration guide](https://tomcat.apache.org/migration-10.1.html): Java 11, Servlet 6/EL 5 removals, internal-API warning, APR removal, `maxParameterCount`, cache-header and `EncryptInterceptor` changes.
- [Tomcat 10.1 changelog](https://tomcat.apache.org/tomcat-10.1-doc/changelog.html): the exact 10.1.46, 10.1.47, 10.1.55, 10.1.56 and 10.1.57 release entries used for ETag, HTTP-method, URI, cluster and DIGEST boundaries.
- [Apache Tomcat 10 security page](https://tomcat.apache.org/security-10.html): CVE-2026-59083 and CVE-2026-59084 affect 10.1.56 and are fixed in 10.1.57, which is why the approved target receives an interim-security marker.
- [`tomcat-catalina` 9.0.117 source tag](https://github.com/apache/tomcat/tree/f892e52577feef83aff57d34c2b4be61a5a68524), [10.0.27 source tag](https://github.com/apache/tomcat/tree/ca8720d41f3be917dc3fcdd03fcca8d3152a13fb), [10.1.56 target tag](https://github.com/apache/tomcat/tree/59f3f1ab4f905f94c9c99cad579d6afb3a935b66), and [10.1.57 superseding tag](https://github.com/apache/tomcat/tree/5da21b1c24a6443bca5c10dc80a69a76042ca337): namespace, API, configuration and security-boundary comparisons. Tag commits are pinned so later default-branch changes cannot alter the evidence.
- Maven Central target artifacts: `tomcat-catalina-10.1.56.jar` SHA-256 `8271c90a0a147ee53639cade2fab3b079b53184949e982464052a468efcce3f5`; target POM SHA-256 `a00ef0ef028138027541a0853101f41420e9dc7cd19d193929710be84ade92d8`.
- [Jakarta Servlet 6.0 specification](https://jakarta.ee/specifications/servlet/6.0/): normative target API.

### Fixed OpenRewrite audit

The official catalog is audited from fixed runtime artifacts, not from mutable
documentation or a recipe name inferred from the current default branch.
`TomcatCatalinaOfficialRecipeReuseTest` verifies each JAR file name, manifest
`Full-Change`, SHA-256, direct composition options and the fully expanded runtime
tree:

| Artifact | Fixed commit | JAR SHA-256 | Audit use |
| --- | --- | --- | --- |
| `rewrite-java:8.87.5` | [`91e23c2858176877428ddc03e146d2bb023217a8`](https://github.com/openrewrite/rewrite/commit/91e23c2858176877428ddc03e146d2bb023217a8) | `a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043` | Core `ChangePackage`, method/type/argument leaves and test harness behavior. |
| `rewrite-maven:8.87.5` | [`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/commit/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) | `d7d4a38376a87e9de2a27f43dfe522abba6a8e20ca3429587ec349fcde23db4c` | Maven model/parser used by dependency fixtures. |
| `rewrite-migrate-java:3.40.0` | [`658481254a6ee678f5f162e51d8d49ee01c75877`](https://github.com/openrewrite/rewrite-migrate-java/commit/658481254a6ee678f5f162e51d8d49ee01c75877) | `8c00217ff2cf4dc9c139a1eff49ed1403fe20e010e42295f5aeb1dd9a5872dc6` | Precise Servlet 6, EL 5 and Cookie leaves plus the broad-aggregate comparison tree. |
| `rewrite-java-dependencies:1.59.0` | [`decb8dbb2b5b726f8815efc51c85c34a60268bb0`](https://github.com/openrewrite/rewrite-java-dependencies/commit/decb8dbb2b5b726f8815efc51c85c34a60268bb0) | `b5c5ffaa0aea06cbbb8ae110ed138261bce621806c789f14ea0f3fe92cf95550` | Exact Servlet/EL dependency relocation leaves. |
| `rewrite-spring:6.35.0` | [`d28afcb6661ad413539056de0936c5489ff9d8ee`](https://github.com/openrewrite/rewrite-spring/commit/d28afcb6661ad413539056de0936c5489ff9d8ee) | `27df444210c8bfee7e9d0f04d6d6f7986d2bee36bcd472d8307912613e93e98b` | Test-scope catalog audit proving there is no standalone Tomcat Catalina 10.1 recipe. |

License boundary: the Core
[`rewrite-java`/`rewrite-maven`](https://github.com/openrewrite/rewrite/blob/91e23c2858176877428ddc03e146d2bb023217a8/LICENSE)
artifacts and
[`rewrite-java-dependencies`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/LICENSE)
are Apache-2.0; the fixed
[`rewrite-migrate-java`](https://github.com/openrewrite/rewrite-migrate-java/blob/658481254a6ee678f5f162e51d8d49ee01c75877/LICENSE.md)
Jakarta implementation and test-scope
[`rewrite-spring`](https://github.com/openrewrite/rewrite-spring/blob/d28afcb6661ad413539056de0936c5489ff9d8ee/LICENSE.md)
catalog are distributed under the Moderne Source Available License. This module
loads those published recipe artifacts at runtime and copies no upstream source.

The adoption boundary is intentionally narrower than the official Jakarta
platform recipes:

| Official capability | Decision | Reason |
| --- | --- | --- |
| Core `ChangePackage(javax.servlet→jakarta.servlet, recursive=true)` and `ChangePackage(javax.el→jakarta.el, recursive=true)` | Adopt directly | Exact type-attributed namespace leaves; removed-type and generated-path preconditions are local policy only. |
| `ChangeDependency` / `UpgradeDependencyVersion` for the two Servlet/EL API coordinates | Adopt directly | Exact `6.0.0` / `5.0.1` targets; runtime tests prove newer API lines are not lowered. |
| Safe nested leaves of `RemovalsServletJakarta10` | Adopt directly | Nine method renames, four argument deletions, two argument reorders and `UpdateGetRealPath` are copied as recipe composition options from the pinned official runtime tree, not reimplemented as visitors. |
| `RemovalsServletJakarta10` aggregate | Exclude as a whole | Its `HttpSession.getValueNames()`→`getAttributeNames()` leaf changes `String[]` to `Enumeration<String>` without adapting callers; tests prove that leaf is absent and the original call receives a MARK finding. |
| `RemovedIsParmetersProvidedMethod`, `ServletCookieBehaviorChangeRFC6265` | Adopt directly | Precise official EL 5 and RFC 6265 call-site recipes; their nested Core leaves are runtime-audited. |
| `JakartaEE10`, `JavaxMigrationToJakarta`, `JavaxServletToJakartaServlet`, `JavaxElToJakartaEl` | Exclude | Broad Java EE/Jakarta migration families modify unrelated APIs, plugins, Jetty, Faces, CDI and other frameworks. |
| `MigrationToJakarta10Apis` | Exclude | Runtime tree contains at least twenty platform-wide range selectors such as `jakarta.persistence:3.1.x` and `jakarta.jms:3.1.x`, outside the two approved API coordinates. |
| Spring framework/Boot aggregates | Exclude | The fixed Spring catalog has no standalone Catalina 10.1 leaf; an aggregate would couple unrelated Spring and Jakarta changes. |
| Generic/wildcard Tomcat dependency selector | Exclude | The primary coordinate remains the exact eight-version custom whitelist; no official selector is allowed to broaden it or lower Tomcat 11/10.1.57. |

Reduced real-repository fixtures retain the relevant expression and are pinned to immutable commits:

- [`jfinal/jfinal` `JsonRequest`](https://github.com/jfinal/jfinal/blob/a0e9e8b99dc793bcf0cd40ca7feba005ba0c5349/src/main/java/com/jfinal/core/paragetter/JsonRequest.java) supplies an `isRequestedSessionIdFromUrl()` wrapper shape.
- [`yona-projects/yona` `PlayServletResponse`](https://github.com/yona-projects/yona/blob/60a5ac40689fc36ee5b55eddedd345fc34878190/app/utils/PlayServletResponse.java) supplies both removed response override shapes; the recipe marks the declarations instead of creating duplicate replacements.
- [`Jahia/jahia` `ServletContextWrapper`](https://github.com/Jahia/jahia/blob/5e201521576ec5814b58321845915c3a984892d8/bundles/jahiamodule-extender/src/main/java/org/jahia/bundles/extender/jahiamodules/jsp/ServletContextWrapper.java) supplies the legacy `ServletContext.log(Exception,String)` delegate call and obsolete override shape.
- Apache Tomcat's own 10.0.27 [`ResponseFacade`](https://github.com/apache/tomcat/blob/ca8720d41f3be917dc3fcdd03fcca8d3152a13fb/java/org/apache/catalina/connector/ResponseFacade.java) and [`RequestFacade`](https://github.com/apache/tomcat/blob/ca8720d41f3be917dc3fcdd03fcca8d3152a13fb/java/org/apache/catalina/connector/RequestFacade.java) establish the compatibility-delegate relationships.

Tests follow OpenRewrite's pinned [`RewriteTest`/cycle assertions](https://github.com/openrewrite/rewrite/blob/fb933bdb74f2f4dc10ec79387e29aa8f5a8a9503/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java), [`ChangePackageTest`](https://github.com/openrewrite/rewrite/blob/fb933bdb74f2f4dc10ec79387e29aa8f5a8a9503/rewrite-java-test/src/test/java/org/openrewrite/java/ChangePackageTest.java), and [`SearchResult` marker](https://github.com/openrewrite/rewrite/blob/fb933bdb74f2f4dc10ec79387e29aa8f5a8a9503/rewrite-java/src/main/java/org/openrewrite/java/search/UsesType.java) patterns. Each fixture test identifies any namespace-only reduction in its comment.

## Known limits

The recipe does not infer effective versions from remote parents/BOMs/catalogs, edit external files, choose security limits, select a native/TLS architecture, coordinate a cluster shutdown, or prove runtime compatibility of Tomcat internals. It never treats a higher patch, minor or major version as an upgrade candidate for the 10.1.56 target, and it does not silently replace the approved target with 10.1.57. Those boundaries are visible MARK/NOOP behavior and are covered by exact-whitelist, target-conflict, interim-security, negative, ownership, generated-source, type-attribution, overload, lookalike, idempotence and aggregate-parity tests.
