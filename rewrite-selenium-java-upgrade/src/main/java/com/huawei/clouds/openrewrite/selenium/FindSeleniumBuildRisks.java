package com.huawei.clouds.openrewrite.selenium;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark build declarations deliberately excluded from the strict automatic dependency upgrade. */
public final class FindSeleniumBuildRisks extends Recipe {
    static final String VERSION =
            "Selenium Java version is not the workbook-owned literal 4.8.1 (or resolved target 4.41.0); resolve the " +
            "BOM/property/catalog/platform owner and choose the upgrade scope explicitly";
    static final String VARIANT =
            "This selenium-java dependency has a classifier/non-jar/Gradle variant; 4.41.0 artifact topology and " +
            "transitive browser modules must be reviewed before changing it";
    static final String JAVA =
            "Selenium 4.14+ requires Java 11; this build still declares Java 8, so raise the owned toolchain/compiler/runtime " +
            "baseline to at least 11 and verify CI images, test workers, agents and Grid nodes";
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");

    @Override
    public String getDisplayName() {
        return "Find Selenium Java 4.41.0 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unresolved/out-of-workbook Maven and Gradle owners, artifact variants, and Maven Java 8 baselines.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || UpgradeSelectedSeleniumDependency.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return visitPom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedSeleniumDependency.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            return direct ? markGradle(m) : m;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedSeleniumDependency.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            return direct ? markGradle(m) : m;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document visitPom(Xml.Document document, ExecutionContext ctx) {
        Map<Owner, String> properties = new HashMap<>();
        Map<Owner, Integer> propertyDefinitions = new HashMap<>();
        boolean[] rootSelenium = {false};
        Set<UUID> profileSelenium = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedSeleniumDependency.isMavenPropertyDefinition(getCursor(), t)) {
                    Owner owner = owner(getCursor(), t.getName());
                    propertyDefinitions.merge(owner, 1, Integer::sum);
                    t.getValue().ifPresent(value -> properties.put(owner, value.trim()));
                }
                if (isRawSeleniumDependency(getCursor(), t)) {
                    UUID profile = profileId(getCursor());
                    if (profile == null) rootSelenium[0] = true;
                    else profileSelenium.add(profile);
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (isRawSeleniumDependency(getCursor(), t)) {
                    if (t.getChild("classifier").isPresent() || !"jar".equals(t.getChildValue("type").orElse("jar"))) {
                        return mark(t, VARIANT);
                    }
                    String version = t.getChildValue("version").map(String::trim).orElse(null);
                    if (!resolvedTarget(getCursor(), version, properties, propertyDefinitions)) return mark(t, VERSION);
                }
                UUID profile = profileId(getCursor());
                boolean dependencyVisible = profile == null
                        ? rootSelenium[0] || !profileSelenium.isEmpty()
                        : rootSelenium[0] || profileSelenium.contains(profile);
                if (dependencyVisible && UpgradeSelectedSeleniumDependency.isMavenPropertyDefinition(getCursor(), t) &&
                    JAVA_PROPERTIES.contains(t.getName()) && t.getValue().map(String::trim)
                            .filter(value -> "8".equals(value) || "1.8".equals(value)).isPresent()) {
                    return mark(t, JAVA);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean isRawSeleniumDependency(Cursor cursor, Xml.Tag tag) {
        return UpgradeSelectedSeleniumDependency.isProjectDependency(cursor, tag) &&
               UpgradeSelectedSeleniumDependency.GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               UpgradeSelectedSeleniumDependency.ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static boolean resolvedTarget(Cursor cursor, String version, Map<Owner, String> properties,
                                          Map<Owner, Integer> definitions) {
        if (UpgradeSelectedSeleniumDependency.TARGET.equals(version)) return true;
        if (version == null) return false;
        Matcher matcher = PROPERTY.matcher(version);
        if (!matcher.matches()) return false;
        String profile = profile(cursor);
        Owner local = profile == null ? null : new Owner(profile, matcher.group(1));
        Owner resolved = local != null && properties.containsKey(local)
                ? local : new Owner("ROOT", matcher.group(1));
        if (definitions.getOrDefault(resolved, 0) != 1) return false;
        String value = properties.get(resolved);
        return UpgradeSelectedSeleniumDependency.TARGET.equals(value);
    }

    private static Owner owner(Cursor cursor, String name) {
        String profile = profile(cursor);
        return new Owner(profile == null ? "ROOT" : profile, name);
    }

    private static String profile(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) return tag.getId().toString();
            if (current.getValue() instanceof Xml.Document) break;
        }
        return null;
    }

    private static UUID profileId(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) return tag.getId();
            if (current.getValue() instanceof Xml.Document) break;
        }
        return null;
    }

    private static J.MethodInvocation markGradle(J.MethodInvocation invocation) {
        String printed = invocation.printTrimmed();
        if (!printed.contains(UpgradeSelectedSeleniumDependency.GROUP + ":" +
                              UpgradeSelectedSeleniumDependency.ARTIFACT) &&
            !(UpgradeSelectedSeleniumDependency.GROUP.equals(
                    UpgradeSelectedSeleniumDependency.mapValue(invocation, "group")) &&
              UpgradeSelectedSeleniumDependency.ARTIFACT.equals(
                    UpgradeSelectedSeleniumDependency.mapValue(invocation, "name")))) return invocation;
        if (UpgradeSelectedSeleniumDependency.hasVariant(invocation) || printed.contains(":tests") || printed.contains("@")) {
            return mark(invocation, VARIANT);
        }
        String coordinate = UpgradeSelectedSeleniumDependency.GROUP + ":" +
                            UpgradeSelectedSeleniumDependency.ARTIFACT + ":" +
                            UpgradeSelectedSeleniumDependency.TARGET;
        String mapVersion = UpgradeSelectedSeleniumDependency.mapValue(invocation, "version");
        return printed.contains(coordinate) || UpgradeSelectedSeleniumDependency.TARGET.equals(mapVersion)
                ? invocation : mark(invocation, VERSION);
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }

    private record Owner(String scope, String name) { }
}
