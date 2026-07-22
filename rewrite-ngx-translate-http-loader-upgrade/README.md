# @ngx-translate/http-loader upgrade to 17.0.0

本模块对应 `开源软件升级.xlsx` 中的 `@ngx-translate/http-loader`，合并处理 `4.0.0`、`6.0.0`、`7.0.0` 和 `8.0.0`，目标版本为 `17.0.0`。

配方名称：

```text
com.huawei.clouds.openrewrite.ngxtranslate.UpgradeNgxTranslateHttpLoaderTo17
```

## 自动处理范围

配方仅修改 `package.json`，将 `dependencies`、`devDependencies`、`peerDependencies` 和 `optionalDependencies` 中的 `@ngx-translate/http-loader` 设置为 `17.0.0`。

配方不会隐式修改 `@ngx-translate/core`。实际升级时应同时运行 `rewrite-ngx-translate-core-upgrade`，使 core 与 loader 均为 `17.0.0`，再用项目原有包管理器重新生成锁文件。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| Angular 基线从 v4 的 Angular 7、v6 的 Angular 10、v7 的 Angular 13、v8 的 Angular 16 逐步提升 | v17 peer dependency 要求 `@angular/core` 和 `@angular/common >=16`；先统一 Angular、RxJS、TypeScript 和 ngx-translate core 版本 |
| 旧式 `TranslateHttpLoader(HttpClient, prefix, suffix)` 构造器不再接受参数 | standalone 配置改用 `provideTranslateHttpLoader({prefix, suffix})`；不要继续在 factory 中 `new TranslateHttpLoader(http, ...)` |
| v17 推荐 provider 函数配置 loader | 将 `TranslateModule.forRoot({loader: {provide, useFactory, deps}})` 迁移到 `provideTranslateService({loader: provideTranslateHttpLoader(...)})`；NgModule 保留方案也必须使用与 v17 API 匹配的 provider |
| loader 通过 Angular `inject()` 获取 HTTP 依赖和配置 | 确保根环境已注册 `provideHttpClient()`，或在 NgModule 工程正确导入 HTTP provider |
| 新配置项 `useHttpBackend` 可绕过拦截器 | 只有翻译文件明确不需要认证、租户头、错误处理等 interceptor 时才启用；开启后补充请求链路测试 |
| 新配置项 `enforceLoading` 为 URL 添加时间戳查询参数 | 这会绕过浏览器/CDN 缓存；仅在确需每次重新加载时使用，并评估流量与缓存策略 |
| `getTranslation()` 返回 `Observable<TranslationObject>`，类型不再宽泛 | 修正自定义 loader、测试替身和转换逻辑中的 `any`/错误响应类型 |
| prefix/suffix 默认值仍为 `/assets/i18n/` 与 `.json`，但配置入口改变 | 对非默认路径显式传入配置，验证 `<base href>`、SSR、子路径部署和 CDN URL |
| 旧版 loader 与 core 的 peer dependency 组合发生多次跃迁 | 不要只升级 loader；核对 `@ngx-translate/core` 17、Angular >=16 及实际 RxJS 版本，清理 npm/yarn peer warning |
| HTTP 请求错误、缓存和快速语言切换时序会受 Angular HTTP 与 core 行为共同影响 | 覆盖 404、离线、回退语言、并发切换、SSR hydration 和拦截器异常测试 |

官方依据：[@ngx-translate/http-loader v4/v6 releases](https://github.com/ngx-translate/http-loader/releases)、[v17 package metadata](https://github.com/ngx-translate/core/blob/v17.0.0/projects/http-loader/package.json)、[v17 loader API](https://github.com/ngx-translate/core/blob/v17.0.0/projects/http-loader/src/lib/http-loader.ts) 和 [v16 → v17 migration guide](https://ngx-translate.org/v17/getting-started/migration-guide/)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-ngx-translate-http-loader-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.ngxtranslate.UpgradeNgxTranslateHttpLoaderTo17
```

确认 patch 后将 `dryRun` 改为 `run`，重新生成锁文件，并执行 Angular build、TypeScript strict 编译、HTTP 拦截器、语言切换和端到端测试。

本模块自身验证：

```bash
mvn -pl rewrite-ngx-translate-http-loader-upgrade -am clean verify
```
