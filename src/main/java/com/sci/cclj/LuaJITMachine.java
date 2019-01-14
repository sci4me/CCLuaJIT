package com.sci.cclj;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.lua.ILuaMachine;

import java.io.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Object specialEventsLock = new Object();
    private static final Set<String> specialEvents = new HashSet<>();

    static void registerSpecialEvent(final String filter) {
        synchronized(LuaJITMachine.specialEventsLock) {
            LuaJITMachine.specialEvents.add(filter);
        }
    }

    public static boolean isSpecialEvent(final String filter) {
        synchronized(LuaJITMachine.specialEventsLock) {
            return LuaJITMachine.specialEvents.contains(filter);
        }
    }

    private final IComputer computer;

    private final Map<String, Object[]> yieldResults; // @TODO: Change this to something like Map<String, List<Object[]>>

    private long luaState;
    private long mainRoutine;

    private String eventFilter;
    private volatile String hardAbortMessage;
    private volatile String softAbortMessage;

    public LuaJITMachine(final IComputer computer) {
        this.computer = computer;

        this.yieldResults = new ConcurrentHashMap<>();

        if(!this.createLuaState()) {
            throw new RuntimeException("Failed to create native Lua state");
        }
    }

    private native boolean createLuaState();

    private native void destroyLuaState();

    private native boolean registerAPI(final ILuaAPI api);

    private native boolean loadBios(final String bios);

    private native Object[] resumeMainRoutine(final Object[] arguments) throws InterruptedException;

    public Object[] yield(final Object[] arguments) {
        if(arguments.length > 0 && arguments[0] instanceof String) {
            final String filter = (String) arguments[0];

            if(!LuaJITMachine.isSpecialEvent(filter)) {
                throw new RuntimeException("Attempt to call yield with an event filter that is not registered: '" + filter + "'");
            }

            if(this.yieldResults.containsKey(filter)) {
                final Object[] results = this.yieldResults.get(filter);
                this.yieldResults.remove(filter);
                return results;
            }
        } else {
            throw new RuntimeException("Attempt to yield but no filter was provided!");
        }

        return new Object[0];
    }

    @Override
    public void finalize() {
        synchronized(this) {
            if(this.luaState != 0) this.destroyLuaState();
        }
    }

    @Override
    public void addAPI(final ILuaAPI api) {
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
    public void handleEvent(final String eventName, final Object[] args) {
        if(this.mainRoutine == 0) return;

        final Object[] arguments;
        if(args == null) {
            arguments = new Object[0];
        } else {
            arguments = new Object[args.length + 1];
            arguments[0] = eventName;
            System.arraycopy(args, 0, arguments, 1, args.length);
        }

        if(LuaJITMachine.isSpecialEvent(eventName)) {
            this.yieldResults.put(eventName, arguments);
            return;
        }

        if(this.eventFilter == null || eventName == null || eventName.equals(this.eventFilter) || eventName.equals("terminate")) {
            try {
                final Object[] results = this.resumeMainRoutine(arguments);

                if(this.hardAbortMessage != null) {
                    this.destroyLuaState();
                } else if(results.length > 0 && results[0] instanceof Boolean && !(Boolean) results[0]) {
                    this.destroyLuaState();
                } else {
                    if(results.length >= 2 && results[1] instanceof String) {
                        this.eventFilter = (String) results[1];
                    } else {
                        this.eventFilter = null;
                    }
                }
            } catch(final InterruptedException e) {
                this.destroyLuaState();
            } finally {
                this.softAbortMessage = null;
                this.hardAbortMessage = null;
            }
        }
    }

    @Override
    public void softAbort(final String abortMessage) {
        this.softAbortMessage = abortMessage;
    }

    @Override
    public void hardAbort(final String abortMessage) {
        this.softAbortMessage = abortMessage;
        this.hardAbortMessage = abortMessage;
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
        return this.mainRoutine == 0;
    }

    @Override
    public void unload() {
        if(this.mainRoutine != 0) this.destroyLuaState();
    }
}