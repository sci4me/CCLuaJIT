package com.sci.cclj;

import com.google.common.eventbus.EventBus;
import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.InvalidVersionSpecificationException;
import cpw.mods.fml.common.versioning.VersionRange;

import java.util.Collections;

public final class CCLuaJITModContainer extends DummyModContainer {
    public CCLuaJITModContainer() {
        super(new ModMetadata());
        final ModMetadata meta = this.getMetadata();
        meta.modId = "ccluajit";
        meta.name = "CCLuaJIT";
        meta.description = "Changes ComputerCraft to use LuaJIT instead of LuaJ";
        meta.version = CCLuaJIT.MC_VERSION + "-" + CCLuaJIT.CCLJ_VERSION;
        meta.authorList = Collections.singletonList("sci4me");

        try {
            meta.requiredMods.add(new DefaultArtifactVersion("ComputerCraft", VersionRange.createFromVersionSpec("[1.70,1.75]")));
        } catch(final InvalidVersionSpecificationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean registerBus(final EventBus bus, final LoadController controller) {
        bus.register(this);
        return true;
    }
}