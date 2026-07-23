package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

/** Migrate Tomcat 9 Servlet/EL source namespaces without inventing Servlet 6 types that do not exist. */
public final class MigrateTomcat9JakartaNamespaces extends Recipe {
    private static final Set<String> REMOVED_TYPES = Set.of(
            "javax.servlet.SingleThreadModel",
            "javax.servlet.http.HttpSessionContext",
            "javax.servlet.http.HttpUtils");

    @Override
    public String getDisplayName() {
        return "Migrate Tomcat 9 Java EE namespaces to Jakarta";
    }

    @Override
    public String getDescription() {
        return "Migrate type-attributed javax.servlet and javax.el Java source to jakarta.servlet and jakarta.el; " +
               "compilation units using Servlet 6 removed types remain unchanged for precise risk markers.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit compilationUnit) ||
                    UpgradeSelectedTomcatCatalinaDependency.generated(compilationUnit.getSourcePath()) ||
                    containsRemovedType(compilationUnit)) return tree;
                J.CompilationUnit servlet = (J.CompilationUnit) new ChangePackage(
                        "javax.servlet", "jakarta.servlet", true).getVisitor().visitNonNull(compilationUnit, ctx);
                return new ChangePackage("javax.el", "jakarta.el", true).getVisitor().visitNonNull(servlet, ctx);
            }
        };
    }

    private static boolean containsRemovedType(J.CompilationUnit compilationUnit) {
        if (compilationUnit.getImports().stream().map(anImport -> anImport.getQualid().printTrimmed())
                .anyMatch(MigrateTomcat9JakartaNamespaces::isRemovedTypeOrMember)) return true;
        for (JavaType type : compilationUnit.getTypesInUse().getTypesInUse()) {
            JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
            if (fullyQualified != null && REMOVED_TYPES.contains(fullyQualified.getFullyQualifiedName())) return true;
        }
        String source = compilationUnit.printAll();
        return REMOVED_TYPES.stream().anyMatch(source::contains);
    }

    private static boolean isRemovedTypeOrMember(String imported) {
        return REMOVED_TYPES.stream().anyMatch(type -> imported.equals(type) || imported.startsWith(type + "."));
    }
}
