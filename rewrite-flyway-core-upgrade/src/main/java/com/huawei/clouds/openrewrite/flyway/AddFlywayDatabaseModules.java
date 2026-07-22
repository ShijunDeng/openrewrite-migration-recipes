package com.huawei.clouds.openrewrite.flyway;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adds target-version Flyway database companions when a Maven driver makes the choice deterministic. */
public final class AddFlywayDatabaseModules extends Recipe {
    private static final Map<String, String> DRIVERS = new LinkedHashMap<>();

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
    }

    @Override
    public String getDisplayName() {
        return "Add deterministic Flyway database modules to Maven builds";
    }

    @Override
    public String getDescription() {
        return "Add the Flyway 11 PostgreSQL, MySQL/MariaDB, SQL Server, Oracle, or DB2 module when a direct Maven Flyway Core dependency and matching JDBC driver make the choice unambiguous.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof Xml.Document document) || !(tree instanceof SourceFile source) ||
                    source.getSourcePath().getFileName() == null ||
                    !"pom.xml".equals(source.getSourcePath().getFileName().toString()) ||
                    !FlywayVersions.isProjectPath(source.getSourcePath())) return tree;
                Set<String> shadowed = new java.util.HashSet<>();
                new XmlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                        if (FlywayVersions.isPropertiesChild(getCursor(), tag) &&
                            !FlywayVersions.isProjectPropertiesChild(getCursor(), tag)) shadowed.add(tag.getName());
                        return super.visitTag(tag, p);
                    }
                }.visitNonNull(document, ctx);
                return new XmlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                        Xml.Tag t = super.visitTag(tag, p);
                        if (!"dependencies".equals(t.getName()) ||
                            !FlywayVersions.isProject(getCursor().getParentTreeCursor())) return t;
                        List<Xml.Tag> dependencies = t.getChildren().stream()
                                .filter(candidate -> "dependency".equals(candidate.getName())).toList();
                        Xml.Tag core = dependencies.stream().filter(AddFlywayDatabaseModules::isCore)
                                .filter(FlywayVersions::isStandardJar)
                                .filter(dependency -> hasTargetVersion(dependency, document, shadowed)).findFirst().orElse(null);
                        if (core == null) return t;
                        Set<String> presentModules = dependencies.stream()
                                .filter(candidate -> FlywayVersions.GROUP.equals(candidate.getChildValue("groupId").orElse(null)))
                                .map(candidate -> candidate.getChildValue("artifactId").orElse(""))
                                .collect(java.util.stream.Collectors.toSet());
                        List<String> needed = dependencies.stream().filter(FlywayVersions::isStandardJar)
                                .map(AddFlywayDatabaseModules::coordinate).map(DRIVERS::get)
                                .filter(java.util.Objects::nonNull).distinct()
                                .filter(module -> !presentModules.contains(module)).toList();
                        if (needed.isEmpty()) return t;
                        String version = core.getChildValue("version").orElse(FlywayVersions.TARGET);
                        String scope = core.getChildValue("scope").orElse(null);
                        List<Content> content = new ArrayList<>(t.getContent().size() + needed.size());
                        for (Content child : t.getContent()) {
                            content.add(child);
                            if (child == core) {
                                needed.forEach(module -> content.add(autoFormat(
                                        moduleTag(module, version, scope).withPrefix(core.getPrefix()), p, getCursor())));
                            }
                        }
                        return t.withContent(content);
                    }
                }.visitNonNull(document, ctx);
            }
        };
    }

    private static boolean hasTargetVersion(Xml.Tag dependency, Xml.Document document, Set<String> shadowed) {
        String version = dependency.getChildValue("version").map(String::trim).orElse(null);
        if (FlywayVersions.TARGET.equals(version)) return true;
        String property = UpgradeSelectedFlywayCoreDependency.propertyName(version).orElse(null);
        if (property == null || shadowed.contains(property)) return false;
        return document.getRoot().getChild("properties").map(properties ->
                properties.getChildren().stream().filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                        .filter(tag -> property.equals(tag.getName())).toList())
                .filter(matches -> matches.size() == 1)
                .map(matches -> FlywayVersions.TARGET.equals(matches.get(0).getValue().map(String::trim).orElse(null)))
                .orElse(false);
    }

    private static boolean isCore(Xml.Tag dependency) {
        return FlywayVersions.hasCoreCoordinates(dependency);
    }

    private static String coordinate(Xml.Tag dependency) {
        return dependency.getChildValue("groupId").orElse("") + ":" +
               dependency.getChildValue("artifactId").orElse("");
    }

    private static Xml.Tag moduleTag(String module, String version, String scope) {
        String scopeXml = scope == null ? "" : "\n  <scope>" + scope + "</scope>";
        return Xml.Tag.build("""
                <dependency>
                  <groupId>org.flywaydb</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>%s
                </dependency>
                """.formatted(module, version, scopeXml));
    }
}
