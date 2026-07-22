package com.huawei.clouds.openrewrite.jcloverslf4j;

/** Strictly upgrades only the XLSX-listed JCL-over-SLF4J dependency. */
public final class UpgradeSelectedJclOverSlf4jDependency extends AbstractSelectedJclDependencyRecipe {
    public UpgradeSelectedJclOverSlf4jDependency() {
        super(Mode.STRICT_CORE);
    }

    @Override
    public String getDisplayName() {
        return "Upgrade selected JCL-over-SLF4J dependencies to 2.0.17";
    }

    @Override
    public String getDescription() {
        return "Upgrades only exact Maven or top-level Gradle org.slf4j:jcl-over-slf4j versions explicitly listed in the spreadsheet, including safely owned Maven properties.";
    }
}
