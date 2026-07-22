package com.huawei.clouds.openrewrite.singlespaangular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.util.Set;

/** Mark dependency graph choices that the package declaration alone cannot settle. */
public final class FindSingleSpaAngularManifestRisks extends Recipe {
    private static final Set<String> ANGULAR = Set.of(
            "@angular/core", "@angular/common", "@angular/compiler", "@angular/compiler-cli", "@angular/cli",
            "@angular/router", "@angular/platform-browser", "@angular/platform-browser-dynamic",
            "@angular-devkit/build-angular");
    private static final Set<String> COMPANIONS = Set.of(
            "single-spa", "@angular-builders/custom-webpack", "style-loader", "json5", "tslib",
            "zone.js", "rxjs");

    @Override
    public String getDisplayName() {
        return "Find single-spa-angular 9.2.0 manifest risks";
    }

    @Override
    public String getDescription() {
        return "Marks non-workbook owners, overrides, Angular major alignment, builder/runtime peers, Zone, RxJS, and shared dependency decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean active;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                boolean previous = active;
                active = SingleSpaAngularSupport.packageJson(document.getSourcePath()) &&
                         SingleSpaAngularSupport.containsPackageReference(document.printAll());
                Json.Document visited = active ? super.visitDocument(document, ctx) : document;
                active = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!active) return visited;
                String key = SingleSpaAngularSupport.key(visited);
                String value = SingleSpaAngularSupport.string(visited);
                String section = SingleSpaAngularSupport.directSection(getCursor());
                if (SingleSpaAngularSupport.PACKAGE.equals(key)) {
                    if (SingleSpaAngularSupport.overrideOwner(getCursor()) && section.isEmpty()) {
                        return SingleSpaAngularSupport.mark(visited,
                                "This override/resolution independently owns single-spa-angular; align the actual package-manager graph and regenerate exactly one selected lockfile");
                    }
                    if (SingleSpaAngularSupport.SECTIONS.contains(section) &&
                        !SingleSpaAngularSupport.target(value)) {
                        return SingleSpaAngularSupport.mark(visited,
                                "This direct single-spa-angular declaration is not the exact workbook target after strict AUTO; resolve ranges, protocols, forks, catalogs, and unlisted versions deliberately");
                    }
                }
                if (!SingleSpaAngularSupport.SECTIONS.contains(section)) return visited;
                if (ANGULAR.contains(key)) {
                    return SingleSpaAngularSupport.mark(visited,
                            "single-spa-angular 9.2.0 publishes an @angular/core >=16 peer while the 9.2.0 repository builds on Angular 18; run every Angular CLI major migration and validate this explicitly owned Angular package as one aligned framework set");
                }
                if ("zone.js".equals(key)) {
                    return SingleSpaAngularSupport.mark(visited,
                            "Exactly one compatible zone.js instance should execute on the page (prefer root-config ownership); verify host/remotes, test setup, and noop-zone choices");
                }
                if ("@angular-builders/custom-webpack".equals(key)) {
                    return SingleSpaAngularSupport.mark(visited,
                            "Align the custom-webpack builder with the final Angular major and verify the browser builder, SystemJS output, externals, serve target, and production configuration");
                }
                if ("single-spa".equals(key)) {
                    return SingleSpaAngularSupport.mark(visited,
                            "The target accepts single-spa >=4, but validate one runtime owner, lifecycle contracts, routing events, parcels, and host/remote deployment together");
                }
                if (Set.of("style-loader", "json5", "tslib").contains(key)) {
                    return SingleSpaAngularSupport.mark(visited,
                            "Validate this explicitly owned target peer/build companion against single-spa-angular 9.2.0 and the final Angular/webpack installation graph");
                }
                if ("rxjs".equals(key)) {
                    return SingleSpaAngularSupport.mark(visited,
                            "Validate RxJS against the selected Angular major and all shared host/remotes; single-spa-angular cannot safely infer or rewrite this version");
                }
                return visited;
            }
        };
    }
}
