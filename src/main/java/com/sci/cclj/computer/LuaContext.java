package com.sci.cclj.computer;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class LuaContext implements ILuaContext {
    private static final MethodHandle GET_UNIQUE_TASK_ID_MH;
    private static final MethodHandle QUEUE_TASK_MH;
    private static final Constructor<?> ITASK_PROXY_CLASS_CONSTRUCTOR;

    static {
        MethodHandle getUniqueTaskID_mh = null;
        MethodHandle queueTask_mh = null;
        Constructor<?> iTask_proxy_class_constructor = null;

        try {
            final Class<?> MAIN_THREAD_CLASS = Class.forName("dan200.computercraft.core.computer.MainThread");
            final Class<?> iTask_class = Class.forName("dan200.computercraft.core.computer.ITask");

            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            getUniqueTaskID_mh = lookup.findStatic(MAIN_THREAD_CLASS, "getUniqueTaskID", MethodType.methodType(long.class));
            queueTask_mh = lookup.findStatic(MAIN_THREAD_CLASS, "queueTask", MethodType.methodType(boolean.class, iTask_class));

            final Class<?> iTask_proxy_class = Proxy.getProxyClass(LuaContext.class.getClassLoader(), iTask_class);
            iTask_proxy_class_constructor = iTask_proxy_class.getConstructor(InvocationHandler.class);
        } catch(final ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
        } finally {
            GET_UNIQUE_TASK_ID_MH = getUniqueTaskID_mh;
            QUEUE_TASK_MH = queueTask_mh;
            ITASK_PROXY_CLASS_CONSTRUCTOR = iTask_proxy_class_constructor;
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
    public long issueMainThreadTask(final ILuaTask task) {
        try {
            final long id = (long) GET_UNIQUE_TASK_ID_MH.invoke();
            final Object proxy = ITASK_PROXY_CLASS_CONSTRUCTOR.newInstance(new ITaskInvocationHandler(this.machine.computer, id, task));
            if((Boolean) QUEUE_TASK_MH.invoke(proxy)) {
                return id;
            } else {
                throw new LuaException("Task limit exceeded");
            }
        } catch(final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static final class ITaskInvocationHandler implements InvocationHandler {
        static {
            LuaJITMachine.registerSpecialEvent("task_complete");
        }

        private final IComputer computer;
        private final long id;
        private final ILuaTask task;

        public ITaskInvocationHandler(final IComputer computer, final long id, final ILuaTask task) {
            this.computer = computer;
            this.id = id;
            this.task = task;
        }

        @Override
        public Object invoke(final Object self, final Method method, final Object[] objects) throws Throwable {
            switch(method.getName()) {
                case "getComputer":
                    return this.computer;
                case "execute":
                    try {
                        final Object[] results = this.task.execute();
                        if(results == null) {
                            this.computer.queueEvent("task_complete", new Object[]{
                                    this.id, true
                            });
                        } else {
                            final Object[] eventArguments = new Object[results.length + 2];
                            eventArguments[0] = this.id;
                            eventArguments[1] = true;
                            System.arraycopy(results, 0, eventArguments, 2, results.length);
                            this.computer.queueEvent("task_complete", eventArguments);
                        }
                    } catch(final LuaException e) {
                        this.computer.queueEvent("task_complete", new Object[]{
                                this.id, false, e.getMessage()
                        });
                    } catch(final Throwable t) {
                        this.computer.queueEvent("task_complete", new Object[]{
                                this.id, false, String.format("Java Exception Thrown: %s", t.toString())
                        });
                    }
                    return null;
                default:
                    throw new Exception("Unknown method on ITask '" + method.getName() + "'");
            }
        }
    }
}