#!/bin/bash
x86_64-w64-mingw32-g++ -Wl,-rpath,'$ORIGIN' -I"$JAVA_HOME/include" -I"LuaJIT/include" -L"$(realpath LuaJIT/bin/windows)" -l:luajit.dll -shared -o out/cclj.dll