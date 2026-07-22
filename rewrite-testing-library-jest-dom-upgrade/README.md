# @testing-library/jest-dom 升级到 6.9.1

本模块处理 npm 包 `@testing-library/jest-dom`。它只采用 `开源软件升级.xlsx` 中明确出现的版本映射，不把相邻版本、复合 semver 或包管理器协议猜成同一升级意图。

| XLSX 行 | 序号 | 原始版本 | 目标版本 | 微服务数 | 分桶 / 难度 |
| --- | --- | --- | --- | --- | --- |
| 2060 | 2059 | `5.17.0` | `6.9.1` | 1 | `B4_Major单包` / 中 |

推荐入口：

```text
com.huawei.clouds.openrewrite.testinglibraryjestdom.MigrateJestDomTo6_9_1
```

只升级依赖声明的低层入口：

```text
com.huawei.clouds.openrewrite.testinglibraryjestdom.UpgradeJestDomTo6_9_1
```

## spec → recipe → test

| 规格 | 配方/实现 | 主要测试 |
| --- | --- | --- |
| XLSX 唯一源版本严格升级 | `UpgradeSelectedJestDomDependency` | exact/caret/tilde、四个 dependency section、range/protocol/alias/target NOOP |
| 根级 runner JSON setup | `MigrateJestDomPackageConfiguration` | Jest removed entry、Vitest 专用 entry、任意嵌套同名配置 NOOP |
| 删除的 v5 side-effect 入口 | `MigrateRemovedJestDomEntries` | 纯副作用 import/dynamic import/direct require、返回值/绑定/re-export 反例、Alibaba 与 NFL 固定提交夹具 |
| v6 公共 matchers/runner 入口 | `MigrateRemovedJestDomEntries` | `dist/matchers`、`.js` 后缀、同文件 Vitest / `@jest/globals` ownership、歧义 NOOP |
| manifest 人工决策 | `FindJestDomManifestRisks` | unlisted/range、根级 npm/pnpm/Yarn override、external types、runner、Node、Jest config、setup leaf 精确 MARK |
| export map 与 import shape | `FindJestDomSourceRisks` | unpublished deep path、side-effect binding、default/side-effect matchers、runner mismatch/multiple owner 精确 MARK |
| deprecated matcher 语义 | `FindJestDomSourceRisks` | 四种 matcher、`.not` 链、非 `expect(...)` lookalike NOOP |
| 生成物隔离 | `JestDomSupport.isProjectPath` | 只过滤父目录；大小写 `node_modules`、`generated*`/`install*` 与常见 cache 父目录，保留 `src/install.js` 叶文件 |
| discovery、组合与幂等 | declarative `rewrite.yml` | strict/recommended validation，AUTO 与 MARK 两轮测试 |

## AUTO 与 MARK 边界

| 不兼容点 | 行为 | 原则 |
| --- | --- | --- |
| 直接依赖 `5.17.0` | **AUTO** | 只改 `package.json` 根级四类 dependency section 的精确、`^`、`~` 单版本并保留运算符 |
| `/extend-expect`、`/dist/extend-expect`、`/dist/index` | **AUTO/MARK** | 纯副作用静态/动态 ESM、direct CJS 与 runner config 迁到公开入口；绑定、re-export 或返回值被消费时不能证明语义，只 MARK |
| `/dist/matchers`、`/matchers.js` | **AUTO** | 精确迁到 target export map 的 `/matchers`，不重写未知 individual matcher deep import |
| Vitest / `@jest/globals` 同文件 ownership | **AUTO** | 仅一个 runner 可证明时，把 root side effect 选成 `/vitest` 或 `/jest-globals`；两个 runner 同时出现不猜测 |
| 根级 Jest/Vitest JSON setup | **AUTO** | 只处理 `jest.setupFilesAfterEnv` / `vitest.setupFiles`，不修改任意嵌套 lookalike |
| range/protocol/alias/catalog/override | **MARK** | 不猜 package-manager 解析；只识别根级 npm `overrides`、Yarn `resolutions`、`pnpm.overrides` selector |
| external `@types/testing-library__jest-dom` | **MARK** | v6 改为本地、runner-specific declarations；需确认 augmentation owner 后删除旧类型包 |
| Node、TypeScript setup、runner | **MARK** | v6 要求 Node >=14；全局 matcher 类型要求 `.ts` setup 被 tsconfig include；Vitest 与 `injectGlobals:false` 使用专用入口 |
| 未发布 deep path / 非法 import shape | **MARK** | target export map 是闭集；root runner entry 是 side-effect-only，`/matchers` 只有 named exports |
| deprecated matcher | **MARK** | `toBeInTheDOM` 等替换涉及 detached node、ARIA 或 whitespace 语义，不能机械改名 |

SearchResult 是人工决策点。推荐配方不会把 MARK 当成修复，也不会修改 lockfile、测试快照、Node 镜像或 tsconfig include。

## 严格 manifest 所有权

以下三种声明会升级：

```json
{
  "devDependencies": {
    "@testing-library/jest-dom": "5.17.0"
  }
}
```

`5.17.0`、`^5.17.0`、`~5.17.0` 分别变为 `6.9.1`、`^6.9.1`、`~6.9.1`。以下内容不 AUTO：

