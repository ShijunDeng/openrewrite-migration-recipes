package com.huawei.clouds.openrewrite.bcprovjdk18on;

/** Limits the official BIKE package move to fully attributed application sources. */
public final class FindSafeBikePackageUse extends FindSafeBcProvPackageUse {
    public FindSafeBikePackageUse() {
        super("org.bouncycastle.pqc.crypto.bike");
    }
}
