package com.sci.cclj.computer;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.sci.cclj.CCLuaJIT;
import com.sci.cclj.util.OS;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.lua.ILuaMachine;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

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

    public static void registerSpecialEvent(final String filter) {
        synchronized(LuaJITMachine.specialEventsLock) {
            LuaJITMachine.specialEvents.add(filter);
        }
    }

    public static boolean isSpecialEvent(final String filter) {
        synchronized(LuaJITMachine.specialEventsLock) {
            return LuaJITMachine.specialEvents.contains(filter);
        }
    }

    public final Computer computer;

    private final Object yieldResultsSignal = new Object();
    private final ListMultimap<String, Object[]> yieldResults;

    private long luaState;
    private long mainRoutine;

    private String eventFilter;
    private volatile String hardAbortMessage; // @TODO: Do we really need to differentiate hard and soft any more?
    private volatile String softAbortMessage;

    private volatile boolean yieldRequested;

    public LuaJITMachine(final Computer computer) {
        this.computer = computer;

        this.yieldResults = Multimaps.synchronizedListMultimap(LinkedListMultimap.create());

        if(!this.createLuaState()) {
            throw new RuntimeException("Failed to create native Lua state");
        }
    }

    private native boolean createLuaState();

    private native void destroyLuaState();

    private native boolean registerAPI(final ILuaAPI api);

    private native boolean loadBios(final String bios);

    private native Object[] resumeMainRoutine(final Object[] arguments) throws InterruptedException;

    private native void abort(); // @TODO: Can we just pass the string to this function to avoid needing REGISTER_KEY_MACHINE in LUA_REGISTRYINDEX

    public Object[] yield(final Object[] arguments) {
        if(arguments.length > 0 && arguments[0] instanceof String) {
            final String filter = (String) arguments[0];

            if(!LuaJITMachine.isSpecialEvent(filter)) {
                throw new RuntimeException("Attempt to call yield with an event filter that is not registered: '" + filter + "'");
            }

            TaskScheduler.INSTANCE.notifyYieldEnter(this.computer);
            while(true) {
                if(this.yieldResults.containsKey(filter) && !this.yieldRequested) {
                    this.yieldRequested = true;
                    this.computer.queueEvent(null, null);
                    final Object[] results = this.yieldResults.get(filter).remove(0);
                    TaskScheduler.INSTANCE.notifyYieldExit(this.computer);
                    return results;
                }

                synchronized(this.yieldResultsSignal) {
                    try {
                        this.yieldResultsSignal.wait();
                    } catch(InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            throw new RuntimeException("Attempt to yield but no filter was provided!");
        }
    }

    @Override
    public void finalize() {
        synchronized(this) {
            this.unload();
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

        if(this.eventFilter == null || eventName == null || eventName.equals(this.eventFilter) || eventName.equals("terminate")) {
            final Object[] arguments;
            if(eventName == null) {
                arguments = new Object[0];
            } else if(args == null) {
                arguments = new Object[]{eventName};
            } else {
                arguments = new Object[args.length + 1];
                arguments[0] = eventName;
                System.arraycopy(args, 0, arguments, 1, args.length);
            }

            if(LuaJITMachine.isSpecialEvent(eventName)) {
                this.yieldResults.put(eventName, arguments);
                synchronized(this.yieldResultsSignal) {
                    this.yieldResultsSignal.notifyAll();
                }
                return;
            }

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

    private void abort(final boolean hard, final String abortMessage) {
        this.softAbortMessage = abortMessage;
        if(hard) this.hardAbortMessage = abortMessage;

        this.abort();
    }

    @Override
    public void softAbort(final String abortMessage) {
        this.abort(false, abortMessage);
    }

    @Override
    public void hardAbort(final String abortMessage) {
        this.abort(true, abortMessage);
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
        if(this.luaState != 0) this.destroyLuaState();
    }
}