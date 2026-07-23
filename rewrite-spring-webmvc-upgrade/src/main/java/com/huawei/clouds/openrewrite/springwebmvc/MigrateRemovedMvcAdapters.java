package com.huawei.clouds.openrewrite.springwebmvc;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

/** Replace the two removed no-op MVC adapter superclasses when no superclass behavior is used. */
public final class MigrateRemovedMvcAdapters extends Recipe {
    private static final String WEB_ADAPTER =
            "org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter";
    private static final String WEB_INTERFACE =
            "org.springframework.web.servlet.config.annotation.WebMvcConfigurer";
    private static final String HANDLER_ADAPTER =
            "org.springframework.web.servlet.handler.HandlerInterceptorAdapter";
    private static final String HANDLER_INTERFACE =
            "org.springframework.web.servlet.AsyncHandlerInterceptor";

    @Override
    public String getDisplayName() {
        return "Replace removed Spring MVC adapter superclasses";
    }

    @Override
    public String getDescription() {
        return "Replace a direct WebMvcConfigurerAdapter or HandlerInterceptorAdapter superclass with its default-method " +
               "interface only when type attribution proves the superclass and the class does not invoke superclass behavior.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration,
                                                                     ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDeclaration, ctx);
                        if (cd.getExtends() == null || containsSuperInvocation(cd)) return cd;
                        String replacement;
                        String removed;
                        if (TypeUtils.isOfClassType(cd.getExtends().getType(), WEB_ADAPTER)) {
                            replacement = WEB_INTERFACE;
                            removed = WEB_ADAPTER;
                        } else if (TypeUtils.isOfClassType(cd.getExtends().getType(), HANDLER_ADAPTER)) {
                            replacement = HANDLER_INTERFACE;
                            removed = HANDLER_ADAPTER;
                        } else {
                            return cd;
                        }

                        JavaType.FullyQualified replacementType = JavaType.ShallowClass.build(replacement);
                        TypeTree implementsType = TypeTree.build(replacement.substring(replacement.lastIndexOf('.') + 1))
                                .withType(replacementType);
                        List<TypeTree> interfaces = cd.getImplements() == null ? new ArrayList<>() :
                                new ArrayList<>(cd.getImplements());
                        if (interfaces.stream().noneMatch(type -> TypeUtils.isOfClassType(type.getType(), replacement))) {
                            interfaces.add(implementsType);
                        }
                        cd = cd.withExtends(null).withImplements(interfaces);
                        if (cd.getType() instanceof JavaType.Class classType) {
                            List<JavaType.FullyQualified> attributed = new ArrayList<>(classType.getInterfaces());
                            if (attributed.stream().noneMatch(type -> TypeUtils.isOfClassType(type, replacement))) {
                                attributed.add(replacementType);
                            }
                            cd = cd.withType(classType
                                    .withSupertype(JavaType.ShallowClass.build("java.lang.Object"))
                                    .withInterfaces(attributed));
                        }
                        maybeRemoveImport(removed);
                        maybeAddImport(replacement);
                        return autoFormat(cd, implementsType, ctx, getCursor().getParentOrThrow());
                    }
                };
    }

    private static boolean containsSuperInvocation(J.ClassDeclaration classDeclaration) {
        boolean[] found = {false};
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer depth) {
                J.MethodInvocation m = super.visitMethodInvocation(method, depth);
                if (m.getSelect() instanceof J.Identifier identifier && "super".equals(identifier.getSimpleName()) ||
                    "super".equals(m.getSimpleName())) found[0] = true;
                return m;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Integer depth) {
                J.Identifier i = super.visitIdentifier(identifier, depth);
                if ("super".equals(i.getSimpleName())) found[0] = true;
                return i;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration nested, Integer depth) {
                if (depth > 0) return nested;
                return super.visitClassDeclaration(nested, depth + 1);
            }
        }.visit(classDeclaration, 0);
        return found[0];
    }
}
