package com.huawei.clouds.openrewrite.jcloverslf4j;

/** Aligns remaining explicit selected companions after the core bridge reaches the target. */
public final class AlignSelectedJclSlf4jCompanions extends AbstractSelectedJclDependencyRecipe {
    public AlignSelectedJclSlf4jCompanions() {
        super(Mode.COMPANIONS_FOR_TARGET);
    }

    @Override
    public String getDisplayName() {
        return "Align selected SLF4J companions with JCL-over-SLF4J 2.0.17";
    }

    @Override
    public String getDescription() {
        return "When JCL-over-SLF4J 2.0.17 is explicit, aligns remaining exact spreadsheet-listed SLF4J API, provider, and one-way bridge versions.";
    }
}
