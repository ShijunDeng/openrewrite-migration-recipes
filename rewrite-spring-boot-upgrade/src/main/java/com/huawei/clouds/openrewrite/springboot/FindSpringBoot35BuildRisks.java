package com.huawei.clouds.openrewrite.springboot;

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

import java.util.Set;

/** Precise build-owner and runtime-baseline markers for Spring Boot 3.5.15. */
public final class FindSpringBoot35BuildRisks extends Recipe {
    static final String OWNER =
            "Spring Boot is versionless, variable, ranged, dynamic, catalog-managed, inherited, shared, or externally owned; migrate the actual owner and verify that 3.5.15 resolves";
    static final String OUTSIDE =
            "This fixed Spring Boot version is outside the workbook-visible source set and target; it is intentionally not auto-upgraded";
    static final String VARIANT =
            "This classified or non-JAR Spring Boot artifact is outside deterministic scope; verify the exact 3.5.15 artifact shape manually";
    static final String BOM_SHAPE =
            "spring-boot-dependencies is not a local Maven import BOM; migrate its actual platform owner instead of rewriting a lookalike dependency";
    static final String JAVA =
            "Spring Boot 3.5 requires Java 17 or newer; align compiler, toolchain, CI, container, and runtime JDKs";
    static final String REMOVED_PARENT =
            "The internal spring-boot-parent module is no longer published in Spring Boot 3.5; replace it with application-owned dependency management (spring-boot-starter-parent is a different artifact)";
    static final String CLOUD =
            "Spring Cloud must use a release train compatible with Spring Boot 3.5; upgrade and test the Cloud BOM as its own owner";
    private static final Set<String> JAVA_KEYS = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target");

    @Override
    public String getDisplayName() {
        return "Find Spring Boot 3.5 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark non-local version owners, variants, non-whitelist versions, forbidden downgrades, " +
               "Java 17, removed parent, malformed BOM, and Spring Cloud alignment boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringBootSupport.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document xml && "pom.xml".equals(fileName)) {
                    boolean selectedProject = source.getMarkers()
                            .findFirst(SpringBootProjectMarker.class).isPresent();
                    return maven(xml, selectedProject, ctx);
                }
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