- `>=5.17 <7`、hyphen、union、wildcard 等复合范围；
- `workspace:`、`npm:` alias、git/GitHub、URL、`file:`、`link:`、tag；
- override/resolution/catalog 与任意嵌套同名键；
- lockfile、普通 JSON、生成物与依赖目录。

运行后用项目锁定的 npm/pnpm/yarn 重建 lockfile，并检查真实图：

```bash
npm install
npm ls @testing-library/jest-dom @types/testing-library__jest-dom jest vitest
```

## 5.17 → 6.9.1 的官方边界

官方 [`v6.0.0` release](https://github.com/testing-library/jest-dom/releases/tag/v6.0.0) 明确列出 breaking change：删除 `extend-expect`，新增 root、`/jest-globals`、`/vitest` 三个 side-effect owner，并保留无副作用的 `/matchers`。同一 release 引入本地类型并停止支持 Node <14。

目标提交 [`0ff8904ff4683d676ff70ab68b7f08465c44d0d0`](https://github.com/testing-library/jest-dom/tree/0ff8904ff4683d676ff70ab68b7f08465c44d0d0) 的 [`package.json`](https://github.com/testing-library/jest-dom/blob/0ff8904ff4683d676ff70ab68b7f08465c44d0d0/package.json) 与 npm tarball 一致：

- `exports` 只开放 `.`, `./jest-globals`, `./matchers`, `./vitest`, `./package.json`；
- 每个 runtime entry 都有 CJS `require` 与 ESM `import` 条件，因此 `require('@testing-library/jest-dom')` 本身仍受支持；
- types 分别指向 Jest globals、`@jest/globals`、standalone matchers 与 Vitest declaration；
- `engines.node` 是 `>=14`；
- 6.9.1 修复 Node 环境中未定义 `Node` 的错误，不能用旧版本行为快照替代 target 回归。

官方 [`README`](https://github.com/testing-library/jest-dom/blob/0ff8904ff4683d676ff70ab68b7f08465c44d0d0/README.md) 还要求：Jest setup 加入 `setupFilesAfterEnv`；`injectGlobals:false` 使用 `/jest-globals`；Vitest setup 使用 `/vitest`；TypeScript setup 必须为 `.ts` 且被 tsconfig include。`toBeEmpty`、`toBeInTheDOM`、`toHaveDescription`、`toHaveErrorMessage` 在 target 文档中仍是 deprecated，替换存在可观察语义差异，所以只 MARK。

## 发布物与固定提交证据

官方 tag 与 npm `gitHead` 一致：

- `v5.17.0` = `d717c66cb4a32c806e53b287418a4013d37898fb`；
- `v6.9.1` = `0ff8904ff4683d676ff70ab68b7f08465c44d0d0`。

npm 发布物：

- 5.17.0 tarball integrity：`sha512-ynmNeT7asXyH3aSVv4vvX4Rb+0qjOhdNHnO/3vuZNqPmhDpV/+rCSGwQ7bLcmU2cJ4dvoheIO85LQj0IbJHEtg==`；
- 6.9.1 tarball integrity：`sha512-zIcONa+hVtVSSep9UT3jZ5rizo2BsxgyDYU7WFD5eICBE7no3881HGeb/QkGfsJs6JTkY1aQhT7rIPC7e+0nnA==`。

target tarball 已逐项核对 `dist/index.{js,mjs}`、`dist/jest-globals.{js,mjs}`、`dist/matchers.{js,mjs}`、`dist/vitest.{js,mjs}` 与四组 declaration；不存在 `extend-expect`。

## 真实仓固定 commit 夹具

- [alibaba/ChatUI `97dfef6bb698697097a17bd103b9710e0f5d289f`](https://github.com/alibaba/ChatUI/blob/97dfef6bb698697097a17bd103b9710e0f5d289f/jest.setup.ts)：真实 `import '@testing-library/jest-dom/extend-expect'`；
- [incarnateTheGreat/nfl `7d6a043f6acb84dbb28fa219a706cb45107ee1bb`](https://github.com/incarnateTheGreat/nfl/blob/7d6a043f6acb84dbb28fa219a706cb45107ee1bb/jest.config.js)：真实 `setupFilesAfterEnv: ['@testing-library/jest-dom/dist/extend-expect']`；
- [this-ezzy/lendsqr-fe-test `25cb9693d8a64cabd687fb68c4fa1c9f0ec12133`](https://github.com/this-ezzy/lendsqr-fe-test/blob/25cb9693d8a64cabd687fb68c4fa1c9f0ec12133/package.json)：真实 `^5.17.0`，并带 `@jest/globals`/TypeScript 测试栈；
- 测试结构参考 OpenRewrite 官方 [`UpgradeDependencyVersionTest` 固定提交 `decb8dbb2b5b726f8815efc51c85c34a60268bb0`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)，并增加 JavaScript AST、runner ownership、export map、marker 与两轮幂等反例。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-testing-library-jest-dom-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.testinglibraryjestdom.MigrateJestDomTo6_9_1
```

模块独立验证：

```bash
mvn -f rewrite-testing-library-jest-dom-upgrade/pom.xml clean verify
```

当前共 86 个测试：44 个 dependency/manifest ownership 测试、16 个确定性 AUTO 测试、26 个 MARK/组合/幂等测试。
