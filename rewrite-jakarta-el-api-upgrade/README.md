# Jakarta Expression Language API 6.0.1 migration

This module migrates the workbook-selected `jakarta.el:jakarta.el-api` upgrades to `6.0.1`. It is an executable
OpenRewrite migration, not only an incompatibility checklist: deterministic changes are applied automatically and
decisions that require application or container evidence are marked on the exact build or Java node.

## Workbook scope

The strict dependency recipe accepts exactly these two workbook rows:

| Workbook sequence | Excel row | Source | Target |
|---:|---:|---:|---:|
| 701 | 702 | `3.0.3` | `6.0.1` |
| 1920 | 1921 | `5.0.1` | `6.0.1` |

Other fixed versions, version ranges, dynamic versions, version catalogs, BOM-owned dependencies, classifiers and
non-JAR artifacts are deliberately not guessed. Maven project/profile declarations and direct root Gradle dependency
declarations are in scope. Generated, installed, cache and report trees are excluded.

## Public recipes

Use the recommended recipe for a migration:

```yaml
activeRecipes:
  - com.huawei.clouds.openrewrite.jakartael.MigrateJakartaElApiTo6_0_1
```

`com.huawei.clouds.openrewrite.jakartael.UpgradeJakartaElApiTo6_0_1` is the lower-level strict dependency-only recipe.
The recommended recipe always runs automatic changes before risk markers so comments describe the post-migration
source.

## Automatic changes

| Area | Transformation | Guardrails |
|---|---|---|
| Dependency | `jakarta.el:jakarta.el-api:{3.0.3,5.0.1}` → `6.0.1` | Exact workbook whitelist; exclusive Maven properties only; root Gradle string/map notation only |
| Java namespace | `javax.el` → `jakarta.el` recursively | Type-attributed Java source; generated/installed trees excluded |
| Removed typo | `MethodExpression.isParmetersProvided()` → `isParametersProvided()` | Calls and overrides of the EL type only; application methods with the same spelling are untouched |
| Java strings | `javax.el.*` → `jakarta.el.*` | Exact namespace prefix in string literals only |
| Resources | `javax.el.*` → `jakarta.el.*` in XML and plain text | `pom.xml` excluded so build ownership remains with the dependency recipe |
| ServiceLoader | `META-INF/services/javax.el.ExpressionFactory` → `.../jakarta.el.ExpressionFactory` | Only the standard factory descriptor is renamed |
| JPMS | `requires java.el` / `requires javax.el` → `requires jakarta.el` | Only real `module-info.java` directives; modifiers and formatting preserved; comments/literals ignored |

The namespace rule is necessary for the `3.0.3` source path. `5.0.1` already uses `jakarta.el`, so it naturally remains
unchanged by that step.

## Incompatibilities located for review

Markers are intentional review work, not generic warnings.

| Marker | Why it cannot be safely rewritten | Required decision and validation |
|---|---|---|
| `ELResolver.getFeatureDescriptors`, `TYPE`, `RESOLVABLE_AT_DESIGN_TIME` | Jakarta EL 6 removed this design-time metadata API | Remove the obsolete override/use or move metadata to an application tooling contract |
| `Object convertToType(..., Class<?>)` override | The contract is now `<T> T convertToType(..., Class<T>)`; a cast chosen by a recipe can hide type errors | Implement the generic contract and test `propertyResolved`, null, primitive/wrapper, enum and exception cases |
| EL expression containing array `.length` / `['length']` | Arrays now have a special `length` property, which can change resolver precedence and results | Test arrays, beans/maps with a `length` property, nulls and mixed resolver chains |
| `new StandardELContext(...)` | `RecordELResolver` is enabled by default | Test record components, collisions, read-only behavior and custom-resolver ordering |
| `ImportHandler` import/resolve calls | Jakarta EL 6 enforces module visibility | Verify `exports`/`opens`, public accessibility, overload selection and named-module deployment |
| `ExpressionFactory.newInstance()` | Provider discovery spans a namespace, Java baseline and implementation boundary | Verify the actual EL 6 implementation, service descriptor/system property, container ownership and duplicates |
| EL expression serialization | Serialized expression objects depend on namespace and implementation details | Version/rebuild cached payloads and test rolling upgrade compatibility |
| Java release below 17 | Jakarta EL 6 requires Java 17 | Upgrade compiler/runtime/toolchain together |
| legacy `javax.el` API beside the selected API | It creates split/duplicate contracts | Remove the legacy API or migrate its real owner and all consumers coherently |
| explicit GlassFish/Tomcat EL provider | The API artifact does not prove runtime compatibility | Align provider, Java 17, ServiceLoader/system property and container lifecycle |
| ranged, dynamic, catalog/BOM-owned or outside-workbook version | The current file does not own a deterministic selected version | Change the real owner deliberately and confirm `6.0.1` resolves |
| classifier/non-JAR variant | Publication of the required target artifact shape is not guaranteed | Confirm the target publishes that variant before changing it |

