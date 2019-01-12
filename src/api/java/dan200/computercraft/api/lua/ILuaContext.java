/**
 * This file is part of the  ComputerCraft API - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2016. This API may be redistributed unmodified and in full only.
 * For help using the API, and posting your mods, visit the forums at computercraft.info.
 */

package dan200.computercraft.api.lua;

public interface ILuaContext {
    Object[] pullEvent(final String filter) throws LuaException, InterruptedException;

    Object[] pullEventRaw(final String filter) throws InterruptedException;

    Object[] yield(final Object[] arguments) throws InterruptedException;

    Object[] executeMainThreadTask(final ILuaTask task) throws LuaException, InterruptedException;

    long issueMainThreadTask(final ILuaTask task) throws LuaException;
}