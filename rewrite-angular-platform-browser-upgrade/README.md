# @angular/platform-browser upgrade to 20.3.26

本模块对应 `开源软件升级.xlsx` 中的 `@angular/platform-browser`，合并处理 `10.0.14`、`10.2.5`、`11.2.14`、`12.2.10`、`12.2.13`、`12.2.14`、`12.2.16`、`12.2.17`、`13.1.3` 以及 `13.2.6 …（共 28 个版本）`，目标版本为 `20.3.26`。

配方名称：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularPlatformBrowserTo20_3_26
```

## 自动处理范围

配方仅把 `package.json` 四个直接依赖区中的 `@angular/platform-browser` 设置为 `20.3.26`。它不修改名称相似但独立发布的 `@angular/platform-browser-dynamic`，也不运行 Angular CLI migrations。

目标包要求 `@angular/core`、`@angular/common` 和可选的 `@angular/animations` 精确匹配 `20.3.26`。应逐大版本执行官方 `ng update`，最后使用此配方核对 Excel 指定版本并重建锁文件。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| Angular 20 Node/TypeScript 基线提升 | Node 使用 `^20.19.0`、`^22.12.0` 或 `>=24`，compiler 工具链使用 TypeScript `>=5.8 <6.0` |
| framework peer 必须完全锁步 | core/common/animations 与 platform-browser 使用 20.3.26，避免 npm 安装出两套 Angular runtime |
| 应用 bootstrap 从 NgModule 逐步转向 standalone | 新应用使用 `bootstrapApplication` 和 `ApplicationConfig`；迁移时用 `importProvidersFrom` 承接尚未 standalone 的模块 provider |
| `platformBrowserDynamic().bootstrapModule()` 路径在 v20 被废弃 | 使用 AOT 友好的 `platformBrowser().bootstrapModule()` 或优先迁移到 `bootstrapApplication`；不要继续依赖运行时 JIT |
| HammerJS 集成在 v20 废弃 | 替换 `HammerModule`、`HAMMER_GESTURE_CONFIG`/loader 依赖，改用 Pointer Events 或维护中的手势库 |
| 客户端 hydration 从无到默认推荐 | SSR 应显式配置 `provideClientHydration()`，检查 DOM 一致性、事件重放、增量 hydration 和第三方直接 DOM 操作 |
| TransferState API/序列化行为演进 | 只写可 JSON 序列化数据，保证 server/client 使用相同 key，并检查敏感数据不会嵌入 HTML |
| `BrowserModule.withServerTransition()` 等旧 SSR 配置被移除/替代 | 使用现代 SSR/`APP_ID` 与 hydration provider；逐版本 migrations 清理旧 Universal bootstrap |
| `BrowserModule` 只能由根模块导入一次 | feature/lazy module 改用 `CommonModule` 或 standalone imports，避免重复 provider 错误 |
| sanitization 与 Trusted Types/CSP 约束加强 | 复查所有 `bypassSecurityTrust*`，不要把不可信输入包装成 trusted value；验证 CSP nonce 与动态样式/脚本 |
| `Title`、`Meta`、DOM adapter 和事件插件在 SSR/浏览器间行为不同 | 对 SEO 元数据、Web Components、自定义 EventManagerPlugin 以及 SSR 无 DOM 环境增加测试 |
| v20 默认停止输出 `ng-reflect-*` | 删除 E2E 和业务代码对调试属性的依赖，改用可访问角色、稳定 data 属性或 component harness |
| zone.js 可选与 zoneless 能力增强 | 第三方库若依赖 Zone 触发变更检测，迁移 zoneless 前需逐项验证；不要仅删除 zone.js 依赖 |
| 测试错误传播与渲染时序变化 | 使用当前 TestBed/component fixture API，覆盖 bootstrap 失败、全局 ErrorHandler、hydration mismatch 和异步稳定性 |

完整迁移步骤以 Angular 官方 [Update Guide](https://angular.dev/update-guide)、[版本兼容矩阵](https://angular.dev/reference/versions)、[bootstrapApplication API](https://angular.dev/api/platform-browser/bootstrapApplication) 和 [hydration guide](https://angular.dev/guide/hydration) 为准；v20 废弃项见 [20.0.0 release](https://github.com/angular/angular/releases/tag/20.0.0)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-platform-browser-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.UpgradeAngularPlatformBrowserTo20_3_26
```

确认 patch 后执行 `run`，重建锁文件，并运行 browser build、strict template check、单元/E2E、CSP、安全、SSR 与 hydration 测试。

本模块自身验证：

```bash
mvn -pl rewrite-angular-platform-browser-upgrade -am clean verify
```
