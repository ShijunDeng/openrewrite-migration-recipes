# @angular/cdk upgrade to 20.2.14

本模块对应 `开源软件升级.xlsx` 中的 `@angular/cdk`，合并处理 `10.2.6`、`10.2.7`、`11.2.13`、`12.2.10`、`12.2.13`、`13.1.3`、`13.3.1`、`13.3.9`、`14.0.6` 以及 `14.2.0 …（共 17 个版本）`，目标版本为 `20.2.14`。

配方名称：

```text
com.huawei.clouds.openrewrite.angular.UpgradeAngularCdkTo20_2_14
```

## 自动处理范围

配方仅修改 `package.json` 四个直接依赖区中的 `@angular/cdk`，把低于目标的常见 10.x–19.x、20.0.x、20.1.x 和 20.2.0–20.2.13 声明设置为 `20.2.14`。它不会降级 21.x/22.x，也不会覆盖 `workspace:*` 或发布占位符。

配方不会修改锁文件、TypeScript/HTML/SCSS、Angular framework、Angular Material 或其他 CDK entry point。版本修改后必须逐大版本运行官方 `ng update @angular/cdk` migrations。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| CDK 20.2.14 的 peer 范围是 Angular core/common `^20.0.0 \|\| ^21.0.0` | CDK 补丁号不必与 framework 补丁号相同；例如真实 DSpace 工程使用 CDK 20.2.14 + Angular 20.3.25，但主版本必须在 peer 窗口内 |
| Angular Material 20.2.14 精确依赖 CDK 20.2.14 | 使用 Material 时两者应精确锁步，不能只升级 CDK；同时执行 Material schematics 与视觉回归 |
| 必须逐大版本运行 `ng update @angular/cdk` | 本配方只改声明，无法代替 v11→…→v20 的 schematics；每一步先安装对应 CLI，再提交 migration patch 和锁文件 |
| v13 移除 Sass `~@angular/cdk` 解析形式 | `@import`/`@use` 去掉 `~`；v17 起不再支持 CDK/Material Sass 的 `@import`，统一迁到 `@use` |
| v13 移除旧 overlay API | `ConnectedPositionStrategy` 改为 `FlexibleConnectedPositionStrategy`，`connectedTo` 改为 `flexibleConnectedTo`，避免导入私有 overlay 符号 |
| v14 移除 Protractor harness entry point | 删除 `@angular/cdk/testing/protractor`，将 E2E 迁到仍维护的 runner，并用 CDK test harness environment 适配层 |
| v19 high-contrast 与 overlay 样式加载时机变化 | `cdk.high-contrast` 改为 media query，样式 specificity 可能降低；覆盖 overlay、高对比模式和自定义主题的 CSS 层叠 |
| v19 virtual scroll 模板类型检查增强 | `CdkVirtualForOf` 的 template context 会暴露以前隐藏的类型错误；修复 item、index、trackBy 和数据源泛型 |
| v20 `SelectionModel` 变更返回值 | `clear`、`select`、`deselect`、`setSelection`、`toggle` 现在返回 boolean；检查封装类、mock 与依赖旧 void 签名的 override |
| v20 drag-drop API 删除和类型变化 | `DragDropRegistry` 不再是泛型，`scroll` 改为 `scrolled`；回归跨列表排序、auto-scroll、axis lock、边界和触摸操作 |
| v20 portal 旧名称被删除 | `DomPortalHost`→`DomPortalOutlet`、`PortalHost`→`PortalOutlet`、`BasePortalHost`→`BasePortalOutlet`，`PortalInjector` 改用 `Injector.create` |
| v20 dialog/component portal 构造签名变化 | 移除 `componentFactoryResolver` 与旧 scroll strategy providers；检查动态 dialog、portal、lazy route 和自定义 injector |
| v20 table sticky 私有/旧 API 被删除 | `CanStick`、`CanStickCtor`、`CDK_TABLE_TEMPLATE`、`StickyDirection`、`StickyStyler` 等不可再用；只依赖公开 table API |
| overlay、drag-drop、virtual scroll 与 focus 行为跨版本演进 | 必须做浏览器实测：滚动容器、RTL、缩放/亚像素、键盘导航、screen reader、focus restore、SSR/hydration 与 zone-less 模式 |
| 旧代码常从深层路径或私有 `ɵ` 符号导入 | 依据目标包 `exports` 改用公开 entry point；构建工具不能再依赖未导出的内部文件布局 |
| Node/TypeScript/RxJS 与 Angular 工具链基线同步提高 | 使用 Angular 20 支持的 Node/TypeScript 组合；CDK 20.2.14 支持 RxJS `^6.5.3 \|\| ^7.4.0`，但应用应统一锁定并全量测试 |

完整变更以 Angular Components 的 [20.2.14 changelog](https://github.com/angular/components/blob/20.2.14/CHANGELOG.md)、[CDK overview](https://material.angular.dev/cdk/categories)、[Angular Update Guide](https://angular.dev/update-guide) 和 `ng update` 实际输出为准。

## 测试样本来源

- [kiswa/TaskBoard](https://github.com/kiswa/TaskBoard/blob/857583e4bb508c7b449a8e45bb0747d22d88abdb/package.json) 的 Angular 10 caret 依赖及相邻 framework 包
- [DSpace/dspace-angular](https://github.com/DSpace/dspace-angular/blob/8410074a9e74654a260d000a89b6f6fe1fd54167/package.json) 的 CDK 20.2.14 + Angular 20.3.x 真实组合
- [Angular Components 官方 CDK manifest](https://github.com/angular/components/blob/20.2.14/src/cdk/package.json) 的 peer、exports、schematics 与 `ng-update` 元数据
- [Angular Material 官方 manifest](https://github.com/angular/components/blob/20.2.14/src/material/package.json) 的 CDK 精确 peer 依赖和 `workspace:*` 开发依赖
- [OpenRewrite 官方 ChangeValueTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 与 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/main/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java) 的 JSON 断言、过滤表达式和 no-op 测试结构

测试覆盖四个 dependency 区、范围/前缀版本、嵌套 workspace、目标前最后一个补丁，以及当前版本、高版本、workspace protocol、lockfile、其他 JSON 和相似包名不修改。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-angular-cdk-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.angular.UpgradeAngularCdkTo20_2_14
```

确认 patch 后逐大版本执行 `ng update @angular/cdk`（使用 Material 时同步更新 Material），重建锁文件，并运行 production build、unit/E2E、test harness、a11y、drag-drop、overlay/dialog、virtual scroll、table、SSR/hydration 与视觉回归。

本模块自身验证：

```bash
mvn -pl rewrite-angular-cdk-upgrade -am clean verify
```
