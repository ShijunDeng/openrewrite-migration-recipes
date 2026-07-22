package com.huawei.clouds.openrewrite.shedlockjdbc;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Type- and syntax-aware review markers for the ShedLock JdbcTemplate 7.2.1 migration. */
public final class FindShedLockJdbcTemplate7MigrationRisks extends Recipe {
    private static final String SHEDLOCK = "net.javacrumbs.shedlock.";
    private static final String LOCK_PROVIDER = "net.javacrumbs.shedlock.core.LockProvider";
    private static final Set<String> SCHEMA_METHODS = Set.of(
            "withTableName", "withColumnNames", "withDbUpperCase", "withDatabaseProduct"
    );
    private static final Set<String> TRANSACTION_METHODS = Set.of("withTransactionManager", "withIsolationLevel");
    private static final Set<String> LOCK_METHODS = Set.of("lock", "unlock", "extend");
    private static final Pattern SHEDLOCK_SCHEMA = Pattern.compile(
            "(?is)\\b(?:create|alter)\\s+table\\s+[^;]*shedlock|\\b(?:lock_until|locked_at|locked_by)\\b");
    private static final Pattern JAVA_LEVEL = Pattern.compile("(?:1\\.)?(\\d+)");

    @Override
    public String getDisplayName() {
        return "Find ShedLock JdbcTemplate 7.2.1 migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark Java/Spring baselines, provider alignment, database time, timezone, transaction/isolation, " +
               "schema, LockException, and multiple-provider annotation semantics that require a runtime decision.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                if (tree instanceof G.CompilationUnit groovy) {
                    return gradleGroovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin) {
                    return gradleKotlin(kotlin, ctx);
                }
                if (tree instanceof J.CompilationUnit java) {
                    return javaRisks(java, ctx);
                }
                if (tree instanceof Xml.Document xml) {
                    return xmlRisks(xml, ctx);
                }
                if (tree instanceof Yaml.Documents yaml) {
                    return yamlRisks(yaml, ctx);
                }
                if (tree instanceof PlainText text) {
                    return textRisks(text);
                }
                return source;
            }
        };
    }

    private static J.CompilationUnit javaRisks(J.CompilationUnit source, ExecutionContext ctx) {
        long providerBeans = source.getClasses().stream()
                .flatMap(type -> type.getBody().getStatements().stream())
                .filter(J.MethodDeclaration.class::isInstance).map(J.MethodDeclaration.class::cast)
                .filter(FindShedLockJdbcTemplate7MigrationRisks::returnsLockProvider).count();
        boolean multipleProviders = providerBeans > 1;

        return (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext executionContext) {
                J.Import i = super.visitImport(anImport, executionContext);
                String name = i.getQualid().printTrimmed(getCursor());
                if (name.startsWith("javax.annotation.") || name.startsWith("javax.persistence.") ||
                    name.startsWith("javax.validation.")) {
                    return SearchResult.found(i,
                            "ShedLock 7 requires Java 17 and Spring 6.2/7; verify the Jakarta namespace and framework baseline before compiling");
                }
                return i;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);
                if (multipleProviders && returnsLockProvider(m)) {
                    return SearchResult.found(m,
                            "Multiple LockProvider definitions detected; bind scheduled methods/classes explicitly with @LockProviderToUse and test provider selection");
                }
                return m;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext executionContext) {
                J.NewClass n = super.visitNewClass(newClass, executionContext);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(n.getType());
                if (type != null && type.getFullyQualifiedName().equals(
                        "net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider")) {
                    return SearchResult.found(n,
                            "Retest JdbcTemplate provider construction against Java 17/Spring 6.2+, database dialect, schema, UTC policy and transaction behavior");
                }
                return n;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext executionContext) {
                J.Annotation a = super.visitAnnotation(annotation, executionContext);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(a.getType());
                String fqn = type == null ? "" : type.getFullyQualifiedName();
                if (fqn.endsWith(".SchedulerLock")) {
                    return SearchResult.found(a,
                            "Verify lock name/durations and Spring proxy semantics; final/non-public/self-invoked methods and reactive scheduling require integration tests");
                }
                if (fqn.endsWith(".EnableSchedulerLock")) {
                    return SearchResult.found(a,
                            "Verify default lock durations and the Spring 6.2+/7 proxy mode after the ShedLock 7 upgrade");
                }
                if (fqn.endsWith(".LockProviderToUse")) {
                    return SearchResult.found(a,
                            "Verify the named LockProvider exists and is selected for every scheduled path; missing selection fails when multiple providers are present");
                }
                return a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                JavaType.Method methodType = m.getMethodType();
                JavaType.FullyQualified owner = methodType == null ? null :
                        TypeUtils.asFullyQualified(methodType.getDeclaringType());
                String fqn = owner == null ? "" : owner.getFullyQualifiedName();
                String name = m.getSimpleName();
                if (!fqn.startsWith(SHEDLOCK)) {
                    return m;
                }
                if ("usingDbTime".equals(name)) {
                    return SearchResult.found(m,
                            "usingDbTime is database-specific and strongly preferred for clock consistency; verify the selected dialect, privileges and DB UTC behavior");
                }
                if ("withTimeZone".equals(name)) {
                    return SearchResult.found(m,
                            "Non-literal or non-UTC withTimeZone requires a deliberate timezone review; forceUtcTimeZone is the 7.2.1 replacement for UTC");
                }
                if (TRANSACTION_METHODS.contains(name)) {
                    return SearchResult.found(m,
                            "Retest ShedLock REQUIRES_NEW transaction boundaries, configured isolation, pool capacity and rollback/deadlock behavior");
                }
                if (SCHEMA_METHODS.contains(name)) {
                    return SearchResult.found(m,
                            "Custom ShedLock table/columns/case/database product detected; validate identifier quoting, schema migration and timestamp precision on the target database");
                }
                if (LOCK_METHODS.contains(name)) {
                    return SearchResult.found(m,
                            "ShedLock 7 providers throw LockException on unexpected failures; distinguish provider errors from an unavailable lock and review retry/catch behavior");
                }
                if (name.startsWith("assert") && fqn.contains("LockAssert")) {
                    return SearchResult.found(m,
                            "Retest lock assertions through the configured Spring proxy; self-invocation or an unproxied method can bypass locking");
                }
                return m;
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document xmlRisks(Xml.Document source, ExecutionContext ctx) {
        boolean pom = source.getSourcePath().getFileName().toString().equals("pom.xml");
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (!pom) {
                    String value = t.getValue().orElse("");
                    boolean shedLockAttribute = t.getAttributes().stream()
                            .anyMatch(attribute -> attribute.getValueAsString().contains("net.javacrumbs.shedlock"));
                    if (value.contains("net.javacrumbs.shedlock") || shedLockAttribute) {
                        return SearchResult.found(t,
                                "ShedLock Spring XML configuration has been unsupported since 3.x; replace it with Java configuration before adopting 7.2.1");
                    }
                    return t;
                }
                if (isJavaVersionTag(t) && t.getValue().filter(FindShedLockJdbcTemplate7MigrationRisks::isBelowJava17).isPresent()) {
                    return SearchResult.found(t, "ShedLock 7 requires Java 17 or newer");
                }
                if ("spring-boot.version".equals(t.getName()) &&
                    t.getValue().filter(FindShedLockJdbcTemplate7MigrationRisks::isSpringBootBelow34).isPresent()) {
                    return SearchResult.found(t,
                            "ShedLock 7 supports Spring Boot 3.4/3.5/4.x and Spring Framework 6.2/7; upgrade the framework baseline deliberately");
                }
                if ("dependency".equals(t.getName()) &&
                    "net.javacrumbs.shedlock".equals(t.getChildValue("groupId").orElse(null))) {
                    String artifact = t.getChildValue("artifactId").orElse("");
                    String version = t.getChildValue("version").orElse("");
                    if (!"shedlock-provider-jdbc-template".equals(artifact) &&
                        (artifact.equals("shedlock-core") || artifact.equals("shedlock-spring") ||
                         artifact.equals("shedlock-bom") || artifact.startsWith("shedlock-provider-")) &&
                        !"7.2.1".equals(version)) {
                        return SearchResult.found(t,
                                "Align ShedLock core, Spring integration, BOM and all providers to one compatible 7.2.1 line; shared properties and external management are not changed automatically");
                    }
                }
                if ("dependency".equals(t.getName()) &&
                    "org.springframework".equals(t.getChildValue("groupId").orElse(null)) &&
                    isSpringFrameworkBelow62(t.getChildValue("version").orElse(""))) {
                    return SearchResult.found(t,
                            "ShedLock 7 requires the Spring Framework 6.2/7 line; align Spring JDBC, transactions, context and Boot management together");
                }
                if ("parent".equals(t.getName()) && "org.springframework.boot".equals(t.getChildValue("groupId").orElse(null)) &&
                    isSpringBootBelow34(t.getChildValue("version").orElse(""))) {
                    return SearchResult.found(t,
                            "ShedLock 7 supports Spring Boot 3.4/3.5/4.x and Spring Framework 6.2/7; upgrade the framework baseline deliberately");
                }
                return t;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit gradleGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment a = super.visitAssignment(assignment, executionContext);
                return isLegacyGradleJavaAssignment(a, getCursor())
                        ? SearchResult.found(a, "ShedLock 7 requires a Java 17+ Gradle toolchain and runtime") : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                return isLegacyToolchainCall(m)
                        ? SearchResult.found(m, "ShedLock 7 requires a Java 17+ Gradle toolchain and runtime") : m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                return markGradleLiteral(super.visitLiteral(literal, executionContext));
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit gradleKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment a = super.visitAssignment(assignment, executionContext);
                return isLegacyGradleJavaAssignment(a, getCursor())
                        ? SearchResult.found(a, "ShedLock 7 requires a Java 17+ Gradle toolchain and runtime") : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                return isLegacyToolchainCall(m)
                        ? SearchResult.found(m, "ShedLock 7 requires a Java 17+ Gradle toolchain and runtime") : m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                return markGradleLiteral(super.visitLiteral(literal, executionContext));
            }
        }.visitNonNull(source, ctx);
    }

    private static J.Literal markGradleLiteral(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) {
            return literal;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("net.javacrumbs.shedlock:shedlock-") &&
            !lower.startsWith("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.2.1") &&
            !lower.endsWith(":7.2.1")) {
            return SearchResult.found(literal,
                    "Align every ShedLock core/Spring/provider declaration to 7.2.1; dynamic catalogs and shared variables are not changed automatically");
        }
        return literal;
    }

    private static PlainText textRisks(PlainText text) {
        String value = text.getText();
        String lowerPath = text.getSourcePath().toString().toLowerCase(Locale.ROOT);
        if ((lowerPath.endsWith(".sql") || lowerPath.contains("migration")) && SHEDLOCK_SCHEMA.matcher(value).find()) {
            return SearchResult.found(text,
                    "ShedLock schema detected; validate primary key width, timestamp precision/timezone, table/column names and privileges against the target database");
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if ((lowerPath.endsWith(".yml") || lowerPath.endsWith(".yaml") || lowerPath.endsWith(".properties")) &&
            lower.contains("shedlock") && (lower.contains("timezone") || lower.contains("time-zone") || lower.contains("table"))) {
            return SearchResult.found(text,
                    "ShedLock table/timezone configuration detected; verify it matches usingDbTime/forceUtcTimeZone and the deployed schema");
        }
        return text;
    }

    private static Yaml.Documents yamlRisks(Yaml.Documents source, ExecutionContext ctx) {
        if (!source.printAll().toLowerCase(Locale.ROOT).contains("shedlock")) {
            return source;
        }
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext executionContext) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, executionContext);
                String key = e.getKey().getValue().toLowerCase(Locale.ROOT);
                if (key.contains("shedlock") || key.contains("timezone") || key.contains("time-zone") ||
                    key.equals("table") || key.equals("table-name")) {
                    return SearchResult.found(e,
                            "Verify ShedLock table/timezone configuration matches usingDbTime/forceUtcTimeZone and the deployed schema");
                }
                return e;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean returnsLockProvider(J.MethodDeclaration method) {
        return method.getReturnTypeExpression() != null &&
               TypeUtils.isAssignableTo(LOCK_PROVIDER, method.getReturnTypeExpression().getType());
    }

    private static boolean isJavaVersionTag(Xml.Tag tag) {
        return Set.of("java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target")
                .contains(tag.getName());
    }

    private static boolean isBelowJava17(String value) {
        Matcher matcher = JAVA_LEVEL.matcher(value.trim());
        return matcher.matches() && Integer.parseInt(matcher.group(1)) < 17;
    }

    private static boolean isSpringBootBelow34(String version) {
        Matcher matcher = Pattern.compile("(\\d+)[.](\\d+).*?").matcher(version);
        if (!matcher.matches()) {
            return false;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        return major < 3 || major == 3 && minor < 4;
    }

    private static boolean isSpringFrameworkBelow62(String version) {
        Matcher matcher = Pattern.compile("(\\d+)[.](\\d+).*?").matcher(version);
        if (!matcher.matches()) {
            return false;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        return major < 6 || major == 6 && minor < 2;
    }

    private static boolean isLegacyToolchainCall(J.MethodInvocation method) {
        if (!"of".equals(method.getSimpleName()) || method.getArguments().size() != 1 ||
            !(method.getArguments().get(0) instanceof J.Literal literal) || !(literal.getValue() instanceof Number number)) {
            return false;
        }
        return number.intValue() < 17 && method.getSelect() != null &&
               method.getSelect().printTrimmed().endsWith("JavaLanguageVersion");
    }

    private static boolean isLegacyGradleJavaAssignment(J.Assignment assignment, org.openrewrite.Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) {
            return false;
        }
        String value = assignment.getAssignment().printTrimmed(cursor);
        Matcher numeric = JAVA_LEVEL.matcher(value.replace("'", "").replace("\"", ""));
        if (numeric.matches()) {
            return Integer.parseInt(numeric.group(1)) < 17;
        }
        Matcher constant = Pattern.compile("(?:JavaVersion[.])?VERSION_(?:1_)?(\\d+)").matcher(value);
        return constant.matches() && Integer.parseInt(constant.group(1)) < 17;
    }
}
