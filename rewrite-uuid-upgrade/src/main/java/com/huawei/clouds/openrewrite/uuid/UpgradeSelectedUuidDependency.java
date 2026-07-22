package com.huawei.clouds.openrewrite.uuid;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.util.Set;

/** Upgrade only scalar uuid declarations anchored to versions visible in the spreadsheet. */
public final class UpgradeSelectedUuidDependency extends Recipe {
    private static final Set<String> SOURCE_VERSIONS = Set.of(
            "8.3.2", "9.0.0", "9.0.1", "10.0.0", "11.0.3", "11.1.0");

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected uuid declarations to 13.0.2";
    }

    @Override
    public String getDescription() {
        return "Upgrade exact, caret, or tilde direct uuid declarations selected by the spreadsheet without " +
               "changing compound ranges, prereleases, protocols, aliases, lockfiles, or unlisted versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!UuidManifestSupport.isPackageJson(
                        getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath()) ||
                    !UuidManifestSupport.PACKAGE.equals(UuidManifestSupport.key(visited)) ||
                    UuidManifestSupport.directDependencySection(getCursor()).isEmpty()) {
                    return visited;
                }
                String declaration = UuidManifestSupport.stringValue(visited);
                if (!selected(declaration) || !(visited.getValue() instanceof Json.Literal value)) {
                    return visited;
                }
                return visited.withValue(value.withSource("\"" + UuidManifestSupport.TARGET_VERSION + "\"")
                        .withValue(UuidManifestSupport.TARGET_VERSION));
            }
        };
    }

    private static boolean selected(String declaration) {
        if (declaration == null || declaration.isEmpty()) {
            return false;
        }
        String candidate = declaration;
        if (candidate.charAt(0) == '^' || candidate.charAt(0) == '~') {
            candidate = candidate.substring(1);
        }
        return SOURCE_VERSIONS.contains(candidate);
    }
}
