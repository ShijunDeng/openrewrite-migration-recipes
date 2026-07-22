package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Set;

/** Marks Angular Common TypeScript nodes whose migration requires application or runtime knowledge. */
public final class FindAngularCommonTypeScriptRisks extends Recipe {
    private static final Set<String> REMOVED_WORKER_APIS = Set.of("isPlatformWorkerApp", "isPlatformWorkerUi");
    private static final Set<String> CONTROL_FLOW_APIS = Set.of(
            "NgIf", "NgFor", "NgForOf", "NgSwitch", "NgSwitchCase", "NgSwitchDefault"
    );
    private static final Set<String> HTTP_PROVIDER_APIS = Set.of(
            "HttpClientModule", "HttpClientXsrfModule", "HTTP_INTERCEPTORS"
    );
    private static final Set<String> LOCALE_REVIEW_APIS = Set.of(
            "CurrencyPipe", "DecimalPipe", "PercentPipe", "DatePipe", "registerLocaleData",
            "formatCurrency", "formatNumber", "formatPercent", "getCurrencySymbol",
            "getLocaleCurrencyCode", "getLocaleDateFormat", "getLocaleFirstDayOfWeek",
            "getLocaleNumberFormat", "getLocaleWeekEndRange"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular Common 20 TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks removed exports, HTTP NgModule/interceptor setup, deprecated control-flow directives, " +
               "AsyncPipe/image/template behavior, and suspicious DatePipe format literals at exact AST nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> formatDateAliases = Set.of();
            private boolean hasDatePipeImport;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> previousAliases = formatDateAliases;
                boolean previousDatePipe = hasDatePipeImport;
                formatDateAliases = new HashSet<>();
                hasDatePipeImport = false;
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                formatDateAliases = previousAliases;
                hasDatePipeImport = previousDatePipe;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = MigrateDeterministicAngularCommonSource.moduleName(visited);
                if ("@angular/common".equals(module)) {
                    addAlias(visited, "formatDate", formatDateAliases);
                    hasDatePipeImport |= imported(visited, "DatePipe");
                    if (imported(visited, "DOCUMENT") &&
                        !MigrateDeterministicAngularCommonSource.importsOnly(visited, "DOCUMENT")) {
                        return SearchResult.found(visited, "DOCUMENT is exported from @angular/core in Angular 20; this mixed import needs an intentional split/merge");
                    }
                    String removed = firstImported(visited, REMOVED_WORKER_APIS);
                    if (removed != null) {
                        return SearchResult.found(visited, removed + " was removed without replacement after the WebWorker platform removal");
                    }
                    if (imported(visited, "AsyncPipe")) {
                        return SearchResult.found(visited, "AsyncPipe now reports unhandled subscription/promise errors directly to ErrorHandler; update tests and error ownership");
                    }
                    String controlFlow = firstImported(visited, CONTROL_FLOW_APIS);
                    if (controlFlow != null) {
                        return SearchResult.found(visited, controlFlow + " is deprecated in Angular 20; migrate templates to built-in control flow and choose a correct @for track expression");
                    }
                    if (imported(visited, "NgTemplateOutlet")) {
                        return SearchResult.found(visited, "NgTemplateOutlet context is strictly typed; align the context object with TemplateRef<T> instead of suppressing diagnostics");
                    }
                    if (imported(visited, "NgOptimizedImage")) {
                        return SearchResult.found(visited, "NgOptimizedImage requires application-specific dimensions, loader/CDN, priority, preconnect, SSR preload, and hydration review");
                    }
                    String localeApi = firstImported(visited, LOCALE_REVIEW_APIS);
                    if (localeApi != null) {
                        return SearchResult.found(visited, localeApi + " depends on locale/CLDR, currency, rounding, timezone, or week semantics that changed across supported majors; verify business fixtures per locale");
                    }
                    if (imported(visited, "LocationStrategy") || imported(visited, "BrowserPlatformLocation")) {
                        return SearchResult.found(visited, "Custom LocationStrategy implementations and BrowserPlatformLocation-dependent tests changed; verify getState and MockPlatformLocation behavior");
                    }
                }
                if ("@angular/common/http".equals(module)) {
                    if (imported(visited, "XhrFactory") &&
                        !MigrateDeterministicAngularCommonSource.importsOnly(visited, "XhrFactory")) {
                        return SearchResult.found(visited, "XhrFactory moved to @angular/common; this mixed HTTP import needs an intentional split");
                    }
                    String provider = firstImported(visited, HTTP_PROVIDER_APIS);
                    if (provider != null) {
                        return SearchResult.found(visited, provider + " uses legacy HTTP NgModule/DI configuration; choose provideHttpClient features and preserve interceptor/XSRF order explicitly");
                    }
                }
                if ("@angular/common/http/testing".equals(module) && imported(visited, "HttpClientTestingModule")) {
                    return SearchResult.found(visited, "HttpClientTestingModule is deprecated; order provideHttpClient before provideHttpClientTesting and verify custom HTTP features");
                }
                if ("@angular/common/testing".equals(module) && imported(visited, "MockPlatformLocation")) {
                    return SearchResult.found(visited, "MockPlatformLocation is now the default test location; remove explicit setup only after checking window.history assumptions");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                boolean formatDate = formatDateAliases.contains(visited.getSimpleName());
                boolean datePipeTransform = "transform".equals(visited.getSimpleName()) && hasDatePipeImport &&
                                            isLikelyDatePipe(visited.getSelect());
                int formatIndex = formatDate ? 1 : datePipeTransform ? 1 : -1;
                if (formatIndex >= 0 && visited.getArguments().size() > formatIndex &&
                    isSuspiciousWeekYear(visited.getArguments().get(formatIndex))) {
                    return visited.withArguments(ListUtils.map(visited.getArguments(), (index, argument) ->
                            index == formatIndex ? SearchResult.found(argument,
                                    "Angular 20 rejects a week-year Y format without week number w; choose calendar-year y or add the intended week field") : argument));
                }
                return visited;
            }

            private boolean isLikelyDatePipe(Expression select) {
                if (select instanceof J.Identifier identifier) {
                    return identifier.getSimpleName().toLowerCase().contains("datepipe");
                }
                return select instanceof J.FieldAccess &&
                       ((J.FieldAccess) select).getSimpleName().toLowerCase().contains("datepipe");
            }
        };
    }

    private static boolean isSuspiciousWeekYear(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String &&
               ((String) literal.getValue()).contains("Y") && !((String) literal.getValue()).contains("w");
    }

    private static boolean imported(JS.Import declaration, String name) {
        return MigrateDeterministicAngularCommonSource.importedAlias(declaration, name) != null;
    }

    private static void addAlias(JS.Import declaration, String name, Set<String> aliases) {
        String alias = MigrateDeterministicAngularCommonSource.importedAlias(declaration, name);
        if (alias != null) aliases.add(alias);
    }

    private static String firstImported(JS.Import declaration, Set<String> names) {
        for (String name : names) {
            if (imported(declaration, name)) return name;
        }
        return null;
    }
}
