package com.huawei.clouds.openrewrite.bcprovjdk18on;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Cursor;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Set;
import java.util.regex.Pattern;

/** Locate external provider, ASN.1, PKCS12, and compatibility configuration decisions. */
public final class FindBcProv184ConfigurationRisks extends Recipe {
    static final String PROVIDER_ORDER =
            "A configured BC/BCPQC provider changes process-wide JCA selection; verify provider index, duplicate " +
            "lineages, 1.84 signed-JAR loading, module/class-loader visibility, and every implicit getInstance call";
    static final String ASN1_LIMITS =
            "1.84 adds ASN.1 maximum constructed depth (default 32) and input size limits; set an override only from " +
            "measured trusted payload requirements and retain bounded malformed/deep/large-input tests";
    static final String PKCS12_LIMIT =
            "1.84 defaults PKCS12 maximum iteration count to 5,000,000; inventory existing stores and set an override " +
            "only with an explicit CPU/DoS budget, interoperability test, and rollback plan";
    static final String COMPATIBILITY_SWITCH =
            "This Bouncy Castle compatibility/security switch changes validation or entropy behavior; re-evaluate the " +
            "exception against 1.84, document its threat model, and test both configured and default paths";
    static final String PACKAGING =
            "Manifest/OSGi/module/shading metadata references Bouncy Castle internals or signed artifacts; verify 1.84 " +
            "exports, service loading, split packages, signature files, and packaged-provider initialization";

    private static final Set<String> LIMIT_KEYS = Set.of(
            "org.bouncycastle.asn1.max_cons_depth", "org.bouncycastle.asn1.max_limit");
    private static final Set<String> PKCS12_KEYS = Set.of(
            "org.bouncycastle.pkcs12.max_it_count", "org.bouncycastle.pkcs12.default");
    private static final Set<String> COMPATIBILITY_KEYS = Set.of(
            "org.bouncycastle.asn1.allow_wrong_oid_enc", "org.bouncycastle.pemreader.lax",
            "org.bouncycastle.ec.disable_f2m", "org.bouncycastle.drbg.effective_256bits_entropy",
            "org.bouncycastle.rsa.no_lenstra_check");
    private static final Pattern SECURITY_PROVIDER_KEY = Pattern.compile("security\\.provider\\.[0-9]+");
    private static final Pattern NESTED_PROVIDER_KEY = Pattern.compile("provider\\.[0-9]+");
    private static final Set<String> PROVIDER_CLASSES = Set.of(
            "org.bouncycastle.jce.provider.BouncyCastleProvider",
            "org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider");

    @Override
    public String getDisplayName() {
        return "Find Bouncy Castle Provider 1.84 configuration migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark java.security/provider order, ASN.1 limits, PKCS12 limits, validation/entropy switches, and " +
               "signed-provider packaging metadata that require security review.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedBcProvDependency.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Properties.File properties) return properties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return yaml(yaml, ctx);
                if (tree instanceof Xml.Document xml && !"pom.xml".equals(file)) return xml(xml, ctx);
                if (tree instanceof PlainText text) return plain(text, file);
                return tree;
            }
        };
    }

    private static Properties.File properties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                Properties.Entry visited = super.visitEntry(entry, ec);
                String message = propertyMessage(visited.getKey(), visited.getValue().getText());
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents yaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ec);
                String value = visited.getValue() instanceof Yaml.Scalar scalar ? scalar.getValue() : "";
                String message = structuredMessage(visited.getKey().getValue(), value, underSecurity(getCursor()));
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document xml(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                String key = attribute(visited, "name");
                if (key == null) key = attribute(visited, "key");
                if (key == null) key = visited.getName();
                String value = attribute(visited, "value");
                if (value == null) value = visited.getValue().orElse("");
                String message = structuredMessage(key, value, false);
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static PlainText plain(PlainText source, String file) {
        String value = source.getText();
        String message = ("java.security".equals(file) || file.endsWith(".policy"))
                ? lineMessage(value) : null;
        PlainText result = message == null ? source : mark(source, message);
        if (("MANIFEST.MF".equals(file) || "bnd.bnd".equals(file)) &&
            (value.contains("org.bouncycastle") || value.contains("bcprov"))) result = mark(result, PACKAGING);
        return result;
    }

    private static String propertyMessage(String key, String value) {
        return structuredMessage(key.trim(), value.trim(), false);
    }

    private static String structuredMessage(String key, String value, boolean nestedProviderKey) {
        if (LIMIT_KEYS.contains(key)) return ASN1_LIMITS;
        if (PKCS12_KEYS.contains(key)) return PKCS12_LIMIT;
        if (COMPATIBILITY_KEYS.contains(key)) return COMPATIBILITY_SWITCH;
        if ((SECURITY_PROVIDER_KEY.matcher(key).matches() ||
             nestedProviderKey && NESTED_PROVIDER_KEY.matcher(key).matches()) &&
            PROVIDER_CLASSES.contains(value.trim())) return PROVIDER_ORDER;
        return null;
    }

    private static String attribute(Xml.Tag tag, String name) {
        return tag.getAttributes().stream().filter(attribute -> name.equals(attribute.getKeyAsString()))
                .map(Xml.Attribute::getValueAsString).findFirst().orElse(null);
    }

    private static boolean underSecurity(Cursor cursor) {
        Cursor mapping = cursor.getParentTreeCursor();
        Cursor owner = mapping.getParentTreeCursor();
        return owner.getValue() instanceof Yaml.Mapping.Entry entry &&
               "security".equals(entry.getKey().getValue());
    }

    private static String lineMessage(String text) {
        for (String line : text.split("\\R")) {
            String candidate = line.strip();
            if (candidate.isEmpty() || candidate.startsWith("#") || candidate.startsWith("//")) continue;
            int separator = candidate.indexOf('=');
            if (separator < 0) separator = candidate.indexOf(':');
            if (separator < 0) continue;
            String message = propertyMessage(candidate.substring(0, separator), candidate.substring(separator + 1));
            if (message != null) return message;
        }
        return null;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
