package com.sci.cclj.computer;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ITask;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Proxy;

public final class LuaContext implements ILuaContext {
    private static final MethodHandle GET_UNIQUE_TASK_ID_MH;
    private static final MethodHandle QUEUE_TASK_MH;

    static {
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

    private final LuaJITMachine machine;

    public LuaContext(final LuaJITMachine machine) {
        this.machine = machine;
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
        return this.machine.yield(arguments);
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
                    return LuaContext.this.machine.computer;
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
                    LuaContext.this.machine.computer.queueEvent("task_complete", arguments);
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