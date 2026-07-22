# Angular Platform Browser Dynamic 升级到 20.3.26

本模块处理 `开源软件升级.xlsx` 中的 `@angular/platform-browser-dynamic`。表格当前可见源版本为：

```text
10.0.14, 10.2.5, 11.2.14, 12.2.10, 12.2.13, 12.2.14,
12.2.16, 12.2.17, 13.1.3, 13.2.6
```

表格注明“共 27 个版本”，但其余折叠值不可见。本模块不会猜测隐藏值；只自动升级上述十个值及其单一 `^`/`~` 声明，目标固定为 `20.3.26`。

## 配方

严格低层依赖配方：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularPlatformBrowserDynamicTo20_3_26
```

推荐迁移配方：

```text
com.huawei.clouds.openrewrite.angular.MigrateAngularPlatformBrowserDynamicTo20_3_26
```

推荐配方组合严格依赖升级、可证明安全的 bootstrap 源码迁移，以及源码/项目风险审计。`SearchResult` marker 表示必须处理的待办，不能当作迁移完成。

## 处理矩阵

| 不兼容点 | 处理 | 配方行为与验证 |
| --- | --- | --- |
| 表格可见 exact/`^`/`~` 单版本 | AUTO | 只改 `package.json` 四个根直接依赖区并写成精确 `20.3.26`；30 组参数化测试和三个固定真实仓库样例 |
| 复杂范围、OR/hyphen、prerelease/build、协议、变量、tag、未列版本、中央 owner、lockfile | NO-OP / MARK | 严格配方不改；推荐配方只在根直接声明处标记。metadata、lockfile、同名包及其他 JSON 有 no-op 测试 |
| 单独导入并直接调用 `platformBrowserDynamic()` | AUTO | 当该 binding 的全部源码引用都是直接调用时，将 import 改为 `platformBrowser`/`@angular/platform-browser`，并同步改未 alias 的调用；Google Maps 与 Todo Angular Firebase 固定 before→after 样例 |
| aliased `platformBrowserDynamic as boot` | AUTO / NO-OP | 只有 `boot` 的全部引用都是直接调用时替换 imported symbol/module，保留本地 alias；binding 逃逸到变量、回调或属性时不改并由审计标记 |
| 混合 named import、namespace/default import、deep/testing import | MARK | 不猜测 import 拆分、测试平台生命周期或私有入口替代，只在具体 import 标记 |
| `JitCompilerFactory`、`COMPILER_OPTIONS`、直接 `@angular/compiler` | MARK | 标记运行时 JIT/provider 所有权；纯 AOT 应删除，明确保留 JIT 时必须显式引入 compiler 并验证 bundle/CSP |
| `Compiler.compileModule*`/cache API | MARK | 只对由 Angular `Compiler`/`CompilerFactory` 类型归因的 receiver 标记；同名普通对象 no-op |
| `ComponentFactoryResolver`/旧动态组件创建 | MARK | 标记迁移到 `ViewContainerRef.createComponent` 或 `createComponent`、`EnvironmentInjector` 和销毁生命周期的边界 |
| `entryComponents` | MARK | 只在导入 `NgModule` 的 Angular 源文件标记；Ivy 忽略该配置，但删除前需验证全部动态组件路径 |
| 运行时计算 `template`/`templateUrl` | MARK | 只在已出现 runtime compiler 证据时标记非 literal 模板；要求改为预编译组件或独立受控模板引擎 |
| Angular framework 包锁步 | MARK | `core/common/compiler/platform-browser` 非精确 `20.3.26` 时在直接声明值标记 |
| Node、TypeScript、ngcc/View Engine | MARK | 标记不兼容 Node/TS 标量和含 `ngcc` 的 script；兼容标量、无本包的 unrelated package 均 no-op |
| AOT/build optimizer、Ivy/strict template | MARK | 在 `angular.json`/`workspace.json` 或 tsconfig 的具体 false 值标记；嵌套同名 metadata 不误报 |
| custom builder、SSR/prerender | MARK | 在具体 builder/target 标记 AOT、资源、lazy chunk、CSP、request scope 与 hydration 回归边界 |
| standalone `bootstrapApplication` 转换 | 人工 | NgModule provider、`APP_INITIALIZER`、路由、SSR 与测试环境无法上下文无关地转换；本配方不伪造自动迁移 |

完成源码迁移且确认不再需要 testing/JIT 入口后，应在单独审核中移除 `@angular/platform-browser-dynamic`；本表格任务仍要求先把被选中的声明对齐到 `20.3.26`。

## 固定依据

- Angular `20.3.26` release cut：[`4d627600a9b096cb85a828fd3cea0ea27fb354aa`](https://github.com/angular/angular/tree/4d627600a9b096cb85a828fd3cea0ea27fb354aa)
- 目标源码 [`packages/platform-browser-dynamic/src/platform_providers.ts`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/platform-browser-dynamic/src/platform_providers.ts)：`platformBrowserDynamic` 废弃说明及 `platformBrowser`/显式 `@angular/compiler` 替代边界
- 目标 [`packages/platform-browser-dynamic/package.json`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/packages/platform-browser-dynamic/package.json) 与 [`CHANGELOG.md`](https://github.com/angular/angular/blob/4d627600a9b096cb85a828fd3cea0ea27fb354aa/CHANGELOG.md)
- OpenRewrite JavaScript AST/测试参考：[`b3008cc4a1f0c43f562da16e5933a2a56d9bc568`](https://github.com/openrewrite/rewrite-javascript/tree/b3008cc4a1f0c43f562da16e5933a2a56d9bc568)

## 固定真实仓库样例

- Google Maps Extended Component Library：[`70aff8d2f92a2cc925bf37338e7ad298edf008aa`](https://github.com/googlemaps/extended-component-library/tree/70aff8d2f92a2cc925bf37338e7ad298edf008aa) 到 [`bddb510441b4f3794b3eb683bb7834d6d7ee687e`](https://github.com/googlemaps/extended-component-library/commit/bddb510441b4f3794b3eb683bb7834d6d7ee687e) 的 `examples/angular_sample_app/src/main.ts` before→after
- Todo Angular Firebase：[`9057353abb7fb9827beda3a940331445e2feb552`](https://github.com/r-park/todo-angular-firebase/tree/9057353abb7fb9827beda3a940331445e2feb552) 的 `src/main.ts` bootstrap fixture
- Apache NiFi：[`59cff970ca8b98ee51ae4418cf4de6830fa28c37`](https://github.com/apache/nifi/tree/59cff970ca8b98ee51ae4418cf4de6830fa28c37) 的 `11.2.14` dependency fixture
- Pokedex Angular App：[`a39ca00439e160069ea711ee98326288f9a1443e`](https://github.com/HybridShivam/pokedex-angular-app/tree/a39ca00439e160069ea711ee98326288f9a1443e) 的 `~10.2.5` dependency fixture
- Elastic APM RUM Agent：[`997138a38ab3253072e710a97343dea240447b7c`](https://github.com/elastic/apm-agent-rum-js/tree/997138a38ab3253072e710a97343dea240447b7c) 的 `^12.2.17` dependency fixture

测试保留提取后的最小路径和 before→after/marker/no-op 形态，并覆盖多轮幂等及同名误报边界。

## 使用

先运行推荐配方的 dry run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-platform-browser-dynamic-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.MigrateAngularPlatformBrowserDynamicTo20_3_26
```

逐项处理 marker，按 major 顺序运行官方 `ng update` migrations，统一 Angular framework packages，重建 lockfile，然后执行 AOT production/strict-template、单元/E2E、TestBed、dynamic component、CSP/Trusted Types、SSR/hydration 与 lazy-route 测试。

模块验证：

```bash
mvn -pl rewrite-angular-platform-browser-dynamic-upgrade -am clean verify
```
