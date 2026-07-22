# @angular/animations 迁移到 20.3.26

本模块对应 `开源软件升级.xlsx` 中的 `@angular/animations`。它先满足表格要求，把命中的直接依赖严格升级到 `20.3.26`；同时针对 Angular 20.2 已废弃的 legacy animations engine，执行少量可证明安全的 AST 修改，并在其余需要业务判断的位置留下精确 `SearchResult`。

推荐配方：

```text
com.huawei.clouds.openrewrite.angular.MigrateAngularAnimationsTo20_3_26
```

## 表格版本边界

只接受表格明确可见的源版本：

```text
10.0.14, 10.2.5, 11.2.14,
12.2.10, 12.2.13, 12.2.14, 12.2.16, 12.2.17,
13.1.3, 13.2.6
```

表格对同组 Angular 软件存在折叠版本格，但没有给出被折叠版本的具体值。本模块不推断这些值；只把明确可见的 `13.2.6` 纳入严格矩阵。每个版本仅支持 exact、`^`、`~` 三种声明，例如 `12.2.17`、`^12.2.17`、`~12.2.17`。

## 配方

| 配方 | 用途 |
| --- | --- |
| `UpgradeAngularAnimationsTo20_3_26` | 严格升级表格命中的直接依赖 |
| `MigrateDeterministicAngularAnimationsTo20` | 执行绑定归属明确、语义等价的 TypeScript 修改 |
| `AuditAngularAnimations20Source` | 标记 legacy DSL、provider/module、builder/player、测试和 SSR 风险 |
| `AuditAngularAnimations20Project` | 标记 package/workspace/toolchain/第三方消费者风险 |
| `AuditAngularAnimations20TemplatesAndStyles` | 标记 legacy 模板绑定以及 native animate/CSS 生命周期风险 |
| `MigrateAngularAnimationsTo20_3_26` | 依次组合以上全部能力，建议使用 |

完整名称前缀均为 `com.huawei.clouds.openrewrite.angular.`。

## 自动修改（AUTO）

| 自动处理 | 严格安全边界 | 对应用例 |
| --- | --- | --- |
| 把 `package.json` 四个直接依赖区中的 `@angular/animations` 更新为 `20.3.26` | 只处理表格源版本的 exact/`^`/`~` 字符串；不模糊匹配 | `UpgradeAngularAnimationsTest` 的完整版本、声明、依赖区矩阵 |
| 把只包含 `ANIMATION_MODULE_TYPE` 的 `@angular/platform-browser/animations` 命名导入移到 `@angular/core` | TypeScript AST；保留 alias/引号；混合 import、default/namespace import、已有同名 core binding 不改 | `AngularAnimationsSourceMigrationTest` |
| `provideAnimationsAsync('animations')` 简化为 `provideAnimationsAsync()` | 仅当函数绑定来自 `@angular/platform-browser/animations/async`，且参数是唯一精确字符串字面量 | `AngularAnimationsSourceMigrationTest` |
| 删除 `BrowserAnimationsModule.withConfig({...})` 中精确的 `disableAnimations: false` | 仅当模块绑定来自 Angular 官方 entry point，值是布尔字面量 `false`；`true`、变量和同名本地 API 不改 | `AngularAnimationsSourceMigrationTest` |

后两项只清理冗余配置，并不表示 legacy provider 已完成 native 迁移；审计配方仍会标记它们。所有自动修改均有幂等测试。

## 只标记（MARK）

