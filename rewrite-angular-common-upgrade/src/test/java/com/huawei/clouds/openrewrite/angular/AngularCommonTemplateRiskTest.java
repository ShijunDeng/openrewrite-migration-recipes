package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;

class AngularCommonTemplateRiskTest implements RewriteTest {
    @Test
    void marksNgSwitchCaseFromPinnedPoeOverlayFixture() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonTemplateRisks()),
                text(
                        "<div [ngSwitch]=\"status\"><span *ngSwitchCase=\"'ready'\">Ready</span></div>\n",
                        source -> source.path("Kyusung4698/PoE-Overlay/src/app/app.component.html")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("~~>\\[ngSwitch\\]") || printed.contains("~~>[ngSwitch]"));
                                    assertTrue(printed.contains("~~>\\*ngSwitchCase") || printed.contains("~~>*ngSwitchCase"));
                                })
                )
        );
    }

    @Test
    void marksEveryDeprecatedStructuralDirectiveAtItsSnippet() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonTemplateRisks()),
                text(
                        "<p *ngIf=\"visible\">x</p><li *ngFor=\"let x of xs\">{{x}}</li><p *ngSwitchDefault>none</p>",
                        source -> source.path("src/app.html").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("~~>*ngIf"));
                                    assertTrue(printed.contains("~~>*ngFor"));
                                    assertTrue(printed.contains("~~>*ngSwitchDefault"));
                                })
                )
        );
    }

    @Test
    void marksTemplateOutletAndAsyncPipe() {
        assertTemplateMarkers(
                "<ng-container *ngTemplateOutlet=\"row; context: ctx\"></ng-container>{{ value | async }}",
                "src/outlet.html",
                "NgTemplateOutlet context is strictly typed",
                "AsyncPipe now reports"
        );
    }

    @Test
    void marksSuspiciousDatePipeFormatButNotCalendarOrWeekFormats() {
        assertTemplateMarkers(
                "{{ createdAt | date:'YYYY-MM-dd' }}",
                "src/date.html",
                "week-year Y format"
        );
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonTemplateRisks()),
                text("{{ createdAt | date:'yyyy-MM-dd' }} {{ createdAt | date:'YYYY-ww' }}",
                        source -> source.path("src/safe-date.html"))
        );
    }

    @Test
    void marksOptimizedImageAttributionAndNgReflectDependency() {
        assertTemplateMarkers(
                "<img [ngSrc]=\"photo\" width=\"100\" height=\"100\"><div ng-reflect-model=\"ready\"></div>",
                "src/image.html",
                "NgOptimizedImage needs verified dimensions",
                "no longer emits ng-reflect"
        );
    }

    @Test
    void ignoresSimilarTextOutsideHtmlFiles() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonTemplateRisks()),
                text("const example = '*ngIf | async ng-reflect-value';\n",
                        source -> source.path("src/example.ts"))
        );
    }

    @Test
    void leavesModernBuiltInControlFlowTemplateAlone() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonTemplateRisks()),
                text(
                        "@if (visible) { <p>{{ value }}</p> } @for (item of items; track item.id) { <li>{{item.name}}</li> }",
                        source -> source.path("src/modern.html")
                )
        );
    }

    @Test
    void emitsNonOverlappingMarkersAndIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonTemplateRisks()).cycles(2),
                text(
                        "<img *ngIf=\"ready\" [ngSrc]=\"url\">",
                        source -> source.path("src/card.html").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("Structural control-flow directives"));
                                    assertTrue(printed.contains("NgOptimizedImage"));
                                    assertFalse(printed.contains("~~(Structural control-flow directives") &&
                                                printed.indexOf("Structural control-flow directives") !=
                                                printed.lastIndexOf("Structural control-flow directives"));
                                })
                )
        );
    }

    private void assertTemplateMarkers(String before, String path, String... messages) {
        rewriteRun(
                spec -> spec.recipe(new FindAngularCommonTemplateRisks()),
                text(before, source -> source.path(path).after(actual -> actual)
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            for (String message : messages) {
                                assertTrue(printed.contains(message), printed);
                            }
                        }))
        );
    }
}
