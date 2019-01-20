package com.sci.cclj;

import com.sci.cclj.asm.CCLJClassTransformer;
import com.sci.cclj.computer.LuaJITMachine;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Map;

@IFMLLoadingPlugin.MCVersion(CCLuaJIT.MC_VERSION)
@IFMLLoadingPlugin.TransformerExclusions({"com.sci.cclj"})
public final class CCLuaJIT implements IFMLLoadingPlugin {
    public static final String CCLJ_VERSION = "$CCLJ_VERSION";
    public static final String MC_VERSION = "$MC_VERSION";

    public static File getModDirectory() {
        try {
            return new File(LuaJITMachine.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
        } catch(URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getInstalledComputerCraftVersion() {
        final ModContainer mc = FMLCommonHandler.instance().findContainerFor("ComputerCraft");
        return mc.getVersion();
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