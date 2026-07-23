package com.huawei.clouds.openrewrite.springbootactuator;

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
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Set;

/** Build ownership, baseline, platform, and no-downgrade checks for the starter migration. */
public final class FindActuatorBuildRisks extends Recipe {
    private static final Set<String> JAVA_KEYS = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");

    static final String OWNER =
            "spring-boot-starter-actuator is versionless, variable, ranged, catalog/platform/BOM-managed, shared, or externally owned; migrate the actual Boot owner and verify that 3.5.15 resolves";
    static final String OUTSIDE =
            "This fixed Spring Boot Actuator version is below 3.5.15 but outside the exact source whitelist; it is intentionally not auto-upgraded";
    static final String TARGET_CONFLICT =
            "目标版本冲突（禁止降级）: this Spring Boot declaration is newer than 3.5.15 or belongs to a higher Boot line; it remains unchanged and is not a migration path to the lower target";
    static final String VARIANT =
            "This classified or non-JAR starter artifact is outside deterministic scope; verify the exact 3.5.15 artifact shape manually";
    static final String JAVA =
            "Spring Boot 3 requires Java 17 or newer; align compiler, toolchain, CI, container, and runtime JDKs before upgrading Actuator";
    static final String BOM_ALIGNMENT =
            "The Actuator starter and Spring Boot parent/BOM/plugin must resolve as one 3.5.15 platform; do not force a divergent leaf version";
    static final String INTERNAL_PARENT =
            "spring-boot-parent is an internal parent that is no longer published in Boot 3.5; replace it with owned dependency management, not spring-boot-starter-parent by assumption";
    static final String JAKARTA =
            "Spring Boot 3 uses Jakarta EE 10; replace this legacy javax API dependency with the matching jakarta coordinate and verify no older transitive Java EE API remains";
    static final String MIGRATOR =
            "spring-boot-properties-migrator is transitional; use it to discover property changes, then remove it after the 3.5 migration is complete";
    static final String SECURITY =
            "Spring Boot 3 uses Spring Security 6; align security dependencies with the Boot 3.5 BOM and migrate the filter-chain authorization model";
    static final String MICROMETER =
            "Spring Boot 3.5 uses Micrometer 1.15; align metrics and tracing through the Boot BOM and regression-test names, tags, histograms, exporters, and observations";

