package com.sci.cclj;

import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

@Mod(CCLuaJIT.MOD_ID)
public final class CCLuaJIT {
    public static final String MOD_ID = "ccluajit";

    public CCLuaJIT() {
    }

    // NOTE TODO: Can this be done better? Probably, but yknow. gdasjlgw

    public static String getCCLJVersion() {
        final Optional<? extends ModContainer> cclj = ModList.get().getModContainerById(MOD_ID);
        if(cclj.isPresent()) {
            final ModContainer c = cclj.get();
            return c.getModInfo().getVersion().toString();
        }
        throw new RuntimeException("Failed to retrieve ModContainer for '" + MOD_ID + "'");
    }

    public static String getMinecraftVersion() {
        final Optional<? extends ModContainer> oc = ModList.get().getModContainerById("minecraft");
        if (oc.isPresent()) {
            final ModContainer c = oc.get();
            return c.getModInfo().getVersion().toString();
        }
        throw new RuntimeException("Failed to retrieve ModContainer for 'minecraft'");
    }

    public static String getComputerCraftVersion() {
        final Optional<? extends ModContainer> oc = ModList.get().getModContainerById("computercraft");
        if (oc.isPresent()) {
            final ModContainer c = oc.get();
            return c.getModInfo().getVersion().toString();
        }
        throw new RuntimeException("Failed to retrieve ModContainer for 'computercraft'");
    }
}