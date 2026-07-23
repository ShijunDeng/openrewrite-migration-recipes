# Bouncy Castle Provider `bcprov-jdk18on` 迁移到 1.84

本模块将工作簿明确列出的 `org.bouncycastle:bcprov-jdk18on` 六个源版本迁移到 `1.84`。它不是单纯修改版本号：配方会自动执行已有官方源码证据证明语义等价的改写，并把需要密钥、编码、Provider 顺序、安全策略或真实运行数据才能决定的事项标记到对应源码、构建或配置节点。

Java package、Maven group 均使用 `com.huawei.clouds.openrewrite`；模块 artifact 为 `rewrite-bcprov-jdk18on-upgrade`。

## 工作簿边界

严格版本白名单固定为：

```text
1.75  1.78  1.78.1  1.79  1.80  1.81  ->  1.84
```

| 坐标 | 精确源版本 | 目标版本 |
| --- | --- | --- |
| `org.bouncycastle:bcprov-jdk18on` | `1.75`, `1.78`, `1.78.1`, `1.79`, `1.80`, `1.81` | `1.84` |

`1.74`、`1.76`、`1.77`、`1.82`、`1.83`、`1.85+` 等表外固定版本不会被擅自纳入；范围、动态版本、version catalog、外部 BOM/platform、无法唯一归属的共享属性、classifier、非 JAR 变体也不会被猜测式修改。

## 配方入口

| 配方 | 模式 | 作用 |
| --- | --- | --- |
| `com.huawei.clouds.openrewrite.bcprovjdk18on.UpgradeBcProvJdk18onTo1_84` | AUTO | 只升级六个白名单版本，且要求依赖所有权与 artifact 形态明确 |
| `com.huawei.clouds.openrewrite.bcprovjdk18on.MigrateDeterministicBcProv1_84Java` | AUTO | 执行类型归因后的确定性包、类型、方法和字段迁移 |
| `com.huawei.clouds.openrewrite.bcprovjdk18on.FindBcProv1_84BuildRisks` | MARK | 标记版本所有权、Bouncy Castle 家族偏斜、Provider 冲突和打包风险 |
| `com.huawei.clouds.openrewrite.bcprovjdk18on.FindBcProv1_84SourceAndConfigurationRisks` | MARK | 标记源码、Provider、PQC、ASN.1、PKCS12、序列化、参数和配置风险 |
| `com.huawei.clouds.openrewrite.bcprovjdk18on.MigrateBcProvJdk18onTo1_84` | 推荐入口 | 按“严格依赖升级 → 确定性源码迁移 → 构建审计 → 源码/配置审计”组合执行 |

`SearchResult` 是配方输出的待决策事项，不是失败噪声。推荐先运行 `dryRun`，审查 AUTO diff 与所有 MARK，再应用到业务分支。

## 自动处理（AUTO）

