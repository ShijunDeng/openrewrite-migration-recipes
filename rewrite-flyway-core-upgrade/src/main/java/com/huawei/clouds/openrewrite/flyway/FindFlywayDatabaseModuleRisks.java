package com.huawei.clouds.openrewrite.flyway;

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
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Marks JDBC drivers and URLs whose Flyway 11 database companion is absent. */
public final class FindFlywayDatabaseModuleRisks extends Recipe {
    private static final Map<String, String> DRIVERS = new LinkedHashMap<>();
    private static final Map<String, String> URLS = new LinkedHashMap<>();

    static {
        DRIVERS.put("org.postgresql:postgresql", "flyway-database-postgresql");
        DRIVERS.put("com.mysql:mysql-connector-j", "flyway-mysql");
        DRIVERS.put("mysql:mysql-connector-java", "flyway-mysql");
        DRIVERS.put("org.mariadb.jdbc:mariadb-java-client", "flyway-mysql");
        DRIVERS.put("com.microsoft.sqlserver:mssql-jdbc", "flyway-sqlserver");
        DRIVERS.put("com.oracle.database.jdbc:ojdbc8", "flyway-database-oracle");
        DRIVERS.put("com.oracle.database.jdbc:ojdbc11", "flyway-database-oracle");
        DRIVERS.put("com.oracle:ojdbc8", "flyway-database-oracle");
        DRIVERS.put("com.ibm.db2:jcc", "flyway-database-db2");
        URLS.put("jdbc:postgresql:", "flyway-database-postgresql");
        URLS.put("jdbc:mysql:", "flyway-mysql");
        URLS.put("jdbc:mariadb:", "flyway-mysql");
        URLS.put("jdbc:sqlserver:", "flyway-sqlserver");
        URLS.put("jdbc:oracle:", "flyway-database-oracle");
        URLS.put("jdbc:db2:", "flyway-database-db2");
    }

    @Override
    public String getDisplayName() { return "Find missing Flyway database modules"; }

