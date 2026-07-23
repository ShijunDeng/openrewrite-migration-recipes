package com.huawei.clouds.openrewrite.springweb;

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
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dependency ownership, Java baseline, and Spring Web stack alignment markers. */
public final class FindSpringWebBuildRisks extends Recipe {
    private static final Set<String> JAVA_KEYS = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");

    static final String OWNER =
            "spring-web is versionless, variable, ranged, catalog/platform/BOM-managed, shared, or externally owned; migrate the actual owner and verify that 6.2.19 resolves";
    static final String OUTSIDE =
            "This fixed spring-web version is below 6.2.19 but outside the exact source whitelist; it is intentionally not auto-upgraded";
    static final String TARGET_CONFLICT =
            "目标版本冲突（禁止降级）: this Spring declaration is newer than the 6.2.19 target line; it remains unchanged";
    static final String VARIANT =
            "This classified or non-JAR spring-web artifact is outside deterministic scope; verify the exact 6.2.19 artifact shape manually";
    static final String JAVA =
            "Spring Framework 6.2 requires Java 17 or newer; align compiler, toolchain, CI, container, and runtime JDKs";
    static final String PARAMETERS =
            "Spring 6.1 removed local-variable-table parameter-name discovery; compile Java with -parameters and retain equivalent Kotlin metadata for web binding and exception handlers";
    static final String ALIGNMENT =
            "Direct Spring Framework modules must align on 6.2.19; migrate the owning Framework BOM/property instead of mixing release lines";
    static final String ALIGNMENT_OWNER =
            "This Spring Framework companion is versionless, variable, ranged, or externally owned; align its actual BOM/property/platform owner with spring-web 6.2.19";
    static final String BOOT =
            "This Spring Boot line does not own Spring Framework 6.2; upgrade the Boot parent/BOM to a compatible 3.4/3.5 line before selecting spring-web 6.2.19";
    static final String BOOT_OWNER =
            "Spring Boot parent/BOM management owns the Spring Framework family; upgrade that owner and verify its managed spring-web resolves to 6.2.19 instead of forcing a divergent leaf";
    static final String JAKARTA =
            "Spring Web 6 uses Jakarta EE namespaces; align Servlet 6, Validation 3, JAXB 3, JSON-P/JSON-B 2, and Activation 2 APIs plus providers and containers";
    static final String HTTP_CLIENT =
            "Spring Web 6 HttpComponents support uses Apache HttpClient 5; replace the 4.x org.apache.httpcomponents family and align client5/httpcore5 as one compatible family";
    static final String JACKSON =
            "Align the Jackson family for Spring 6.2 (2.15+ runtime baseline, normally the owning Boot/Spring platform version) and regression-test message converters";
    static final String REACTOR_NETTY =
            "Align Reactor Netty and Netty with the Spring 6.2 platform when WebClient/ReactorResourceFactory is used; do not mix independently selected release trains";
    static final String JETTY =
            "Align Jetty reactive HTTP client artifacts with a Jakarta-compatible Jetty 12 line and regression-test connector lifecycle and buffers";
    static final String MICROMETER =
            "Align Micrometer Observation with the Spring 6.2 platform and regression-test client observation conventions, key names, URI templates, and error tags";

