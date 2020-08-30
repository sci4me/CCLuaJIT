package com.sci.cclj.computer;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.sci.cclj.CCLuaJIT;
import com.sci.cclj.util.OS;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.MainThread;
import dan200.computercraft.core.computer.TimeoutState;
import dan200.computercraft.core.lua.ILuaMachine;
import dan200.computercraft.core.lua.MachineResult;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;

@SuppressWarnings("unused") // Instantiated by bytecode injected into CC
public final class LuaJITMachine implements ILuaMachine, ILuaContext {
    @SuppressWarnings("unused") // Used in native code.
    public static String decodeString(final byte[] bytes) {
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    public static boolean isSpecialEvent(final String evt) {
        if(evt == null) return false;
        return evt.equals("turtle_response") || evt.equals("task_completed");
    }

    static {
        try {
            LuaJITMachine.loadNatives();
        } catch (final IOException e) {
            e.printStackTrace();
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
        switch (OS.check()) {
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
                libLuaJIT = "libluajit-5.1.so";
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
    public final TimeoutState timeout;

    private String eventFilter;

    private boolean yielded;
    private final ListMultimap<String, Object[]> yieldResults = Multimaps.synchronizedListMultimap(LinkedListMultimap.create());
    private final Object yieldResultsSignal = new Object();
    private volatile boolean yieldRequested;

    private volatile boolean aborted;

    private volatile long luaState;
    private volatile long mainRoutine;

    public LuaJITMachine(final Computer computer, final TimeoutState timeout) {
        this.computer = computer;
        this.timeout = timeout;

        if (!this.initMachine(
                CCLuaJIT.getCCLJVersion(),
                CCLuaJIT.getComputerCraftVersion(),
                CCLuaJIT.getMinecraftVersion(),
                computer.getAPIEnvironment().getComputerEnvironment().getHostString(),
                ComputerCraft.default_computer_settings,
                ThreadLocalRandom.current().nextLong())) {
            throw new RuntimeException("Failed to initialize native state");
        }
    }

    private native boolean initMachine(final String ccljVersion, final String cctVersion, final String mcVersion, final String host, final String defaultSettings, final long randomSeed);

    private native void deinitMachine();

    private native boolean registerAPI(final ILuaAPI api);

    private native boolean loadBios(final String bios);

    private native Object[] handleEvent(final Object[] args);

    private native void abortMachine();

    public void abort() {
        if(this.aborted) return;
        this.aborted = true;
        this.abortMachine();
    }

    @Override
    public void addAPI(@Nonnull final ILuaAPI api) {
        if (!this.registerAPI(api)) {
            throw new RuntimeException("Failed to register API " + api);
        }
    }

    @Override
    public MachineResult loadBios(@Nonnull final InputStream in) {
        try {
            final String pre = IOUtils.toString(LuaJITMachine.class.getResource("/prebios.lua"), StandardCharsets.UTF_8);
            final String bios = IOUtils.toString(in, StandardCharsets.UTF_8);

            if (!this.loadBios(pre + "\n" + bios)) {
                return MachineResult.GENERIC_ERROR;
            }

            return MachineResult.OK;
        } catch (final IOException e) {
            return MachineResult.error(e);
        }
    }

    @Override
    public MachineResult handleEvent(@Nullable final String evt, @Nullable final Object[] args) {
        if (this.mainRoutine == 0) return MachineResult.GENERIC_ERROR;

        if (this.eventFilter != null && evt != null && !evt.equals(this.eventFilter) && !evt.equals("terminate")) {
            return MachineResult.OK;
        }

        final Object[] arguments;
        if (evt == null) {
            arguments = new Object[0];
        } else if (args == null) {
            arguments = new Object[]{evt};
        } else {
            arguments = new Object[args.length + 1];
            arguments[0] = evt;
            System.arraycopy(args, 0, arguments, 1, args.length);
        }

        if (LuaJITMachine.isSpecialEvent(evt)) {
            this.yieldResults.put(evt, arguments);
            synchronized (this.yieldResultsSignal) {
                this.yieldResultsSignal.notify();
            }
            return MachineResult.OK;
        }

        this.timeout.refresh();
        if(this.timeout.isSoftAborted()) this.abort();

        final Object[] results = this.handleEvent(arguments);

        if(this.timeout.isHardAborted()) {
            this.close();
            return MachineResult.TIMEOUT;
        }

        if (results == null) return MachineResult.PAUSE;

        if (results.length > 0 && results[0] instanceof String) this.eventFilter = (String) results[0];
        else this.eventFilter = null;

        return MachineResult.OK;
    }

    @Override
    public void close() {
        if(this.luaState == 0) return;
        this.deinitMachine();
    }

    @Nonnull
    @Override
    public Object[] yield(@Nullable final Object[] args) throws InterruptedException {
        if (this.yielded) {
            throw new RuntimeException("yield is not reentrant!");
        }

        if (args != null && args.length > 0 && args[0] instanceof String) {
            this.yielded = true;

            final String filter = (String) args[0];
            while (true) {
                if(this.yieldResults.containsKey(filter) && !this.yieldRequested) {
                    this.yieldRequested = true;
                    this.computer.queueEvent(null, null);
                    this.yielded = false;
                    return this.yieldResults.get(filter).remove(0);
                }

                try {
                    synchronized (this.yieldResultsSignal) {
                        this.yieldResultsSignal.wait();
                    }
                } catch(final InterruptedException ignored) {
                    System.out.println("blocking yield interrupted! (so long and thanks for all the fish!)");
                }
            }
        } else {
            throw new RuntimeException("Attempt to yield but no filter was provided!");
        }
    }

    @Override
    public Object[] executeMainThreadTask(@Nonnull final ILuaTask task) throws LuaException, InterruptedException {
        final long id = this.issueMainThreadTask(task);

        while (true) {
            final Object[] response = this.pullEvent("task_complete");
            if (response.length >= 3 && response[1] instanceof Number && response[2] instanceof Boolean && ((Number) response[1]).longValue() == id) {
                if ((Boolean) response[2]) {
                    final Object[] returnValues = new Object[response.length - 3];
                    System.arraycopy(response, 3, returnValues, 0, returnValues.length);
                    return returnValues;
                } else {
                    if (response.length >= 4 && response[3] instanceof String) {
                        throw new LuaException((String) response[3]);
                    } else {
                        throw new LuaException();
                    }
                }
            }
        }
    }

    @Override
    public long issueMainThreadTask(@Nonnull final ILuaTask task) throws LuaException {
        final long id = MainThread.getUniqueTaskID();
        this.computer.queueMainThread(() -> {
            try {
                final Object[] results = task.execute();
                if (results == null) {
                    LuaJITMachine.this.taskCompleted(id, true);
                } else {
                    LuaJITMachine.this.taskCompleted(id, true, results);
                }
            } catch (final LuaException e) {
                LuaJITMachine.this.taskCompleted(id, false, e.getMessage());
            } catch (final Throwable t) {
                LuaJITMachine.this.taskCompleted(id, false, String.format("Java Exception Thrown: %s", t.toString()));
            }
        });
        return id;
    }

    private void taskCompleted(final long id, final boolean result, final Object... args) {
        final Object[] arguments = new Object[args.length + 2];
        arguments[0] = id;
        arguments[1] = result;
        System.arraycopy(args, 0, arguments, 2, args.length);
        LuaJITMachine.this.computer.queueEvent("task_complete", arguments);
    }
}