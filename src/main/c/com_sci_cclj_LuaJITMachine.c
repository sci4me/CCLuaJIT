#include "com_sci_cclj_LuaJITMachine.h"

JNIEXPORT jstring JNICALL Java_com_sci_cclj_LuaJITMachine_test(JNIEnv *env, jobject thisObject) {
    return (*env)->NewStringUTF(env, "Hello, JNI!");
}