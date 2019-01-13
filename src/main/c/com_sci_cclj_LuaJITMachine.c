#include <string.h>

#include <jni.h>

extern "C" {
#include <lua.h>
#include <lualib.h>
#include <lauxlib.h>
}

#define CCLJ_JNIVERSION JNI_VERSION_1_6

#define CCLJ_JNIEXPORT(rtype, name, ...) JNIEXPORT rtype JNICALL Java_com_sci_cclj_LuaJITMachine_##name(JNIEnv *env, jobject obj, ##__VA_ARGS__)

static int wrap_lua_object(JNIEnv *env, lua_State *L, jobject obj);

static lua_State* get_lua_state(JNIEnv *env, jobject obj);
static void set_lua_state(JNIEnv *env, jobject obj, lua_State *L);
static lua_State* get_main_routine(JNIEnv *env, jobject obj);
static void set_main_routine(JNIEnv *env, jobject obj, lua_State *L);

static void sysout(JNIEnv *env, const char *str);

static jclass object_class = 0;

static jclass machine_class = 0;
static jfieldID lua_state_id = 0;
static jfieldID main_routine_id = 0;

static jclass iluaobject_class = 0;
static jmethodID get_method_names_id = 0;
static jmethodID call_method_id = 0;

static jclass iluaapi_class = 0;
static jmethodID get_names_id = 0;

static JavaVM *jvm;
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
    jvm = vm;

	JNIEnv *env;
	if (vm->GetEnv((void **) &env, CCLJ_JNIVERSION) != JNI_OK) {
		return CCLJ_JNIVERSION;
	}

    if(!(object_class = env->FindClass("java/lang/Object"))) {
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

    if(!(iluaobject_class = env->FindClass("dan200/computercraft/api/lua/ILuaObject")) ||
        !(get_method_names_id = env->GetMethodID(iluaobject_class, "getMethodNames", "()[Ljava/lang/String;")) ||
        !(call_method_id = env->GetMethodID(iluaobject_class, "callMethod", "(Ldan200/computercraft/api/lua/ILuaContext;I[Ljava/lang/Object;)[Ljava/lang/Object;"))) {
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

    int i = wrap_lua_object(env, L, api);    

    jobjectArray names = (jobjectArray) env->CallObjectMethod(api, get_names_id);
    jsize len = env->GetArrayLength(names);
    for(jsize i = 0; i < len; i++) {
        jstring name = (jstring) env->GetObjectArrayElement(names, i);
        const char *namec = env->GetStringUTFChars(name, JNI_FALSE);
        lua_pushvalue(L, i);
        lua_setglobal(L, namec);
        env->ReleaseStringUTFChars(name, namec);
        env->DeleteLocalRef(name);
    }

    lua_remove(L, i);

    return 1;
}

CCLJ_JNIEXPORT(jboolean, loadBios, jstring bios) {
    lua_State *L = get_lua_state(env, obj);

    lua_State *main_routine = lua_newthread(L);
    if(!main_routine) return 0;

    const char *biosc = env->GetStringUTFChars(bios, JNI_FALSE);
    int err = luaL_loadbuffer(main_routine, biosc, strlen(biosc), "bios.lua");
    env->ReleaseStringUTFChars(bios, biosc);
    if(err) return 0;

    set_main_routine(env, obj, main_routine);

    return lua_isthread(L, -1);
}

CCLJ_JNIEXPORT(jobjectArray, resumeMainRoutine, jobjectArray args) {
    jobjectArray result = env->NewObjectArray(0, object_class, 0);



    return result;
}

#ifdef __cplusplus
}
#endif

typedef struct JavaFN {
    jobject obj;
    int index;
} JavaFN;

static JavaFN* check_java_fn(lua_State *L) {
    JavaFN *jfn = (JavaFN*) luaL_checkudata(L, 1, "JavaFN");
    if(!jfn->obj) luaL_error(L, "Attempt to finalize finalized JavaFN");
    return jfn;
}

static int invoke_java_fn(lua_State *L) {

    return 0;
}

static int finalize_java_fn(lua_State *L) {
    JavaFN *jfn = check_java_fn(L);

    JNIEnv *env;
    if(jvm->GetEnv((void**) &env, CCLJ_JNIVERSION) != JNI_OK) {
        luaL_error(L, "JavaFN finalizer could not retrieve JNIEnv");
        return 0;
    }

    env->DeleteGlobalRef(jfn->obj);

    jfn->obj = 0;
    return 0;
}

static void new_java_fn(JNIEnv *env, lua_State *L, jobject obj, int index) {
    JavaFN *jfn = (JavaFN*) lua_newuserdata(L, sizeof(JavaFN));
    jfn->obj = env->NewGlobalRef(obj);
    jfn->index = index;

    if(luaL_newmetatable(L, "JavaFN")) {
        lua_pushstring(L, "__gc");
        lua_pushcfunction(L, finalize_java_fn);
        lua_settable(L, -3);
    }

    lua_setmetatable(L, -2);
}

static int wrap_lua_object(JNIEnv *env, lua_State *L, jobject obj) {
    lua_newtable(L);
    int table = lua_gettop(L);

    jobjectArray methodNames = (jobjectArray) env->CallObjectMethod(obj, get_method_names_id);
    jsize len = env->GetArrayLength(methodNames);
    for(jsize i = 0; i < len; i++) {
        jstring methodName = (jstring) env->GetObjectArrayElement(methodNames, i);
        const char *methodNamec = env->GetStringUTFChars(methodName, JNI_FALSE);

        lua_pushstring(L, methodNamec);
        new_java_fn(env, L, obj, i);
        lua_pushcclosure(L, invoke_java_fn, 1);
        lua_settable(L, table);

        env->ReleaseStringUTFChars(methodName, methodNamec);
        env->DeleteLocalRef(methodName);
    }

    return lua_gettop(L);
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

void sysout(JNIEnv *env, const char *str) {
    jclass system = env->FindClass("java/lang/System");
    jfieldID outID = env->GetStaticFieldID(system, "out", "Ljava/io/PrintStream;");
    jobject out = env->GetStaticObjectField(system, outID);
    jclass printStream = env->FindClass("java/io/PrintStream");
    jmethodID printlnID = env->GetMethodID(printStream, "println", "(Ljava/lang/String;)V");
    jstring jstr = env->NewStringUTF(str);
    env->CallVoidMethod(out, printlnID, jstr);
}