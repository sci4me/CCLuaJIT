package com.sci.cclj;

import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.lua.ILuaMachine;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public final class LuaJITMachine implements ILuaMachine {
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

    private final IComputer computer;

    private long luaState;
    private long mainRoutine;

    public LuaJITMachine(final IComputer computer) {
        this.computer = computer;

        this.computer.queueEvent("dummy", new Object[0]);
        System.out.println("LuaJITMachine has been created!");

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

    @Override
    public void addAPI(final ILuaAPI api) {

    }

    @Override
    public void loadBios(final InputStream bios) {

    }

    @Override
    public void handleEvent(final String eventName, final Object[] arguments) {

    }

    @Override
    public void softAbort(final String abortMessage) {

    }

    @Override
    public void hardAbort(final String abortMessage) {

    }

    @Override
    public boolean saveState(final OutputStream output) {
        return false;
    }

    @Override
    public boolean restoreState(final InputStream input) {
        return false;
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public void unload() {

    }
}