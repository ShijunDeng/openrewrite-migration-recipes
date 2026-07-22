# @angular/platform-browser-dynamic upgrade to 20.3.26

本模块对应 `开源软件升级.xlsx` 中的 `@angular/platform-browser-dynamic`，合并处理 `10.0.14`、`10.2.5`、`11.2.14`、`12.2.10`、`12.2.13`、`12.2.14`、`12.2.16`、`12.2.17`、`13.1.3` 以及 `13.2.6 …（共 27 个版本）`，目标版本为 `20.3.26`。

配方名称：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularPlatformBrowserDynamicTo20_3_26
```

## 自动处理范围

配方仅把 `package.json` 四个直接依赖区中的 `@angular/platform-browser-dynamic` 设置为 `20.3.26`。这是满足表格目标的过渡动作；Angular 20 已废弃该包全部入口，完成框架升级后应迁移 bootstrap/JIT 用法并移除此依赖。

目标包要求 core、common、compiler 和 platform-browser 精确匹配 `20.3.26`。必须逐大版本执行官方 `ng update` migrations，不能只进行版本替换。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| Angular 20 废弃 `@angular/platform-browser-dynamic` 全部入口 | 制定移除计划；不要在新代码继续增加 `platformBrowserDynamic`、JIT compiler factory 等依赖 |
| `platformBrowserDynamic().bootstrapModule(AppModule)` 旧启动方式 | standalone 应改为 `bootstrapApplication(AppComponent, appConfig)`；保留 NgModule 时使用 AOT 友好的 `platformBrowser().bootstrapModule()` |
| runtime JIT 编译不再是生产推荐路径 | 模板必须能在构建期 AOT 编译；修复动态模板、运行时声明 NgModule 和依赖私有 Compiler API 的实现 |
| 动态组件不再需要 `entryComponents` | 删除旧 entryComponents 配置，使用 `ViewContainerRef.createComponent` 或 `createComponent` 与明确 EnvironmentInjector |
| framework 包严格锁步 | core/common/compiler/platform-browser/platform-browser-dynamic 全部使用 20.3.26，避免 JIT compiler 与 runtime 版本错配 |
| Node 与 TypeScript 基线提升 | Node 使用 `^20.19.0`、`^22.12.0` 或 `>=24`，Angular compiler 工具链使用 TypeScript `>=5.8 <6.0` |
| standalone provider 与 NgModule provider 边界不同 | 使用 `ApplicationConfig`；遗留模块通过 `importProvidersFrom` 过渡，防止服务被重复注册或作用域改变 |
| Ivy/partial compilation 取代 View Engine/ngcc | 升级或替换只支持 View Engine 的库，删除 ngcc postinstall、旧 metadata 和深层导入 |
| TestBed 默认编译路径和异步时序变化 | 避免直接调用 JIT compiler 私有实现；使用公开 TestBed API，并更新 `compileComponents`/fixture 稳定性假设 |
| CSP/Trusted Types 限制动态代码执行 | 严格 CSP 环境尤其不应依赖 JIT；验证 production build 不需要 `unsafe-eval` |
| SSR/hydration 需要编译产物和 client DOM 一致 | 使用现代 Angular SSR builder/provider；覆盖 hydration mismatch、lazy route、动态组件和事件重放 |
| 自定义 runtime template/compiler 服务 | Angular 20 没有稳定等价公共 API；改为预编译组件、受控配置驱动 UI 或独立模板引擎 |

完整迁移步骤以 Angular 官方 [Update Guide](https://angular.dev/update-guide)、[版本兼容矩阵](https://angular.dev/reference/versions) 和 [20.0.0 release](https://github.com/angular/angular/releases/tag/20.0.0) 为准；启动 API 见 [bootstrapApplication](https://angular.dev/api/platform-browser/bootstrapApplication)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-platform-browser-dynamic-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.UpgradeAngularPlatformBrowserDynamicTo20_3_26
```

确认 patch 后执行 `run`，重建锁文件，并运行 AOT production build、strict template check、单元/E2E、CSP、SSR 和 hydration 测试；完成代码迁移后从应用依赖中删除本软件。

本模块自身验证：

```bash
mvn -pl rewrite-angular-platform-browser-dynamic-upgrade -am clean verify
```
