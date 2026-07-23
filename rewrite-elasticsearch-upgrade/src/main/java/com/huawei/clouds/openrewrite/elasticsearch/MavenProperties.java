package com.huawei.clouds.openrewrite.elasticsearch;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Resolves only locally and exclusively owned Maven properties.
 *
 * <p>The source-version gate and the dependency edit deliberately share this
 * analysis so they cannot disagree about whether a property belongs to the
 * selected Testcontainers Elasticsearch declaration.</p>
 */
final class MavenProperties {
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    private final Map<PropertyOwner, Integer> definitions;
    private final Map<PropertyOwner, String> values;
    private final Set<PropertyOwner> safe;

    private MavenProperties(Map<PropertyOwner, Integer> definitions,
                            Map<PropertyOwner, String> values,
                            Set<PropertyOwner> safe) {
        this.definitions = definitions;
        this.values = values;
        this.safe = safe;
    }

    static MavenProperties analyze(Xml.Document source, ExecutionContext ctx) {
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, String> values = new HashMap<>();
        Set<String> profilePropertyNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (ElasticsearchUpgradeSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyOwner owner = propertyOwner(getCursor(), visited.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(owner, value.trim()));
                    if (!"ROOT".equals(owner.scope())) {
                        profilePropertyNames.add(owner.name());
                    }
                }
                return visited;
            }
        }.visitNonNull(source, ctx);

        Map<PropertyOwner, Integer> references = new HashMap<>();
        Map<PropertyOwner, Integer> ownedReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), definitions, references,
                        targetVersionReference(getCursor(), visited.getText()) ? ownedReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), definitions, references, null);
                return visited;
            }
        }.visitNonNull(source, ctx);

        Set<PropertyOwner> safe = ownedReferences.keySet().stream()
                .filter(owner -> ElasticsearchUpgradeSupport.SOURCE.equals(values.get(owner)))
                .filter(owner -> definitions.getOrDefault(owner, 0) == 1)
                .filter(owner -> references.getOrDefault(owner, 0)
                        .equals(ownedReferences.getOrDefault(owner, 0)))
                .filter(owner -> !"ROOT".equals(owner.scope()) ||
                                 !profilePropertyNames.contains(owner.name()))
                .collect(Collectors.toUnmodifiableSet());
        return new MavenProperties(Map.copyOf(definitions), Map.copyOf(values), safe);
    }

    boolean isSafe(PropertyOwner owner) {
        return safe.contains(owner);
    }

    String resolveOwnedVersion(String raw, Cursor cursor) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher matcher = PROPERTY_REFERENCE.matcher(raw.trim());
        if (!matcher.matches()) {
            return raw.trim();
        }
        PropertyOwner owner = resolvedOwner(cursor, matcher.group(1), definitions);
        return safe.contains(owner) ? values.get(owner) : null;
    }

    static PropertyOwner propertyOwner(Cursor cursor, String name) {
        String profile = profileId(cursor);
        return new PropertyOwner(profile == null ? "ROOT" : profile, name);
    }

    private static PropertyOwner resolvedOwner(Cursor cursor, String name,
                                               Map<PropertyOwner, Integer> definitions) {
        String profile = profileId(cursor);
        PropertyOwner local = profile == null ? null : new PropertyOwner(profile, name);
        return local != null && definitions.containsKey(local)
                ? local : new PropertyOwner("ROOT", name);
    }

    private static boolean targetVersionReference(Cursor cursor, String text) {
        if (!PROPERTY_REFERENCE.matcher(text.trim()).matches()) {
            return false;
        }
        Cursor versionCursor = cursor.getParentTreeCursor();
        if (!(versionCursor.getValue() instanceof Xml.Tag version) ||
            !"version".equals(version.getName())) {
            return false;
        }
        Cursor dependencyCursor = versionCursor.getParentTreeCursor();
        return dependencyCursor.getValue() instanceof Xml.Tag dependency &&
               ElasticsearchUpgradeSupport.isTestcontainersDependency(dependencyCursor, dependency) &&
               ElasticsearchUpgradeSupport.standardJar(dependency);
    }

    private static void collectReferences(String text, Cursor cursor,
                                          Map<PropertyOwner, Integer> definitions,
                                          Map<PropertyOwner, Integer> references,
                                          Map<PropertyOwner, Integer> ownedReferences) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            PropertyOwner owner = resolvedOwner(cursor, matcher.group(1), definitions);
            references.merge(owner, 1, Integer::sum);
            if (ownedReferences != null) {
                ownedReferences.merge(owner, 1, Integer::sum);
            }
        }
    }

    private static String profileId(Cursor cursor) {
        for (Cursor current = cursor; current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) {
                break;
            }
        }
        return null;
    }

    record PropertyOwner(String scope, String name) {
    }
}
