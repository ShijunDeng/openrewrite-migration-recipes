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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark build declarations deliberately excluded from the strict automatic upgrade. */
public final class FindTomcatCatalinaBuildRisks extends Recipe {
    static final String VERSION =
            "Tomcat Catalina version does not resolve to 10.1.56 after the strict remediation pass; update the " +
            "actual property, BOM, platform, catalog or parent without widening this recipe's source whitelist";
    static final String VARIANT =
            "This tomcat-catalina declaration uses a classifier/non-jar/Gradle variant; verify its packaging and " +
            "classpath role before selecting the ordinary 10.1.56 jar";
    static final String JAVA =
            "Tomcat 10.1 requires Java 11 or later; raise the owned compiler/toolchain/runtime level and verify CI " +
            "images, containers, application launchers, test workers and production JVMs";
    static final String FAMILY =
            "Tomcat Catalina sibling artifacts under org.apache.tomcat must be aligned with tomcat-catalina 10.1.56; " +
            "update the owning BOM/property/catalog and verify coyote, util, API, jasper and optional modules resolve " +
            "to one Tomcat release";
    static final String WEB_API =
            "Tomcat 10.1 implements Jakarta Servlet 6.0 and EL 5.0; replace remaining javax Servlet/EL dependencies, " +
            "align explicit Jakarta API jars with the container, and keep container APIs provided/compileOnly rather than packaged";
    static final String INTERIM_TARGET =
            "The supplied Tomcat 10.1.56 target is now an interim security target: Apache reports CVE-2026-59083 " +
            "and CVE-2026-59084 affect 10.1.56 and are fixed in 10.1.57; this upgrade-only recipe keeps the approved " +
            "target but requires approval of 10.1.57 or a later supported release";
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern MAJOR = Pattern.compile("(?:[^0-9]*)(\\d+).*?");
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");

