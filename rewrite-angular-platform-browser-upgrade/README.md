# @angular/platform-browser 迁移到 20.3.26

本模块对应 `开源软件升级.xlsx` 中的 `@angular/platform-browser`，提供依赖升级、确定性源码/配置迁移，以及无法安全自动决策位置的精确标记。推荐配方是：

```text
com.huawei.clouds.openrewrite.angular.MigrateAngularPlatformBrowserTo20_3_26
```

## 表格版本边界

只接受表格中可见的源版本：

```text
10.0.14, 10.2.5, 11.2.14,
12.2.10, 12.2.13, 12.2.14, 12.2.16, 12.2.17,
13.1.3, 13.2.6
```

表格最后一格显示 `13.2.6 …（共 28 个版本）`，但没有展开其余版本值。为避免推断并误改不在清单中的工程，本模块只把可见值 `13.2.6` 作为源版本；其余版本应在表格给出精确值后再加入测试矩阵。

支持上述版本的精确、`^`、`~` 三种声明，例如 `12.2.17`、`^12.2.17`、`~12.2.17`；目标统一固定为 `20.3.26`。

## 配方

| 配方 | 用途 |
| --- | --- |
| `UpgradeAngularPlatformBrowserTo20_3_26` | 只升级表格命中的直接依赖声明 |
| `MigrateDeterministicPlatformBrowserTo20` | 执行可证明安全的 TypeScript 与 tsconfig 修改 |
| `AuditAngularPlatformBrowser20Source` | 标记 TypeScript 中需业务判断的风险 |
| `AuditAngularPlatformBrowser20Project` | 标记 package/workspace/tsconfig 风险 |
| `AuditAngularPlatformBrowser20Templates` | 标记 HTML hydration、调试属性、手势和不安全 URL 风险 |
| `MigrateAngularPlatformBrowserTo20_3_26` | 依次组合以上全部能力，建议使用 |

完整名称前缀均为 `com.huawei.clouds.openrewrite.angular.`。

## 自动修改（AUTO）

