package com.huawei.clouds.openrewrite.springfoxswagger2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Locale;
import java.util.Set;

/** Mark exact attributed Springfox and Swagger-2 annotation boundaries because the requested target API does not exist. */
public final class FindSpringfoxSwagger2JavaRisks extends Recipe {
    private static final String API_MESSAGE =
            "This Springfox API cannot be migrated to the workbook target because io.springfox:springfox-swagger2:1.1.2 is unpublished and has no inspectable classes. Correct the target first, then map Docket/selectors/plugins/models/security/alternate types against that exact API";
    private static final String ANNOTATION_MESSAGE =
            "Swagger 2/Springfox annotation behavior cannot be validated against the unpublished 1.1.2 target; after correcting the coordinate, regenerate the OpenAPI document and compare paths, operations, parameters, schemas, security, examples, hidden members, and response codes";
    private static final String ENDPOINT_MESSAGE =
            "This hard-coded Springfox endpoint is part of the deployed security/routing contract; the unpublished target provides no endpoint mapping to verify. Correct the target, then test authentication, CSRF/CORS, reverse-proxy prefixes, context paths, and exposure in production";
    private static final Set<String> ENDPOINTS = Set.of(
            "/v2/api-docs", "/v3/api-docs", "/swagger-resources", "/swagger-ui.html", "/swagger-ui/",
            "/webjars/**", "/swagger-resources/**"
    );

    @Override
    public String getDisplayName() {
        return "Find Springfox Swagger 2 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark type-attributed Springfox APIs, Swagger 2 annotations, and exact documentation endpoints that " +
               "cannot be mapped until the unpublished workbook target is corrected.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private boolean endpointOwnership;

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                if (UpgradeSelectedSpringfoxSwagger2Dependency.excluded(compilationUnit.getSourcePath())) {
                    return compilationUnit;
                }
                boolean old = endpointOwnership;
                String path = compilationUnit.getSourcePath().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                String source = compilationUnit.printAll();
                endpointOwnership = path.contains("springfox") || source.contains("import springfox.");
                J.CompilationUnit visited = super.visitCompilationUnit(compilationUnit, ctx);
                endpointOwnership = old;
                return visited;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (getCursor().firstEnclosing(J.Import.class) != null) return visited;
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(visited.getType());
                if (type == null || !visited.getSimpleName().equals(type.getClassName())) return visited;
                String fqn = type.getFullyQualifiedName();
                if (fqn.startsWith("springfox.")) return mark(visited, API_MESSAGE);
                return fqn.startsWith("io.swagger.annotations.") ? mark(visited, ANNOTATION_MESSAGE) : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String owner = method.getDeclaringType().getFullyQualifiedName();
                return owner.startsWith("springfox.") ? mark(visited, API_MESSAGE) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                return endpointOwnership && visited.getValue() instanceof String value && ENDPOINTS.contains(value)
                        ? mark(visited, ENDPOINT_MESSAGE) : visited;
            }
        };
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
