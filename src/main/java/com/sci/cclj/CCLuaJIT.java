package com.sci.cclj;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({"com.sci.cclj"})
public final class CCLuaJIT implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(final Map<String, Object> data) {
        for(int i = 0; i < 10000; i++) System.out.println("HELLo, COREMOD");
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}