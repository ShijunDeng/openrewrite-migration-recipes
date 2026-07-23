package com.huawei.clouds.openrewrite.log4jcore;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Locate source-level Log4j Core behavior and integration decisions. */
public final class FindLog4jCore25SourceRisks extends Recipe {
    static final String PLUGIN_DISCOVERY =
            "Package scanning is deprecated and warns in Log4j 2.24+; compile custom @Plugin classes with " +
            "PluginProcessor, verify Log4j2Plugins.dat is packaged/merged, and add GraalVM metadata when native";
    static final String JMX =
            "Log4j JMX is disabled by default since 2.24; decide whether this management path is required and, if so, " +
            "set log4j2.disableJmx=false before Log4j initializes and test MBean registration/reconfiguration";
    static final String DATETIME =
            "FixedDateFormat/FastDateFormat are deprecated and 2.25 uses a new instant formatter; compare timestamps, " +
            "locale/time-zone behavior and n/x directives, or temporarily select log4j2.instantFormatter=legacy";
    static final String JNDI =
            "Modern Log4j disables each JNDI feature by default and permits only java: names; validate the trusted " +
            "resource and explicitly enable only the required lookup/JDBC/JMS/context-selector capability";
    static final String SCRIPT =
            "Script execution requires an explicit log4j2.scriptEnableLanguages allow-list; inventory engines, " +
            "sandbox untrusted configuration and test Script/ScriptFile/ScriptRef resolution";
    static final String SERIALIZATION =
            "Serialized LogEvent/ThrowableProxy payloads are implementation contracts; version or rebuild persisted " +
            "events and verify cross-version deserialization, stack traces, context data and custom messages";
    static final String PROPERTY =
            "This Log4j property is obsolete or deprecated: message lookups are not globally re-enabled and " +
            "loggerContextFactory is deprecated in favor of provider; choose an explicit supported policy instead";
    static final String LOOKUP =
            "Message/context lookup evaluation was hardened after 2.13; verify this explicit lookup cannot consume " +
            "untrusted message/MDC data, recurse, disclose secrets or rely on removed interpolation behavior";

    private static final Set<String> DATE_TYPES = Set.of(
            "org.apache.logging.log4j.core.util.datetime.FixedDateFormat",
            "org.apache.logging.log4j.core.util.datetime.FastDateFormat");
    private static final Set<String> JNDI_TYPES = Set.of(
            "org.apache.logging.log4j.core.lookup.JndiLookup",
            "org.apache.logging.log4j.core.net.JndiManager",
            "org.apache.logging.log4j.core.appender.db.jdbc.JndiConnectionSource",
            "org.apache.logging.log4j.core.appender.mom.JmsAppender");
    private static final Set<String> SCRIPT_TYPES = Set.of(
            "org.apache.logging.log4j.core.script.Script",
            "org.apache.logging.log4j.core.script.ScriptFile",
            "org.apache.logging.log4j.core.script.ScriptRef");
    private static final Set<String> CHANGED_PROPERTIES = Set.of(
            "log4j2.formatMsgNoLookups", "log4j.formatMsgNoLookups",
            "log4j2.loggerContextFactory");

    @Override
    public String getDisplayName() {
        return "Find Apache Log4j Core 2.25 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark custom plugin discovery, JMX, date formatting, JNDI, scripts, serialization and lookup/property behavior that requires application evidence.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return Log4jCoreSupport.generated(cu.getSourcePath()) ? cu :
                        super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String type = visited.getTypeName();
                if (type.startsWith("org.apache.logging.log4j.core.jmx.")) return mark(visited, JMX);
                if (DATE_TYPES.contains(type)) return mark(visited, DATETIME);
                if (JNDI_TYPES.contains(type)) return mark(visited, JNDI);
                if (SCRIPT_TYPES.contains(type)) return mark(visited, SCRIPT);
                return visited;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation visited = super.visitAnnotation(annotation, ctx);
                return TypeUtils.isOfClassType(
                        visited.getType(), "org.apache.logging.log4j.core.config.plugins.Plugin")
                        ? mark(visited, PLUGIN_DISCOVERY) : visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (isAny(visited.getType(), DATE_TYPES)) return mark(visited, DATETIME);
                if (isAny(visited.getType(), JNDI_TYPES)) return mark(visited, JNDI);
                if (isAny(visited.getType(), SCRIPT_TYPES)) return mark(visited, SCRIPT);
                if (TypeUtils.isOfClassType(
                        visited.getType(), "org.apache.logging.log4j.core.impl.ThrowableProxy")) {
                    return mark(visited, SERIALIZATION);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                JavaType owner = method == null ? null : method.getDeclaringType();
                String name = visited.getSimpleName();
                if (Set.of("addPackage", "addPackages").contains(name) &&
                    TypeUtils.isOfClassType(owner,
                            "org.apache.logging.log4j.core.config.plugins.util.PluginManager")) {
                    return mark(visited, PLUGIN_DISCOVERY);
                }
                if (fullyQualified(owner).startsWith("org.apache.logging.log4j.core.jmx.")) {
                    return mark(visited, JMX);
                }
                if (isAny(owner, DATE_TYPES)) return mark(visited, DATETIME);
                if (isAny(owner, JNDI_TYPES)) return mark(visited, JNDI);
                if (isAny(owner, SCRIPT_TYPES)) return mark(visited, SCRIPT);
                if (Set.of("serialize", "deserialize").contains(name) &&
                    TypeUtils.isOfClassType(owner,
                            "org.apache.logging.log4j.core.impl.Log4jLogEvent")) {
                    return mark(visited, SERIALIZATION);
                }
                if (TypeUtils.isOfClassType(owner, "java.lang.System") &&
                    Set.of("setProperty", "getProperty", "clearProperty").contains(name) &&
                    !visited.getArguments().isEmpty() &&
                    visited.getArguments().get(0) instanceof J.Literal literal &&
                    CHANGED_PROPERTIES.contains(literal.getValue())) {
                    return mark(visited, PROPERTY);
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String value)) return visited;
                return value.contains("%m{lookups}") || value.contains("%msg{lookups}") ||
                       value.contains("%message{lookups}") || value.contains("$${ctx:")
                        ? mark(visited, LOOKUP) : visited;
            }
        };
    }

    private static boolean isAny(JavaType type, Set<String> names) {
        return names.stream().anyMatch(name -> TypeUtils.isOfClassType(type, name));
    }

    private static String fullyQualified(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? "" : fq.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