| 不兼容点 | 自动操作 | 证明边界 |
| --- | --- | --- |
| 工作簿选中的 `bcprov-jdk18on` 版本 | Maven root/profile 的直接依赖与 `dependencyManagement`、根 Gradle `dependencies` 中的 Groovy/Kotlin 字符串坐标及 Groovy map，精确改为 `1.84` | Maven property 只有定义唯一、引用全部归目标依赖所有且无 profile 同名遮蔽时才改；保留 scope、optional、exclusions 等元数据 |
| BIKE lightweight API | `org.bouncycastle.pqc.crypto.bike` → `org.bouncycastle.pqc.legacy.bike` | 只对类型归因为官方类型的 import、类型和引用执行递归包迁移；迁移后仍 MARK persisted key/vector/provider 风险 |
| Picnic lightweight API | `org.bouncycastle.pqc.crypto.picnic` → `org.bouncycastle.pqc.legacy.picnic` | 上游 1.84 是包移动；不替换参数集、OID、密钥或测试向量 |
| Rainbow lightweight API | `org.bouncycastle.pqc.crypto.rainbow` → `org.bouncycastle.pqc.legacy.rainbow` | 上游 1.84 是包移动；算法退休与持久化兼容仍由业务决定 |
| `WrapUtil` 提升 | `org.bouncycastle.pqc.jcajce.provider.util.WrapUtil` → `org.bouncycastle.jcajce.provider.asymmetric.util.WrapUtil` | 精确 FQN 类型迁移，不改同名业务类型；目标实现的 secret 截取/KDF 行为仍由 MARK 要求做向量验证 |
| `Pack` 四参数 overload 删除 | `Pack.longToBigEndian(long, byte[], int, int)` → `Pack.longToBigEndian_Low(...)` | 新方法保留旧 overload 的低位字节写入语义；覆盖普通调用和 static import，不影响其他 overload |
| HPKE 常量拼写修复 | `HPKE.kem_P384_SHA348` → `HPKE.kem_P384_SHA384` | 两个常量值均为 `17`；只改官方 `HPKE` 字段引用 |
| `ECPrivateKey#getParameters()` 删除 | `key.getParameters()` → `key.getParametersObject().toASN1Primitive()` | 上游旧方法体逐字就是该表达式；只匹配类型归因后的 `org.bouncycastle.asn1.sec.ECPrivateKey` 接收者，保留接收者求值次数 |

所有 AUTO 都依赖 OpenRewrite 类型归因并跳过 `target`、`build/generated`、缓存、安装、报告等生成目录。同名业务类、缺失类型归因和相似字符串不会被当成可自动迁移证据。对每一个待迁移的 PQC 包，配方还以编译单元为安全边界：一旦发现星号 import、无法归因的旧包 import，或源码本身声明在旧 Bouncy Castle package/subpackage 内，就拒绝该包迁移；完全限定且可归因的官方类型仍可安全迁移。

## SPHINCS+：绝不做全局包替换

1.84 同样把 SPHINCS+ lightweight API 移到 `org.bouncycastle.pqc.legacy.sphincsplus`，但本模块故意没有将它加入 AUTO。原因不是缺少一条 `ChangePackage`，而是相同字段名在不同源版本代表不同算法参数，盲目改包可能编译成功却改变签名算法、OID、密钥编码和测试向量。

| 源路径 | 源版本行为 | 1.84 对应关系 | 本模块行为 |
| --- | --- | --- | --- |
| lightweight `SPHINCSPlusParameters` | 1.75 的无后缀 `sha2_*` / `shake_*` 是 **robust**；`*_simple` 才是 simple | 若要保留 1.75 的运行语义，无后缀应选 1.84 的 `*_robust`，旧 `*_simple` 才对应 1.84 无后缀标准化/simple 名称 | MARK；不得只改 package |
| JCA `SPHINCSPlusParameterSpec` | 1.75 的 `sha2_128f_simple` 实际 name 是 `sha2-128s-simple`，`sha2_128s_simple` 实际 name 是 `sha2-128f-simple` | “保留旧运行结果”与“保留字段字面意图”会产生不同映射 | 人工用已知向量、OID 和持久化密钥决定 |
| JCA `SPHINCSPlusParameterSpec` | 1.78 与 1.78.1 的 `sha2_128f` / `sha2_128s` 仍交叉绑定为 `sha2-128s` / `sha2-128f`；1.79 才纠正 | 不能对 1.78/1.78.1 与 1.79+ 应用同一字段替换 | 人工按模块实际解析版本决定 |
| lightweight/JCA 字符串 | `fromName(...)`、配置、反射或数据库可能保存 `*-simple`、`*-robust`、OID 或自定义别名 | 字符串与对象编码不受 Java package 改写保护 | 手工清点并做跨版本验证 |

低层 `org.bouncycastle.pqc.crypto.sphincsplus.*` 使用会被风险配方标记；JCA `SPHINCSPlusParameterSpec`、反射和外部字符串应额外执行：

```bash
rg -n 'SPHINCSPlus|sphincsplus|sha2-[0-9]+[fs]|shake-[0-9]+[fs]' .
```

