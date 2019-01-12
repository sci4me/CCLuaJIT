#include <jni.h>

#include <lua.h>
#include <lualib.h>
#include <lauxlib.h>

#define CCLJ_JNIVERSION JNI_VERSION_1_6

static void sysout(JNIEnv *env, const char *str);

static jclass get_class(JNIEnv *env, const char *name);

static lua_State* get_lua_state(JNIEnv *env, jobject obj);
static void set_lua_state(JNIEnv *env, jobject obj, lua_State *L);

static jclass machine_class = 0;
static jfieldID lua_state_id = 0;
static int initialized = 0;

JNIEXPORT jint JNICALL JNI_OnLoad (JavaVM *vm, void *reserved) {
	JNIEnv *env;
	if ((*vm)->GetEnv(vm, (void **) &env, CCLJ_JNIVERSION) != JNI_OK) {
		return CCLJ_JNIVERSION;
	}

    if(!(machine_class = get_class(env, "com/sci/cclj/LuaJITMachine")) ||
        !(lua_state_id = (*env)->GetFieldID(env, machine_class, "luaState", "J"))) {
        return CCLJ_JNIVERSION;
    }

    initialized = 1;
	return CCLJ_JNIVERSION;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, CCLJ_JNIVERSION) != JNI_OK) {
        return;
    }

    if(machine_class) {
        (*env)->DeleteGlobalRef(env, machine_class);
    }
}

JNIEXPORT jboolean JNICALL Java_com_sci_cclj_LuaJITMachine_createLuaState(JNIEnv *env, jobject obj) {
    if(!initialized) return 0;

    lua_State *L = luaL_newstate();
    if(!L) return 0;

    set_lua_state(env, obj, L);

    sysout(env, "INITIALIZED LUA STATE");

    return 1;
}

JNIEXPORT void JNICALL Java_com_sci_cclj_LuaJITMachine_destroyLuaState(JNIEnv *env, jobject obj) {
    lua_State *L = get_lua_state(env, obj);

    lua_close(L);

    set_lua_state(env, obj, 0);

    sysout(env, "CLOSED LUA STATE");
}

static void sysout(JNIEnv *env, const char *str) {
    jclass system = (*env)->FindClass(env, "java/lang/System");
    jfieldID outID = (*env)->GetStaticFieldID(env, system, "out", "Ljava/io/PrintStream;");
    jobject out = (*env)->GetStaticObjectField(env, system, outID);
    jclass printStream = (*env)->FindClass(env, "java/io/PrintStream");
    jmethodID printlnID = (*env)->GetMethodID(env, printStream, "println", "(Ljava/lang/String;)V");
    jstring jstr = (*env)->NewStringUTF(env, str);
    (*env)->CallVoidMethod(env, out, printlnID, jstr);
}

static jclass get_class(JNIEnv *env, const char *name) {
    jclass clazz = (*env)->FindClass(env, name);
    if(!clazz) {
        return 0;
    }
    return (*env)->NewGlobalRef(env, clazz);
}

lua_State *get_lua_state(JNIEnv *env, jobject obj) {
    return (lua_State*) (*env)->GetLongField(env, obj, lua_state_id);
}

void set_lua_state(JNIEnv *env, jobject obj, lua_State *L) {
    (*env)->SetLongField(env, obj, lua_state_id, (jlong) L);
}