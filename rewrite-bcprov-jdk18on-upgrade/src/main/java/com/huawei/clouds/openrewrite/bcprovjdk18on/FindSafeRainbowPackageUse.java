package com.huawei.clouds.openrewrite.bcprovjdk18on;

/** Limits the official Rainbow package move to fully attributed application sources. */
public final class FindSafeRainbowPackageUse extends FindSafeBcProvPackageUse {
    public FindSafeRainbowPackageUse() {
        super("org.bouncycastle.pqc.crypto.rainbow");
    }
}
