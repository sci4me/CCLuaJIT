package com.sci.cclj.util;

public enum OS {
    WINDOWS,
    OSX,
    LINUX,
    OTHER;

    public static OS check() {
        final String os = System.getProperty("os.name");
        if(os.contains("win")) {
            return OS.WINDOWS;
        } else if(os.contains("mac")) {
            return OS.OSX;
        } else if(os.contains("nux") || os.contains("nix")) {
            return OS.LINUX;
        } else {
            return OS.OTHER;
        }
    }
}