# Bootstrap 5.3.6 migration recipes

This module upgrades only the `bootstrap` versions explicitly listed in `开源软件升级.xlsx` and separates a narrow dependency update from the recommended application migration.

## Recipes

| Recipe | Purpose |
| --- | --- |
| `com.huawei.clouds.openrewrite.bootstrap.UpgradeBootstrapTo5_3_6` | Upgrade only XLSX-selected direct `package.json` declarations. |
| `com.huawei.clouds.openrewrite.bootstrap.MigrateBootstrapTo5_3_6` | Run the strict update, deterministic static-markup and imported-source migrations, then add precise review markers. |

The exact whitelist is `3.4.1`, `4.6.0`, `4.6.1`, `4.6.2`, `5.2.0`, and `5.2.3`, all targeting `5.3.6`. Exact, caret, and tilde styles are preserved: `^4.6.2` becomes `^5.3.6`. No omitted or intermediate versions are inferred.

Comparators, OR and hyphen ranges, prereleases, build metadata, wildcards, variables, npm aliases, workspace/file/link/Git/HTTP protocols, unlisted versions, overrides, resolutions, scripts, and lockfiles are not version-rewritten. The recommended recipe marks an unresolved direct Bootstrap declaration instead.

## Deterministic automatic migrations

Automatic changes are limited to direct equivalents documented by Bootstrap's v5 migration guide and to files with Bootstrap ownership evidence.

- Static Bootstrap widget tags receive the `data-bs-*` namespace for known JavaScript attributes such as `data-toggle`, `data-target`, `data-dismiss`, `data-parent`, `data-ride`, `data-slide`, and `data-spy`. Application-specific `data-*`, dynamic bindings, comments, and tags without component evidence are unchanged.
- Directional utilities move to logical RTL-aware names: `float-left/right`, responsive float/text alignment, `border-left/right`, `rounded-left/right`, and nonnegative `ml/mr/pl/pr` utilities become their `start/end` forms.
- Direct helper equivalents are migrated: screen-reader, monospace, font weight/style, `no-gutters`, `custom-select`, `custom-range`, `badge-pill`, and responsive embed/ratio classes. The obsolete child `embed-responsive-item` token is removed.
- An imported Bootstrap plugin's `_getInstance()` call becomes `getInstance()`.
- Owned Tooltip and Popover `whiteList` options become `allowList` when the object has no spread or conflicting target option.
- A side-effect-only `bootstrap/js/dist/util` import is removed because v5 integrates those helpers into each plugin. Imports that consume util symbols are marked, not deleted.

## Incompatible changes marked for review

Markers are attached to the smallest provable manifest member, AST node, template fragment, selector, Sass token, or lock key.

- Bootstrap 5 dropped jQuery. jQuery dependencies, Bootstrap-owned `$(...).modal()/tooltip()/...` calls, and jQuery lifecycle listeners are marked for native component, event, cancellation, delegation, and disposal migration.
- Popper v1 (`popper.js`) changed to Popper 2 (`@popperjs/core`). Dropdown, Tooltip, and Popover construction and the changed `flip`, `boundary`, and fallback option boundaries are marked.
- ScrollSpy uses `IntersectionObserver` and deprecates the old offset model. Owned `offset` configuration is marked for root margin, threshold, nested scrolling, and activation tests.
- Bootstrap 5 replaced Libsass with Dart Sass. `node-sass`/Sass toolchains, partial imports, removed functions/mixins, variables, maps, import order, warnings, and custom selectors are marked.
- Bootstrap 4 form/custom-control and input-group markup requires structural changes. Bootstrap 3 panels, wells, glyphicons, pull utilities, default variants, and other components have no single safe token replacement.
- Removed badges, close buttons, tables, text hiding, rounding, block buttons, negative margins, and progress-bar accessibility markup are marked for contextual and visual migration.
- React, Angular, and Vue wrappers own component markup, peers, events, lifecycle, SSR/hydration, and styling. Their declarations are marked instead of guessing an unrelated wrapper version.
- Bootstrap 5 dropped Internet Explorer and legacy Edge. A Bootstrap manifest with a legacy browserslist entry is marked for build, polyfill, CSS prefix, test, and support-policy alignment.
- CDN and copied assets require an exact 5.3.6 filename, matching SRI/crossorigin/CSP, Popper bundle choice, source maps, license, deployment, and cache review.
- npm, Yarn, and pnpm lock entries are marked for regeneration. Resolved URLs, integrity hashes, peer snapshots, and package-manager metadata are never fabricated.