| 不兼容点 | 为什么不能机械重写 | 标记位置 |
| --- | --- | --- |
| 整个 `@angular/animations` legacy package 自 20.2 废弃，计划 v23 移除 | 依赖升级到 20.3.26 只是过渡；删除包前必须确认应用和第三方均不再使用 engine | 直接依赖 member |
| trigger/state/transition/style/animate | 状态表达式、`:enter`/`:leave`、中断和最终样式需要逐组件设计 | import、具体 DSL 调用、component `animations` metadata |
| query/stagger/group/sequence/keyframes/animation/useAnimation/animateChild | selector 可选性、顺序、并行、子动画与 reusable 参数没有一一对应的 CSS 变换 | import 与每个具体调用 |
| `BrowserAnimationsModule`、`NoopAnimationsModule`、`provideAnimations*` | 第三方组件、全局 renderer、测试时序和 disabled 模式需整体迁离 | import、provider 调用、`withConfig` |
| `AnimationBuilder`/`AnimationFactory` | Web Animations/CSS/第三方库的 build、cancel、finish、destroy 所有权不同 | import 与已归属 builder/factory 调用 |
| `AnimationPlayer`/`NoopAnimationPlayer` | play/pause/finish/destroy/position 会改变 callback 顺序和 DOM teardown | import 与已归属 player 调用 |
| `AnimationEvent`、`(@trigger.start/done)` | legacy phase/timing/event shape 不等同于 DOM event 或新的 `AnimationCallbackEvent` | import 与准确模板 callback |
| browser driver/testing internals | MockAnimationDriver/Player 的测试锁定旧 engine 实现细节 | `@angular/animations/browser[/testing]` import |
| route animation metadata | outlet DOM、导航方向、lazy route 和并发导航属于业务语义 | legacy API 文件中的 `animation` metadata |
| `@.disabled` | 子树继承与 native CSS/reduced-motion 不等价 | 准确模板属性/绑定 |
| native `animate.enter`/`animate.leave` | class 只保留到最长 timeline 结束；function callback 还必须完成/取消，leave 控制 DOM 删除 | 准确模板属性、绑定、callback |
| `@starting-style`、transition、keyframes、animation properties | cascade、浏览器支持、最长 timeline、fill、interrupt 和最终样式决定 Angular 清理时间 | CSS/SCSS/Sass/Less 准确 token |
| `prefers-reduced-motion` | 动画关闭后仍要保证 DOM removal、焦点和业务 callback 不挂起 | media feature token |
| View Transitions | 跨导航/文档状态、fallback、并发导航与 SSR 需要应用验证 | `::view-transition-*()` token |
| Angular framework package group | animations 的 peer core 及同组框架包必须锁步到 20.3.26 | 14 个 Angular 直接依赖逐项标记 |
| Angular CLI/TypeScript/Node/zone.js | 仅不兼容 CLI/TypeScript/Node 标量会标记；zone.js 仍标记 zoned/zoneless 时序决策 | 精确 dependency/engine member；兼容工具链 no-op 测试 |
| web-animations-js | 全局 polyfill 是否仍需保留取决于浏览器矩阵 | package dependency 与 workspace polyfills member |
| Material/CDK/PrimeNG/ngx-bootstrap/ngx-toastr 等 | 这些第三方 UI 可能仍要求 legacy renderer | 8 类第三方直接依赖 member |
| custom builder、SSR/prerender | 自定义 CSS pipeline 和 server 初始 DOM/style 影响 native 迁移与 hydration | angular/workspace JSON member |

HTML/CSS 注释和非目标文件类型不会产生标记。

## 刻意不修改（NO-OP）

- comparator、OR、hyphen、通配等复杂范围，以及 `workspace:`、`npm:`、`file:`、GitHub、tag、`${...}`、`catalog:` 声明：保留并标记 owner。
- 不在表格可见源版本中的普通标量：升级配方保持不变；审计配方仍提示 legacy package 需要最终移除。
- lockfile、overrides/resolutions、peer metadata、普通 JSON 和名称相似的包：不改。
- 混合 `ANIMATION_MODULE_TYPE` import：不自动拆分，避免重复本地 binding；精确标记。
- `provideAnimationsAsync('noop')`、动态 mode、`disableAnimations:true` 或动态值：行为是有意配置，不猜测。
- legacy trigger/DSL、BrowserAnimationsModule/NoopAnimationsModule、provider、AnimationBuilder/Player：不会自动改写为 native CSS。
- `[@trigger]`、callback、`@.disabled`：没有跨 TypeScript/template/style 的可靠机械转换，不自动改。
- native `animate.enter/leave` 和 CSS：只做语义审计，不重写用户的 class、时长、easing、keyframes 或 reduced-motion 策略。
- 不运行 `ng update`、不重建 lockfile，也不替应用选择 Web Animations、CSS、View Transitions 或第三方库。

## 上游事实依据（固定版本）

