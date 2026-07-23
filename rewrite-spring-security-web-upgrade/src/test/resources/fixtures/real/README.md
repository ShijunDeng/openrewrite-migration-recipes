# 固定真实仓库用例

这些 fixture 从固定 commit 的真实 Java 仓库提取，只保留触发迁移边界所需的最小代码；测试不依赖默认分支的后续变化。

| fixture | 固定仓库与 commit | 原始文件 | 验证 |
|---|---|---|---|
| `eugenp-jjwt-security.java` | `eugenp/tutorials@5e4114a9482d68b6766ca738c087f0f9a87a7bd2` | `security-modules/jjwt/src/main/java/io/jsonwebtoken/jjwtfun/config/WebSecurityConfig.java` | `javax.servlet`、旧 CSRF/授权 DSL、自定义 `OncePerRequestFilter` 和过滤器顺序 |
| `eugenp-httpclient-security.java` | `eugenp/tutorials@5e4114a9482d68b6766ca738c087f0f9a87a7bd2` | `apache-httpclient4/src/main/java/com/baeldung/filter/CustomWebSecurityConfigurerAdapter.java` | `authorizeRequests`/`antMatchers`、Basic entry point 与 `addFilterAfter` |
| `spring-guide-security.java` | `spring-guides/gs-securing-web@299296be54569a14ef8e67b25f6193936385e6bf` | `complete/src/main/java/com/example/securingweb/WebSecurityConfig.java` | 已采用 lambda DSL 的 `SecurityFilterChain`、matcher 顺序、form login/logout 与用户存储审计 |
| `spring-security-sample-remember-me.java` | `spring-projects/spring-security-samples@472a9b7cb683e854bc9d9781875b2df72faad7a5` | `servlet/java-configuration/authentication/remember-me/src/main/java/example/SecurityConfiguration.java` | remember-me、filter chain、form login 与默认密码编码器边界 |

原仓库许可证分别适用；这里的最小摘录仅用于兼容性测试，并在每个文件中保留来源说明。