`node_modules`, `vendor`, `dist`, `build`, `out`, `generated`, `install`, `.next`, `.nuxt`, `.cache`, `.mvn`, `.m2`, `.yarn`, `coverage`, and `target` path components are protected from both changes and markers. Application-owned `public` sources remain in scope.

## Specification-to-recipe-to-test map

| Rule or evidence | Implementation | Regression coverage |
| --- | --- | --- |
| XLSX exact source set and target | `UpgradeSelectedBootstrapDependency` | `BootstrapDependencyTest`: all six exact/caret/tilde inputs, exact set equality, four dependency sections, 25 complex/dynamic/protocol negatives, protected paths, discovery, validation, and idempotency. |
| v5 data namespace, logical utilities, helpers/forms/ratio direct equivalents | `MigrateDeterministicBootstrapMarkup` | `BootstrapMarkupMigrationTest`: modal/collapse/dismiss/carousel/ScrollSpy, responsive RTL utilities, helpers, ownership negatives, comments, dynamic bindings, protected paths, and two-cycle idempotency. |
| `_getInstance`, `whiteList`, and removed side-effect util | `MigrateDeterministicBootstrapSource` | `BootstrapSourceMigrationTest`: named/deep aliases, quoted options, spreads/conflicts, same-name and shadowing negatives, formatting, protected paths, and idempotency. |
| Dependency ecosystem, browsers, npm locks, JSON aliases | `FindBootstrapJsonRisks` | `BootstrapJsonAndLockRiskTest`: jQuery, both Popper generations, Sass, six wrappers, browserslist ownership, npm v1/v3, preserved integrity, precise negatives, and exclusions. |
| Yarn and pnpm lock keys | `FindBootstrapTextLockRisks` | `BootstrapJsonAndLockRiskTest`: exact package keys, wrapper/scoped negatives, unchanged hashes, exclusions. |
| jQuery/events, internals, Popper, plugin options, source/config selectors | `FindBootstrapJavaScriptRisks` | `BootstrapJavaScriptRiskTest`: owned positives, local/unrelated negatives, source paths, executable config, generated trees, and idempotency. |
| Structural templates, Bootstrap 3, accessibility, Sass/CSS, CDN/assets | `FindBootstrapTemplateStyleRisks` | `BootstrapTemplateStyleRiskTest`: parameterized markup/style families, comment and modern negatives, assets, exclusions, and idempotency. |
| End-to-end real repository behavior | `MigrateBootstrapTo5_3_6` | `BootstrapRecommendedRecipeTest`: fixed Bootstrap v4/v5, RuangAdmin, migration-guide, and OpenRewrite fixtures; target NOOP, discovery, validation, and idempotency. |

## Pinned primary sources and real fixtures

- [Bootstrap v5.3.6 migration guide](https://github.com/twbs/bootstrap/blob/f849680d16a9695c9a6c9c062d6cff55ddcf071e/site/src/content/docs/migration.mdx) defines the data namespace, jQuery/Popper/Sass changes, logical utilities, forms, helpers, options, components, and accessibility boundaries.
- [Bootstrap v5.3.6 package metadata](https://github.com/twbs/bootstrap/blob/f849680d16a9695c9a6c9c062d6cff55ddcf071e/package.json) fixes the target version, public JS/CSS/Sass entries, and Popper peer contract.
- [Bootstrap v4.6.2 package metadata](https://github.com/twbs/bootstrap/blob/e5643aaa89eb67327a5b4abe7db976f0ea276b70/package.json) provides the real jQuery, Popper v1, node-sass, bundle, and distribution baseline.
- [RuangAdmin's fixed 404 template](https://github.com/indrijunanda/RuangAdmin/blob/0ab27d0024263bfa0d334850dd708ebb669d222e/404.html) supplies real collapse/dropdown triggers and directional spacing/float utilities.
- [OpenRewrite JavaScript import parser tests](https://github.com/openrewrite/rewrite-javascript/blob/9e3b820e6a44808b095bb7e3aab670fd67de99a5/rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java) supply fixed named-import, alias, spacing, and quote AST forms.

## Verification and use

From the repository root:

```shell
mvn -f rewrite-bootstrap-upgrade/pom.xml clean verify
```

Activate the recommended recipe by its fully qualified name with the OpenRewrite Maven or Gradle plugin. Review every `SearchResult`, regenerate the lockfile with the repository's own package-manager version, and run template compilation, Sass/CSS builds, type-checking, unit/integration tests, keyboard and screen-reader tests, responsive/RTL and browser tests, visual snapshots, SSR/hydration tests, and production-bundle checks before accepting the migration.
