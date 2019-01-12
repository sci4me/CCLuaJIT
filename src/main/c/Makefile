all:
	gcc -I"${JAVA_HOME}/include" -I"${JAVA_HOME}/include/linux" -shared -o cclj.so com_sci_cclj_LuaJITMachine.c
	mkdir -p ../../../build/natives
	mv cclj.so ../../../build/natives