## Compatibility facts behind the recipe

The target specification records the Java 17 minimum, removed deprecated API, optional `java.desktop`, special array
`length`, optional `OptionalELResolver`, module visibility enforcement, SecurityManager cleanup and default record
resolver. The target JPMS descriptor therefore uses `requires static transitive java.desktop`; applications should not
blindly add a hard desktop dependency.

The public API comparison also shows two source-level breaks handled differently here:

- the old misspelled `isParmetersProvided()` has an unambiguous corrected method and is automatically renamed;
- removed resolver metadata and the generic `convertToType` override require design/type decisions, so they are marked.

## Validation checklist after running

1. Build and test with the real deployment JDK 17+ and container, not only an API-only unit-test classpath.
2. Inspect every `/*~~(...)~~>*/` and `<!--~~(...)~~>-->` marker; either implement the decision or document why it is safe.
3. Exercise expression evaluation for arrays, records, maps/beans, nulls, coercion failures, overloaded methods and
   imported statics/classes in both classpath and named-module deployments used in production.
4. Assert which `ExpressionFactory` provider is selected. Test ServiceLoader and the system property only if the
   application owns them; application servers commonly own provider lifecycle.
5. Invalidate or version serialized/cached `Expression`, `ValueExpression` and `MethodExpression` data across a rolling
   upgrade.
6. Run dependency convergence checks to ensure no `javax.el-api`, old GlassFish `javax.el`, duplicate API or incompatible
   provider remains.

## Fixed evidence

Official Jakarta EL sources and specification:

- [Jakarta EL 3.0.3 API commit](https://github.com/jakartaee/expression-language/tree/5f7181ac2dd07a9e697cbf003f1cc8065386bc70/api/src/main/java/javax/el)
- [Jakarta EL 5.0.1 API commit](https://github.com/jakartaee/expression-language/tree/0f9ddee067f39ddc43f69b2f84fdb65ac95ea460/api/src/main/java/jakarta/el)
- [Jakarta EL 6.0.1 API commit](https://github.com/jakartaee/expression-language/tree/38694bc161ea8f8608bfb99b612e6d1d71a2b1ea/api/src/main/java/jakarta/el)
- [Jakarta EL 6.0 specification appendix](https://github.com/jakartaee/expression-language/blob/38694bc161ea8f8608bfb99b612e6d1d71a2b1ea/spec/src/main/asciidoc/ELSpec.adoc)
- [Fixed 3.0.3-to-6.0.1 comparison](https://github.com/jakartaee/expression-language/compare/5f7181ac2dd07a9e697cbf003f1cc8065386bc70...38694bc161ea8f8608bfb99b612e6d1d71a2b1ea)
- [Fixed 5.0.1-to-6.0.1 comparison](https://github.com/jakartaee/expression-language/compare/0f9ddee067f39ddc43f69b2f84fdb65ac95ea460...38694bc161ea8f8608bfb99b612e6d1d71a2b1ea)

Real repository patterns used to shape the tests:

- [ZK `MethodExpressionImpl` historic spelling](https://github.com/zkoss/zk/blob/cd627f95834864bcacf2c9257053defcc7e6c7fd/zel/src/main/java/org/zkoss/zel/impl/MethodExpressionImpl.java)
- [Flowable custom `convertToType`](https://github.com/flowable/flowable-engine/blob/4998672ad3c53cd4a9bdf7712e0578e4d44d21a5/modules/flowable-engine-common/src/main/java/org/flowable/common/engine/impl/javax/el/TypeConverter.java)
- [HubSpot Jinjava custom resolver metadata](https://github.com/HubSpot/jinjava/blob/8ea4eddab5e198b8b85976c597851d6f8aa731a4/src/main/java/com/hubspot/jinjava/el/NoInvokeELResolver.java)
- [Open Liberty custom resolver](https://github.com/OpenLiberty/open-liberty/blob/72f3d2b210d82740bd709fd61aba18565c92dfa1/dev/com.ibm.ws.jsp.2.3/src/org/apache/jasper/el/ELResolverImpl.java)

OpenRewrite test style is based on the fixed upstream
[`UpgradeDependencyVersionTest`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java),
with additional strict ownership, negative, generated-tree, marker-precision, whole-project and two-cycle idempotence cases.
The module currently contains 147 tests, including standard-primary companion-audit gating, namespace lookalikes and nested-project isolation.
