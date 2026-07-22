package com.huawei.clouds.openrewrite.fastjson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.Map;

/** Marks legacy Fastjson process properties whose Fastjson2 behavior requires an explicit security choice. */
public final class FindFastjson2PropertiesRisks extends Recipe {
    private static final Map<String, String> MESSAGES = Map.of(
            "fastjson.parser.autoTypeSupport", "Fastjson2 does not preserve the old AutoType switch; use a narrow AutoTypeBeforeHandler allow-list",
            "fastjson.parser.autoTypeAccept", "Fastjson2 removed the old whitelist model; review and migrate every accepted package prefix",
            "fastjson.parser.deny", "Fastjson2 uses a different AutoType security model; verify this deny rule rather than assuming it remains authoritative",
            "fastjson.parser.deny.internal", "This Fastjson 1.x internal deny property is not consumed by the 2.0.62 compatibility ParserConfig",
            "fastjson.parser.safeMode", "Fastjson2 safe mode is initialized process-wide; verify the deployment startup value and inability to toggle it"
    );

    @Override
    public String getDisplayName() {
        return "Find legacy Fastjson security properties";
    }

    @Override
    public String getDescription() {
        return "Mark exact Fastjson 1.x AutoType and safe-mode properties that must be reviewed for Fastjson2.";
    }

    @Override
    public PropertiesIsoVisitor<ExecutionContext> getVisitor() {
        return new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                Properties.Entry e = super.visitEntry(entry, ctx);
                Properties.File file = getCursor().firstEnclosing(Properties.File.class);
                if (file == null || !UpgradeSelectedFastjsonDependency.isProjectPath(file.getSourcePath())) {
                    return e;
                }
                String message = MESSAGES.get(e.getKey());
                return message == null ? e : SearchResult.found(e, message);
            }
        };
    }
}
