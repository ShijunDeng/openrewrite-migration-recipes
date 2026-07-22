# openrewrite-migration-recipes

[![CI](https://github.com/ShijunDeng/openrewrite-migration-recipes/actions/workflows/ci.yml/badge.svg)](https://github.com/ShijunDeng/openrewrite-migration-recipes/actions/workflows/ci.yml)

基于 OpenRewrite 的软件迁移配方集合。工程采用 Maven 多模块结构，统一管理依赖、插件版本与 CI，同时让每种迁移保持独立发布、独立测试和按需引入。

## Modules

| Module | Recipe | Description |
| --- | --- | --- |
| [`rewrite-fastjson-to-jackson`](rewrite-fastjson-to-jackson) | `com.huawei.clouds.openrewrite.fastjson.MigrateFastjsonToJackson` | 将 Fastjson 1.x Java 工程迁移到 Jackson 2.x |

后续迁移应新增独立模块，例如：

```text
openrewrite-migration-recipes/
├── pom.xml
├── rewrite-fastjson-to-jackson/
├── rewrite-foo-to-bar/
└── rewrite-legacy-framework-to-modern-framework/
```

所有模块继承统一坐标：

```text
com.huawei.clouds.openrewrite:<module-name>:1.0.0-SNAPSHOT
```

## Build

要求 JDK 17+ 和 Maven 3.8+：

```bash
mvn clean verify
```

只构建指定迁移模块：

```bash
mvn -pl rewrite-fastjson-to-jackson -am clean verify
```

具体配方能力与使用方法见各模块 README。

## Module conventions

- 一个独立迁移目标对应一个 Maven module。
- Java package 使用 `com.huawei.clouds.openrewrite.<domain>`。
- artifact ID 使用 `rewrite-<source>-to-<target>`。
- 每个模块独立声明公开 recipe，并包含源码、依赖和安全回退测试。
- 通用能力成熟后再抽取到共享模块，避免提前制造不必要的耦合。

## License

Apache License 2.0。
