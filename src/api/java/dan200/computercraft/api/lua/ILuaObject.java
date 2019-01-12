/**
 * This file is part of the public ComputerCraft API - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2016. This API may be redistributed unmodified and in full only.
 * For help using the API, and posting your mods, visit the forums at computercraft.info.
 */

package dan200.computercraft.api.lua;

public interface ILuaObject {
    String[] getMethodNames();

    Object[] callMethod(final ILuaContext context, final int method, final Object[] arguments) throws LuaException, InterruptedException;
}