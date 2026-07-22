package com.huawei.clouds.openrewrite.springcontextsupport;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Locale;
import java.util.Set;

/** Type- and format-aware markers for Spring Framework 6.0-6.2 Context Support migration decisions. */
public final class FindSpringContextSupport6MigrationRisks extends Recipe {
    private static final Set<String> MAIL_METHODS = Set.of(
            "setSession", "setJavaMailProperties", "setProtocol", "setDefaultEncoding", "setDefaultFileTypeMap"
    );
    private static final Set<String> QUARTZ_METHODS = Set.of(
            "setDataSource", "setNonTransactionalDataSource", "setTaskExecutor", "setQuartzProperties",
            "setOverwriteExistingJobs", "setWaitForJobsToCompleteOnShutdown", "setExposeSchedulerInRepository",
            "setApplicationContextSchedulerContextKey", "setJobFactory"
    );
    private static final Set<String> COMPATIBILITY_PROPERTIES = Set.of(
            "spring.cache.reactivestreams.ignore", "spring.locking.strict", "spring.expression.maxoperations"
    );
    private static final String EHCACHE_MESSAGE =
            "Spring 6 removed org.springframework.cache.ehcache and Ehcache 2 integration; choose Ehcache 3 through JCache or its native API and migrate configuration deliberately";
    private static final String PARAMETER_MESSAGE =
            "LocalVariableTableParameterNameDiscoverer was removed in Spring 6.1; use standard reflection and compile Java/Groovy/Kotlin with parameter metadata";
    private static final String REMOVED_MESSAGE =
            "Spring 6 removed legacy remoting or EJB integration; replace it with a supported protocol or direct JNDI design and add integration tests";
    private static final String ASYNC_MESSAGE =
            "Spring 6 enforces @Async return types; use void, Future/CompletableFuture, or redesign the asynchronous boundary";
    private static final String BEAN_MESSAGE =
            "Spring 6.2 rejects invalid @Configuration methods: @Bean must not return void or also declare @Autowired";
    private static final String MAIL_MESSAGE =
            "JavaMail integration detected; verify Jakarta Mail/Activation provider versions, Session properties, MIME encoding, attachments and transport failures";
    private static final String QUARTZ_MESSAGE =
            "Quartz integration detected; verify Quartz/Spring alignment, JobFactory injection, transaction/data-source ownership, lifecycle and shutdown semantics";
    private static final String CACHE_MESSAGE =
            "Caffeine reactive cache mode detected; Spring 6.1 caches produced Mono/Flux values asynchronously, so verify provider support and existing Publisher caching semantics";
    private static final String SPEL_MESSAGE =
            "Spring 6.2.19 limits SpEL evaluation to 10,000 operations by default; size and secure trusted expressions and configure maxOperations only when justified";
    private static final String PROPERTY_MESSAGE =
            "Spring compatibility switch or escaped placeholder detected; verify the Spring 6.1/6.2 parameter, cache, locking, placeholder and SpEL behavior before retaining it";

