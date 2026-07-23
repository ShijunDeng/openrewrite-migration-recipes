# MongoDB BSON 5.4.0 升级配方

本模块把工作簿中明确列出的 `org.mongodb:bson` 依赖升级到 `5.4.0`，并把能够保持语义的 Java API 修改真正自动化。必须依据存量 BSON 字节、Extended JSON 契约、资源所有权或部署环境才能决定的事项，不做猜测式改写，而是在准确的 AST/构建声明上留下 OpenRewrite 搜索标记。

## 工作簿范围

| 工作簿序号 | Excel 物理行 | 坐标 | 源版本 | 目标版本 |
|---:|---:|---|---|---|
| 704 | 705 | `org.mongodb:bson` | `3.12.14` | `5.4.0` |
| 1926 | 1927 | `org.mongodb:bson` | `4.7.2` | `5.4.0` |

严格配方只接受源集合 `{3.12.14, 4.7.2}`。其他固定版本、范围、动态版本、BOM/平台托管版本、共享属性、classifier 和非 JAR 变体不会被静默修改。

## 配方

- `com.huawei.clouds.openrewrite.bson.UpgradeBsonTo5_4_0`：只做工作簿严格版本升级。
- `com.huawei.clouds.openrewrite.bson.MigrateBsonTo5_4_0`：推荐配方，按“严格版本升级 → 确定性 Java 自动迁移 → 构建风险标记 → 源码风险标记”执行。

Maven 中可通过 OpenRewrite Maven 插件激活推荐配方：

```bash
mvn rewrite:run \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.bson.MigrateBsonTo5_4_0
```

## 自动处理（AUTO）

| 不兼容点 | 自动操作 | 保持的语义 |
|---|---|---|
| 工作簿选中的 BSON 版本 | Maven 项目/当前 profile 的直接依赖和 dependencyManagement；根 Gradle `dependencies` 的 Groovy/Kotlin 字符串与 Groovy map 精确升级为 `5.4.0` | 只改当前声明的确切所有者；独占 Maven 属性才改属性值 |
| `ObjectId.getTimeSecond()` 移除 | 改为 `getTimestamp()` | 秒级时间值不变 |
| `ObjectId.toStringMongod()` 移除 | 改为 `toHexString()` | 24 位十六进制 ObjectId 表示不变 |
| `ObjectId.getTime()` 移除 | 改为 `getDate().getTime()` | 毫秒值不变，接收者只求值一次 |
| `org.bson.codecs.record.annotations` 移除 | `BsonId`、`BsonProperty`、`BsonRepresentation` 改到 `org.bson.codecs.pojo.annotations` | 注解含义和参数不变 |
| 六个 `JsonWriterSettings` 构造器移除 | 改为等价的 `builder()` 链 | 保留 mode、indent、缩进字符、换行字符、参数顺序和求值次数；无参构造显式保留旧的 `STRICT`，不会落入 builder 的 `RELAXED` 默认值 |

自动配方跳过 `target`、`build/generated`、缓存、报告、安装目录等生成内容；同名业务方法、嵌套 Gradle 项目和错误坐标不会误改。

## 精确标记、需要业务证据（MARK）

| 不兼容点 | 配方标记位置 | 迁移决定 |
|---|---|---|
| 无参 `UuidCodec` 默认表示由 `JAVA_LEGACY` 变为 `UNSPECIFIED`，因而不能编码 UUID | `new UuidCodec()` | 根据已有数据的 subtype/字节序显式选 `UuidRepresentation`，同时回归新旧数据；已显式传入表示的构造器不标记 |
| 3.x `toJson()` 默认 strict Extended JSON，4.x 起默认 relaxed | `Document`、`BsonDocument`、`RawBsonDocument` 的无参 `toJson()` | API、日志、快照或下游解析器依赖类型包装时显式传 `JsonWriterSettings` |
| ObjectId Java 序列化格式在 4.2 改变 | `ObjectOutputStream.writeObject(ObjectId)` 和 `(ObjectId) readObject()` | 版本化、清空或重建缓存/消息载荷，验证滚动升级；旧载荷可能触发 `InvalidClassException` |
| ObjectId 机器/进程/计数器构造和访问 API 移除 | 旧构造器和 legacy component 方法 | 决定是否只保留原始 12 字节身份；不要凭空重建主机/进程语义 |
| BSON 5 中 `BsonDecimal128.isNumber()` 变为 `true`，`asNumber()` 返回 `BsonNumber` | 两个数值分派调用 | 检查 visitor 分支、强制转换、相等性、JSON 和 schema 行为 |
| 公共 `MapCodec`、`IterableCodec` 移除 | import、构造和类型使用 | 用 `MapCodecProvider`、`CollectionCodecProvider` 或 `IterableCodecProvider` 注册到 `CodecRegistry`，验证嵌套泛型与 UUID |
| `Parameterizable` 移除 | import 和实现类 | 将参数化类型选择迁入 `CodecProvider.get()`，保留 `TypeWithTypeParameters` 信息 |
| `BsonReader.mark()/reset()` 移除 | 精确方法调用 | 持有 `reader.getMark()` 返回的那个 `BsonReaderMark` 并重置它，检查嵌套标记和异常路径 |
| `BsonWriter.flush()` 移除 | 精确方法调用 | 根据底层 stream/buffer 的所有权决定 flush 或 close |
| `BSON` hooks、`org.bson.io.Bits`、`org.bson.util.*` 不再可访问 | import 和精确调用 | 换用公共 Codec/BsonReader/BsonWriter/JDK API，并验证字节序与转换语义 |
| 依赖版本所有者不明确或版本不在工作簿 | Maven/Gradle 版本声明 | 找到 catalog、BOM、platform、父 POM 或共享属性的真实所有者再迁移 |
| MongoDB artifact family 版本偏斜 | `mongodb-driver-*`、`bson-record-codec`、`bson-kotlinx*` 声明 | 使用 5.4 BOM 对齐完整家族，排除重复 Codec/UUID/record-codec 类 |
| 3.x uber JAR | `mongo-java-driver`、`mongodb-driver` 声明 | 改成具体的 `mongodb-driver-sync` 或 `mongodb-driver-legacy` 并整体对齐 |
| shading、OSGi、JPMS、native-image 集成 | Maven shade/bnd/native 插件和 Gradle `shadowJar` 的 `org.bson` relocation | 保留包、service、record codec、反射元数据、模块身份和 codec discovery |

