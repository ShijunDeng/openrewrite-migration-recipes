# Fastjson 迁移到 2.0.62

本模块对应 `开源软件升级.xlsx` 的 `com.alibaba:fastjson` 行，目标版本为 `2.0.62`。推荐入口是：

```text
com.huawei.clouds.openrewrite.fastjson.MigrateFastjsonTo2_0_62
```

它采用 Fastjson2 官方兼容 artifact（坐标仍为 `com.alibaba:fastjson`），完成严格依赖升级、确定性源码迁移，并把不能从语法推断的行为选择精确标成 `SearchResult`。旧入口 `UpgradeFastjsonTo2_0_62` 保留为同一配方的兼容别名。

## 严格版本白名单

只接受工作簿中完整可见的十个源版本：

```text
1.2.40, 1.2.70, 1.2.71, 1.2.75, 1.2.83, 1.2.83_noneautotype,
2.0.12, 2.0.13, 2.0.14, 2.0.16
```

工作簿最后一个单元格写作 `2.0.16 ... (共25个版本)`，但没有保存其余精确值。本模块不会根据 Maven Central 发布历史猜测隐藏值，也不会把省略号扩大成版本范围。

支持 Maven 直接依赖、`dependencyManagement`、profile，以及 Gradle Groovy/Kotlin DSL 的直接字符串坐标；Groovy map notation 也支持。Maven 属性只有在值属于白名单且所有引用都专用于目标依赖时才升级。以下声明保持不变：

- 任意表外、目标或更新版本；
- Maven/Gradle 范围、动态版本和 Gradle 插值变量；
- 外部 BOM 管理而没有本地版本的依赖；
- 被项目版本或其他组件共享的 Maven 属性；
- `com.alibaba.fastjson2:fastjson2`、`fastjson2-extension` 以及其他近似 artifact。
- `target`、`build`、`out`、`dist`、`generated` 等生成或安装目录中的构建描述符和源码。

## AUTO：确定性修改

| 不兼容点 | 自动结果 | 依据 |
| --- | --- | --- |
| 表格白名单中的 `com.alibaba:fastjson` | 精确升级到 `2.0.62`，不覆盖表外版本 | 工作簿版本矩阵 |
| `ParserConfig.getDeserializers().put(type, reader)` 在兼容包中删除 | 仅当调用是丢弃旧返回值的独立语句时，改为 `ParserConfig.putDeserializer(type, reader)` | 2.0.62 compatibility `ParserConfig`；新方法返回 `void` |
| `ParserConfig.getDeserializers().get(type)` 在兼容包中删除 | 改为 `ParserConfig.getDeserializer(type)` | 2.0.62 compatibility `ParserConfig` |
| `JSON.writeJSONStringTo(value, writer, features...)` 删除 | 改为 `JSON.writeJSONString(writer, value, features...)`，同时保持参数空白和类型归因 | 1.2.83/2.0.62 API 对照 |

这些 Java 修改依赖类型归因，因此普通 `Map.put/get`、业务类同名方法和已经迁移的调用都不会变化。

## MARK：需要人工决策

配方只在具体调用、枚举常量、注解或 properties entry 上加标记，不用整文件提示代替定位。

| 风险 | 标记位置 | 为什么不自动改 |
| --- | --- | --- |
| `ParserConfig.setAutoTypeSupport`、`addAccept/addDeny` | 精确方法调用 | Fastjson2 移除了旧白名单模型，应设计最小范围 `AutoTypeBeforeHandler` |
| `ParserConfig.setSafeMode` | 精确方法调用 | Fastjson2 safe mode 是进程级启动选择，运行期切换可能抛异常 |
| `ParserConfig/SerializeConfig.getGlobalInstance()` | 精确方法调用 | 全局 provider 的初始化顺序、隔离范围属于业务设计 |
| 残留 `ParserConfig.getDeserializers()` | 精确方法调用 | 新兼容层不暴露可变 map；若旧 `put` 返回值被消费，改为 `void` 会改变语义，必须重构 |
| `SerializerFeature.DisableCircularReferenceDetect` | 精确枚举常量 | `JSONWriter.Feature.ReferenceDetection` 语义相反，且 Fastjson2 默认关闭引用检测 |
| `UseISO8601DateFormat`、`WriteDateUseDateFormat` | 精确枚举常量 | 新 API 应显式选择 `iso8601`、`millis` 或业务格式 |
| `SortField/MapSortField` | 精确枚举常量 | `JSONObject` 改为保持插入顺序，外部协议是否要求排序不可推断 |
| `Feature.SupportAutoType/IgnoreAutoType/SafeMode/DisableFieldSmartMatch` | 精确枚举常量 | 安全模型和默认值改变，不能按同名常量迁移 |
| `JSON.toJSONStringWithDateFormat` | 精确方法调用 | 时区、日期默认值及生产协议需要样例比对 |
| `@JSONField/@JSONType` 的 format/custom serializer/features | 精确注解 | 扩展接口和 feature 类型已重构 |
| `fastjson.parser.autoTypeSupport/autoTypeAccept/deny/deny.internal/safeMode` | 精确 properties entry | 部分键不再消费，部分键语义或生命周期改变 |

