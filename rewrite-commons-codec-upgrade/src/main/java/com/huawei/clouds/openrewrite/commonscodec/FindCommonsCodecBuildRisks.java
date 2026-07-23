package com.huawei.clouds.openrewrite.commonscodec;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark build declarations deliberately excluded from strict automatic migration. */
public final class FindCommonsCodecBuildRisks extends Recipe {
    static final String VERSION =
            "Commons Codec version is not a workbook-selected literal resolved to 1.22.0; update the actual " +
            "property/BOM/catalog/platform owner without widening this recipe's source whitelist";
    static final String VARIANT =
            "This commons-codec declaration uses a classifier/non-jar/Gradle variant; verify shaded/relocated classes, " +
            "OSGi metadata and classpath topology before selecting the ordinary 1.22.0 jar";
    static final String JAVA =
            "Commons Codec 1.22 requires Java 8 or later; raise the owned compiler/toolchain/runtime level and verify " +
            "CI images, application servers, test workers, Android level/desugaring and container base images";
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern MAJOR = Pattern.compile("(?:[^0-9]*)(\\d+).*?");
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");

    @Override
    public String getDisplayName() {
        return "Find Apache Commons Codec 1.22 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unresolved/out-of-workbook owners, variants, and Java compiler/toolchain levels below 8.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedCommonsCodecDependency.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return visitPom(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    boolean primary = hasOwnedGroovyPrimary(groovy, ctx);
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedCommonsCodecDependency.isGradleDependencyInvocation(getCursor(), method);
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
                            boolean direct = UpgradeSelectedCommonsCodecDependency.isGradleDependencyInvocation(getCursor(), method);
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
                if (UpgradeSelectedCommonsCodecDependency.isMavenPropertyDefinition(getCursor(), t)) {
                    Owner owner = owner(getCursor(), t.getName());
                    propertyDefinitions.merge(owner, 1, Integer::sum);
                    t.getValue().ifPresent(value -> properties.put(owner, value.trim()));
                }
                return t;
            }
        }.visitNonNull(document, ctx);

        Set<String> managedTargetScopes = new java.util.HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (isRawPrimary(getCursor(), visited) &&
                    isManagedDependency(getCursor()) &&
                    visited.getChild("classifier").isEmpty() &&
                    "jar".equals(visited.getChildValue("type").orElse("jar")) &&
                    UpgradeSelectedCommonsCodecDependency.TARGET.equals(resolve(
                            getCursor(),
                            visited.getChildValue("version")
                                    .map(String::trim).orElse(null),
                            properties, propertyDefinitions))) {
                    String profile = profile(getCursor());
                    managedTargetScopes.add(
                            profile == null ? "ROOT" : profile);
                }
                return visited;
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
                    String rawVersion = t.getChildValue("version")
                            .map(String::trim).orElse(null);
                    if (!(rawVersion == null &&
                          managedTargetVisible(
                                  getCursor(), managedTargetScopes)) &&
                        !UpgradeSelectedCommonsCodecDependency.TARGET.equals(resolve(
                                getCursor(), rawVersion,
                                properties, propertyDefinitions))) {
                        return mark(t, VERSION);
                    }
                }
                if (!primaryVisible(getCursor(), primaryScopes)) return t;
                if (UpgradeSelectedCommonsCodecDependency.isMavenPropertyDefinition(getCursor(), t) &&
                    JAVA_PROPERTIES.contains(t.getName()) && t.getValue().map(String::trim)
                            .map(value -> resolve(getCursor(), value, properties, propertyDefinitions))
                            .filter(FindCommonsCodecBuildRisks::below8).isPresent()) return mark(t, JAVA);
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static boolean isRawPrimary(Cursor cursor, Xml.Tag tag) {
        return UpgradeSelectedCommonsCodecDependency.isProjectDependency(cursor, tag) &&
               UpgradeSelectedCommonsCodecDependency.GROUP.equals(tag.getChildValue("groupId").orElse(null)) &&
               UpgradeSelectedCommonsCodecDependency.ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null));
    }

    private static boolean isManagedDependency(Cursor dependencyCursor) {
        Cursor dependencies = dependencyCursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag dependenciesTag) ||
            !"dependencies".equals(dependenciesTag.getName())) {
            return false;
        }
        Cursor owner = dependencies.getParentTreeCursor();
        return owner.getValue() instanceof Xml.Tag ownerTag &&
               "dependencyManagement".equals(ownerTag.getName());
    }

    private static boolean managedTargetVisible(
            Cursor cursor, Set<String> managedTargetScopes) {
        String profile = profile(cursor);
        return managedTargetScopes.contains("ROOT") ||
               profile != null && managedTargetScopes.contains(profile);
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

    private static String resolve(Cursor cursor, String version, Map<Owner, String> properties,
                                  Map<Owner, Integer> definitions) {
        if (version == null) return null;
        Matcher matcher = PROPERTY.matcher(version);
        if (!matcher.matches()) return version;
        String profile = profile(cursor);
        Owner local = profile == null ? null : new Owner(profile, matcher.group(1));
        Owner resolved = local != null && definitions.containsKey(local)
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
                if (UpgradeSelectedCommonsCodecDependency.isGradleDependencyInvocation(getCursor(), m) && standardPrimaryCoordinate(m)) found[0] = true;
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
                if (UpgradeSelectedCommonsCodecDependency.isGradleDependencyInvocation(getCursor(), m) && standardPrimaryCoordinate(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean isPrimaryCoordinate(J.MethodInvocation method) {
        if (UpgradeSelectedCommonsCodecDependency.GROUP.equals(
                    UpgradeSelectedCommonsCodecDependency.mapValue(method, "group")) &&
            UpgradeSelectedCommonsCodecDependency.ARTIFACT.equals(
                    UpgradeSelectedCommonsCodecDependency.mapValue(method, "name"))) return true;
        for (J argument : method.getArguments()) {
            if (argument instanceof J.Literal literal && primaryLiteral(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map && UpgradeSelectedCommonsCodecDependency.GROUP.equals(
                        UpgradeSelectedCommonsCodecDependency.mapValue(map, "group")) &&
                UpgradeSelectedCommonsCodecDependency.ARTIFACT.equals(
                        UpgradeSelectedCommonsCodecDependency.mapValue(map, "name"))) return true;
            if (primaryTemplate(argument)) return true;
        }
        return false;
    }

    private static boolean standardPrimaryCoordinate(J.MethodInvocation method) {
        if (UpgradeSelectedCommonsCodecDependency.GROUP.equals(
                    UpgradeSelectedCommonsCodecDependency.mapValue(method, "group")) &&
            UpgradeSelectedCommonsCodecDependency.ARTIFACT.equals(
                    UpgradeSelectedCommonsCodecDependency.mapValue(method, "name"))) {
            return !UpgradeSelectedCommonsCodecDependency.hasVariant(method);
        }
        for (J argument : method.getArguments()) {
            if (argument instanceof J.Literal literal && standardLiteral(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map && UpgradeSelectedCommonsCodecDependency.GROUP.equals(
                        UpgradeSelectedCommonsCodecDependency.mapValue(map, "group")) &&
                UpgradeSelectedCommonsCodecDependency.ARTIFACT.equals(
                        UpgradeSelectedCommonsCodecDependency.mapValue(map, "name")) &&
                !UpgradeSelectedCommonsCodecDependency.hasVariant(map)) return true;
            if (standardTemplate(argument)) return true;
        }
        return false;
    }

    private static J.MethodInvocation markGradleDependency(J.MethodInvocation method) {
        if (isPrimaryCoordinate(method)) {
            if (!standardPrimaryCoordinate(method)) {
                return mark(method, VARIANT);
            }
            return targetCoordinate(method)
                    ? method : mark(method, VERSION);
        }
        return method;
    }

    private static boolean primaryLiteral(Object value) {
        if (!(value instanceof String coordinate)) return false;
        String prefix = UpgradeSelectedCommonsCodecDependency.GROUP + ":" +
                        UpgradeSelectedCommonsCodecDependency.ARTIFACT;
        return prefix.equals(coordinate) || coordinate.startsWith(prefix + ":");
    }

    private static boolean standardLiteral(Object value) {
        if (!(value instanceof String coordinate) || !primaryLiteral(coordinate)) return false;
        String prefix = UpgradeSelectedCommonsCodecDependency.GROUP + ":" +
                        UpgradeSelectedCommonsCodecDependency.ARTIFACT;
        if (prefix.equals(coordinate)) return true;
        String suffix = coordinate.substring(prefix.length() + 1);
        return !suffix.contains(":") && !suffix.contains("@");
    }

    private static boolean primaryTemplate(J argument) {
        return templateParts(argument).stream().findFirst()
                .filter(part -> part.equals(UpgradeSelectedCommonsCodecDependency.GROUP + ":" +
                                            UpgradeSelectedCommonsCodecDependency.ARTIFACT + ":"))
                .isPresent();
    }

    private static boolean standardTemplate(J argument) {
        java.util.List<String> parts = templateParts(argument);
        return primaryTemplate(argument) && parts.stream().skip(1)
                .noneMatch(part -> part.contains(":") || part.contains("@"));
    }

    private static java.util.List<String> templateParts(J argument) {
        java.util.List<J> strings;
        if (argument instanceof G.GString template) strings = template.getStrings();
        else if (argument instanceof K.StringTemplate template) strings = template.getStrings();
        else return java.util.List.of();
        return strings.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static boolean targetCoordinate(J.MethodInvocation method) {
        if (UpgradeSelectedCommonsCodecDependency.TARGET.equals(
                    UpgradeSelectedCommonsCodecDependency.mapValue(method, "version"))) return true;
        String target = UpgradeSelectedCommonsCodecDependency.GROUP + ":" +
                        UpgradeSelectedCommonsCodecDependency.ARTIFACT + ":" +
                        UpgradeSelectedCommonsCodecDependency.TARGET;
        return method.getArguments().stream().anyMatch(argument ->
                argument instanceof J.Literal literal && target.equals(literal.getValue()) ||
                argument instanceof G.MapLiteral map && UpgradeSelectedCommonsCodecDependency.GROUP.equals(
                        UpgradeSelectedCommonsCodecDependency.mapValue(map, "group")) &&
                    UpgradeSelectedCommonsCodecDependency.ARTIFACT.equals(
                        UpgradeSelectedCommonsCodecDependency.mapValue(map, "name")) &&
                    UpgradeSelectedCommonsCodecDependency.TARGET.equals(
                        UpgradeSelectedCommonsCodecDependency.mapValue(map, "version")));
    }

    private static boolean legacyToolchain(J.MethodInvocation method, Cursor cursor) {
        if (!rootToolchainScope(cursor)) return false;
        return "of".equals(method.getSimpleName()) && method.getArguments().size() == 1 &&
               method.getArguments().get(0) instanceof J.Literal literal && literal.getValue() instanceof Number number &&
               number.intValue() < 8 && method.getSelect() != null &&
               method.getSelect().printTrimmed().endsWith("JavaLanguageVersion");
    }

    private static boolean legacyJavaAssignment(J.Assignment assignment, Cursor cursor) {
        if (!rootJavaAssignmentScope(cursor)) return false;
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) return false;
        return below8(assignment.getAssignment().printTrimmed(cursor).replace("'", "").replace("\"", ""));
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

    private static boolean below8(String value) {
        if (value == null) return false;
        Matcher constant = Pattern.compile("(?:JavaVersion[.])?VERSION_(?:1_)?(\\d+)").matcher(value);
        if (constant.matches()) return Integer.parseInt(constant.group(1)) < 8;
        Matcher legacy = Pattern.compile("1[.](\\d+)").matcher(value);
        if (legacy.matches()) return Integer.parseInt(legacy.group(1)) < 8;
        int major = major(value);
        return major > 0 && major < 8;
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