## 分路径兼容性

### `3.12.14 → 5.4.0`

该路径跨过 4.0、4.2、4.8、5.0 和 5.2。除上述删除 API 外，必须特别验证 UUID 表示、无参 `toJson()` 的 relaxed 输出和 ObjectId Java 序列化边界。4.0 已移除 3.12 中标记 deprecated 的 API。

### `4.7.2 → 5.4.0`

UUID/JSON 默认值和 ObjectId 序列化变化在 4.7.2 之前已经发生，但若应用仍未显式固定这些契约，标记仍能暴露隐含依赖。本路径主要跨过 4.8、5.0 和 5.2：公共容器 codec、`Parameterizable`、reader/writer 方法、record 注解旧包以及 Decimal128 数值分派需要处理。

目标 5.4 已不支持 MongoDB Server 3.6 及更早版本，服务端基线至少应为 4.0。OSGi 中使用 records 时，从 4.8 起需要显式提供 `bson-record-codec` 并检查 bundle 可见性。

## 验收清单

- 对代表性文档做 BSON bytes 和 UUID subtype/字节序 golden test，同时覆盖 3.x 写入的历史数据。
- 对 REST、日志、快照及消息中的 Extended JSON 做契约对比，显式选择 strict/relaxed/shell。
- 清点 ObjectId Java serialization 的缓存、队列和持久化载荷，演练滚动升级、回滚和旧载荷处理。
- 用嵌套 `Map`/collection、records、泛型 codec、Decimal128、UUID 组合用例验证 `CodecRegistry`。
- 通过 dependency tree/lockfile 确认所有 MongoDB JVM artifacts 与 5.4 BOM 收敛，且没有旧 uber JAR 和重复类。
- 在 MongoDB Server 4.0+ 做真实读写、聚合和 round-trip 测试。
- 对 OSGi、JPMS、shaded JAR、native-image 分别验证 service、反射配置、module/bundle 名称和 `org.bson` 可见性。
- 对 reader mark/reset 和 writer flush 的替换执行异常路径、嵌套状态和资源所有权测试。

## 测试证据

`mvn -f rewrite-bson-upgrade/pom.xml clean verify` 当前执行 **165** 项测试（0 failure / 0 error / 0 skip）。测试不仅验证版本字符串，还覆盖 Maven root/profile/dependencyManagement/属性所有权，Gradle Groovy/Kotlin/map，标准主制品与变体的伴随项隔离、动态模板、嵌套项目、生成目录，全部 AUTO 改写，全部 MARK 信息，误匹配 NOOP，以及两轮幂等。

真实代码模式包括：

- OpenCGA 在 `VariantMongoDBQueryParser` 中的 `new JsonWriterSettings(JsonMode.SHELL, false)`：
  [`opencb/opencga@eaa4ff8`](https://github.com/opencb/opencga/blob/eaa4ff8859e7d0e0303acc4a94eb3daf5332e28e/opencga-storage/opencga-storage-mongodb/src/main/java/org/opencb/opencga/storage/mongodb/variant/adaptors/VariantMongoDBQueryParser.java)
- uMongo 的 `ObjectId.getTimeSecond()`：
  [`agirbal/umongo@44dfa90`](https://github.com/agirbal/umongo/blob/44dfa90603f41d3aac4b1c8e942d5fa93dc6b891/src/com/edgytech/umongo/EditObjectIdDialog.java)
- emfjson-mongo 的旧 `JsonWriterSettings` 构造模式：
  [`emfjson/emfjson-mongo@c1a348e`](https://github.com/emfjson/emfjson-mongo/blob/c1a348ed832e4a0928ff96bdeb41ad04a68579c1/src/main/java/org/emfjson/mongo/bson/codecs/JsonWriter.java)

OpenRewrite 依赖升级测试结构参考固定提交
[`rewrite-java-dependencies@decb8db`](https://github.com/openrewrite/rewrite-java-dependencies/blob/decb8dbb2b5b726f8815efc51c85c34a60268bb0/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)，避免跟随上游分支漂移。

## 官方依据（固定版本）

- MongoDB Java Driver 源码：
  [`r3.12.14@9118eb8`](https://github.com/mongodb/mongo-java-driver/tree/9118eb8a8d6688001c9822ed5509e2e3000a2579)、
  [`r4.7.2@a75852e`](https://github.com/mongodb/mongo-java-driver/tree/a75852edca37f60e044822a0d82dbe140fc3f47f)、
  [`r5.4.0@28e51aa`](https://github.com/mongodb/mongo-java-driver/tree/28e51aa5e7550b0ec9e082205e1232704a70a6c2)
- [MongoDB Java Sync Driver upgrade guide](https://www.mongodb.com/docs/drivers/java/sync/current/reference/upgrade/)
- [MongoDB Java Driver 3.12 upgrade guide](https://mongodb.github.io/mongo-java-driver/3.12/upgrading/)
- [MongoDB BSON data format guide](https://www.mongodb.com/docs/drivers/java/sync/current/data-formats/document-data-format-bson/)
