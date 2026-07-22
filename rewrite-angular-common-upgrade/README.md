# @angular/common upgrade to 20.3.26

本模块对应 `开源软件升级.xlsx` 中的 `@angular/common`，合并处理 `10.0.14`、`10.2.5`、`11.2.14`、`12.2.10`、`12.2.13`、`12.2.14`、`12.2.16`、`12.2.17`、`13.1.3` 以及表中压缩记录 `13.2.6 …（共 50 个版本）`，目标版本为 `20.3.26`。

配方名称：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularCommonTo20_3_26
```

## 自动处理范围

配方仅修改 `package.json`，将四个直接依赖区中的 `@angular/common` 设置为 `20.3.26`。它不会修改其他 Angular 包，也不会改写 TypeScript、模板、workspace 配置或锁文件。

Angular framework 包必须使用完全一致的 patch 版本。实际迁移应从当前版本开始逐个大版本运行 Angular CLI `ng update @angular/core@<major> @angular/cli@<major>`，接受并验证官方 schematics，最后再用本配方核对表格指定的目标版本；不能把一次版本字符串替换当作完整 Angular 迁移。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| Angular 20.3.26 要求 Node `^20.19.0`、`^22.12.0` 或 `>=24` | 先升级 CI、开发机和运行镜像；旧 Node 18 不受支持 |
| `@angular/common` 要求精确匹配 `@angular/core:20.3.26` | 同步升级 core、compiler、platform、router、forms 等 framework 包，清理重复 Angular 副本 |
| Angular 20 compiler 工具链要求 TypeScript `>=5.8 <6.0` | 按每个中间 Angular 大版本的矩阵分阶段升级 TypeScript，不要一步跳到最终版本后再运行旧 migrations |
| View Engine 被移除，Ivy 成为唯一编译/链接机制 | 替换仍只发布 View Engine 的三方库；删除 `ngcc` 和旧构建脚本 |
| `HttpClientModule`/`HttpClientTestingModule` 等 NgModule 配置逐步被 provider API 取代并废弃 | 新配置使用 `provideHttpClient()`、`provideHttpClientTesting()`；迁移 interceptor、XSRF 和测试 provider 的顺序 |
| class-based HTTP interceptors 与 functional interceptors 并存 | 使用 `withInterceptors()`；保留 DI 类拦截器时显式使用 `withInterceptorsFromDi()`，验证执行顺序 |
| `NgIf`、`NgFor`、`NgSwitch` 在 v20 被废弃 | 逐步迁移到内建 `@if`、`@for`、`@switch` 控制流；`@for` 必须提供正确的 track 表达式 |
| v20 默认不再生成 `ng-reflect-*` 属性 | 测试和业务逻辑不得查询这些调试属性；改用 DOM 可见行为或组件 harness |
| DatePipe 对可疑 week-year 模式加强校验 | 使用 `Y` 时同时提供周数 `w`；普通公历年份通常应使用 `y`，覆盖 locale/timezone 边界 |
| `AsyncPipe` 直接把未处理错误报告给应用 `ErrorHandler` | 更新依赖旧 Zone 错误路径的测试；流错误应在合适层处理，避免生产全局错误噪声 |
| locale、currency、date、decimal pipe 数据与 Intl 行为跨版本变化 | 注册实际使用的 locale data，并对金额舍入、窄货币符号、时区、周起始日做快照/业务断言 |
| `DOCUMENT` token 在 v20 移至 `@angular/core` | 逐大版本 migrations 后检查旧 import，避免继续依赖已废弃的 common 导出 |
| `NgOptimizedImage` 引入严格尺寸、loader、preconnect 和 SSR preload 规则 | 采用该指令的页面补全 width/height 与 loader 配置，验证 CDN URL、LCP 和 hydration |
| standalone API、signals、内建控制流与 zoneless 能力改变应用结构 | 不要求一次性重写，但需防止混合 provider 重复、ChangeDetection 时序变化和 SSR hydration 不一致 |
| 测试错误传播和稳定性语义改变 | 升级 TestBed 写法，清理依赖 Zone 私有行为的 fakeAsync 测试，并覆盖 AsyncPipe/HTTP/SSR 错误路径 |

完整迁移步骤以 Angular 官方 [Update Guide](https://angular.dev/update-guide) 和 [版本兼容矩阵](https://angular.dev/reference/versions) 为准；v20 差异见 [Angular 20.0.0 release](https://github.com/angular/angular/releases/tag/20.0.0)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-common-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.UpgradeAngularCommonTo20_3_26
```

确认 patch 后执行 `run`，用原包管理器重建锁文件，并运行 Angular build、strict template/type check、单元测试、SSR/hydration 与端到端测试。

本模块自身验证：

```bash
mvn -pl rewrite-angular-common-upgrade -am clean verify
```
