#include <stdlib.h>
#include <string.h>

#include <jni.h>

extern "C" {
#include <lua.h>
#include <lualib.h>
#include <lauxlib.h>
}

#define CCLJ_JNIVERSION JNI_VERSION_1_6

#define CCLJ_JNIEXPORT(rtype, name, ...) JNIEXPORT rtype JNICALL Java_com_sci_cclj_LuaJITMachine_##name(JNIEnv *env, jobject obj, ##__VA_ARGS__)

static void map_to_table(JNIEnv *env, lua_State *L, jobject map, jobject valuesInProgress);
static void map_to_table(JNIEnv *env, lua_State *L, jobject map);
static jobject table_to_map(JNIEnv *env, lua_State *L, jobject objectsInProgress);
static jobject table_to_map(JNIEnv *env, lua_State *L);

static void to_lua_value(JNIEnv *env, lua_State *L, jobject value);
static jobject to_java_value(JNIEnv *env, lua_State *L);

static void to_lua_values(JNIEnv *env, lua_State *L, jobjectArray values);
static jobjectArray to_java_values(JNIEnv *env, lua_State *L, int n);

static int wrap_lua_object(JNIEnv *env, lua_State *L, jobject obj);

static lua_State* get_lua_state(JNIEnv *env, jobject obj);
static void set_lua_state(JNIEnv *env, jobject obj, lua_State *L);
static lua_State* get_main_routine(JNIEnv *env, jobject obj);
static void set_main_routine(JNIEnv *env, jobject obj, lua_State *L);

static void sysout(jobject obj);
static void sysout(const char *str);

static jclass object_class = 0;

static jclass number_class = 0;
static jmethodID doublevalue_id = 0;

static jclass integer_class = 0;

static jclass double_class = 0;
static jmethodID double_valueof_id = 0;

static jclass boolean_class = 0;
static jmethodID boolean_valueof_id = 0;
static jmethodID booleanvalue_id = 0;

static jclass string_class = 0;

static jclass bytearray_class = 0;

static jclass map_class = 0;