1.84 release notes 明确说明：这是 BC Provider 最后一个继续识别旧 Dilithium/SPHINCSPlus 名称、并保留 Kyber wrapper 的版本；它们不是在 1.84 已全部删除。不要把面向 1.85 的退役说明错误应用到本次目标版本，但应提前建立后续退出计划。

## 精确标记（MARK）

### 构建、依赖家族与 Provider 制品

| 风险 | 配方定位 | 必须决定的事项 |
| --- | --- | --- |
| 版本没有本地唯一所有者 | versionless、变量、范围、动态 Gradle、catalog、platform/BOM、共享或 profile 遮蔽 property | 修改父 POM、BOM、catalog 或 lockfile 的真实所有者，并证明最终解析为 `1.84` |
| 表外固定版本或非标准 artifact | 依赖 version、classifier、type、四段 Gradle 坐标 | 为该来源单独批准路径，确认 1.84 是否存在相同 artifact shape |
| Bouncy Castle 家族偏斜 | `bc-bom`、`bcutil`、`bcpkix`、`bcpg`、`bctls`、`bcmail`、`bctest` | 以经批准的兼容矩阵验证家族；本模块绝不修改 companion 版本 |
| Provider lineage 冲突 | `bcprov-jdk15on`、`bcprov-jdk15to18`、`bcprov-ext-*`、`bc-fips` | 选择唯一 Provider lineage，检查 `BC`/`BCPQC` 名称、插入顺序、class loader 和服务可见性 |
| signed Provider 打包 | Maven Shade/bnd/native 配置、Gradle Shadow relocation、`MANIFEST.MF`、`bnd.bnd`、policy | 1.84 Provider JAR 改用 RSA 证书签名；验证未错误 relocation、剥离或合并签名/服务元数据，并在最终制品中初始化 Provider |

构建风险扫描以同一个 Maven root/profile 或根 Gradle `dependencies` 中存在目标主依赖为所有权证据，不把无关子工程的 companion 自动归入同一个升级单元。

工作簿中 `bcpkix-jdk18on` 的独立目标是 `1.81.1`，而本模块的 `bcprov-jdk18on` 目标是 `1.84`。该组合会留下 FAMILY MARK 供兼容矩阵审批；配方只把 `bcprov` 改为 `1.84`，明确保留 `bcpkix:1.81.1`，不会跨模块替用户“对齐”版本。

### 1.82 legacy 删除与 PQC 标准化

升级路径跨过 1.82 的 legacy PQC 删除。配方精确标记：

- `org.bouncycastle.pqc.legacy.crypto.{gemss,gmss,mceliece,ntru,qtesla}.*` 与 `org.bouncycastle.pqc.legacy.math.{linearalgebra,ntru}.*`；
- 1.75 路径中的 `org.bouncycastle.pqc.crypto.gemss.*`；
- GMSS、McEliece、Kyber、Rainbow 等已删除/签名不兼容的 ASN.1、JCA spec、interface 和 provider 类型；
- `org.bouncycastle.pqc.crypto.crystals.kyber.*` 等从实验接口转向 ML-KEM 的边界。

这些迁移没有普适的一对一替换。应选择 ML-KEM、ML-DSA、SLH-DSA 或其他已批准算法，重新生成/导入密钥，固定 draft/standard、OID、seed/expanded encoding、secret 长度和互操作向量。Dilithium-AES、Kyber-AES、旧 SPHINCS robust/simple 也不得机械改成非 AES 或新标准参数集。

BIKE、Picnic、Rainbow 的包迁移虽然是 AUTO，目标仍位于 `pqc.legacy`；推荐组合配方会在迁移后的类型上继续留下 MARK，提醒业务验证 Provider 可用性、持久化密钥、测试向量和退役计划。

### Provider 与 PKCS12

源码配方标记类型归因后的：

- `Security.addProvider`、`insertProviderAt`、`removeProvider`、`getProvider("BC"/"BCPQC")`；
- 指定 BC/BCPQC 的 JCA `getInstance(...)`，以及发生过映射变化的 PQC/DSTU 算法名；
- `KeyStore.getInstance("PKCS12", "BC")` 等显式 BC PKCS12 路径。

