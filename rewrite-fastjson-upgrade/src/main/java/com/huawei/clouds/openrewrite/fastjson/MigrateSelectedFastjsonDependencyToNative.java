package com.huawei.clouds.openrewrite.fastjson;

/** Strict dependency migration for projects that explicitly select the native Fastjson2 API. */
public final class MigrateSelectedFastjsonDependencyToNative extends UpgradeSelectedFastjsonDependency {
    public MigrateSelectedFastjsonDependencyToNative() {
        super(true);
    }
}
