package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;

import java.util.Locale;
import java.util.Set;

/** Find service-provider and configuration strings that cannot be safely namespace-rewritten without an owner. */
public final class FindTomcatCatalinaResourceRisks extends Recipe {
    static final String SERVICE =
            "Tomcat 9 service-provider contract still uses javax Servlet/EL; rename the META-INF/services contract " +
            "and provider types to Jakarta after checking target-path collisions, provider order and framework discovery";
    static final String CONFIG =
            "Configuration still names a javax Servlet/EL type; identify whether the value drives reflection, scanning, " +
            "serialization, OSGi or documentation before changing it to Jakarta";
    private static final Set<String> CONFIG_SUFFIXES = Set.of(
            ".properties", ".yml", ".yaml", ".json", ".conf", ".cfg", ".ini", ".toml", ".mf");

    @Override
    public String getDisplayName() {
        return "Find Tomcat 9 Java EE namespace resource risks";
    }

    @Override
    public String getDescription() {
        return "Marks META-INF/services contracts and configuration values that retain javax.servlet or javax.el names.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof PlainText text) || !(tree instanceof SourceFile source) ||
                    UpgradeSelectedTomcatCatalinaDependency.generated(source.getSourcePath())) return tree;
                String path = source.getSourcePath().toString().replace('\\', '/');
                String content = text.getText();
                if (!containsJavaxWebType(content) && !containsJavaxWebType(path)) return tree;
                if (path.contains("META-INF/services/") && containsJavaxWebType(path)) {
                    return mark(text, SERVICE);
                }
                String lower = path.toLowerCase(Locale.ROOT);
                if (CONFIG_SUFFIXES.stream().anyMatch(lower::endsWith) || lower.endsWith("manifest.mf")) {
                    return mark(text, CONFIG);
                }
                return tree;
            }
        };
    }

    private static boolean containsJavaxWebType(String value) {
        return value.contains("javax.servlet.") || value.contains("javax.el.");
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
