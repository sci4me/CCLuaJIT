#!/bin/bash
g++ -shared -fPIC -Wl,-rpath,'$ORIGIN' -o cclj.so -I"${JAVA_HOME}/include" -I"$JAVA_HOME/include/linux" -I"LuaJIT/include" -L"$(realpath LuaJIT/bin/linux)" -l:libluajit-5.1.so.2 LuaJITMachine.cpp