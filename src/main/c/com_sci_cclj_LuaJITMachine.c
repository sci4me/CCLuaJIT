#include <string.h>

#include <jni.h>

extern "C" {
#include <lua.h>
#include <lualib.h>
#include <lauxlib.h>
}

#define CCLJ_JNIVERSION JNI_VERSION_1_6

#define CCLJ_JNIEXPORT(rtype, name, ...) JNIEXPORT rtype JNICALL Java_com_sci_cclj_LuaJITMachine_##name(JNIEnv *env, jobject obj, ##__VA_ARGS__)

static void sysout(JNIEnv *env, const char *str);

static lua_State* get_lua_state(JNIEnv *env, jobject obj);
static void set_lua_state(JNIEnv *env, jobject obj, lua_State *L);
static lua_State* get_main_routine(JNIEnv *env, jobject obj);
static void set_main_routine(JNIEnv *env, jobject obj, lua_State *L);

static jclass machine_class = 0;
static jfieldID lua_state_id = 0;
static jfieldID main_routine_id = 0;

static jclass iluaapi_class = 0;
static jmethodID get_names_id = 0;

static int initialized = 0;

#ifdef __cplusplus
extern "C" {
#endif

static void dump_stack(JNIEnv *env, lua_State *L) {
    sysout(env, "stack: [");

    char buf[32];

    int top = lua_gettop(L);
    for (int i = top; i > 0; i--) {
        int j = i - top - 1;
        int t = lua_type(L, i);
        switch (t) {
            case LUA_TSTRING:
                sprintf(buf, "%i (%i)  '%s'", i, j, lua_tostring(L, i));
                break;
            case LUA_TBOOLEAN:
                sprintf(buf, "%i (%i)  %i", i, j, lua_toboolean(L, i) ? "true" : "false");
                break;
            case LUA_TNUMBER:
                sprintf(buf, "%i (%i)  %g", i, j, lua_tonumber(L, i));
                break;
            default:
                sprintf(buf, "%i (%i)  %s", i, j, lua_typename(L, t));
                break;
        }
        sysout(env, buf);
    }
    sysout(env, "]");
}

JNIEXPORT jint JNICALL JNI_OnLoad (JavaVM *vm, void *reserved) {
	JNIEnv *env;
	if (vm->GetEnv((void **) &env, CCLJ_JNIVERSION) != JNI_OK) {
		return CCLJ_JNIVERSION;
	}

    if(!(machine_class = env->FindClass("com/sci/cclj/LuaJITMachine")) ||
        !(lua_state_id = env->GetFieldID(machine_class, "luaState", "J")) ||
        !(main_routine_id = env->GetFieldID(machine_class, "mainRoutine", "J"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(iluaapi_class = env->FindClass("dan200/computercraft/core/apis/ILuaAPI")) ||
        !(get_names_id = env->GetMethodID(iluaapi_class, "getNames", "()[Ljava/lang/String;"))) {
        return CCLJ_JNIVERSION;
    }

    initialized = 1;
	return CCLJ_JNIVERSION;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv((void **) &env, CCLJ_JNIVERSION) != JNI_OK) {
        return;
    }

    if(machine_class) {
        env->DeleteGlobalRef(machine_class);
    }
}

CCLJ_JNIEXPORT(jboolean, createLuaState) {
    if(!initialized) return 0;

    lua_State *L = luaL_newstate();
    if(!L) return 0;

    luaopen_base(L);
    luaopen_math(L);
    luaopen_string(L);
    luaopen_table(L);
    luaopen_bit(L);

    lua_pop(L, lua_gettop(L));

    set_lua_state(env, obj, L);

    return 1;
}

CCLJ_JNIEXPORT(void, destroyLuaState) {
    lua_State *L = get_lua_state(env, obj);

    lua_close(L);

    set_lua_state(env, obj, 0);
}

CCLJ_JNIEXPORT(jboolean, registerAPI, jobject api) {
    lua_State *L = get_lua_state(env, obj);

    jobjectArray names = (jobjectArray) env->CallObjectMethod(api, get_names_id);
    jsize len = env->GetArrayLength(names);
    for(jsize i = 0; i < len; i++) {
        jstring name = (jstring) env->GetObjectArrayElement(names, i);

        // @TODO
    }

    return 0;
}

CCLJ_JNIEXPORT(jboolean, loadBios, jstring bios) {
    lua_State *L = get_lua_state(env, obj);

    lua_State *main_routine = lua_newthread(L);
    if(!main_routine) return 0;

    const char *biosc = env->GetStringUTFChars(bios, JNI_FALSE);
    int err = luaL_loadbuffer(main_routine, biosc, strlen(biosc), "bios.lua");
    env->ReleaseStringUTFChars(bios, biosc);
    if(err) return 0;

    return lua_isthread(L, -1);
}

#ifdef __cplusplus
}
#endif

void sysout(JNIEnv *env, const char *str) {
    jclass system = env->FindClass("java/lang/System");
    jfieldID outID = env->GetStaticFieldID(system, "out", "Ljava/io/PrintStream;");
    jobject out = env->GetStaticObjectField(system, outID);
    jclass printStream = env->FindClass("java/io/PrintStream");
    jmethodID printlnID = env->GetMethodID(printStream, "println", "(Ljava/lang/String;)V");
    jstring jstr = env->NewStringUTF(str);
    env->CallVoidMethod(out, printlnID, jstr);
}

lua_State *get_lua_state(JNIEnv *env, jobject obj) {
    return (lua_State*) env->GetLongField(obj, lua_state_id);
}

void set_lua_state(JNIEnv *env, jobject obj, lua_State *L) {
    env->SetLongField(obj, lua_state_id, (jlong) L);
}

lua_State *get_main_routine(JNIEnv *env, jobject obj) {
    return (lua_State*) env->GetLongField(obj, main_routine_id);
}

void set_main_routine(JNIEnv *env, jobject obj, lua_State *L) {
    env->SetLongField(obj, main_routine_id, (jlong) L);
}