    @Override
    public String getDisplayName() {
        return "Find Spring Context Support 6.2 migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed Ehcache/remoting/parameter APIs, invalid async/bean methods, typed mail/Quartz/cache/SpEL code, and exact compatibility configuration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !UpgradeSelectedSpringContextSupportDependency
                        .isProjectPath(source.getSourcePath())) return tree;
                if (tree instanceof J.CompilationUnit java) return markJava(java, ctx);
                if (tree instanceof Properties.File properties) return markProperties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return markYaml(yaml, ctx);
                if (tree instanceof Xml.Document xml && !"pom.xml".equals(
                        source.getSourcePath().getFileName().toString())) return markXml(xml, ctx);
                return tree;
            }
        };
    }

    private static J.CompilationUnit markJava(J.CompilationUnit source, ExecutionContext ctx) {
        return (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext p) {
                J.Import visited = super.visitImport(anImport, p);
                String name = visited.getQualid().printTrimmed(getCursor());
                if (name.startsWith("org.springframework.cache.ehcache.")) return mark(visited, EHCACHE_MESSAGE);
                if (name.startsWith("org.springframework.core.LocalVariableTableParameterNameDiscoverer")) {
                    return mark(visited, PARAMETER_MESSAGE);
                }
                if (name.startsWith("org.springframework.remoting.") || name.startsWith("org.springframework.ejb.")) {
                    return mark(visited, REMOVED_MESSAGE);
                }
                return visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext p) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, p);
                if (hasAnnotation(visited, "org.springframework.scheduling.annotation.Async") &&
                    !validAsyncReturn(visited)) return mark(visited, ASYNC_MESSAGE);
                if (hasAnnotation(visited, "org.springframework.context.annotation.Bean") &&
                    (returnsVoid(visited) || hasAnnotation(visited, "org.springframework.beans.factory.annotation.Autowired"))) {
                    return mark(visited, BEAN_MESSAGE);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                String owner = owner(visited.getMethodType());
                if ("org.springframework.mail.javamail.JavaMailSenderImpl".equals(owner) &&
                    MAIL_METHODS.contains(visited.getSimpleName())) return mark(visited, MAIL_MESSAGE);
                if ("org.springframework.scheduling.quartz.SchedulerFactoryBean".equals(owner) &&
                    QUARTZ_METHODS.contains(visited.getSimpleName())) return mark(visited, QUARTZ_MESSAGE);
                if ("org.springframework.cache.caffeine.CaffeineCacheManager".equals(owner) &&
                    "setAsyncCacheMode".equals(visited.getSimpleName())) return mark(visited, CACHE_MESSAGE);
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext p) {
                J.NewClass visited = super.visitNewClass(newClass, p);
                String type = fqn(visited.getType());
                if ("org.springframework.expression.spel.standard.SpelExpressionParser".equals(type)) {
                    return mark(visited, SPEL_MESSAGE);
                }
                if ("org.springframework.mail.javamail.JavaMailSenderImpl".equals(type)) return mark(visited, MAIL_MESSAGE);
                if ("org.springframework.scheduling.quartz.SchedulerFactoryBean".equals(type)) return mark(visited, QUARTZ_MESSAGE);
                if (type.startsWith("org.springframework.cache.ehcache.")) return mark(visited, EHCACHE_MESSAGE);
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                J.Literal visited = super.visitLiteral(literal, p);
                if (!(visited.getValue() instanceof String value)) return visited;
                String risk = riskForText(value);
                return risk == null ? visited : mark(visited, risk);
            }
        }.visitNonNull(source, ctx);
    }

    private static Properties.File markProperties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext p) {
                Properties.Entry visited = super.visitEntry(entry, p);
                if (COMPATIBILITY_PROPERTIES.contains(visited.getKey().toLowerCase(Locale.ROOT))) {
                    return mark(visited, PROPERTY_MESSAGE);
                }
                String risk = riskForText(visited.getValue().getText());
                return risk == null ? visited : mark(visited, risk);
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents markYaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext p) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, p);
                String key = visited.getKey().getValue().toLowerCase(Locale.ROOT);
                if (COMPATIBILITY_PROPERTIES.contains(key)) return mark(visited, PROPERTY_MESSAGE);
                String value = visited.getValue() instanceof Yaml.Scalar scalar ? scalar.getValue() : "";
                String risk = riskForText(value);
                return risk == null ? visited : mark(visited, risk);
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document markXml(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext p) {
                Xml.CharData visited = super.visitCharData(charData, p);
                String risk = riskForText(visited.getText());
                return risk == null ? visited : mark(visited, risk);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext p) {
                Xml.Attribute visited = super.visitAttribute(attribute, p);
                String risk = riskForText(visited.getValueAsString());
                return risk == null ? visited : mark(visited, risk);
            }
        }.visitNonNull(source, ctx);
    }

    private static String riskForText(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("org.springframework.cache.ehcache.")) return EHCACHE_MESSAGE;
        if (lower.contains("org.springframework.core.localvariabletableparameternamediscoverer")) return PARAMETER_MESSAGE;
        if (lower.contains("org.springframework.remoting.") || lower.contains("org.springframework.ejb.")) return REMOVED_MESSAGE;
        if (lower.contains("org.springframework.mail.javamail.javamailsenderimpl")) return MAIL_MESSAGE;
        if (lower.contains("org.springframework.scheduling.quartz.schedulerfactorybean")) return QUARTZ_MESSAGE;
        if (lower.contains("spring.expression.maxoperations")) return SPEL_MESSAGE;
        if (lower.contains("spring.cache.reactivestreams.ignore") || lower.contains("spring.locking.strict") ||
            value.contains("\\${")) return PROPERTY_MESSAGE;
        return null;
    }

    private static boolean hasAnnotation(J.MethodDeclaration method, String target) {
        return method.getLeadingAnnotations().stream().anyMatch(annotation -> target.equals(fqn(annotation.getType())));
    }

    private static boolean validAsyncReturn(J.MethodDeclaration method) {
        if (returnsVoid(method)) return true;
        JavaType type = method.getReturnTypeExpression() == null ? null : method.getReturnTypeExpression().getType();
        return TypeUtils.isAssignableTo("java.util.concurrent.Future", type);
    }

    private static boolean returnsVoid(J.MethodDeclaration method) {
        return method.getReturnTypeExpression() != null &&
               method.getReturnTypeExpression().getType() == JavaType.Primitive.Void;
    }

    private static String owner(JavaType.Method method) {
        return method == null ? "" : fqn(method.getDeclaringType());
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
