package com.huawei.clouds.openrewrite.springexpression;

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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dependency ownership, Java baseline, module, and Spring family alignment markers. */
public final class FindSpringExpressionBuildRisks extends Recipe {
    private static final Set<String> JAVA_KEYS = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");

    static final String OWNER =
            "spring-expression is versionless, variable, ranged, catalog/platform/BOM-managed, shared, aggregated, or externally owned; migrate the actual owner and verify that 6.2.19 resolves";
    static final String OUTSIDE =
            "This fixed spring-expression version is below 6.2.19 but outside the exact 17-version whitelist; it is intentionally not auto-upgraded";
    static final String TARGET_CONFLICT =
            "目标版本冲突（禁止降级）: this Spring declaration is newer than the 6.2.19 target line; it remains unchanged";
    static final String VARIANT =
            "This classified or non-JAR spring-expression artifact is outside deterministic scope; verify the exact 6.2.19 artifact shape manually";
    static final String JAVA =
            "Spring Framework 6.2 requires Java 17 or newer; align compiler release, toolchain, CI, container, and runtime JDKs";
    static final String PARAMETERS =
            "Spring 6.1 removed local-variable-table parameter-name discovery; compile Java/Groovy with -parameters and Kotlin with -java-parameters for SpEL method and constructor resolution";
    static final String ALIGNMENT =
            "Direct Spring Framework modules must align on 6.2.19; migrate the owning Framework BOM/property instead of mixing spring-expression and spring-core release lines";
    static final String ALIGNMENT_OWNER =
            "This Spring Framework companion is versionless, variable, ranged, or externally owned; align its actual BOM/property/platform owner with spring-expression 6.2.19";
    static final String BOOT =
            "This Spring Boot line does not own Spring Framework 6.2; upgrade the Boot parent/BOM to a compatible 3.4/3.5 line before selecting spring-expression 6.2.19";
    static final String BOOT_OWNER =
            "Spring Boot parent/BOM management owns the Spring Framework family; upgrade that owner and verify its managed spring-expression resolves to 6.2.19 instead of forcing a divergent leaf";
    static final String JAKARTA =
            "Spring Framework 6 uses Jakarta EE namespaces; review javax dependencies and any javax type names embedded in SpEL expressions before moving to Jakarta EE 9/10";
    static final String MODULE =
            "SpEL type lookup, reflection, generated compiled-expression classes, and custom ClassLoaders cross JPMS/native-image boundaries; verify requires/opens and runtime hints on Java 17";

