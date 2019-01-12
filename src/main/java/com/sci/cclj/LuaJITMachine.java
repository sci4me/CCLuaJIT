package com.sci.cclj;

import java.io.File;
import java.net.URISyntaxException;

public final class LuaJITMachine {
    static {
        try {
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

            final File modsDirectory = new File(LuaJITMachine.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
            final File library = new File(modsDirectory, "cclj." + libraryExtension);
            System.load(library.getPath());
        } catch(final URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public LuaJITMachine() {
        System.out.println(this.test());
    }

    private native String test();
}