    @Override
    public String getDescription() {
        return "Mark JDBC drivers in the same owned build as Flyway Core, and exact Flyway URL properties, when the corresponding Flyway 11 companion is absent.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !FlywayVersions.isProjectPath(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) return markPom(document, ctx);
                if (tree instanceof G.CompilationUnit cu && fileName.endsWith(".gradle")) return markGroovy(cu, ctx);
                if (tree instanceof K.CompilationUnit cu && fileName.endsWith(".gradle.kts")) return markKotlin(cu, ctx);
                if (tree instanceof Properties.File file) return markProperties(file, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document markPom(Xml.Document document, ExecutionContext ctx) {
        Xml.Tag dependencies = document.getRoot().getChild("dependencies").orElse(null);
        if (dependencies == null) return document;
        java.util.List<Xml.Tag> direct = dependencies.getChildren().stream()
                .filter(tag -> "dependency".equals(tag.getName())).toList();
        boolean ownsCore = direct.stream().anyMatch(tag -> FlywayVersions.hasCoreCoordinates(tag) && FlywayVersions.isStandardJar(tag));
        if (!ownsCore) return document;
        Set<String> modules = direct.stream().filter(tag -> FlywayVersions.GROUP.equals(tag.getChildValue("groupId").orElse(null)))
                .map(tag -> tag.getChildValue("artifactId").orElse("")).collect(java.util.stream.Collectors.toSet());
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (!FlywayVersions.isMavenDependencyBlock(getCursor(), visited) || !FlywayVersions.isStandardJar(visited) ||
                    !(getCursor().getParentTreeCursor().getParentTreeCursor().getValue() instanceof Xml.Tag owner) ||
                    !"project".equals(owner.getName())) return visited;
                String module = DRIVERS.get(coordinate(visited));
                return module != null && !modules.contains(module) ? mark(visited, message(module)) : visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit markGroovy(G.CompilationUnit cu, ExecutionContext ctx) {
        Set<String> modules = new java.util.HashSet<>();
        boolean[] core = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                collectBuildOwner(getCursor(), method, modules, core);
                return super.visitMethodInvocation(method, p);
            }
        }.visitNonNull(cu, ctx);
        if (!core[0]) return cu;
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                return markMapDriver(getCursor(), visited, modules);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                boolean direct = FlywayVersions.isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, p);
                return direct ? markDriver(visited, modules) : visited;
            }
        }.visitNonNull(cu, ctx);
    }

    private static K.CompilationUnit markKotlin(K.CompilationUnit cu, ExecutionContext ctx) {
        Set<String> modules = new java.util.HashSet<>();
        boolean[] core = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                collectBuildOwner(getCursor(), method, modules, core);
                return super.visitMethodInvocation(method, p);
            }
        }.visitNonNull(cu, ctx);
        if (!core[0]) return cu;
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                return markMapDriver(getCursor(), visited, modules);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                boolean direct = FlywayVersions.isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, p);
                return direct ? markDriver(visited, modules) : visited;
            }
        }.visitNonNull(cu, ctx);
    }

    private static Properties.File markProperties(Properties.File file, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext p) {
                Properties.Entry visited = super.visitEntry(entry, p);
                if (!("flyway.url".equals(visited.getKey()) || "spring.flyway.url".equals(visited.getKey()))) return visited;
                String value = visited.getValue().getText().trim();
                String module = URLS.entrySet().stream().filter(candidate -> value.startsWith(candidate.getKey()))
                        .map(Map.Entry::getValue).findFirst().orElse(null);
                return module == null ? visited : mark(visited, message(module));
            }
        }.visitNonNull(file, ctx);
    }

    private static void collectBuildOwner(org.openrewrite.Cursor cursor, J.MethodInvocation invocation,
                                          Set<String> modules, boolean[] core) {
        if (!FlywayVersions.isGradleDependencyInvocation(cursor, invocation)) return;
        String group = UpgradeSelectedFlywayCoreDependency.mapValue(invocation, "group");
        String name = UpgradeSelectedFlywayCoreDependency.mapValue(invocation, "name");
        if (FlywayVersions.GROUP.equals(group) && FlywayVersions.CORE.equals(name)) core[0] = true;
        if (FlywayVersions.GROUP.equals(group) && URLS.containsValue(name)) modules.add(name);
        invocation.getArguments().stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast).forEach(value -> {
                    if (UpgradeSelectedFlywayCoreDependency.isCoreCoordinate(value)) core[0] = true;
                    URLS.values().stream().filter(module -> value.equals(FlywayVersions.GROUP + ":" + module) ||
                            value.startsWith(FlywayVersions.GROUP + ":" + module + ":")).forEach(modules::add);
                });
    }

    private static J.Literal markDriver(J.Literal literal, Set<String> modules) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String module = DRIVERS.entrySet().stream().filter(entry -> value.startsWith(entry.getKey() + ":"))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        return module != null && !modules.contains(module) ? mark(literal, message(module)) : literal;
    }

    private static J.MethodInvocation markMapDriver(org.openrewrite.Cursor cursor, J.MethodInvocation invocation,
                                                     Set<String> modules) {
        if (!FlywayVersions.isGradleDependencyInvocation(cursor, invocation)) return invocation;
        String coordinate = UpgradeSelectedFlywayCoreDependency.mapValue(invocation, "group") + ":" +
                            UpgradeSelectedFlywayCoreDependency.mapValue(invocation, "name");
        String module = DRIVERS.get(coordinate);
        return module != null && !modules.contains(module) ? mark(invocation, message(module)) : invocation;
    }

    private static String coordinate(Xml.Tag tag) {
        return tag.getChildValue("groupId").orElse("") + ":" + tag.getChildValue("artifactId").orElse("");
    }

    private static String message(String module) {
        return "Flyway 11 requires org.flywaydb:" + module + " on the relevant application/plugin runtime classpath";
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findFirst(SearchResult.class).isPresent() ? tree : SearchResult.found(tree, message);
    }
}
