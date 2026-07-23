package com.huawei.clouds.openrewrite.bcprovjdk18on;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeFieldName;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

/** Apply only source migrations backed by exact upstream moves or equivalent implementations. */
public final class MigrateBcProv184Java extends Recipe {
    private static final MethodMatcher EC_PRIVATE_KEY_PARAMETERS =
            new MethodMatcher("org.bouncycastle.asn1.sec.ECPrivateKey getParameters()");
    private static final List<PackageMove> PACKAGE_MOVES = List.of(
            new PackageMove("org.bouncycastle.pqc.crypto.bike", "org.bouncycastle.pqc.legacy.bike"),
            new PackageMove("org.bouncycastle.pqc.crypto.picnic", "org.bouncycastle.pqc.legacy.picnic"),
            new PackageMove("org.bouncycastle.pqc.crypto.rainbow", "org.bouncycastle.pqc.legacy.rainbow")
    );
    private static final List<Recipe> MIGRATIONS = List.of(
            new ChangeType(
                    "org.bouncycastle.pqc.jcajce.provider.util.WrapUtil",
                    "org.bouncycastle.jcajce.provider.asymmetric.util.WrapUtil",
                    true),
            new ChangeMethodName(
                    "org.bouncycastle.util.Pack longToBigEndian(long, byte[], int, int)",
                    "longToBigEndian_Low",
                    false,
                    true)
    );

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Bouncy Castle 1.84 Java API moves";
    }

    @Override
    public String getDescription() {
        return "Move BIKE, Picnic, and Rainbow lightweight API packages to their 1.84 legacy locations, " +
               "move WrapUtil to its promoted provider package, and replace the removed Pack overload with its " +
               "byte-for-byte equivalent low-order method and ECPrivateKey parameter getter. All changes require " +
               "attributed Bouncy Castle types; " +
               "SPHINCS+ remains review-only because its 1.75 and 1.78 parameter names do not have one safe mapping.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit source) ||
                    UpgradeSelectedBcProvDependency.generated(source.getSourcePath())) return tree;

                Tree migrated = tree;
                for (PackageMove move : PACKAGE_MOVES) {
                    migrated = changeAttributedPackage(migrated, move, ctx);
                }
                for (Recipe migration : MIGRATIONS) {
                    migrated = migration.getVisitor().visit(migrated, ctx);
                }
                migrated = new ChangeFieldName<ExecutionContext>(
                        "org.bouncycastle.crypto.hpke.HPKE",
                        "kem_P384_SHA348",
                        "kem_P384_SHA384").visit(migrated, ctx);
                migrated = new JavaIsoVisitor<ExecutionContext>() {
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
                }.visit(migrated, ctx);
                return migrated;
            }
        };
    }

    private static Tree changeAttributedPackage(Tree tree, PackageMove move, ExecutionContext ctx) {
        if (!(tree instanceof J.CompilationUnit source)) return tree;
        J.Package sourcePackage = source.getPackageDeclaration();
        if (sourcePackage != null && inPackage(sourcePackage.getPackageName(), move.oldPackage())) return tree;

        boolean attributedImport = false;
        for (J.Import anImport : source.getImports()) {
            if (!inPackage(anImport.getTypeName(), move.oldPackage())) continue;
            if ("*".equals(anImport.getClassName())) return tree;
            JavaType.FullyQualified type = TypeUtils.asFullyQualified(anImport.getQualid().getType());
            if (type == null || !inPackage(type.getFullyQualifiedName(), move.oldPackage())) return tree;
            attributedImport = true;
        }
        if (!attributedImport && !source.getTypesInUse().hasTypeInPackageOrSubpackage(move.oldPackage(), false)) {
            return tree;
        }
        Tree changed = new ChangePackage(move.oldPackage(), move.newPackage(), true).getVisitor().visit(tree, ctx);
        return changed == null ? tree : changed;
    }

    private static boolean inPackage(String value, String packageName) {
        return value.equals(packageName) || value.startsWith(packageName + ".");
    }

    private record PackageMove(String oldPackage, String newPackage) { }
}
