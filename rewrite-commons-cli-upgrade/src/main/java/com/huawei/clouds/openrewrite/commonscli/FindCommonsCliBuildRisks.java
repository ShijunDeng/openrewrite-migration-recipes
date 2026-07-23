package com.huawei.clouds.openrewrite.commonscli;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
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

/** Mark build ownership, Java baseline, module-path, shading, and native-image decisions. */
public final class FindCommonsCliBuildRisks extends Recipe {
    private static final Pattern JAVA_LEVEL = Pattern.compile("(?:1[.])?(\\d+)");
    private static final Pattern LITERAL_VERSION = Pattern.compile("\\d+(?:[.]\\d+)+(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");

    @Override
    public String getDisplayName() {
        return "Find Apache Commons CLI 1.9 build risks";
    }

    @Override
    public String getDescription() {
        return "Marks Java below 8, external or variant dependency owners, old JPMS module names, shading, OSGi, and native-image boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || CommonsCliSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Map<String, String> rootProperties = new HashMap<>();
        Map<UUID, Map<String, String>> profileProperties = new HashMap<>();
        boolean[] rootClassicDependency = {false};
        Set<UUID> profileClassicDependencies = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (classicCommonsCliDependency(getCursor(), visited)) {
                    UUID profile = profileId(getCursor());
                    if (profile == null) rootClassicDependency[0] = true;
                    else profileClassicDependencies.add(profile);
                }
                if (!CommonsCliSupport.isMavenPropertyDefinition(getCursor(), visited)) return visited;
                UUID profile = profileId(getCursor());
                Map<String, String> properties = profile == null ? rootProperties :
                        profileProperties.computeIfAbsent(profile, ignored -> new HashMap<>());
                visited.getValue().ifPresent(value -> properties.put(visited.getName(), value.trim()));
                return visited;
            }
        }.visitNonNull(source, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                String value = visited.getValue().orElse("").trim();
                boolean dependencyVisible = mavenConfigurationHasDependency(
                        profileId(getCursor()), rootClassicDependency[0], profileClassicDependencies);
                if (CommonsCliSupport.isMavenPropertyDefinition(getCursor(), visited) &&
                    JAVA_PROPERTIES.contains(visited.getName()) && belowJava8(value) && dependencyVisible) {
                    return SearchResult.found(visited, "Commons CLI 1.9.0 requires Java 8+; align compiler release/source/target, toolchain, CI, runtime, and container image");
                }
                if (("arg".equals(visited.getName()) || "compilerArg".equals(visited.getName())) &&
                    oldModuleName(value) && dependencyVisible) {
                    return SearchResult.found(visited, "Commons CLI 1.9.0 provides explicit JPMS module org.apache.commons.cli; replace old automatic module name commons.cli in compiler/runtime arguments");
                }
                if ("plugin".equals(visited.getName()) && projectBuildPlugin(getCursor()) && dependencyVisible) {
                    String group = visited.getChildValue("groupId").orElse("");
                    String artifact = visited.getChildValue("artifactId").orElse("");
                    String printed = visited.printTrimmed(getCursor());
                    if ("org.apache.maven.plugins".equals(group) && "maven-shade-plugin".equals(artifact) &&
                        printed.contains("org.apache.commons.cli")) {
                        return SearchResult.found(visited, "Commons CLI is shaded/relocated; verify the 1.9.0 multi-release module-info, OSGi metadata, reflection strings, service/resource merging, and duplicate unshaded copies");
                    }
                    if (("org.graalvm.buildtools".equals(group) && "native-maven-plugin".equals(artifact)) ||
                        printed.contains("native-image")) {
                        return SearchResult.found(visited, "Native-image build detected; verify TypeHandler class/object conversion, reflective constructors, help resources, exception paths, and module metadata after Commons CLI 1.9.0");
                    }
                }
                if (!"dependency".equals(visited.getName()) || !CommonsCliSupport.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                if (!CommonsCliSupport.GROUP.equals(group) || !CommonsCliSupport.ARTIFACT.equals(artifact)) return visited;
                if (!CommonsCliSupport.standardJar(visited)) {
                    return SearchResult.found(visited, "Classifier/type variants are outside the workbook's ordinary Commons CLI JAR target and require an explicit artifact decision");
                }
                String declared = visited.getChildValue("version").orElse("").trim();
                if (declared.isEmpty()) {
                    return SearchResult.found(visited, "This versionless Commons CLI dependency is controlled by a parent/BOM; update and validate that owner rather than adding a local version");
                }
                Map<String, String> visible = new HashMap<>(rootProperties);
                UUID profile = profileId(getCursor());
                if (profile != null) visible.putAll(profileProperties.getOrDefault(profile, Map.of()));
                String resolved = resolve(declared, visible);
                if (resolved == null) {
                    return SearchResult.found(visited, "This Commons CLI version is externally or ambiguously owned; resolve its parent/property/catalog and upgrade the actual owner to 1.9.0");
                }
                if (!CommonsCliSupport.SOURCE.equals(resolved) && !CommonsCliSupport.TARGET.equals(resolved)) {
                    return SearchResult.found(visited, "This fixed Commons CLI version is outside the workbook's 1.5.0 source selection; determine its own supported migration path instead of widening AUTO scope");
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean hasClassicDependency = hasClassicGroovyDependency(source, ctx);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment visited = super.visitAssignment(assignment, executionContext);
                return hasClassicDependency && legacyJavaAssignment(visited, getCursor()) ? SearchResult.found(visited,
                        "Commons CLI 1.9.0 requires a Java 8+ Gradle toolchain and runtime") : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (hasClassicDependency && legacyToolchain(visited)) return SearchResult.found(visited,
                        "Commons CLI 1.9.0 requires a Java 8+ Gradle toolchain and runtime");
                return markGradleDependency(visited, getCursor());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return hasClassicDependency && visited.getValue() instanceof String value &&
                       moduleConfigurationLiteral(getCursor(), value) ? SearchResult.found(visited,
                        "Replace old automatic JPMS module name commons.cli with explicit module org.apache.commons.cli for 1.9.0") : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean hasClassicDependency = hasClassicKotlinDependency(source, ctx);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment visited = super.visitAssignment(assignment, executionContext);
                return hasClassicDependency && legacyJavaAssignment(visited, getCursor()) ? SearchResult.found(visited,
                        "Commons CLI 1.9.0 requires a Java 8+ Gradle toolchain and runtime") : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (hasClassicDependency && legacyToolchain(visited)) return SearchResult.found(visited,
                        "Commons CLI 1.9.0 requires a Java 8+ Gradle toolchain and runtime");
                return markGradleDependency(visited, getCursor());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return hasClassicDependency && visited.getValue() instanceof String value &&
                       moduleConfigurationLiteral(getCursor(), value) ? SearchResult.found(visited,
                        "Replace old automatic JPMS module name commons.cli with explicit module org.apache.commons.cli for 1.9.0") : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean hasClassicGroovyDependency(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (standardGradleDependency(visited, getCursor())) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean hasClassicKotlinDependency(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (standardGradleDependency(visited, getCursor())) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean standardGradleDependency(J.MethodInvocation method, Cursor cursor) {
        if (!CommonsCliSupport.isGradleDependencyInvocation(cursor, method) || method.getArguments().isEmpty()) return false;
        G.MapLiteral map = method.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                .map(G.MapLiteral.class::cast).findFirst().orElse(null);
        String group = map == null ? CommonsCliSupport.mapValue(method, "group") : CommonsCliSupport.mapValue(map, "group");
        String artifact = map == null ? CommonsCliSupport.mapValue(method, "name") : CommonsCliSupport.mapValue(map, "name");
        if (CommonsCliSupport.GROUP.equals(group) && CommonsCliSupport.ARTIFACT.equals(artifact)) {
            return !(map == null ? CommonsCliSupport.hasVariant(method) : CommonsCliSupport.hasVariant(map));
        }
        String prefix = CommonsCliSupport.GROUP + ":" + CommonsCliSupport.ARTIFACT;
        if (method.getArguments().get(0) instanceof J.Literal literal && literal.getValue() instanceof String coordinate) {
            if (prefix.equals(coordinate)) return true;
            if (!coordinate.startsWith(prefix + ":")) return false;
            String suffix = coordinate.substring(prefix.length() + 1);
            return !suffix.contains(":") && !suffix.contains("@");
        }
        String expression = method.getArguments().get(0).printTrimmed(cursor);
        if (!expression.startsWith("\"" + prefix + ":") && !expression.startsWith("'" + prefix + ":")) return false;
        return !expression.substring(prefix.length() + 2).contains(":") && !expression.contains("@");
    }

    private static J.MethodInvocation markGradleDependency(J.MethodInvocation method, Cursor cursor) {
        if (!CommonsCliSupport.isGradleDependencyInvocation(cursor, method) || method.getArguments().isEmpty()) return method;
        G.MapLiteral map = method.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                .map(G.MapLiteral.class::cast).findFirst().orElse(null);
        String group = map == null ? CommonsCliSupport.mapValue(method, "group") : CommonsCliSupport.mapValue(map, "group");
        String artifact = map == null ? CommonsCliSupport.mapValue(method, "name") : CommonsCliSupport.mapValue(map, "name");
        String version = map == null ? CommonsCliSupport.mapValue(method, "version") : CommonsCliSupport.mapValue(map, "version");
        if (CommonsCliSupport.GROUP.equals(group) && CommonsCliSupport.ARTIFACT.equals(artifact)) {
            if (map == null ? CommonsCliSupport.hasVariant(method) : CommonsCliSupport.hasVariant(map)) {
                return SearchResult.found(method, "Classifier/type/extension variants are outside the workbook's ordinary Commons CLI JAR target");
            }
            return markVersionOwner(method, version);
        }
        String prefix = CommonsCliSupport.GROUP + ":" + CommonsCliSupport.ARTIFACT;
        if (!(method.getArguments().get(0) instanceof J.Literal literal) || !(literal.getValue() instanceof String coordinate)) {
            String expression = method.getArguments().get(0).printTrimmed(cursor);
            if ((expression.startsWith("\"" + prefix + ":") || expression.startsWith("'" + prefix + ":")) &&
                (expression.contains("$") || expression.contains("+") || expression.contains("provider("))) {
                return SearchResult.found(method, "This Commons CLI version is externally/dynamically owned; resolve its property/catalog/platform and upgrade the actual owner to 1.9.0");
            }
            return method;
        }
        if (prefix.equals(coordinate)) return SearchResult.found(method,
                "This versionless Commons CLI dependency is controlled by a Gradle platform/catalog; update that owner to 1.9.0");
        if (!coordinate.startsWith(prefix + ":")) return method;
        String suffix = coordinate.substring(prefix.length() + 1);
        if (suffix.contains(":") || suffix.contains("@")) return SearchResult.found(method,
                "Classifier/type/extension variants are outside the workbook's ordinary Commons CLI JAR target");
        return markVersionOwner(method, suffix);
    }

    private static J.MethodInvocation markVersionOwner(J.MethodInvocation method, String version) {
        if (version == null || version.isBlank()) return SearchResult.found(method,
                "This versionless Commons CLI dependency is controlled by a Gradle platform/catalog; update that owner to 1.9.0");
        if (version.contains("$") || version.contains("+") || version.startsWith("[") || version.startsWith("(")) {
            return SearchResult.found(method, "This Commons CLI version is externally/dynamically owned; resolve its property/catalog/platform and upgrade the actual owner to 1.9.0");
        }
        if (!CommonsCliSupport.SOURCE.equals(version) && !CommonsCliSupport.TARGET.equals(version)) {
            return SearchResult.found(method, "This fixed Commons CLI version is outside the workbook's 1.5.0 source selection; do not widen AUTO scope");
        }
        return method;
    }

    private static String resolve(String value, Map<String, String> properties) {
        if (LITERAL_VERSION.matcher(value).matches()) return value;
        Matcher matcher = PROPERTY.matcher(value);
        if (!matcher.matches()) return null;
        String resolved = properties.get(matcher.group(1));
        return resolved != null && LITERAL_VERSION.matcher(resolved).matches() ? resolved : null;
    }

    private static boolean belowJava8(String value) {
        Matcher matcher = JAVA_LEVEL.matcher(value);
        return matcher.matches() && Integer.parseInt(matcher.group(1)) < 8;
    }

    private static boolean oldModuleName(String value) {
        return Pattern.compile("(^|[^A-Za-z0-9_.])commons[.]cli([^A-Za-z0-9_.]|$)").matcher(value).find();
    }

    private static boolean moduleConfigurationLiteral(Cursor cursor, String value) {
        if (!oldModuleName(value)) return false;
        if (value.contains("--add-modules") || value.contains("--limit-modules") || value.contains("--module-path")) {
            return true;
        }
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            Object node = current.getValue();
            if (node instanceof Statement statement) {
                String source = statement.printTrimmed(current);
                if (source.contains("compilerArgs") || source.contains("jvmArgs") || source.contains("freeCompilerArgs") ||
                    source.contains("addModules") || source.contains("limitModules") || source.contains("moduleName")) {
                    return true;
                }
            }
            if (node instanceof J.CompilationUnit || node instanceof G.CompilationUnit || node instanceof K.CompilationUnit) break;
        }
        return false;
    }

    private static boolean legacyToolchain(J.MethodInvocation method) {
        return "of".equals(method.getSimpleName()) && method.getArguments().size() == 1 &&
               method.getArguments().get(0) instanceof J.Literal literal && literal.getValue() instanceof Number number &&
               number.intValue() < 8 && method.getSelect() != null && method.getSelect().printTrimmed().endsWith("JavaLanguageVersion");
    }

    private static boolean legacyJavaAssignment(J.Assignment assignment, Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) return false;
        String value = assignment.getAssignment().printTrimmed(cursor).replace("'", "").replace("\"", "");
        Matcher numeric = JAVA_LEVEL.matcher(value);
        if (numeric.matches()) return Integer.parseInt(numeric.group(1)) < 8;
        Matcher constant = Pattern.compile("(?:JavaVersion[.])?VERSION_(?:1_)?(\\d+)").matcher(value);
        return constant.matches() && Integer.parseInt(constant.group(1)) < 8;
    }

    private static UUID profileId(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) return tag.getId();
            if (current.getValue() instanceof Xml.Document) return null;
        }
        return null;
    }

    private static boolean classicCommonsCliDependency(Cursor cursor, Xml.Tag tag) {
        if (!"dependency".equals(tag.getName()) || !CommonsCliSupport.standardJar(tag) ||
            !CommonsCliSupport.GROUP.equals(tag.getChildValue("groupId").orElse(null)) ||
            !CommonsCliSupport.ARTIFACT.equals(tag.getChildValue("artifactId").orElse(null))) return false;
        Cursor dependencies = cursor.getParentTreeCursor();
        if (!(dependencies.getValue() instanceof Xml.Tag dependenciesTag) || !"dependencies".equals(dependenciesTag.getName())) {
            return false;
        }
        Cursor owner = dependencies.getParentTreeCursor();
        if (owner.getValue() instanceof Xml.Tag project && "project".equals(project.getName()) &&
            owner.getParentTreeCursor().getValue() instanceof Xml.Document) return true;
        if (!(owner.getValue() instanceof Xml.Tag profile) || !"profile".equals(profile.getName())) return false;
        Cursor profiles = owner.getParentTreeCursor();
        if (!(profiles.getValue() instanceof Xml.Tag profilesTag) || !"profiles".equals(profilesTag.getName())) return false;
        Cursor project = profiles.getParentTreeCursor();
        return project.getValue() instanceof Xml.Tag root && "project".equals(root.getName()) &&
               project.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    private static boolean mavenConfigurationHasDependency(UUID profile, boolean rootClassicDependency,
                                                            Set<UUID> profileClassicDependencies) {
        // Root build/compiler configuration is inherited by every profile, while profile-local configuration
        // is relevant only when the root dependency is visible there or that exact profile owns the dependency.
        return profile == null ? rootClassicDependency || !profileClassicDependencies.isEmpty() :
                rootClassicDependency || profileClassicDependencies.contains(profile);
    }

    private static boolean projectBuildPlugin(Cursor cursor) {
        Cursor plugins = cursor.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag pluginsTag) || !"plugins".equals(pluginsTag.getName())) return false;
        Cursor build = plugins.getParentTreeCursor();
        if (!(build.getValue() instanceof Xml.Tag buildTag) || !"build".equals(buildTag.getName())) return false;
        Cursor owner = build.getParentTreeCursor();
        if (owner.getValue() instanceof Xml.Tag project && "project".equals(project.getName()) &&
            owner.getParentTreeCursor().getValue() instanceof Xml.Document) return true;
        if (!(owner.getValue() instanceof Xml.Tag profile) || !"profile".equals(profile.getName())) return false;
        Cursor profiles = owner.getParentTreeCursor();
        if (!(profiles.getValue() instanceof Xml.Tag profilesTag) || !"profiles".equals(profilesTag.getName())) return false;
        Cursor project = profiles.getParentTreeCursor();
        return project.getValue() instanceof Xml.Tag root && "project".equals(root.getName()) &&
               project.getParentTreeCursor().getValue() instanceof Xml.Document;
    }
}
