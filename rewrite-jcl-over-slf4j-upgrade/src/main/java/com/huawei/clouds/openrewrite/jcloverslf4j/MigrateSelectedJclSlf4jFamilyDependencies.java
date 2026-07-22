package com.huawei.clouds.openrewrite.jcloverslf4j;

/** Migrates a safely owned, explicitly selected SLF4J family together. */
public final class MigrateSelectedJclSlf4jFamilyDependencies extends AbstractSelectedJclDependencyRecipe {
    public MigrateSelectedJclSlf4jFamilyDependencies() {
        super(Mode.FAMILY_FROM_SOURCE);
    }

    @Override
    public String getDisplayName() {
        return "Migrate selected JCL and SLF4J family dependencies to 2.0.17";
    }

    @Override
    public String getDescription() {
        return "When an XLSX-listed JCL bridge is present, aligns exact selected SLF4J API, first-party provider, and one-way bridge versions plus family-owned Maven properties.";
    }
}
