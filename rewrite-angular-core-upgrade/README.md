# Angular Core 10–13 → 20.3.26

本模块对应 `开源软件升级.xlsx` 的 `@angular/core` 行。表格中可直接证明的源版本只有：

```text
10.0.14, 10.2.5, 11.2.14,
12.2.10, 12.2.13, 12.2.14, 12.2.16, 12.2.17,
13.1.3, 13.2.6
```

最后一个单元格写作 `13.2.6 ...（共 28 个版本）`；配方只接受其中可见的 `13.2.6`，不会猜测被省略的另外 27 个版本。目标版本是 `20.3.26`。

## 配方

推荐应用迁移配方：

```text
com.huawei.clouds.openrewrite.angular.MigrateAngularCoreTo20_3_26
```

它依次执行：严格依赖升级、确定性 TypeScript AST 迁移、JSON/TypeScript 风险标记。只希望改最终依赖声明时可使用：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularCoreTo20_3_26
```

依赖配方只改每个 `package.json` 顶层 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies` 中与表格版本锚定的精确、`^` 或 `~` 单一标量；比较器、OR、hyphen、workspace/npm 协议、变量、tag、未列版本、嵌套 metadata、catalog、override、resolution 和 lockfile 均不会被猜改。npm、Yarn、pnpm 的 lockfile 必须由所选包管理器在 review 后重建。

## 不兼容点与处理/测试映射

`AUTO` 表示已有官方 migration/API 证据且本模块在 AST/JSON 节点上做等价变换；`MARK` 表示在精确节点添加 `SearchResult`，要求业务选择；`NO-OP` 表示有意保持原文。

| 不兼容点 | 处理 | 配方行为 | 对应测试 |
| --- | --- | --- | --- |
| 表格所列精确/`^`/`~` 单一 `@angular/core` 版本 | AUTO | 四个直接依赖区改为 `20.3.26` | `upgradesEveryVisibleSpreadsheetSource`、`upgradesSafeScalarRangesFromRealWorkersListPokedexAndElasticFixtures`、`npmDependencyIsUpdatedWithoutInventingLockfileChanges`、`yarnDevDependencyIsUpdated`、`pnpmDependencyIsUpdated` |
| compound range/protocol/变量/tag/未列或较新版本 | NO-OP；推荐配方 MARK | 依赖配方不改；推荐配方标记该版本值与 lockfile 决策 | `preservesCompoundAndUnlistedRanges`、`preservesDynamicAndProtocolDeclarations`、`preservesUnlistedNewerAndTargetVersions`、`marksRangeAndCentralOwnershipWithoutUpgradingThem` |
| workspace catalog、pnpm override、Yarn resolution、嵌套 metadata | NO-OP；推荐配方 MARK | 不把共享版本误当直接依赖；中央 owner 在推荐配方中标记 | `doesNotRewriteCentralOwnersOrNestedMetadata`、`marksRangeAndCentralOwnershipWithoutUpgradingThem` |
| Angular framework package group 要与 core 同 patch | MARK | 标记 `@angular/common`、compiler、router、forms 等各自的版本值 | `marksPackageGroupToolchainAndRuntimeValues`、`apacheNifiExactFixtureMigratesButSiblingAngularPackagesDoNot` |
| Node/TypeScript 基线提升 | MARK | 标记 `engines.node` 和 TypeScript 依赖；目标要求 Node `^20.19.0 || ^22.12.0 || >=24.0.0`、TS `>=5.8 <6.0` | `marksPackageGroupToolchainAndRuntimeValues` |
| View Engine/ngcc 被移除 | MARK | 标记包含 `ngcc` 的 script；不擅自删除可能仍被旧库使用的安装步骤 | `marksRealNgccScriptAtItsJsonValue` |
| `TestBed.get` 被移除 | AUTO | 仅当 `TestBed` 确由 `@angular/core/testing` import 且无同名声明遮蔽时，AST 重命名为 `inject`；方法引用也处理 | `migratesBitpayStyleTestBedGetCall`、`migratesAliasedTestBedAndPropertyReference`、`conservativelyLeavesShadowedImportedName` |
| `DOCUMENT` 从 common 移到 core | AUTO/MARK | 只有独占/可别名的 import 自动改模块；混合 common import 标记，避免破坏其他 binding | `movesStandaloneDocumentImportToCore`、`movesAliasedStandaloneDocumentImportAndPreservesQuotes`、`marksMixedDocumentImportInsteadOfPartiallyRewritingIt` |
| `InjectFlags` 被移除 | MARK | 标记实际 `@angular/core` import；options 对象需要结合调用重载、flag 组合和 alias 决定 | `marksInjectFlagsAtTheImportFromRealTabbyFixture` |
| `entryComponents`、`ANALYZE_FOR_ENTRY_COMPONENTS` | MARK | 标记 metadata/import；先确认动态组件与 View Engine 库，再删除 | `marksEntryComponentsAtTheMetadataMemberFromRealFrappeFixture`、`marksInjectFlagsAtTheImportFromRealTabbyFixture`（同类移除 import 路径） |
| `TestBed.flushEffects`、Router current navigation | MARK | 在方法调用 AST 上提示 `TestBed.tick` 的更广行为和 signal 读取决策 | `marksTimingSensitiveTestAndRouterCallsIndividually` |
| `afterRender`、实验性 zoneless API、可选 `zone.js` | MARK | 标记 import/调用和 `zone.js` 值；要求选择稳定 API 并复测 scheduling/change detection | `marksPackageGroupToolchainAndRuntimeValues`、`marksTimingSensitiveTestAndRouterCallsIndividually` |
| SSR `bootstrapApplication` 需要 `BootstrapContext` | MARK | 仅在 `main.server.ts` 且参数少于 3 时标记调用；wrapper 参数/import 不能凭空推断 | `marksSsrBootstrapCallOnlyInServerEntryPoint` |
| SSR/prerender、custom builder、legacy workspace/tsconfig compiler 选项 | MARK | 在 `angular.json`/`workspace.json`/`tsconfig*.json` 的具体 member 标记 | `marksAngularWorkspaceAndTsconfigAtExactMembers` |
| standalone 默认、provider scope、`inject()` context、signals/effect、`ng-reflect-*`、错误传播、hydration/PendingTasks | NO-OP 或局部 MARK | 缺少组件边界、运行时和部署语义时不做全局替换；`providedIn` 特殊 scope、SSR 配置及已知 timing API 会局部标记 | `doesNotRenameUnrelatedGetMethod`、`recommendedRecipeCombinesDependencyAndDeterministicSourceMigration` |
| 幂等性 | AUTO/NO-OP | 第二 cycle 不产生变化 | `sharedDirectDeclarationsMoveTogetherAndAreIdempotent`、`deterministicSourceMigrationIsIdempotent` |

