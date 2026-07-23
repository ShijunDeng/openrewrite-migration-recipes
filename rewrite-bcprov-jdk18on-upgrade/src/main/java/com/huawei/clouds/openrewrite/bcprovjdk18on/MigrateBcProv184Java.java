package com.huawei.clouds.openrewrite.bcprovjdk18on;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

/** Fill the ECPrivateKey expression gap that cannot be represented by a generic OpenRewrite leaf recipe. */
public final class MigrateBcProv184Java extends Recipe {
    private static final MethodMatcher EC_PRIVATE_KEY_PARAMETERS =
            new MethodMatcher("org.bouncycastle.asn1.sec.ECPrivateKey getParameters()");

    @Override
    public String getDisplayName() {
        return "Migrate the removed Bouncy Castle ECPrivateKey parameter getter";
    }

    @Override
    public String getDescription() {
        return "Replace ECPrivateKey.getParameters() with its exact former implementation, " +
               "getParametersObject().toASN1Primitive(). Generic package, type, method, and constant changes are " +
               "composed directly from official OpenRewrite recipes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit source) ||
                    UpgradeSelectedBcProvDependency.generated(source.getSourcePath())) return tree;

                Tree migrated = new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                        J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                        if (!EC_PRIVATE_KEY_PARAMETERS.matches(visited) || visited.getSelect() == null) return visited;
                        J.MethodInvocation replacement = JavaTemplate.builder(
                                                    "#{any(org.bouncycastle.asn1.sec.ECPrivateKey)}" +
                                                    ".getParametersObject().toASN1Primitive()")
                                .contextSensitive()
                                .build()
                                .apply(getCursor(), visited.getCoordinates().replace(), visited.getSelect());
                        if (!(replacement.getSelect() instanceof J.MethodInvocation inner) ||
                            visited.getMethodType() == null) return visited;
                        JavaType.FullyQualified asn1Object =
                                JavaType.ShallowClass.build("org.bouncycastle.asn1.ASN1Object");
                        JavaType.Method innerType = visited.getMethodType()
                                .withName("getParametersObject")
                                .withReturnType(asn1Object);
                        inner = inner.withMethodType(innerType).withName(inner.getName().withType(innerType));
                        JavaType.Method outerType = visited.getMethodType()
                                .withDeclaringType(asn1Object)
                                .withName("toASN1Primitive");
                        return replacement.withSelect(inner)
                                .withMethodType(outerType)
                                .withName(replacement.getName().withType(outerType));
                    }
                }.visit(tree, ctx);
                return migrated;
            }
        };
    }
}
