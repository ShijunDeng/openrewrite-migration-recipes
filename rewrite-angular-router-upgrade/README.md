# @angular/router upgrade to 20.3.26

本模块对应 `开源软件升级.xlsx` 中的 `@angular/router`，合并处理 `10.0.14`、`10.2.5`、`11.2.14`、`12.2.10`、`12.2.13`、`12.2.14`、`12.2.16`、`12.2.17`、`13.1.3` 以及 `13.2.6 …（共 28 个版本）`，目标版本为 `20.3.26`。

配方名称：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularRouterTo20_3_26
```

## 自动处理范围

配方仅把 `package.json` 四个直接依赖区中的 `@angular/router` 设置为 `20.3.26`。目标包要求 core、common、platform-browser 精确匹配 `20.3.26`，并支持 RxJS `^6.5.3` 或 `^7.4.0`。

必须逐大版本运行 Angular CLI `ng update` migrations；本配方用于最后核对 Excel 目标版本，不替代路由源码迁移。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| standalone 路由成为首选配置 | `RouterModule.forRoot()` 可逐步迁移为 `provideRouter(routes, ...)`；feature 使用 standalone route/component imports |
| 字符串式 lazy `loadChildren` 已移除 | 改为动态 import 返回 routes 或 NgModule；对 chunk 失败、预加载和部署 base href 做 E2E 测试 |
| class-based guard/resolver 可用但 functional API 成为推荐 | 使用 `CanActivateFn`、`CanMatchFn`、`ResolveFn` 与 `inject()`；避免在函数外调用 inject |
| `CanLoad` 被 `CanMatch` 取代 | 迁移权限逻辑并确认“不匹配后尝试后续 route”与旧阻止加载语义的差异 |
| `RouterLinkWithHref` 合并/废弃 | 统一使用 `RouterLink`；更新直接引用该 directive 类型的测试和封装组件 |
| `RouterTestingModule` 废弃 | 测试使用 `provideRouter()` 与 `RouterTestingHarness`，不要依赖旧 stub 的非真实导航行为 |
| `Router.getCurrentNavigation()` 在 v20.2 废弃 | 改用 `Router.currentNavigation` signal；导航结束后 signal 为 null，保存状态时明确生命周期 |
| Router 配置转为 feature/provider API | initial navigation、hash location、scrolling、preloading、component input binding 等使用对应 `with*` feature，避免重复 provider |
| 导航取消/跳过事件和 code 类型演进 | 不要只按事件名称/字符串判断；处理 `NavigationCancel`、`NavigationSkipped` 和重定向/守卫拒绝的不同结果 |
| resolver/guard 返回空 Observable 会取消导航 | 确保每条路径明确发出值或 UrlTree/RedirectCommand，并覆盖 error、timeout 与 unsubscribe |
| relative link、redirect 和 params 继承边界经多次修正 | 回归嵌套路由、空路径、matrix/query params、named outlets、通配符与相对 `routerLink` |
| route `title`、component input binding 和 signal API 增加 | 自定义 TitleStrategy、resolver data 与 input 名称冲突需测试，避免业务静默拿到不同值 |
| SSR blocking initial navigation 与 hydration | server/client 使用一致 routes/provider；防止重复导航、认证重定向闪烁和 hydration mismatch |
| scroll restoration 时机与 ViewportScroller 行为变化 | 测试 back/forward、anchor、异步页面高度和自定义容器；不要以固定 timeout 模拟路由稳定 |
| Node/TypeScript/Angular 包基线锁步 | Node 使用 `^20.19.0`、`^22.12.0` 或 `>=24`，TypeScript 按 v20 兼容矩阵；全部 framework 包同 patch |

完整迁移步骤以 Angular 官方 [Update Guide](https://angular.dev/update-guide)、[Routing guide](https://angular.dev/guide/routing)、[版本兼容矩阵](https://angular.dev/reference/versions) 和 [20.2.0 release](https://github.com/angular/angular/releases/tag/20.2.0) 为准。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-router-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.UpgradeAngularRouterTo20_3_26
```

确认 patch 后执行 `run`，重建锁文件，并运行 production build、路由 harness、权限/重定向、lazy chunk、浏览器历史、SSR 与 hydration 测试。

本模块自身验证：

```bash
mvn -pl rewrite-angular-router-upgrade -am clean verify
```
