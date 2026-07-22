package com.huawei.clouds.openrewrite.jultoslf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks provider-internal SLF4J 1.7 binder references that have no safe general rewrite. */
public final class FindRemainingStaticBinderReferences extends Recipe {
    private static final Set<String> REMOVED_BINDERS = Set.of(
            "org.slf4j.impl.StaticLoggerBinder",
            "org.slf4j.impl.StaticMDCBinder",
            "org.slf4j.impl.StaticMarkerBinder"
    );
    private static final String MESSAGE =
            "SLF4J 2.0 removed the Static*Binder contract; migrate this provider-internal reference manually";

    @Override
    public String getDisplayName() {
        return "Find remaining SLF4J StaticBinder references";
    }

    @Override
    public String getDescription() {
        return "Mark type-attributed or reflective references to removed SLF4J 1.7 Static*Binder classes after " +
               "the safe public-accessor transformations have run.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier id = super.visitIdentifier(identifier, ctx);
                if (getCursor().firstEnclosing(J.Import.class) != null) {
                    return id;
                }
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(id.getType());
                return type != null && REMOVED_BINDERS.contains(type.getFullyQualifiedName()) ?
                        SearchResult.found(id, MESSAGE) : id;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (!(l.getValue() instanceof String value)) {
                    return l;
                }
                return REMOVED_BINDERS.stream().anyMatch(value::contains) ? SearchResult.found(l, MESSAGE) : l;
            }
        };
    }
}
