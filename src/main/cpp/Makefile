CC=
INCLUDES=-I"LuaJIT/src" -I"jni-headers"
FLAGS=
TARGET_LIB=
OUT_DIR=out

ifeq ($(TARGET_SYS), Linux)
	CC=g++
	INCLUDES+=-I"jni-headers/linux"
	FLAGS=-L"LuaJIT/bin/linux" -l:libluajit-5.1.so.2 -shared -fPIE
	TARGET_LIB=cclj.so
else
ifeq ($(TARGET_SYS), Windows)
	CC=x86_64-w64-mingw32-c++
	INCLUDES+=-I"jni-headers/win32"
	FLAGS=-L"LuaJIT/bin/windows" -l:lua51.dll -shared -Wl,--kill-at
	TARGET_LIB=cclj.dll
else
ifeq ($(TARGET_SYS), OSX)
	CC=x86_64-apple-darwin15-c++
	INCLUDES+=-I"jni-headers/darwin"
	FLAGS=-single_module -undefined dynamic_lookup
	TARGET_LIB=cclj.dylib
else
$(error TARGET_SYS must be set to Linux, Windows, or OSX)
endif
endif
endif

.PHONY: all

all:
	mkdir -p $(OUT_DIR)
	$(CC) $(INCLUDES) $(FLAGS) -o $(OUT_DIR)/$(TARGET_LIB) LuaJITMachine.cpp