    private static Xml.Document maven(
            Xml.Document source, boolean selectedProject, ExecutionContext ctx) {
        SpringBootSupport.PomProperties properties = SpringBootSupport.analyzeProperties(source, ctx);
        boolean bootOwner = containsBootCoordinates(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (SpringBootSupport.isBootDependency(getCursor(), t) &&
                    !SpringBootSupport.isStandardArtifact(t)) return SpringBootSupport.mark(t, VARIANT);
                if (SpringBootSupport.isBootOwner(getCursor(), t)) {
                    String raw = t.getChildValue("version").orElse("");
                    String resolved = properties.resolveUnique(raw, getCursor());
                    String message = properties.safeReference(raw, getCursor())
                            ? versionMessage(raw, resolved) : OWNER;
                    return message == null ? t : SpringBootSupport.markVersionOrOwner(t, message);
                }
                if (isBootBomLookalike(getCursor(), t)) return SpringBootSupport.mark(t, BOM_SHAPE);
                if (isRemovedBootParent(getCursor(), t)) {
                    return SpringBootSupport.markVersionOrOwner(t, REMOVED_PARENT);
                }
                if ((bootOwner || selectedProject) &&
                    JAVA_KEYS.contains(t.getName()) &&
                    SpringBootSupport.isMavenPropertyDefinition(getCursor(), t) &&
                    SpringBootSupport.belowJava17(t.getValue().orElse(""))) {
                    return SpringBootSupport.mark(t, JAVA);
                }
                if (bootOwner && SpringBootSupport.isProjectDependency(getCursor(), t) &&
                    "org.springframework.cloud".equals(t.getChildValue("groupId").orElse("")) &&
                    "spring-cloud-dependencies".equals(t.getChildValue("artifactId").orElse(""))) {
                    return SpringBootSupport.markVersionOrOwner(t, CLOUD);
                }
                return t;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean containsBootCoordinates(Xml.Document source, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (SpringBootSupport.isBootOwner(getCursor(), t)) found[0] = true;
                return t;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean isBootBomLookalike(Cursor cursor, Xml.Tag tag) {
        return SpringBootSupport.isProjectDependency(cursor, tag) &&
               SpringBootSupport.GROUP.equals(tag.getChildValue("groupId").orElse("")) &&
               SpringBootSupport.BOM.equals(tag.getChildValue("artifactId").orElse("")) &&
               !SpringBootSupport.isBootBom(cursor, tag);
    }

    private static boolean isRemovedBootParent(Cursor cursor, Xml.Tag tag) {
        if (!"parent".equals(tag.getName()) ||
            !SpringBootSupport.GROUP.equals(tag.getChildValue("groupId").orElse("")) ||
            !"spring-boot-parent".equals(tag.getChildValue("artifactId").orElse(""))) return false;
        Cursor project = cursor.getParentTreeCursor();
        return project.getValue() instanceof Xml.Tag projectTag && "project".equals(projectTag.getName());
    }

    private static String versionMessage(String raw, String resolved) {
        if (raw.isBlank() || resolved == null || !SpringBootSupport.FIXED_VERSION.matcher(resolved).matches()) {
            return OWNER;
        }
        if (SpringBootSupport.SOURCE_VERSIONS.contains(resolved) ||
            SpringBootSupport.TARGET.equals(resolved)) return null;
        return SpringBootSupport.targetConflict(resolved) ? SpringBootSupport.TARGET_CONFLICT : OUTSIDE;
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                return markGradle(getCursor(), m);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringBootSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = rootPlatformLiteral(getCursor());
                boolean buildscriptPlugin =
                        SpringBootSupport.isBuildscriptClasspathLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                if (buildscriptPlugin) return markBuildscriptPluginCoordinate(l);
                return direct || platform ? markCoordinate(l, platform) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                return markGradle(getCursor(), m);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringBootSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = rootPlatformLiteral(getCursor());
                boolean buildscriptPlugin =
                        SpringBootSupport.isBuildscriptClasspathLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                if (buildscriptPlugin) return markBuildscriptPluginCoordinate(l);
                return direct || platform ? markCoordinate(l, platform) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static J.MethodInvocation markGradle(Cursor cursor, J.MethodInvocation invocation) {
        if (SpringBootSupport.isBootGradlePluginId(cursor, invocation)) {
            Cursor parent = cursor.getParentTreeCursor();
            boolean versioned = parent.getValue() instanceof J.MethodInvocation version &&
                                SpringBootSupport.isBootGradlePluginVersion(parent, version);
            return versioned ? invocation : SpringBootSupport.mark(invocation, OWNER);
        }
        if (SpringBootSupport.isBootGradlePluginVersion(cursor, invocation)) {
            if (invocation.getArguments().size() != 1 ||
                !(invocation.getArguments().get(0) instanceof J.Literal literal) ||
                !(literal.getValue() instanceof String version)) {
                return SpringBootSupport.mark(invocation, OWNER);
            }
            String message = versionMessage(version, version);
            return message == null ? invocation : SpringBootSupport.mark(invocation, message);
        }
        if (SpringBootSupport.isRootGradleDependency(cursor, invocation) &&
            SpringBootSupport.GROUP.equals(SpringBootSupport.mapValue(invocation, "group")) &&
            SpringBootSupport.ARTIFACT.equals(SpringBootSupport.mapValue(invocation, "name"))) {
            if (SpringBootSupport.hasVariant(invocation)) return SpringBootSupport.mark(invocation, VARIANT);
            String version = SpringBootSupport.mapValue(invocation, "version");
            String message = versionMessage(version == null ? "" : version, version);
            return message == null ? invocation : SpringBootSupport.mark(invocation, message);
        }
        return invocation;
    }

    private static boolean rootPlatformLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof J.MethodInvocation platform) ||
            !("platform".equals(platform.getSimpleName()) ||
              "enforcedPlatform".equals(platform.getSimpleName()))) return false;
        Cursor dependency = parent.getParentTreeCursor();
        return dependency.getValue() instanceof J.MethodInvocation invocation &&
               SpringBootSupport.isRootGradleDependency(dependency, invocation);
    }

    private static J.Literal markCoordinate(J.Literal literal, boolean allowBom) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String version = SpringBootSupport.coordinateVersion(value, SpringBootSupport.ARTIFACT);
        if (version == null && allowBom) {
            version = SpringBootSupport.coordinateVersion(value, SpringBootSupport.BOM);
        }
        if (version == null) return literal;
        String message = versionMessage(version, version);
        return message == null ? literal : SpringBootSupport.mark(literal, message);
    }

    private static J.Literal markBuildscriptPluginCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String version = SpringBootSupport.coordinateVersion(
                value, SpringBootSupport.GRADLE_PLUGIN_ARTIFACT);
        if (version == null) return literal;
        String message = versionMessage(version, version);
        return message == null ? literal : SpringBootSupport.mark(literal, message);
    }
}
