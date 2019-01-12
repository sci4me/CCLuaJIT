package com.sci.cclj;

public final class LuaJITMachine {
    static {
        System.load("/home/sci4me/Projects/CCLuaJIT/build/natives/cclj.so");
    }

    public LuaJITMachine() {
        System.out.println(this.test());
    }

    private native String test();
}