目标事实固定到 Angular `20.3.26` commit [`4d627600a9b096cb85a828fd3cea0ea27fb354aa`](https://github.com/angular/angular/tree/4d627600a9b096cb85a828fd3cea0ea27fb354aa)：

- [`packages/animations/package.json`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/animations/package.json)：目标 package、core peer 与 Node engine。
- [`animation_metadata.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/animations/src/animation_metadata.ts)：legacy trigger/timeline DSL。
- [`animation_builder.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/animations/src/animation_builder.ts) 与 [`animation_player.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/animations/src/players/animation_player.ts)：程序式 API 与 player 生命周期。
- [`platform-browser/animations/module.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/platform-browser/animations/src/module.ts)：20.2 deprecation、v23 removal intent、module/provider 配置。
- [`animations/async/providers.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/platform-browser/animations/async/src/providers.ts)：async renderer、server noop 行为与 deprecation。
- [`platform-browser/animations/animations.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/platform-browser/animations/src/animations.ts)：`ANIMATION_MODULE_TYPE` 从 core 重导出的归属证据。
- [`core/animation/interfaces.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/core/src/animation/interfaces.ts) 与 [`render3/instructions/animation.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/core/src/render3/instructions/animation.ts)：native callback、最长 timeline、class cleanup 与 leave-node removal。
- 官方固定版本指南：[enter/leave](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/adev/src/content/guide/animations/enter-and-leave.md)、[CSS animations](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/adev/src/content/guide/animations/css.md)、[migration](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/adev/src/content/guide/animations/migration.md)。

测试结构固定参考 OpenRewrite `8.87.5` commit [`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568) 的 [`RewriteTest`](https://github.com/openrewrite/rewrite/blob/b3008cc4a1f0c43f562da16e5933a2a56d9bc568/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)：before/after、marker、recipe discovery、cycle 幂等与误报边界均使用同一套 harness。

## 真实公开仓回归样本（固定 commit）

- [`HybridShivam/pokedex-angular-app@a39ca00/package.json`](https://github.com/HybridShivam/pokedex-angular-app/blob/a39ca00439e160069ea711ee98326288f9a1443e/package.json)：`~10.2.5` animations/framework 依赖组。
- [`apache/nifi@59cff97` registry package.json](https://github.com/apache/nifi/blob/59cff970ca8b98ee51ae4418cf4de6830fa28c37/nifi-registry/nifi-registry-core/nifi-registry-web-ui/src/main/package.json)：`11.2.14` 精确依赖。
- [`apache/nifi@c7725c4` app.module.ts](https://github.com/apache/nifi/blob/c7725c43340292e2231b4024b2456aa0ac284c44/nifi-frontend/src/main/frontend/apps/nifi/src/app/app.module.ts)：BrowserAnimationsModule 与运行时 provide/noop 分支。
- [`NativeScript/ns-ng-animation-examples@75ca827` query-stagger.component.ts](https://github.com/NativeScript/ns-ng-animation-examples/blob/75ca8272e8a4d2b448e510112327e06d9bb70bdd/app/query-stagger.component.ts)：query/stagger/`:enter` 复杂 timeline。
- [`EvictionLab/eviction-maps@6969db5` guide.component.ts](https://github.com/EvictionLab/eviction-maps/blob/6969db5b29263c6fff1b8191efd60d5a972280be/src/app/map-tool/guide/guide.component.ts)：真实 `:enter`/`:leave` opacity trigger。
- [`moodlehq/moodleapp@4caf3f3` testing/utils.ts](https://github.com/moodlehq/moodleapp/blob/4caf3f3f7d38c4bfe14939897910469aaa0a988a/src/testing/utils.ts)：测试环境 NoopAnimationsModule。
- [`IHTSDO/request-management-portal-ui@17cddcf` app.config.ts](https://github.com/IHTSDO/request-management-portal-ui/blob/17cddcf3255a67c7dd9472610967934b4193f9bb/src/app/app.config.ts)：standalone `provideAnimations()`。

样本被缩成最小可解析 fixture；测试保留仓库、完整 commit 和原路径，因此无需联网也可稳定回归，同时能追溯真实形态。

## 测试覆盖

| 测试类 | 数量 | 覆盖重点 |
| --- | ---: | --- |
| `UpgradeAngularAnimationsTest` | 64 | 10 个表格版本、exact/`^`/`~`、4 个依赖区、真实 manifest、复杂/动态/lockfile 负例与幂等 |
| `AngularAnimationsSourceMigrationTest` | 28 | 绑定归属严格的 AST 自动迁移，全部 legacy DSL/provider/builder/player/testing/SSR marker，5 个真实源码仓 |
| `AngularAnimationsProjectMigrationTest` | 78 | complex/dynamic、lockstep/toolchain/第三方/workspace、兼容工具链 no-op、legacy/native template、CSS/reduced-motion/View Transition 与组合配方 |
| 合计 | **170** | 零失败、零错误、零跳过 |

验证命令：

```bash
mvn -pl rewrite-angular-animations-upgrade -am clean verify
```

## 使用

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-animations-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.MigrateAngularAnimationsTo20_3_26
```

确认自动 patch 与每个 `SearchResult` 后再执行 `run`。随后按 major 顺序执行 Angular 官方 migrations，锁步所有 framework 包，重建 lockfile，并运行 production/browser/server build、unit/E2E、视觉回归、reduced-motion、callback/teardown、SSR 与 hydration 测试。
