package com.sci.cclj;

import dan200.computercraft.api.lua.*;

public final class LuaContext implements ILuaContext {
	private final LuaJITMachine machine;

	public LuaContext(final LuaJITMachine machine) {
		this.machine = machine;
	}

    public Object[] pullEvent(final String filter) throws LuaException, InterruptedException {
    	final Object[] results = this.pullEventRaw(filter);
    	if(results.length > 0 && results[0].equals("terminate")) {
    		throw new LuaException("Terminated", 0);
    	}
    	return results;
    }

    public Object[] pullEventRaw(final String filter) throws InterruptedException {
    	return this.yield(new Object[] { filter });
    }

    public Object[] yield(final Object[] arguments) throws InterruptedException {
    	throw new RuntimeException("yield");
    }

    public Object[] executeMainThreadTask(final ILuaTask task) throws LuaException, InterruptedException {
    	throw new RuntimeException("executeMainThreadTask");
    }

    public long issueMainThreadTask(final ILuaTask task) throws LuaException {
    	throw new RuntimeException("issueMainThreadTask");
    }
}