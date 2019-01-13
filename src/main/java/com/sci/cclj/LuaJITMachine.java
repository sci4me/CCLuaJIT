package com.sci.cclj;

import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.lua.ILuaMachine;

import java.io.*;

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

        if(!this.createLuaState()) {
            throw new RuntimeException("Failed to create native Lua state");
        }
    }

    private native boolean createLuaState();

    private native void destroyLuaState();

    private native boolean registerAPI(final ILuaAPI api);

    private native boolean loadBios(final String bios);

    @Override
    public void finalize() {
        synchronized(this) {
            if(this.luaState != 0) this.destroyLuaState();
        }
    }

    @Override
    public void addAPI(final ILuaAPI api) {
        System.out.println("addAPI " + api);

        if(!this.registerAPI(api)) {
            throw new RuntimeException("Failed to register API " + api);
        }
    }

    @Override
    public void loadBios(final InputStream bios) {
        if(this.mainRoutine != 0) return;

        try {
            final StringBuilder sb = new StringBuilder();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(bios));
            String line;
            while((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }

            if(!this.loadBios(sb.toString())) {
                throw new RuntimeException("Failed to create main routine");
            }
        } catch(final IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleEvent(final String eventName, final Object[] arguments) {
        System.out.print("handleEvent " + eventName);
        if(arguments != null) System.out.print(" " + arguments.length);
        System.out.println();
    }

    @Override
    public void softAbort(final String abortMessage) {
        System.out.println("softAbort");
    }

    @Override
    public void hardAbort(final String abortMessage) {
        System.out.println("hardAbort");
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
        System.out.println("unload");
    }
}