    @Override
    public String getDisplayName() {
        return "Find Spring Expression 6.2 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact dependency ownership, no-downgrade conflicts, Java 17, parameter metadata, Spring/Boot " +
               "alignment, Jakarta namespace, and module/class-loader boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringExpressionSupport.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof J.CompilationUnit java && "module-info.java".equals(fileName) &&
                    java.printAll().matches("(?s).*\\brequires\\s+(?:static\\s+|transitive\\s+)*spring[.]expression\\s*;.*")) {
                    return SpringExpressionSupport.mark(java, MODULE);
                }
                if (tree instanceof PlainText text && "module-info.java".equals(fileName) &&
                    text.getText().matches("(?s).*\\brequires\\s+(?:static\\s+|transitive\\s+)*spring[.]expression\\s*;.*")) {
                    return SpringExpressionSupport.mark(text, MODULE);
                }
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
        SpringExpressionSupport.PomProperties properties =
                SpringExpressionSupport.analyzeProperties(document, ctx);
        MavenScopes scopes = scopes(document, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (SpringExpressionSupport.isTargetDependency(getCursor(), t)) {
                    String raw = t.getChildValue("version").orElse("");
                    String safe = properties.resolveSafePrimary(raw, getCursor());
                    String unique = properties.resolveUnique(raw, getCursor());
                    if (!SpringExpressionSupport.isStandardArtifact(t)) {
                        t = SpringExpressionSupport.mark(t, VARIANT);
                        return higherThanTarget(safe != null ? safe : unique)
                                ? SpringExpressionSupport.mark(t, TARGET_CONFLICT) : t;
                    }
                    String message;
                    if (safe != null) {
                        message = primaryMessage(raw, safe);
                    } else if (higherThanTarget(unique)) {
                        message = TARGET_CONFLICT;
                    } else {
                        message = OWNER;
                    }
                    return message == null ? t : SpringExpressionSupport.markVersionOrOwner(t, message);
                }
                if (!visible(getCursor(), scopes)) return t;

                if (JAVA_KEYS.contains(t.getName())) {
                    String resolved = properties.resolveUnique(t.getValue().orElse(""), getCursor());
                    if (preJava17(resolved)) return SpringExpressionSupport.mark(t, JAVA);
                }
                if ("maven.compiler.parameters".equals(t.getName()) &&
                    !"true".equalsIgnoreCase(t.getValue().orElse("").trim())) {
                    return SpringExpressionSupport.mark(t, PARAMETERS);
                }
                if (SpringExpressionSupport.isMavenBuildPlugin(
                        getCursor(), t, "org.apache.maven.plugins", "maven-compiler-plugin")) {
                    String release = t.getChild("configuration").flatMap(c -> c.getChildValue("release"))
                            .orElse(t.getChild("configuration").flatMap(c -> c.getChildValue("source")).orElse(""));
                    if (preJava17(properties.resolveUnique(release, getCursor()))) {
                        t = SpringExpressionSupport.mark(t, JAVA);
                    }
                    String parameters = t.getChild("configuration")
                            .flatMap(c -> c.getChildValue("parameters")).orElse("");
                    if ("false".equalsIgnoreCase(parameters.trim())) {
                        t = SpringExpressionSupport.mark(t, PARAMETERS);
                    }
                    return t;
                }
                if ("parent".equals(t.getName()) &&
                    "org.springframework.boot".equals(t.getChildValue("groupId").orElse(""))) {
                    String version = properties.resolveUnique(t.getChildValue("version").orElse(""), getCursor());
                    return SpringExpressionSupport.markVersionOrOwner(t, bootMessage(version));
                }
                if (!SpringExpressionSupport.isProjectDependency(getCursor(), t)) return t;
                String group = t.getChildValue("groupId").orElse("");
                String artifact = t.getChildValue("artifactId").orElse("");
                String version = properties.resolveUnique(t.getChildValue("version").orElse(""), getCursor());
                String message = companionMessage(group, artifact, version);
                return message == null ? t : SpringExpressionSupport.markVersionOrOwner(t, message);
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
                        ? SpringExpressionSupport.mark(a, JAVA) : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringExpressionSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (primary && rootBuildSetting(getCursor()) && legacyToolchain(m)) {
                    return SpringExpressionSupport.mark(m, JAVA);
                }
                if (primary && parameterConfiguration(m)) {
                    return SpringExpressionSupport.mark(m, PARAMETERS);
                }
                if (!dependency) return m;
                m = markTemplates(m, primary);
                String alias = m.getArguments().isEmpty() ? "" :
                        m.getArguments().get(0).printTrimmed(getCursor());
                if (alias.matches("libs(?:[.]versions)?[.]spring[.]expression")) {
                    return SpringExpressionSupport.mark(m, OWNER);
                }
                String message = mapMessage(m, primary);
                if (message == null) {
                    G.MapLiteral map = m.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                            .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                    message = map == null ? null : mapMessage(map, primary);
                }
                return message == null ? m : SpringExpressionSupport.mark(m, message);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringExpressionSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = isPlatformLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                String message = direct || platform ? coordinateMessage(l.getValue(), primary) : null;
                return message == null ? l : SpringExpressionSupport.mark(l, message);
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
                        ? SpringExpressionSupport.mark(a, JAVA) : a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringExpressionSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (primary && rootBuildSetting(getCursor()) && legacyToolchain(m)) {
                    return SpringExpressionSupport.mark(m, JAVA);
                }
                if (primary && parameterConfiguration(m)) {
                    return SpringExpressionSupport.mark(m, PARAMETERS);
                }
                if (!dependency) return m;
                m = markTemplates(m, primary);
                String alias = m.getArguments().isEmpty() ? "" :
                        m.getArguments().get(0).printTrimmed(getCursor());
                if (alias.matches("libs(?:[.]versions)?[.]spring[.]expression")) {
                    return SpringExpressionSupport.mark(m, OWNER);
                }
                String message = mapMessage(m, primary);
                return message == null ? m : SpringExpressionSupport.mark(m, message);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringExpressionSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = isPlatformLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                String message = direct || platform ? coordinateMessage(l.getValue(), primary) : null;
                return message == null ? l : SpringExpressionSupport.mark(l, message);
            }
        }.visitNonNull(source, ctx);
    }

    static String primaryMessage(String raw, String resolved) {
        if (raw == null || raw.trim().isEmpty() || resolved == null ||
            !SpringExpressionSupport.FIXED_VERSION.matcher(resolved).matches()) return OWNER;
        if (SpringExpressionSupport.SOURCE_VERSIONS.contains(resolved) ||
            SpringExpressionSupport.TARGET.equals(resolved)) return null;
        return SpringExpressionSupport.compareVersions(resolved, SpringExpressionSupport.TARGET) > 0
                ? TARGET_CONFLICT : OUTSIDE;
    }

    private static boolean higherThanTarget(String version) {
        return version != null && SpringExpressionSupport.FIXED_VERSION.matcher(version).matches() &&
               SpringExpressionSupport.compareVersions(version, SpringExpressionSupport.TARGET) > 0;
    }

    private static String companionMessage(String group, String artifact, String version) {
        if ("org.springframework".equals(group) &&
            ("spring-framework-bom".equals(artifact) || artifact.startsWith("spring-")) &&
            !SpringExpressionSupport.ARTIFACT.equals(artifact)) {
            if (version == null || !SpringExpressionSupport.FIXED_VERSION.matcher(version).matches()) {
                return ALIGNMENT_OWNER;
            }
            if (SpringExpressionSupport.TARGET.equals(version)) return null;
            return SpringExpressionSupport.compareVersions(version, SpringExpressionSupport.TARGET) > 0
                    ? TARGET_CONFLICT : ALIGNMENT;
        }
        if ("org.springframework.boot".equals(group) && artifact.startsWith("spring-boot")) {
            return bootMessage(version);
        }
        if (group.startsWith("javax.") &&
            (artifact.contains("annotation") || artifact.contains("inject") || artifact.contains("validation") ||
             artifact.contains("servlet") || artifact.contains("persistence") || artifact.contains("el"))) {
            return JAKARTA;
        }
        return null;
    }

    private static String coordinateMessage(Object value, boolean companions) {
        if (!(value instanceof String coordinate)) return null;
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2) return null;
        String group = parts[0];
        String artifact = parts[1];
        if (SpringExpressionSupport.GROUP.equals(group) &&
            SpringExpressionSupport.ARTIFACT.equals(artifact)) {
            if (parts.length > 3) {
                return higherThanTarget(parts[2]) ? TARGET_CONFLICT : VARIANT;
            }
            if (parts.length == 3 && parts[2].contains("@")) {
                String version = parts[2].substring(0, parts[2].indexOf('@'));
                return higherThanTarget(version) ? TARGET_CONFLICT : VARIANT;
            }
            if (parts.length != 3 ||
                !SpringExpressionSupport.FIXED_VERSION.matcher(parts[2]).matches()) return OWNER;
            if (SpringExpressionSupport.SOURCE_VERSIONS.contains(parts[2]) ||
                SpringExpressionSupport.TARGET.equals(parts[2])) return null;
            return SpringExpressionSupport.compareVersions(parts[2], SpringExpressionSupport.TARGET) > 0
                    ? TARGET_CONFLICT : OUTSIDE;
        }
        if (!companions) return null;
        String version = parts.length == 3 &&
                         SpringExpressionSupport.FIXED_VERSION.matcher(parts[2]).matches()
                ? parts[2] : null;
        return companionMessage(group, artifact, version);
    }

    private static String mapMessage(J.MethodInvocation invocation, boolean companions) {
        return dependencyMessage(
                SpringExpressionSupport.mapValue(invocation, "group"),
                SpringExpressionSupport.mapValue(invocation, "name"),
                SpringExpressionSupport.mapValue(invocation, "version"),
                SpringExpressionSupport.hasVariant(invocation), companions);
    }

    private static String mapMessage(G.MapLiteral map, boolean companions) {
        return dependencyMessage(
                SpringExpressionSupport.mapValue(map, "group"),
                SpringExpressionSupport.mapValue(map, "name"),
                SpringExpressionSupport.mapValue(map, "version"),
                SpringExpressionSupport.hasVariant(map), companions);
    }

    private static String dependencyMessage(String group, String artifact, String version,
                                            boolean variant, boolean companions) {
        if (SpringExpressionSupport.GROUP.equals(group) &&
            SpringExpressionSupport.ARTIFACT.equals(artifact)) {
            if (variant) return higherThanTarget(version) ? TARGET_CONFLICT : VARIANT;
            if (version == null ||
                !SpringExpressionSupport.FIXED_VERSION.matcher(version).matches()) return OWNER;
            if (SpringExpressionSupport.SOURCE_VERSIONS.contains(version) ||
                SpringExpressionSupport.TARGET.equals(version)) return null;
            return SpringExpressionSupport.compareVersions(version, SpringExpressionSupport.TARGET) > 0
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
            if (SpringExpressionSupport.GROUP.equals(coordinate.group()) &&
                SpringExpressionSupport.ARTIFACT.equals(coordinate.artifact())) {
                message = coordinate.variant() ? VARIANT : OWNER;
            } else {
                message = companions ? companionMessage(
                        coordinate.group(), coordinate.artifact(), null) : null;
            }
            return message == null ? argument : SpringExpressionSupport.mark(argument, message);
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
        if (coordinate.length != 2 || coordinate[0].isEmpty() ||
            coordinate[1].isEmpty()) return null;
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
               SpringExpressionSupport.isRootGradleDependency(owner, dependency);
    }

    private static boolean containsStandardPrimary(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringExpressionSupport.isRootGradleDependency(getCursor(), method);
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
                boolean direct = SpringExpressionSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && standardInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean standardInvocation(J.MethodInvocation invocation) {
        if (SpringExpressionSupport.GROUP.equals(SpringExpressionSupport.mapValue(invocation, "group")) &&
            SpringExpressionSupport.ARTIFACT.equals(SpringExpressionSupport.mapValue(invocation, "name")) &&
            !SpringExpressionSupport.hasVariant(invocation)) return true;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && standardCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map &&
                SpringExpressionSupport.GROUP.equals(SpringExpressionSupport.mapValue(map, "group")) &&
                SpringExpressionSupport.ARTIFACT.equals(SpringExpressionSupport.mapValue(map, "name")) &&
                !SpringExpressionSupport.hasVariant(map)) return true;
            TemplateCoordinate coordinate = templateCoordinate(argument);
            if (coordinate != null &&
                SpringExpressionSupport.GROUP.equals(coordinate.group()) &&
                SpringExpressionSupport.ARTIFACT.equals(coordinate.artifact()) &&
                !coordinate.variant()) return true;
        }
        return false;
    }

    private static boolean standardCoordinate(Object value) {
        if (!(value instanceof String coordinate)) return false;
        String base = SpringExpressionSupport.GROUP + ":" + SpringExpressionSupport.ARTIFACT;
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
                if (SpringExpressionSupport.isStandardTargetDependency(getCursor(), t)) {
                    String scope = SpringExpressionSupport.scope(getCursor());
                    if ("ROOT".equals(scope)) root[0] = true;
                    else profiles.add(scope);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return new MavenScopes(root[0], Set.copyOf(profiles));
    }

    private static boolean visible(Cursor cursor, MavenScopes scopes) {
        String scope = SpringExpressionSupport.scope(cursor);
        if ("ROOT".equals(scope)) return scopes.root() || !scopes.profiles().isEmpty();
        return scopes.root() || scopes.profiles().contains(scope);
    }

    private static boolean preJava17(String value) {
        return value != null &&
               (value.matches("1[.][0-9]") || value.matches("(?:[1-9]|1[0-6])"));
    }

    private static String bootMessage(String version) {
        int[] actual = SpringExpressionSupport.numbers(version);
        if (actual == null) return BOOT_OWNER;
        if (actual[0] > 3 || actual[0] == 3 && actual[1] > 5) return TARGET_CONFLICT;
        return actual[0] == 3 && (actual[1] == 4 || actual[1] == 5) ? BOOT_OWNER : BOOT;
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

    private static boolean parameterConfiguration(J.MethodInvocation method) {
        String printed = method.printTrimmed();
        return printed.contains("compilerArgs") && !printed.contains("-parameters") ||
               printed.contains("groovyOptions.parameters") && printed.contains("false") ||
               printed.contains("freeCompilerArgs") && !printed.contains("-java-parameters") ||
               printed.contains("javaParameters") && printed.contains("false");
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
