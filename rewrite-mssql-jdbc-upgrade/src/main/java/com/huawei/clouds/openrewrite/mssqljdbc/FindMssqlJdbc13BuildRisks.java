package com.huawei.clouds.openrewrite.mssqljdbc;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Set;

/** Syntax-aware build markers for runtime components and the Java 11 baseline required by mssql-jdbc 13.2. */
public final class FindMssqlJdbc13BuildRisks extends Recipe {
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target"
    );
    private static final Set<String> OPTIONAL_COORDINATES = Set.of(
            "com.microsoft.sqlserver:mssql-jdbc_auth",
            "com.microsoft.azure:msal4j",
            "com.azure:azure-identity",
            "com.azure:azure-security-keyvault-keys",
            "com.azure:azure-keyvault",
            "com.google.code.gson:gson"
    );
    private static final String JAVA_MESSAGE =
            "mssql-jdbc 13.2.1.jre11 requires Java 11 or newer; align compiler, toolchain, CI, container and runtime JDKs";
    private static final String OPTIONAL_MESSAGE =
            "SQL Server JDBC optional runtime dependency detected; align it with the selected Entra, native authentication, Always Encrypted or vector feature and verify dependency convergence";
    private static final String DRIVER_OWNER_MESSAGE =
            "This mssql-jdbc version is externally managed, dynamic, ranged, unresolved, or outside the workbook selection; migrate its actual owner and verify the resolved artifact is 13.2.1.jre11";
    private static final String DRIVER_VARIANT_MESSAGE =
            "This classified or explicitly typed mssql-jdbc artifact is outside deterministic runtime upgrade scope; verify that 13.2.1.jre11 publishes the same artifact shape before migrating it";

    @Override
    public String getDisplayName() {
        return "Find Microsoft SQL Server JDBC 13.2 build risks";
    }

    @Override
    public String getDescription() {
        return "Mark Java baselines below 11 and exact optional runtime dependencies, only in projects that own an actual mssql-jdbc declaration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !UpgradeSelectedMssqlJdbcDependency.isProjectPath(source.getSourcePath())) return tree;
                String name = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document pom && "pom.xml".equals(name)) return markPom(pom, ctx);
                if (tree instanceof G.CompilationUnit groovy && name.endsWith(".gradle")) {
                    boolean standard = hasGroovyDriver(groovy, ctx);
                    return standard || hasAnyGroovyDriver(groovy, ctx) ? markGroovy(groovy, ctx, standard) : groovy;
                }
                if (tree instanceof K.CompilationUnit kotlin && name.endsWith(".gradle.kts")) {
                    boolean standard = hasKotlinDriver(kotlin, ctx);
                    return standard || hasAnyKotlinDriver(kotlin, ctx) ? markKotlin(kotlin, ctx, standard) : kotlin;
                }
                return tree;
            }
        };
    }

    private static Xml.Document markPom(Xml.Document document, ExecutionContext ctx) {
        boolean[] hasDriver = {false};
        boolean[] hasAnyDriver = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                if (UpgradeSelectedMssqlJdbcDependency.isDriverDependency(getCursor(), tag)) {
                    hasAnyDriver[0] = true;
                }
                if (isStandardDriver(getCursor(), tag)) hasDriver[0] = true;
                return hasDriver[0] && hasAnyDriver[0] ? tag : super.visitTag(tag, p);
            }
        }.visit(document, ctx);
        if (!hasAnyDriver[0]) return document;

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (UpgradeSelectedMssqlJdbcDependency.isDriverDependency(getCursor(), visited)) {
                    if (!isStandardDriver(getCursor(), visited)) return mark(visited, DRIVER_VARIANT_MESSAGE);
                    String version = visited.getChildValue("version").map(String::trim).orElse("");
                    if (!UpgradeSelectedMssqlJdbcDependency.TARGET.equals(version)) {
                        return markVersion(visited, DRIVER_OWNER_MESSAGE);
                    }
                }
                if (hasDriver[0] && UpgradeSelectedMssqlJdbcDependency.isProjectDependency(getCursor(), visited) &&
                    isOptionalRuntimeDependency(visited.getChildValue("groupId").orElse(""),
                            visited.getChildValue("artifactId").orElse(""))) {
                    return mark(visited, OPTIONAL_MESSAGE);
                }
                if (hasDriver[0] && isVisibleMavenJavaLevel(getCursor(), visited) &&
                    belowJava11(visited.getValue().orElse(""))) return mark(visited, JAVA_MESSAGE);
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit markGroovy(G.CompilationUnit source, ExecutionContext ctx,
                                                boolean standardDriver) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                return markGradleLiteral(super.visitLiteral(literal, p), getCursor(), standardDriver);
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext p) {
                J.FieldAccess visited = super.visitFieldAccess(fieldAccess, p);
                return standardDriver ? markJavaVersion(visited, getCursor()) : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                String driverMessage = mapDriverMessage(visited, getCursor());
                if (driverMessage != null) return mark(visited, driverMessage);
                if (standardDriver && isLegacyToolchainCall(visited, getCursor())) return mark(visited, JAVA_MESSAGE);
                return standardDriver && isOptionalMapDependency(visited, getCursor()) ?
                        mark(visited, OPTIONAL_MESSAGE) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit markKotlin(K.CompilationUnit source, ExecutionContext ctx,
                                                boolean standardDriver) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                return markGradleLiteral(super.visitLiteral(literal, p), getCursor(), standardDriver);
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext p) {
                J.FieldAccess visited = super.visitFieldAccess(fieldAccess, p);
                return standardDriver ? markJavaVersion(visited, getCursor()) : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                return standardDriver && isLegacyToolchainCall(visited, getCursor()) ?
                        mark(visited, JAVA_MESSAGE) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean hasGroovyDriver(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                if (isStandardGradleDriver(method, getCursor())) found[0] = true;
                return found[0] ? method : super.visitMethodInvocation(method, p);
            }
        }.visit(source, ctx);
        return found[0];
    }

    private static boolean hasKotlinDriver(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                if (isStandardGradleDriver(method, getCursor())) found[0] = true;
                return found[0] ? method : super.visitMethodInvocation(method, p);
            }
        }.visit(source, ctx);
        return found[0];
    }

    private static boolean hasAnyGroovyDriver(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                if (isAnyGradleDriver(method, getCursor())) found[0] = true;
                return found[0] ? method : super.visitMethodInvocation(method, p);
            }
        }.visit(source, ctx);
        return found[0];
    }

    private static boolean hasAnyKotlinDriver(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                if (isAnyGradleDriver(method, getCursor())) found[0] = true;
                return found[0] ? method : super.visitMethodInvocation(method, p);
            }
        }.visit(source, ctx);
        return found[0];
    }

    private static boolean isStandardDriver(Cursor cursor, Xml.Tag dependency) {
        return UpgradeSelectedMssqlJdbcDependency.isDriverDependency(cursor, dependency) &&
               dependency.getChild("classifier").isEmpty() && dependency.getChild("type").isEmpty();
    }

    private static boolean isStandardGradleDriver(J.MethodInvocation invocation, Cursor cursor) {
        if (!UpgradeSelectedMssqlJdbcDependency.isGradleDependencyInvocation(cursor, invocation)) return false;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && literal.getValue() instanceof String coordinate) {
                String[] parts = coordinate.split(":", -1);
                if ((parts.length == 2 || parts.length == 3) &&
                    UpgradeSelectedMssqlJdbcDependency.GROUP.equals(parts[0]) &&
                    UpgradeSelectedMssqlJdbcDependency.ARTIFACT.equals(parts[1])) return true;
            }
        }
        return UpgradeSelectedMssqlJdbcDependency.GROUP.equals(mapValue(invocation, "group")) &&
               UpgradeSelectedMssqlJdbcDependency.ARTIFACT.equals(mapValue(invocation, "name")) &&
               !hasMapKey(invocation, "classifier") && !hasMapKey(invocation, "ext") &&
               !hasMapKey(invocation, "type") && !hasMapKey(invocation, "variant");
    }

    private static boolean isAnyGradleDriver(J.MethodInvocation invocation, Cursor cursor) {
        if (!UpgradeSelectedMssqlJdbcDependency.isGradleDependencyInvocation(cursor, invocation)) return false;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && literal.getValue() instanceof String coordinate) {
                String[] parts = coordinate.split(":", -1);
                if (parts.length >= 2 && UpgradeSelectedMssqlJdbcDependency.GROUP.equals(parts[0]) &&
                    UpgradeSelectedMssqlJdbcDependency.ARTIFACT.equals(parts[1])) return true;
            }
        }
        return UpgradeSelectedMssqlJdbcDependency.GROUP.equals(mapValue(invocation, "group")) &&
               UpgradeSelectedMssqlJdbcDependency.ARTIFACT.equals(mapValue(invocation, "name"));
    }

    private static boolean isVisibleMavenJavaLevel(Cursor cursor, Xml.Tag tag) {
        if (UpgradeSelectedMssqlJdbcDependency.isRootProperty(cursor, tag) &&
            JAVA_PROPERTIES.contains(tag.getName())) return true;
        if (!Set.of("source", "target", "release").contains(tag.getName())) return false;
        return isOwnedCompilerPlugin(cursor);
    }

    private static boolean isOptionalRuntimeDependency(String group, String artifact) {
        return OPTIONAL_COORDINATES.contains(group + ":" + artifact) ||
               "org.bouncycastle".equals(group) && artifact.startsWith("bcprov-");
    }

    private static boolean isOptionalMapDependency(J.MethodInvocation invocation, Cursor cursor) {
        return UpgradeSelectedMssqlJdbcDependency.isGradleDependencyInvocation(cursor, invocation) &&
               isOptionalRuntimeDependency(mapValue(invocation, "group"), mapValue(invocation, "name"));
    }

    private static J.Literal markGradleLiteral(J.Literal literal, Cursor cursor, boolean standardDriver) {
        Object value = literal.getValue();
        if (value instanceof String string) {
            Cursor parent = cursor.getParentTreeCursor();
            if (parent.getValue() instanceof J.MethodInvocation invocation &&
                UpgradeSelectedMssqlJdbcDependency.isGradleDependencyInvocation(parent, invocation)) {
                String driverMessage = coordinateDriverMessage(string);
                if (driverMessage != null) return mark(literal, driverMessage);
                String[] parts = string.split(":", -1);
                if (standardDriver && parts.length >= 2 && isOptionalRuntimeDependency(parts[0], parts[1])) {
                    return mark(literal, OPTIONAL_MESSAGE);
                }
            }
        }
        Cursor parent = cursor.getParentTreeCursor();
        if (standardDriver && parent.getValue() instanceof J.Assignment assignment &&
            isJavaCompatibility(assignment.getVariable().printTrimmed()) && belowJava11(String.valueOf(value))) {
            return mark(literal, JAVA_MESSAGE);
        }
        return literal;
    }

    private static String coordinateDriverMessage(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2 || !UpgradeSelectedMssqlJdbcDependency.GROUP.equals(parts[0]) ||
            !UpgradeSelectedMssqlJdbcDependency.ARTIFACT.equals(parts[1])) return null;
        if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return DRIVER_VARIANT_MESSAGE;
        return parts.length == 3 && UpgradeSelectedMssqlJdbcDependency.TARGET.equals(parts[2]) ?
                null : DRIVER_OWNER_MESSAGE;
    }

    private static String mapDriverMessage(J.MethodInvocation invocation, Cursor cursor) {
        if (!UpgradeSelectedMssqlJdbcDependency.isGradleDependencyInvocation(cursor, invocation) ||
            !UpgradeSelectedMssqlJdbcDependency.GROUP.equals(mapValue(invocation, "group")) ||
            !UpgradeSelectedMssqlJdbcDependency.ARTIFACT.equals(mapValue(invocation, "name"))) return null;
        if (hasMapKey(invocation, "classifier") || hasMapKey(invocation, "ext") ||
            hasMapKey(invocation, "type") || hasMapKey(invocation, "variant")) return DRIVER_VARIANT_MESSAGE;
        return UpgradeSelectedMssqlJdbcDependency.TARGET.equals(mapValue(invocation, "version")) ?
                null : DRIVER_OWNER_MESSAGE;
    }

    private static Xml.Tag markVersion(Xml.Tag dependency, String message) {
        return dependency.getChild("version").map(version -> {
            Xml.Tag marked = mark(version, message);
            return dependency.withContent(dependency.getContent().stream()
                    .map(content -> content == version ? marked : content).toList());
        }).orElseGet(() -> mark(dependency, message));
    }

    private static boolean isOwnedCompilerPlugin(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (!(current.getValue() instanceof Xml.Tag plugin) || !"plugin".equals(plugin.getName())) continue;
            if (!"maven-compiler-plugin".equals(plugin.getChildValue("artifactId").orElse(null)) ||
                !Set.of("", "org.apache.maven.plugins").contains(plugin.getChildValue("groupId").orElse(""))) {
                return false;
            }
            Cursor plugins = current.getParentTreeCursor();
            if (!(plugins.getValue() instanceof Xml.Tag pluginsTag) || !"plugins".equals(pluginsTag.getName())) {
                return false;
            }
            Cursor owner = plugins.getParentTreeCursor();
            if (owner.getValue() instanceof Xml.Tag ownerTag && "pluginManagement".equals(ownerTag.getName())) {
                owner = owner.getParentTreeCursor();
            }
            if (!(owner.getValue() instanceof Xml.Tag build) || !"build".equals(build.getName())) return false;
            Cursor buildOwner = owner.getParentTreeCursor();
            return UpgradeSelectedMssqlJdbcDependency.isProjectOwner(buildOwner) ||
                   UpgradeSelectedMssqlJdbcDependency.isProfileOwner(buildOwner);
        }
        return false;
    }

    private static J.FieldAccess markJavaVersion(J.FieldAccess fieldAccess, Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        String value = fieldAccess.printTrimmed();
        if (parent.getValue() instanceof J.Assignment assignment &&
            isJavaCompatibility(assignment.getVariable().printTrimmed()) &&
            value.startsWith("JavaVersion.VERSION_") &&
            belowJava11(value.substring("JavaVersion.VERSION_".length()))) return mark(fieldAccess, JAVA_MESSAGE);
        return fieldAccess;
    }

    private static boolean isLegacyToolchainCall(J.MethodInvocation invocation, Cursor cursor) {
        if (!"of".equals(invocation.getSimpleName()) || invocation.getSelect() == null ||
            !"JavaLanguageVersion".equals(invocation.getSelect().printTrimmed()) ||
            invocation.getArguments().size() != 1 ||
            !(invocation.getArguments().get(0) instanceof J.Literal literal) ||
            !belowJava11(String.valueOf(literal.getValue()))) return false;
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.Assignment assignment &&
               assignment.getVariable().printTrimmed().endsWith("languageVersion");
    }

    private static boolean isJavaCompatibility(String value) {
        return value.equals("sourceCompatibility") || value.equals("targetCompatibility") ||
               value.endsWith(".sourceCompatibility") || value.endsWith(".targetCompatibility") ||
               value.endsWith("languageVersion");
    }

    private static boolean belowJava11(String value) {
        String normalized = value.trim().replace("VERSION_", "");
        if (normalized.startsWith("1.")) normalized = normalized.substring(2);
        try {
            return Integer.parseInt(normalized.replaceAll("[^0-9].*", "")) < 11;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean hasMapKey(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof G.MapEntry entry && key.equals(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && map.getElements().stream()
                        .anyMatch(entry -> key.equals(mapKey(entry))));
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        for (J argument : invocation.getArguments()) {
            if (argument instanceof G.MapEntry entry && key.equals(mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal && literal.getValue() instanceof String value) return value;
            if (argument instanceof G.MapLiteral map) {
                for (G.MapEntry entry : map.getElements()) {
                    if (key.equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal &&
                        literal.getValue() instanceof String value) return value;
                }
            }
        }
        return null;
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
