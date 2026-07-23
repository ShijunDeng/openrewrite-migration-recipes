package com.huawei.clouds.openrewrite.springwebmvc;

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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Build ownership, baseline, and companion alignment markers for Spring Web MVC 6.2.19. */
public final class FindSpringWebMvc6BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_KEYS = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");
    private static final Set<String> LEGACY_COORDINATES = Set.of(
            "javax.servlet:javax.servlet-api", "javax.servlet:servlet-api", "javax.validation:validation-api",
            "javax.annotation:javax.annotation-api", "commons-fileupload:commons-fileupload",
            "org.webjars:webjars-locator-core");
    private static final Set<String> SERVLET_CONTAINERS = Set.of(
            "org.apache.tomcat.embed:tomcat-embed-core", "org.apache.tomcat:tomcat-catalina",
            "org.eclipse.jetty:jetty-server", "io.undertow:undertow-servlet");

    static final String OWNER =
            "spring-webmvc is versionless, variable, ranged, dynamic, catalog/platform/BOM-managed, shared, or externally owned; migrate the actual owner and verify that 6.2.19 resolves";
    static final String OUTSIDE =
            "This fixed spring-webmvc version is outside the workbook-visible source set and target; it is intentionally not auto-upgraded";
    static final String TARGET_CONFLICT = "目标版本冲突（禁止降级）";
    static final String VARIANT =
            "This classified or non-JAR spring-webmvc artifact is outside deterministic scope; verify the exact 6.2.19 artifact shape manually";
    static final String JAVA =
            "Spring Framework 6.2 requires Java 17 or newer; align compiler, toolchain, CI, container, and runtime JDKs";
    static final String PARAMETERS =
            "Spring 6.1 no longer discovers parameter names from local-variable tables; enable Java -parameters and equivalent Kotlin/Groovy metadata for MVC binding and exception handling";
    static final String ALIGNMENT =
            "All directly versioned Spring Framework modules must align on 6.2.19; migrate the owning Spring Framework BOM/property rather than mixing lines";
    static final String ALIGNMENT_OWNER =
            "This Spring Framework companion version is absent, variable, ranged, or externally owned; align its actual BOM/property/platform owner with spring-webmvc 6.2.19";
    static final String JAKARTA =
            "Spring Web MVC 6 uses Jakarta EE namespaces; migrate this javax dependency and its source imports as one coordinated change";
    static final String INTEGRATION =
            "This removed or behavior-sensitive MVC integration requires an explicit migration: Tiles is removed, CommonsMultipartResolver is removed, and webjars-locator-core is superseded by locator-lite";
    static final String CONTAINER =
            "Verify that the Servlet container supports Jakarta Servlet 5/6 and align its complete family; Tomcat 9, Jetty 9/10, and other javax-era runtimes are incompatible";
    static final String BOOT =
            "Spring Framework 6.2 belongs to the Spring Boot 3.4/3.5 management lines; align the Boot parent/BOM instead of overriding spring-webmvc under an older, newer, or externally owned line";

    @Override
    public String getDisplayName() {
        return "Find Spring Web MVC 6.2 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark local or external dependency ownership, variants, Java 17, Spring family alignment, Jakarta, " +
               "Servlet container, removed integration, and Spring Boot compatibility risks with owner isolation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || UpgradeSelectedSpringWebMvcDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        MavenScopes scopes = scopes(source, ctx);
        ScopedProperties properties = properties(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedSpringWebMvcDependency.isTargetDependency(getCursor(), t)) {
                    if (!UpgradeSelectedSpringWebMvcDependency.isStandardArtifact(t)) return mark(t, VARIANT);
                    String message = primaryMessage(t.getChildValue("version").map(String::trim).orElse(""),
                            getCursor(), properties);
                    return message == null ? t : markVersionOrOwner(t, message);
                }
                if (!visible(getCursor(), scopes)) return t;
                if (JAVA_KEYS.contains(t.getName())) {
                    String resolved = resolve(t.getValue().orElse(""), getCursor(), properties);
                    if (resolved != null && preJava17(resolved)) return mark(t, JAVA);
                }
                if ("maven.compiler.parameters".equals(t.getName()) &&
                    "false".equalsIgnoreCase(t.getValue().orElse("").trim())) return mark(t, PARAMETERS);
                if (isMavenCompilerPlugin(getCursor(), t)) {
                    String release = t.getChild("configuration").flatMap(c -> c.getChildValue("release"))
                            .orElse(t.getChild("configuration").flatMap(c -> c.getChildValue("source")).orElse(""));
                    String resolved = resolve(release, getCursor(), properties);
                    if (resolved != null && preJava17(resolved)) return mark(t, JAVA);
                    String parameterMetadata = t.getChild("configuration")
                            .flatMap(c -> c.getChildValue("parameters")).orElse("");
                    if ("false".equalsIgnoreCase(parameterMetadata.trim())) return mark(t, PARAMETERS);
                }
                if ("parent".equals(t.getName()) &&
                    "org.springframework.boot".equals(t.getChildValue("groupId").orElse("")) &&
                    incompatibleBoot(resolve(t.getChildValue("version").orElse(""), getCursor(), properties))) {
                    return markVersionOrOwner(t, BOOT);
                }
                if (!UpgradeSelectedSpringWebMvcDependency.isProjectDependency(getCursor(), t)) return t;
                String group = t.getChildValue("groupId").orElse("");
                String artifact = t.getChildValue("artifactId").orElse("");
                String coordinate = group + ":" + artifact;
                String version = resolve(t.getChildValue("version").orElse(""), getCursor(), properties);
                if ("org.springframework".equals(group) && "spring-framework-bom".equals(artifact)) {
                    return aligned(version) ? t : markVersionOrOwner(t, version == null ? ALIGNMENT_OWNER : ALIGNMENT);
                }
                if ("org.springframework".equals(group) && artifact.startsWith("spring-") &&
                    !UpgradeSelectedSpringWebMvcDependency.ARTIFACT.equals(artifact)) {
                    return aligned(version) ? t : markVersionOrOwner(t, version == null ? ALIGNMENT_OWNER : ALIGNMENT);
                }
                if ("org.springframework.boot".equals(group) && "spring-boot-dependencies".equals(artifact) &&
                    incompatibleBoot(version)) {
                    return markVersionOrOwner(t, BOOT);
                }
                if (coordinate.startsWith("org.apache.tiles:") || LEGACY_COORDINATES.contains(coordinate)) {
                    return mark(t, coordinate.startsWith("javax.") ? JAKARTA : INTEGRATION);
                }
                if (SERVLET_CONTAINERS.contains(coordinate) && legacyContainer(artifact, version)) return mark(t, CONTAINER);
                return t;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean standard = containsStandardPrimary(source, ctx);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                J.Assignment a = super.visitAssignment(assignment, ec);
                return standard && legacyJavaAssignment(a, getCursor()) ? mark(a, JAVA) : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = UpgradeSelectedSpringWebMvcDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (standard && legacyToolchain(m)) return mark(m, JAVA);
                if (!dependency) return m;
                m = markDynamicTemplateArgument(m, standard);
                String alias = m.getArguments().isEmpty() ? "" : m.getArguments().get(0).printTrimmed(getCursor());
                if (alias.matches("libs(?:[.]versions)?[.]spring[.]webmvc")) return mark(m, OWNER);
                String message = mapMessage(m, standard);
                if (message == null) {
                    G.MapLiteral map = m.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                            .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                    message = map == null ? null : mapMessage(map, standard);
                }
                return message == null ? m : mark(m, message);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedSpringWebMvcDependency.isDirectDependencyLiteral(getCursor());
                boolean platform = isPlatformLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                String message = direct || platform ? coordinateMessage(l.getValue(), standard) : null;
                return message == null ? l : mark(l, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean standard = containsStandardPrimary(source, ctx);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ec) {
                J.Assignment a = super.visitAssignment(assignment, ec);
                return standard && legacyJavaAssignment(a, getCursor()) ? mark(a, JAVA) : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = UpgradeSelectedSpringWebMvcDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (standard && legacyToolchain(m)) return mark(m, JAVA);
                if (!dependency) return m;
                m = markDynamicTemplateArgument(m, standard);
                String alias = m.getArguments().isEmpty() ? "" : m.getArguments().get(0).printTrimmed(getCursor());
                if (alias.matches("libs(?:[.]versions)?[.]spring[.]webmvc")) return mark(m, OWNER);
                return m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedSpringWebMvcDependency.isDirectDependencyLiteral(getCursor());
                boolean platform = isPlatformLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                String message = direct || platform ? coordinateMessage(l.getValue(), standard) : null;
                return message == null ? l : mark(l, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static String primaryMessage(String raw, Cursor cursor, ScopedProperties properties) {
        if (raw.isEmpty()) return OWNER;
        String resolved = resolve(raw, cursor, properties);
        if (resolved == null || !FIXED.matcher(resolved).matches()) return OWNER;
        if (UpgradeSelectedSpringWebMvcDependency.SOURCE_VERSIONS.contains(resolved) ||
            UpgradeSelectedSpringWebMvcDependency.TARGET.equals(resolved)) return null;
        return higherThanTarget(resolved) ? TARGET_CONFLICT : OUTSIDE;
    }

    private static String coordinateMessage(Object literal, boolean companions) {
        if (!(literal instanceof String value)) return null;
        String[] parts = value.split(":", -1);
        if (parts.length < 2) return null;
        String group = parts[0];
        String artifact = parts[1];
        if (UpgradeSelectedSpringWebMvcDependency.GROUP.equals(group) &&
            UpgradeSelectedSpringWebMvcDependency.ARTIFACT.equals(artifact)) {
            if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return VARIANT;
            if (parts.length != 3 || !FIXED.matcher(parts[2]).matches()) return OWNER;
            if (UpgradeSelectedSpringWebMvcDependency.SOURCE_VERSIONS.contains(parts[2])) return null;
            return UpgradeSelectedSpringWebMvcDependency.TARGET.equals(parts[2]) ? null :
                    higherThanTarget(parts[2]) ? TARGET_CONFLICT : OUTSIDE;
        }
        if (!companions) return null;
        String coordinate = group + ":" + artifact;
        if ("org.springframework".equals(group) &&
            ("spring-framework-bom".equals(artifact) || artifact.startsWith("spring-"))) {
            if (parts.length != 3 || !FIXED.matcher(parts[2]).matches()) return ALIGNMENT_OWNER;
            return aligned(parts[2]) ? null : ALIGNMENT;
        }
        if (coordinate.startsWith("org.apache.tiles:") || LEGACY_COORDINATES.contains(coordinate)) {
            return coordinate.startsWith("javax.") ? JAKARTA : INTEGRATION;
        }
        if (SERVLET_CONTAINERS.contains(coordinate) && (parts.length < 3 || legacyContainer(artifact, parts[2]))) {
            return CONTAINER;
        }
        if ("org.springframework.boot".equals(group) && "spring-boot-dependencies".equals(artifact) &&
            (parts.length < 3 || incompatibleBoot(parts[2]))) return BOOT;
        return null;
    }

    private static String mapMessage(J.MethodInvocation invocation, boolean companions) {
        return dependencyMessage(UpgradeSelectedSpringWebMvcDependency.mapValue(invocation, "group"),
                UpgradeSelectedSpringWebMvcDependency.mapValue(invocation, "name"),
                UpgradeSelectedSpringWebMvcDependency.mapValue(invocation, "version"),
                UpgradeSelectedSpringWebMvcDependency.hasVariant(invocation), companions);
    }

    private static String mapMessage(G.MapLiteral map, boolean companions) {
        String group = UpgradeSelectedSpringWebMvcDependency.mapValue(map, "group");
        String artifact = UpgradeSelectedSpringWebMvcDependency.mapValue(map, "name");
        String version = UpgradeSelectedSpringWebMvcDependency.mapValue(map, "version");
        boolean variant = map.getElements().stream().anyMatch(e ->
                Set.of("classifier", "ext", "type", "variant").contains(UpgradeSelectedSpringWebMvcDependency.mapKey(e)));
        return dependencyMessage(group, artifact, version, variant, companions);
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant,
                                            boolean companions) {
        if (UpgradeSelectedSpringWebMvcDependency.GROUP.equals(group) &&
            UpgradeSelectedSpringWebMvcDependency.ARTIFACT.equals(artifact)) {
            if (variant) return VARIANT;
            if (version == null || !FIXED.matcher(version).matches()) return OWNER;
            if (UpgradeSelectedSpringWebMvcDependency.SOURCE_VERSIONS.contains(version)) return null;
            return UpgradeSelectedSpringWebMvcDependency.TARGET.equals(version) ? null :
                    higherThanTarget(version) ? TARGET_CONFLICT : OUTSIDE;
        }
        if (!companions || group == null || artifact == null) return null;
        return coordinateMessage(group + ":" + artifact + ":" + (version == null ? "" : version), true);
    }

    private static boolean higherThanTarget(String version) {
        String[] candidate = version.split("[^0-9]+");
        String[] target = UpgradeSelectedSpringWebMvcDependency.TARGET.split("\\.");
        int length = Math.max(candidate.length, target.length);
        for (int index = 0; index < length; index++) {
            BigInteger left = new BigInteger(index < candidate.length && !candidate[index].isEmpty()
                    ? candidate[index] : "0");
            BigInteger right = new BigInteger(index < target.length ? target[index] : "0");
            int comparison = left.compareTo(right);
            if (comparison != 0) return comparison > 0;
        }
        return false;
    }

    private static J.MethodInvocation markDynamicTemplateArgument(J.MethodInvocation invocation, boolean companions) {
        return invocation.withArguments(invocation.getArguments().stream().map(argument -> {
            String message = templateMessage(argument, companions);
            return message == null ? argument : mark(argument, message);
        }).toList());
    }

    private static String templateMessage(J argument, boolean companions) {
        TemplateCoordinate coordinate = templateCoordinate(argument);
        if (coordinate == null) return null;
        if (UpgradeSelectedSpringWebMvcDependency.GROUP.equals(coordinate.group()) &&
            UpgradeSelectedSpringWebMvcDependency.ARTIFACT.equals(coordinate.artifact())) {
            return coordinate.variant() ? VARIANT : OWNER;
        }
        if (!companions) return null;
        return coordinateMessage(coordinate.group() + ":" + coordinate.artifact() + ":", true);
    }

    private static TemplateCoordinate templateCoordinate(J argument) {
        java.util.List<J> strings;
        if (argument instanceof G.GString template) strings = template.getStrings();
        else if (argument instanceof K.StringTemplate template) strings = template.getStrings();
        else return null;
        java.util.List<String> parts = strings.stream().filter(J.Literal.class::isInstance)
                .map(J.Literal.class::cast).map(J.Literal::getValue).filter(String.class::isInstance)
                .map(String.class::cast).toList();
        if (parts.isEmpty()) return null;
        String first = parts.get(0);
        if (!first.endsWith(":")) return null;
        String[] coordinate = first.substring(0, first.length() - 1).split(":", -1);
        if (coordinate.length != 2 || coordinate[0].isEmpty() || coordinate[1].isEmpty()) return null;
        boolean variant = parts.stream().skip(1).anyMatch(part -> part.contains(":") || part.contains("@"));
        return new TemplateCoordinate(coordinate[0], coordinate[1], variant);
    }

    private record TemplateCoordinate(String group, String artifact, boolean variant) { }

    private static boolean isPlatformLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof J.MethodInvocation platform) ||
            !("platform".equals(platform.getSimpleName()) || "enforcedPlatform".equals(platform.getSimpleName()))) return false;
        Cursor owner = parent.getParent();
        while (owner != null && !(owner.getValue() instanceof J.MethodInvocation)) owner = owner.getParent();
        return owner != null && owner.getValue() instanceof J.MethodInvocation dependency &&
               UpgradeSelectedSpringWebMvcDependency.isGradleDependencyInvocation(owner, dependency);
    }

    private static boolean containsStandardPrimary(G.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedSpringWebMvcDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && standardInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean containsStandardPrimary(K.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedSpringWebMvcDependency.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && standardInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean standardInvocation(J.MethodInvocation invocation) {
        if (UpgradeSelectedSpringWebMvcDependency.GROUP.equals(
                UpgradeSelectedSpringWebMvcDependency.mapValue(invocation, "group")) &&
            UpgradeSelectedSpringWebMvcDependency.ARTIFACT.equals(
                UpgradeSelectedSpringWebMvcDependency.mapValue(invocation, "name")) &&
            !UpgradeSelectedSpringWebMvcDependency.hasVariant(invocation)) return true;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && standardCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map && UpgradeSelectedSpringWebMvcDependency.GROUP.equals(
                    UpgradeSelectedSpringWebMvcDependency.mapValue(map, "group")) &&
                UpgradeSelectedSpringWebMvcDependency.ARTIFACT.equals(
                    UpgradeSelectedSpringWebMvcDependency.mapValue(map, "name")) &&
                map.getElements().stream().noneMatch(entry ->
                        Set.of("classifier", "ext", "type", "variant")
                                .contains(UpgradeSelectedSpringWebMvcDependency.mapKey(entry)))) return true;
            TemplateCoordinate coordinate = templateCoordinate(argument);
            if (coordinate != null && UpgradeSelectedSpringWebMvcDependency.GROUP.equals(coordinate.group()) &&
                UpgradeSelectedSpringWebMvcDependency.ARTIFACT.equals(coordinate.artifact()) && !coordinate.variant()) return true;
        }
        return false;
    }

    private static boolean standardCoordinate(Object value) {
        if (!(value instanceof String coordinate)) return false;
        String base = UpgradeSelectedSpringWebMvcDependency.GROUP + ":" +
                      UpgradeSelectedSpringWebMvcDependency.ARTIFACT;
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
                if (UpgradeSelectedSpringWebMvcDependency.isStandardTargetDependency(getCursor(), t)) {
                    String scope = scope(getCursor());
                    if ("ROOT".equals(scope)) root[0] = true;
                    else profiles.add(scope);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return new MavenScopes(root[0], Set.copyOf(profiles));
    }

    private static boolean visible(Cursor cursor, MavenScopes scopes) {
        String scope = scope(cursor);
        if ("ROOT".equals(scope)) return scopes.root() || !scopes.profiles().isEmpty();
        return scopes.root() || scopes.profiles().contains(scope);
    }

    private static ScopedProperties properties(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedSpringWebMvcDependency.isMavenPropertyDefinition(getCursor(), t)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), t.getName());
                    counts.merge(key, 1, Integer::sum);
                    t.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return new ScopedProperties(counts, values);
    }

    private static String resolve(String raw, Cursor cursor, ScopedProperties properties) {
        String value = raw == null ? "" : raw.trim();
        if (FIXED.matcher(value).matches()) return value;
        Matcher matcher = PROPERTY.matcher(value);
        if (!matcher.matches()) return null;
        String currentScope = scope(cursor);
        PropertyKey local = new PropertyKey(currentScope, matcher.group(1));
        PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
        PropertyKey owner = !"ROOT".equals(currentScope) && properties.counts().containsKey(local) ? local : root;
        return properties.counts().getOrDefault(owner, 0) == 1 ? properties.values().get(owner) : null;
    }

    private static String scope(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) return tag.getId().toString();
            if (current.getValue() instanceof Xml.Document) break;
        }
        return "ROOT";
    }

    private static boolean aligned(String version) {
        return UpgradeSelectedSpringWebMvcDependency.TARGET.equals(version);
    }

    private static boolean preJava17(String value) {
        return value.matches("1[.][0-9]") || value.matches("(?:[1-9]|1[0-6])");
    }

    private static boolean incompatibleBoot(String value) {
        if (value == null) return true;
        Matcher matcher = Pattern.compile("^(\\d+)(?:[.](\\d+))?.*").matcher(value);
        if (!matcher.matches()) return true;
        BigInteger major = new BigInteger(matcher.group(1));
        BigInteger minor = new BigInteger(matcher.group(2) == null ? "0" : matcher.group(2));
        return !BigInteger.valueOf(3).equals(major) || minor.compareTo(BigInteger.valueOf(4)) < 0;
    }

    private static boolean legacyContainer(String artifact, String version) {
        if (version == null) return true;
        Matcher matcher = Pattern.compile("^(\\d+)(?:[.](\\d+))?.*").matcher(version);
        if (!matcher.matches()) return true;
        BigInteger major = new BigInteger(matcher.group(1));
        if (artifact.startsWith("tomcat")) return major.compareTo(BigInteger.TEN) < 0;
        if (artifact.startsWith("jetty")) return major.compareTo(BigInteger.valueOf(11)) < 0;
        BigInteger minor = new BigInteger(matcher.group(2) == null ? "0" : matcher.group(2));
        return major.compareTo(BigInteger.valueOf(2)) < 0 ||
               BigInteger.valueOf(2).equals(major) && minor.compareTo(BigInteger.valueOf(3)) < 0;
    }

    private static boolean legacyJavaAssignment(J.Assignment assignment, Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) return false;
        String value = assignment.getAssignment().printTrimmed(cursor).replace("'", "").replace("\"", "");
        Matcher numeric = Pattern.compile("(?:1[.])?(\\d+)").matcher(value);
        if (numeric.matches()) return new BigInteger(numeric.group(1)).compareTo(BigInteger.valueOf(17)) < 0;
        Matcher constant = Pattern.compile("(?:JavaVersion[.])?VERSION_(?:1_)?(\\d+)").matcher(value);
        return constant.matches() &&
               new BigInteger(constant.group(1)).compareTo(BigInteger.valueOf(17)) < 0;
    }

    private static boolean legacyToolchain(J.MethodInvocation method) {
        if (!"of".equals(method.getSimpleName()) || method.getArguments().size() != 1 ||
            !(method.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof Number number) || method.getSelect() == null) return false;
        return new BigDecimal(number.toString()).compareTo(BigDecimal.valueOf(17)) < 0 &&
               method.getSelect().printTrimmed().endsWith("JavaLanguageVersion");
    }

    private static boolean isMavenCompilerPlugin(Cursor cursor, Xml.Tag tag) {
        if (!"plugin".equals(tag.getName()) || !"maven-compiler-plugin".equals(
                tag.getChildValue("artifactId").orElse(""))) return false;
        Cursor plugins = cursor.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag container) || !"plugins".equals(container.getName())) return false;
        Cursor owner = plugins.getParentTreeCursor();
        if (!(owner.getValue() instanceof Xml.Tag ownerTag)) return false;
        if ("build".equals(ownerTag.getName())) return UpgradeSelectedSpringWebMvcDependency.isProjectOrProfile(owner.getParentTreeCursor());
        if (!"pluginManagement".equals(ownerTag.getName())) return false;
        Cursor build = owner.getParentTreeCursor();
        return build.getValue() instanceof Xml.Tag buildTag && "build".equals(buildTag.getName()) &&
               UpgradeSelectedSpringWebMvcDependency.isProjectOrProfile(build.getParentTreeCursor());
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }

    private static Xml.Tag markVersionOrOwner(Xml.Tag owner, String message) {
        return owner.getChild("version").map(child -> {
            Xml.Tag marked = mark(child, message);
            return marked == child ? owner : owner.withContent(owner.getContent().stream()
                    .map(content -> content == child ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    private record PropertyKey(String scope, String name) { }
    private record ScopedProperties(Map<PropertyKey, Integer> counts, Map<PropertyKey, String> values) { }
    private record MavenScopes(boolean root, Set<String> profiles) { }
}
