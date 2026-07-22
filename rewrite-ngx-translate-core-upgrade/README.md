# @ngx-translate/core upgrade to 17.0.0

本模块对应 `开源软件升级.xlsx` 中的 `@ngx-translate/core`，合并处理 `11.0.1`、`13.0.0`、`14.0.0` 和 `15.0.0`，目标版本为 `17.0.0`。

配方名称：

```text
com.huawei.clouds.openrewrite.ngxtranslate.UpgradeNgxTranslateCoreTo17
```

## 自动处理范围

配方将 `package.json` 的 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies` 中的 `@ngx-translate/core` 更新为 `17.0.0`。

锁文件必须使用项目原有包管理器重新生成。若项目同时使用 `@ngx-translate/http-loader`，应将它同步升级到兼容的 17.x 版本；本模块不会在表格未列出该软件时隐式增加或升级另一个包。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| Angular 版本基线连续提升 | v12 要求 Angular 8+，v13 面向 Angular 10，v14 面向 Angular 13，v15 面向 Angular 16；v17 peer dependency 要求 `@angular/core`/`common >=16`，必须先确认 Angular/RxJS/TypeScript 组合兼容 |
| v14 仅发布 Ivy 构建，View Engine 不再支持 | 旧 Angular/View Engine 工程需要先迁移 Angular 和库构建链 |
| v16 开始支持 standalone pipe/directive 和 `provideTranslateService()` | standalone 项目改用 provider 配置；NgModule 项目仍可使用 `TranslateModule.forRoot()`，不要混合创建多个根服务实例 |
| v17 将 “default language” 术语改为 “fallback language” | `defaultLanguage` + `useDefaultLang` 改为 `fallbackLang`；`setDefaultLang()`/`getDefaultLang()` 改为 `setFallbackLang()`/`getFallbackLang()` |
| `onDefaultLangChange` 改为 `onFallbackLangChange`，事件由 `EventEmitter` 转为 Observable | 更新事件类型和订阅代码，确保组件销毁时正确退订 |
| 推荐使用 getter 方法代替属性直取 | `currentLang`、`defaultLang`、`langs` 分别改用 `getCurrentLang()`、`getFallbackLang()`、`getLangs()`；处理可空返回值 |
| provider 系统重构 | `provideTranslateLoader`、compiler、parser、missing handler 等应嵌套在 `provideTranslateService({...})` 配置中；单独并列 provider 可能被默认实现覆盖 |
| HTTP loader 构造器不再接收 prefix/suffix 等参数 | 使用 `provideTranslateHttpLoader({prefix, suffix, ...})`；同步验证 `enforceLoading` 与 `useHttpBackend` 对缓存和拦截器的影响 |
| v16 参数和返回值从宽泛 `any` 收紧 | TypeScript 编译可能暴露自定义 loader/parser/compiler、翻译参数及递归对象的类型错误 |
| `use()` 的并发调用行为已修正为最后请求语言生效 | 对快速切换语言、路由初始化和 SSR hydration 增加时序测试 |
| `getTranslation()` 自 v16 起废弃 | 避免继续依赖底层加载接口，按需求使用 `get`、`instant`、`stream` 或自定义 loader |
| 内部类重命名 | `FakeMissingTranslationHandler`、`TranslateFakeCompiler`、`TranslateFakeLoader` 分别迁移到 `DefaultMissingTranslationHandler`、`TranslateNoOpCompiler`、`TranslateNoOpLoader`；不要依赖内部实现 |

官方依据：[@ngx-translate/core v12–v15 releases](https://github.com/ngx-translate/core/releases)、[v15 → v16 说明](https://github.com/ngx-translate/core/releases/tag/v16.0.0)、[v16 → v17 migration guide](https://ngx-translate.org/v17/getting-started/migration-guide/)、[v17 package peer dependencies](https://github.com/ngx-translate/core/blob/v17.0.0/projects/ngx-translate/package.json)。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-ngx-translate-core-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.ngxtranslate.UpgradeNgxTranslateCoreTo17
```

确认 patch 后将 `dryRun` 改为 `run`，重新生成锁文件，再执行 Angular build、TypeScript strict 编译、单元测试和端到端语言切换测试。

本模块自身验证：

```bash
mvn -pl rewrite-ngx-translate-core-upgrade -am clean verify
```
