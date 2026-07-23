package com.huawei.clouds.openrewrite.springwebflux;

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

/** Dependency ownership, Java baseline, and reactive-stack alignment markers. */
public final class FindSpringWebFluxBuildRisks extends Recipe {
    private static final Set<String> JAVA_KEYS = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");

    static final String OWNER =
            "spring-webflux is versionless, variable, ranged, catalog/platform/BOM-managed, shared, or externally owned; migrate the actual owner and verify that 6.2.19 resolves";
    static final String OUTSIDE =
            "This fixed spring-webflux version is below 6.2.19 but outside the exact source whitelist; it is intentionally not auto-upgraded";
    static final String TARGET_CONFLICT =
            "目标版本冲突（禁止降级）: this Spring declaration is newer than the 6.2.19 target line; it remains unchanged and is not a migration path to the lower target";
    static final String VARIANT =
            "This classified or non-JAR spring-webflux artifact is outside deterministic scope; verify the exact 6.2.19 artifact shape manually";
    static final String JAVA =
            "Spring Framework 6.2 requires Java 17 or newer; align compiler, toolchain, CI, container, and runtime JDKs";
    static final String PARAMETERS =
            "Spring 6.1 removed local-variable-table parameter-name discovery; compile Java with -parameters and retain equivalent Kotlin metadata for WebFlux binding and exception handlers";
    static final String ALIGNMENT =
            "Direct Spring Framework modules must align on 6.2.19; migrate the owning Framework BOM/property instead of mixing release lines";
    static final String ALIGNMENT_OWNER =
            "This Spring Framework companion is versionless, variable, ranged, or externally owned; align its actual BOM/property/platform owner with spring-webflux 6.2.19";
    static final String BOOT =
            "This Spring Boot line does not own Spring Framework 6.2; upgrade the Boot parent/BOM to a compatible 3.4/3.5 line before selecting spring-webflux 6.2.19";
    static final String BOOT_OWNER =
            "Spring Boot parent/BOM management owns the Spring Framework family; upgrade that owner and verify its managed spring-webflux resolves to 6.2.19 instead of forcing a divergent leaf";
    static final String REACTOR =
            "Align the Reactor family with the Spring 6.2.19 platform (Reactor BOM 2024.0.18 / reactor-core 3.7 line); review schedulers, context propagation, and operator behavior";
    static final String NETTY =
            "Align Reactor Netty and Netty as one family; the Spring 6.2.19 platform pins Reactor 2024.0.18 and Netty 4.1.134.Final, while the migration program may select a newer compatible 4.1 patch";
    static final String JACKSON =
            "Spring 6.2 recommends Jackson 2.18/2.19 and retains runtime compatibility with 2.15+; align the Jackson BOM and WebFlux codecs instead of mixing modules";
    static final String KOTLIN =
            "Align Kotlin and kotlinx-coroutines for Spring 6.2 (target source uses Kotlin 1.9.25 and coroutines 1.8.1; Spring 6.1 requires the revised coroutine baseline)";
    static final String VALIDATION =
            "Spring WebFlux 6 uses Jakarta Validation and built-in method validation; replace javax.validation and align jakarta.validation-api 3.x plus the provider";
    static final String WEBJARS =
            "Spring 6.2 deprecates webjars-locator-core integration; use webjars-locator-lite with LiteWebJarsResourceResolver and regression-test resource URLs";

