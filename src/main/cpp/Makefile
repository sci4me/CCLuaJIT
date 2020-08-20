LUAJIT_DIR=vendor/luajit
JNI_HEADERS_DIR=vendor/jni-headers

CC=
INCLUDES=-I"${LUAJIT_DIR}/src" -I"${JNI_HEADERS_DIR}"
FLAGS=
TARGET_LIB=
OUT_DIR=/root/out

ifeq ($(TARGET_SYS), Linux)
	CC=clang
	INCLUDES+=-I"${JNI_HEADERS_DIR}/linux"
	FLAGS=-L"${LUAJIT_DIR}/bin/linux" -l:libluajit-5.1.so -shared -fPIE
	TARGET_LIB=cclj.so
else
ifeq ($(TARGET_SYS), Windows)
	CC=x86_64-w64-mingw32-c++
	INCLUDES+=-I"${JNI_HEADERS_DIR}/win32"
	FLAGS=-L"${LUAJIT_DIR}/bin/windows" -l:lua51.dll -shared -Wl,--kill-at
	TARGET_LIB=cclj.dll
else
ifeq ($(TARGET_SYS), OSX)
	CC=x86_64-apple-darwin15-c++
	INCLUDES+=-I"${JNI_HEADERS_DIR}/darwin"
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
	$(CC) $(INCLUDES) $(FLAGS) -o $(OUT_DIR)/$(TARGET_LIB) main.cpp