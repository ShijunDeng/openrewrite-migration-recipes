package com.huawei.clouds.openrewrite.jakartawrs;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark build declarations deliberately excluded from strict automatic migration. */
public final class FindJakartaRest4BuildRisks extends Recipe {
    static final String VERSION =
            "Jakarta REST API version is not a workbook-selected literal resolved to 4.0.0; update the actual " +
            "property/BOM/catalog/platform owner without widening this recipe's source whitelist";
    static final String VARIANT =
            "This jakarta.ws.rs-api declaration uses a classifier/non-jar/Gradle variant; verify artifact/module-path " +
            "topology before selecting the ordinary 4.0.0 API jar";
    static final String JAVA =
            "Jakarta REST 4 requires Java SE 17; raise the owned compiler/toolchain/runtime level and verify CI images, " +
            "application server, test workers, bytecode plugins and container base images";
    static final String IMPLEMENTATION =
            "The Jakarta REST API is paired with an implementation outside the 4.0 generation; align Jersey 4, RESTEasy 7 " +
            "or another certified implementation and verify provider discovery, servlet integration and classloader isolation";
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern MAJOR = Pattern.compile("(?:[^0-9]*)(\\d+).*?");
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");

    @Override
    public String getDisplayName() {
        return "Find Jakarta REST 4 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unresolved/out-of-workbook owners, variants, Java levels below 17, and incompatible Jersey/RESTEasy generations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedJakartaWsRsApiDependency.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return visitPom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    boolean primary = hasOwnedGroovyPrimary(groovy, ctx);
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedJakartaWsRsApiDependency.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            if (direct) return markGradleDependency(m);
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
                            boolean direct = UpgradeSelectedJakartaWsRsApiDependency.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            if (direct) return markGradleDependency(m);
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
        Map<Owner, Integer> propertyDefinitions = new HashMap<>();
        MavenScopes primaryScopes = primaryScopes(document, ctx);
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedJakartaWsRsApiDependency.isMavenPropertyDefinition(getCursor(), t)) {
                    Owner owner = owner(getCursor(), t.getName());
                    propertyDefinitions.merge(owner, 1, Integer::sum);
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
                    if (!UpgradeSelectedJakartaWsRsApiDependency.TARGET.equals(resolve(
                            getCursor(), t.getChildValue("version").map(String::trim).orElse(null),
                            properties, propertyDefinitions))) {
                        return mark(t, VERSION);
                    }
                }
                if (!primaryVisible(getCursor(), primaryScopes)) return t;
                if (UpgradeSelectedJakartaWsRsApiDependency.isMavenPropertyDefinition(getCursor(), t) &&
                    JAVA_PROPERTIES.contains(t.getName()) && t.getValue().map(String::trim).filter(
                            FindJakartaRest4BuildRisks::below17).isPresent()) return mark(t, JAVA);
                if (UpgradeSelectedJakartaWsRsApiDependency.isProjectDependency(getCursor(), t) &&
                    incompatibleRuntime(t, getCursor(), properties, propertyDefinitions)) {
                    return mark(t, IMPLEMENTATION);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean isRawPrimary(Cursor cursor, Xml.Tag tag) {
        return UpgradeSelectedJakartaWsRsApiDependency.isProjectDependency(cursor, tag) &&
               UpgradeSelectedJakartaWsRsApiDependency.GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               UpgradeSelectedJakartaWsRsApiDependency.ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static MavenScopes primaryScopes(Xml.Document document, ExecutionContext ctx) {
        boolean[] root = {false};
        Set<String> profiles = new java.util.HashSet<>();
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

    private static boolean incompatibleRuntime(Xml.Tag tag, Cursor cursor, Map<Owner, String> properties,
                                               Map<Owner, Integer> definitions) {
        String group = tag.getChildValue("groupId").orElse("");
        int required = group.startsWith("org.glassfish.jersey") ? 4 : group.startsWith("org.jboss.resteasy") ? 7 : -1;
        if (required < 0) return false;
        String version = resolve(cursor, tag.getChildValue("version").map(String::trim).orElse(null), properties, definitions);
        return major(version) != required;
    }

    private static String resolve(Cursor cursor, String version, Map<Owner, String> properties,
                                  Map<Owner, Integer> definitions) {
        if (version == null) return null;
        Matcher matcher = PROPERTY.matcher(version);
        if (!matcher.matches()) return version;
        String profile = profile(cursor);
        Owner local = profile == null ? null : new Owner(profile, matcher.group(1));
        Owner resolved = local != null && properties.containsKey(local)
                ? local : new Owner("ROOT", matcher.group(1));
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
                if (UpgradeSelectedJakartaWsRsApiDependency.isGradleDependencyInvocation(getCursor(), m) && standardPrimaryCoordinate(m)) found[0] = true;
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
                if (UpgradeSelectedJakartaWsRsApiDependency.isGradleDependencyInvocation(getCursor(), m) && standardPrimaryCoordinate(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean isPrimaryCoordinate(J.MethodInvocation method) {
        String printed = method.printTrimmed();
        return printed.contains(UpgradeSelectedJakartaWsRsApiDependency.GROUP + ":" +
                                UpgradeSelectedJakartaWsRsApiDependency.ARTIFACT) ||
               (UpgradeSelectedJakartaWsRsApiDependency.GROUP.equals(
                        UpgradeSelectedJakartaWsRsApiDependency.mapValue(method, "group")) &&
                UpgradeSelectedJakartaWsRsApiDependency.ARTIFACT.equals(
                        UpgradeSelectedJakartaWsRsApiDependency.mapValue(method, "name")));
    }

    private static boolean standardPrimaryCoordinate(J.MethodInvocation method) {
        String printed = method.printTrimmed();
        return isPrimaryCoordinate(method) && !UpgradeSelectedJakartaWsRsApiDependency.hasVariant(method) &&
               !printed.contains(":tests") && !printed.contains("@") &&
               !printed.contains("classifier:") && !printed.contains("ext:") && !printed.contains("type:");
    }

    private static J.MethodInvocation markGradleDependency(J.MethodInvocation method) {
        String printed = method.printTrimmed();
        if (isPrimaryCoordinate(method)) {
            if (UpgradeSelectedJakartaWsRsApiDependency.hasVariant(method) || printed.contains(":tests") || printed.contains("@")) {
                return mark(method, VARIANT);
            }
            String target = UpgradeSelectedJakartaWsRsApiDependency.GROUP + ":" +
                            UpgradeSelectedJakartaWsRsApiDependency.ARTIFACT + ":" +
                            UpgradeSelectedJakartaWsRsApiDependency.TARGET;
            String mapVersion = UpgradeSelectedJakartaWsRsApiDependency.mapValue(method, "version");
            return printed.contains(target) || UpgradeSelectedJakartaWsRsApiDependency.TARGET.equals(mapVersion)
                    ? method : mark(method, VERSION);
        }
        String coordinate = literalCoordinate(method);
        if (coordinate != null && coordinate.startsWith("org.glassfish.jersey.") &&
            major(coordinateVersion(coordinate)) != 4) {
            return mark(method, IMPLEMENTATION);
        }
        if (coordinate != null && coordinate.startsWith("org.jboss.resteasy:") &&
            major(coordinateVersion(coordinate)) != 7) {
            return mark(method, IMPLEMENTATION);
        }
        return method;
    }

    private static String literalCoordinate(J.MethodInvocation method) {
        for (Expression argument : method.getArguments()) {
            if (argument instanceof J.Literal literal && literal.getValue() instanceof String coordinate &&
                coordinate.split(":", -1).length >= 3) {
                return coordinate;
            }
        }
        return null;
    }

    private static String coordinateVersion(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        return parts.length >= 3 && !parts[2].contains("$") ? parts[2] : null;
    }

    private static boolean legacyToolchain(J.MethodInvocation method, Cursor cursor) {
        if (!rootToolchainScope(cursor)) return false;
        return "of".equals(method.getSimpleName()) && method.getArguments().size() == 1 &&
               method.getArguments().get(0) instanceof J.Literal literal && literal.getValue() instanceof Number number &&
               number.intValue() < 17 && method.getSelect() != null &&
               method.getSelect().printTrimmed().endsWith("JavaLanguageVersion");
    }

    private static boolean legacyJavaAssignment(J.Assignment assignment, Cursor cursor) {
        if (!rootJavaAssignmentScope(cursor)) return false;
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) return false;
        return below17(assignment.getAssignment().printTrimmed(cursor).replace("'", "").replace("\"", ""));
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

    private static boolean below17(String value) {
        if (value == null) return false;
        Matcher constant = Pattern.compile("(?:JavaVersion[.])?VERSION_(?:1_)?(\\d+)").matcher(value);
        if (constant.matches()) return Integer.parseInt(constant.group(1)) < 17;
        int major = major(value);
        return major > 0 && major < 17;
    }

    private static int major(String value) {
        if (value == null) return -1;
        Matcher matcher = MAJOR.matcher(value);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }

    private record Owner(String scope, String name) { }

    private record MavenScopes(boolean root, Set<String> profiles) { }
}
