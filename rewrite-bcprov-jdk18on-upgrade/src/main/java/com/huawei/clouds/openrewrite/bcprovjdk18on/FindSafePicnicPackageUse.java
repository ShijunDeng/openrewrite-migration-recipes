package com.huawei.clouds.openrewrite.bcprovjdk18on;

/** Limits the official Picnic package move to fully attributed application sources. */
public final class FindSafePicnicPackageUse extends FindSafeBcProvPackageUse {
    public FindSafePicnicPackageUse() {
        super("org.bouncycastle.pqc.crypto.picnic");
    }
}
