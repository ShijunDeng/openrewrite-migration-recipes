# Prettier 1.19.1 / 2.8.8 → 3.6.2

本模块把工作簿中的两个 Prettier 升级项实现为可审计的 OpenRewrite 配方：

| 工作簿位置 | 序号 | 源版本 | 目标版本 |
| --- | ---: | ---: | ---: |
| 行 563 | 562 | 1.19.1 | 3.6.2 |
| 行 1738 | 1737 | 2.8.8 | 3.6.2 |

配方遵循 **AUTO 先于 MARK**：只自动修改能够证明唯一所有权且语义确定的内容；异步传播、插件契约、运行时和输出变化均保留给业务所有者处理。

## 固定依据

版本源码全部固定到 peeled commit，避免移动 tag 或主分支改变审计结论：

- [Prettier 1.19.1：`95b8e54d1a6eeabebd81db37a4e088ea1de9197d`](https://github.com/prettier/prettier/tree/95b8e54d1a6eeabebd81db37a4e088ea1de9197d)
- [Prettier 2.8.8：`1b7fad52558e16444399d11ff2d89aa8ed895c77`](https://github.com/prettier/prettier/tree/1b7fad52558e16444399d11ff2d89aa8ed895c77)
- [Prettier 3.6.2：`7a8b05f41574633fd3af5298f3eeaf33567ad3d3`](https://github.com/prettier/prettier/tree/7a8b05f41574633fd3af5298f3eeaf33567ad3d3)
- [官方 Prettier 3.0 迁移说明](https://prettier.io/blog/2023/07/05/3.0.0.html)
- [发布到 npm 的 3.6.2 `package.json`](https://unpkg.com/prettier@3.6.2/package.json)
- [3.6.2 公共 API 类型声明](https://unpkg.com/prettier@3.6.2/index.d.ts)

Node 基线采用**发布包**的 `engines.node >=14`，不是 Prettier 源码仓为开发自身设置的 Node 版本。3.6.2 根导出同时提供 ESM `index.mjs`（`import`）和 CJS `index.cjs`（`require`）；因此根 `require("prettier")` 本身不会被误报。

## 已处理的不兼容点

### AUTO：确定性修改

`UpgradeSelectedPrettierDependency` 只处理 `package.json` 根级 `dependencies`、`devDependencies`、`peerDependencies`、`optionalDependencies` 中：

- 精确 `1.19.1` / `2.8.8`；
- `^1.19.1` / `^2.8.8`；
- `~1.19.1` / `~2.8.8`。

它保留版本运算符并改到 `3.6.2`。范围、workspace/catalog、npm alias、Git、文件、URL、预发布、未列入工作簿的版本、override/resolution 和 lockfile 均不猜测。

`MigratePrettierConfigOption` 把 `jsxBracketSameLine` 重命名为 `bracketSameLine`，但仅限：

- 专用 `.prettierrc` / JSON / YAML 根对象；
- `package.json` 的根级 `prettier` 对象；
- `module.exports = { ... }` 或 `export default { ... }` 的直接对象字面量。

只有旧键恰好出现一次、没有新键冲突、JS 对象没有 spread 时才修改。嵌套 override、计算属性、变量间接导出和重复键不会自动处理。

### MARK：需要业务决策

`FindPrettierManifestRisks` 标记：

- 未被严格 AUTO 选中的直接 Prettier owner；
- `overrides` / `resolutions` 的独立版本所有权；
- 低于或无法证明满足 Node 14 的 `engines.node`；
- 已移除的 `--plugin-search-dir`、`--no-plugin-search`、物理 `bin-prettier.js`，以及改名为 `--log-level` 的 `--loglevel`；
- Prettier 3 已自带类型时残留的 `@types/prettier`；
- 每个显式 `prettier-plugin-*` 的 3.x 兼容性。

`FindPrettierConfigRisks` 标记：

- 同一已证明 owner 同时声明 `jsxBracketSameLine` 和 `bracketSameLine`；
- 已移除的 `pluginSearchDirs`；
- 显式 `plugins` 的 ESM、完整路径/扩展名、加载顺序和快照复核。

`FindPrettierSourceRisks` 使用 import/require 所有权和局部声明计数，精确标记：

- 变为 Promise API 的 `format`、`formatWithCursor`、`formatAST`、`check`、`getSupportInfo`、`clearConfigCache`、`resolveConfig`、`resolveConfigFile`、`getFileInfo`；
- 被移除的 `resolveConfig.sync`、`resolveConfigFile.sync`、`getFileInfo.sync`；
- 旧的 `prettier/parser-*`、`prettier/esm/*`、`prettier/bin-prettier.js` 物理入口；
- 插件 parser/preprocess 的异步能力、`embed` 新签名、`parse` 不再接收 parsers 参数、`print` 必须返回 Doc，以及显式 ESM 插件加载；
- 被移除/改变的 `doc.builders.concat`、`getDocParts`、`propagateBreaks`、`cleanDoc`、`getDocType`、`printDocToDebug`。

此外，Prettier 3 还改变了 `trailingComma` 默认值（`es5` → `all`）、未知代码不再默认使用 `babel` parser、默认忽略 `.gitignore` 文件、`getFileInfo` 默认解析配置，以及部分格式化输出。它们不能仅凭一个语法节点安全重写，升级后必须对完整文件集运行 `prettier --check` 并审查快照/生成物差异。

## 配方

严格低层入口（仅依赖版本）：

```yaml
recipeList:
  - com.huawei.clouds.openrewrite.prettier.UpgradePrettierTo3_6_2
```

推荐迁移入口：

```text
com.huawei.clouds.openrewrite.prettier.MigratePrettierTo3_6_2
```

推荐配方的顺序固定为：依赖 AUTO → 配置 AUTO → manifest MARK → 配置 MARK → 源码 MARK。

## 路径边界

模块允许普通根工程和 workspace 子包；跳过父目录中的 `node_modules`、构建/覆盖率输出、vendor/cache，以及名称以 `generated` 或 `install` 开头的目录。文件名为 `install.js` 的正常源码叶节点仍会分析，避免把安装目录规则错误扩大到文件名。

## 真实仓库固定用例

- [DevCloudFE/ng-devui `.prettierrc` @ `ef76c44e4a7489cfbc587056d947094d0a0c3d1e`](https://github.com/DevCloudFE/ng-devui/blob/ef76c44e4a7489cfbc587056d947094d0a0c3d1e/.prettierrc)：真实 JSON 旧键。
- [antvis/F2 `.prettierrc.yml` @ `fff56a0f6e0d6109a003bfb29b3376ab9865c731`](https://github.com/antvis/F2/blob/fff56a0f6e0d6109a003bfb29b3376ab9865c731/.prettierrc.yml)：真实 YAML 旧键和文档标记。
- [ShareDropio/sharedrop `prettier.config.js` @ `2b6a9fe5e6cada786d1a1f319612537305b204c7`](https://github.com/ShareDropio/sharedrop/blob/2b6a9fe5e6cada786d1a1f319612537305b204c7/prettier.config.js)：真实 CommonJS 配置对象。
- [liriliri/licia `lib/format.js` @ `0cfbc3f1b589f2181aff027811d48a889f432966`](https://github.com/liriliri/licia/blob/0cfbc3f1b589f2181aff027811d48a889f432966/lib/format.js)：同步消费 `prettier.format` 返回值。
- [vueComponent/ant-design-vue `scripts/prettier.js` @ `ca85aec996e0019cffb07a0f879b47ec770a0be4`](https://github.com/vueComponent/ant-design-vue/blob/ca85aec996e0019cffb07a0f879b47ec770a0be4/scripts/prettier.js)：`resolveConfig.sync`、`getFileInfo.sync` 和同步 `format` 的组合。

## 测试映射

| 规范 | 配方 | 主要测试 |
| --- | --- | --- |
| 工作簿精确 / `^` / `~` | `UpgradeSelectedPrettierDependency` | 两个源版本、四个直接 section、范围/协议/未列版本反例、workspace/生成目录、幂等 |
| 唯一配置键所有权 | `MigratePrettierConfigOption` | JSON、YAML、CJS、ESM、`package.json#prettier`、冲突/重复/spread/嵌套反例、3 个真实仓库 |
| Node / CLI / plugin / types / overrides | `FindPrettierManifestRisks` | Node 正反边界、全部移除参数、类似包反例、幂等 |
| 配置冲突与插件加载 | `FindPrettierConfigRisks` | JSON、YAML、CJS、package owner、AUTO-before-MARK |
| Promise / sync / plugin / doc / entry | `FindPrettierSourceRisks` | 每个公共 API 的 ESM named/namespace 与 CJS 用例、shadow 反例、真实仓库、幂等 |

当前共 185 个测试，全部同时作为正例或明确的误报防护边界。

独立验证：

```bash
mvn -f rewrite-prettier-upgrade/pom.xml clean verify
```
