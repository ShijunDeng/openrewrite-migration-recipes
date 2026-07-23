# openrewrite-migration-recipes

[![CI](https://github.com/ShijunDeng/openrewrite-migration-recipes/actions/workflows/ci.yml/badge.svg)](https://github.com/ShijunDeng/openrewrite-migration-recipes/actions/workflows/ci.yml)

基于 OpenRewrite 的软件迁移配方集合。工程采用 Maven 多模块结构，统一管理依赖、插件版本与 CI，同时让每种迁移保持独立发布、独立测试和按需引入。

## Workbook migration catalog

Excel 全量迁移清单已拆分为
[`catalog/`](catalog/README.md) 下的 1,967 个文档规格模块；完整行级映射见
[`docs/workbook-module-index.md`](docs/workbook-module-index.md)。catalog 模块只承载
README、机器可读 manifest 和禁降级契约，不进入默认 Maven reactor；配方代码仍按下表
逐模块核验、完善和提交。

用户指定的高优先级 Java 清单已完成 25/25，官方能力复用与提交记录见
[`docs/high-priority-java-ledger.md`](docs/high-priority-java-ledger.md)。
既有模块的补充官方能力复审、双 JDK 结果与远端门禁修复记录见
[`docs/official-reuse-audit-ledger.md`](docs/official-reuse-audit-ledger.md)。

## Modules

| Module | Recipe | Description |
| --- | --- | --- |
| `rewrite-fastjson-to-jackson-common` | 内部模块 | Fastjson 1.x / Fastjson2 共用迁移引擎，不直接激活 |
| [`rewrite-fastjson-to-jackson`](rewrite-fastjson-to-jackson) | `com.huawei.clouds.openrewrite.fastjson.MigrateFastjsonToJackson` | 将 Fastjson 1.x Java 工程迁移到 Jackson 2.x |
| [`rewrite-fastjson2-to-jackson`](rewrite-fastjson2-to-jackson) | `com.huawei.clouds.openrewrite.fastjson2.MigrateFastjson2ToJackson` | 将 Fastjson2 Java 工程迁移到 Jackson 2.x |
| [`rewrite-rxjs-upgrade`](rewrite-rxjs-upgrade) | `com.huawei.clouds.openrewrite.rxjs.MigrateRxjsTo7_8_2` | 升级 RxJS 依赖，自动迁移确定性的导入、Ajax 类型和 `throwError`，并标记需要流语义判断的不兼容点 |
| [`rewrite-guava-upgrade`](rewrite-guava-upgrade) | `com.huawei.clouds.openrewrite.guava.MigrateGuavaTo33_5_0Jre` | 将 Guava 21/29–32 升级到 33.5.0-jre，自动迁移 `CharMatcher` 常量和旧 executor 重载，并标记移除 API、Android flavor、GWT 与 Gradle metadata 风险 |
| [`rewrite-ngx-translate-core-upgrade`](rewrite-ngx-translate-core-upgrade) | `com.huawei.clouds.openrewrite.ngxtranslate.MigrateNgxTranslateCoreTo17` | 将 `@ngx-translate/core` 11/13/14/15 升级到 17.0.0，自动迁移绑定可证明的 fallback API 与默认实现，并精确标记 standalone、provider、HTTP loader、事件、并发与 SSR 风险 |
| [`rewrite-hibernate-validator-upgrade`](rewrite-hibernate-validator-upgrade) | `com.huawei.clouds.openrewrite.hibernatevalidator.MigrateHibernateValidatorTo8_0_3` | 严格升级 Hibernate Validator，迁移 Jakarta Validation/EL、XML 与 service loader，并标记移除约束、SPI 和 EL 风险 |
| [`rewrite-echarts-upgrade`](rewrite-echarts-upgrade) | `com.huawei.clouds.openrewrite.echarts.MigrateEChartsTo6_1_0` | 将 ECharts 4/5 升级到 6.1.0，自动迁移公开入口和旧 option 类型，并标记 deprecated option、主题/布局、data index、地图与 wrapper 风险 |
| [`rewrite-fastjson-upgrade`](rewrite-fastjson-upgrade) | `com.huawei.clouds.openrewrite.fastjson.MigrateFastjsonTo2_0_62` | 将 Fastjson 1.x/2.x 兼容模块升级到 2.0.62，自动迁移确定性 API，并精确标记 AutoType、全局 provider、feature 和配置风险 |
| [`rewrite-ngx-translate-http-loader-upgrade`](rewrite-ngx-translate-http-loader-upgrade) | `com.huawei.clouds.openrewrite.ngxtranslate.MigrateNgxTranslateHttpLoaderTo17` | 严格升级 HTTP loader，迁移可确定的旧 factory/provider，并标记路径、HTTP、缓存和配套版本风险 |
| [`rewrite-jedis-upgrade`](rewrite-jedis-upgrade) | `com.huawei.clouds.openrewrite.jedis.MigrateJedisTo7_2_1` | 严格升级 Jedis，迁移确定性 Java API，并标记 sharding、Cluster、pool、命令返回值和连接行为风险 |
| [`rewrite-mybatis-spring-boot-starter-upgrade`](rewrite-mybatis-spring-boot-starter-upgrade) | `com.huawei.clouds.openrewrite.mybatisspringboot.MigrateMyBatisSpringBootStarterTo4_0_0` | 将 MyBatis Spring Boot Starter 升级到 4.0.0，并迁移 Boot 4 类型、配置键、测试注解与 MyBatis XML XSD；高风险项生成迁移标记 |
| [`rewrite-slf4j-upgrade`](rewrite-slf4j-upgrade) | `com.huawei.clouds.openrewrite.slf4j.MigrateSlf4jTo2_0_17` | 严格升级 SLF4J API 到 2.0.17，迁移确定性 provider 坐标，并标记旧 binding、ServiceLoader、bridge 环、shade 与 fluent logging 风险 |
| [`rewrite-angular-common-upgrade`](rewrite-angular-common-upgrade) | `com.huawei.clouds.openrewrite.angular.MigrateAngularCommonTo20_3_26` | 严格升级 Angular Common 20.3.26，自动迁移 `DOCUMENT`/`XhrFactory`/Ivy 配置，并标记 HTTP、模板、locale、SSR 与工具链风险 |
| [`rewrite-angular-platform-browser-upgrade`](rewrite-angular-platform-browser-upgrade) | `com.huawei.clouds.openrewrite.angular.MigrateAngularPlatformBrowserTo20_3_26` | 严格升级 Angular Platform Browser 20.3.26，自动迁移 core 所有权与 Ivy 配置，并标记 bootstrap、hydration、sanitizer、Hammer、SSR 和模板风险 |
| [`rewrite-angular-platform-browser-dynamic-upgrade`](rewrite-angular-platform-browser-dynamic-upgrade) | `com.huawei.clouds.openrewrite.angular.MigrateAngularPlatformBrowserDynamicTo20_3_26` | 严格升级依赖，自动迁移可证明安全的 `platformBrowserDynamic` bootstrap，并精确标记 JIT/AOT/compiler/SSR 风险 |
| [`rewrite-angular-animations-upgrade`](rewrite-angular-animations-upgrade) | `com.huawei.clouds.openrewrite.angular.MigrateAngularAnimationsTo20_3_26` | 严格升级依赖，自动清理可证明冗余的 animation 配置，并精确标记 legacy DSL、native CSS、SSR 与第三方风险 |
| [`rewrite-angular-router-upgrade`](rewrite-angular-router-upgrade) | `com.huawei.clouds.openrewrite.angular.MigrateAngularRouterTo20_3_26` | 严格升级 Angular Router 20.3.26，自动迁移 current navigation、legacy router options 与 RouterLink，并标记 guard、redirect、lazy、provider、SSR 和模板风险 |
| [`rewrite-angular-core-upgrade`](rewrite-angular-core-upgrade) | `com.huawei.clouds.openrewrite.angular.MigrateAngularCoreTo20_3_26` | 严格升级 Angular Core 20.3.26，自动迁移 `TestBed.get`/`DOCUMENT`，并标记 package group、工具链、SSR、DI 与渲染风险 |
| [`rewrite-angular-forms-upgrade`](rewrite-angular-forms-upgrade) | `com.huawei.clouds.openrewrite.angular.MigrateAngularFormsTo20_3_26` | 严格升级 `@angular/forms`，自动迁移可证明安全的 typed-forms/CVA/template 变化并精确标记业务语义风险 |
| [`rewrite-angular-compiler-upgrade`](rewrite-angular-compiler-upgrade) | `com.huawei.clouds.openrewrite.angular.MigrateAngularCompilerTo20_3_26` | 严格升级依赖，自动迁移 Ivy/ngcc/NgModule/保留字的确定性变化并精确标记 compiler、template、toolchain 与 SSR 风险 |
| [`rewrite-jasypt-spring-boot-starter-upgrade`](rewrite-jasypt-spring-boot-starter-upgrade) | `com.huawei.clouds.openrewrite.jasypt.MigrateJasyptSpringBootStarterTo4_0_3` | 严格升级到 4.0.3，自动迁移配置键和自动配置包，并定位 Java/Boot 基线、历史密文、密钥及扩展点风险 |
| [`rewrite-angular-cdk-upgrade`](rewrite-angular-cdk-upgrade) | `com.huawei.clouds.openrewrite.angular.MigrateAngularCdkTo20_2_14` | 严格升级 Angular CDK，自动迁移确定性的 portal/overlay/dialog/Sass 变化，并精确标记 Material、drag-drop、table、SSR 与工具链风险 |
| [`rewrite-hibernate-core-upgrade`](rewrite-hibernate-core-upgrade) | `com.huawei.clouds.openrewrite.hibernate.MigrateHibernateCoreTo7_2_12` | 将 Hibernate Core 5.x 依赖和常见源码 API 迁到 7.2.12.Final |
| [`rewrite-vue-router-upgrade`](rewrite-vue-router-upgrade) | `com.huawei.clouds.openrewrite.vuerouter.MigrateVueRouterTo5_0_3` | 严格升级 Vue Router，自动迁移 unplugin/路由/滚动/配置的确定性变化并精确标记 Vue 2、导航与 SSR 风险 |
| [`rewrite-okhttp-upgrade`](rewrite-okhttp-upgrade) | `com.huawei.clouds.openrewrite.okhttp.MigrateOkHttpTo5_3_0` | 严格升级 OkHttp，迁移 `OkHttpClient.clone()`，并标记 internal、Kotlin、MockWebServer、alpha 与连接行为风险 |
| [`rewrite-d3-upgrade`](rewrite-d3-upgrade) | `com.huawei.clouds.openrewrite.d3.MigrateD3To7_9_0` | 严格升级 D3 7.9.0，自动迁移 `histogram`/`bin` 与 `scan`/`leastIndex`，并标记 event、pointer、collections、Voronoi、ESM、格式及渲染语义风险 |
| [`rewrite-vue-i18n-upgrade`](rewrite-vue-i18n-upgrade) | `com.huawei.clouds.openrewrite.vuei18n.MigrateVueI18nTo11_3_0` | 严格升级 Vue I18n，自动迁移确定性的 options、plural 和 translation component，并精确标记 Vue 2、Legacy、message compiler、JIT 与 SSR 风险 |
| [`rewrite-kafka-clients-upgrade`](rewrite-kafka-clients-upgrade) | `com.huawei.clouds.openrewrite.kafka.MigrateKafkaClientsTo4_1_2` | 将 Kafka Clients 升级到 4.1.2，并迁移明确移除的 Admin API 与 JMX 配置键 |
| [`rewrite-jakarta-servlet-api-upgrade`](rewrite-jakarta-servlet-api-upgrade) | `com.huawei.clouds.openrewrite.jakartaservlet.MigrateJakartaServletApiTo6_1_0` | 将 Jakarta Servlet API 升级到 6.1.0，并迁移 javax namespace 与有明确替代的删除 API |
| [`rewrite-mybatis-spring-upgrade`](rewrite-mybatis-spring-upgrade) | `com.huawei.clouds.openrewrite.mybatisspring.MigrateMyBatisSpringTo4_0_0` | 严格升级 MyBatis-Spring 4.0.0，迁移 Batch 6 包、XSD、scanner XML 与确定性异常构造，并标记平台/API 风险 |
| [`rewrite-marked-upgrade`](rewrite-marked-upgrade) | `com.huawei.clouds.openrewrite.marked.MigrateMarkedTo17_0_6` | 严格升级 Marked 17.0.6，清理可证明冗余的类型依赖与 renderer 过渡开关，并标记 ESM、Node、扩展、异步、token 和 XSS 风险 |
| [`rewrite-ngx-echarts-upgrade`](rewrite-ngx-echarts-upgrade) | `com.huawei.clouds.openrewrite.ngxecharts.MigrateNgxEchartsTo20_0_2` | 严格升级 ngx-echarts，自动规范公开入口和旧模板输入，并精确标记 Angular、ECharts custom build、signal、SSR 与运行时风险 |
| [`rewrite-flyway-core-upgrade`](rewrite-flyway-core-upgrade) | `com.huawei.clouds.openrewrite.flyway.MigrateFlywayTo11_14_1` | 严格升级 Flyway Core/插件到 11.14.1，补齐确定的数据库模块并迁移配置、Java API、callback 文件；高风险数据库操作仅标记 |
| [`rewrite-date-fns-upgrade`](rewrite-date-fns-upgrade) | `com.huawei.clouds.openrewrite.datefns.MigrateDateFnsTo4_1_0` | 严格升级 date-fns 4.1.0，自动迁移确定性的命名导入/CommonJS/公开子路径，并标记 ESM、内部路径、区间、时区与 companion 风险 |
| [`rewrite-uuid-upgrade`](rewrite-uuid-upgrade) | `com.huawei.clouds.openrewrite.uuid.MigrateUuidTo13_0_2` | 严格升级 uuid 13.0.2，自动迁移旧 ESM subpath 与冗余类型依赖，并标记 CommonJS、Node、状态语义、buffer 和 React Native 风险 |
| [`rewrite-shedlock-spring-upgrade`](rewrite-shedlock-spring-upgrade) | `com.huawei.clouds.openrewrite.shedlockspring.MigrateShedLockSpringTo7_2_1` | 严格升级 ShedLock Spring 7.2.1，自动迁移旧 SchedulerLock 包名、duration 与 interceptMode，并标记 AOP、provider、异步及构建兼容风险 |
| [`rewrite-ng-dynamic-forms-core-upgrade`](rewrite-ng-dynamic-forms-core-upgrade) | `com.huawei.clouds.openrewrite.ngdynamicforms.MigrateNgDynamicFormsCoreTo18_0_0` | 严格升级 NG Dynamic Forms Core，自动移除可证明的无参数 forRoot wrapper，并精确标记 Angular 16、standalone renderer、service/model、模板和 Kendo 风险 |
| [`rewrite-tweenjs-upgrade`](rewrite-tweenjs-upgrade) | `com.huawei.clouds.openrewrite.tweenjs.MigrateTweenJsTo23_1_1` | 严格升级 Tween.js，自动归一化公开入口并删除可证明冗余的 main Group 参数，精确标记时钟、retarget、repeat/callback、Group、配置和部署风险 |
| [`rewrite-mssql-jdbc-upgrade`](rewrite-mssql-jdbc-upgrade) | `com.huawei.clouds.openrewrite.mssqljdbc.MigrateMssqlJdbcTo13_2_1Jre11` | 严格升级 SQL Server JDBC 到 13.2.1.jre11，迁移确定性 Entra 认证值，并标记 Java、TLS、超时、加密、vector、原生库和会话风险 |
| [`rewrite-react-router-dom-upgrade`](rewrite-react-router-dom-upgrade) | `com.huawei.clouds.openrewrite.reactrouterdom.MigrateReactRouterDomTo6_30_4` | 严格升级 React Router DOM，自动迁移确定性的类型包、NavLink 与 StaticRouter，并精确标记路由、导航、blocker、SSR 和 data-router 风险 |
| [`rewrite-lossless-json-upgrade`](rewrite-lossless-json-upgrade) | `com.huawei.clouds.openrewrite.losslessjson.MigrateLosslessJsonTo4_0_1` | 严格升级 lossless-json 4.0.1，安全删除冗余类型依赖，并精确标记 Node、模块、unknown、BigInt、解析与序列化风险 |
| [`rewrite-shedlock-jdbc-template-upgrade`](rewrite-shedlock-jdbc-template-upgrade) | `com.huawei.clouds.openrewrite.shedlockjdbc.MigrateShedLockJdbcTemplateTo7_2_1` | 严格升级 ShedLock JDBC provider，迁移 DatabaseProduct/UTC API，并标记 Java/Spring、数据库时间、事务、schema、异常和多 provider 风险 |
| [`rewrite-jakarta-annotation-api-upgrade`](rewrite-jakarta-annotation-api-upgrade) | `com.huawei.clouds.openrewrite.jakartaannotation.MigrateJakartaAnnotationApiTo3_0_0` | 严格升级 1.3.5→3.0.0，迁移 14 个类型，并定位 ManagedBean、Java/JPMS/OSGi、外部平台与反射字符串风险 |
| [`rewrite-kubernetes-client-java-upgrade`](rewrite-kubernetes-client-java-upgrade) | `com.huawei.clouds.openrewrite.kubernetesclientjava.MigrateKubernetesClientJavaTo7_3_1` | 严格迁移表格中的 Fabric8 Kubernetes Client 5.12 到 7.3.1，自动处理确定性 API/Mock 类型并标记 transport、DSL、模型和集群行为风险 |
| [`rewrite-jaxb-runtime-upgrade`](rewrite-jaxb-runtime-upgrade) | `com.huawei.clouds.openrewrite.jaxbruntime.MigrateJaxbRuntimeTo4_0_8` | 将 JAXB Runtime 2.3.7/2.3.8 升级到 4.0.8，迁移 Jakarta XML Binding/Activation、RI 扩展与 XJC binding 文件 |
| [`rewrite-i18next-upgrade`](rewrite-i18next-upgrade) | `com.huawei.clouds.openrewrite.i18next.MigrateI18nextTo25_10_10` | 严格升级 i18next，自动迁移无冲突的 initAsync 与英文 JSON v4 复数键，并精确标记类型、配置、locale、运行时和生态包风险 |
| [`rewrite-diagram-js-minimap-upgrade`](rewrite-diagram-js-minimap-upgrade) | `com.huawei.clouds.openrewrite.diagramjsminimap.MigrateDiagramJsMinimapTo5_2_0` | 严格升级 diagram-js-minimap，自动规范入口、重复注册和 Sass asset，并精确标记 diagram-js、触摸、DOM/SVG、多实例与构建风险 |
| [`rewrite-jetbrains-annotations-upgrade`](rewrite-jetbrains-annotations-upgrade) | `com.huawei.clouds.openrewrite.jetbrainsannotations.MigrateJetBrainsAnnotationsTo26_0_2_1` | 严格升级 org.jetbrains:annotations 23/24，并定位 Java 8、旧 artifact、混合 nullability、实验性默认契约与 KMP 风险 |
| [`rewrite-gridstack-upgrade`](rewrite-gridstack-upgrade) | `com.huawei.clouds.openrewrite.gridstack.MigrateGridStackTo12_3_3` | 严格升级 GridStack，自动迁移可证明的 import、API、配置、模板与样式，并精确标记渲染、拖拽、事件、序列化、nested grid、框架和 SSR 风险 |
| [`rewrite-react-dom-upgrade`](rewrite-react-dom-upgrade) | `com.huawei.clouds.openrewrite.reactdom.MigrateReactDomTo19_0_0` | 升级表格指定依赖，自动迁移可证明安全的 root、hydrate、act 与 ref 写法，并标记 SSR、测试、工具链和其余上下文风险 |
| [`rewrite-react-upgrade`](rewrite-react-upgrade) | `com.huawei.clouds.openrewrite.react.MigrateReactTo19_2_7` | 严格升级 React 19.2.7，自动迁移安全 root/act/ref 形态，并标记 renderer、JSX、类型、StrictMode、Suspense、SSR/RSC 风险 |
| [`rewrite-angular-gridster2-upgrade`](rewrite-angular-gridster2-upgrade) | `com.huawei.clouds.openrewrite.angulargridster2.MigrateAngularGridster2To20_2_4` | 将 angular-gridster2 12/13/16 升级到 20.2.4，自动迁移公开入口、strict-safe API、简单 control flow 与 standalone imports，并标记布局、交互、responsive、SSR 和私有 API 风险 |
| [`rewrite-netflix-eureka-client-upgrade`](rewrite-netflix-eureka-client-upgrade) | `com.huawei.clouds.openrewrite.netflixeureka.MigrateNetflixEurekaClientTo2_0_4` | 将原生 Netflix Eureka Client 1.10.18 升级到 2.0.4，并精准检测旧构造器、transport、Jersey/Guice、Jakarta namespace 与已删除依赖 |
| [`rewrite-spring-cloud-eureka-client-upgrade`](rewrite-spring-cloud-eureka-client-upgrade) | `com.huawei.clouds.openrewrite.springcloudeureka.MigrateEurekaClientTo4_2_0` | 将 Spring Cloud Netflix Eureka Client starter 2.1/3.1 升级到 4.2.0，删除废弃启用注解、标记 Ribbon 风险，并说明 Boot/Cloud release train、Java 17、Jakarta、配置、LoadBalancer 与 AOT 迁移边界 |
| [`rewrite-spring-data-elasticsearch-upgrade`](rewrite-spring-data-elasticsearch-upgrade) | `com.huawei.clouds.openrewrite.springdataelasticsearch.MigrateSpringDataElasticsearchTo6_0_5` | 将 Spring Data Elasticsearch 4.2/4.4 升级到 6.0.5，自动迁移确定性的查询、Range、completion 与 converter 类型，并标记旧客户端、ES 7 DSL、删除 API 和 repository 语义风险 |
| [`rewrite-curator-framework-upgrade`](rewrite-curator-framework-upgrade) | `com.huawei.clouds.openrewrite.curatorframework.MigrateCuratorFrameworkTo5_7_1` | 将 Apache Curator Framework 2.7.1 升级到 5.7.1，自动迁移 ListenerContainer 构造/类型，并精确标记已删除 API、旧 cache 与 ZooKeeper 3.4 风险 |
| [`rewrite-hikaricp-upgrade`](rewrite-hikaricp-upgrade) | `com.huawei.clouds.openrewrite.hikaricp.MigrateHikariCPTo6_3_3` | 将 HikariCP 3.3/3.4/4.0 升级到 6.3.3，自动迁移原子 Credentials 与旧 keepalive 默认值，并标记 Java、MXBean 和异常驱逐风险 |
| [`rewrite-ngx-infinite-scroll-upgrade`](rewrite-ngx-infinite-scroll-upgrade) | `com.huawei.clouds.openrewrite.ngxinfinitescroll.MigrateNgxInfiniteScrollTo17_0_1` | 将 ngx-infinite-scroll 9/10/13/14/16 升级到 17.0.1，自动迁移 deprecated NgModule 到 standalone directive，并标记 Angular peer、深度导入与滚动行为风险 |
| [`rewrite-jul-to-slf4j-upgrade`](rewrite-jul-to-slf4j-upgrade) | `com.huawei.clouds.openrewrite.jultoslf4j.MigrateJulToSlf4jTo2_0_17` | 将 JUL-to-SLF4J 1.7.30/1.7.32/1.7.36 升级到 2.0.17，迁移 provider/binder 兼容点，并检测双向桥和旧 binding 风险 |
| [`rewrite-swiper-upgrade`](rewrite-swiper-upgrade) | `com.huawei.clouds.openrewrite.swiper.MigrateSwiperTo12_1_2` | 将 Swiper 3/6/7/8/9 升级到 12.1.2，自动迁移可证明的包入口、模块 import、样式路径和容器类，并标记 framework/参数/loop/lazy/event 风险 |
| [`rewrite-tomcat-embed-core-upgrade`](rewrite-tomcat-embed-core-upgrade) | `com.huawei.clouds.openrewrite.tomcatembedcore.MigrateTomcatEmbedCoreTo10_1_57` | 将 38 个精确 Tomcat Embed Core 9.0/10.1 版本升级到 10.1.57；按升级前最近构建根和来源分支门控，复用官方 Jakarta、Servlet 6、EL 5 安全叶子，并标记返回类型、内部 API、协议、安全、集群与配置风险；Tomcat 11 和更高版本禁止降级 |
| [`rewrite-tomcat-catalina-upgrade`](rewrite-tomcat-catalina-upgrade) | `com.huawei.clouds.openrewrite.tomcatcatalina.MigrateTomcatCatalinaTo10_1_56` | 精确升级工作簿列出的 Tomcat Catalina 9.0/10.1 版本，自动迁移确定性的 Servlet/EL/Jakarta API 与配置，并标记 Java 11、内部 API、协议、集群和目标版本安全风险；所有更高版本禁止降级 |
| [`rewrite-spring-boot-upgrade`](rewrite-spring-boot-upgrade) | `com.huawei.clouds.openrewrite.springboot.MigrateSpringBootTo3_5_15` | 将 19 个精确 Spring Boot 版本升级到 3.5.15；升级前按最近构建根及原始 release 分段门控，复用 53 个官方源码叶子、官方配置和 auto-configuration 扫描能力，并定位 Java 17、Security、Actuator、生命周期、默认值与禁止降级风险 |
| [`rewrite-jetty-http-upgrade`](rewrite-jetty-http-upgrade) | `com.huawei.clouds.openrewrite.jettyhttp.MigrateJettyHttpTo12_0_34` | 将十个精确 Jetty HTTP 9.4/11.0/12.0 版本升级到 12.0.34；按最近构建根门控，复用官方 Java 17、方法和类型迁移叶子，并定位 HTTP content、header/URI、Handler、EE、配置及运行时风险；12.1.0 和更高版本禁止降级 |
| [`rewrite-spring-boot-starter-actuator-upgrade`](rewrite-spring-boot-starter-actuator-upgrade) | `com.huawei.clouds.openrewrite.springbootactuator.MigrateSpringBootActuatorTo3_5_15` | 精确升级 Spring Boot Actuator 到 3.5.15，自动迁移 Jakarta 和确定性 Actuator 配置，并定位 Java 17、endpoint access/exposure、health、security、Jackson 与 Micrometer 风险 |
| [`rewrite-netty-handler-upgrade`](rewrite-netty-handler-upgrade) | `com.huawei.clouds.openrewrite.nettyhandler.MigrateNettyHandlerTo4_1_136` | 精确升级工作簿和用户清单中的 Netty Handler 4.1 版本，直接复用官方参数迁移配方，自动迁移有源码证明的 deprecated API，并定位 TLS、SNI、ALPN、native、pipeline、timeout 与禁降级风险 |
| [`rewrite-spring-kafka-upgrade`](rewrite-spring-kafka-upgrade) | `com.huawei.clouds.openrewrite.springkafka.MigrateSpringKafkaTo3_3_15` | 将 Spring Kafka 2.8.11/2.9.5 精确升级到 3.3.15，直接复用官方 Future、error handler、Header 与测试工具迁移，并定位 Java 17、JSON、listener、事务/EOS、retry/DLT 和可观测性风险 |
| [`rewrite-spring-expression-upgrade`](rewrite-spring-expression-upgrade) | `com.huawei.clouds.openrewrite.springexpression.MigrateSpringExpressionTo6_2_19` | 将 17 个精确 Spring Expression 版本升级到 6.2.19，复用官方 Java 17/参数元数据构建配方，并定位 SpEL 信任边界、accessor/resolver、编译、内部 API 与操作上限风险 |
| [`rewrite-spring-web-upgrade`](rewrite-spring-web-upgrade) | `com.huawei.clouds.openrewrite.springweb.MigrateSpringWebTo6_2_19` | 将 14 个精确 Spring Web 版本升级到 6.2.19，直接复用官方 MediaType、ResponseStatusException 与 ClientHttpResponse 配方，并定位 Jakarta、HTTP client、URI、multipart、validation 和配置风险 |
| [`rewrite-logback-core-upgrade`](rewrite-logback-core-upgrade) | `com.huawei.clouds.openrewrite.logbackcore.MigrateLogbackCoreTo1_5_34` | 将 Logback Core 1.2.5/1.2.9 精确升级到 1.5.34，直接复用官方 core/XML 类型、方法和属性配方，并定位 Joran、rolling、DB、receiver、配置安全、JPMS/OSGi 和依赖族风险 |
| [`rewrite-zookeeper-upgrade`](rewrite-zookeeper-upgrade) | `com.huawei.clouds.openrewrite.zookeeper.MigrateZooKeeperTo3_8_6` | 将 5 个精确 ZooKeeper 低版本升级到 3.8.6，直接复用官方方法、类型和 Properties 配方，并定位持久化、滚动升级、Jute、TLS/SASL、审计、传输和嵌入式服务风险；3.9.x 禁止降级 |
| [`rewrite-spring-security-web-upgrade`](rewrite-spring-security-web-upgrade) | `com.huawei.clouds.openrewrite.springsecurityweb.MigrateSpringSecurityWebTo6_5_11` | 将 24 个精确 Spring Security Web 低版本升级到 6.5.11；按升级前最近构建根和源 release 门控，组合官方 5.7→6.2、Java 17 与 Jakarta Servlet 确定性能力，并定位授权、filter chain、session/context、CSRF、协议登录、SPI 和依赖族风险；7.x 禁止降级 |
| [`rewrite-spring-security-core-upgrade`](rewrite-spring-security-core-upgrade) | `com.huawei.clouds.openrewrite.springsecuritycore.MigrateSpringSecurityCoreTo6_5_11` | 将 7 个精确 Spring Security Core 版本升级到 6.5.11；按升级前最近构建根门控，直接复用官方密码编码器、方法安全、响应式方法安全和 `queryableText` 检测能力，并定位 Java 17、密码存储、认证授权、SecurityContext 与依赖族风险；任何高版本禁止降级 |
| [`rewrite-log4j-1-2-api-upgrade`](rewrite-log4j-1-2-api-upgrade) | `com.huawei.clouds.openrewrite.log4j12api.MigrateLog4j12ApiTo2_25_5` | 将六个精确 Log4j 1.2 API bridge 版本升级到 2.25.5，默认不擅自选择日志后端；直接拥有 Log4j Core 后可用 opt-in 组合官方源码叶子，并定位配置、旧 API、classpath、bridge 环和依赖族风险 |
| [`rewrite-spring-retry-upgrade`](rewrite-spring-retry-upgrade) | `com.huawei.clouds.openrewrite.springretry.MigrateSpringRetryTo2_0_13` | 将当前高优先级精确 Spring Retry 1.3.4 升级到 2.0.13；按升级前最近构建根门控，直接复用官方依赖、Java 17、注解属性和方法迁移配方，并定位 listener、表达式、stateful/cache、policy/backoff、AOP、指标及依赖族风险 |
| [`rewrite-junrar-upgrade`](rewrite-junrar-upgrade) | `com.huawei.clouds.openrewrite.junrar.MigrateJunrarTo7_5_10` | 将 Junrar 7.5.5/7.5.8 精确升级到 7.5.10；按升级前最近构建根门控，直接复用官方 `FindMethods` 和 `LatestRelease`，并定位路径穿越、自定义解包、RAR/stream、异常回滚、SLF4J 与打包风险；目标/表外版本 NOOP，未来版本禁止降级 |
| [`rewrite-elasticsearch-upgrade`](rewrite-elasticsearch-upgrade) | `com.huawei.clouds.openrewrite.elasticsearch.MigrateElasticsearchTo1_21_4` | 解析 bare `elasticsearch` 的版本分裂身份：仅将 `org.testcontainers:elasticsearch:1.17.6` 升级到 1.21.4，并复用官方 `ExplicitContainerImage`；Elasticsearch Server 7.x 只标记组件身份冲突，绝不跨坐标转换或降级 |

后续迁移应新增独立模块，例如：

```text
openrewrite-migration-recipes/
├── pom.xml
├── rewrite-fastjson-to-jackson-common/
├── rewrite-fastjson-to-jackson/
├── rewrite-fastjson2-to-jackson/
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
mvn -pl rewrite-fastjson2-to-jackson -am clean verify
```

具体配方能力与使用方法见各模块 README。

## Module conventions

- 一个独立迁移目标对应一个 Maven module。
- Java package 使用 `com.huawei.clouds.openrewrite.<domain>`。
- 迁移类 artifact ID 使用 `rewrite-<source>-to-<target>`，原地升级类使用 `rewrite-<software>-upgrade`。
- 每个模块独立声明公开 recipe；推荐入口使用 `Migrate...` 复合配方，组合严格版本升级、确定性源码/配置改写和精准风险检测。
- 实现前必须检索 OpenRewrite 官方 catalog 和源码；官方已支持的能力优先直接组合或复用官方 recipe/class，自定义代码只补严格版本边界、业务语义和官方未覆盖的缺口，并在模块 README 的“官方能力复用审计”中记录证据、取舍与组合测试。
- README 是迁移 spec：每个不兼容点必须映射到配方行为、自动化状态和测试；README 的说明不能代替配方实现。
- 仅修改依赖版本的 `Upgrade...` 可以作为底层配方，但不视为完整迁移模块；无法安全自动修复的点必须生成精确 `SearchResult`，并明确标注“检测”而非“已迁移”。
- 测试必须包含真实公开仓库固定 commit 的实际 before/after 或风险 marker，同时覆盖错误坐标、未列版本、已迁移状态和相似语法等安全回退。
- 同一迁移族的稳定公共能力放入 `-common` 内部模块，公开模块只保留版本入口和版本差异。

## License

Apache License 2.0。
