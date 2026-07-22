package com.huawei.clouds.openrewrite.ojdbc8;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

/** Replace the exact deprecated Oracle driver implementation class string. */
public final class ReplaceLegacyOracleDriverString extends Recipe {
    static final String OLD_DRIVER = "oracle.jdbc.driver.OracleDriver";
    static final String NEW_DRIVER = "oracle.jdbc.OracleDriver";

    @Override
    public String getDisplayName() {
        return "Replace deprecated Oracle JDBC driver class strings";
    }

    @Override
    public String getDescription() {
        return "Replace exact Java string literals naming oracle.jdbc.driver.OracleDriver with Oracle's supported " +
               "oracle.jdbc.OracleDriver facade without changing arbitrary text fragments.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedOjdbc8Dependency.isProjectPath(compilationUnit.getSourcePath())
                        ? super.visitCompilationUnit(compilationUnit, ctx) : compilationUnit;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!OLD_DRIVER.equals(visited.getValue())) return visited;
                String source = visited.getValueSource();
                return visited.withValue(NEW_DRIVER)
                        .withValueSource(source == null ? null : source.replace(OLD_DRIVER, NEW_DRIVER));
            }
        };
    }
}