| 自动处理 | 安全边界 | 对应用例 |
| --- | --- | --- |
| 升级 `package.json` 中 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies` 的直接 `@angular/platform-browser` | 只处理表格源版本的 exact/`^`/`~` 标量；写成精确 `20.3.26` | `UpgradeAngularPlatformBrowserTest` 的完整版本/声明/依赖区矩阵 |
| 把仅包含 `ApplicationConfig`、`TransferState`、`StateKey`、`makeStateKey` 的 platform-browser 命名导入移到 `@angular/core` | TypeScript AST 修改；保留 alias、引号；混合导入和已有同名 core binding 不猜测 | `AngularPlatformBrowserSourceMigrationTest` |
| 删除 `tsconfig*.json` 根级 `angularCompilerOptions.enableIvy: true` | 只删除布尔值 `true`；不改 `false`、嵌套同名对象或其他 JSON 文件 | `AngularPlatformBrowserSourceMigrationTest` |

确定性迁移是幂等的，多次运行不会重复修改。

## 只标记（MARK）

标记使用 OpenRewrite `SearchResult`，落在具体 import、调用、JSON member 或 HTML snippet 上，不把整份文件笼统标红。

| 不兼容点 | 为什么不能直接改 | 配方行为与测试 |
| --- | --- | --- |
| Angular framework 包版本锁步 | core/common/animations/compiler/router/platform-server 等必须结合官方逐 major migrations 一起升级 | 标记所有未到 `20.3.26` 的 Angular 同组直接依赖；14 个包逐项测试 |
| `platformBrowserDynamic()` 废弃 | AOT 可用 `platformBrowser`，真正 JIT 工程还需显式 compiler，无法从单个调用判断 | 精确标记导入绑定的调用与 dynamic 依赖 |
| standalone/bootstrap 与 SSR | server bootstrap 需要逐请求 `BootstrapContext`，provider 顺序和平台复用有运行时语义 | 标记 server `bootstrapApplication` 和相关入口 |
| hydration | `provideClientHydration` 的 event replay、增量边界和 DOM 一致性依赖应用结构 | 标记绑定调用和 `ngSkipHydration` 模板位置 |
| `BrowserModule`/`withServerTransition` | 根模块、feature/lazy module 和旧 Universal 配置需要一起判断 | 标记 import 与准确调用节点 |
| HammerJS | 内建集成在 Angular 20 废弃，替代方案取决于产品手势需求 | 标记 Hammer import、依赖和 `(tap/swipe/pan/pinch/press/rotate*)` 模板事件 |
| EventManager 插件 | 插件顺序、zone/zoneless、SSR 和 listener cleanup 是业务行为 | 标记 EventManager import |
| legacy animations | Angular 20.2 废弃旧 provider/module，模板迁移不能由 import 名机械推导 | 标记 animations/async import |
| browser testing/Protractor/debug tools | 测试环境是全局状态，debug 工具不能泄漏到生产或 SSR | 标记 testing import、testability/debug import 与调用 |
| `DomSanitizer.bypassSecurityTrust*` | 是否可信取决于数据来源、CSP 与 Trusted Types 边界 | 有 DomSanitizer 绑定时标记具体 bypass 调用 |
| `TransferState` | key、序列化、缓存生命周期和 HTML 敏感信息暴露需业务判断 | 有 TransferState 绑定时标记具体读写/序列化调用 |
| `Title`、`Meta`、样式销毁和 platform metadata | SSR、生产诊断与 teardown 行为不同 | 标记相关 platform-browser import |
| Node/TypeScript/zone.js/RxJS/ngcc | 运行时、编译器和异步模型需要整体迁移 | 标记准确 dependency/engine/script member |
| Angular CLI/builders/workspace SSR | custom builder、`browserTarget`、SSR/prerender schema 跨 major 变化 | 标记 `angular.json`/`workspace.json` 准确 member |
| Ivy/template/compiler options | `enableIvy:false`、strictTemplates、partial compilation 与 module emit 取决于应用/库产物 | 标记相应 `tsconfig*.json` member |
| `ng-reflect-*` | Angular 20 默认不再输出，测试应改为 role/harness/data attribute | 标记 HTML 属性文本 |
| `javascript:` URL | 自动替换可能改变交互语义 | 标记准确 `href`/`src` 片段，要求改为事件与可信导航 |

## 刻意不修改（NO-OP）

- compound/comparator/OR/hyphen 范围以及 `workspace:`、`npm:`、`file:`、GitHub、tag、`${...}`、`catalog:` 等动态声明：保留并标记 owner。
- 不在表格可见源版本中的普通标量：保持不变；推荐配方在原声明明确标记，避免静默漏项。
- `package-lock.json`、其他 lockfile、overrides/resolutions、依赖元数据和名称相似的 JSON：不改。
- `@angular/platform-browser-dynamic`：不机械替换依赖或 API，只标记废弃/JIT 决策。
- 混合 platform-browser import、default import、namespace import、已有同名 core binding：不拆分，避免重复绑定或改变格式/语义。
- `enableIvy:false`：不删除，标记为必须先清除 View Engine 依赖的问题。
- HTML 注释、非 HTML 文件、同名本地 TypeScript 函数/方法：不标记。
- 不运行 `ng update`、不重建 lockfile，也不替应用选择 zoned/zoneless、SSR adapter、hydration features 或手势库。

## 上游事实依据（固定版本）

目标事实固定到 Angular `20.3.26` commit [`4d627600a9b096cb85a828fd3cea0ea27fb354aa`](https://github.com/angular/angular/tree/4d627600a9b096cb85a828fd3cea0ea27fb354aa)：

- [`packages/platform-browser/package.json`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/platform-browser/package.json)：目标版本、core/common/animations peer 与 Node engine。
- [`browser.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/platform-browser/src/browser.ts)：BrowserModule、bootstrap 与兼容导出。
- [`core/transfer_state.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/core/src/transfer_state.ts)：TransferState、StateKey、makeStateKey 的 core 所有权。
- [`platform_providers.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/platform-browser-dynamic/src/platform_providers.ts)：dynamic/JIT 平台路径。
- [`hammer_gestures.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/platform-browser/src/dom/events/hammer_gestures.ts) 与 [`event_manager.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/platform-browser/src/dom/events/event_manager.ts)：手势和事件插件边界。
- [`hydration.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/platform-browser/src/hydration.ts)：客户端 hydration 公共 provider/features。
- [`dom_sanitization_service.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/platform-browser/src/security/dom_sanitization_service.ts)：DomSanitizer 与 bypass API。