配置配方识别 properties、YAML、非 POM XML 及 plain-text 安全/打包文件中的 Provider 顺序和下列键：

```text
org.bouncycastle.pkcs12.max_it_count
org.bouncycastle.pkcs12.default
```

识别采用结构化精确键：properties 必须是目标 entry key，YAML 必须是目标 scalar key（Provider 允许 `security` 下的 `provider.N`），XML 必须是目标 tag 或 `name`/`key` + `value` 属性结构；plain text 只解析 `java.security`/policy 的非注释 `key=value` 行。README、docs XML、`application.yml` 或 log4j2 properties 中仅把这些名称当说明文字/value 的内容不会被标记。

1.84 的 PKCS12 默认最大 iteration count 是 `5,000,000`。配方不会自动提高上限，也不会删除进程级 Provider 注册；应先盘点现有 keystore、密码、alias、MAC/PBMAC1/PBKDF2、GCM 变体、HSM/custom provider 和最坏 CPU 成本，再依据明确的 DoS 预算决定是否覆盖。还应验证 1.84 修复后的“使用原 Provider 调用 `getInstance`”行为是否消除了旧的强制全局注册依赖。

### ASN.1、DER 与 `bcutil` 拆分

以下 FQN 在 1.84 保持不变，但承载 artifact 已从 `bcprov-jdk18on` 移到 `bcutil-jdk18on`；配方只 MARK，不猜测依赖所有权：

- `cryptlib`、`edec`、`gnu`、`iana`、`isara`、`iso`、`kisa`、`microsoft`、`nsri`、`ntt`、`rosstandart` 包中的 `*ObjectIdentifiers`；
- `misc.CAST5CBCParameters`、`IDEACBCPar`、`MiscObjectIdentifiers`、`NetscapeCertType`、`NetscapeRevocationURL`、`ScryptParams`、`VerisignCzagExtension`；
- `mozilla.PublicKeyAndChallenge`、`mozilla.SignedPublicKeyAndChallenge`；
- `oiw.ElGamalParameter`、`oiw.OIWObjectIdentifiers`。

确认实际使用后，在真实 Maven/BOM/Gradle owner 中加入并对齐 `org.bouncycastle:bcutil-jdk18on:1.84`；JPMS 工程还需检查 `requires org.bouncycastle.util;`。当前搜索 visitor 对这些 ASN.1 package 做保守标记，因此需用目标依赖树确认具体类型，不应无条件给所有工程加 `bcutil`。

配方还标记 `ASN1InputStream`、`ASN1ObjectIdentifier`、`ASN1Primitive.fromByteArray` 与 ASN.1 `getEncoded` 边界。升级跨越 OID 内容最多 4096 字节的校验变化；1.84 又加入：

```text
org.bouncycastle.asn1.max_cons_depth   # 默认 32
org.bouncycastle.asn1.max_limit        # bytes，或 k/m/g 后缀
org.bouncycastle.asn1.allow_wrong_oid_enc
```

不要为了让旧数据“能读”而无条件放宽限制。应保留恶意、深层、大体积、截断、BER/DL/DER、非规范 OID 输入测试，并对证书、CMS、PKCS、签名输入和持久化 payload 做逐字节 golden test。DER/DL 输出、PQC OID/encoding 和草案升级都可能在 Java 源码仍可编译时破坏互操作。

### Java API、序列化与 crypto parameters

配方会在可归因节点标记以下非确定性边界：