建议至少用生产 JSON 做双写对比，覆盖循环引用、AutoType、大小写/下划线智能匹配、空值、日期时区、超大整数、泛型、JSONPath 和非法输入。

## 显式迁移到原生 Fastjson2 API

确认可以承担更大源码迁移后，可显式选择：

```text
com.huawei.clouds.openrewrite.fastjson.MigrateFastjson1ToFastjson2Api
```

它同样遵守十版本白名单，把依赖改为 `com.alibaba.fastjson2:fastjson2:2.0.62`，并只迁移官方可一一对应的核心类型：`JSON`、`JSONObject`、`JSONArray`、`TypeReference`、`JSONException`、`JSONField`、`JSONType`。它不再使用原先危险的递归 `ChangePackage`：`ParserConfig`、`SerializeConfig`、`ObjectSerializer/ObjectDeserializer`、Reader/Writer feature 和 Spring 扩展没有同包同名替代物，仍由 MARK 引导人工迁移。

若使用 Spring、Redis 或 Kotlin，还要按运行时选择 `fastjson2-extension-spring5`、`fastjson2-extension-spring6`、`fastjson2-kotlin` 等模块；配方不会猜测框架代际并自动添加。

## 固定提交的真实用例

测试不是凭空构造，缩减自以下固定提交：

- [crossoverJie/cim 的 1.2.83 Maven 声明](https://github.com/crossoverJie/cim/blob/8863d9f6d76d0ad55a27bd0d6f05d6476937f0e8/pom.xml#L165-L169)；
- [alibaba/otter 的 `getDeserializers().put` 与 AutoType allow-list](https://github.com/alibaba/otter/blob/7544d0515e832b188736cc6d882d5a7da0558a55/shared/common/src/main/java/com/alibaba/otter/shared/common/utils/JsonUtils.java#L62-L72)；
- [alipay/SoloPi 的 `writeJSONStringTo`](https://github.com/alipay/SoloPi/blob/35a4a3e3fe02deeb89df35c82dc3ba03a33f4f13/src/app/src/main/java/com/alipay/hulu/activity/CaseReplayResultActivity.java)；
- [eishay/jvm-serializers 的 `DisableCircularReferenceDetect`](https://github.com/eishay/jvm-serializers/blob/3f7e4bc94e40f2c8c89acd3d2e51454867cec596/tpc/src/serializers/json/FastJSONDatabind.java#L50-L70)；
- [jfinal/jfinal 的 safe mode 与日期格式调用](https://github.com/jfinal/jfinal/blob/a0e9e8b99dc793bcf0cd40ca7feba005ba0c5349/src/main/java/com/jfinal/json/FastJson.java#L34-L70)。

行为映射以固定提交的 [Fastjson 1.x Upgrade Guide](https://github.com/alibaba/fastjson2/blob/67871d7a0c36d2493d5594b57a717cce6d1f6787/docs/fastjson_1_upgrade_en.md) 为依据；依赖测试组织参考 OpenRewrite 固定提交的 [UpgradeDependencyVersionTest](https://github.com/openrewrite/rewrite-java-dependencies/blob/1a76737ac25a7999fc4a758fc709918768a16732/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)。当前 50 项测试覆盖全部十个白名单版本、表外/目标/更新/范围负例、Maven 属性隔离、dependency management、Gradle 两种 DSL、原生坐标迁移、AUTO、MARK、类型归因、生成目录、返回值语义和幂等性。

## 使用与验证

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-fastjson-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.fastjson.MigrateFastjsonTo2_0_62
```

模块验证：

```bash
mvn -pl rewrite-fastjson-upgrade -am clean verify
```
