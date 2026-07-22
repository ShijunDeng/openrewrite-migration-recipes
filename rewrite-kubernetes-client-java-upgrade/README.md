# Fabric8 Kubernetes Client 5.12 升级到 7.3.1

本模块严格处理《开源软件升级.xlsx》中的坐标 `io.fabric8:kubernetes-client`：

| 表格旧版本 | 目标版本 |
| --- | --- |
| `5.12.0` | `7.3.1` |
| `5.12.4` | `7.3.1` |

依赖窄配方：

```text
com.huawei.clouds.openrewrite.kubernetesclientjava.UpgradeKubernetesClientJavaDependencyTo7_3_1
```

完整迁移配方：

```text
com.huawei.clouds.openrewrite.kubernetesclientjava.MigrateKubernetesClientJavaTo7_3_1
```

实现依据固定到 Fabric8 `v7.3.1` 的 commit [`40e5b2d12646ced86dfeb256be236bbe6b6b6cfe`](https://github.com/fabric8io/kubernetes-client/tree/40e5b2d12646ced86dfeb256be236bbe6b6b6cfe)。跨代事实以同一提交中的官方 [Migration to 6.x](https://github.com/fabric8io/kubernetes-client/blob/40e5b2d12646ced86dfeb256be236bbe6b6b6cfe/doc/MIGRATION-v6.md) 和 [Migration to 7.x](https://github.com/fabric8io/kubernetes-client/blob/40e5b2d12646ced86dfeb256be236bbe6b6b6cfe/doc/MIGRATION-v7.md) 为准。

## 自动修改的范围

### 依赖版本

只把显式属于 `io.fabric8:kubernetes-client` 的 `5.12.0` 或 `5.12.4` 改为 `7.3.1`：

- Maven 直接依赖和本地 `dependencyManagement` 字面量；
- 只被匹配坐标使用的 Maven 属性；
- Gradle Groovy/Kotlin DSL 的直接字符串坐标；
- 保留 scope、optional、classifier、exclusions 和依赖声明的其他结构。

以下情况安全 no-op：

- 无版本依赖由 parent、BOM、Gradle platform 或 version catalog 管理；
- Maven 属性同时被其他坐标或其他位置引用；
- 未解析属性、版本范围、Gradle 插值、Map notation、catalog alias；
- `5.12.1`、`5.12.3`、6.x、已是 `7.3.1`、更高版本；
- `kubernetes-client-api`、`kubernetes-client-bom`、`kubernetes-model*`、HTTP client、mock server、OpenShift/extension 等 companion artifact。

这些边界是刻意的。BOM 或共享属性必须在真正拥有整套 Fabric8 版本的位置升级，不能只把一个模块改成 7.3.1 后留下混版 classpath。

### 确定性 Java API

完整配方会自动应用官方指南中不需要业务判断的一对一迁移：

| 旧写法 | 自动结果 |
| --- | --- |
| `io.fabric8.kubernetes.client.internal.readiness.*` | `io.fabric8.kubernetes.client.readiness.*` |
| `CustomResourceList<T>` | `DefaultKubernetesResourceList<T>` |
| `SharedInformer<T>` | `SharedIndexInformer<T>` |
| `Applicable.apply()` | `createOrReplace()` |
| OpenShift `clusterautoscaling` package | `autoscaling` package |
| OpenShift `machineconfig` package | `machineconfiguration` package |
| `OpenShiftClient.clusterAutoscaling()` | `openShiftAutoscaling()` |
| `io.fabric8.verticalpodautoscaler.api.model` | `io.fabric8.autoscaling.api.model.v1` |
| Fabric8 mock tests中的 OkHttp/Okio 类型 | 官方 7.x `io.fabric8.mockwebserver` 对应类型 |

`new DefaultKubernetesClient()` 和 `new DefaultKubernetesClient(config)` 仅在接收变量的声明类型精确为 `KubernetesClient` 时改成 `KubernetesClientBuilder`。如果声明类型是 `DefaultKubernetesClient` 或 `NamespacedKubernetesClient`，builder 的返回类型和 namespace 语义不能机械保持，配方会留下 `SearchResult`，不会制造编译错误。

mock-server 替换也有源码级前置条件：文件必须实际引用 `okhttp3.mockwebserver`。普通生产代码中的 `okhttp3.Headers` 等类型不会被全局改成 Fabric8 mock 类型。

## 需要人工处理的不兼容点

完整配方对下面各类位置添加精确搜索标记；标记是迁移待办，不等于自动修复。

| 领域 | 5.12 → 7.3.1 的处理要求 |
| --- | --- |
| Java 基线 | Fabric8 7 要求 Java 11；同步调整 compiler/toolchain、CI image、运行时和下游框架基线 |
| HTTP transport | 7.x 默认从 OkHttp 改为 Vert.x；如需继续 OkHttp，显式使用 `kubernetes-httpclient-okhttp` 并排除/校验默认 transport，重新验证 TLS、proxy、interceptor、dispatcher 和连接池 |
| client 创建 | 优先 `KubernetesClientBuilder` 的 `withConfig` / `withHttpClientFactory`；直接 OkHttp constructor、`HttpClientUtils`、`AutoAdaptableKubernetesClient` 已移除或不再是支持路径 |
| Config/kubeconfig | `Config.getKubeconfigFilename()` 改为返回集合的 `getKubeconfigFilenames()`；String/File/Path 消费者必须决定多文件顺序，不能只改方法名 |
| Pod ready | 获取 log/exec 等操作的默认 ready wait 从 5 秒变为 0；需要旧行为时显式 `withReadyWaitTimeout`，并重新验证 init container/未就绪 Pod |
| namespace | `inNamespace` 与 item namespace 的优先级在 6.x 改变；检查 create/load/update/delete 是否写到预期 namespace |
| compatibility interceptor | `kubernetes.backwardsCompatibilityInterceptor.disable` 默认从 `false` 变为 `true`；旧 API fallback 依赖者要明确设置并规划淘汰 |
| model/serialization | Map 默认非 null 且默认值可能不序列化；反序列化要求正确 `apiVersion`；检查 null/default、unknown field、CRD、模板对象、排序及 JSON/YAML golden file |
| resource DSL | `customResource`、`lists`、`deletingExisting` 和多项旧 DSL interface 被移除；`resource(item)`、generic resource、delete/create 或 server-side apply 的选择取决于原业务意图 |
| delete/evict | delete 结果改为 `List<StatusDetails>`，collection delete 请求方式改变，evict 对不存在 Pod 会抛异常；重写 Boolean 分支和 404/partial-success 处理 |
| watch/informer | 只保留 `SharedIndexInformer`；验证 watch 重连、close、resync、indexer、executor、异常 handler 和客户端关闭顺序 |
| exec/log/port-forward | piped stream 支持被移除，部分 read/write API 改成 redirect；验证 backpressure、线程、exit code、listener 和资源释放 |
| adapt/support | `adapt` 不再先执行 `isAdaptable`；根据目的选择 `supports`、`hasApiGroup` 或直接 adapt，并测试 extension 不存在的路径 |
| `IntOrString` | `setIntVal`、`setStrVal`、`setKind` 移除；按值类型使用 constructor 或 builder |
| OpenShift/extensions | model artifact 合并/改名，若干 alpha/beta 类型删除；cert-manager、Istio、Tekton、OCM、OVN、VPA 和 OpenShift model 必须按官方 7.x 表逐一对齐 |
| mock/CRD generator | MockWebServer 不再基于 OkHttp；OpenShift 专用 mock 合并；`@PrinterColumn.format` 从 String 改为 enum，旧 CRD generator API 要迁移到 v2 |

本模块不会自动选择 transport、把 `deletingExisting()` 猜成 delete+create、把所有 delete Boolean 分支猜成状态列表处理，也不会替调用者决定多 kubeconfig 合并顺序。这些都可能改变集群写入、重试和安全语义。

## 真实仓库测试

测试不是只用合成样例。以下公开仓库均固定到不可变 commit，并把实际代码约简成 before→after 或 SearchResult fixture：

- [Quarkus `6378c697`](https://github.com/quarkusio/quarkus/blob/6378c69703a485f55b3d221493b5f1e3cfdf9003/bom/application/pom.xml)：`5.12.0` 属性拥有 `kubernetes-client-bom`，验证主 artifact 配方不越权修改 BOM 属性。
- [EPAM Cloud Pipeline `0474f0b1`](https://github.com/epam/cloud-pipeline/blob/0474f0b13233edbf600f339001aa77b82edb8a28/vm-monitor/src/main/java/com/epam/pipeline/vmmonitor/service/k8s/KubernetesDeploymentMonitor.java)：`KubernetesClient` try-with-resources 接收 `new DefaultKubernetesClient(config)`，验证安全 builder 自动迁移。
- [YugabyteDB `d2dbffa5`](https://github.com/yugabyte/yugabyte-db/blob/d2dbffa51b6ad60903dac46442f36b2a2a786299/managed/src/main/java/com/yugabyte/yw/common/operator/KubernetesOperator.java)：同一真实构造模式，额外验证两 cycle 幂等性。
- [Apache Flink `98997ea3`](https://github.com/apache/flink/blob/98997ea37ba08eae0f9aa6dd34823238097d8e0d/flink-kubernetes/src/main/java/org/apache/flink/kubernetes/kubeclient/FlinkKubeClientFactory.java)：`NamespacedKubernetesClient` 接收旧默认实现，验证不做不类型安全替换并给出 marker。
- [Apache HugeGraph Computer `20cb8852`](https://github.com/apache/hugegraph-computer/blob/20cb8852ac69af14871df20618a541cb1725594a/computer/computer-test/src/main/java/org/apache/hugegraph/computer/k8s/MiniKubeTest.java)：`getKubeconfigFilename()` 直接进入 `File`，验证集合签名变化被精确标记。

测试结构参考 OpenRewrite 官方固定提交中的 [`ChangeTypeTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeTypeTest.java) 与 [`ChangeMethodNameTest`](https://github.com/openrewrite/rewrite/blob/1b1804a5af7692612398fcce034a846b48b5b8cf/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeMethodNameTest.java) 的 before/after、negative、marker 和 cycle 断言风格。当前 26 个测试覆盖 Maven/Gradle、直接/属性/managed/BOM、相似坐标、目标/邻近/未来版本、构造器、package/type/method、mock、build/config marker、组合配方、discovery/validation 和幂等性。

## 使用与验收

先 dry run：

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-kubernetes-client-java-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.kubernetesclientjava.MigrateKubernetesClientJavaTo7_3_1
```

应用后至少执行：

1. 用 Java 11+ 全量编译，并运行 dependency convergence、重复类、linkage、shade/OSGi/native-image 检查。
2. 明确 Vert.x 或 OkHttp transport，测试 kubeconfig/in-cluster、token rotation、exec/OIDC、TLS、proxy、timeout。
3. 在与生产一致的 Kubernetes/OpenShift minor 上验证 CRUD、namespace、delete/evict、watch/reconnect、informer/resync、log/exec/attach/port-forward。
4. 对内置 model、CRD、OpenShift Template 和 JSON/YAML 做 round-trip golden tests；确保每个资源有正确 `apiVersion`。
5. 升级真正拥有版本的 BOM/platform 和全部 Fabric8 extension，刷新 lockfile、依赖校验、SBOM、镜像扫描结果，再执行 canary 与回滚演练。

模块自身验收：

```bash
mvn -f rewrite-kubernetes-client-java-upgrade/pom.xml clean verify
```
