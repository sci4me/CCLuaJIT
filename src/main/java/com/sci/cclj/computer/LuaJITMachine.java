package com.sci.cclj.computer;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.sci.cclj.CCLuaJIT;
import com.sci.cclj.asm.transformers.PullEventScanner;
import com.sci.cclj.util.OS;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ITask;
import dan200.computercraft.core.lua.ILuaMachine;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;

public final class LuaJITMachine implements ILuaMachine, ILuaContext {
    private static final MethodHandle GET_UNIQUE_TASK_ID_MH;
    private static final MethodHandle QUEUE_TASK_MH;

    static {
        try {
            LuaJITMachine.loadNatives();
        } catch(final IOException e) {
            e.printStackTrace();
        }

        MethodHandle getUniqueTaskID_mh = null;
        MethodHandle queueTask_mh = null;
        try {
            final Class<?> MAIN_THREAD_CLASS = Class.forName("dan200.computercraft.core.computer.MainThread");
            final Class<?> iTask_class = Class.forName("dan200.computercraft.core.computer.ITask");

            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            getUniqueTaskID_mh = lookup.findStatic(MAIN_THREAD_CLASS, "getUniqueTaskID", MethodType.methodType(long.class));
            queueTask_mh = lookup.findStatic(MAIN_THREAD_CLASS, "queueTask", MethodType.methodType(boolean.class, iTask_class));
        } catch(final ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
        } finally {
            GET_UNIQUE_TASK_ID_MH = getUniqueTaskID_mh;
            QUEUE_TASK_MH = queueTask_mh;
        }
    }

    private static File extract(final File location, final String name) throws IOException {
        final File result = new File(location, name);
        result.deleteOnExit();

        final GZIPInputStream in = new GZIPInputStream(LuaJITMachine.class.getResourceAsStream("/natives/" + name + ".gz"));
        final FileOutputStream out = new FileOutputStream(result);

        IOUtils.copy(in, out);

        in.close();
        out.flush();
        out.close();

        return result;
    }

    private static void loadNatives() throws IOException {
        final String libCCLJ;
        final String libLuaJIT;
        switch(OS.check()) {
            case WINDOWS:
                libCCLJ = "cclj.dll";
                libLuaJIT = "lua51.dll";
                break;
            case OSX:
                libCCLJ = "cclj.dylib";
                libLuaJIT = "libluajit-5.1.2.dylib";
                break;
            case LINUX:
                libCCLJ = "cclj.so";
                libLuaJIT = "libluajit-5.1.so.2";
                break;
            default:
                throw new RuntimeException(String.format("Unknown operating system: '%s'", System.getProperty("os.name")));
        }

        final File tempDir = Files.createTempDirectory("cclj-natives").toFile();
        tempDir.deleteOnExit();

        final File luajitFile = LuaJITMachine.extract(tempDir, libLuaJIT);
        final File ccljFile = LuaJITMachine.extract(tempDir, libCCLJ);

        System.load(luajitFile.getAbsolutePath());
        System.load(ccljFile.getAbsolutePath());
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

        if(!this.createLuaState(CCLuaJIT.getInstalledComputerCraftVersion(), CCLuaJIT.MC_VERSION, ThreadLocalRandom.current().nextLong())) {
            throw new RuntimeException("Failed to create native Lua state");
        }
    }

    private native boolean createLuaState(final String ccVersion, final String mcVersion, final long randomSeed);

    private native void destroyLuaState();

    private native boolean registerAPI(final ILuaAPI api);

    private native boolean loadBios(final String bios);

    private native Object[] resumeMainRoutine(final Object[] arguments) throws InterruptedException;

    private native void abort(); // @TODO: Can we just pass the string to this function to avoid needing REGISTER_KEY_MACHINE in LUA_REGISTRYINDEX

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

            if(PullEventScanner.isSpecialEvent(eventName)) {
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

    @Override
    public Object[] pullEvent(final String filter) throws LuaException {
        final Object[] results = this.pullEventRaw(filter);
        if(results.length > 0 && results[0].equals("terminate")) {
            throw new LuaException("Terminated", 0);
        }
        return results;
    }

    @Override
    public Object[] pullEventRaw(final String filter) {
        return this.yield(new Object[]{filter});
    }

    @Override
    public Object[] yield(final Object[] arguments) {
        if(arguments.length > 0 && arguments[0] instanceof String) {
            final String filter = (String) arguments[0];

            if(!PullEventScanner.isSpecialEvent(filter)) {
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
                    } catch(final InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            throw new RuntimeException("Attempt to yield but no filter was provided!");
        }
    }

    @Override
    public Object[] executeMainThreadTask(final ILuaTask task) throws LuaException {
        final long id = this.issueMainThreadTask(task);

        while(true) {
            final Object[] response = this.pullEvent("task_complete");
            if(response.length >= 3 && response[1] instanceof Number && response[2] instanceof Boolean && ((Number) response[1]).longValue() == id) {
                if((Boolean) response[2]) {
                    final Object[] returnValues = new Object[response.length - 3];
                    System.arraycopy(response, 3, returnValues, 0, returnValues.length);
                    return returnValues;
                } else {
                    if(response.length >= 4 && response[3] instanceof String) {
                        throw new LuaException((String) response[3]);
                    } else {
                        throw new LuaException();
                    }
                }
            }
        }
    }

    @Override
    public long issueMainThreadTask(final ILuaTask luaTask) {
        try {
            final long id = (long) GET_UNIQUE_TASK_ID_MH.invoke();
            final ITask task = new ITask() {
                @Override
                public Computer getOwner() {
                    return LuaJITMachine.this.computer;
                }

                @Override
                public void execute() {
                    try {
                        final Object[] results = luaTask.execute();
                        if(results == null) {
                            this.respond(true);
                        } else {
                            this.respond(true, results);
                        }
                    } catch(final LuaException e) {
                        this.respond(false, e.getMessage());
                    } catch(final Throwable t) {
                        this.respond(false, String.format("Java Exception Thrown: %s", t.toString()));
                    }
                }

                private void respond(final boolean result, final Object... args) {
                    final Object[] arguments = new Object[args.length + 2];
                    arguments[0] = id;
                    arguments[1] = result;
                    System.arraycopy(args, 0, arguments, 2, args.length);
                    LuaJITMachine.this.computer.queueEvent("task_complete", arguments);
                }
            };
            if((Boolean) QUEUE_TASK_MH.invoke(task)) {
                return id;
            } else {
                throw new LuaException("Task limit exceeded");
            }
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
    }
}