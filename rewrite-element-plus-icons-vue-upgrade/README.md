# @element-plus/icons-vue 2.3.2 migration

This module turns the workbook entries for `@element-plus/icons-vue` into an executable, reviewable migration. It intentionally separates strict dependency selection, deterministic source changes, and contextual review markers.

## Workbook scope

| XLSX row | Sequence | Authorized source | Target |
|---:|---:|---:|---:|
| 396 | 395 | 0.2.4 | 2.3.2 |
| 3267 | 3266 | 2.0.10 | 2.3.2 |
| 3268 | 3267 | 2.1.0 | 2.3.2 |

Only scalar `version`, `^version`, and `~version` forms in the four root direct dependency sections are rewritten. Ranges, prereleases, aliases, protocols, catalogs, overrides, resolutions, lockfiles, arbitrary nested objects, and every unlisted version are protected.

## Recipes

- `com.huawei.clouds.openrewrite.elementplusiconsvue.UpgradeElementPlusIconsVueTo2_3_2` is the low-level, version-only recipe.
- `com.huawei.clouds.openrewrite.elementplusiconsvue.AuditElementPlusIconsVue2_3_2` adds no guesses; it places `SearchResult` markers on exact unresolved nodes/snippets.
- `com.huawei.clouds.openrewrite.elementplusiconsvue.MigrateElementPlusIconsVueTo2_3_2` is the recommended recipe: strict upgrade, deterministic source migration, then audit.

## Incompatibility spec and implemented behavior

| Incompatible boundary | Evidence | Recipe behavior | Status |
|---|---|---|---|
| 0.2.4 publishes per-icon `dist/es/*.mjs` and `dist/lib/*.js`; 2.3.2 publishes aggregate `dist/index.js`/`.cjs` through conditional exports | official 0.2.4 and 2.3.2 tarballs/package manifests | The immutable 0.2.4 inventory proves each supported filename/export pair, so default deep imports become root named imports while preserving local aliases. Aggregate `dist/*/index` imports/loaders become the root. Unknown paths, non-default binding shapes, and dynamic per-icon paths are marked rather than guessed. | AUTO + MARK |
| The documented all-icon plugin is the `/global` export and supports an optional prefix (default `ElIcon`) | official `src/global.ts` at v2.3.2 | Exact legacy `dist/global(.js/.cjs)` imports/loaders become `/global`; plugin use and global bundle imports are marked for prefix collision, bundle, SSR isolation, and hydration review. | AUTO + MARK |
| The root has named icon exports and no default export | official 2.3.2 `dist/types/components/index.d.ts` | Correct named imports are retained; default root imports, namespace imports, unknown internals, CommonJS, and dynamic loading boundaries receive precise markers. | NOOP + MARK |
| Official 0.2.4 exports are retained through 2.3.2; later releases add icons rather than remove those exports | fixed tarball type-index comparison | No supported named import is spuriously renamed. Legacy/missing individual paths and unknown subpaths are marked instead of inventing a removed-icon mapping. | VERIFIED NOOP + MARK |
| 2.3.2 peers on Vue `^3.2.0` | official 2.3.2 package manifest | Only exact/caret/tilde Vue 3.2+ scalars below Vue 4 are accepted as proven; comparators, unions, tags, older Vue and Vue 4 are marked. | MARK |
| Element Plus 2 replaces `el-icon-*` font classes/string props with Vue icon components | Element Plus icon documentation plus real project fixtures | Exact legacy tags, props, and CSS selectors are marked as snippets. Dynamic `<component :is>` and string `resolveComponent` boundaries are marked for registry/casing/SSR decisions. | MARK |
| Manual/global registration and auto-import plugins affect names, tree shaking, generated types, SSR, and hydration | real projects and official plugin API | Manual `app.component`, namespace enumeration, `/global` `app.use`, Element Plus, auto-import tooling, and risky `sideEffects:false` ownership are marked. | MARK |

The module never rewrites icon names from fuzzy similarity and never changes a template to a guessed component. That is deliberate: visual semantics and application registration ownership are not derivable from a package version.

