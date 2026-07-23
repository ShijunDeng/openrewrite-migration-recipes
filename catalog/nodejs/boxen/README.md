# boxen 升级规格

> 规格状态：`COMPLETE`；证据状态：`PENDING`；自动化状态：`CATALOG_ONLY`。
> 本 README 已完成工作簿事实、禁止降级边界、不兼容点分类和后续配方验收契约；
> 它不声称尚未固定官方证据的具体 API 已得到确认。
> catalog 本身不包含配方代码；现有候选实现也将在全量规格覆盖完成后逐模块核验和完善。

## 模块身份

| 字段 | 值 |
| --- | --- |
| Catalog 路径 | `catalog/nodejs/boxen` |
| Maven artifactId | `migration-spec-nodejs-boxen` |
| groupId | `com.huawei.clouds.openrewrite` |
| 规范表格标识 | `boxen` |
| Catalog canonical identity | `boxen`（`UNVERIFIED`，只用于避免目录碰撞） |
| 归一语言类 | `nodejs` |
| Excel 原始语言 | `nodejs` |
| 目标版本 | `8.0.1` |
| Excel 迁移边 | 1 |
| 涉及微服务数 | 最大可见值 `1`；不同版本行不累加 |
| 分桶 | `B6_Multi-major单包` |
| 难度 | `高` |
| 工作簿 SHA-256 | `17020a54165808d7a90801b56cf6c7dff428f3b6dfa931b089e84f9946104309` |
| 候选实现模块 | `NONE`（尚无已识别的顶层实现模块） |

## Excel 事实快照

本节逐字记录表格，不把自动分桶、难度或备注提升为官方兼容性结论。厂商后缀、
截断显示、无法解析值和疑似跨发布线目标均原样保留。

| Excel 行 | 序号 | 软件名称 | 原始语言 | 原始版本 | 目标版本 | 微服务数 | 分桶 | 难度 | 保守方向/动作 | 原始备注 |
| ---: | ---: | --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| 847 | 846 | `boxen` | nodejs | `4.2.0` | `8.0.1` | 1 | B6_Multi-major单包 | 高 | upgrade-candidate/mark | 跨2+个大版本，breaking change概率极高，需API迁移 |

## 升级方向与禁止降级

- 表格原始源版本记录（不是 AUTO 白名单）：`4.2.0`。
- 升级候选边：`4.2.0`；在 E-001～E-003 完成前仍保持 `MARK`。
- 相同版本 NOOP：`NONE`。
- 潜在降级冲突：`NONE`。
- 截断、聚合或无法可靠比较：`NONE`。
- 任何高于目标的版本、更新发布线或无法可靠比较的厂商版本必须保持字节级不变，并在
  真实依赖 owner 上标记 `目标版本冲突（禁止降级）`；本项目不存在回退路径。
- 表外低版本、动态版本、范围、变量、BOM/platform、parent、catalog、workspace、
  constraints 和锁文件不能被猜测式改写；应定位并迁移真正的版本 owner。
- 若同一模块列出多个坐标或别名，配方必须分别证明身份；在官方 relocation 证据固定前，
  不得因为 artifact 名相同而跨 group、生态或发行渠道改坐标。


## 不兼容点规格

| ID | 维度 | 适用迁移边 | Excel 提示 | 官方确认事实 | 处置契约 |
| --- | --- | --- | --- | --- | --- |
| C-001 | 多主版本 API / 数据 / 协议 / 工具链 | Excel #847 4.2.0 [upgrade-candidate/mark: 表格方向看似升级，但制品身份和官方兼容证据未固定；当前仅作为候选边。] → `8.0.1` | 跨2+个大版本，breaking change概率极高，需API迁移 | `UNVERIFIED` | 按每个中间主版本逐跳建立证据和回归门禁，不把多跳升级伪装成一次兼容升级；需要分阶段处理源码、配置、数据、协议、运行时与回滚。 |

`UNVERIFIED` 表示 Excel 提示已进入规格，但尚未用不可变的官方 tag/commit、发布说明和
制品元数据完成验证。此时允许 README 和精确 MARK 设计，不允许据此发明 API AUTO。

### `nodejs` 生态最低核查项

- 确认规范 npm 包名；覆盖 package.json 的 workspace、dependencies/dev/peer/optional owner 与锁文件。
- 核查 Node.js/TypeScript/框架 peer 基线、ESM/CJS 与 exports、类型声明、深导入和构建测试工具。
- 核查浏览器/SSR、模板与样式、运行时默认值、bundler tree-shaking、配置和持久化数据。

## 证据台账

| Claim ID | 待证明事项 | 状态 | 固定官方证据 | 形成 AUTO 的条件 |
| --- | --- | --- | --- | --- |
| E-001 | 包/坐标身份、源版本和目标制品身份 | `UNVERIFIED` | 后续固定官网、registry/repository 元数据与 SHA | 身份无歧义且目标确为升级 |
| E-002 | 每条迁移边的 API、配置和默认行为变化 | `UNVERIFIED` | 后续固定 release notes、迁移指南、tag/commit diff | 存在一一对应且语义等价的变换 |
| E-003 | 真实工程中的用法和负例 | `UNVERIFIED` | 后续固定真实仓库 commit、路径、许可证与裁剪说明 | 正例、负例和上下文边界均可复现 |

真实仓库只能证明“用法存在”，不能替代官方兼容性证据。推断必须显式标为
`INFERENCE`；只有固定上游证据支持的事实才能改为 `VERIFIED`。

## 后续 OpenRewrite 配方契约

### AUTO

- 当前阶段 AUTO 白名单为空；只有 E-001～E-003 变为 `VERIFIED` 后，升级候选边才可逐项进入；
- 只处理经验证的原子源版本、明确坐标和当前文件拥有的标准依赖声明；
- 更高版本永不降级，表外版本、变体和外部 owner 永不猜测；
- 只实现有官方源码证明、上下文无歧义、行为等价且可幂等运行的 AST/配置修改；
- 保留 scope、classifier/type、optional、exclusions、workspace/profile 和相邻内容。

### MARK

- 在具体依赖、属性、BOM/platform、调用、类型、配置键或资源节点标记未决事项；
- marker 必须说明业务 owner 需要作出的决定、所需证据和验收方法；
- 不用文件级泛化告警代替精确定位，也不把 README 文字伪装成已执行迁移。

### MANUAL

- 运行时流量、安全策略、数据和 wire format、集群滚动策略、原生 ABI、性能容量、
  外部服务兼容性与回滚均由业务证据决定；
- 无法通过静态上下文证明安全的语义变换保持原样。

## 测试与真实用例验收

- 每个经验证的升级候选源版本才要求 AUTO 正例；目标/相同行为 NOOP；
- 冲突、未知、截断和聚合版本保持不变并 MARK；所有更高版本和更高发布线验证禁止降级；
- 覆盖对应生态的直接声明、共享 owner、BOM/platform/workspace、动态值、范围、锁文件和变体；
- 覆盖同名业务符号、相似坐标、注释/字符串、生成目录、缓存和安装产物负例；
- 每项 AUTO 有 before/after、类型或结构归因、两轮幂等和 aggregate 顺序测试；
- 固定真实仓库 commit 与文件路径，记录裁剪内容；真实夹具不能取代官方差异证据；
- 最终执行编译、单元/集成、行为、安全、性能、数据兼容、部署和回滚门禁。

## 当前阶段结论

本模块的不兼容点文档规格已经建立；官方证据、真实仓库夹具和可执行配方属于下一阶段。
在 E-001～E-003 完成前，除严格版本所有权和禁止降级守卫外，不批准猜测式 AUTO。
