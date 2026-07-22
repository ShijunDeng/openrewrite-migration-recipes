package com.huawei.clouds.openrewrite.d3;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class D3SourceMigrationTest implements RewriteTest {
    private static final String DEPENDENCY = "com.huawei.clouds.openrewrite.d3.UpgradeD3To7_9_0";
    private static final String SOURCE = "com.huawei.clouds.openrewrite.d3.MigrateDeterministicD3SourceTo7";
    private static final String AUDIT = "com.huawei.clouds.openrewrite.d3.AuditD3SourceCompatibility";
    private static final String MIGRATION = "com.huawei.clouds.openrewrite.d3.MigrateD3To7_9_0";

    @Test
    void migratesD3NodeRealHistogramCall() {
        // d3-node/d3-node, fixed commit 17185801d09ddf5c37ae28e39cd7e763f360203e.
        // https://github.com/d3-node/d3-node/blob/17185801d09ddf5c37ae28e39cd7e763f360203e/examples/histogram.js
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        """
                        import * as d3 from 'd3'
                        var bins = d3.histogram()
                          .domain(x.domain())
                          .thresholds(x.ticks(20))(data)
                        """,
                        """
                        import * as d3 from 'd3'
                        var bins = d3.bin()
                          .domain(x.domain())
                          .thresholds(x.ticks(20))(data)
                        """,
                        source -> source.path("examples/histogram.js")
                )
        );
    }

    @Test
    void migratesRawgraphsRealScanCalls() {
        // rawgraphs/rawgraphs-charts, fixed commit dba66b4c5341344e239c9a86e4ed2f6ed7f157d2.
        // https://github.com/rawgraphs/rawgraphs-charts/blob/dba66b4c5341344e239c9a86e4ed2f6ed7f157d2/src/contourPlot/render.js
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        """
                        const n = possibilities[d3.scan(scores)]
                        const start = 1 + (d3.scan(p.map((xy) => -xy[1])) % n)
                        """,
                        """
                        const n = possibilities[d3.leastIndex(scores)]
                        const start = 1 + (d3.leastIndex(p.map((xy) => -xy[1])) % n)
                        """,
                        source -> source.path("src/contourPlot/render.js")
                )
        );
    }

    @Test
    void migratesImportedNamespaceAliasAndCommonJsAliasCalls() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        """
                        import * as arrays from "d3-array";
                        const charts = require('d3');
                        const bins = arrays.histogram()(values);
                        const index = charts.scan(values);
                        """,
                        """
                        import * as arrays from "d3-array";
                        const charts = require('d3');
                        const bins = arrays.bin()(values);
                        const index = charts.leastIndex(values);
                        """,
                        source -> source.path("src/stats.ts")
                )
        );
    }

    @Test
    void deterministicMigrationLeavesCommentsStringsTemplatesRegexAndLookalikesUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        """
                        // d3.histogram()
                        /* d3.scan(values) */
                        const docs = "d3.histogram() and d3.scan(values)";
                        const template = `d3.histogram()`;
                        const matcher = /d3.histogram\\(\\)/;
                        const histogram = service.histogram();
                        const property = d3.histogramScale();
                        """,
                        source -> source.path("src/docs.ts")
                )
        );
    }

    @Test
    void deterministicMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text(
                        "const bins = d3.histogram()(values); const i = d3.scan(values);\n",
                        "const bins = d3.bin()(values); const i = d3.leastIndex(values);\n",
                        source -> source.path("src/idempotent.js")
                )
        );
    }

    @Test
    void marksIpfsRealGlobalEventAtExactExpression() {
        // ipfs/ipfs-webui, fixed commit 818f09c371b7a4bf30acecd0fbb94e59da978069.
        // https://github.com/ipfs/ipfs-webui/blob/818f09c371b7a4bf30acecd0fbb94e59da978069/src/peers/WorldMap/WorldMap.js
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        ".on('mouseenter', () => handleMouseEnter(peerIds, d3.event.relatedTarget))\n",
                        ".on('mouseenter', () => handleMouseEnter(peerIds, ~~(D3 6 removed d3.event; add event as the listener's first parameter and re-check datum/index assumptions)~~>d3.event.relatedTarget))\n",
                        source -> source.path("src/peers/WorldMap/WorldMap.js")
                )
        );
    }

    @Test
    void marksPointerHelpersAtEachExactCall() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "const p = d3.mouse(node); const touches = d3.touches(node);\n",
                        "const p = ~~(Removed pointer helper; choose pointer(event,target) or pointers(event,target) after verifying coordinates and touch multiplicity)~~>d3.mouse(node); const touches = ~~(Removed pointer helper; choose pointer(event,target) or pointers(event,target) after verifying coordinates and touch multiplicity)~~>d3.touches(node);\n",
                        source -> source.path("src/pointer.js")
                )
        );
    }

    @Test
    void marksTouchAndClientPointWithoutChoosingTargets() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "const one = d3.touch(container); const local = d3.clientPoint(node, event);\n",
                        "const one = ~~(Removed pointer helper; choose pointer(event,target) or pointers(event,target) after verifying coordinates and touch multiplicity)~~>d3.touch(container); const local = ~~(Removed pointer helper; choose pointer(event,target) or pointers(event,target) after verifying coordinates and touch multiplicity)~~>d3.clientPoint(node, event);\n",
                        source -> source.path("src/touch.js")
                )
        );
    }

    @Test
    void marksCollectionAndVoronoiMigrationSites() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "const grouped = d3.nest().key(key).entries(data); const keys = d3.keys(object); const diagram = d3.voronoi().extent(bounds);\n",
                        "const grouped = ~~(D3 collection API was removed; choose group/rollup, native Map/Set, or Object.* while preserving key coercion and order)~~>d3.nest().key(key).entries(data); const keys = ~~(D3 collection API was removed; choose group/rollup, native Map/Set, or Object.* while preserving key coercion and order)~~>d3.keys(object); const diagram = ~~(d3.voronoi was removed; port the full extent/polygons/links/triangles/find workflow to Delaunay and Voronoi)~~>d3.voronoi().extent(bounds);\n",
                        source -> source.path("src/legacy-layout.js")
                )
        );
    }

    @Test
    void marksEveryRemovedCollectionFamilyAtTheCallSite() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "const map = d3.map(data); const set = d3.set(values); const values = d3.values(object); const entries = d3.entries(object);\n",
                        "const map = ~~(D3 collection API was removed; choose group/rollup, native Map/Set, or Object.* while preserving key coercion and order)~~>d3.map(data); const set = ~~(D3 collection API was removed; choose group/rollup, native Map/Set, or Object.* while preserving key coercion and order)~~>d3.set(values); const values = ~~(D3 collection API was removed; choose group/rollup, native Map/Set, or Object.* while preserving key coercion and order)~~>d3.values(object); const entries = ~~(D3 collection API was removed; choose group/rollup, native Map/Set, or Object.* while preserving key coercion and order)~~>d3.entries(object);\n",
                        source -> source.path("src/collections.js")
                )
        );
    }

    @Test
    void marksNamedImportsInsteadOfGuessingBindingRenames() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION)),
                text(
                        "import { histogram, scan as minimum } from 'd3-array';\nconst bins = histogram()(values);\n",
                        "~~(Named histogram/scan import needs binding-aware rename to bin/leastIndex; update import and every reference together)~~>import { histogram, scan as minimum } from 'd3-array';\nconst bins = histogram()(values);\n",
                        source -> source.path("src/named-import.ts")
                )
        );
    }

    @Test
    void marksCommonJsAndDomMatrixSsrRisks() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "const d3 = require('d3');\nconst transform = d3.interpolateTransformCss('translate(1em)', 'translate(2em)');\n",
                        "const d3 = ~~(D3 7 is pure ESM; replace CommonJS loading and verify Node, Jest, SSR, worker and bundler module configuration)~~>require('d3');\nconst transform = ~~(D3 6 uses DOMMatrix and absolute CSS units here; provide browser/SSR support and remove relative transform units)~~>d3.interpolateTransformCss('translate(1em)', 'translate(2em)');\n",
                        source -> source.path("server/render.cjs")
                )
        );
    }

    @Test
    void marksD3fcRealFormatAndInternMapSites() {
        // d3fc/d3fc, fixed commit 55ee5942a0885ea073838afaaa89eba24cefab15.
        // https://github.com/d3fc/d3fc/blob/55ee5942a0885ea073838afaaa89eba24cefab15/examples/bubble-chart/index.js
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "const color = d3.scaleOrdinal(d3.schemeCategory10).domain(regions.values());\nconst tick = d3.format(',d');\n",
                        "const color = ~~(D3 7 ordinal domains use InternMap/valueOf instead of string coercion; test Date and object key deduplication)~~>d3.scaleOrdinal(d3.schemeCategory10).domain(regions.values());\nconst tick = ~~(D3 6 formatting uses Unicode minus by default; verify snapshots, parsers, CSV exports, width and copy/paste expectations)~~>d3.format(',d');\n",
                        source -> source.path("examples/bubble-chart/index.js")
                )
        );
    }

    @Test
    void marksSelectionNullAndComparatorBehaviorChanges() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "const nodes = d3.selectAll(parent.childNodes); const bins = d3.bin()(values); values.sort(d3.ascending);\n",
                        "const nodes = ~~(D3 7 snapshots array-like inputs; code relying on a live NodeList must refresh the selection explicitly)~~>d3.selectAll(parent.childNodes); const bins = ~~(D3 7 bin ignores null; verify missing-value filtering, thresholds and sample counts)~~>d3.bin()(values); values.sort(~~(D3 7 comparators no longer consider null comparable; normalize missing values before sorting)~~>d3.ascending);\n",
                        source -> source.path("src/array-behavior.js")
                )
        );
    }

    @Test
    void marksD3_7_9GeographicPrecisionSites() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "const circle = d3.geoCircle().center(center); const projection = d3.geoMercator().clipAngle(80);\n",
                        "const circle = ~~(D3 7.9 changes geoCircle precision to 2 degrees; visually diff paths, point count and performance)~~>d3.geoCircle().center(center); const projection = d3.geoMercator().~~(D3 7.9 changes projection.clipAngle precision to 2 degrees; visually diff clipping boundaries and snapshots)~~>clipAngle(80);\n",
                        source -> source.path("src/map.ts")
                )
        );
    }

    @Test
    void auditLeavesCommentsStringsTemplatesRegexAndUnrelatedLibrariesUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        """
                        // d3.event and d3.voronoi()
                        const docs = "d3.mouse(node), d3.format('d')";
                        const template = `d3.scaleOrdinal()`;
                        const matcher = /d3\\.geoCircle/;
                        const format = chart.format('d');
                        """,
                        source -> source.path("src/docs.ts")
                )
        );
    }

    @Test
    void sourceRecipesLeaveUnsupportedExtensionsUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION)),
                text("d3.histogram(); d3.event;\n", source -> source.path("docs/migration.md")),
                text("<script>d3.histogram(); d3.event;</script>\n", source -> source.path("public/index.html"))
        );
    }

    @Test
    void recommendedRecipeMigratesDependencySourceAndMarksRemainingRisk() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION)),
                json(
                        "{\"dependencies\":{\"d3\":\"^5.16.0\"}}",
                        "{\"dependencies\":{\"d3\":\"7.9.0\"}}",
                        source -> source.path("package.json")
                ),
                text(
                        "const bins = d3.histogram()(values); selection.on('click', () => d3.event.preventDefault());\n",
                        "const bins = ~~(D3 7 bin ignores null; verify missing-value filtering, thresholds and sample counts)~~>d3.bin()(values); selection.on('click', () => ~~(D3 6 removed d3.event; add event as the listener's first parameter and re-check datum/index assumptions)~~>d3.event.preventDefault());\n",
                        source -> source.path("src/chart.js")
                )
        );
    }

    @Test
    void discoversValidatesAndNamesEveryPublicRecipe() {
        Environment environment = environment();
        for (String name : new String[]{DEPENDENCY, SOURCE, AUDIT, MIGRATION}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                    () -> name + ": " + recipe.validateAll());
            assertEquals(name, recipe.getName());
        }
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.d3")
                .scanYamlResources()
                .build();
    }
}
