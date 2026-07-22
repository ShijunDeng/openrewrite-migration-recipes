# rewrite-fastjson-to-jackson

[![CI](https://github.com/ShijunDeng/rewrite-fastjson-to-jackson/actions/workflows/ci.yml/badge.svg)](https://github.com/ShijunDeng/rewrite-fastjson-to-jackson/actions/workflows/ci.yml)

一个面向 Fastjson 1.x 的 OpenRewrite 配方插件，用 Jackson 2.x 自动迁移 Java 源码以及 Maven/Gradle 依赖。

主配方：

```text
io.github.shijundeng.rewrite.fastjson.MigrateFastjsonToJackson
```

## 能迁移什么

配方目前覆盖最常见的业务代码模式：

| Fastjson 1.x | 迁移结果 |
| --- | --- |
| `JSON.toJSONString(value)` | `JacksonJson.toJson(value)` |
| `JSON.toJSONBytes(value)` | `JacksonJson.toJsonBytes(value)` |
| `JSON.parseObject(json, User.class)` | `JacksonJson.fromJson(json, User.class)` |
| `JSON.parseObject(json, new TypeReference<T>() {})` | Jackson `TypeReference` + `JacksonJson.fromJson` |
| `JSON.parseArray(json, User.class)` | `JacksonJson.fromJsonList(json, User.class)` |
| `JSON.parseObject(json)` / `JSON.parseArray(json)` | `ObjectNode` / `ArrayNode` |
| `JSONObject` / `JSONArray` | Jackson `ObjectNode` / `ArrayNode` |
| 常用 `get*`、`put`、`add`、`remove`、`toJavaObject`、`toJavaList` | 等价的 `JacksonJson` facade 调用 |
| `@JSONField(name/format/serialize/deserialize)` | `@JsonProperty` / `@JsonFormat` |
| `com.alibaba:fastjson` | `com.fasterxml.jackson.core:jackson-databind:2.22.x` |

Jackson 的读写 API会抛出 checked exception，而 Fastjson 的调用通常不需要捕获异常。配方会在每个使用到迁移 API 的模块中生成：

```text
src/main/java/io/github/shijundeng/fastjson/JacksonJson.java
```

这个 facade 使用单例 `ObjectMapper`，并把 Jackson 异常包装为 `IllegalArgumentException`，从而让迁移后的调用点保持简洁且可编译。

## 安全行为

插件不会盲目删除 Fastjson。当源码仍存在未覆盖的 Fastjson 类型时，`com.alibaba:fastjson` 依赖会保留，便于继续人工迁移；只有类型扫描确认不再使用 Fastjson 后才会删除依赖。

下列高级能力暂不做自动语义映射：

- `SerializerFeature`、`Feature`、Filter、自定义序列化器/反序列化器
- `JSONPath`、AutoType、ParserConfig、SerializeConfig
- `@JSONType` 的高级选项
- Fastjson 2.x（`com.alibaba.fastjson2`）

运行后应执行项目完整测试，并搜索剩余引用：

```bash
rg 'com\.alibaba\.fastjson|SerializerFeature|JSONPath' src
```

## 使用方式

### 1. 安装配方到本机 Maven 仓库

```bash
git clone https://github.com/ShijunDeng/rewrite-fastjson-to-jackson.git
cd rewrite-fastjson-to-jackson
mvn clean install
```

### 2. 对 Maven 项目运行

在待迁移项目根目录执行：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:run \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-fastjson-to-jackson:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=io.github.shijundeng.rewrite.fastjson.MigrateFastjsonToJackson
```

也可以先使用 `dryRun` 查看 patch：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-fastjson-to-jackson:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=io.github.shijundeng.rewrite.fastjson.MigrateFastjsonToJackson
```

### 3. 对 Gradle 项目运行

在 `build.gradle` 中配置 OpenRewrite 插件，并确保 `mavenLocal()` 可用：

```groovy
plugins {
    id 'java'
    id 'org.openrewrite.rewrite' version 'latest.release'
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    rewrite 'com.huawei.clouds.openrewrite:rewrite-fastjson-to-jackson:1.0.0-SNAPSHOT'
}

rewrite {
    activeRecipe 'io.github.shijundeng.rewrite.fastjson.MigrateFastjsonToJackson'
}
```

然后运行：

```bash
./gradlew rewriteDryRun
./gradlew rewriteRun
```

## 示例

迁移前：

```java
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

JSONObject node = JSON.parseObject(json);
String name = node.getString("name");
String output = JSON.toJSONString(node);
```

迁移后：

```java
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.shijundeng.fastjson.JacksonJson;

ObjectNode node = JacksonJson.readObject(json);
String name = JacksonJson.getString(node, "name");
String output = JacksonJson.toJson(node);
```

## 开发与验证

要求 JDK 17+ 和 Maven 3.8+：

```bash
mvn clean verify
```

测试覆盖静态 JSON API、`JSONObject`/`JSONArray` 操作、`@JSONField`、facade 生成，以及 Maven 依赖的新增和安全删除。

## License

Apache License 2.0。
