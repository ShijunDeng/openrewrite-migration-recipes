package com.huawei.clouds.openrewrite.d3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Mark D3 6/7 source incompatibilities whose replacement needs application context. */
public final class FindD3SourceMigrationRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find D3 6 and 7 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Precisely mark removed event, pointer, collection and Voronoi APIs plus ESM, formatting, " +
               "InternMap, DOMMatrix/SSR, array-like selection and geographic precision behavior changes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!D3SourceText.isSupported(visited)) {
                    return visited;
                }
                Set<String> aliases = D3SourceText.namespaceAliases(visited.getText());
                return D3SourceText.markMatches(visited, risks(aliases));
            }
        };
    }

    private static List<D3SourceText.RiskPattern> risks(Set<String> aliases) {
        List<D3SourceText.RiskPattern> risks = new ArrayList<>();
        risks.add(risk(D3SourceText.member(aliases, "event"),
                "D3 6 removed d3.event; add event as the listener's first parameter and re-check datum/index assumptions"));
        risks.add(risk(D3SourceText.memberCall(aliases, "mouse|touch|touches|clientPoint"),
                "Removed pointer helper; choose pointer(event,target) or pointers(event,target) after verifying coordinates and touch multiplicity"));
        risks.add(risk(D3SourceText.memberCall(aliases, "nest|map|set|keys|values|entries"),
                "D3 collection API was removed; choose group/rollup, native Map/Set, or Object.* while preserving key coercion and order"));
        risks.add(risk(D3SourceText.memberCall(aliases, "voronoi"),
                "d3.voronoi was removed; port the full extent/polygons/links/triangles/find workflow to Delaunay and Voronoi"));
        risks.add(risk(Pattern.compile("\\brequire\\(\\s*([\"'])d3\\1\\s*\\)"),
                "D3 7 is pure ESM; replace CommonJS loading and verify Node, Jest, SSR, worker and bundler module configuration"));
        risks.add(risk(D3SourceText.memberCall(aliases, "format"),
                "D3 6 formatting uses Unicode minus by default; verify snapshots, parsers, CSV exports, width and copy/paste expectations"));
        risks.add(risk(D3SourceText.memberCall(aliases, "scaleOrdinal"),
                "D3 7 ordinal domains use InternMap/valueOf instead of string coercion; test Date and object key deduplication"));
        risks.add(risk(D3SourceText.memberCall(aliases, "interpolateTransformCss"),
                "D3 6 uses DOMMatrix and absolute CSS units here; provide browser/SSR support and remove relative transform units"));
        risks.add(risk(D3SourceText.memberCall(aliases, "selectAll"),
                "D3 7 snapshots array-like inputs; code relying on a live NodeList must refresh the selection explicitly"));
        risks.add(risk(D3SourceText.memberCall(aliases, "geoCircle"),
                "D3 7.9 changes geoCircle precision to 2 degrees; visually diff paths, point count and performance"));
        risks.add(risk(Pattern.compile("(?<=[.])clipAngle(?=\\s*\\()"),
                "D3 7.9 changes projection.clipAngle precision to 2 degrees; visually diff clipping boundaries and snapshots"));
        risks.add(risk(Pattern.compile("\\bimport\\s*\\{[^}]*\\b(?:histogram|scan)\\b[^}]*}\\s*from\\s*([\"'])(?:d3|d3-array)\\1"),
                "Named histogram/scan import needs binding-aware rename to bin/leastIndex; update import and every reference together"));
        risks.add(risk(D3SourceText.memberCall(aliases, "bin"),
                "D3 7 bin ignores null; verify missing-value filtering, thresholds and sample counts"));
        risks.add(risk(D3SourceText.member(aliases, "ascending|descending"),
                "D3 7 comparators no longer consider null comparable; normalize missing values before sorting"));
        return risks;
    }

    private static D3SourceText.RiskPattern risk(Pattern pattern, String message) {
        return new D3SourceText.RiskPattern(pattern, message);
    }
}