    @Override
    public String getDisplayName() {
        return "Find Spring Boot Actuator 3.5 build risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact starter ownership, parent/BOM/platform/variant, Java 17, Jakarta, Security, " +
               "Micrometer, and properties-migrator boundaries while enforcing no downgrade.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringBootActuatorSupport.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document xml && "pom.xml".equals(fileName)) return maven(xml, ctx);
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return groovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return kotlin(kotlin, ctx);
                }
                if (tree instanceof PlainText text && fileName.endsWith(".toml") &&
                    text.getText().contains(SpringBootActuatorSupport.ARTIFACT)) {
                    return SpringBootActuatorSupport.mark(text, OWNER);
                }
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document document, ExecutionContext ctx) {
        boolean hasStarter = SpringBootActuatorSupport.containsTargetStarter(document, ctx);
        SpringBootActuatorSupport.PomProperties properties =
                SpringBootActuatorSupport.analyzeProperties(document, ctx);
        boolean locallyTargetManaged = locallyTargetManaged(document, properties, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (SpringBootActuatorSupport.isTargetDependency(getCursor(), t)) {
                    if (!SpringBootActuatorSupport.isStandardArtifact(t)) {
                        return SpringBootActuatorSupport.mark(t, VARIANT);
                    }
                    String raw = t.getChildValue("version").orElse("");
                    String resolved = properties.resolveUnique(raw, getCursor());
                    String message = primaryMessage(
                            raw, resolved, locallyTargetManaged, properties.safeReference(raw, getCursor()));
                    return message == null ? t : SpringBootActuatorSupport.markVersionOrOwner(t, message);
                }
                if (SpringBootActuatorSupport.isBootParent(getCursor(), t) ||
                    hasStarter && SpringBootActuatorSupport.isBootBom(getCursor(), t)) {
                    String raw = t.getChildValue("version").orElse("");
                    String resolved = properties.resolveUnique(raw, getCursor());
                    String message = ownerMessage(resolved, properties.safeReference(raw, getCursor()));
                    return message == null ? t : SpringBootActuatorSupport.markVersionOrOwner(t, message);
                }
                if ("parent".equals(t.getName()) &&
                    SpringBootActuatorSupport.GROUP.equals(t.getChildValue("groupId").orElse("")) &&
                    "spring-boot-parent".equals(t.getChildValue("artifactId").orElse(""))) {
                    return SpringBootActuatorSupport.mark(t, INTERNAL_PARENT);
                }
                if (JAVA_KEYS.contains(t.getName()) || isCompilerBaseline(getCursor(), t)) {
                    if (SpringBootActuatorSupport.belowJava17(t.getValue().orElse(""))) {
                        return SpringBootActuatorSupport.mark(t, JAVA);
                    }
                }
                if (!SpringBootActuatorSupport.isProjectDependency(getCursor(), t)) return t;
                String group = t.getChildValue("groupId").orElse("");
                String artifact = t.getChildValue("artifactId").orElse("");
                String version = properties.resolveUnique(t.getChildValue("version").orElse(""), getCursor());
                String message = companionMessage(group, artifact, version);
                return message == null ? t : SpringBootActuatorSupport.markVersionOrOwner(t, message);
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean isCompilerBaseline(Cursor cursor, Xml.Tag tag) {
        if (!Set.of("release", "source", "target").contains(tag.getName())) return false;
        Cursor configuration = cursor.getParentTreeCursor();
        if (!(configuration.getValue() instanceof Xml.Tag configurationTag) ||
            !"configuration".equals(configurationTag.getName())) return false;
        Cursor plugin = configuration.getParentTreeCursor();
        if (!(plugin.getValue() instanceof Xml.Tag pluginTag) ||
            !"plugin".equals(pluginTag.getName()) ||
            !"maven-compiler-plugin".equals(pluginTag.getChildValue("artifactId").orElse(""))) return false;
        String group = pluginTag.getChildValue("groupId").orElse("org.apache.maven.plugins");
        return "org.apache.maven.plugins".equals(group);
    }

    private static boolean locallyTargetManaged(Xml.Document document,
                                                SpringBootActuatorSupport.PomProperties properties,
                                                ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (SpringBootActuatorSupport.isBootParent(getCursor(), t) ||
                    SpringBootActuatorSupport.isBootBom(getCursor(), t)) {
                    String value = properties.resolveUnique(t.getChildValue("version").orElse(""), getCursor());
                    if (SpringBootActuatorSupport.TARGET.equals(value)) found[0] = true;
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return found[0];
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean targetManaged = source.printAll().contains(
                SpringBootActuatorSupport.GROUP + ":" + SpringBootActuatorSupport.BOM + ":" +
                SpringBootActuatorSupport.TARGET);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                J.Assignment a = super.visitAssignment(assignment, ec);
                String variable = a.getVariable().printTrimmed(getCursor());
                return ("sourceCompatibility".equals(variable) || "targetCompatibility".equals(variable)) &&
                       SpringBootActuatorSupport.belowJava17(a.getAssignment().printTrimmed(getCursor()))
                        ? SpringBootActuatorSupport.mark(a, JAVA) : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringBootActuatorSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (!dependency) return m;
                if (SpringBootActuatorSupport.hasVariant(m) &&
                    SpringBootActuatorSupport.GROUP.equals(SpringBootActuatorSupport.mapValue(m, "group")) &&
                    SpringBootActuatorSupport.ARTIFACT.equals(SpringBootActuatorSupport.mapValue(m, "name"))) {
                    return SpringBootActuatorSupport.mark(m, VARIANT);
                }
                String group = SpringBootActuatorSupport.mapValue(m, "group");
                String name = SpringBootActuatorSupport.mapValue(m, "name");
                if (SpringBootActuatorSupport.GROUP.equals(group) &&
                    SpringBootActuatorSupport.ARTIFACT.equals(name)) {
                    String message = primaryMessage(
                            SpringBootActuatorSupport.mapValue(m, "version"),
                            SpringBootActuatorSupport.mapValue(m, "version"), targetManaged, true);
                    return message == null ? m : SpringBootActuatorSupport.mark(m, message);
                }
                String printed = m.printTrimmed(getCursor());
                if (printed.matches(".*libs(?:[.]versions)?[.]spring[.]boot[.]starter[.]actuator.*")) {
                    return SpringBootActuatorSupport.mark(m, OWNER);
                }
                return m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringBootActuatorSupport.isDirectDependencyLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(l.getValue(), targetManaged) : null;
                return message == null ? l : SpringBootActuatorSupport.mark(l, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean targetManaged = source.printAll().contains(
                SpringBootActuatorSupport.GROUP + ":" + SpringBootActuatorSupport.BOM + ":" +
                SpringBootActuatorSupport.TARGET);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                J.Assignment a = super.visitAssignment(assignment, ec);
                String variable = a.getVariable().printTrimmed(getCursor());
                return ("sourceCompatibility".equals(variable) || "targetCompatibility".equals(variable)) &&
                       SpringBootActuatorSupport.belowJava17(a.getAssignment().printTrimmed(getCursor()))
                        ? SpringBootActuatorSupport.mark(a, JAVA) : a;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringBootActuatorSupport.isDirectDependencyLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(l.getValue(), targetManaged) : null;
                return message == null ? l : SpringBootActuatorSupport.mark(l, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static String primaryMessage(String raw, String resolved, boolean locallyTargetManaged,
                                         boolean locallyOwned) {
        if ((raw == null || raw.trim().isEmpty()) && locallyTargetManaged) return null;
        if (raw == null || raw.trim().isEmpty() || resolved == null ||
            !SpringBootActuatorSupport.FIXED_VERSION.matcher(resolved).matches()) return OWNER;
        if (!locallyOwned) return OWNER;
        if (SpringBootActuatorSupport.SOURCE_VERSIONS.contains(resolved)) return null;
        if (SpringBootActuatorSupport.TARGET.equals(resolved)) {
            return locallyTargetManaged ? null : BOM_ALIGNMENT;
        }
        return SpringBootActuatorSupport.targetConflict(resolved) ? TARGET_CONFLICT : OUTSIDE;
    }

    private static String ownerMessage(String resolved, boolean locallyOwned) {
        if (resolved == null || !SpringBootActuatorSupport.FIXED_VERSION.matcher(resolved).matches()) {
            return OWNER;
        }
        if (!locallyOwned) return OWNER;
        if (SpringBootActuatorSupport.SOURCE_VERSIONS.contains(resolved) ||
            SpringBootActuatorSupport.TARGET.equals(resolved)) return null;
        return SpringBootActuatorSupport.targetConflict(resolved) ? TARGET_CONFLICT : BOM_ALIGNMENT;
    }

    private static String coordinateMessage(Object raw, boolean targetManaged) {
        if (!(raw instanceof String value)) return null;
        if (value.equals(SpringBootActuatorSupport.GROUP + ":" + SpringBootActuatorSupport.ARTIFACT)) {
            return targetManaged ? null : OWNER;
        }
        if (!SpringBootActuatorSupport.isTargetCoordinate(value)) return companionCoordinateMessage(value);
        String version = SpringBootActuatorSupport.coordinateVersion(value);
        return primaryMessage(version, version, targetManaged, true);
    }

    private static String companionCoordinateMessage(String value) {
        if (value.startsWith("javax.")) return JAKARTA;
        if (value.startsWith("io.micrometer:")) return MICROMETER;
        if (value.startsWith("org.springframework.security:")) return SECURITY;
        if (value.startsWith(SpringBootActuatorSupport.GROUP + ":spring-boot-properties-migrator")) {
            return MIGRATOR;
        }
        return null;
    }

    private static String companionMessage(String group, String artifact, String version) {
        if (group.startsWith("javax.")) return JAKARTA;
        if (SpringBootActuatorSupport.GROUP.equals(group) &&
            "spring-boot-properties-migrator".equals(artifact)) return MIGRATOR;
        if ("org.springframework.security".equals(group) &&
            version != null && version.startsWith("5.")) return SECURITY;
        if ("io.micrometer".equals(group) &&
            version != null && !version.startsWith("1.15.")) return MICROMETER;
        return null;
    }
}
