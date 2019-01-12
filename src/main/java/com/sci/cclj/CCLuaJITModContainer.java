package com.sci.cclj;

import com.google.common.eventbus.EventBus;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.ModMetadata;

import java.util.Collections;

public final class CCLuaJITModContainer extends DummyModContainer {
    public CCLuaJITModContainer() {
        super(new ModMetadata());
        final ModMetadata meta = this.getMetadata();
        meta.modId = "ccluajit";
        meta.name = "CCLuaJIT";
        meta.description = "Changes ComputerCraft to use LuaJIT instead of LuaJ";
        meta.version = "1.7.10-0.1.0";
        meta.authorList = Collections.singletonList("sci4me");
    }

    @Override
    public boolean registerBus(final EventBus bus, final LoadController controller) {
        bus.register(this);
        return true;
    }
}