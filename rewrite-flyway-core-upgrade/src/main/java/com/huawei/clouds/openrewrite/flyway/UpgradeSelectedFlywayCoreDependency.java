package com.huawei.clouds.openrewrite.flyway;

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Upgrades only spreadsheet-selected Flyway Core declarations. */
public final class UpgradeSelectedFlywayCoreDependency extends Recipe {
    private static final String PREFIX = FlywayVersions.GROUP + ":" + FlywayVersions.CORE + ":";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern FLYWAY_VERSION_WORD = Pattern.compile("\\bflywayVersion\\b");

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected Flyway Core dependencies";
    }

    @Override
    public String getDescription() {
        return "Upgrade only explicit org.flywaydb:flyway-core versions named by the spreadsheet, preserving managed, dynamic, ranged, profiled, custom-artifact, and generated declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !FlywayVersions.isProjectPath(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return upgradePom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit compilationUnit && fileName.endsWith(".gradle")) {
                    return upgradeGroovy(compilationUnit, ctx);
                }
                if (tree instanceof K.CompilationUnit compilationUnit && fileName.endsWith(".gradle.kts")) {
                    return upgradeKotlin(compilationUnit, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document upgradePom(Xml.Document document, ExecutionContext ctx) {
        Map<String, Integer> rootDefinitions = new HashMap<>();
        Map<String, String> propertyValues = new HashMap<>();
        document.getRoot().getChild("properties").ifPresent(properties ->
                properties.getChildren().stream().filter(Xml.Tag.class::isInstance).map(Xml.Tag.class::cast)
                        .forEach(property -> {
                            rootDefinitions.merge(property.getName(), 1, Integer::sum);
                            property.getValue().ifPresent(value -> propertyValues.put(property.getName(), value.trim()));
                        }));

        Map<String, Integer> allReferences = new HashMap<>();
        Map<String, Integer> eligibleCoreReferences = new HashMap<>();
        Set<String> shadowedProperties = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                collectReferences(charData.getText(), allReferences);
                return super.visitCharData(charData, executionContext);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                collectReferences(attribute.getValueAsString(), allReferences);
                return super.visitAttribute(attribute, executionContext);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (FlywayVersions.isPropertiesChild(getCursor(), tag) &&
                    !FlywayVersions.isProjectPropertiesChild(getCursor(), tag)) {
                    shadowedProperties.add(tag.getName());
                }
                if (FlywayVersions.isMavenCoreDependency(getCursor(), tag)) {
                    propertyName(tag.getChildValue("version").orElse(null))
                            .filter(name -> FlywayVersions.isSource(propertyValues.get(name)))
                            .ifPresent(name -> eligibleCoreReferences.merge(name, 1, Integer::sum));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visitNonNull(document, ctx);

        Set<String> safeProperties = new HashSet<>();
        eligibleCoreReferences.forEach((name, count) -> {
            if (count.equals(allReferences.get(name)) && rootDefinitions.getOrDefault(name, 0) == 1 &&
                !shadowedProperties.contains(name)) safeProperties.add(name);
        });

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (FlywayVersions.isProjectPropertiesChild(getCursor(), visited) &&
                    safeProperties.contains(visited.getName()) &&
                    visited.getValue().map(String::trim).filter(FlywayVersions::isSource).isPresent()) {
                    return visited.withValue(FlywayVersions.TARGET);
                }
                if (!FlywayVersions.isMavenCoreDependency(getCursor(), visited)) return visited;
                String declared = visited.getChildValue("version").map(String::trim).orElse("");
                if (FlywayVersions.isSource(declared)) return visited.withChildValue("version", FlywayVersions.TARGET);
                String property = propertyName(declared).orElse(null);
                if (property != null && eligibleCoreReferences.containsKey(property) &&
                    !safeProperties.contains(property) && !shadowedProperties.contains(property) &&
                    rootDefinitions.getOrDefault(property, 0) == 1) {
                    return visited.withChildValue("version", FlywayVersions.TARGET);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit upgradeGroovy(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean exclusiveFlywayVersion = hasExclusiveFlywayVersionVariable(compilationUnit, ctx);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext executionContext) {
                J.Assignment visited = super.visitAssignment(assignment, executionContext);
                if (exclusiveFlywayVersion && "flywayVersion".equals(visited.getVariable().printTrimmed()) &&
                    visited.getAssignment() instanceof J.Literal literal &&
                    literal.getValue() instanceof String version && FlywayVersions.isSource(version)) {
                    return visited.withAssignment(upgradeVersionLiteral(literal));
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (!FlywayVersions.isGradleDependencyInvocation(getCursor(), visited) || hasGradleVariant(visited)) {
                    return visited;
                }
                if (FlywayVersions.GROUP.equals(mapValue(visited, "group")) &&
                    FlywayVersions.CORE.equals(mapValue(visited, "name")) &&
                    FlywayVersions.isSource(mapValue(visited, "version"))) {
                    return visited.withArguments(visited.getArguments().stream().map(argument ->
                            argument instanceof G.MapEntry entry && "version".equals(mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal
                                    ? entry.withValue(upgradeVersionLiteral(literal)) : argument).toList());
                }
                return visited.withArguments(visited.getArguments().stream().map(argument ->
                        argument instanceof G.MapLiteral map && !hasGradleVariant(map) ? upgradeMap(map) : argument)
                        .toList());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = FlywayVersions.isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static K.CompilationUnit upgradeKotlin(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = FlywayVersions.isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(compilationUnit, ctx);
    }

    private static boolean hasExclusiveFlywayVersionVariable(G.CompilationUnit compilationUnit,
                                                              ExecutionContext ctx) {
        boolean[] referencedByCore = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (FlywayVersions.isGradleDependencyInvocation(getCursor(), method)) {
                    String source = method.printTrimmed(getCursor());
                    if (source.contains(PREFIX + "$flywayVersion") ||
                        source.contains(PREFIX + "${flywayVersion}")) referencedByCore[0] = true;
                }
                return referencedByCore[0] ? method : super.visitMethodInvocation(method, executionContext);
            }
        }.visit(compilationUnit, ctx);
        Matcher matcher = FLYWAY_VERSION_WORD.matcher(compilationUnit.printAll());
        int references = 0;
        while (matcher.find()) references++;
        return referencedByCore[0] && references == 2;
    }

    static Optional<String> propertyName(String version) {
        if (version == null) return Optional.empty();
        Matcher matcher = PROPERTY_REFERENCE.matcher(version.trim());
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static void collectReferences(String text, Map<String, Integer> references) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) references.merge(matcher.group(1), 1, Integer::sum);
    }

    static boolean hasGradleVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .map(UpgradeSelectedFlywayCoreDependency::mapKey)
                .anyMatch(UpgradeSelectedFlywayCoreDependency::isVariantKey) ||
               invocation.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast)
                       .anyMatch(UpgradeSelectedFlywayCoreDependency::hasGradleVariant);
    }

    private static boolean hasGradleVariant(G.MapLiteral map) {
        return map.getElements().stream().map(UpgradeSelectedFlywayCoreDependency::mapKey)
                .anyMatch(UpgradeSelectedFlywayCoreDependency::isVariantKey);
    }

    private static boolean isVariantKey(String key) {
        return "classifier".equals(key) || "ext".equals(key) || "type".equals(key);
    }

    static String mapValue(J.MethodInvocation invocation, String key) {
        String direct = invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
        if (direct != null) return direct;
        return invocation.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast)
                .map(map -> mapValue(map, key)).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        if (!FlywayVersions.GROUP.equals(mapValue(map, "group")) ||
            !FlywayVersions.CORE.equals(mapValue(map, "name")) ||
            !FlywayVersions.isSource(mapValue(map, "version"))) return map;
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(mapKey(entry)) && entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(upgradeVersionLiteral(literal)) : entry).toList());
    }

    static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    static boolean isCoreCoordinate(String value) {
        return value.equals(FlywayVersions.GROUP + ":" + FlywayVersions.CORE) || value.startsWith(PREFIX);
    }

    static boolean isTargetCoreCoordinate(String value) {
        return (PREFIX + FlywayVersions.TARGET).equals(value);
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value) || !value.startsWith(PREFIX) ||
            !FlywayVersions.isSource(value.substring(PREFIX.length()))) return literal;
        return replaceLiteral(literal, value, PREFIX + FlywayVersions.TARGET);
    }

    private static J.Literal upgradeVersionLiteral(J.Literal literal) {
        return literal.getValue() instanceof String value && FlywayVersions.isSource(value)
                ? replaceLiteral(literal, value, FlywayVersions.TARGET) : literal;
    }

    private static J.Literal replaceLiteral(J.Literal literal, String oldValue, String replacement) {
        return literal.withValue(replacement).withValueSource(literal.getValueSource() == null ? null :
                literal.getValueSource().replace(oldValue, replacement));
    }
}
