/**
 * This file is part of the public ComputerCraft API - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2016. This API may be redistributed unmodified and in full only.
 * For help using the API, and posting your mods, visit the forums at computercraft.info.
 */

package dan200.computercraft.core.lua;

import dan200.computercraft.core.apis.ILuaAPI;

import java.io.InputStream;
import java.io.OutputStream;

public interface ILuaMachine {
    void addAPI(final ILuaAPI api);

    void loadBios(final InputStream bios);

    void handleEvent(final String eventName, final Object[] arguments);

    void softAbort(final String abortMessage);

    void hardAbort(final String abortMessage);

    boolean saveState(final OutputStream output);

    boolean restoreState(final InputStream input);

    boolean isFinished();

    void unload();
}