Manifest relevance is derived from exact JSON member selectors, not whole-file text. A description containing the package name or a similar package such as `@element-plus/icons-vue-extra` cannot trigger Vue/plugin markers. Direct old selected versions are marked when the audit recipe is run alone; in the recommended recipe the strict AUTO step resolves them before audit. Central `overrides`/`resolutions`/pnpm selectors are recognized in qualified forms and SearchResult is placed on the actual owned value.

## Path safety

Visitors ignore cache/artifact parents including `node_modules`, `.pnpm`, `.yarn`, `.npm`, `.m2`, `.gradle`, build/output/coverage/report directories, and every lowercase-normalized parent beginning with `generated` or `install`. A legitimate leaf such as `src/install.ts`, `src/generated.ts`, or `src/package.json` remains eligible because only parent directories are filtered.

## Fixed evidence

Official package history is pinned, not read from a moving branch:

- `v0.2.4` commit [`b683e528117ea99ceb377656115a94136db985bd`](https://github.com/element-plus/element-plus-icons/tree/b683e528117ea99ceb377656115a94136db985bd/packages/vue), npm tarball SHA-1 `dadcf72f0cea53dc83b7b7db80e1418716d7b02c`.
- `v2.0.10` commit [`971f292c776717e6c682f3f806a4a85d97f709a1`](https://github.com/element-plus/element-plus-icons/tree/971f292c776717e6c682f3f806a4a85d97f709a1/packages/vue), tarball SHA-1 `60808d613c3dbdad025577022be8a972739ade21`.
- `v2.1.0` commit [`d688f75b2659bb4db1423474bdc5d4086b626f84`](https://github.com/element-plus/element-plus-icons/tree/d688f75b2659bb4db1423474bdc5d4086b626f84/packages/vue), tarball SHA-1 `7ad90d08a8c0d5fd3af31c4f73264ca89614397a`.
- target `v2.3.2` commit [`70f4518da60a339e142ce91440581a7868c48c41`](https://github.com/element-plus/element-plus-icons/tree/70f4518da60a339e142ce91440581a7868c48c41/packages/vue), tarball SHA-1 `7e9cb231fb738b2056f33e22c3a29e214b538dcf`.
- immutable tarballs: [`0.2.4`](https://registry.npmjs.org/@element-plus/icons-vue/-/icons-vue-0.2.4.tgz), [`2.0.10`](https://registry.npmjs.org/@element-plus/icons-vue/-/icons-vue-2.0.10.tgz), [`2.1.0`](https://registry.npmjs.org/@element-plus/icons-vue/-/icons-vue-2.1.0.tgz), [`2.3.2`](https://registry.npmjs.org/@element-plus/icons-vue/-/icons-vue-2.3.2.tgz).

Real public fixtures are also pinned:

- [`song-will/vue3-ts@15394b2`](https://github.com/song-will/vue3-ts/tree/15394b216eb0d5996b8dcc536dbfe00bd815d751) uses `^0.2.4` with named `ChatRound`, `Avatar`, and `Search` imports.
- [`Cqqgyh/newPermissionBaseLocalMock@a09126d`](https://github.com/Cqqgyh/newPermissionBaseLocalMock/tree/a09126d7163557f48e1742eeae7afac65359927f) uses `^2.0.10`, named icons, and dynamic `<component :is>` rendering.
- [`Kirsh-60/vite-vue3-ts-pinia_demo@75ae34d`](https://github.com/Kirsh-60/vite-vue3-ts-pinia_demo/tree/75ae34d3c1d9f834e058e878ae5ce22165a1ef3a) pins `^2.1.0`.

Test shape follows OpenRewrite JavaScript's fixed [`ImportTest` at `9e3b820`](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java): parser-native before/after assertions, exact markers, protected no-ops, and two-cycle idempotency.

## Verification

Run:

```bash
mvn -f rewrite-element-plus-icons-vue-upgrade/pom.xml clean verify
```

The suite covers all selected declaration forms and sections, complex/protocol/central-owner protection, generated/install/cache filtering, official layouts, pinned real-repository fixtures, deterministic static/dynamic/CommonJS entry normalization, source/template/manifest markers, correct no-ops, discovery/validation, and two-cycle convergence.

Current verification executes **110 tests**: 51 dependency/recipe tests, 26 deterministic source tests, and 33 manifest/source/template/marker tests.
