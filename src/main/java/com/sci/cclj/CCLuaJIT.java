package com.sci.cclj;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({"com.sci.cclj"})
public final class CCLuaJIT implements IFMLLoadingPlugin {
    static File getModDirectory() {
        try {
            return new File(LuaJITMachine.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
        } catch(URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{CCLJClassTransformer.class.getCanonicalName()};
    }

    @Override
    public String getModContainerClass() {
        return CCLuaJITModContainer.class.getCanonicalName();
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(final Map<String, Object> data) {

    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}