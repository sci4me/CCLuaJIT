/**
 * This file is part of the public ComputerCraft API - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2016. This API may be redistributed unmodified and in full only.
 * For help using the API, and posting your mods, visit the forums at computercraft.info.
 */

package dan200.computercraft.core.apis;

import dan200.computercraft.api.lua.ILuaObject;

public interface ILuaAPI extends ILuaObject {
    String[] getNames();

    void startup();

    void advance(final double dt);

    void shutdown();
}
