package com.sci.cclj;

import cpw.mods.fml.common.CertificateHelper;
import cpw.mods.fml.relauncher.IFMLCallHook;

import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.Map;

public final class CCLuaJITCallHook implements IFMLCallHook {
    public static final String CCLJ_FINGERPRINT = "9B:3C:8C:CD:BE:2B:6F:70:93:27:95:47:CC:67:28:D8:4F:53:38:E0".toLowerCase().replace(":", "");

    @Override
    public void injectData(final Map<String, Object> data) {
    }

    @Override
    public Void call() {
        this.checkCertificates();
        return null;
    }

    private void checkCertificates() {
        final CodeSource codeSource = CCLuaJITCallHook.class.getProtectionDomain().getCodeSource();
        if(codeSource.getLocation().getPath().endsWith(".jar")) {
            final Certificate[] certs = codeSource.getCertificates();

            if(certs == null || certs.length == 0) {
                CCLuaJIT.LOGGER.warn("No certificates were found for CCLuaJIT");
                return;
            }

            for(final Certificate cert : certs) {
                final String fingerprint = CertificateHelper.getFingerprint(cert);
                if(fingerprint.equals(CCLJ_FINGERPRINT)) {
                    CCLuaJIT.LOGGER.info("Found valid certificate fingerprint for CCLuaJIT: " + fingerprint);
                } else {
                    CCLuaJIT.LOGGER.warn("Found invalid certificate fingerprint for CCLuaJIT: " + fingerprint);
                }
            }
        }
    }
}