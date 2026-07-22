package com.huawei.clouds.openrewrite.okhttp;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Changes only locally proven Maven JVM OkHttp 5 dependencies to the JVM artifact. */
public final class MigrateOwnedMavenOkHttpToJvm extends Recipe {
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String getDisplayName() {
        return "Use the OkHttp 5 JVM artifact in confirmed Maven JVM projects";
    }

    @Override
    public String getDescription() {
        return "Change owned Maven okhttp dependencies proven to resolve to 5.3.0 into okhttp-jvm, including " +
               "versionless consumers of a local managed declaration, without touching plugins or external BOMs.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof Xml.Document document) || !(tree instanceof SourceFile source) ||
                    !"pom.xml".equals(source.getSourcePath().getFileName().toString()) ||
                    UpgradeSelectedOkHttpDependency.generated(source.getSourcePath())) return tree;

                Map<PropertyKey, Integer> definitions = new HashMap<>();
                Map<PropertyKey, String> values = new HashMap<>();
                Set<UUID> localManagementOwners = new java.util.HashSet<>();
                UUID rootId = document.getRoot().getId();
                new XmlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                        Xml.Tag visited = super.visitTag(tag, executionContext);
                        if (UpgradeSelectedOkHttpDependency.isPropertyDefinition(getCursor(), visited)) {
                            propertyOwner(getCursor()).ifPresent(owner -> {
                                PropertyKey key = new PropertyKey(owner.getId(), visited.getName());
                                definitions.merge(key, 1, Integer::sum);
                                visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                            });
                        }
                        return visited;
                    }
                }.visitNonNull(document, ctx);

                new XmlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                        Xml.Tag visited = super.visitTag(tag, executionContext);
                        if (isOwnedCore(getCursor(), visited) &&
                            resolvesToTarget(getCursor(), visited, rootId, values, definitions)) {
                            managementOwner(getCursor()).ifPresent(localManagementOwners::add);
                        }
                        return visited;
                    }
                }.visitNonNull(document, ctx);

                return new XmlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                        Xml.Tag visited = super.visitTag(tag, executionContext);
                        if (!isOwnedCore(getCursor(), visited)) return visited;
                        boolean target = resolvesToTarget(getCursor(), visited, rootId, values, definitions);
                        UUID scopeOwner = dependencyOwner(getCursor()).map(Xml.Tag::getId).orElse(null);
                        boolean localVersionless = visited.getChild("version").isEmpty() &&
                                (localManagementOwners.contains(rootId) || localManagementOwners.contains(scopeOwner));
                        return target || localVersionless
                                ? visited.withChildValue("artifactId", "okhttp-jvm") : visited;
                    }
                }.visitNonNull(document, ctx);
            }
        };
    }

    private static boolean isOwnedCore(org.openrewrite.Cursor cursor, Xml.Tag dependency) {
        return UpgradeSelectedOkHttpDependency.isStandardOkHttpFamilyDependency(cursor, dependency) &&
               "okhttp".equals(dependency.getChildValue("artifactId").orElse(""));
    }

    private static java.util.Optional<UUID> managementOwner(org.openrewrite.Cursor dependencyCursor) {
        org.openrewrite.Cursor dependencies = dependencyCursor.getParentTreeCursor();
        org.openrewrite.Cursor owner = dependencies.getParentTreeCursor();
        if (!(owner.getValue() instanceof Xml.Tag tag) || !"dependencyManagement".equals(tag.getName())) {
            return java.util.Optional.empty();
        }
        org.openrewrite.Cursor managedOwner = owner.getParentTreeCursor();
        return UpgradeSelectedOkHttpDependency.isProjectOrProfile(managedOwner) &&
               managedOwner.getValue() instanceof Xml.Tag actualOwner
                ? java.util.Optional.of(actualOwner.getId()) : java.util.Optional.empty();
    }

    private static java.util.Optional<Xml.Tag> dependencyOwner(org.openrewrite.Cursor dependencyCursor) {
        org.openrewrite.Cursor dependencies = dependencyCursor.getParentTreeCursor();
        org.openrewrite.Cursor owner = dependencies.getParentTreeCursor();
        if (!(owner.getValue() instanceof Xml.Tag tag)) return java.util.Optional.empty();
        if (UpgradeSelectedOkHttpDependency.isProjectOrProfile(owner)) {
            return java.util.Optional.of(tag);
        }
        if (!"dependencyManagement".equals(tag.getName())) return java.util.Optional.empty();
        org.openrewrite.Cursor managedOwner = owner.getParentTreeCursor();
        return UpgradeSelectedOkHttpDependency.isProjectOrProfile(managedOwner) &&
               managedOwner.getValue() instanceof Xml.Tag actualOwner
                ? java.util.Optional.of(actualOwner) : java.util.Optional.empty();
    }

    private static java.util.Optional<Xml.Tag> propertyOwner(org.openrewrite.Cursor propertyCursor) {
        org.openrewrite.Cursor properties = propertyCursor.getParentTreeCursor();
        org.openrewrite.Cursor owner = properties.getParentTreeCursor();
        return UpgradeSelectedOkHttpDependency.isProjectOrProfile(owner) && owner.getValue() instanceof Xml.Tag tag
                ? java.util.Optional.of(tag) : java.util.Optional.empty();
    }

    private static boolean resolvesToTarget(org.openrewrite.Cursor dependencyCursor, Xml.Tag dependency, UUID rootId,
                                            Map<PropertyKey, String> values,
                                            Map<PropertyKey, Integer> definitions) {
        String version = dependency.getChildValue("version").map(String::trim).orElse("");
        if (UpgradeSelectedOkHttpDependency.TARGET.equals(version)) return true;
        Matcher matcher = PROPERTY.matcher(version);
        if (!matcher.matches()) return false;
        UUID owner = dependencyOwner(dependencyCursor).map(Xml.Tag::getId).orElse(null);
        if (owner == null) return false;
        PropertyKey local = new PropertyKey(owner, matcher.group(1));
        PropertyKey root = new PropertyKey(rootId, matcher.group(1));
        PropertyKey effective = definitions.containsKey(local) ? local : root;
        return definitions.getOrDefault(effective, 0) == 1 &&
               UpgradeSelectedOkHttpDependency.TARGET.equals(values.get(effective));
    }

    private record PropertyKey(UUID owner, String name) {}
}
