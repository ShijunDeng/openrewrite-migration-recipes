package com.huawei.clouds.openrewrite.springcontextsupport;

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

/** Build ownership and runtime integration markers for Spring Context Support 6.2.19. */
public final class FindSpringContextSupport6BuildRisks extends Recipe {
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target"
    );
    private static final Set<String> LEGACY_INTEGRATIONS = Set.of(
            "net.sf.ehcache:ehcache", "javax.mail:mail", "com.sun.mail:javax.mail",
            "javax.activation:activation", "com.sun.activation:javax.activation"
    );
    private static final Set<String> OPTIONAL_INTEGRATIONS = Set.of(
            "org.ehcache:ehcache", "javax.cache:cache-api", "com.github.ben-manes.caffeine:caffeine",
            "org.freemarker:freemarker", "org.quartz-scheduler:quartz",
            "jakarta.mail:jakarta.mail-api", "jakarta.activation:jakarta.activation-api"
    );
    private static final String JAVA_MESSAGE =
            "Spring Framework 6.2 requires Java 17 or newer; align compiler, toolchain, CI, container and runtime JDKs";
    private static final String LEGACY_MESSAGE =
            "Legacy Context Support integration dependency detected; migrate Ehcache 2 or javax Mail/Activation to a Jakarta-compatible provider and retest the integration";
    private static final String OPTIONAL_MESSAGE =
            "Spring Context Support optional integration detected; align its Spring 6.2-compatible version and test provider-specific cache, mail, Quartz or FreeMarker behavior";
    private static final String ALIGNMENT_MESSAGE =
            "Spring Framework modules must use one 6.2.19 line; align the owning BOM/property instead of running mixed Spring versions";
    private static final String MANAGED_MESSAGE =
            "spring-context-support is versionless or externally managed; resolve and migrate the owning Spring BOM, parent, platform or catalog to 6.2.19";
    private static final String VARIANT_MESSAGE =
            "spring-context-support classifier/type/ext/variant declaration is not the standard runtime artifact; verify its classpath and migrate the exact variant manually";

    @Override
    public String getDisplayName() {
        return "Find Spring Context Support 6.2 build risks";
    }

    @Override
    public String getDescription() {
        return "Mark Java baselines, external version owners, mixed Spring modules, and exact Context Support " +
               "integration dependencies only when the build owns a standard spring-context-support declaration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !UpgradeSelectedSpringContextSupportDependency.isProjectPath(source.getSourcePath())) return tree;
                String name = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document pom && "pom.xml".equals(name)) return markPom(pom, ctx);
                if (tree instanceof G.CompilationUnit groovy && name.endsWith(".gradle")) {
                    G.CompilationUnit variants = markGroovyVariants(groovy, ctx);
                    return hasGroovyTarget(variants, ctx) ? markGroovy(variants, ctx) : variants;
                }
                if (tree instanceof K.CompilationUnit kotlin && name.endsWith(".gradle.kts")) {
                    K.CompilationUnit variants = markKotlinVariants(kotlin, ctx);
                    return hasKotlinTarget(variants, ctx) ? markKotlin(variants, ctx) : variants;
                }
                return tree;
            }
        };
    }

    private static Xml.Document markPom(Xml.Document document, ExecutionContext ctx) {
        boolean[] hasTarget = {false};
        Map<String, String> properties = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(container -> container.getChildren().stream()
                .filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                .forEach(tag -> tag.getValue().ifPresent(value -> properties.put(tag.getName(), value.trim()))));
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                if (isStandardTarget(getCursor(), tag)) hasTarget[0] = true;
                return hasTarget[0] ? tag : super.visitTag(tag, p);
            }
        }.visit(document, ctx);
        Xml.Document withVariants = (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                return UpgradeSelectedSpringContextSupportDependency.isTargetDependency(getCursor(), visited) &&
                       !UpgradeSelectedSpringContextSupportDependency.isStandardArtifact(visited)
                        ? mark(visited, VARIANT_MESSAGE) : visited;
            }
        }.visitNonNull(document, ctx);
        if (!hasTarget[0]) return withVariants;

        UUID rootId = document.getRoot().getId();
        Set<UUID> localTargetManagement = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (isStandardTarget(getCursor(), visited) &&
                    UpgradeSelectedSpringContextSupportDependency.TARGET.equals(
                            resolve(visited.getChildValue("version").orElse(""), properties))) {
                    managementOwner(getCursor()).ifPresent(owner -> localTargetManagement.add(owner.getId()));
                }
                return visited;
            }
        }.visitNonNull(withVariants, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                if (isStandardTarget(getCursor(), visited)) {
                    String raw = visited.getChildValue("version").map(String::trim).orElse("");
                    if (raw.isEmpty()) {
                        boolean declaration = managementOwner(getCursor()).isPresent();
                        UUID owner = dependencyOwner(getCursor()).map(Xml.Tag::getId).orElse(null);
                        boolean locallyManaged = !declaration && (localTargetManagement.contains(rootId) ||
                                owner != null && localTargetManagement.contains(owner));
                        if (!locallyManaged) return mark(visited, MANAGED_MESSAGE);
                    }
                    String resolved = resolve(raw, properties);
                    if (raw.startsWith("${") && raw.endsWith("}")) {
                        if (resolved == null || isDynamic(resolved)) return mark(visited, MANAGED_MESSAGE);
                    } else if (isDynamic(raw)) return mark(visited, MANAGED_MESSAGE);
                }
                if (UpgradeSelectedSpringContextSupportDependency.isProjectDependency(getCursor(), visited)) {
                    String group = visited.getChildValue("groupId").orElse("");
                    String artifact = visited.getChildValue("artifactId").orElse("");
                    String coordinate = group + ":" + artifact;
                    if (LEGACY_INTEGRATIONS.contains(coordinate)) return mark(visited, LEGACY_MESSAGE);
                    if (OPTIONAL_INTEGRATIONS.contains(coordinate)) return mark(visited, OPTIONAL_MESSAGE);
                    if (UpgradeSelectedSpringContextSupportDependency.GROUP.equals(group) &&
                        artifact.startsWith("spring-") &&
                        !UpgradeSelectedSpringContextSupportDependency.ARTIFACT.equals(artifact)) {
                        String version = resolve(visited.getChildValue("version").orElse(""), properties);
                        if (version != null && !version.isEmpty() &&
                            !UpgradeSelectedSpringContextSupportDependency.TARGET.equals(version)) {
                            return mark(visited, ALIGNMENT_MESSAGE);
                        }
                    }
                }
                if (isMavenJavaLevel(getCursor(), visited) && belowJava17(visited.getValue().orElse(""))) {
                    return mark(visited, JAVA_MESSAGE);
                }
                return visited;
            }
        }.visitNonNull(withVariants, ctx);
    }

    private static G.CompilationUnit markGroovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                return markGradleLiteral(super.visitLiteral(literal, p), getCursor());
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext p) {
                return markJavaVersion(super.visitFieldAccess(fieldAccess, p), getCursor());
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                if (isLegacyToolchainCall(visited, getCursor())) return mark(visited, JAVA_MESSAGE);
                return markMapDependency(visited, getCursor());
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit markKotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                return markGradleLiteral(super.visitLiteral(literal, p), getCursor());
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext p) {
                return markJavaVersion(super.visitFieldAccess(fieldAccess, p), getCursor());
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                return isLegacyToolchainCall(visited, getCursor()) ? mark(visited, JAVA_MESSAGE) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit markGroovyVariants(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, p);
                if (UpgradeSelectedSpringContextSupportDependency.isGradleDependencyInvocation(getCursor(), visited) &&
                    UpgradeSelectedSpringContextSupportDependency.GROUP.equals(mapValue(visited, "group")) &&
                    UpgradeSelectedSpringContextSupportDependency.ARTIFACT.equals(mapValue(visited, "name")) &&
                    (hasMapKey(visited, "classifier") || hasMapKey(visited, "type") ||
                     hasMapKey(visited, "ext") || hasMapKey(visited, "variant"))) {
                    return mark(visited, VARIANT_MESSAGE);
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                return markExtendedCoordinate(super.visitLiteral(literal, p), getCursor());
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit markKotlinVariants(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext p) {
                return markExtendedCoordinate(super.visitLiteral(literal, p), getCursor());
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean hasGroovyTarget(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                if (isStandardGradleTarget(method, getCursor())) found[0] = true;
                return found[0] ? method : super.visitMethodInvocation(method, p);
            }
        }.visit(source, ctx);
        return found[0];
    }

    private static boolean hasKotlinTarget(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext p) {
                if (isStandardGradleTarget(method, getCursor())) found[0] = true;
                return found[0] ? method : super.visitMethodInvocation(method, p);
            }
        }.visit(source, ctx);
        return found[0];
    }

    private static boolean isStandardTarget(Cursor cursor, Xml.Tag dependency) {
        return UpgradeSelectedSpringContextSupportDependency.isTargetDependency(cursor, dependency) &&
               UpgradeSelectedSpringContextSupportDependency.isStandardArtifact(dependency);
    }

    private static boolean isStandardGradleTarget(J.MethodInvocation invocation, Cursor cursor) {
        if (!UpgradeSelectedSpringContextSupportDependency.isGradleDependencyInvocation(cursor, invocation)) return false;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && literal.getValue() instanceof String value) {
                String[] parts = value.split(":", -1);
                if ((parts.length == 2 || parts.length == 3) &&
                    UpgradeSelectedSpringContextSupportDependency.GROUP.equals(parts[0]) &&
                    UpgradeSelectedSpringContextSupportDependency.ARTIFACT.equals(parts[1])) return true;
            }
        }
        return UpgradeSelectedSpringContextSupportDependency.GROUP.equals(mapValue(invocation, "group")) &&
               UpgradeSelectedSpringContextSupportDependency.ARTIFACT.equals(mapValue(invocation, "name")) &&
               !hasMapKey(invocation, "classifier") && !hasMapKey(invocation, "type") &&
               !hasMapKey(invocation, "ext") && !hasMapKey(invocation, "variant");
    }

    private static boolean isMavenJavaLevel(Cursor cursor, Xml.Tag tag) {
        if (UpgradeSelectedSpringContextSupportDependency.isRootProperty(cursor, tag) &&
            JAVA_PROPERTIES.contains(tag.getName())) return true;
        if (!Set.of("source", "target", "release").contains(tag.getName())) return false;
        return isOwnedCompilerConfiguration(cursor);
    }

    private static J.MethodInvocation markMapDependency(J.MethodInvocation invocation, Cursor cursor) {
        if (!UpgradeSelectedSpringContextSupportDependency.isGradleDependencyInvocation(cursor, invocation)) return invocation;
        if (UpgradeSelectedSpringContextSupportDependency.GROUP.equals(mapValue(invocation, "group")) &&
            UpgradeSelectedSpringContextSupportDependency.ARTIFACT.equals(mapValue(invocation, "name"))) {
            String version = mapValue(invocation, "version");
            if (version == null || isDynamic(version)) return mark(invocation, MANAGED_MESSAGE);
        }
        String coordinate = mapValue(invocation, "group") + ":" + mapValue(invocation, "name");
        if (LEGACY_INTEGRATIONS.contains(coordinate)) return mark(invocation, LEGACY_MESSAGE);
        if (OPTIONAL_INTEGRATIONS.contains(coordinate)) return mark(invocation, OPTIONAL_MESSAGE);
        return invocation;
    }

    private static J.Literal markGradleLiteral(J.Literal literal, Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        Object value = literal.getValue();
        if (value instanceof String coordinate && parent.getValue() instanceof J.MethodInvocation invocation &&
            UpgradeSelectedSpringContextSupportDependency.isGradleDependencyInvocation(parent, invocation)) {
            String[] parts = coordinate.split(":", -1);
            if (parts.length >= 2) {
                String ga = parts[0] + ":" + parts[1];
                if ((UpgradeSelectedSpringContextSupportDependency.GROUP + ":" +
                     UpgradeSelectedSpringContextSupportDependency.ARTIFACT).equals(ga)) {
                    if (parts.length == 2 || parts.length == 3 && isDynamic(parts[2])) {
                        return mark(literal, MANAGED_MESSAGE);
                    }
                }
                if (LEGACY_INTEGRATIONS.contains(ga)) return mark(literal, LEGACY_MESSAGE);
                if (OPTIONAL_INTEGRATIONS.contains(ga)) return mark(literal, OPTIONAL_MESSAGE);
                if (UpgradeSelectedSpringContextSupportDependency.GROUP.equals(parts[0]) &&
                    parts[1].startsWith("spring-") &&
                    !UpgradeSelectedSpringContextSupportDependency.ARTIFACT.equals(parts[1]) &&
                    parts.length == 3 && !UpgradeSelectedSpringContextSupportDependency.TARGET.equals(parts[2])) {
                    return mark(literal, ALIGNMENT_MESSAGE);
                }
            }
        }
        if (parent.getValue() instanceof J.Assignment assignment && isJavaCompatibility(
                assignment.getVariable().printTrimmed()) && belowJava17(String.valueOf(value))) {
            return mark(literal, JAVA_MESSAGE);
        }
        return literal;
    }

    private static J.Literal markExtendedCoordinate(J.Literal literal, Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(literal.getValue() instanceof String coordinate) ||
            !(parent.getValue() instanceof J.MethodInvocation invocation) ||
            !UpgradeSelectedSpringContextSupportDependency.isGradleDependencyInvocation(parent, invocation)) return literal;
        String[] parts = coordinate.split(":", -1);
        return (parts.length > 3 || coordinate.contains("@")) && parts.length >= 2 &&
               UpgradeSelectedSpringContextSupportDependency.GROUP.equals(parts[0]) &&
               UpgradeSelectedSpringContextSupportDependency.ARTIFACT.equals(parts[1])
                ? mark(literal, VARIANT_MESSAGE) : literal;
    }

    private static java.util.Optional<Xml.Tag> dependencyOwner(Cursor dependencyCursor) {
        Cursor dependencies = dependencyCursor.getParentTreeCursor();
        Cursor owner = dependencies.getParentTreeCursor();
        if (UpgradeSelectedSpringContextSupportDependency.isProjectOrProfile(owner) &&
            owner.getValue() instanceof Xml.Tag tag) return java.util.Optional.of(tag);
        if (owner.getValue() instanceof Xml.Tag tag && "dependencyManagement".equals(tag.getName())) {
            Cursor scope = owner.getParentTreeCursor();
            if (UpgradeSelectedSpringContextSupportDependency.isProjectOrProfile(scope) &&
                scope.getValue() instanceof Xml.Tag scoped) return java.util.Optional.of(scoped);
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<Xml.Tag> managementOwner(Cursor dependencyCursor) {
        Cursor dependencies = dependencyCursor.getParentTreeCursor();
        Cursor management = dependencies.getParentTreeCursor();
        if (!(management.getValue() instanceof Xml.Tag tag) || !"dependencyManagement".equals(tag.getName())) {
            return java.util.Optional.empty();
        }
        Cursor owner = management.getParentTreeCursor();
        return UpgradeSelectedSpringContextSupportDependency.isProjectOrProfile(owner) &&
               owner.getValue() instanceof Xml.Tag scoped ? java.util.Optional.of(scoped) : java.util.Optional.empty();
    }

    private static boolean isOwnedCompilerConfiguration(Cursor settingCursor) {
        Cursor configuration = settingCursor.getParentTreeCursor();
        if (!(configuration.getValue() instanceof Xml.Tag configurationTag) ||
            !"configuration".equals(configurationTag.getName())) return false;
        Cursor plugin = configuration.getParentTreeCursor();
        if (!(plugin.getValue() instanceof Xml.Tag pluginTag) || !"plugin".equals(pluginTag.getName()) ||
            !"maven-compiler-plugin".equals(pluginTag.getChildValue("artifactId").orElse(null)) ||
            !Set.of("", "org.apache.maven.plugins").contains(pluginTag.getChildValue("groupId").orElse(""))) {
            return false;
        }
        Cursor plugins = plugin.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag pluginsTag) || !"plugins".equals(pluginsTag.getName())) return false;
        Cursor owner = plugins.getParentTreeCursor();
        Cursor build;
        if (owner.getValue() instanceof Xml.Tag ownerTag && "pluginManagement".equals(ownerTag.getName())) {
            build = owner.getParentTreeCursor();
        } else {
            build = owner;
        }
        if (!(build.getValue() instanceof Xml.Tag buildTag) || !"build".equals(buildTag.getName())) return false;
        return UpgradeSelectedSpringContextSupportDependency.isProjectOrProfile(build.getParentTreeCursor());
    }

    private static J.FieldAccess markJavaVersion(J.FieldAccess fieldAccess, Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        String value = fieldAccess.printTrimmed();
        if (parent.getValue() instanceof J.Assignment assignment &&
            isJavaCompatibility(assignment.getVariable().printTrimmed()) &&
            value.startsWith("JavaVersion.VERSION_") &&
            belowJava17(value.substring("JavaVersion.VERSION_".length()))) return mark(fieldAccess, JAVA_MESSAGE);
        return fieldAccess;
    }

    private static boolean isLegacyToolchainCall(J.MethodInvocation invocation, Cursor cursor) {
        if (!"of".equals(invocation.getSimpleName()) || invocation.getSelect() == null ||
            !"JavaLanguageVersion".equals(invocation.getSelect().printTrimmed()) ||
            invocation.getArguments().size() != 1 ||
            !(invocation.getArguments().get(0) instanceof J.Literal literal) ||
            !belowJava17(String.valueOf(literal.getValue()))) return false;
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.Assignment assignment &&
               assignment.getVariable().printTrimmed().endsWith("languageVersion");
    }

    private static boolean isJavaCompatibility(String value) {
        return value.equals("sourceCompatibility") || value.equals("targetCompatibility") ||
               value.endsWith(".sourceCompatibility") || value.endsWith(".targetCompatibility") ||
               value.endsWith("languageVersion");
    }

    private static String resolve(String value, Map<String, String> properties) {
        if (value.startsWith("${") && value.endsWith("}")) return properties.get(value.substring(2, value.length() - 1));
        return value;
    }

    private static boolean isDynamic(String value) {
        return value.contains("+") || value.contains("[") || value.contains("(") ||
               value.contains("]") || value.contains(")") || value.contains("$");
    }

    private static boolean belowJava17(String value) {
        String normalized = value.trim().replace("VERSION_", "");
        if (normalized.startsWith("1.")) normalized = normalized.substring(2);
        try {
            return Integer.parseInt(normalized.replaceAll("[^0-9].*", "")) < 17;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean hasMapKey(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().anyMatch(argument ->
                argument instanceof G.MapEntry entry && key.equals(mapKey(entry)) ||
                argument instanceof G.MapLiteral map && map.getElements().stream().anyMatch(entry -> key.equals(mapKey(entry))));
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        for (J argument : invocation.getArguments()) {
            if (argument instanceof G.MapEntry entry && key.equals(mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal && literal.getValue() instanceof String value) return value;
            if (argument instanceof G.MapLiteral map) {
                for (G.MapEntry entry : map.getElements()) {
                    if (key.equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal &&
                        literal.getValue() instanceof String value) return value;
                }
            }
        }
        return null;
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
