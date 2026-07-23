# 已完成模块官方能力复用复审台账

> 状态：`COMPLETE`（本轮候选 4/4）
> 复审基线：`main@d6eeedd`
> 记录日期：2026-07-23

本台账记录对既有配方代码的补充复审，不改变
[`high-priority-java-ledger.md`](high-priority-java-ledger.md) 的 25/25 范围。
复审目标是删除与 OpenRewrite 官方能力重复的自研转换，优先以固定版本二进制依赖
直接组合官方 recipe；本地代码只保留工作簿精确版本门控、语义安全护栏、官方
LST 元数据缺口和尚无官方实现的行为迁移。

## 模块复审结果

| 模块 | 复用的官方能力 | 保留的本地职责 | 验证 | 提交 |
| --- | --- | --- | ---: | --- |
| `rewrite-junit-jupiter-api-upgrade` | `rewrite-testing-frameworks:3.42.1` 的 `MigrateMethodOrdererAlphanumeric`、官方依赖升级和 Core `ChangeMethodName` | 升级前 build-root 门控、interceptor 方法体保留、官方 orderer 类型元数据修复 | JDK 17/21 各 223 项 | `9bf8c3e` |
| `rewrite-guava-upgrade` | `rewrite-migrate-java:3.40.0` 的 `NoGuavaCreateTempDir`、`NoGuavaDirectExecutor`、65 个 `InlineMethodCalls` 叶及官方依赖升级 | 精确版本/Java 11 门控、缺失 overload 补齐、行为风险定位 | JDK 17/21 各 76 项 | `1f1a60f` |
| `rewrite-junit-jupiter-upgrade` | `rewrite-testing-frameworks:3.42.1` 的安全 JUnit 5.13/5.14/6 叶、官方依赖升级及 Core recipe | 排除宽泛 `JUnit5to6Migration` 的删除/最低 JRE 副作用，保留精确门控、平台表达式和 interceptor 缺口 | JDK 17/21 各 273 项 | `d9736b6` |
| `rewrite-commons-codec-upgrade` | `rewrite-apache:2.28.0` 的 `ApacheBase64ToJavaBase64`、`rewrite-java-dependencies:1.59.0` 的 `UpgradeDependencyVersion`、Core `ChangeMethodName` / `ReplaceConstantWithAnotherConstant` | Base64 null/MIME/URL-safe/streaming 语义护栏、精确项目门控、raw syntax fallback、风险定位；旧公开类仅作为官方叶兼容组合 | JDK 17/21 各 254 项 | `d6eeedd` |

## 验收规则

- 官方聚合不是无条件启用：先展开 recipe tree，只选择与模块目标、源版本和行为边界
  一致的叶；会删除配置、改变最低 JRE、扩大依赖族或改变协议语义的聚合明确排除。
- 官方通用依赖 recipe 必须位于升级前 project marker 之后；目标版、未来版、表外版、
  动态/范围/variant、混合版本、嵌套和兄弟 build root 均有负例。
- 组合测试校验官方 recipe 名、实现类、artifact 版本、选项和顺序；行为测试同时覆盖
  Maven model-aware 路径与本地严格 fallback。
- 每个模块均执行 JDK 17、JDK 21 `clean verify`；全量离线 Catalog 仍为 1,967 个模块、
  4,887 行，禁止降级策略有效。

## 远端门禁

- `d33a088` 修复 `org-json` 版本正则回溯导致的 CodeQL 告警。
- `8986309` 将 Actions 固定到提交 SHA，设置 Maven 90 分钟上限和并发去重，稳定
  Catalog、Workflow Policy、Dependency Review、CodeQL、JDK 17/21 Maven 六项门禁。
- main branch protection 的六项 required checks 均绑定 GitHub Actions app；历史
  Maven `cancelled` 是后续 main 推送触发的预期去重，不是失败。
- 远端长流水线异步运行；本地双 JDK 和 Catalog 验收完成后继续模块提交，不以远端
  等待阻塞开发。