## 固定的上游证据

Angular 目标基线固定在官方提交 [`4d627600a9b096cb85a828fd3cea0ea27fb354aa`](https://github.com/angular/angular/commit/4d627600a9b096cb85a828fd3cea0ea27fb354aa)，提交信息为 `release: cut the v20.3.26 release`。本模块实现/标记的边界直接核对该提交中的：

- [`migrations.json`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/core/schematics/migrations.json)
- [`TestBed.get` migration](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/core/schematics/migrations/test-bed-get/test_bed_get_migration.ts)
- [`InjectFlags` migration](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/core/schematics/migrations/inject-flags/inject_flags_migration.ts)
- [`DOCUMENT` migration](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/core/schematics/migrations/document-core/document_core_migration.ts)
- [SSR bootstrap-context migration](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/core/schematics/migrations/add-bootstrap-context-to-server-main/migration.ts)

OpenRewrite 测试/API 基线固定在 [`openrewrite/rewrite@b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/commit/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)，对应本工程 `8.87.5`；用例结构参考该提交的 [`Assertions`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-javascript/src/main/java/org/openrewrite/javascript/Assertions.java) 和 [`ChangeMethodNameTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-javascript/src/integTest/java/org/openrewrite/javascript/ChangeMethodNameTest.java)。

## 固定真实仓 fixture

测试只摘取与迁移判断相关的最小 before，不复制整个项目；仓库和 commit 均固定：

| 仓库 fixture | 原始模式 | 预期 |
| --- | --- | --- |
| [apache/nifi@59cff970…](https://github.com/apache/nifi/blob/59cff970ca8b98ee51ae4418cf4de6830fa28c37/nifi-registry/nifi-registry-core/nifi-registry-web-ui/src/main/package.json#L45-L53) | 精确 `@angular/core: 11.2.14` | AUTO |
| [Jooszko/WorkersList@e343c5fb…](https://github.com/Jooszko/WorkersList/blob/e343c5fbb6896e2b8c8ef21871ecafe0275539c9/workers-list-client/package.json#L14-L22) | `^10.0.14` | AUTO |
| [HybridShivam/pokedex-angular-app@a39ca004…](https://github.com/HybridShivam/pokedex-angular-app/blob/a39ca00439e160069ea711ee98326288f9a1443e/package.json#L20-L28) | `~10.2.5` | AUTO |
| [elastic/apm-agent-rum-js@997138a3…](https://github.com/elastic/apm-agent-rum-js/blob/997138a38ab3253072e710a97343dea240447b7c/package.json#L76-L82) | `^12.2.17` | AUTO |
| [bitpay/wallet@ff2e9d05…](https://github.com/bitpay/wallet/blob/ff2e9d05b8157e2931824a20daa6d61c9425d02d/src/app/app.component.spec.ts#L55-L68) | imported `TestBed.get` | AUTO |
| [Eugeny/tabby@14e2d60b…](https://github.com/Eugeny/tabby/blob/14e2d60b9b6dee84a53c37f05eefeb803787de04/tabby-ssh/src/profiles.ts#L1-L64) | `InjectFlags.Optional` | MARK |
| [frappe/mobile-accounting@b83f4e15…](https://github.com/frappe/mobile-accounting/blob/b83f4e1569b1eb5d00658a04bff8a728e8cea6a0/src/app/app.module.ts#L37-L45) | `entryComponents` | MARK |
| [thelgevold/angular-samples@7852f85c…](https://github.com/thelgevold/angular-samples/blob/7852f85c2e9bb64fde4ca7c5ea9128814263bc91/package.json#L3-L10) | `postinstall: ngcc` | MARK |

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-core-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.MigrateAngularCoreTo20_3_26
```

审核 AUTO diff 和所有 `SearchResult` 后，按当前项目逐个 Angular major 运行官方 `ng update`，重建 npm/Yarn/pnpm lockfile，并执行 production/AOT build、strict template/type check、单元/E2E、SSR、hydration 以及实际采用的 zoned/zoneless 目标测试。本模块自身验证：

```bash
mvn -pl rewrite-angular-core-upgrade -am clean verify
```