    @Override
    public String getDisplayName() {
        return "Find Spring Web 6.2 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact dependency ownership, variants, Java 17 and parameter metadata, Spring/Boot ownership, " +
               "and Jakarta, Apache HttpClient, Jackson, Reactor Netty, Jetty, and Micrometer alignment risks.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || SpringWebSupport.generated(source.getSourcePath())) {
                    return tree;
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document xml && "pom.xml".equals(fileName)) return maven(xml, ctx);
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return groovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return kotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document document, ExecutionContext ctx) {
        SpringWebSupport.PomProperties properties = SpringWebSupport.analyzeProperties(document, ctx);
        MavenScopes scopes = scopes(document, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (SpringWebSupport.isTargetDependency(getCursor(), t)) {
                    if (!SpringWebSupport.isStandardArtifact(t)) return SpringWebSupport.mark(t, VARIANT);
                    String raw = t.getChildValue("version").orElse("");
                    String resolved = properties.resolveSafePrimary(raw, getCursor());
                    String message = primaryMessage(raw, resolved);
                    return message == null ? t : SpringWebSupport.markVersionOrOwner(t, message);
                }
                if (!visible(getCursor(), scopes)) return t;

                if (JAVA_KEYS.contains(t.getName())) {
                    String resolved = properties.resolveUnique(t.getValue().orElse(""), getCursor());
                    if (preJava17(resolved)) return SpringWebSupport.mark(t, JAVA);
                }
                if ("maven.compiler.parameters".equals(t.getName()) &&
                    "false".equalsIgnoreCase(t.getValue().orElse("").trim())) {
                    return SpringWebSupport.mark(t, PARAMETERS);
                }
                if (SpringWebSupport.isMavenBuildPlugin(
                        getCursor(), t, "org.apache.maven.plugins", "maven-compiler-plugin")) {
                    String release = t.getChild("configuration").flatMap(c -> c.getChildValue("release"))
                            .orElse(t.getChild("configuration").flatMap(c -> c.getChildValue("source")).orElse(""));
                    if (preJava17(properties.resolveUnique(release, getCursor()))) {
                        return SpringWebSupport.mark(t, JAVA);
                    }
                    String parameters = t.getChild("configuration")
                            .flatMap(c -> c.getChildValue("parameters")).orElse("");
                    if ("false".equalsIgnoreCase(parameters.trim())) {
                        return SpringWebSupport.mark(t, PARAMETERS);
                    }
                }
                if ("parent".equals(t.getName()) &&
                    "org.springframework.boot".equals(t.getChildValue("groupId").orElse(""))) {
                    String version = properties.resolveUnique(t.getChildValue("version").orElse(""), getCursor());
                    return SpringWebSupport.markVersionOrOwner(t, bootMessage(version));
                }
                if (!SpringWebSupport.isProjectDependency(getCursor(), t)) return t;
                String group = t.getChildValue("groupId").orElse("");
                String artifact = t.getChildValue("artifactId").orElse("");
                String version = properties.resolveUnique(t.getChildValue("version").orElse(""), getCursor());
                String message = companionMessage(group, artifact, version);
                return message == null ? t : SpringWebSupport.markVersionOrOwner(t, message);
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean primary = containsStandardPrimary(source, ctx);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                J.Assignment a = super.visitAssignment(assignment, ec);
                return primary && rootBuildSetting(getCursor()) && legacyJavaAssignment(a, getCursor())
                        ? SpringWebSupport.mark(a, JAVA) : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringWebSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (primary && rootBuildSetting(getCursor()) && legacyToolchain(m)) {
                    return SpringWebSupport.mark(m, JAVA);
                }
                if (!dependency) return m;
                m = markTemplates(m, primary);
                String alias = m.getArguments().isEmpty() ? "" :
                        m.getArguments().get(0).printTrimmed(getCursor());
                if (alias.matches("libs(?:[.]versions)?[.]spring[.]web")) {
                    return SpringWebSupport.mark(m, OWNER);
                }
                String message = mapMessage(m, primary);
                if (message == null) {
                    G.MapLiteral map = m.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                            .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                    message = map == null ? null : mapMessage(map, primary);
                }
                return message == null ? m : SpringWebSupport.mark(m, message);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringWebSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = isPlatformLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                String message = direct || platform ? coordinateMessage(l.getValue(), primary) : null;
                return message == null ? l : SpringWebSupport.mark(l, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean primary = containsStandardPrimary(source, ctx);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                J.Assignment a = super.visitAssignment(assignment, ec);
                return primary && rootBuildSetting(getCursor()) && legacyJavaAssignment(a, getCursor())
                        ? SpringWebSupport.mark(a, JAVA) : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringWebSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (primary && rootBuildSetting(getCursor()) && legacyToolchain(m)) {
                    return SpringWebSupport.mark(m, JAVA);
                }
                if (!dependency) return m;
                m = markTemplates(m, primary);
                String alias = m.getArguments().isEmpty() ? "" :
                        m.getArguments().get(0).printTrimmed(getCursor());
                return alias.matches("libs(?:[.]versions)?[.]spring[.]web")
                        ? SpringWebSupport.mark(m, OWNER) : m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringWebSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = isPlatformLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                String message = direct || platform ? coordinateMessage(l.getValue(), primary) : null;
                return message == null ? l : SpringWebSupport.mark(l, message);
            }
        }.visitNonNull(source, ctx);
    }

    static String primaryMessage(String raw, String resolved) {
        if (raw == null || raw.trim().isEmpty() || resolved == null ||
            !SpringWebSupport.FIXED_VERSION.matcher(resolved).matches()) return OWNER;
        if (SpringWebSupport.SOURCE_VERSIONS.contains(resolved) || SpringWebSupport.TARGET.equals(resolved)) {
            return null;
        }
        return SpringWebSupport.compareVersions(resolved, SpringWebSupport.TARGET) > 0
                ? TARGET_CONFLICT : OUTSIDE;
    }

    private static String companionMessage(String group, String artifact, String version) {
        if ("org.springframework".equals(group) &&
            ("spring-framework-bom".equals(artifact) || artifact.startsWith("spring-")) &&
            !SpringWebSupport.ARTIFACT.equals(artifact)) {
            if (version == null || !SpringWebSupport.FIXED_VERSION.matcher(version).matches()) {
                return ALIGNMENT_OWNER;
            }
            if (SpringWebSupport.TARGET.equals(version)) return null;
            return SpringWebSupport.compareVersions(version, SpringWebSupport.TARGET) > 0
                    ? TARGET_CONFLICT : ALIGNMENT;
        }
        if ("org.springframework.boot".equals(group) && artifact.startsWith("spring-boot")) {
            return bootMessage(version);
        }
        if ("javax.servlet".equals(group) || "javax.validation".equals(group) ||
            "javax.xml.bind".equals(group) ||
            "javax.json".equals(group) || "javax.activation".equals(group)) return JAKARTA;
        if ("jakarta.servlet".equals(group) && outdated(version, 6, 0, 0) ||
            "jakarta.validation".equals(group) && outdated(version, 3, 0, 0) ||
            "jakarta.xml.bind".equals(group) && outdated(version, 3, 0, 0) ||
            "jakarta.json".equals(group) && outdated(version, 2, 0, 0) ||
            "jakarta.json.bind".equals(group) && outdated(version, 2, 0, 0) ||
            "jakarta.activation".equals(group) && outdated(version, 2, 0, 0)) return JAKARTA;
        if ("org.apache.httpcomponents".equals(group) ||
            "org.apache.httpcomponents.client5".equals(group) && outdated(version, 5, 3, 0) ||
            "org.apache.httpcomponents.core5".equals(group) && outdated(version, 5, 2, 0)) {
            return HTTP_CLIENT;
        }
        if (group.startsWith("com.fasterxml.jackson.") && artifact.startsWith("jackson-") &&
            outdated(version, 2, 15, 0)) return JACKSON;
        if ("io.projectreactor.netty".equals(group) && artifact.startsWith("reactor-netty") ||
            "io.netty".equals(group) && artifact.startsWith("netty-")) return REACTOR_NETTY;
        if (group.startsWith("org.eclipse.jetty") && artifact.contains("reactive")) return JETTY;
        if ("io.micrometer".equals(group) && artifact.startsWith("micrometer-observation") &&
            outdated(version, 1, 14, 0)) return MICROMETER;
        return null;
    }

    private static String coordinateMessage(Object value, boolean companions) {
        if (!(value instanceof String coordinate)) return null;
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2) return null;
        String group = parts[0];
        String artifact = parts[1];
        if (SpringWebSupport.GROUP.equals(group) && SpringWebSupport.ARTIFACT.equals(artifact)) {
            if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return VARIANT;
            if (parts.length != 3 || !SpringWebSupport.FIXED_VERSION.matcher(parts[2]).matches()) return OWNER;
            if (SpringWebSupport.SOURCE_VERSIONS.contains(parts[2]) ||
                SpringWebSupport.TARGET.equals(parts[2])) return null;
            return SpringWebSupport.compareVersions(parts[2], SpringWebSupport.TARGET) > 0
                    ? TARGET_CONFLICT : OUTSIDE;
        }
        if (!companions) return null;
        String version = parts.length == 3 && SpringWebSupport.FIXED_VERSION.matcher(parts[2]).matches()
                ? parts[2] : null;
        return companionMessage(group, artifact, version);
    }

    private static String mapMessage(J.MethodInvocation invocation, boolean companions) {
        return dependencyMessage(
                SpringWebSupport.mapValue(invocation, "group"),
                SpringWebSupport.mapValue(invocation, "name"),
                SpringWebSupport.mapValue(invocation, "version"),
                SpringWebSupport.hasVariant(invocation), companions);
    }

    private static String mapMessage(G.MapLiteral map, boolean companions) {
        return dependencyMessage(
                SpringWebSupport.mapValue(map, "group"),
                SpringWebSupport.mapValue(map, "name"),
                SpringWebSupport.mapValue(map, "version"),
                SpringWebSupport.hasVariant(map), companions);
    }

    private static String dependencyMessage(String group, String artifact, String version,
                                            boolean variant, boolean companions) {
        if (SpringWebSupport.GROUP.equals(group) && SpringWebSupport.ARTIFACT.equals(artifact)) {
            if (variant) return VARIANT;
            if (version == null || !SpringWebSupport.FIXED_VERSION.matcher(version).matches()) return OWNER;
            if (SpringWebSupport.SOURCE_VERSIONS.contains(version) ||
                SpringWebSupport.TARGET.equals(version)) return null;
            return SpringWebSupport.compareVersions(version, SpringWebSupport.TARGET) > 0
                    ? TARGET_CONFLICT : OUTSIDE;
        }
        return companions && group != null && artifact != null
                ? companionMessage(group, artifact, version) : null;
    }

    private static J.MethodInvocation markTemplates(J.MethodInvocation invocation, boolean companions) {
        return invocation.withArguments(invocation.getArguments().stream().map(argument -> {
            TemplateCoordinate coordinate = templateCoordinate(argument);
            if (coordinate == null) return argument;
            String message;
            if (SpringWebSupport.GROUP.equals(coordinate.group()) &&
                SpringWebSupport.ARTIFACT.equals(coordinate.artifact())) {
                message = coordinate.variant() ? VARIANT : OWNER;
            } else {
                message = companions ? companionMessage(coordinate.group(), coordinate.artifact(), null) : null;
            }
            return message == null ? argument : SpringWebSupport.mark(argument, message);
        }).toList());
    }

    private static TemplateCoordinate templateCoordinate(J argument) {
        List<J> strings;
        if (argument instanceof G.GString template) strings = template.getStrings();
        else if (argument instanceof K.StringTemplate template) strings = template.getStrings();
        else return null;
        List<String> parts = strings.stream().filter(J.Literal.class::isInstance)
                .map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).toList();
        if (parts.isEmpty()) return null;
        String first = parts.get(0);
        if (!first.endsWith(":")) return null;
        String[] coordinate = first.substring(0, first.length() - 1).split(":", -1);
        if (coordinate.length != 2 || coordinate[0].isEmpty() || coordinate[1].isEmpty()) return null;
        boolean variant = parts.stream().skip(1).anyMatch(part -> part.contains(":") || part.contains("@"));
        return new TemplateCoordinate(coordinate[0], coordinate[1], variant);
    }

    private static boolean isPlatformLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof J.MethodInvocation platform) ||
            !("platform".equals(platform.getSimpleName()) ||
              "enforcedPlatform".equals(platform.getSimpleName()))) return false;
        Cursor owner = parent.getParent();
        while (owner != null && !(owner.getValue() instanceof J.MethodInvocation)) owner = owner.getParent();
        return owner != null && owner.getValue() instanceof J.MethodInvocation dependency &&
               SpringWebSupport.isRootGradleDependency(owner, dependency);
    }

    private static boolean containsStandardPrimary(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringWebSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && standardInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean containsStandardPrimary(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringWebSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && standardInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean standardInvocation(J.MethodInvocation invocation) {
        if (SpringWebSupport.GROUP.equals(SpringWebSupport.mapValue(invocation, "group")) &&
            SpringWebSupport.ARTIFACT.equals(SpringWebSupport.mapValue(invocation, "name")) &&
            !SpringWebSupport.hasVariant(invocation)) return true;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && standardCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map &&
                SpringWebSupport.GROUP.equals(SpringWebSupport.mapValue(map, "group")) &&
                SpringWebSupport.ARTIFACT.equals(SpringWebSupport.mapValue(map, "name")) &&
                !SpringWebSupport.hasVariant(map)) return true;
            TemplateCoordinate coordinate = templateCoordinate(argument);
            if (coordinate != null && SpringWebSupport.GROUP.equals(coordinate.group()) &&
                SpringWebSupport.ARTIFACT.equals(coordinate.artifact()) && !coordinate.variant()) return true;
        }
        return false;
    }

    private static boolean standardCoordinate(Object value) {
        if (!(value instanceof String coordinate)) return false;
        String base = SpringWebSupport.GROUP + ":" + SpringWebSupport.ARTIFACT;
        if (base.equals(coordinate)) return true;
        if (!coordinate.startsWith(base + ":")) return false;
        String suffix = coordinate.substring(base.length() + 1);
        return !suffix.contains(":") && !suffix.contains("@");
    }

    private static MavenScopes scopes(Xml.Document document, ExecutionContext ctx) {
        boolean[] root = {false};
        Set<String> profiles = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (SpringWebSupport.isStandardTargetDependency(getCursor(), t)) {
                    String scope = SpringWebSupport.scope(getCursor());
                    if ("ROOT".equals(scope)) root[0] = true;
                    else profiles.add(scope);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return new MavenScopes(root[0], Set.copyOf(profiles));
    }

    private static boolean visible(Cursor cursor, MavenScopes scopes) {
        String scope = SpringWebSupport.scope(cursor);
        if ("ROOT".equals(scope)) return scopes.root() || !scopes.profiles().isEmpty();
        return scopes.root() || scopes.profiles().contains(scope);
    }

    private static boolean preJava17(String value) {
        return value != null && (value.matches("1[.][0-9]") || value.matches("(?:[1-9]|1[0-6])"));
    }

    private static String bootMessage(String version) {
        int[] actual = SpringWebSupport.numbers(version);
        if (actual == null) return BOOT_OWNER;
        if (actual[0] > 3 || actual[0] == 3 && actual[1] > 5) return TARGET_CONFLICT;
        return actual[0] == 3 && (actual[1] == 4 || actual[1] == 5) ? BOOT_OWNER : BOOT;
    }

    private static boolean outdated(String version, int major, int minor, int patch) {
        int[] actual = SpringWebSupport.numbers(version);
        if (actual == null) return true;
        if (actual[0] != major) return actual[0] < major;
        if (actual[1] != minor) return actual[1] < minor;
        return actual[2] < patch;
    }

    private static boolean legacyJavaAssignment(J.Assignment assignment, Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) return false;
        String value = assignment.getAssignment().printTrimmed(cursor).replace("'", "").replace("\"", "");
        Matcher numeric = Pattern.compile("(?:1[.])?(\\d+)").matcher(value);
        if (numeric.matches()) return Integer.parseInt(numeric.group(1)) < 17;
        Matcher constant = Pattern.compile("(?:JavaVersion[.])?VERSION_(?:1_)?(\\d+)").matcher(value);
        return constant.matches() && Integer.parseInt(constant.group(1)) < 17;
    }

    private static boolean legacyToolchain(J.MethodInvocation method) {
        if (!"of".equals(method.getSimpleName()) || method.getArguments().size() != 1 ||
            !(method.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof Number number) || method.getSelect() == null) return false;
        return number.intValue() < 17 &&
               method.getSelect().printTrimmed().endsWith("JavaLanguageVersion");
    }

    private static boolean rootBuildSetting(Cursor cursor) {
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (!(current.getValue() instanceof J.MethodInvocation invocation)) continue;
            if (Set.of("project", "subprojects", "allprojects", "buildscript")
                    .contains(invocation.getSimpleName())) return false;
        }
        return true;
    }

    private record MavenScopes(boolean root, Set<String> profiles) {
    }

    private record TemplateCoordinate(String group, String artifact, boolean variant) {
    }
}