static jclass identityhashmap_class = 0;
static jmethodID identityhashmap_init_id = 0;

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
    sysout("stack: [");

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
        sysout(buf);
    }
    sysout("]");
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

    if(!(number_class = env->FindClass("java/lang/Number")) ||
        !(doublevalue_id = env->GetMethodID(number_class, "doubleValue", "()D"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(integer_class = env->FindClass("java/lang/Integer"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(double_class = env->FindClass("java/lang/Double")) ||
        !(double_valueof_id = env->GetStaticMethodID(double_class, "valueOf", "(D)Ljava/lang/Double;"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(boolean_class = env->FindClass("java/lang/Boolean")) ||
        !(boolean_valueof_id = env->GetStaticMethodID(boolean_class, "valueOf", "(Z)Ljava/lang/Boolean;")) ||
        !(booleanvalue_id = env->GetMethodID(boolean_class, "booleanValue", "()Z"))) {
        return CCLJ_JNIVERSION;
    }    

    if(!(string_class = env->FindClass("java/lang/String"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(bytearray_class = env->FindClass("[B"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(map_class = env->FindClass("java/util/Map"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(identityhashmap_class = env->FindClass("java/util/IdentityHashMap")) ||
        !(identityhashmap_init_id = env->GetMethodID(identityhashmap_class, "<init>", "()V"))) {
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

    if(object_class)        env->DeleteGlobalRef(object_class);
    if(machine_class)       env->DeleteGlobalRef(machine_class);
    if(iluaapi_class)       env->DeleteGlobalRef(iluaapi_class);
    if(iluaobject_class)    env->DeleteGlobalRef(iluaobject_class); 
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
    set_main_routine(env, obj, 0);
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
    lua_State *L = get_main_routine(env, obj);

    to_lua_values(env, L, args);

    jsize alen = env->GetArrayLength(args);
    int before = lua_gettop(L) + alen;
    int stat = lua_resume(L, alen);
    int after = lua_gettop(L);

    if(stat == LUA_YIELD || stat == 0) {
        jobjectArray results;

        int nresults = after - before;
        if(nresults > 0) {
            results = to_java_values(env, L, nresults);
        } else {
            results = env->NewObjectArray(0, object_class, 0);
        }
    
        return results;
    } else {
        // @TODO throw an exception

        return 0;
    }
}

#ifdef __cplusplus
}
#endif

static void map_to_table(JNIEnv *env, lua_State *L, jobject map, jobject valuesInProgress) {
    sysout("MAP_TO_TABLE!!!");
    sysout("MAP_TO_TABLE!!!");
    sysout("MAP_TO_TABLE!!!");
}

static void map_to_table(JNIEnv *env, lua_State *L, jobject map) {
    jobject vip = env->NewObject(identityhashmap_class, identityhashmap_init_id);
    map_to_table(env, L, map, vip);
}

static jobject table_to_map(JNIEnv *env, lua_State *L, jobject objectsInProgress) {
    sysout("TABLE_TO_MAP!!!");
    sysout("TABLE_TO_MAP!!!");
    sysout("TABLE_TO_MAP!!!");
}

static jobject table_to_map(JNIEnv *env, lua_State *L) {
    jobject oip = env->NewObject(identityhashmap_class, identityhashmap_init_id);
    return table_to_map(env, L, oip);
}

static void to_lua_value(JNIEnv *env, lua_State *L, jobject value) {
    // sysout("nil check");

    // if(value == 0) {
    //     lua_pushnil(L);
    //     return;
    // } 

    // sysout("number check");

    // if(env->IsInstanceOf(value, integer_class) || env->IsInstanceOf(value, double_class)) {
    //     jdouble n = (jdouble) env->CallDoubleMethod(value, doublevalue_id);
    //     lua_pushnumber(L, n);
    //     return;
    // }

    // sysout("boolean check");

    // if(env->IsInstanceOf(value, boolean_class)) {
    //     jboolean b = (jboolean) env->CallBooleanMethod(value, booleanvalue_id);
    //     lua_pushboolean(L, b);
    //     return;
    // } 

    // sysout("string check");

    // if(env->IsInstanceOf(value, string_class)) {
    //     sysout("PUSHING STRING TO LUA STACK");
    //     jstring str = (jstring) value;
    //     const char *cstr = env->GetStringUTFChars(str, JNI_FALSE);
    //     lua_pushstring(L, cstr);
    //     env->ReleaseStringUTFChars(str, cstr);
    //     return;
    // }

    // sysout("bytearray check"); 

    // if(env->IsInstanceOf(value, bytearray_class)) {
    //     jbyteArray a = (jbyteArray) value;
    //     jsize alen = env->GetArrayLength(a);
    //     jbyte *ca = env->GetByteArrayElements(a, JNI_FALSE);

    //     char *rca = (char*) malloc(alen + 1);
    //     memcpy(rca, ca, alen);
    //     rca[alen] = 0;
    //     lua_pushstring(L, rca);
    //     free(rca);

    //     env->ReleaseByteArrayElements(a, ca, JNI_ABORT);
    //     return;
    // }

    // sysout("map check");

    // if(env->IsInstanceOf(value, map_class)) {
    //     sysout("to_lua_value map");
    //     map_to_table(env, L, value);
    //     return;
    // }

    // sysout("iluaobject check");

    // if(env->IsInstanceOf(value, iluaobject_class)) {
    //     sysout("to_lua_value iluaobject");
    //     wrap_lua_object(env, L, value);
    //     return;
    // } 

    // luaL_error(L, "Attempt to convert unrecognized Java value to a Lua value!");

    sysout(value);
    sysout(env->GetObjectClass(value));

    if(value == 0) {
        lua_pushnil(L);
    } else if(env->IsInstanceOf(value, number_class)) {
        jdouble n = (jdouble) env->CallDoubleMethod(value, doublevalue_id);
        lua_pushnumber(L, n);
    } else if(env->IsInstanceOf(value, boolean_class)) {
        jboolean b = (jboolean) env->CallBooleanMethod(value, booleanvalue_id);
        lua_pushboolean(L, b);
    } else if(env->IsInstanceOf(value, string_class)) {
        jstring str = (jstring) value;
        const char *cstr = env->GetStringUTFChars(str, JNI_FALSE);
        lua_pushstring(L, cstr);
        env->ReleaseStringUTFChars(str, cstr);
    } else if(env->IsInstanceOf(value, bytearray_class)) {
        jbyteArray a = (jbyteArray) value;
        jsize alen = env->GetArrayLength(a);
        jbyte *ca = env->GetByteArrayElements(a, JNI_FALSE);

        char *rca = (char*) malloc(alen + 1);
        memcpy(rca, ca, alen);
        rca[alen] = 0;
        lua_pushstring(L, rca);
        free(rca);

        env->ReleaseByteArrayElements(a, ca, JNI_ABORT);
    } else if(env->IsInstanceOf(value, map_class)) {
        sysout("to_lua_value map");
        map_to_table(env, L, value);
    } else if(env->IsInstanceOf(value, iluaobject_class)) {
        sysout("to_lua_value iluaobject");
        wrap_lua_object(env, L, value);
    } else {
        luaL_error(L, "Attempt to convert unrecognized Java value to a Lua value!");
    }
}

static void to_lua_values(JNIEnv *env, lua_State *L, jobjectArray values) {
    sysout("to_lua_values");

    jsize len = env->GetArrayLength(values);
    for(jsize i = 0; i < len; i++) {
        char buf[32];
        sprintf(buf, "i: %i", i);
        sysout(buf);

        jobject elem = env->GetObjectArrayElement(values, i);
        to_lua_value(env, L, elem);
        env->DeleteLocalRef(elem);
    }
}

static jobject to_java_value(JNIEnv *env, lua_State *L) {
    dump_stack(env, L);

    int t = lua_type(L, -1);
    switch(t) {
        case LUA_TNIL:
        case LUA_TNONE:
            lua_pop(L, 1);
            return 0;
        case LUA_TNUMBER: {
            lua_Number n = lua_tonumber(L, -1);
            lua_pop(L, 1);
            return env->CallStaticObjectMethod(double_class, double_valueof_id, n);
        }
        case LUA_TBOOLEAN: {
            int b = lua_toboolean(L, -1);
            lua_pop(L, 1);
            return env->CallStaticObjectMethod(boolean_class, boolean_valueof_id, b);
        }
        case LUA_TSTRING: {
            const char *cstr = lua_tostring(L, -1);
            jstring result = env->NewStringUTF(cstr);
            lua_pop(L, 1);
            return result;
        }
        case LUA_TTABLE:
            return table_to_map(env, L);
        case LUA_TFUNCTION:
            luaL_error(L, "Attempt to convert a Lua function to a Java value!");
            break;
        case LUA_TTHREAD:
            luaL_error(L, "Attempt to convert a Lua thread to a Java value!");
            break;
        case LUA_TUSERDATA:
            luaL_error(L, "Attempt to convert userdata to a Java value!");
            break;
        case LUA_TLIGHTUSERDATA:
            luaL_error(L, "Attempt to convert lightuserdata to a Java value!");
            break;
        default:
            luaL_error(L, "Attempt to convert unrecognized Lua value to a Java value!");
            break;
    }

    return 0;
}

static jobjectArray to_java_values(JNIEnv *env, lua_State *L, int n) {
    jobjectArray result = env->NewObjectArray(n, object_class, 0);
    for(int i = n; i > 0; i--) {
        env->SetObjectArrayElement(result, i, to_java_value(env, L));
    }
    return result;
}

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

    return table;
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

void sysout(jobject obj) {
    JNIEnv *env;
    if(jvm->GetEnv((void**) &env, CCLJ_JNIVERSION) != JNI_OK) {
        return;
    }

    jclass system = env->FindClass("java/lang/System");
    jfieldID outID = env->GetStaticFieldID(system, "out", "Ljava/io/PrintStream;");
    jobject out = env->GetStaticObjectField(system, outID);
    jclass printStream = env->FindClass("java/io/PrintStream");
    jmethodID printlnID = env->GetMethodID(printStream, "println", "(Ljava/lang/Object;)V");
    env->CallVoidMethod(out, printlnID, obj);

    jmethodID flushID = env->GetMethodID(printStream, "flush", "()V");
    env->CallVoidMethod(out, flushID);
}

void sysout(const char *str) {
    JNIEnv *env;
    if(jvm->GetEnv((void**) &env, CCLJ_JNIVERSION) != JNI_OK) {
        return;
    }

    jclass system = env->FindClass("java/lang/System");
    jfieldID outID = env->GetStaticFieldID(system, "out", "Ljava/io/PrintStream;");
    jobject out = env->GetStaticObjectField(system, outID);
    jclass printStream = env->FindClass("java/io/PrintStream");
    jmethodID printlnID = env->GetMethodID(printStream, "println", "(Ljava/lang/String;)V");
    jstring jstr = env->NewStringUTF(str);
    env->CallVoidMethod(out, printlnID, jstr);

    jmethodID flushID = env->GetMethodID(printStream, "flush", "()V");
    env->CallVoidMethod(out, flushID);
}