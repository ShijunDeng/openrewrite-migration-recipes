package com.huawei.clouds.openrewrite.bcprovjdk18on;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/**
 * A package-specific precondition that prevents {@code ChangePackage} from treating text as type evidence.
 */
abstract class FindSafeBcProvPackageUse extends Recipe {
    private final String oldPackageName;

    FindSafeBcProvPackageUse(String oldPackageName) {
        this.oldPackageName = oldPackageName;
    }

    @Override
    public String getDisplayName() {
        return "Find safe attributed Bouncy Castle package uses";
    }

    @Override
    public String getDescription() {
        return "Find compilation units that use the selected official Bouncy Castle package with complete type " +
               "attribution, excluding wildcard or unresolved imports and Bouncy Castle-owned package declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit source) ||
                    UpgradeSelectedBcProvDependency.generated(source.getSourcePath())) {
                    return tree;
                }

                J.Package sourcePackage = source.getPackageDeclaration();
                if (sourcePackage != null && inPackage(sourcePackage.getPackageName())) {
                    return tree;
                }

                boolean attributedImport = false;
                for (J.Import anImport : source.getImports()) {
                    if (!inPackage(anImport.getTypeName())) {
                        continue;
                    }
                    if ("*".equals(anImport.getClassName())) {
                        return tree;
                    }
                    JavaType.FullyQualified type = TypeUtils.asFullyQualified(anImport.getQualid().getType());
                    if (type == null || !inPackage(type.getFullyQualifiedName())) {
                        return tree;
                    }
                    attributedImport = true;
                }

                if (attributedImport ||
                    source.getTypesInUse().hasTypeInPackageOrSubpackage(oldPackageName, false)) {
                    return SearchResult.found(source);
                }
                return tree;
            }
        };
    }

    private boolean inPackage(String value) {
        return value.equals(oldPackageName) || value.startsWith(oldPackageName + ".");
    }
}
