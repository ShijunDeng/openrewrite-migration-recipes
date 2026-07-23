package com.huawei.clouds.openrewrite.tomcatcatalina;

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

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Preserve explicit warnings when the requested target crosses a Tomcat branch or would lower a version. */
public final class FindTomcatCatalinaBranchTransitionRisks extends Recipe {
    static final String TOMCAT_9 =
            "Tomcat 9 to 10.1 crosses Java EE javax.* to Jakarta EE jakarta.* and Servlet 4 to Servlet 6; " +
            "migrate every Servlet/EL dependency, source type, descriptor, service provider and framework integration, " +
            "then run container-level compatibility tests";
    static final String TARGET_CONFLICT =
            "目标版本冲突（禁止降级）：this tomcat-catalina version is higher than the supplied 10.1.56 target; " +
            "the upgrade-only policy keeps it byte-for-byte unchanged until an approved higher target is supplied";
    private static final String PREFIX = UpgradeSelectedTomcatCatalinaDependency.GROUP + ":" +
                                         UpgradeSelectedTomcatCatalinaDependency.ARTIFACT + ":";
    private static final Pattern FIXED_VERSION = Pattern.compile("[0-9]+(?:\\.[0-9]+)+");

    @Override
    public String getDisplayName() {
        return "Find Tomcat Catalina branch-transition risks";
    }

    @Override
    public String getDescription() {
        return "Marks selected Tomcat 9 to 10.1 namespace transitions and every fixed version above the target; " +
               "higher dependency literals remain unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedTomcatCatalinaDependency.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return visitPom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedTomcatCatalinaDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                            return direct ? markBranch(visited, version(visited)) : visited;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedTomcatCatalinaDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                            return direct ? markBranch(visited, version(visited)) : visited;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document visitPom(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (UpgradeSelectedTomcatCatalinaDependency.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyOwner owner = owner(getCursor(), visited.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    visited.getValue().map(String::trim).ifPresent(value -> values.put(owner, value));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!UpgradeSelectedTomcatCatalinaDependency.isTomcatCatalinaDependency(getCursor(), visited)) {
                    return visited;
                }
                String version = visited.getChildValue("version").map(String::trim).orElse(null);
                if (version != null && version.startsWith("${") && version.endsWith("}")) {
                    String name = version.substring(2, version.length() - 1);
                    PropertyOwner resolved = resolvedOwner(getCursor(), name, definitions);
                    version = definitions.getOrDefault(resolved, 0) == 1 ? values.get(resolved) : null;
                }
                return markBranch(visited, version);
            }
        }.visitNonNull(document, ctx);
    }

    private static String version(J.MethodInvocation invocation) {
        String mapped = UpgradeSelectedTomcatCatalinaDependency.mapValue(invocation, "version");
        if (mapped != null && UpgradeSelectedTomcatCatalinaDependency.GROUP.equals(
                UpgradeSelectedTomcatCatalinaDependency.mapValue(invocation, "group")) &&
            UpgradeSelectedTomcatCatalinaDependency.ARTIFACT.equals(
                UpgradeSelectedTomcatCatalinaDependency.mapValue(invocation, "name"))) return mapped;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && literal.getValue() instanceof String coordinate &&
                coordinate.startsWith(PREFIX)) return plainVersion(coordinate.substring(PREFIX.length()));
            if (argument instanceof G.MapLiteral map && UpgradeSelectedTomcatCatalinaDependency.GROUP.equals(
                    UpgradeSelectedTomcatCatalinaDependency.mapValue(map, "group")) &&
                UpgradeSelectedTomcatCatalinaDependency.ARTIFACT.equals(
                    UpgradeSelectedTomcatCatalinaDependency.mapValue(map, "name"))) {
                return UpgradeSelectedTomcatCatalinaDependency.mapValue(map, "version");
            }
        }
        return null;
    }

    private static String plainVersion(String value) {
        return value.contains(":") || value.contains("@") ? null : value;
    }

    private static <T extends Tree> T markBranch(T tree, String version) {
        String message = branchMessage(version);
        if (message == null || tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))) return tree;
        return SearchResult.found(tree, message);
    }

    private static String branchMessage(String version) {
        if (version == null) return null;
        if (FIXED_VERSION.matcher(version).matches() &&
            compare(version, UpgradeSelectedTomcatCatalinaDependency.TARGET) > 0) return TARGET_CONFLICT;
        if (!UpgradeSelectedTomcatCatalinaDependency.SOURCE_VERSIONS.contains(version)) return null;
        if (version.startsWith("9.0.")) return TOMCAT_9;
        return null;
    }

    private static int compare(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            BigInteger leftPart = new BigInteger(i < leftParts.length ? leftParts[i] : "0");
            BigInteger rightPart = new BigInteger(i < rightParts.length ? rightParts[i] : "0");
            int compared = leftPart.compareTo(rightPart);
            if (compared != 0) return compared;
        }
        return 0;
    }

    private static PropertyOwner owner(Cursor cursor, String name) {
        String profile = profile(cursor);
        return new PropertyOwner(profile == null ? "ROOT" : profile, name);
    }

    private static PropertyOwner resolvedOwner(Cursor cursor, String name,
                                               Map<PropertyOwner, Integer> definitions) {
        String profile = profile(cursor);
        PropertyOwner local = profile == null ? null : new PropertyOwner(profile, name);
        return local != null && definitions.containsKey(local) ? local : new PropertyOwner("ROOT", name);
    }

    private static String profile(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return null;
    }

    private record PropertyOwner(String scope, String name) { }
}
