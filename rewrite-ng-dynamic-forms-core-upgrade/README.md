# @ng-dynamic-forms/core upgrade to 18.0.0

本模块对应 `开源软件升级.xlsx` 中的 `@ng-dynamic-forms/core`，覆盖 `14.0.0`、`15.0.0`、`16.0.0`，目标版本为 `18.0.0`。

配方名称：

```text
com.huawei.clouds.openrewrite.ngdynamicforms.UpgradeNgDynamicFormsCoreTo18_0_0
```

## 自动处理范围

配方只修改根目录或 workspace 子目录 `package.json` 的 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies` 中名为 `@ng-dynamic-forms/core` 的直接 npm registry 声明。它处理 14.x–17.x 的精确版本、caret、tilde、comparator range、major wildcard、`v` 前缀与 prerelease，统一设置为 `18.0.0`。

它不会降级 18.x+，不会覆盖 `workspace:`、npm alias、Git、file、URL、tag、无界范围，也不会修改 lockfile、Angular、RxJS、core-js、TypeScript 或任何 NG Dynamic Forms UI 包。升级后必须由原包管理器重新解析 peer dependency 并重建锁文件。

`@ng-dynamic-forms/core@18.0.0` 的官方 peer 是 Angular common/core/forms `^16.0.0`、`core-js ^3.31.0`、`rxjs ^7.5.7`。这不是“只换一个版本号”就能安全完成的升级：旧 Angular 12/13/15 工程应先按 Angular 官方逐大版本迁移，再安装目标包。

## 不兼容修改点

| 版本跨度内的变化 | 影响与迁移建议 |
| --- | --- |
| v15 将工程迁到 Angular 13 | 从 v14/Angular 12 升级时同步升级 Angular framework、CLI、compiler-cli 与 CDK/Material；重新运行 AOT、strict template 和 production build |
| v15 的 core peer 从 RxJS 6.6.3 升到 RxJS 7.5.5 | 检查废弃 operator/result selector、`toPromise()`、scheduler、Subscription teardown 以及测试 marble；目标 core 要求 RxJS `^7.5.7` |
| v16 将库迁到 Angular 15，并在内部改用 Untyped Forms | `FormControl`/`FormGroup` typed-forms 泛型与 NG Dynamic Forms 的 `UntypedForm*` 边界可能需要显式类型收窄；检查自定义 model、validator、control mapper 和继承组件 |
| v17 将 Material UI 迁到 Material 15 MDC | 使用 `ui-material` 时必须执行 Angular Material MDC migration；回归主题 Sass、DOM/CSS selector、密度、typography、form-field、checkbox/radio/slide-toggle 与 test harness |
| v18 将库迁到 Angular 16 | 目标仓库使用 Angular 16.1.3、TypeScript 5.1.6、Zone.js 0.13.1；按 Angular 16 兼容矩阵使用受支持 Node/TypeScript，并处理 Angular 16 的构建器、SSR/hydration 与严格类型变化 |
| v18 core peer 提升到 core-js 3.31.0、RxJS 7.5.7 | 旧 polyfill 和 RxJS 6 不能原样保留；在应用支持的浏览器矩阵上验证 Promise、iterator、structured APIs、async validator 和 relation observable 行为 |
| v18 公开 standalone components/directives | standalone 应用应把实际使用的 UI component/directive 放进 `imports`；测试中的 TestBed 也应放到 `imports`，不能再放 standalone declaration 到 `declarations` |
| `DynamicFormsCoreModule.forRoot()` 在 v18 删除 | 删除 `.forRoot()`，NgModule 应直接 import `DynamicFormsCoreModule`；核心 services 已 `providedIn: "root"`，自定义 matcher/validator token provider 仍需在合适 injector 提供 |
| 各 UI 包的 `*FormUiModule` 在 v18 删除 | 例如 `DynamicBasicFormUiModule`、`DynamicMaterialFormUiModule`、`DynamicBootstrapFormUiModule` 不再导出；逐个改为导入 standalone form/container/control components，检查 shared module 和 lazy route |
| v18 停止 `@ng-dynamic-forms/ui-kendo` | 必须选择自维护 renderer、迁移到仍支持的 UI 包，或直接使用 core 构建自定义动态控件；删除 Kendo UI module/component import 后做等价功能和许可核查 |
| companion UI packages 必须配套到 18.x | `ui-basic`、`ui-bootstrap`、`ui-foundation`、`ui-ionic`、`ui-material`、`ui-ng-bootstrap`、`ui-ngx-bootstrap`、`ui-primeng` 都以 core `^18.0.0` 为 peer；本配方有意不跨模块改它们 |
| UI 框架 peer 发生联动 | 官方 v18 分别面向 Material 16、Ionic 7、PrimeNG 16；basic/bootstrap/foundation 方案要求对应的 ngx-mask，Bootstrap 两个 renderer 的 Bootstrap/ngx-bootstrap peer 也不同，按实际所用包逐项核对 |
| standalone 改造改变组件作用域 | 以前由 UI module 间接导出的 pipe/directive/component 不再天然可见；检查 feature module、route-level imports、storybook、dialog/overlay 动态创建与测试 fixture |
| OnPush 与手工 model 更新语义仍然存在 | `value`、`disabled` setter 之外的 label/layout 等 model 修改通常仍需 `DynamicFormService.detectChanges()`；迁移后回归异步 validator、relations、数组增删和动态 template |
| 自定义 `DYNAMIC_FORM_CONTROL_MAP_FN` 与 DI 范围 | standalone/lazy 环境中 provider 位置可能产生多实例或不可见；覆盖 root、lazy route、dialog、TestBed override 与 SSR 请求级 injector |
| mask 支持与 `maskConfig` | v14 已从 angular2-text-mask 转到 ngx-mask，目标部分 UI 包要求 ngx-mask 16、部分仍声明 13；不要盲目统一，按所选 renderer 的 v18 manifest 与 Angular 版本验证 ControlValueAccessor |
| 序列化模型和验证函数跨严格类型边界 | 对从 JSON 恢复的 model、日期、文件、option value、自定义 async validator 做运行期 schema 校验；配置式依赖配方不会修改业务模型或补类型转换 |

目标包的 UI peer 配套如下；实际项目只需处理自己使用的 renderer：

| v18 UI 包 | 主要 peer 要求 |
| --- | --- |
| `ui-basic` | core 18、ngx-mask 16 |
| `ui-bootstrap` | core 18、Bootstrap 3、ngx-bootstrap 6、ngx-mask 16 |
| `ui-foundation` | core 18、Foundation Sites 6、ngx-mask 16 |
| `ui-ionic` | core 18、Ionic Angular 7 |
| `ui-material` | core 18、Angular Material 16 |
| `ui-ng-bootstrap` | core 18、ng-bootstrap 11、Bootstrap 4、ngx-mask 13 |
| `ui-ngx-bootstrap` | core 18、ngx-bootstrap 8、Bootstrap 4、ngx-mask 13 |
| `ui-primeng` | core 18、PrimeNG 16；Quill 为 optional dependency |

官方依据包括 [v18.0.0 release](https://github.com/udos86/ng-dynamic-forms/releases/tag/v18.0.0)、[v18 CHANGELOG](https://github.com/udos86/ng-dynamic-forms/blob/v18.0.0/CHANGELOG.md)、[core v18 manifest](https://github.com/udos86/ng-dynamic-forms/blob/v18.0.0/projects/ng-dynamic-forms/core/package.json)、[v17→v18 source diff](https://github.com/udos86/ng-dynamic-forms/compare/v17.0.0...v18.0.0) 和 [Angular 官方版本兼容表](https://angular.dev/reference/versions)。

## 测试样本来源

- [dhrn/electron-mailer-poc](https://github.com/dhrn/electron-mailer-poc/blob/51602c24bc36c400fe8d5a28a02d7a06188aa11f/package.json) 的 Angular 12 + core/ui-material 14 组合
- [Patrick5078/Angular-form-builder](https://github.com/Patrick5078/Angular-form-builder/blob/1a5d5f64142c68bf869ccd75b312a24fbac7c181/package.json) 的 Angular 13 + core/ui-basic 15 组合
- [umd-lib/mdsoar-angular](https://github.com/umd-lib/mdsoar-angular/blob/0e309c2b2aeba34c6815b5e4df56fc26fe1bbc4b/package.json) 的 workspace 应用 core 16 组合
- [NG Dynamic Forms 官方 v18 core manifest](https://github.com/udos86/ng-dynamic-forms/blob/v18.0.0/projects/ng-dynamic-forms/core/package.json) 的目标 peer 边界
- OpenRewrite 官方 [ChangeValueTest](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/ChangeValueTest.java) 与 [JsonPathMatcherTest](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-json/src/test/java/org/openrewrite/json/JsonPathMatcherTest.java) 的 JSONPath filter、格式保持和 no-op 测试结构

38 个测试覆盖三个固定 commit 的真实工程、表格全部版本、14.x–17.x patch、常见 semver、四依赖区、多层 workspace、JSON5 格式保持、相邻 Angular/UI/RxJS 保持，以及目标/新版本、协议引用、URL、旧 13.x、tag、数字子串、lockfile、普通 JSON、相似包名和缺失依赖不修改。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-ng-dynamic-forms-core-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.ngdynamicforms.UpgradeNgDynamicFormsCoreTo18_0_0
```

确认 patch 后，按 Angular 12→13→14→15→16 顺序完成官方 migrations；同步使用中的 NG Dynamic Forms UI 包，删除 `.forRoot()` 与旧 UI module import，再重建 lockfile。运行 production/AOT build、strict template/typecheck、unit/E2E、SSR/hydration、lazy route、动态 component、mask、async validation、relation、form array、Material MDC 视觉快照和可访问性测试。

本模块自身验证：

```bash
mvn -f rewrite-ng-dynamic-forms-core-upgrade/pom.xml clean verify
```
