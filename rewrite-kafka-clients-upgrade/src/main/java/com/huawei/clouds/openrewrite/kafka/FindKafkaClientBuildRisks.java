package com.huawei.clouds.openrewrite.kafka;

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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Mark build decisions that constrain a kafka-clients 4.1.2 migration. */
public final class FindKafkaClientBuildRisks extends Recipe {
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target"
    );
    private static final Set<String> UNSUPPORTED_JAVA = Set.of("1.8", "8", "9", "10");
    private static final Set<String> COMPANION_ARTIFACTS = Set.of(
            "kafka-streams", "kafka-streams-test-utils", "kafka_2.12", "kafka_2.13", "connect-api",
            "connect-runtime", "connect", "connect-basic-auth-extension", "connect-file", "connect-json",
            "connect-mirror", "connect-mirror-client", "connect-transforms", "kafka-streams-scala_2.12",
            "kafka-streams-scala_2.13", "kafka-tools", "kafka-server", "kafka-server-common"
    );
    static final String JAVA_MESSAGE =
            "Kafka clients 4.1 requires Java 11 or newer; align compiler, test JVM and production runtime";
    static final String UNRESOLVED_MESSAGE =
            "This kafka-clients version was not selected by the spreadsheet gate; resolve its property, catalog, BOM, range or release path deliberately";
    static final String MANAGED_MESSAGE =
            "This kafka-clients declaration has no local version; upgrade the owning BOM, parent, platform or catalog and verify that it resolves to 4.1.2";
    static final String COMPANION_MESSAGE =
            "This Kafka companion has an independent 4.x API/runtime matrix; align it with kafka-clients and review Java, Scala, Streams/Connect and broker compatibility";
    static final String CUSTOM_ARTIFACT_MESSAGE =
            "This classified or non-JAR kafka-clients artifact was not automatically changed; verify that the target publishes the same artifact shape";

    @Override
    public String getDisplayName() {
        return "Find Kafka clients 4.1 build compatibility risks";
    }

    @Override
    public String getDescription() {
        return "Mark unsupported Java baselines, unresolved/versionless client declarations, custom artifact shapes " +
               "and adjacent Kafka components at exact Maven or Gradle nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !UpgradeSelectedKafkaClientsDependency.isProjectPath(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return inspectPom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    return inspectGroovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    return inspectKotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document inspectPom(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> properties = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(container -> container.getChildren().forEach(property ->
                property.getValue().ifPresent(value -> properties.put(property.getName(), value.trim()))));
        boolean[] clientPom = {false};
        boolean[] rootManagedTarget = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (UpgradeSelectedKafkaClientsDependency.isMavenDependencyBlock(getCursor(), tag) &&
                    UpgradeSelectedKafkaClientsDependency.hasClientCoordinates(tag)) {
                    clientPom[0] = true;
                    if (isRootDependencyManagementEntry(getCursor()) &&
                        UpgradeSelectedKafkaClientsDependency.isStandardJar(tag) &&
                        UpgradeSelectedKafkaClientsDependency.TARGET_VERSION.equals(
                                resolve(tag.getChildValue("version").map(String::trim).orElse(""), properties))) {
                        rootManagedTarget[0] = true;
                    }
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        if (!clientPom[0]) return document;
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag t = super.visitTag(tag, executionContext);
                if (isMavenProperty(getCursor(), t) && JAVA_PROPERTIES.contains(t.getName()) &&
                    t.getValue().map(value -> resolveFully(value, properties))
                            .filter(UNSUPPORTED_JAVA::contains).isPresent()) {
                    return SearchResult.found(t, JAVA_MESSAGE);
                }
                if (isCompilerLevel(getCursor(), t) &&
                    t.getValue().map(value -> resolveFully(value, properties))
                            .filter(UNSUPPORTED_JAVA::contains).isPresent()) {
                    return SearchResult.found(t, JAVA_MESSAGE);
                }
                if (!UpgradeSelectedKafkaClientsDependency.isMavenDependencyBlock(getCursor(), t)) return t;
                String group = t.getChildValue("groupId").orElse("");
                String artifact = t.getChildValue("artifactId").orElse("");
                if ("org.apache.kafka".equals(group) && "kafka-clients".equals(artifact)) {
                    if (!UpgradeSelectedKafkaClientsDependency.isStandardJar(t)) {
                        return SearchResult.found(t, CUSTOM_ARTIFACT_MESSAGE);
                    }
                    String declared = t.getChildValue("version").map(String::trim).orElse("");
                    if (declared.isEmpty()) {
                        if (isRootDirectDependency(getCursor()) && rootManagedTarget[0]) {
                            return t;
                        }
                        return SearchResult.found(t, MANAGED_MESSAGE);
                    }
                    String resolved = resolve(declared, properties);
                    if (!UpgradeSelectedKafkaClientsDependency.TARGET_VERSION.equals(resolved)) {
                        return SearchResult.found(t, UNRESOLVED_MESSAGE);
                    }
                }
                if ("org.apache.kafka".equals(group) && COMPANION_ARTIFACTS.contains(artifact)) {
                    return SearchResult.found(t, COMPANION_MESSAGE);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit inspectGroovy(G.CompilationUnit cu, ExecutionContext ctx) {
        boolean clientFile = containsGroovyClient(cu, ctx);
        if (!clientFile) return cu;
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if (javaBaselineCall(m)) return SearchResult.found(m, JAVA_MESSAGE);
                if (!UpgradeSelectedKafkaClientsDependency.isGradleDependencyInvocation(getCursor(), m)) return m;
                String compact = m.printTrimmed(getCursor()).replaceAll("\\s+", "");
                if (client(compact) && UpgradeSelectedKafkaClientsDependency.hasGradleVariant(m)) {
                    return SearchResult.found(m, CUSTOM_ARTIFACT_MESSAGE);
                }
                if (client(compact) && !targetClient(compact)) {
                    return SearchResult.found(m,
                            customGradleClient(compact) ? CUSTOM_ARTIFACT_MESSAGE : UNRESOLVED_MESSAGE);
                }
                if (companion(compact)) return SearchResult.found(m, COMPANION_MESSAGE);
                return m;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment a = super.visitAssignment(assignment, executionContext);
                return javaBaselineAssignment(a, getCursor()) ? SearchResult.found(a, JAVA_MESSAGE) : a;
            }
        }.visitNonNull(cu, ctx);
    }

    private static K.CompilationUnit inspectKotlin(K.CompilationUnit cu, ExecutionContext ctx) {
        boolean clientFile = containsKotlinClient(cu, ctx);
        if (!clientFile) return cu;
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if (javaBaselineCall(m)) return SearchResult.found(m, JAVA_MESSAGE);
                if (!UpgradeSelectedKafkaClientsDependency.isGradleDependencyInvocation(getCursor(), m)) return m;
                String compact = m.printTrimmed(getCursor()).replaceAll("\\s+", "");
                if (client(compact) && UpgradeSelectedKafkaClientsDependency.hasGradleVariant(m)) {
                    return SearchResult.found(m, CUSTOM_ARTIFACT_MESSAGE);
                }
                if (client(compact) && !targetClient(compact)) {
                    return SearchResult.found(m,
                            customGradleClient(compact) ? CUSTOM_ARTIFACT_MESSAGE : UNRESOLVED_MESSAGE);
                }
                if (companion(compact)) return SearchResult.found(m, COMPANION_MESSAGE);
                return m;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment a = super.visitAssignment(assignment, executionContext);
                return javaBaselineAssignment(a, getCursor()) ? SearchResult.found(a, JAVA_MESSAGE) : a;
            }
        }.visitNonNull(cu, ctx);
    }

    private static boolean containsGroovyClient(G.CompilationUnit cu, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (UpgradeSelectedKafkaClientsDependency.isGradleDependencyInvocation(getCursor(), method) &&
                    client(method.printTrimmed(getCursor()).replaceAll("\\s+", ""))) found[0] = true;
                return found[0] ? method : super.visitMethodInvocation(method, executionContext);
            }
        }.visit(cu, ctx);
        return found[0];
    }

    private static boolean containsKotlinClient(K.CompilationUnit cu, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (UpgradeSelectedKafkaClientsDependency.isGradleDependencyInvocation(getCursor(), method) &&
                    client(method.printTrimmed(getCursor()).replaceAll("\\s+", ""))) found[0] = true;
                return found[0] ? method : super.visitMethodInvocation(method, executionContext);
            }
        }.visit(cu, ctx);
        return found[0];
    }

    private static boolean isMavenProperty(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        Cursor owner = parent == null ? null : parent.getParentTreeCursor();
        return parent != null && parent.getValue() instanceof Xml.Tag properties &&
               "properties".equals(properties.getName()) && owner != null && owner.getValue() instanceof Xml.Tag root &&
               Set.of("project", "profile").contains(root.getName()) && !"properties".equals(tag.getName());
    }

    private static boolean isCompilerLevel(Cursor cursor, Xml.Tag tag) {
        if (!Set.of("source", "target", "release").contains(tag.getName())) return false;
        Cursor current = cursor.getParentTreeCursor();
        while (current != null && !(current.getValue() instanceof Xml.Document)) {
            if (current.getValue() instanceof Xml.Tag ancestor && "plugin".equals(ancestor.getName())) {
                return "maven-compiler-plugin".equals(ancestor.getChildValue("artifactId").orElse(null));
            }
            current = current.getParentTreeCursor();
        }
        return false;
    }

    private static boolean javaBaselineAssignment(J.Assignment assignment, Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) return false;
        String value = assignment.getAssignment().printTrimmed(cursor).replace("'", "").replace("\"", "");
        return UNSUPPORTED_JAVA.contains(value) || "JavaVersion.VERSION_1_8".equals(value) ||
               "JavaVersion.VERSION_1_9".equals(value) || "JavaVersion.VERSION_1_10".equals(value);
    }

    private static boolean javaBaselineCall(J.MethodInvocation method) {
        if ("jvmToolchain".equals(method.getSimpleName()) && method.getArguments().size() == 1) {
            return UNSUPPORTED_JAVA.contains(method.getArguments().get(0).printTrimmed());
        }
        if ("of".equals(method.getSimpleName()) && method.getSelect() != null &&
            method.getSelect().printTrimmed().endsWith("JavaLanguageVersion") && method.getArguments().size() == 1) {
            return UNSUPPORTED_JAVA.contains(method.getArguments().get(0).printTrimmed());
        }
        return false;
    }

    private static boolean mapTarget(String compact) {
        return (compact.contains("group:'org.apache.kafka'") && compact.contains("name:'kafka-clients'") &&
                compact.contains("version:'" + UpgradeSelectedKafkaClientsDependency.TARGET_VERSION + "'")) ||
               (compact.contains("group:\"org.apache.kafka\"") && compact.contains("name:\"kafka-clients\"") &&
                compact.contains("version:\"" + UpgradeSelectedKafkaClientsDependency.TARGET_VERSION + "\""));
    }

    private static boolean targetClient(String compact) {
        String coordinate = UpgradeSelectedKafkaClientsDependency.COORDINATE_PREFIX +
                            UpgradeSelectedKafkaClientsDependency.TARGET_VERSION;
        return compact.contains("'" + coordinate + "'") || compact.contains("\"" + coordinate + "\"") ||
               mapTarget(compact);
    }

    private static boolean client(String compact) {
        return compact.contains("'" + UpgradeSelectedKafkaClientsDependency.COORDINATE_PREFIX) ||
               compact.contains("\"" + UpgradeSelectedKafkaClientsDependency.COORDINATE_PREFIX) ||
               compact.contains("group:'org.apache.kafka'") && compact.contains("name:'kafka-clients'") ||
               compact.contains("group:\"org.apache.kafka\"") && compact.contains("name:\"kafka-clients\"");
    }

    private static boolean companion(String compact) {
        for (String artifact : COMPANION_ARTIFACTS) {
            if (compact.contains("'org.apache.kafka:" + artifact + ":") ||
                compact.contains("\"org.apache.kafka:" + artifact + ":") ||
                compact.contains("group:'org.apache.kafka'") && compact.contains("name:'" + artifact + "'") ||
                compact.contains("group:\"org.apache.kafka\"") && compact.contains("name:\"" + artifact + "\"")) return true;
        }
        return false;
    }

    private static String resolve(String declared, Map<String, String> properties) {
        if (declared.startsWith("${") && declared.endsWith("}") && declared.length() > 3) {
            return properties.getOrDefault(declared.substring(2, declared.length() - 1), declared);
        }
        return declared;
    }

    private static String resolveFully(String declared, Map<String, String> properties) {
        String resolved = declared.trim();
        for (int depth = 0; depth < 5; depth++) {
            String next = resolve(resolved, properties).trim();
            if (next.equals(resolved)) {
                return next;
            }
            resolved = next;
        }
        return resolved;
    }

    private static boolean customGradleClient(String compact) {
        int start = compact.indexOf(UpgradeSelectedKafkaClientsDependency.COORDINATE_PREFIX);
        if (start < 0) {
            return false;
        }
        char quoteCharacter = compact.lastIndexOf('\'', start) >= 0 ? '\'' : '"';
        int end = compact.indexOf(quoteCharacter, start);
        String coordinate = end < 0 ? compact.substring(start) : compact.substring(start, end);
        String version = coordinate.substring(UpgradeSelectedKafkaClientsDependency.COORDINATE_PREFIX.length());
        return version.contains(":") || version.contains("@");
    }

    private static boolean isRootDirectDependency(Cursor cursor) {
        Cursor dependencies = cursor.getParent();
        Cursor project = dependencies == null ? null : dependencies.getParent();
        return dependencies != null && dependencies.getValue() instanceof Xml.Tag dependenciesTag &&
               "dependencies".equals(dependenciesTag.getName()) && project != null &&
               project.getValue() instanceof Xml.Tag projectTag && "project".equals(projectTag.getName());
    }

    private static boolean isRootDependencyManagementEntry(Cursor cursor) {
        Cursor dependencies = cursor.getParent();
        Cursor management = dependencies == null ? null : dependencies.getParent();
        Cursor project = management == null ? null : management.getParent();
        return dependencies != null && dependencies.getValue() instanceof Xml.Tag dependenciesTag &&
               "dependencies".equals(dependenciesTag.getName()) && management != null &&
               management.getValue() instanceof Xml.Tag managementTag &&
               "dependencyManagement".equals(managementTag.getName()) && project != null &&
               project.getValue() instanceof Xml.Tag projectTag && "project".equals(projectTag.getName());
    }

}
