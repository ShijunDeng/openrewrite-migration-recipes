# @angular/common 迁移到 20.3.26

本模块对应 `开源软件升级.xlsx` 中的 `@angular/common`。配方只接受表格中**明确可见**的源版本：

```text
10.0.14, 10.2.5, 11.2.14, 12.2.10, 12.2.13, 12.2.14,
12.2.16, 12.2.17, 13.1.3, 13.2.6
```

表格把 `13.2.6` 后的内容折叠为“共 50 个版本”；本模块不会猜测其余 49 个值。目标版本固定为 `20.3.26`；只接受以上版本的精确、`^` 或 `~` 单一声明，不改 compound range 或协议声明。

## 配方

推荐在完整应用迁移中启用：

```text
com.huawei.clouds.openrewrite.angular.MigrateAngularCommonTo20_3_26
```

该配方先执行可证明安全的依赖、TypeScript 和 tsconfig 修改，再在不能由静态信息决定的位置写入精确 `SearchResult`。仅需严格依赖值替换时可使用兼容配方：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularCommonTo20_3_26
```

Angular framework 包必须按同一个 patch 对齐。先从当前版本逐个大版本运行官方 `ng update @angular/core@<major> @angular/cli@<major>`，验证每一阶段的官方 migrations，最后用本配方收敛并审查应用选择；锁文件始终交给项目原有包管理器重建。

## 不兼容点与处理证明

状态含义：`AUTO` 为确定性自动修改；`MARK` 为在准确 AST/JSON/HTML 位置提示人工决策；`NO-OP` 为有意保持原文，防止误改。

| 不兼容点或迁移边界 | 状态 | 配方行为 | 对应测试 |
| --- | --- | --- | --- |
| 表格可见的 10 个精确/`^`/`~` 单一版本升级到 `20.3.26` | AUTO | 只改 `package.json` 四个直接依赖区的标量值 | `AngularCommonDependencyTest.upgradesEveryVisibleSpreadsheetSource`、`upgradesSafeRangeFromRealMvsLightwalletFixture`、`supportsNpmYarnAndPnpmWorkspacePackageLocations` |
| npm/yarn/pnpm compound range、workspace/protocol、变量、tag、未列出或较新版本 | MARK / NO-OP | 依赖升级器保持原值；推荐配方标记约束选择和锁文件重建 | `preservesAllConstraintAndDynamicForms`、`marksRangeAndCentralVersionOwnerWithoutChangingThem` |
| catalog、overrides、resolutions、pnpm 中央版本与嵌套 metadata | MARK / NO-OP | 不把中央 owner 当直接依赖改写；在 owner 值精确标记原子升级要求 | `leavesCentralOwnersAndNestedMetadataUntouched`、`marksRangeAndCentralVersionOwnerWithoutChangingThem` |
| `@angular/core` 等 framework 包必须和 common 精确对齐 | MARK | 标记未到 `20.3.26` 的 sibling Angular 声明 | `apacheNifiPinnedFixtureMigratesOnlyAngularCommon`、`marksAngularPackageGroupToolchainRuntimeAndNgccValues` |
| Angular 20.3.26 的 Node、TypeScript、RxJS 支持矩阵 | MARK | 标记 `engines.node`、`typescript`、`rxjs` 的实际声明 | `marksAngularPackageGroupToolchainRuntimeAndNgccValues`、`scopesPackageToolchainMarkersToAngularCommonConsumers` |
| Ivy-only，View Engine/ngcc 被移除 | AUTO / MARK | 删除根级 `angularCompilerOptions.enableIvy: true`；保留并标记 `false`，标记 ngcc 脚本 | `removesOnlyEnableIvyTrueFromAngularCompilerOptions`、`keepsEnableIvyFalseNestedAndNonTsconfigValues`、`marksWorkspaceBuildersSsrAndCompilerOptions` |
| `DOCUMENT` 从 `@angular/common` 移到 `@angular/core` | AUTO / MARK | 单独 import 自动移动并保留 alias/引号；mixed import 标记为需要 split/merge | `movesStandaloneDocumentFollowingOfficialAngularMigration`、`preservesDoubleQuotesAndDocumentAlias`、`marksDocumentOnlyWhenMixedBecauseStandaloneImportIsAutoSafe` |
| `XhrFactory` 从 `@angular/common/http` 移到 `@angular/common` | AUTO / MARK | 单独 import 自动移动；mixed HTTP import 精确标记 | `movesStandaloneXhrFactoryFollowingAngularCommonExportMove`、`preservesDoubleQuotesAndXhrFactoryAlias`、`marksMixedXhrFactoryImportFromPinnedNativeScriptFixture` |
| `isPlatformWorkerApp` / `isPlatformWorkerUi` 随 WebWorker platform 移除 | MARK | 标记包含被移除 export 的 import | `marksRemovedWorkerApis` |
| `LocationStrategy` 合约、`BrowserPlatformLocation` 依赖与测试 location 默认行为改变 | MARK | 标记 production import 与 `MockPlatformLocation` test import | `marksLocationAndTestLocationBehavior` |
| `NgTemplateOutlet` context 严格类型检查 | MARK | 同时标记 TypeScript import 与 `*ngTemplateOutlet` 模板片段 | `marksStrictTemplateOutletAndOptimizedImageChoices`、`marksTemplateOutletAndAsyncPipe` |
| `NgSwitch` 匹配从宽松相等改为严格相等；v20 control-flow directives 废弃 | MARK | 标记 `NgIf/NgFor/NgSwitch` import 和每个旧结构指令片段；不猜测 `@for track` | `marksAsyncPipeAndControlFlowImports`、`marksNgSwitchCaseFromPinnedPoeOverlayFixture`、`marksEveryDeprecatedStructuralDirectiveAtItsSnippet` |
| v20 对 `Y` 且没有 `w` 的 week-year 格式报错 | MARK / NO-OP | 标记 `formatDate`/DatePipe 的可疑字符串及模板 date pipe；`y` 和 `Y...w` 保持不变 | `marksSuspiciousFormatDateWeekYearAtTheLiteral`、`supportsAliasedFormatDateAndLikelyDatePipeButNotSafeFormats`、`marksSuspiciousDatePipeFormatButNotCalendarOrWeekFormats` |
| `AsyncPipe` 将未处理错误直接报告给应用 `ErrorHandler` | MARK | 标记 AsyncPipe import 和模板 `| async` 片段，要求确认错误所有权 | `marksAsyncPipeAndControlFlowImports`、`marksTemplateOutletAndAsyncPipe` |
| locale/CLDR、currency rounding、timezone、首日/周末语义跨版本变化 | MARK | 标记 currency/decimal/percent/date pipe、locale registration/查询 API import | `marksLocaleCurrencyAndDatePipeBusinessSemantics` |
| `HttpClientModule`、XSRF module、class interceptor 配置迁向 provider API | MARK | 标记旧 HTTP module/token import，要求显式保持 interceptor/XSRF 顺序 | `marksLegacyHttpModuleFromPinnedNgcValidateFixture`、`marksHttpXsrfInterceptorsAndTestingProviderOrdering` |
| `HttpClientTestingModule` 废弃且测试 provider 有顺序要求 | MARK | 标记 testing module import，提示先 `provideHttpClient` 后 `provideHttpClientTesting` | `marksHttpXsrfInterceptorsAndTestingProviderOrdering` |
| `NgOptimizedImage` 的尺寸、loader/CDN、priority、preconnect、SSR preload/hydration | MARK | 标记 import 和 `ngSrc` 模板片段，不臆造业务 loader | `marksStrictTemplateOutletAndOptimizedImageChoices`、`marksOptimizedImageAttributionAndNgReflectDependency` |
| v20 默认不再生成 `ng-reflect-*` | MARK | 精确标记模板中的属性名，要求改为公开 DOM 行为或 harness | `marksOptimizedImageAttributionAndNgReflectDependency` |
| custom builder、SSR/server/prerender 与 hydration/HTTP transfer cache | MARK | 在 `angular.json`/`workspace.json` 精确标记 builder 与目标成员 | `marksWorkspaceBuildersSsrAndCompilerOptions` |
| 非 Angular package、非 HTML、现代 `@if/@for`、同名本地符号 | NO-OP | 路径和 import attribution 约束避免误报 | `scopesPackageToolchainMarkersToAngularCommonConsumers`、`ignoresSimilarTextOutsideHtmlFiles`、`leavesModernBuiltInControlFlowTemplateAlone`、`doesNotRewriteSameNamesFromUnrelatedPackagesOrDefaults` |
| 多次运行 | NO-OP | 自动修改第二轮不再产生 diff，模板 marker 不重叠/重复 | `directSectionsMoveTogetherAndRecipeIsIdempotent`、`sourceMigrationIsIdempotent`、`configurationMigrationIsIdempotent`、`emitsNonOverlappingMarkersAndIsIdempotent` |

## 固定上游依据与真实用例

官方语义以 Angular `v20.3.26` release cut 固定提交 [`4d627600a9b096cb85a828fd3cea0ea27fb354aa`](https://github.com/angular/angular/tree/4d627600a9b096cb85a828fd3cea0ea27fb354aa) 为基线：

- [完整 CHANGELOG](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/CHANGELOG.md) 用于核对 v14–v20 的 common breaking changes/deprecations；
- [官方 DOCUMENT migration](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/core/schematics/migrations/document-core/document_core_migration.ts) 用于确定可自动迁移的 import；
- [`@angular/common` package.json](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/common/package.json) 用于核对 core/RxJS peer 和 Node engines。

OpenRewrite TypeScript 测试结构参考固定提交 [`openrewrite/rewrite@b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)，包括其 [`ChangeMethodNameTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-javascript/src/integTest/java/org/openrewrite/javascript/ChangeMethodNameTest.java) 和 [`Assertions`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-javascript/src/main/java/org/openrewrite/javascript/Assertions.java)。

测试中的公开仓样例全部固定 commit，避免默认分支漂移：

| 仓库固定提交 | 提取内容 | 本模块用例 |
| --- | --- | --- |
| [apache/nifi@59cff970](https://github.com/apache/nifi/blob/59cff970ca8b98ee51ae4418cf4de6830fa28c37/nifi-registry/nifi-registry-core/nifi-registry-web-ui/src/main/package.json) | 精确 `@angular/common: 11.2.14` 与 sibling Angular packages | exact dependency before→after，siblings no-op |
| [mvs-org/lightwallet@c95b8c9b](https://github.com/mvs-org/lightwallet/blob/c95b8c9b7215f76d028393a72ff1c60d1040379d/package.json) | `^10.0.14` 单一 range | dependency before→after |
| [NativeScript/nativescript-angular@352c5a9d](https://github.com/NativeScript/nativescript-angular/blob/352c5a9d0fc7a7b1cb1ffea0c14e808d7a4801ea/nativescript-angular/http-client/ns-http-backend.ts) | mixed `XhrFactory` HTTP import | precise marker |
| [wardbell/ngc-validate@9d4d5bd8](https://github.com/wardbell/ngc-validate/blob/9d4d5bd89c1b37f596a1722eec28c3001e6ba7e5/src/main.ts) | `HttpClientModule` bootstrap import | provider migration marker |
| [kainonly/litho@6fc0950a](https://github.com/kainonly/litho/blob/6fc0950a73e0c698a00ec9eb82861a7f43962f43/src/shared/pipes/date.pipe.ts) | Angular common date formatting | suspicious format marker attribution |
| [Kyusung4698/PoE-Overlay@c961eabb](https://github.com/Kyusung4698/PoE-Overlay/blob/c961eabbf423099aaa4c7870ac7641f712d9a7f5/src/app/app.component.html) | legacy `ngSwitch` template | HTML snippet marker |

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-common-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.MigrateAngularCommonTo20_3_26
```

审查所有 `SearchResult` 与 patch 后执行 `run`，逐阶段运行 Angular build、strict template/type check、单元测试、HTTP 测试、SSR/hydration 和端到端测试，并用原包管理器重建锁文件。

本模块验证命令：

```bash
mvn -pl rewrite-angular-common-upgrade -am clean verify
```