测试风格固定参考 OpenRewrite `8.87.5` commit [`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)，包括 [`RewriteTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java) 的 before/after、幂等 cycle、marker 与 recipe discovery 断言方式。

## 真实仓库回归样本（固定 commit）

- [`HybridShivam/pokedex-angular-app@a39ca00/package.json`](https://github.com/HybridShivam/pokedex-angular-app/blob/a39ca00439e160069ea711ee98326288f9a1443e/package.json)：`~10.2.5` 依赖组。
- [`apache/nifi@59cff97/.../package.json`](https://github.com/apache/nifi/blob/59cff970ca8b98ee51ae4418cf4de6830fa28c37/nifi-registry/nifi-registry-core/nifi-registry-web-ui/src/main/package.json)：`11.2.14` 精确依赖。
- [`elastic/apm-agent-rum-js@997138a/package.json`](https://github.com/elastic/apm-agent-rum-js/blob/997138a38ab3253072e710a97343dea240447b7c/package.json)：`^12.2.17` devDependency。
- [`maciejtreder/ng-toolkit@eab11d4` TransferState resolver](https://github.com/maciejtreder/ng-toolkit/blob/eab11d43df4efc6e604d84cfc63d589872007cb2/application/src/app/services/resolvers/hitWithTransferState.resolver.ts)：platform-browser 到 core 的真实导入迁移。
- [`bullhorn/career-portal@c450bae/src/app/app.module.ts`](https://github.com/bullhorn/career-portal/blob/c450bae71461bf667ebb480456d8ef920689a9d1/src/app/app.module.ts)：`BrowserModule.withServerTransition` 形态。
- [`woodstream/appetite@39ebc6e/src/providers/common/util.ts`](https://github.com/woodstream/appetite/blob/39ebc6e8e3721f0b04a50216e16a4c91baff7a67/src/providers/common/util.ts)：DomSanitizer bypass 调用形态。

样本复制为最小、可解析 fixture；commit 与原路径写在测试中，既避免网络测试不稳定，也保留来源可追溯性。

## 测试覆盖

| 测试类 | 数量 | 覆盖重点 |
| --- | ---: | --- |
| `UpgradeAngularPlatformBrowserTest` | 63 | 10 个表格版本、exact/`^`/`~`、4 个依赖区、真实 manifest、负例/幂等/发现 |
| `AngularPlatformBrowserSourceMigrationTest` | 21 | AST import/config 自动迁移，bootstrap/hydration/sanitizer/EventManager/BrowserModule/animations/testing/SSR/TransferState 标记 |
| `AngularPlatformBrowserProjectMigrationTest` | 61 | complex/dynamic 声明、lockstep/toolchain/workspace/tsconfig/template、组合配方与 discovery |
| 合计 | **145** | 所有测试必须零失败 |

验证命令：

```bash
mvn -pl rewrite-angular-platform-browser-upgrade -am clean verify
```

## 使用

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-platform-browser-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.MigrateAngularPlatformBrowserTo20_3_26
```

确认 patch 和所有 `SearchResult` 后再运行 `run`。随后按 major 顺序执行 Angular 官方 migrations，统一 Angular package group，重建 lockfile，并验证 browser/server build、strict templates、单元/E2E、SSR/hydration、CSP/Trusted Types 与真实浏览器交互。
