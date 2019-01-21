package com.sci.cclj;

import com.google.common.eventbus.EventBus;
import cpw.mods.fml.common.CertificateHelper;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.InvalidVersionSpecificationException;
import cpw.mods.fml.common.versioning.VersionRange;

import java.security.cert.Certificate;
import java.util.Collections;

public final class CCLuaJITModContainer extends DummyModContainer {
    public CCLuaJITModContainer() {
        super(new ModMetadata());
        final ModMetadata meta = this.getMetadata();
        meta.modId = "ccluajit";
        meta.name = "CCLuaJIT";
        meta.description = "Changes ComputerCraft to use LuaJIT instead of LuaJ. Special thanks to Mike Pall for for LuaJIT, dan200 for ComputerCraft, and kkaylium for the logo!";
        meta.logoFile = "/logo.png";
        meta.version = CCLuaJIT.CCLJ_VERSION;
        meta.authorList = Collections.singletonList("sci4me");

        try {
            meta.requiredMods.add(new DefaultArtifactVersion("ComputerCraft", VersionRange.createFromVersionSpec("[1.70,1.75]")));
        } catch(final InvalidVersionSpecificationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Certificate getSigningCertificate() {
        final Certificate[] certs = CCLuaJIT.class.getProtectionDomain().getCodeSource().getCertificates();

        if(certs == null || certs.length == 0) {
            return null;
        }

        for(final Certificate cert : certs) {
            final String fingerprint = CertificateHelper.getFingerprint(cert);
            if(fingerprint.equals(CCLuaJITCallHook.CCLJ_FINGERPRINT)) {
                return cert;
            }
        }

        return null;
    }

    @Override
    public boolean registerBus(final EventBus bus, final LoadController controller) {
        bus.register(this);
        return true;
    }
}