- `ECPrivateKey` 已删除的 `BigInteger` 起始构造器；构造器所需 order bit length、参数对象和求值副作用不能凭语法猜测。无参 `getParameters()` 已由 AUTO 按其旧方法体等价替换；
- Elephant、ISAP、PhotonBeetle、Xoodyak 的 `getBlockSize()` 删除，`GcmSpecUtil.extractGcmParameters` 返回契约变化，`BrokenKDF2BytesGenerator`、ASN.1 `Dump` 等已删除 API；
- `Strings.split` 对首字符就是 delimiter 的 1.84 修复；若旧错误结果曾被持久化或进入协议，需要单独回归；
- 自动换包后的 `WrapUtil`：1.75→1.84 期间无 KDF 分支改为按 key size 截取 secret，内部 KDF/wrapper 实现也演进；必须比较固定 key-size、KDF、wrapped bytes 与跨版本 unwrap 向量；
- PQC parameter/key `getEncoded()`，以及跨越 ML-KEM/ML-DSA/SLH-DSA private-key draft、seed-only/expanded form、HQC/Falcon/Ascon/LMS 等参数或编码演进；
- `ObjectOutputStream.writeObject` 且静态参数类型为 `org.bouncycastle.*` 的直接 Java 序列化。

1.84 还修复了 JDK 25 KDF API 的多 IKM/salt 行为，但旧版低层 `HKDFParameters`/`HKDFBytesGenerator.init` 并没有同一项行为变化，因此配方不会把每个低层 HKDF 调用误报成 1.84 迁移问题；使用 JDK 25 KDF API 的工程仍应按官方提交增加多 IKM 向量。

Java serialization 不是 Bouncy Castle 的稳定跨版本契约。以 1.75 与 1.84 的默认 UID 对比为例：

| 类 | 1.75 | 1.84 |
| --- | ---: | ---: |
| `CompositePrivateKey` | `-6601286978752987209` | `5099614314794573286` |
| `CompositePublicKey` | `-5430332130043477554` | `-6665067880844956731` |
| `BouncyCastleProvider` | `-7425315890654846242` | `-8623630895370985667` |
| `JCEECPrivateKey` | `1973854221598404955` | `-8809410011355167629` |
| `X509CertificateObject` | `-477877398915096851` | `4783657299441891023` |

应把 Provider、实现类、composite key、certificate 的 ObjectStream 持久化改为带 schema/version 的标准编码，或显式完成旧数据读取、滚动升级和回滚演练。通过 `Object`、缓存框架、ORM、消息中间件或反射间接序列化时，静态 visitor 不一定能定位，必须配合仓库搜索与运行时数据盘点。

### 配置安全开关

下列属性会被 MARK，但不会自动增删或改值：

```text
org.bouncycastle.asn1.allow_wrong_oid_enc
org.bouncycastle.pemreader.lax
org.bouncycastle.ec.disable_f2m
org.bouncycastle.drbg.effective_256bits_entropy
org.bouncycastle.rsa.no_lenstra_check
```

每个开关都改变验证、算法面或熵假设。应重新记录 threat model、配置来源与优先级，并分别测试默认路径和显式配置路径。

## NO-OP 与已知人工边界

以下输入保持不变，这是安全门禁而不是遗漏式“顺手升级”：

- 六个白名单之外的版本、已经是 `1.84` 的声明及更高版本；
- `bcprov-jdk15on`、`bcprov-jdk15to18`、`bcprov-ext-*`、`bc-fips` 等不同 lineage；它们只在同一升级单元中作为冲突 MARK；
- Maven plugin dependency、远程 parent/BOM、catalog、lockfile、动态/范围/插值版本、共享或遮蔽 property；
- classifier、test-jar、zip、四段 Gradle 坐标、嵌套 `subprojects`/`project(...)`/`buildscript` scope；
- generated/cache/install/report 目录、缺失类型归因的源码、同名业务 API 和普通 JCA 算法；
- 无法由本地 AST 证明的密钥轮换、Provider 顺序、HSM 行为、算法替代、ASN.1 限额、安全开关和持久化数据变换。

另外，`HQCKeyPairGenerator#generateKeyPairWithSeed(byte[])`、LMS public surface、反射 API、资源内算法别名、JCA `SPHINCSPlusParameterSpec` 字段和非直接 ObjectStream 持久化目前没有安全的通用 AUTO；其中部分也无法在所有形式下自动定位。生产审批前必须结合下面的 `rg` 清单与编译错误逐项处理，不能把“没有 marker”解释为兼容。

