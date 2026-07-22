# JAXB Runtime 2.3.x 升级到 4.0.8

本模块对应表格中的 `org.glassfish.jaxb:jaxb-runtime`，处理 `2.3.7`、`2.3.8` 到 `4.0.8` 的迁移。它不是普通补丁升级：JAXB Runtime 2.3.x 实现的是仍使用 `javax.xml.bind.*` 的 JAXB 2 API，而 4.0.8 实现 Jakarta XML Binding 4，应用、生成代码、Activation、XJC 配置和 JPMS 模块必须落在同一套 Jakarta 命名空间。

推荐先执行完整配方并审查 dry-run：

```text
com.huawei.clouds.openrewrite.jaxbruntime.MigrateJaxbRuntimeTo4_0_8
```

如果工程已完成 Jakarta 迁移，只希望更新精确 runtime 坐标，可用低风险配方：

```text
com.huawei.clouds.openrewrite.jaxbruntime.UpgradeJaxbRuntimeTo4_0_8
```

也可以单独迁移 XJC binding 文件：

```text
com.huawei.clouds.openrewrite.jaxbruntime.MigrateJaxbBindingsToJakarta3
```

## 自动处理范围

依赖窄配方只匹配 Maven/Gradle 的精确坐标 `org.glassfish.jaxb:jaxb-runtime` 并更新到 `4.0.8`，支持 Maven 直接版本、版本属性、`dependencyManagement`，以及 Gradle 字符串、Map 和本地版本变量。它保留 scope、optional 等声明，不修改 `jaxb-core`、`jaxb-xjc`、`com.sun.xml.bind:jaxb-impl` 等相似坐标，也不会把 4.0.9 及后续版本降级。

未写版本、由外部 parent/BOM 管理的依赖不会被强行加版本。此时应升级拥有版本的 parent/BOM，或者在本项目自己的 `dependencyManagement` 显式覆盖；这能避免配方悄悄破坏平台对整套 Jakarta EE 依赖的对齐。

完整配方额外执行以下高置信度修改：

- `javax.xml.bind:jaxb-api` → `jakarta.xml.bind:jakarta.xml.bind-api:4.0.5`，并把已有的 2.x/3.x Jakarta API 坐标更新到 4.0.5；
- `javax.activation:javax.activation-api` → `jakarta.activation:jakarta.activation-api:2.1.4`，并更新已有 Jakarta Activation API；
- 递归迁移 Java 的 `javax.xml.bind.*` → `jakarta.xml.bind.*` 与 `javax.activation.*` → `jakarta.activation.*`；
- RI 扩展 `com.sun.xml.bind.marshaller.NamespacePrefixMapper` → `org.glassfish.jaxb.runtime.marshaller.NamespacePrefixMapper`；
- 更新 2.x 与 4.x 中有一一对应关系的 RI Marshaller 属性：`namespacePrefixMapper`、`indentString`、`characterEscapeHandler`、`xmlDeclaration`、`xmlHeaders`、`objectIdentitityCycleDetection` 的前缀从 `com.sun.xml.bind` 改为 `org.glassfish.jaxb`；
- 在 `*.xjb`、`*.jxb`、`*.xsd` 中把标准 binding namespace 改为 `https://jakarta.ee/xml/ns/jaxb`，把规范 binding schema 改为 `bindingschema_3_0.xsd`，并把常见 `jxb`/`jaxb` 前缀或默认 namespace 的 binding language version 改为 `3.0`。

注意：Jakarta XML Binding API 已到 4.0，但 XJC binding language 的当前版本仍是 `3.0`，不能机械写成 `4.0`。RI 的 XJC 厂商扩展 namespace `http://java.sun.com/xml/ns/jaxb/xjc` 在 4.x 仍然有效，本配方特意不修改它。

## 4.0.8 官方依赖基线

