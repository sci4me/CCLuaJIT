package com.sci.cclj;

import java.io.File;
import java.net.URISyntaxException;

public final class LuaJITMachine {
    static {
        try {
            final String libraryExtension;

            final String os = System.getProperty("os.name").toLowerCase();
            if(os.contains("win")) {
                throw new RuntimeException("Windows is currently not supported");
            } else if(os.contains("mac")) {
                throw new RuntimeException("OSX is currently not supported");
            } else if(os.contains("nux") || os.contains("nix") || os.contains("aix")) {
                libraryExtension = "so";
            } else {
                throw new RuntimeException("Unknown operating system: '" + os + "'");
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