    @Override
    public String getDisplayName() {
        return "Find Spring WebFlux 6.2 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact dependency ownership, variants, Java 17 and parameter metadata, Spring/Boot ownership, " +
               "and Reactor, Netty, Jackson, Kotlin, validation, and WebJars alignment risks.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringWebFluxSupport.generated(source.getSourcePath())) return tree;
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
        SpringWebFluxSupport.PomProperties properties = SpringWebFluxSupport.analyzeProperties(document, ctx);
        MavenScopes scopes = scopes(document, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (SpringWebFluxSupport.isTargetDependency(getCursor(), t)) {
                    if (!SpringWebFluxSupport.isStandardArtifact(t)) {
                        return SpringWebFluxSupport.mark(t, VARIANT);
                    }
                    String raw = t.getChildValue("version").orElse("");
                    String resolved = properties.resolveSafePrimary(raw, getCursor());
                    String message = primaryMessage(raw, resolved);
                    return message == null ? t : SpringWebFluxSupport.markVersionOrOwner(t, message);
                }
                if (!visible(getCursor(), scopes)) return t;

                if (JAVA_KEYS.contains(t.getName())) {
                    String resolved = properties.resolveUnique(t.getValue().orElse(""), getCursor());
                    if (preJava17(resolved)) return SpringWebFluxSupport.mark(t, JAVA);
                }
                if ("maven.compiler.parameters".equals(t.getName()) &&
                    "false".equalsIgnoreCase(t.getValue().orElse("").trim())) {
                    return SpringWebFluxSupport.mark(t, PARAMETERS);
                }
                if (SpringWebFluxSupport.isMavenBuildPlugin(
                        getCursor(), t, "org.apache.maven.plugins", "maven-compiler-plugin")) {
                    String release = t.getChild("configuration").flatMap(c -> c.getChildValue("release"))
                            .orElse(t.getChild("configuration").flatMap(c -> c.getChildValue("source")).orElse(""));
                    if (preJava17(properties.resolveUnique(release, getCursor()))) {
                        return SpringWebFluxSupport.mark(t, JAVA);
                    }
                    String parameters = t.getChild("configuration")
                            .flatMap(c -> c.getChildValue("parameters")).orElse("");
                    if ("false".equalsIgnoreCase(parameters.trim())) {
                        return SpringWebFluxSupport.mark(t, PARAMETERS);
                    }
                }
                if ("parent".equals(t.getName()) &&
                    "org.springframework.boot".equals(t.getChildValue("groupId").orElse(""))) {
                    String version = properties.resolveUnique(
                            t.getChildValue("version").orElse(""), getCursor());
                    return SpringWebFluxSupport.markVersionOrOwner(
                            t, bootMessage(version));
                }
                if (!SpringWebFluxSupport.isProjectDependency(getCursor(), t)) return t;

                String group = t.getChildValue("groupId").orElse("");
                String artifact = t.getChildValue("artifactId").orElse("");
                String version = properties.resolveUnique(t.getChildValue("version").orElse(""), getCursor());
                String message = companionMessage(group, artifact, version);
                return message == null ? t : SpringWebFluxSupport.markVersionOrOwner(t, message);
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean primary = containsStandardPrimary(source, ctx);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                J.Assignment a = super.visitAssignment(assignment, ec);
                return primary && legacyJavaAssignment(a, getCursor())
                        ? SpringWebFluxSupport.mark(a, JAVA) : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringWebFluxSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (primary && legacyToolchain(m)) return SpringWebFluxSupport.mark(m, JAVA);
                if (!dependency) return m;
                m = markTemplates(m, primary);
                String alias = m.getArguments().isEmpty()
                        ? "" : m.getArguments().get(0).printTrimmed(getCursor());
                if (alias.matches("libs(?:[.]versions)?[.]spring[.]webflux")) {
                    return SpringWebFluxSupport.mark(m, OWNER);
                }
                String message = mapMessage(m, primary);
                if (message == null) {
                    G.MapLiteral map = m.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                            .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                    message = map == null ? null : mapMessage(map, primary);
                }
                return message == null ? m : SpringWebFluxSupport.mark(m, message);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringWebFluxSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = isPlatformLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                String message = direct || platform ? coordinateMessage(l.getValue(), primary) : null;
                return message == null ? l : SpringWebFluxSupport.mark(l, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean primary = containsStandardPrimary(source, ctx);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                J.Assignment a = super.visitAssignment(assignment, ec);
                return primary && legacyJavaAssignment(a, getCursor())
                        ? SpringWebFluxSupport.mark(a, JAVA) : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringWebFluxSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (primary && legacyToolchain(m)) return SpringWebFluxSupport.mark(m, JAVA);
                if (!dependency) return m;
                m = markTemplates(m, primary);
                String alias = m.getArguments().isEmpty()
                        ? "" : m.getArguments().get(0).printTrimmed(getCursor());
                return alias.matches("libs(?:[.]versions)?[.]spring[.]webflux")
                        ? SpringWebFluxSupport.mark(m, OWNER) : m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringWebFluxSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = isPlatformLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                String message = direct || platform ? coordinateMessage(l.getValue(), primary) : null;
                return message == null ? l : SpringWebFluxSupport.mark(l, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static String primaryMessage(String raw, String resolved) {
        if (raw == null || raw.trim().isEmpty() || resolved == null ||
            !SpringWebFluxSupport.FIXED_VERSION.matcher(resolved).matches()) return OWNER;
        if (SpringWebFluxSupport.SOURCE_VERSIONS.contains(resolved) ||
            SpringWebFluxSupport.TARGET.equals(resolved)) return null;
        return targetConflict(resolved) ? TARGET_CONFLICT : OUTSIDE;
    }

    private static String companionMessage(String group, String artifact, String version) {
        if ("org.springframework".equals(group) &&
            ("spring-framework-bom".equals(artifact) || artifact.startsWith("spring-")) &&
            !SpringWebFluxSupport.ARTIFACT.equals(artifact)) {
            if (version == null || !SpringWebFluxSupport.FIXED_VERSION.matcher(version).matches()) {
                return ALIGNMENT_OWNER;
            }
            if (SpringWebFluxSupport.TARGET.equals(version)) return null;
            return targetConflict(version) ? TARGET_CONFLICT : ALIGNMENT;
        }
        if ("org.springframework.boot".equals(group) && artifact.startsWith("spring-boot")) {
            return bootMessage(version);
        }
        if ("javax.validation".equals(group)) return VALIDATION;
        if ("jakarta.validation".equals(group) && outdated(version, 3, 0, 0)) return VALIDATION;
        if ("io.projectreactor".equals(group) && "reactor-core".equals(artifact) &&
            outdated(version, 3, 7, 0)) return REACTOR;
        if ("io.projectreactor.netty".equals(group) && artifact.startsWith("reactor-netty") &&
            outdated(version, 1, 2, 0)) return NETTY;
        if ("io.netty".equals(group) && artifact.startsWith("netty-") && oldNetty(version)) return NETTY;
        if (group.startsWith("com.fasterxml.jackson.") && artifact.startsWith("jackson-") &&
            outdated(version, 2, 15, 0)) return JACKSON;
        if ("org.jetbrains.kotlin".equals(group) && artifact.startsWith("kotlin-") &&
            outdated(version, 1, 9, 0)) return KOTLIN;
        if ("org.jetbrains.kotlinx".equals(group) && artifact.startsWith("kotlinx-coroutines-") &&
            outdated(version, 1, 7, 0)) return KOTLIN;
        if ("org.webjars".equals(group) && "webjars-locator-core".equals(artifact)) return WEBJARS;
        return null;
    }

    private static String coordinateMessage(Object value, boolean companions) {
        if (!(value instanceof String coordinate)) return null;
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2) return null;
        String group = parts[0];
        String artifact = parts[1];
        if (SpringWebFluxSupport.GROUP.equals(group) &&
            SpringWebFluxSupport.ARTIFACT.equals(artifact)) {
            if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return VARIANT;
            if (parts.length != 3 ||
                !SpringWebFluxSupport.FIXED_VERSION.matcher(parts[2]).matches()) return OWNER;
            if (SpringWebFluxSupport.SOURCE_VERSIONS.contains(parts[2]) ||
                SpringWebFluxSupport.TARGET.equals(parts[2])) return null;
            return targetConflict(parts[2]) ? TARGET_CONFLICT : OUTSIDE;
        }
        if (!companions) return null;
        String version = parts.length == 3 &&
                         SpringWebFluxSupport.FIXED_VERSION.matcher(parts[2]).matches()
                ? parts[2] : null;
        return companionMessage(group, artifact, version);
    }

    private static String mapMessage(J.MethodInvocation invocation, boolean companions) {
        return dependencyMessage(
                SpringWebFluxSupport.mapValue(invocation, "group"),
                SpringWebFluxSupport.mapValue(invocation, "name"),
                SpringWebFluxSupport.mapValue(invocation, "version"),
                SpringWebFluxSupport.hasVariant(invocation),
                companions);
    }

    private static String mapMessage(G.MapLiteral map, boolean companions) {
        return dependencyMessage(
                SpringWebFluxSupport.mapValue(map, "group"),
                SpringWebFluxSupport.mapValue(map, "name"),
                SpringWebFluxSupport.mapValue(map, "version"),
                SpringWebFluxSupport.hasVariant(map),
                companions);
    }

    private static String dependencyMessage(String group, String artifact, String version,
                                            boolean variant, boolean companions) {
        if (SpringWebFluxSupport.GROUP.equals(group) &&
            SpringWebFluxSupport.ARTIFACT.equals(artifact)) {
            if (variant) return VARIANT;
            if (version == null ||
                !SpringWebFluxSupport.FIXED_VERSION.matcher(version).matches()) return OWNER;
            if (SpringWebFluxSupport.SOURCE_VERSIONS.contains(version) ||
                SpringWebFluxSupport.TARGET.equals(version)) return null;
            return targetConflict(version) ? TARGET_CONFLICT : OUTSIDE;
        }
        return companions && group != null && artifact != null
                ? companionMessage(group, artifact, version) : null;
    }

    private static J.MethodInvocation markTemplates(J.MethodInvocation invocation, boolean companions) {
        return invocation.withArguments(invocation.getArguments().stream().map(argument -> {
            TemplateCoordinate coordinate = templateCoordinate(argument);
            if (coordinate == null) return argument;
            String message;
            if (SpringWebFluxSupport.GROUP.equals(coordinate.group()) &&
                SpringWebFluxSupport.ARTIFACT.equals(coordinate.artifact())) {
                message = coordinate.variant() ? VARIANT : OWNER;
            } else {
                message = companions
                        ? companionMessage(coordinate.group(), coordinate.artifact(), null) : null;
            }
            return message == null ? argument : SpringWebFluxSupport.mark(argument, message);
        }).toList());
    }

    /**
     * Only accept a coordinate when the first static fragment is exactly
     * "group:artifact:". This prevents substring and lookalike GString matches.
     */
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
               SpringWebFluxSupport.isRootGradleDependency(owner, dependency);
    }

    private static boolean containsStandardPrimary(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringWebFluxSupport.isRootGradleDependency(getCursor(), method);
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
                boolean direct = SpringWebFluxSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && standardInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean standardInvocation(J.MethodInvocation invocation) {
        if (SpringWebFluxSupport.GROUP.equals(SpringWebFluxSupport.mapValue(invocation, "group")) &&
            SpringWebFluxSupport.ARTIFACT.equals(SpringWebFluxSupport.mapValue(invocation, "name")) &&
            !SpringWebFluxSupport.hasVariant(invocation)) return true;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && standardCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map &&
                SpringWebFluxSupport.GROUP.equals(SpringWebFluxSupport.mapValue(map, "group")) &&
                SpringWebFluxSupport.ARTIFACT.equals(SpringWebFluxSupport.mapValue(map, "name")) &&
                !SpringWebFluxSupport.hasVariant(map)) return true;
            TemplateCoordinate coordinate = templateCoordinate(argument);
            if (coordinate != null &&
                SpringWebFluxSupport.GROUP.equals(coordinate.group()) &&
                SpringWebFluxSupport.ARTIFACT.equals(coordinate.artifact()) &&
                !coordinate.variant()) return true;
        }
        return false;
    }

    private static boolean standardCoordinate(Object value) {
        if (!(value instanceof String coordinate)) return false;
        String base = SpringWebFluxSupport.GROUP + ":" + SpringWebFluxSupport.ARTIFACT;
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
                if (SpringWebFluxSupport.isStandardTargetDependency(getCursor(), t)) {
                    String scope = SpringWebFluxSupport.scope(getCursor());
                    if ("ROOT".equals(scope)) root[0] = true;
                    else profiles.add(scope);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return new MavenScopes(root[0], Set.copyOf(profiles));
    }

    private static boolean visible(Cursor cursor, MavenScopes scopes) {
        String scope = SpringWebFluxSupport.scope(cursor);
        if ("ROOT".equals(scope)) return scopes.root() || !scopes.profiles().isEmpty();
        return scopes.root() || scopes.profiles().contains(scope);
    }

    private static boolean preJava17(String value) {
        return value != null &&
               (value.matches("1[.][0-9]") || value.matches("(?:[1-9]|1[0-6])"));
    }

    private static boolean compatibleBoot(String version) {
        int[] numbers = numbers(version);
        return numbers != null && numbers[0] == 3 && (numbers[1] == 4 || numbers[1] == 5);
    }

    private static String bootMessage(String version) {
        int[] actual = numbers(version);
        if (actual == null) return BOOT_OWNER;
        if (actual[0] > 3 || actual[0] == 3 && actual[1] > 5) return TARGET_CONFLICT;
        return compatibleBoot(version) ? BOOT_OWNER : BOOT;
    }

    private static boolean targetConflict(String version) {
        int[] actual = numbers(version);
        int[] target = numbers(SpringWebFluxSupport.TARGET);
        if (actual == null || target == null) return false;
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] != target[i]) return actual[i] > target[i];
        }
        return false;
    }

    private static boolean outdated(String version, int major, int minor, int patch) {
        int[] actual = numbers(version);
        if (actual == null) return true;
        if (actual[0] != major) return actual[0] < major;
        if (actual[1] != minor) return actual[1] < minor;
        return actual[2] < patch;
    }

    private static boolean oldNetty(String version) {
        int[] actual = numbers(version);
        if (actual == null) return true;
        if (actual[0] < 4) return true;
        return actual[0] == 4 && actual[1] == 1 && actual[2] < 134;
    }

    private static int[] numbers(String version) {
        if (version == null) return null;
        Matcher matcher = Pattern.compile("^(\\d+)(?:[.](\\d+))?(?:[.](\\d+))?.*").matcher(version);
        if (!matcher.matches()) return null;
        return new int[]{
                Integer.parseInt(matcher.group(1)),
                matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2)),
                matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3))
        };
    }

    private static boolean legacyJavaAssignment(J.Assignment assignment, Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") &&
            !variable.endsWith("targetCompatibility")) return false;
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

    private record MavenScopes(boolean root, Set<String> profiles) {
    }

    private record TemplateCoordinate(String group, String artifact, boolean variant) {
    }
}
