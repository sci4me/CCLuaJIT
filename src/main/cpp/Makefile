all: compile copy

compile:
	cp ../../../LuaJIT/bin/linux/libluajit-5.1.so.2 .
	g++ -I"${JAVA_HOME}/include" -I"${JAVA_HOME}/include/linux" -I"../../../LuaJIT/include" -L"$(realpath ../../../LuaJIT/bin/linux)" -l:libluajit-5.1.so.2 -Wl,-rpath,'$$ORIGIN' -shared -o cclj.so com_sci_cclj_LuaJITMachine.cpp

copy:
	mv cclj.so ../../../build/libs
	mv libluajit-5.1.so.2 ../../../build/libs
