package com.sci.cclj;

import java.io.File;

public final class LuaJITMachine {
    static {
        final String libraryExtension;

        switch(OS.check()) {
            case WINDOWS:
                throw new RuntimeException("Windows is currently not supported");
            case OSX:
                throw new RuntimeException("OSX is currently not supported");
            case LINUX:
                libraryExtension = "so";
                break;
            default:
                throw new RuntimeException(String.format("Unknown operating system: '%s'", System.getProperty("os.name")));
        }

        final File library = new File(CCLuaJIT.getModDirectory(), "cclj." + libraryExtension);
        System.load(library.getPath());
    }

    private long luaState;

    public LuaJITMachine() {
        if(!this.createLuaState()) {
            throw new RuntimeException("Failed to create native Lua state");
        }
    }

    private native boolean createLuaState();

    private native void destroyLuaState();

    @Override
    public void finalize() {
        synchronized(this) {
            if(this.luaState != 0) this.destroyLuaState();
        }
    }
}