    @Override
    public String getDisplayName() {
        return "Find Tomcat Catalina 10.1 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unresolved/out-of-workbook owners, variants, Java levels below 11, and unaligned " +
               "org.apache.tomcat family declarations.";
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
                    boolean primary = hasOwnedGroovyPrimary(groovy, ctx);
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedTomcatCatalinaDependency.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            if (direct) {
                                J.MethodInvocation marked = markGradleDependency(m, primary);
                                return primary && webApiCoordinate(m) ? mark(marked, WEB_API) : marked;
                            }
                            return primary && legacyToolchain(m, getCursor()) ? mark(m, JAVA) : m;
                        }

                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                            J.Assignment a = super.visitAssignment(assignment, ec);
                            return primary && legacyJavaAssignment(a, getCursor()) ? mark(a, JAVA) : a;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    boolean primary = hasOwnedKotlinPrimary(kotlin, ctx);
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedTomcatCatalinaDependency.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            if (direct) {
                                J.MethodInvocation marked = markGradleDependency(m, primary);
                                return primary && webApiCoordinate(m) ? mark(marked, WEB_API) : marked;
                            }
                            return primary && legacyToolchain(m, getCursor()) ? mark(m, JAVA) : m;
                        }

                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                            J.Assignment a = super.visitAssignment(assignment, ec);
                            return primary && legacyJavaAssignment(a, getCursor()) ? mark(a, JAVA) : a;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document visitPom(Xml.Document document, ExecutionContext ctx) {
        Map<Owner, String> properties = new HashMap<>();
        Map<Owner, Integer> definitions = new HashMap<>();
        MavenScopes primaryScopes = primaryScopes(document, ctx);
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedTomcatCatalinaDependency.isMavenPropertyDefinition(getCursor(), t)) {
                    Owner owner = owner(getCursor(), t.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    t.getValue().ifPresent(value -> properties.put(owner, value.trim()));
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (isRawPrimary(getCursor(), t)) {
                    if (t.getChild("classifier").isPresent() || !"jar".equals(t.getChildValue("type").orElse("jar"))) {
                        return mark(t, VARIANT);
                    }
                    String resolved = resolve(
                            getCursor(), t.getChildValue("version").map(String::trim).orElse(null),
                            properties, definitions);
                    if (!UpgradeSelectedTomcatCatalinaDependency.TARGET.equals(resolved)) return mark(t, VERSION);
                    return mark(t, INTERIM_TARGET);
                }
                if (primaryVisible(getCursor(), primaryScopes) && isTomcatCatalinaFamily(getCursor(), t) &&
                    !UpgradeSelectedTomcatCatalinaDependency.ARTIFACT.equals(t.getChildValue("artifactId").orElse(null)) &&
                    !UpgradeSelectedTomcatCatalinaDependency.TARGET.equals(resolve(
                            getCursor(), t.getChildValue("version").map(String::trim).orElse(null), properties, definitions))) {
                    return mark(t, FAMILY);
                }
                if (primaryVisible(getCursor(), primaryScopes) && isWebApiDependency(getCursor(), t)) {
                    return mark(t, WEB_API);
                }
                if (!primaryVisible(getCursor(), primaryScopes)) return t;
                if (UpgradeSelectedTomcatCatalinaDependency.isMavenPropertyDefinition(getCursor(), t) &&
                    JAVA_PROPERTIES.contains(t.getName()) && t.getValue().map(String::trim)
                            .map(value -> resolve(getCursor(), value, properties, definitions))
                            .filter(FindTomcatCatalinaBuildRisks::below11).isPresent()) return mark(t, JAVA);
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean isRawPrimary(Cursor cursor, Xml.Tag tag) {
        return UpgradeSelectedTomcatCatalinaDependency.isProjectDependency(cursor, tag) &&
               UpgradeSelectedTomcatCatalinaDependency.GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               UpgradeSelectedTomcatCatalinaDependency.ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static boolean isTomcatCatalinaFamily(Cursor cursor, Xml.Tag tag) {
        return UpgradeSelectedTomcatCatalinaDependency.isProjectDependency(cursor, tag) &&
               UpgradeSelectedTomcatCatalinaDependency.GROUP.equals(tag.getChildValue("groupId").orElse(null));
    }

    private static boolean isWebApiDependency(Cursor cursor, Xml.Tag tag) {
        if (!UpgradeSelectedTomcatCatalinaDependency.isProjectDependency(cursor, tag)) return false;
        String group = tag.getChildValue("groupId").orElse("");
        return group.equals("javax.servlet") || group.startsWith("javax.servlet.") ||
               group.equals("javax.el") || group.startsWith("javax.el.") ||
               group.equals("jakarta.servlet") || group.startsWith("jakarta.servlet.") ||
               group.equals("jakarta.el") || group.startsWith("jakarta.el.");
    }

    private static MavenScopes primaryScopes(Xml.Document document, ExecutionContext ctx) {
        boolean[] root = {false};
        Set<String> profiles = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (isRawPrimary(getCursor(), t) && t.getChild("classifier").isEmpty() &&
                    "jar".equals(t.getChildValue("type").orElse("jar"))) {
                    String profile = profile(getCursor());
                    if (profile == null) root[0] = true;
                    else profiles.add(profile);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return new MavenScopes(root[0], Set.copyOf(profiles));
    }

    private static boolean primaryVisible(Cursor cursor, MavenScopes scopes) {
        String profile = profile(cursor);
        return profile == null ? scopes.root() || !scopes.profiles().isEmpty()
                : scopes.root() || scopes.profiles().contains(profile);
    }

    private static String resolve(Cursor cursor, String version, Map<Owner, String> properties,
                                  Map<Owner, Integer> definitions) {
        if (version == null) return null;
        Matcher matcher = PROPERTY.matcher(version);
        if (!matcher.matches()) return version;
        String profile = profile(cursor);
        Owner local = profile == null ? null : new Owner(profile, matcher.group(1));
        Owner resolved = local != null && definitions.containsKey(local) ? local : new Owner("ROOT", matcher.group(1));
        return definitions.getOrDefault(resolved, 0) == 1 ? properties.get(resolved) : null;
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

    private static boolean hasOwnedGroovyPrimary(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (UpgradeSelectedTomcatCatalinaDependency.isGradleDependencyInvocation(getCursor(), m) &&
                    standardPrimaryCoordinate(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean hasOwnedKotlinPrimary(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (UpgradeSelectedTomcatCatalinaDependency.isGradleDependencyInvocation(getCursor(), m) &&
                    standardPrimaryCoordinate(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean isPrimaryCoordinate(J.MethodInvocation method) {
        return coordinate(method, UpgradeSelectedTomcatCatalinaDependency.ARTIFACT);
    }

    private static boolean standardPrimaryCoordinate(J.MethodInvocation method) {
        if (UpgradeSelectedTomcatCatalinaDependency.GROUP.equals(
                    UpgradeSelectedTomcatCatalinaDependency.mapValue(method, "group")) &&
            UpgradeSelectedTomcatCatalinaDependency.ARTIFACT.equals(
                    UpgradeSelectedTomcatCatalinaDependency.mapValue(method, "name"))) {
            return !UpgradeSelectedTomcatCatalinaDependency.hasVariant(method);
        }
        for (J argument : method.getArguments()) {
            if (argument instanceof J.Literal literal && standardLiteral(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map && UpgradeSelectedTomcatCatalinaDependency.GROUP.equals(
                        UpgradeSelectedTomcatCatalinaDependency.mapValue(map, "group")) &&
                UpgradeSelectedTomcatCatalinaDependency.ARTIFACT.equals(
                        UpgradeSelectedTomcatCatalinaDependency.mapValue(map, "name")) &&
                !UpgradeSelectedTomcatCatalinaDependency.hasVariant(map)) return true;
            if (primaryTemplate(argument)) return true;
        }
        return false;
    }

    private static boolean coordinate(J.MethodInvocation method, String artifact) {
        if (UpgradeSelectedTomcatCatalinaDependency.GROUP.equals(
                    UpgradeSelectedTomcatCatalinaDependency.mapValue(method, "group")) &&
            artifact.equals(UpgradeSelectedTomcatCatalinaDependency.mapValue(method, "name"))) return true;
        String prefix = UpgradeSelectedTomcatCatalinaDependency.GROUP + ":" + artifact;
        for (J argument : method.getArguments()) {
            if (argument instanceof J.Literal literal && literal.getValue() instanceof String value &&
                (prefix.equals(value) || value.startsWith(prefix + ":"))) return true;
            if (argument instanceof G.MapLiteral map && UpgradeSelectedTomcatCatalinaDependency.GROUP.equals(
                        UpgradeSelectedTomcatCatalinaDependency.mapValue(map, "group")) &&
                artifact.equals(UpgradeSelectedTomcatCatalinaDependency.mapValue(map, "name"))) return true;
            if (templateParts(argument).stream().findFirst().filter(part -> part.equals(prefix + ":")).isPresent()) return true;
        }
        return false;
    }

    private static boolean familyCoordinate(J.MethodInvocation method) {
        if (UpgradeSelectedTomcatCatalinaDependency.GROUP.equals(
                UpgradeSelectedTomcatCatalinaDependency.mapValue(method, "group"))) return true;
        String prefix = UpgradeSelectedTomcatCatalinaDependency.GROUP + ":";
        return method.getArguments().stream().anyMatch(argument ->
                argument instanceof J.Literal literal && literal.getValue() instanceof String value && value.startsWith(prefix) ||
                argument instanceof G.MapLiteral map && UpgradeSelectedTomcatCatalinaDependency.GROUP.equals(
                        UpgradeSelectedTomcatCatalinaDependency.mapValue(map, "group")) ||
                templateParts(argument).stream().findFirst().filter(part -> part.startsWith(prefix)).isPresent());
    }

    private static boolean webApiCoordinate(J.MethodInvocation method) {
        String mappedGroup = UpgradeSelectedTomcatCatalinaDependency.mapValue(method, "group");
        if (webApiGroup(mappedGroup)) return true;
        for (J argument : method.getArguments()) {
            if (argument instanceof J.Literal literal && literal.getValue() instanceof String coordinate) {
                int separator = coordinate.indexOf(':');
                if (separator > 0 && webApiGroup(coordinate.substring(0, separator))) return true;
            }
            if (argument instanceof G.MapLiteral map && webApiGroup(
                    UpgradeSelectedTomcatCatalinaDependency.mapValue(map, "group"))) return true;
            List<String> parts = templateParts(argument);
            if (!parts.isEmpty()) {
                String first = parts.get(0);
                int separator = first.indexOf(':');
                if (separator > 0 && webApiGroup(first.substring(0, separator))) return true;
            }
        }
        return false;
    }

    private static boolean webApiGroup(String group) {
        return group != null && (group.equals("javax.servlet") || group.startsWith("javax.servlet.") ||
               group.equals("javax.el") || group.startsWith("javax.el.") ||
               group.equals("jakarta.servlet") || group.startsWith("jakarta.servlet.") ||
               group.equals("jakarta.el") || group.startsWith("jakarta.el."));
    }

    private static J.MethodInvocation markGradleDependency(J.MethodInvocation method, boolean primaryPresent) {
        if (isPrimaryCoordinate(method)) {
            if (!standardPrimaryCoordinate(method)) return mark(method, VARIANT);
            return targetCoordinate(method) ? mark(method, INTERIM_TARGET) : mark(method, VERSION);
        }
        return primaryPresent && familyCoordinate(method) && !targetCoordinate(method) ? mark(method, FAMILY) : method;
    }

    private static boolean standardLiteral(Object value) {
        if (!(value instanceof String coordinate)) return false;
        String prefix = UpgradeSelectedTomcatCatalinaDependency.GROUP + ":" +
                        UpgradeSelectedTomcatCatalinaDependency.ARTIFACT;
        if (prefix.equals(coordinate)) return true;
        if (!coordinate.startsWith(prefix + ":")) return false;
        String suffix = coordinate.substring(prefix.length() + 1);
        return !suffix.contains(":") && !suffix.contains("@");
    }

    private static boolean primaryTemplate(J argument) {
        String prefix = UpgradeSelectedTomcatCatalinaDependency.GROUP + ":" +
                        UpgradeSelectedTomcatCatalinaDependency.ARTIFACT + ":";
        List<String> parts = templateParts(argument);
        return !parts.isEmpty() && prefix.equals(parts.get(0)) && parts.stream().skip(1)
                .noneMatch(part -> part.contains(":") || part.contains("@"));
    }

    private static List<String> templateParts(J argument) {
        List<J> strings;
        if (argument instanceof G.GString template) strings = template.getStrings();
        else if (argument instanceof K.StringTemplate template) strings = template.getStrings();
        else return List.of();
        return strings.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static boolean targetCoordinate(J.MethodInvocation method) {
        if (UpgradeSelectedTomcatCatalinaDependency.TARGET.equals(
                    UpgradeSelectedTomcatCatalinaDependency.mapValue(method, "version"))) return true;
        String suffix = ":" + UpgradeSelectedTomcatCatalinaDependency.TARGET;
        return method.getArguments().stream().anyMatch(argument ->
                argument instanceof J.Literal literal && literal.getValue() instanceof String value && value.endsWith(suffix) ||
                argument instanceof G.MapLiteral map && UpgradeSelectedTomcatCatalinaDependency.TARGET.equals(
                        UpgradeSelectedTomcatCatalinaDependency.mapValue(map, "version")));
    }

    private static boolean legacyToolchain(J.MethodInvocation method, Cursor cursor) {
        if (!rootToolchainScope(cursor)) return false;
        return "of".equals(method.getSimpleName()) && method.getArguments().size() == 1 &&
               method.getArguments().get(0) instanceof J.Literal literal && literal.getValue() instanceof Number number &&
               number.intValue() < 11 && method.getSelect() != null &&
               method.getSelect().printTrimmed().endsWith("JavaLanguageVersion");
    }

    private static boolean legacyJavaAssignment(J.Assignment assignment, Cursor cursor) {
        if (!rootJavaAssignmentScope(cursor)) return false;
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) return false;
        return below11(assignment.getAssignment().printTrimmed(cursor).replace("'", "").replace("\"", ""));
    }

    private static boolean rootJavaAssignmentScope(Cursor cursor) {
        int methods = 0;
        String owner = null;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation invocation) {
                methods++;
                owner = invocation.getSimpleName();
            }
        }
        return methods == 0 || methods == 1 && "java".equals(owner);
    }

    private static boolean rootToolchainScope(Cursor cursor) {
        boolean java = false;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (!(current.getValue() instanceof J.MethodInvocation invocation)) continue;
            String name = invocation.getSimpleName();
            if ("java".equals(name)) java = true;
            else if (!Set.of("toolchain", "languageVersion", "set").contains(name)) return false;
        }
        return java;
    }

    private static boolean below11(String value) {
        if (value == null) return false;
        Matcher constant = Pattern.compile("(?:JavaVersion[.])?VERSION_(?:1_)?(\\d+)").matcher(value);
        if (constant.matches()) return Integer.parseInt(constant.group(1)) < 11;
        Matcher legacy = Pattern.compile("1[.](\\d+)").matcher(value);
        if (legacy.matches()) return Integer.parseInt(legacy.group(1)) < 11;
        Matcher matcher = MAJOR.matcher(value);
        return matcher.matches() && Integer.parseInt(matcher.group(1)) < 11;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }

    private record Owner(String scope, String name) { }

    private record MavenScopes(boolean root, Set<String> profiles) { }
}