`jaxb-runtime:4.0.8` 的发布 tag 是 [`4.0.8-RI`](https://github.com/eclipse-ee4j/jaxb-ri/tree/4.0.8-RI)，发布提交为 [`8ff4b1e`](https://github.com/eclipse-ee4j/jaxb-ri/commit/8ff4b1e53cd918630c61cb8565db365809683a8f)。其 [runtime POM](https://repo1.maven.org/maven2/org/glassfish/jaxb/jaxb-runtime/4.0.8/jaxb-runtime-4.0.8.pom) 依赖 `jaxb-core:4.0.8`；官方 [4.0.8 BOM](https://repo1.maven.org/maven2/org/glassfish/jaxb/jaxb-bom/4.0.8/jaxb-bom-4.0.8.pom) 对齐以下版本：

| 组件 | 4.0.8 BOM 版本 | 用途 |
| --- | --- | --- |
| `jakarta.xml.bind:jakarta.xml.bind-api` | 4.0.5 | 标准 API 与注解 |
| `jakarta.activation:jakarta.activation-api` | 2.1.4 | `DataHandler`、`DataSource`、MIME API |
| `org.eclipse.angus:angus-activation` | 2.0.3 | Activation SPI 实现 |
| `org.glassfish.jaxb:jaxb-core` | 4.0.8 | JAXB Runtime 核心 |
| `org.jvnet.staxex:stax-ex` | 2.1.0，可选 | StAX 扩展 |
| `com.sun.xml.fastinfoset:FastInfoset` | 2.1.1，可选 | Fast Infoset |

不要继续显式锁定 2.3.x 的 `txw2`、`jaxb-core`、`com.sun.activation:jakarta.activation:1.2.2` 或旧 API；混合 classpath 很容易产生 `ClassNotFoundException`、provider 加载失败或看似同名却不能赋值的 `javax`/`jakarta` 类型。

## 不兼容修改点

| 变化 | 影响与迁移建议 |
| --- | --- |
| Java 包从 `javax.xml.bind.*` 改为 `jakarta.xml.bind.*` | 源码和二进制均不兼容；所有直接依赖、生成代码、反射类名、序列化 adapter、测试 fixture 和框架集成必须一起重编译 |
| Activation 包从 `javax.activation.*` 改为 `jakarta.activation.*` | `DataHandler`/`DataSource` 出现在 JAXB attachment API、MTOM、邮件和内容类型处理中；不能混用两种包的同名接口 |
| JAXB RI 4 要求 Java 11+ | 编译、运行、CI、容器镜像、Maven/Gradle toolchain 和执行 XJC 的 JVM都至少使用 JDK 11；如果应用平台要求更高版本，以平台要求为准 |
| JAXB 4 不支持直接运行 JAXB 1/2 应用 | 官方 FAQ 要求替换包、用新 XJC 重生成 schema classes，并重新编译应用；只升级 JAR 不可行 |
| JAXB 4 删除 JAXB 1 兼容和已废弃 `Validator` | 改用 XML Schema validation：`SchemaFactory` 创建 `Schema` 后传给 `Marshaller`/`Unmarshaller#setSchema`；业务校验独立处理 |
| provider discovery 发生变化 | 4.0 删除通过 `jaxb.properties` 和旧 `META-INF/services/...JAXBContext` 文件发现 provider 的方式；优先依赖标准 `ServiceLoader`/module `provides`，自定义 provider 按 4.0 `JAXBContextFactory` 约定注册并做隔离 classloader 测试 |
| 废弃的 `javax.xml.bind.context.factory` 属性被删除 | 不要只替换字符串前缀；移除旧属性并按 4.0 provider lookup 机制配置，OSGi、应用服务器、插件式 classloader 尤其要验证 |
| `DatatypeConverter` 对非法输入的处理更严格 | 4.0 的默认实现会对无效 lexical value 抛异常；为非法日期、数字、Base64、QName 增加负向用例，不要依赖旧版容错 |
| RI 内部包从 `com.sun.xml.bind.*` 重组到 `org.glassfish.jaxb.*` | 本配方只迁移有官方直接替代的 `NamespacePrefixMapper` 与稳定属性键；其他 internal API 没有兼容承诺，应改用标准 JAXB SPI/API 或逐项人工迁移 |
| RI Marshaller 属性前缀变化 | 已自动处理六个 2.3.8/4.0.8 都存在的同义键；常量拼接、配置文件、脚本或数据库中的字符串不自动改，必须搜索 `com.sun.xml.bind` |
| binding namespace 从 Sun URI 改为 Jakarta URI | 标准 URI 改为 `https://jakarta.ee/xml/ns/jaxb`、version 为 `3.0`；但 XJC vendor extension URI 仍保留旧 Sun URI，不能全局替换所有包含 `/jaxb` 的地址 |
| schema 重新生成会改变 Java 源码 | 类/属性命名、`JAXBElement` 包装、episode、注解、`ObjectFactory`、`package-info.java`、集合与可空语义均需 diff；先清理旧 generated-sources，避免两代类同时编译 |
| XML 输出不保证字节级稳定 | namespace prefix、属性/namespace 声明顺序、空元素、`xsi:nil`、默认值、字符转义和 XML declaration 可能变化；比较 XML infoset/规范化结果，并对签名 XML 重新评估 canonicalization |
| `JAXBContext` 可复用而 Marshaller/Unmarshaller 不保证线程安全 | RI FAQ 明确 `JAXBContext` 可跨线程共享，Marshaller、Unmarshaller 不应共享；检查对象池、ThreadLocal、listener、adapter 和 schema 的并发使用 |

## Java 与 JPMS

JAXB 从 JDK 11 起不再由 JDK 自带，不能依赖 `--add-modules java.xml.bind`。4.0.8 的实际 module descriptor 为：

```java
module com.example.orders {
    requires jakarta.xml.bind;
    // 仅在直接使用 RI 扩展时需要：
    requires org.glassfish.jaxb.runtime;
    // 直接使用 DataHandler/DataSource 时需要：
    requires jakarta.activation;
}
```

相关模块名是 `jakarta.xml.bind`、`org.glassfish.jaxb.runtime`、`org.glassfish.jaxb.core`、`jakarta.activation`、`org.eclipse.angus.activation`。旧工程可能写 `requires java.xml.bind`、`requires java.activation` 或 automatic-module 名称；这些声明的修复取决于 API 是否直接使用以及运行在 classpath 还是 module-path，本配方不猜测 `module-info.java`，需人工更新后运行 `jdeps --check` 和真实 module-path 启动测试。

如果有强封装错误，不要默认给整个应用加宽泛 `--add-opens`。先确认是否使用了 RI internal package；仅对不可替换的框架集成添加最小范围 opens，并记录目标 module。

## Activation 迁移

JAXB Runtime 4.0.8 通过 `jaxb-core` 传递引入 Jakarta Activation API 与 Angus 实现。普通 classpath 应用通常不需要再次声明实现；应用服务器/Jakarta EE 容器可能已经提供 API/SPI，应遵循平台 BOM 和 classloader 规则，避免把另一份 API 打进部署包。

人工检查以下场景：

- `DataHandler`、`DataSource`、`MimeType`、`CommandMap` 的 import 和公开方法签名；
- `@XmlMimeType`、`@XmlAttachmentRef`、MTOM/XOP、SOAP/JAX-WS 集成是否也已迁到 Jakarta；
- `META-INF/mailcap`、`META-INF/mime.types`、自定义 Activation SPI provider 是否仍可发现；
- shaded/uber JAR 是否合并了 service 文件，应用服务器是否出现 API 由 parent classloader、实现由 child classloader 加载的分裂。

## XJC、Maven 插件与 schema generation

完整配方会迁移标准 `xjb`/`jxb`/内联 `xsd` binding 声明，但不会自动选择或升级生成插件。原因是生态中至少存在 `org.codehaus.mojo:jaxb2-maven-plugin`、Highsource/Evolved Binary 插件、Ant task、直接调用 `jaxb-xjc`，不同主版本的 goal、参数、episode 和 extension 插件兼容性并不相同。

迁移步骤建议：

1. 选择明确使用 JAXB 4 的插件版本。例如当前 MojoHaus `jaxb2-maven-plugin` 4.x 以 Java 11/JAXB 4 为基线；其他插件按其官方兼容矩阵选择，不能只改插件版本号。
2. 对齐 tooling artifacts：`com.sun.xml.bind:jaxb-xjc:4.0.8` 与 `com.sun.xml.bind:jaxb-jxc:4.0.8`。这里 group 仍可能是 `com.sun.xml.bind`，不要因 Java 包迁移而盲目改 Maven group。
3. 清空旧的 `target/generated-sources`/Gradle generated 目录，重新执行 XJC；确认新代码 import `jakarta.xml.bind.*`。
4. 检查 `*.episode`、catalog、binding include、schemaLocation、XJC extension plugin、`-extension`、`-X...`、`-m`、package 参数和 annotation-processing 配置。
5. 用 schemagen 从 Jakarta 注解的源码重新生成 XSD，diff namespace、element form、nillable/default、choice、枚举和 adapter 映射。
6. 如果 WSDL/JAX-WS 工具间接调用 XJC，还需同时升级 Jakarta XML Web Services 工具链；本模块不会修改 WSDL、`*.xjc` 或任意扩展名的 XML。

配方只识别 `*.xjb`、`*.jxb` 和 `*.xsd`，版本正则覆盖常见 `jxb`/`jaxb` prefix 与默认 namespace。自定义 prefix、放在 `*.xml`/`*.wsdl` 中的内联 binding、字符串生成的 XML 和非标准 schema URL 必须人工处理。运行后搜索：

```bash
rg -n 'javax\.xml\.bind|javax\.activation|java\.sun\.com/xml/ns/jaxb|com\.sun\.xml\.bind|java\.xml\.bind|java\.activation'
```

不要把结果全部替换；尤其要保留仍由 JAXB RI 4 使用的 `http://java.sun.com/xml/ns/jaxb/xjc` 厂商扩展 URI。

## 序列化与反序列化验证

至少准备以下回归：

- 每个核心 schema 的 representative XML 做 unmarshal → object → marshal，并按 XML namespace-aware/canonical 方式比较；
- `@XmlRootElement` 与 `JAXBElement` 两种 root、`ObjectFactory`、QName、default namespace 和自定义 prefix；
- `@XmlElement(required/nillable/defaultValue)`、choice、列表、枚举未知值、日期时区、BigDecimal scale、Base64 与 Unicode；
- `XmlAdapter`、listener、validation event handler、Schema validation、DOM/StAX/SAX/StreamSource 输入输出；
- `DataHandler`/attachment、MTOM/XOP、大文件流式处理、外部 entity/DTD 安全配置；
- 多 classloader、ServiceLoader、OSGi/JPMS、native image/shading，以及并发复用 `JAXBContext`；
- 保存过 JAXB 对象的 Java serialization、缓存或消息载荷。类名和 `serialVersionUID` 不会因 XML 兼容而自动兼容，不能把 Java native serialization 当作 JAXB XML 兼容性的一部分。

安全上应重新确认 SAX/StAX/SchemaFactory 的外部实体、DTD 和外部 schema 访问限制。升级 JAXB 并不会自动修正应用自己创建 parser 时的 XXE 配置。

## 测试样本来源

- [GIScience/openrouteservice](https://github.com/GIScience/openrouteservice/blob/786d1d119922a0206c837c9b938bdb769453af27/openrouteservice/pom.xml#L313-L318) 的 Maven `2.3.8` 直接依赖；
- [Apache NiFi](https://github.com/apache/nifi/blob/6bdea7e2047110e68dbd099ea74ed8552e6c064d/pom.xml#L128) 的 `jaxb.runtime.version` 与 dependencyManagement；
- [DataHub](https://github.com/datahub-project/datahub/blob/55339d3f80f06a35975b38304d7c1af800a97c71/metadata-service/war/build.gradle#L76) 的 Gradle 字符串依赖；
- [Google Cloud healthcare-data-harmonization](https://github.com/GoogleCloudPlatform/healthcare-data-harmonization/blob/a69ff9619ae665ce475f6206ebc1fb459f69fbc2/wstl1/tools/XmlToJson/src/main/java/com/google/cloud/healthcare/etl/xmltojson/XmlToJsonCDARev2.java) 的 `JAXBContext`、Marshaller RI property 与 `NamespacePrefixMapper`；
- [Eclipse Paho MQTT Spy](https://github.com/eclipse-paho/paho.mqtt-spy/blob/737699afbabaf01520302080a6f8b910f121ab2f/spy-common/src/main/resources/spy-bindings.xjb) 的真实外部 binding 文件。

实现与测试模式参考 OpenRewrite Apache 2 核心的 [ChangePackage](https://github.com/openrewrite/rewrite/blob/main/rewrite-java/src/test/java/org/openrewrite/java/ChangePackageTest.java)、[Gradle ChangeDependency](https://github.com/openrewrite/rewrite/blob/main/rewrite-gradle/src/test/java/org/openrewrite/gradle/ChangeDependencyTest.java)、[FindAndReplace](https://github.com/openrewrite/rewrite/blob/main/rewrite-core/src/test/java/org/openrewrite/text/FindAndReplaceTest.java) 与 [`rewrite-java-dependencies` UpgradeDependencyVersion](https://github.com/openrewrite/rewrite-java-dependencies/blob/main/src/test/java/org/openrewrite/java/dependencies/UpgradeDependencyVersionTest.java)。OpenRewrite 官方也有 [JavaxXmlBindMigrationToJakartaXmlBind](https://docs.openrewrite.org/recipes/java/migrate/jakarta/javaxxmlbindmigrationtojakartaxmlbind) 行为资料；该 recipe pack 使用 Moderne Source Available License，本模块没有复制其代码或引入该依赖，只组合 Apache 2.0 OpenRewrite recipes。

官方规范与实现资料：

- [Jakarta XML Binding 4.0 Specification](https://jakarta.ee/specifications/xml-binding/4.0/jakarta-xml-binding-spec-4.0.html)；
- [JAXB API 4.0.0 release changes](https://github.com/jakartaee/jaxb-api/releases/tag/4.0.0)；
- [Eclipse JAXB RI 4.0.x release documentation](https://eclipse-ee4j.github.io/jaxb-ri/4.0.5/docs/release-documentation.html)，包括 Java 11、JAR/JPMS、XJC、schemagen、thread-safety 与 RI extensions；
- [4.0.8 source tag](https://github.com/eclipse-ee4j/jaxb-ri/tree/4.0.8-RI) 与 [4.0.8 BOM](https://repo1.maven.org/maven2/org/glassfish/jaxb/jaxb-bom/4.0.8/jaxb-bom-4.0.8.pom)。

测试覆盖表格全部版本、Maven 直接/属性/dependencyManagement、Gradle 字符串/Map/变量、scope/optional 保留、目标/后续版本和相似坐标/no-version no-op、JAXB/Activation API 坐标、源码 import/annotations/全限定类型、RI 扩展类型与属性、外部 binding/inline XSD/default namespace、XJC 厂商扩展 URI 保留、无关 XML 防误伤、完整组合与 recipe validation，共 32 个场景。

## 使用与验证

先生成 dry-run patch：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-jaxb-runtime-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.jaxbruntime.MigrateJaxbRuntimeTo4_0_8
```

审查 patch 后，先升级/执行 XJC 与 schemagen，再用 JDK 11+ 进行 `clean` 编译、单元/集成测试和真实运行环境启动。最后检查 Maven `dependency:tree` 或 Gradle `dependencies`，确保不存在 JAXB/Activation 两代 API、旧 RI core/tooling 或重复实现。

本模块自身验证：

```bash
mvn -f rewrite-jaxb-runtime-upgrade/pom.xml clean verify
```
