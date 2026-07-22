# Fastjson upgrade to 2.0.62

本模块对应 `开源软件升级.xlsx` 中的 `com.alibaba:fastjson`，合并处理源版本：

```text
1.2.40、1.2.70、1.2.71、1.2.75、1.2.83、1.2.83_noneautotype、
2.0.12、2.0.13、2.0.14、2.0.16 …（共 25 个版本）
```

目标版本为 `2.0.62`。由于 Fastjson2 同时提供兼容模块和原生新 API，本模块提供两个互斥的配置型配方。

## 配方选择

### 兼容模式（默认）

```text
com.huawei.clouds.openrewrite.fastjson.UpgradeFastjsonTo2_0_62
```

将 Maven/Gradle 中的依赖版本升级为：

```text
com.alibaba:fastjson:2.0.62
```

这是 Fastjson2 官方提供的 1.x 兼容模块，保留 `com.alibaba.fastjson` 包名，适合先降低依赖漏洞风险、再分阶段迁移业务代码。它不是 Fastjson 1.x 的继续演进，也不保证深度使用场景 100% 兼容。

### 原生 Fastjson2 API 模式（显式选择）

```text
com.huawei.clouds.openrewrite.fastjson.MigrateFastjson1ToFastjson2Api
```

该配方自动完成两类机械修改：

- 将 `com.alibaba:fastjson` 依赖替换为 `com.alibaba.fastjson2:fastjson2:2.0.62`；
- 将 Java 源码中 `com.alibaba.fastjson` 及子包引用递归调整为 `com.alibaba.fastjson2`。

包名修改只能处理一一对应的类型。Fastjson2 重构或删除的 API 会在编译时暴露，必须按下表人工迁移。若当前已经使用 `com.alibaba:fastjson:2.x` 兼容模块，也应先确认业务代码不依赖兼容层内部行为，再选择原生模式。

## 不兼容修改点

| Fastjson 1.x 行为/API | Fastjson2 2.0.62 迁移要求 |
| --- | --- |
| AutoType 使用 `ParserConfig` 白名单，旧工程可能默认依赖类型反序列化 | Fastjson2 默认关闭 AutoType；按最小范围启用 `JSONReader.Feature.SupportAutoType` 或配置安全 handler，不要全局放开任意类型 |
| 循环引用默认检测并输出 `$ref` | Fastjson2 默认不检测；需要原语义时启用 `JSONWriter.Feature.ReferenceDetection`，并验证协议消费者 |
| 字段智能匹配默认开启 | Fastjson2 默认关闭；确有兼容需求时启用 `JSONReader.Feature.SupportSmartMatch`，同时检查大小写/下划线误匹配风险 |
| 多项 Reader/Writer feature 默认开启 | Fastjson2 默认 feature 集不同；逐项映射 `Feature`，不要假定相同名称就有相同默认值 |
| `SerializerFeature`、`parser.Feature` | 分别迁移为 `JSONWriter.Feature`、`JSONReader.Feature`；部分枚举被删除或改为格式参数 |
| `ObjectSerializer`/`ObjectDeserializer` | 改为 `ObjectWriter`/`ObjectReader`，注册到对应 provider 或 module |
| `SerializeConfig`/`ParserConfig` | 改为 `ObjectWriterProvider`/`ObjectReaderProvider`；检查全局单例初始化顺序 |
| `SerializerFeature.DisableCircularReferenceDetect` | 新特性 `ReferenceDetection` 语义相反且默认关闭，不能机械同名替换 |
| `SerializerFeature.UseISO8601DateFormat`、`WriteDateUseDateFormat` | 使用 `format="iso8601"` 或明确的日期格式；若要旧版毫秒值，使用 `format="millis"` |
| `SerializerFeature.SortField` | Fastjson2 `JSONObject` 基于 `LinkedHashMap`，通常无需该 feature；仍需验证输出字段顺序是否属于外部协议 |
| Spring MVC、Redis、Messaging 等 `support.*` 集成 | 原生 API 模式需额外选择 `fastjson2-extension-spring5` 或 `fastjson2-extension-spring6` 等扩展，并重新注册 converter/serializer |
| JSONPath、日期、数字精度、Bean 属性发现和异常类型 | 实现与边界行为均可能变化；使用生产样例做双写对比，并覆盖空值、泛型、超大整数、时区和非法输入 |
| `1.2.83_noneautotype` 的安全约束 | 不能因升级而恢复宽松 AutoType；将禁用策略转化为 Fastjson2 明确的 reader 配置和安全测试 |

官方资料：[Fastjson 1.x upgrade guide](https://github.com/alibaba/fastjson2/blob/main/docs/fastjson_1_upgrade_en.md) 和 [Fastjson2 README](https://github.com/alibaba/fastjson2#12-fastjson-v1-compatibility-module)。

## 使用方式

兼容模式：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-fastjson-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.fastjson.UpgradeFastjsonTo2_0_62
```

若已决定迁移到原生 Fastjson2 API，将 active recipe 改为：

```text
com.huawei.clouds.openrewrite.fastjson.MigrateFastjson1ToFastjson2Api
```

确认 patch 后执行 `run`，再进行完整编译、安全测试、序列化结果对比和依赖树审计。若长期目标是移除 Fastjson，本仓库另有 `rewrite-fastjson-to-jackson` 模块，不应与本模块在同一次运行中同时激活。

## 模块验证

```bash
mvn -pl rewrite-fastjson-upgrade -am clean verify
```