```bash
rg -n 'org\.bouncycastle|BCPQC|Security\.(addProvider|insertProviderAt|removeProvider)|PKCS12' src .
rg -n 'SPHINCSPlus|Kyber|Dilithium|MLKEM|MLDSA|SLHDSA|GMSS|McEliece|QTESLA|HQC|LMS' src .
rg -n 'Object(Input|Output)Stream|Serializable|asn1|max_cons_depth|max_limit|allow_wrong_oid_enc' src .
```

## 生产工程集成

先在配方仓安装本模块：

```bash
mvn -f rewrite-bcprov-jdk18on-upgrade/pom.xml clean install
```

在目标 Maven 工程根目录先生成 patch：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-bcprov-jdk18on-upgrade:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.bcprovjdk18on.MigrateBcProvJdk18onTo1_84
```

审批 AUTO 与 MARK 后，把 `dryRun` 改为 `run`。生产流水线应固定已经验证过的 recipe artifact 和 OpenRewrite plugin 版本，不使用浮动版本。

推荐按以下门禁集成：

1. 在独立分支记录升级前 dependency tree、Provider/service 列表、JAR signer、密钥/OID/DER/PKCS12 golden fixtures 和可回滚制品。
2. 运行推荐配方；把每一个 MARK 作为有 owner、有证据、有结论的迁移事项处理。若需分阶段，可先运行严格 dependency recipe，再分别运行 AUTO 与 audit recipes。
3. 用目标依赖重新编译全部 source set；确认 Bouncy Castle 家族、JPMS/OSGi/service/shaded artifact 在最终运行包内一致，不只检查 IDE classpath。
4. 执行源版本与 1.84 的双向互操作：读旧 key/store/certificate/CMS/ASN.1，写新数据给旧端读取，校验 OID、DER bytes、secret/signature/vector，而不只验证“调用未抛异常”。
5. 覆盖 Provider 顺序、并发 class loader、PKCS12 高 iteration、ASN.1 深度/大小/畸形输入、PQC 参数、HKDF 多 IKM、序列化旧数据、滚动升级和回滚。
6. 对最终 JAR/WAR/container image 运行签名与安全扫描；启动时打印实际 `Provider.Service` 和版本，灰度期间监控解析拒绝、签名失败、CPU/内存和 keystore 加载延迟。

## 测试证据与真实仓库用例

模块测试套件覆盖六个版本、Maven/Gradle 所有权与 NO-OP 门禁、BIKE/Picnic/Rainbow、WrapUtil、Pack、HPKE、EC getter 的确定性源码改写、构建/源码/配置 MARK、星号/未知 import 和第三方同包声明拒绝门禁、生成目录、同名 API、类型归因、marker 幂等和推荐组合配方顺序。

测试形态来自固定到不可变 commit 的公开真实仓库，而不是只构造理想化片段：

- Membrane API Gateway 的 [`PEMSupport`](https://github.com/membrane/api-gateway/blob/324c5dde40f82226b514b9a7824a9b51c7a5c35f/core/src/main/java/com/predic8/membrane/core/transport/ssl/PEMSupport.java#L63-L78) 在构造路径调用 `Security.addProvider(new BouncyCastleProvider())`，用于 Provider 全局状态 MARK 形态。
- `cbomkit/sonar-cryptography` 的 [`BcEncapsulatedSecretGenerator`](https://github.com/cbomkit/sonar-cryptography/blob/e83dd0b39d33932325fcc71a7efb4e9f744d0bcd/java/src/main/java/com/ibm/plugin/rules/detection/bc/encapsulatedsecret/BcEncapsulatedSecretGenerator.java#L45-L59) 同时保存 BIKE 与旧 Kyber package 字符串，用于验证字符串扫描会暴露 Kyber removed/promoted 风险，却不会机械改写检测规则数据。
- LUMII QKD 服务的 [`InjectableSphincsPlus`](https://github.com/LUMII-Syslab/qkd-as-a-service/blob/f1a5e862d33fcc4bc06f5cc8c202f902e818b425/pqproxy/src/main/java/lv/lumii/pqc/InjectableSphincsPlus.java#L38-L57) 把 `sha2_128f` 与 OID、code point 绑定，并在 [编码路径](https://github.com/LUMII-Syslab/qkd-as-a-service/blob/f1a5e862d33fcc4bc06f5cc8c202f902e818b425/pqproxy/src/main/java/lv/lumii/pqc/InjectableSphincsPlus.java#L107-L162) 手工处理参数前缀，证明 SPHINCS+ 不能只靠编译成功判断兼容。
- Android `PQCBenchmark` 的 [`SphincsPlusAlgorithm`](https://github.com/Spuddy10345/PQCBenchmark/blob/f91482d015054e38a216d423b9fe019b7cb4b674/app/src/main/java/com/example/pqcbenchmark/SphincsPlusAlgorithm.java#L5-L31) 是无后缀 lightweight 参数的真实形态，用于保留“版本未知时不盲迁”的负例。

OpenRewrite 测试结构参考固定 commit 中的 [`RewriteTest`](https://github.com/openrewrite/rewrite/blob/fb933bdb74f2f4dc10ec79387e29aa8f5a8a9503/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java)、[`ChangePackageTest`](https://github.com/openrewrite/rewrite/blob/fb933bdb74f2f4dc10ec79387e29aa8f5a8a9503/rewrite-java-test/src/test/java/org/openrewrite/java/ChangePackageTest.java) 和 [`ChangeMethodNameTest`](https://github.com/openrewrite/rewrite/blob/fb933bdb74f2f4dc10ec79387e29aa8f5a8a9503/rewrite-java-test/src/test/java/org/openrewrite/java/ChangeMethodNameTest.java) 的 before/after、negative、cycle/idempotence 与类型归因模式。

模块自检：

```bash
mvn -f rewrite-bcprov-jdk18on-upgrade/pom.xml clean verify
```

当前执行 **157** 项测试（0 failure / 0 error / 0 skip）。

## 官方依据（固定 commit）

所有比较均固定到 tag 对应的源码 commit，避免默认分支后续变化改变证据：

| 版本/tag | 固定源码 commit |
| --- | --- |
| `r1rv75` | [`739a5316`](https://github.com/bcgit/bc-java/tree/739a5316dea4c2d05a14933ad77082e671745a7b) |
| `r1rv78` | [`30c6cc60`](https://github.com/bcgit/bc-java/tree/30c6cc60ef5aa9062a083a8cea3e5c4f96d91a2a) |
| `r1rv78v1` | [`ada71e4c`](https://github.com/bcgit/bc-java/tree/ada71e4cf20c990e045a57ee1d42378d84050e03) |
| `r1rv79` | [`b6cb37c8`](https://github.com/bcgit/bc-java/tree/b6cb37c83caabba2f3a6b87787dd08a51124dffe) |
| `r1rv80` | [`5ce0d4d4`](https://github.com/bcgit/bc-java/tree/5ce0d4d4536bcb622c1077a6b9157b02ad8adcc5) |
| `r1rv81` | [`228211ec`](https://github.com/bcgit/bc-java/tree/228211ecb973fe87fdd0fc4ab16ba0446ec1a29c) |
| `r1rv82`（中间删除边界） | [`de42702b`](https://github.com/bcgit/bc-java/tree/de42702b6cda2631e8e3ff94f8458198860b328e) |
| `r1rv84` | [`d716d771`](https://github.com/bcgit/bc-java/tree/d716d7716a452bad283323aefd88ff21eba8deef) |

关键官方差异：

- [固定 1.84 release notes](https://github.com/bcgit/bc-java/blob/d716d7716a452bad283323aefd88ff21eba8deef/docs/releasenotes.html)：PKCS12/ASN.1 限额、Provider RSA 签名、HKDF、`Strings.split`、旧 PQC provider 生命周期等目标行为。
- [1.82 删除 legacy PQC package](https://github.com/bcgit/bc-java/commit/9ffcb59e3fabbf0fa73b046817543421c1efc808)。
- [1.84 移动 BIKE/Picnic/Rainbow/SPHINCS+ package](https://github.com/bcgit/bc-java/commit/e76a2d818f569aab9c203c32a057f2357f3c5277)。
- [ML-KEM 提升及 `WrapUtil` 移动](https://github.com/bcgit/bc-java/commit/d7c6af3ab72e29ba0961e5de0b748fa7aadc1a71)，以及[PQC AES/SPHINCS robust 清理](https://github.com/bcgit/bc-java/commit/f55f635b1a18eee658fe517b04c2c2254e215072)。
- ASN.1 类型迁移到 bcutil 的固定提交：[一](https://github.com/bcgit/bc-java/commit/30a7eb7a90c9a79cf04a99c543716d76545443e1)、[二](https://github.com/bcgit/bc-java/commit/c7a425cafb91c66503566bea345e36951d2cc0a6)、[三](https://github.com/bcgit/bc-java/commit/e05e4b78ae963f96daa28bfd86077bc4d8a14f5b)、[四](https://github.com/bcgit/bc-java/commit/3b77ea5d90ace95ab943cfcb3d466950245a568a)。
- [`Pack.longToBigEndian_Low` 等价替代](https://github.com/bcgit/bc-java/commit/55465014a20a5e21f12e687c76fc9c5d00e1c922)、[`ECPrivateKey` deprecated API 删除](https://github.com/bcgit/bc-java/commit/492446feed1d72e3abfa450c98904851c6577154)、[HPKE 常量 typo 修复](https://github.com/bcgit/bc-java/commit/3bd7b4bca025338816bb7263638f649235fb8dda)。
- [ASN.1 constructed-depth/stream limits](https://github.com/bcgit/bc-java/commit/02527424eb4fdf06a3d14a31138576b487fc2065)、[OID 4096-byte 限制](https://github.com/bcgit/bc-java/commit/3790993df5d28f661a64439a8664343437ed3865)、[PKCS12 iteration ceiling](https://github.com/bcgit/bc-java/commit/fa59cc23502f73def89d94374540cc92af647b96)、[PKCS12 保留原 Provider](https://github.com/bcgit/bc-java/commit/248716d1a34c92d5f3d1be2c99a780f26495d12f)、[HKDF 多 IKM/salt 修复](https://github.com/bcgit/bc-java/commit/2c726a92245c3d08bc7e4126d67de2404193c833)。
- SPHINCS+ 语义核对源码：[1.75 lightweight](https://github.com/bcgit/bc-java/blob/739a5316dea4c2d05a14933ad77082e671745a7b/core/src/main/java/org/bouncycastle/pqc/crypto/sphincsplus/SPHINCSPlusParameters.java)、[1.75 JCA spec](https://github.com/bcgit/bc-java/blob/739a5316dea4c2d05a14933ad77082e671745a7b/prov/src/main/java/org/bouncycastle/pqc/jcajce/spec/SPHINCSPlusParameterSpec.java)、[1.78 JCA spec](https://github.com/bcgit/bc-java/blob/30c6cc60ef5aa9062a083a8cea3e5c4f96d91a2a/prov/src/main/java/org/bouncycastle/pqc/jcajce/spec/SPHINCSPlusParameterSpec.java)、[1.78.1 JCA spec](https://github.com/bcgit/bc-java/blob/ada71e4cf20c990e045a57ee1d42378d84050e03/prov/src/main/java/org/bouncycastle/pqc/jcajce/spec/SPHINCSPlusParameterSpec.java)、[1.79 修复后的 JCA spec](https://github.com/bcgit/bc-java/blob/b6cb37c83caabba2f3a6b87787dd08a51124dffe/prov/src/main/java/org/bouncycastle/pqc/jcajce/spec/SPHINCSPlusParameterSpec.java)、[1.84 lightweight](https://github.com/bcgit/bc-java/blob/d716d7716a452bad283323aefd88ff21eba8deef/core/src/main/java/org/bouncycastle/pqc/legacy/sphincsplus/SPHINCSPlusParameters.java)。
