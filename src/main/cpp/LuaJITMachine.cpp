#include <stdlib.h>
#include <string.h>

#include <jni.h>

//
// HACKS: This is to allow us to compile for all platforms using the same jni.h
//

#if defined(_WIN32) || defined(__APPLE__)
    #undef JNIEXPORT
    #undef JNIIMPORT
    #undef JNICALL

    #if defined(_WIN32)
        #define JNIEXPORT __declspec(dllexport)
        #define JNIIMPORT __declspec(dllimport)
        #define JNICALL __stdcall
    #elif defined(__APPLE__)
        #define JNIEXPORT __attribute__((visibility("default")))
        #define JNIIMPORT __attribute__((visibility("default")))
        #define JNICALL
    #else
        #error "unreachable"
    #endif
#endif

//
// End HACKS
//

extern "C" {
#include <lua.h>
#include <lualib.h>
#include <lauxlib.h>
}

/*
    Potential refactors:
        - change "obj" to "self"
        - for functions that take JNIEnv* and jobject as the first parameter, use a macro to do that in the preprocessor
*/

#define CCLJ_JNIVERSION JNI_VERSION_1_6
#define CCLJ_JNIEXPORT(rtype, name, ...) JNIEXPORT rtype JNICALL Java_com_sci_cclj_computer_LuaJITMachine_##name(JNIEnv *env, jobject obj, ##__VA_ARGS__)

//
// Forward Declarations
//

typedef struct JavaFN {
    jobject machine;
    jobject obj;
    int index;
} JavaFN;

static jclass get_class_global_ref(JNIEnv *env, const char *name);

static int finalize_jobject_ref(lua_State *L);
static void new_jobject_ref(JNIEnv *env, lua_State *L, jobject obj);

static void thread_interrupt_hook(lua_State *L, lua_Debug *ar);

static void map_to_table(JNIEnv *env, lua_State *L, jobject map, jobject valuesInProgress, jobject machine);
static void map_to_table(JNIEnv *env, lua_State *L, jobject map, jobject machine);
static jobject table_to_map(JNIEnv *env, lua_State *L, int objectsInProgress);
static jobject table_to_map(JNIEnv *env, lua_State *L);
static void to_lua_value(JNIEnv *env, lua_State *L, jobject value, jobject machine);
static void to_lua_values(JNIEnv *env, lua_State *L, jobjectArray values, jobject machine);
static jobject to_java_value(JNIEnv *env, lua_State *L);
static jobjectArray to_java_values(JNIEnv *env, lua_State *L, int n);

int try_abort(JNIEnv *env, jobject obj, lua_State *L);
static void thread_yield_request_handler_hook(lua_State *L, lua_Debug *ar);

static JavaFN* check_java_fn(lua_State *L);
static int invoke_java_fn(lua_State *L);
static int finalize_java_fn(lua_State *L);
static void new_java_fn(JNIEnv *env, lua_State *L, jobject obj, int index, jobject machine);
static int wrap_lua_object(JNIEnv *env, lua_State *L, jobject obj, jobject machine);

lua_State *get_lua_state(JNIEnv *env, jobject obj);
void set_lua_state(JNIEnv *env, jobject obj, lua_State *L);
lua_State *get_main_routine(JNIEnv *env, jobject obj);
void set_main_routine(JNIEnv *env, jobject obj, lua_State *L);

void sysout(jobject obj);
void sysout(const char *str);
static void dump_stack_element(lua_State *L, int i);
static void dump_stack(lua_State *L);

//
// Variables
//

static jclass object_class = 0;

static jclass number_class = 0;
static jmethodID doublevalue_id = 0;
static jmethodID intvalue_id = 0;

static jclass integer_class = 0;
static jmethodID integer_valueof_id = 0;

static jclass double_class = 0;
static jmethodID double_valueof_id = 0;

static jclass boolean_class = 0;
static jmethodID boolean_valueof_id = 0;
static jmethodID booleanvalue_id = 0;
static jobject boolean_false = 0;

static jclass string_class = 0;

static jclass bytearray_class = 0;

static jclass map_class = 0;
static jmethodID map_containskey_id = 0;
static jmethodID map_get_id = 0;
static jmethodID map_put_id = 0;
static jmethodID map_entryset_id = 0;

static jclass map_entry_class = 0;
static jmethodID map_entry_getkey_id = 0;
static jmethodID map_entry_getvalue_id = 0;

static jclass hashmap_class = 0;
static jmethodID hashmap_init_id = 0;

static jclass set_class = 0;
static jmethodID set_iterator_id = 0;

static jclass iterator_class = 0;
static jmethodID iterator_hasnext_id = 0;
static jmethodID iterator_next_id = 0;

static jclass identityhashmap_class = 0;
static jmethodID identityhashmap_init_id = 0;

static jclass interruptedexception_class = 0;
static jmethodID interruptedexception_init_id = 0;

static jclass machine_class = 0;
static jfieldID lua_state_id = 0;
static jfieldID main_routine_id = 0;
static jfieldID soft_abort_message_id = 0;
static jfieldID hard_abort_message_id = 0;
static jfieldID yield_requested_id = 0;

static jclass luacontext_class = 0;
static jmethodID luacontext_init_id = 0;

static jclass iluaobject_class = 0;
static jmethodID get_method_names_id = 0;
static jmethodID call_method_id = 0;

static jclass iluaapi_class = 0;
static jmethodID get_names_id = 0;

static JavaVM *jvm;
static int initialized = 0;

static const char REGISTRY_KEY_MACHINE = 'm';

//
// Main Code
//

static jclass get_class_global_ref(JNIEnv *env, const char *name) {
    jclass clazz = env->FindClass(name);
    if(!clazz) return 0;
    return (jclass) env->NewGlobalRef((jobject) clazz);
}

static int finalize_jobject_ref(lua_State *L) {
    jobject *obj = (jobject*) luaL_checkudata(L, 1, "jobject_ref");
    if(!obj) luaL_error(L, "Attempt to finalize finalized jobject_ref");

    JNIEnv *env;
    if(jvm->GetEnv((void**) &env, CCLJ_JNIVERSION) != JNI_OK) {
        luaL_error(L, "jobject_ref finalizer could not retrieve JNIEnv");
        return 0;
    }

    env->DeleteGlobalRef(*obj);
    *obj = 0;
    return 0;
}

static void new_jobject_ref(JNIEnv *env, lua_State *L, jobject obj) {
    jobject *ud = (jobject*) lua_newuserdata(L, sizeof(jobject));
    *ud = env->NewGlobalRef(obj);

    if(luaL_newmetatable(L, "jobject_ref")) {
        lua_pushstring(L, "__gc");
        lua_pushcfunction(L, finalize_jobject_ref);
        lua_settable(L, -3);
    }

    lua_setmetatable(L, -2);
}

static void thread_interrupt_hook(lua_State *L, lua_Debug *ar) {
    lua_sethook(L, 0, 0, 0);

    lua_pushlightuserdata(L, (void*)&REGISTRY_KEY_MACHINE);
    lua_rawget(L, LUA_REGISTRYINDEX);

    jobject obj = *((jobject*) luaL_checkudata(L, -1, "jobject_ref"));
    lua_pop(L, 1);

    JNIEnv *env;
    if(jvm->GetEnv((void**) &env, CCLJ_JNIVERSION) != JNI_OK) {
        luaL_error(L, "thread_interrupt_hook could not retrieve JNIEnv");
        return;
    }

    try_abort(env, obj, L);
}

static void map_to_table(JNIEnv *env, lua_State *L, jobject map, jobject valuesInProgress, jobject machine) {
    if(env->CallBooleanMethod(valuesInProgress, map_containskey_id, map)) {
        jobject bidx = env->CallObjectMethod(valuesInProgress, map_get_id, map);
        jint idx = env->CallStaticIntMethod(integer_class, intvalue_id, bidx);
        lua_pushvalue(L, idx);
        return;
    }

    lua_newtable(L);
    int idx = lua_gettop(L);

    env->CallObjectMethod(valuesInProgress, map_put_id, map, env->CallStaticObjectMethod(integer_class, integer_valueof_id, idx));

    jobject entrySet = env->CallObjectMethod(map, map_entryset_id);
    jobject iterator = env->CallObjectMethod(entrySet, set_iterator_id);

    while(env->CallBooleanMethod(iterator, iterator_hasnext_id)) {
        jobject next = env->CallObjectMethod(iterator, iterator_next_id);
        jobject key = env->CallObjectMethod(next, map_entry_getkey_id);
        jobject value = env->CallObjectMethod(next, map_entry_getvalue_id);

        to_lua_value(env, L, key, machine);
        to_lua_value(env, L, value, machine);
        lua_settable(L, idx);
    }
}

static void map_to_table(JNIEnv *env, lua_State *L, jobject map, jobject machine) {
    jobject vip = env->NewObject(identityhashmap_class, identityhashmap_init_id);
    map_to_table(env, L, map, vip, machine);
}

static jobject table_to_map(JNIEnv *env, lua_State *L, int objectsInProgress) {
    int t = lua_gettop(L);

    lua_pushvalue(L, t);
    lua_gettable(L, objectsInProgress);
    if(!lua_isnil(L, -1)) {
        jobject oip = (jobject) lua_touserdata(L, -1);
        lua_pop(L, 1);
        return oip;
    } else {
        lua_pop(L, 1);
    }

    jobject map = env->NewObject(hashmap_class, hashmap_init_id);
    lua_pushvalue(L, t);
    lua_pushlightuserdata(L, map);
    lua_settable(L, objectsInProgress);

    lua_pushnil(L);
    while(lua_next(L, t)) {
        jobject value = to_java_value(env, L);
        lua_pushvalue(L, -1);
        jobject key = to_java_value(env, L);
        env->CallObjectMethod(map, map_put_id, key, value);
    }

    lua_pop(L, 1);

    return map;
}

static jobject table_to_map(JNIEnv *env, lua_State *L) {
    lua_newtable(L);
    int oip = lua_gettop(L);
    lua_pushvalue(L, -2);
    jobject result = table_to_map(env, L, oip);
    lua_pop(L, 2);
    return result;
}

static void to_lua_value(JNIEnv *env, lua_State *L, jobject value, jobject machine) {
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
        map_to_table(env, L, value, machine);
    } else if(env->IsInstanceOf(value, iluaobject_class)) {
        wrap_lua_object(env, L, value, machine);
    } else {
        luaL_error(L, "Attempt to convert unrecognized Java value to a Lua value!");
    }
}

static void to_lua_values(JNIEnv *env, lua_State *L, jobjectArray values, jobject machine) {
    jsize len = env->GetArrayLength(values);
    for(jsize i = 0; i < len; i++) {
        jobject elem = env->GetObjectArrayElement(values, i);
        to_lua_value(env, L, elem, machine);
        env->DeleteLocalRef(elem);
    }
}

static jobject to_java_value(JNIEnv *env, lua_State *L) {
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
        env->SetObjectArrayElement(result, i - 1, to_java_value(env, L));
    }
    return result;
}

int try_abort(JNIEnv *env, jobject obj, lua_State *L) {
    int result = 0;
    jstring softAbortMessage = (jstring) env->GetObjectField(obj, soft_abort_message_id);
    if(softAbortMessage) {
        env->SetObjectField(obj, soft_abort_message_id, 0);
        env->SetObjectField(obj, hard_abort_message_id, 0);

        const char *cstr = env->GetStringUTFChars(softAbortMessage, JNI_FALSE);
        lua_pushstring(L, cstr);
        env->ReleaseStringUTFChars(softAbortMessage, cstr);

        lua_error(L);
        result = 1;
    }
    env->DeleteLocalRef(softAbortMessage);
    return result;
}

static void thread_yield_request_handler_hook(lua_State *L, lua_Debug *ar) {
    lua_sethook(L, 0, 0, 0);
    lua_yield(L, 0);
}

static JavaFN* check_java_fn(lua_State *L) {
    JavaFN *jfn = (JavaFN*) luaL_checkudata(L, 1, "JavaFN");
    if(!jfn->obj) luaL_error(L, "Attempt to access finalized JavaFN");
    return jfn;
}

static int invoke_java_fn(lua_State *L) {
    JNIEnv *env;
    if(jvm->GetEnv((void**) &env, CCLJ_JNIVERSION) != JNI_OK) {
        luaL_error(L, "JavaFN invocation wrapper could not retrieve JNIEnv");
        return 0;
    }

    JavaFN *jfn = (JavaFN*) lua_touserdata(L, lua_upvalueindex(1));
    jobject machine = jfn->machine;

    if(try_abort(env, machine, L)) {
        return 0;
    }

    jobject luaContext = env->NewObject(luacontext_class, luacontext_init_id, machine);

    jobjectArray arguments = to_java_values(env, L, lua_gettop(L));
    jobjectArray results = (jobjectArray) env->CallObjectMethod(jfn->obj, call_method_id, luaContext, jfn->index, arguments);

    if(env->GetBooleanField(machine, yield_requested_id)) {
        env->SetBooleanField(machine, yield_requested_id, 0);
        lua_sethook(L, thread_yield_request_handler_hook, LUA_MASKCOUNT, 1);
    }

    if(results) {
        to_lua_values(env, L, results, jfn->machine);
        return env->GetArrayLength(results);
    } else {
        return 0;
    }
}

static int finalize_java_fn(lua_State *L) {
    JavaFN *jfn = check_java_fn(L);

    JNIEnv *env;
    if(jvm->GetEnv((void**) &env, CCLJ_JNIVERSION) != JNI_OK) {
        luaL_error(L, "JavaFN finalizer could not retrieve JNIEnv");
        return 0;
    }

    env->DeleteGlobalRef(jfn->machine);
    env->DeleteGlobalRef(jfn->obj);

    jfn->obj = 0;
    return 0;
}

static void new_java_fn(JNIEnv *env, lua_State *L, jobject obj, int index, jobject machine) {
    JavaFN *jfn = (JavaFN*) lua_newuserdata(L, sizeof(JavaFN));
    jfn->machine = env->NewGlobalRef(machine);
    jfn->obj = env->NewGlobalRef(obj);
    jfn->index = index;

    if(luaL_newmetatable(L, "JavaFN")) {
        lua_pushstring(L, "__gc");
        lua_pushcfunction(L, finalize_java_fn);
        lua_settable(L, -3);
    }

    lua_setmetatable(L, -2);
}

static int wrap_lua_object(JNIEnv *env, lua_State *L, jobject obj, jobject machine) {
    lua_newtable(L);
    int table = lua_gettop(L);

    jobjectArray methodNames = (jobjectArray) env->CallObjectMethod(obj, get_method_names_id);
    jsize len = env->GetArrayLength(methodNames);
    for(jsize i = 0; i < len; i++) {
        jstring methodName = (jstring) env->GetObjectArrayElement(methodNames, i);
        const char *methodNamec = env->GetStringUTFChars(methodName, JNI_FALSE);

        lua_pushstring(L, methodNamec);
        new_java_fn(env, L, obj, i, machine);
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

    jclass system = get_class_global_ref(env, "java/lang/System");
    jfieldID outID = env->GetStaticFieldID(system, "out", "Ljava/io/PrintStream;");
    jobject out = env->GetStaticObjectField(system, outID);
    jclass printStream = get_class_global_ref(env, "java/io/PrintStream");
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

    jclass system = get_class_global_ref(env, "java/lang/System");
    jfieldID outID = env->GetStaticFieldID(system, "out", "Ljava/io/PrintStream;");
    jobject out = env->GetStaticObjectField(system, outID);
    jclass printStream = get_class_global_ref(env, "java/io/PrintStream");
    jmethodID printlnID = env->GetMethodID(printStream, "println", "(Ljava/lang/String;)V");
    jstring jstr = env->NewStringUTF(str);
    env->CallVoidMethod(out, printlnID, jstr);

    jmethodID flushID = env->GetMethodID(printStream, "flush", "()V");
    env->CallVoidMethod(out, flushID);
}

static void dump_stack_element(lua_State *L, int i) {
    char buf[32];

    int top = lua_gettop(L);
    int j = i - top - 1;
    int t = lua_type(L, i);
    switch (t) {
        case LUA_TSTRING:
            sprintf(buf, "%i (%i)  '%s'", i, j, lua_tostring(L, i));
            break;
        case LUA_TBOOLEAN:
            sprintf(buf, "%i (%i)  %s", i, j, lua_toboolean(L, i) ? "true" : "false");
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

static void dump_stack(lua_State *L) {
    sysout("stack: [");
    int top = lua_gettop(L);
    for (int i = top; i > 0; i--) {
        dump_stack_element(L, i);
    }
    sysout("]");
}

//
// JNI Code
//

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL JNI_OnLoad (JavaVM *vm, void *reserved) {
    jvm = vm;

	JNIEnv *env;
	if (vm->GetEnv((void **) &env, CCLJ_JNIVERSION) != JNI_OK) {
		return CCLJ_JNIVERSION;
	}

    if(!(object_class = get_class_global_ref(env, "java/lang/Object"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(number_class = get_class_global_ref(env, "java/lang/Number")) ||
        !(doublevalue_id = env->GetMethodID(number_class, "doubleValue", "()D")) ||
        !(intvalue_id = env->GetMethodID(number_class, "intValue", "()I"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(integer_class = get_class_global_ref(env, "java/lang/Integer")) ||
        !(integer_valueof_id = env->GetStaticMethodID(integer_class, "valueOf", "(I)Ljava/lang/Integer;"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(double_class = get_class_global_ref(env, "java/lang/Double")) ||
        !(double_valueof_id = env->GetStaticMethodID(double_class, "valueOf", "(D)Ljava/lang/Double;"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(boolean_class = get_class_global_ref(env, "java/lang/Boolean")) ||
        !(boolean_valueof_id = env->GetStaticMethodID(boolean_class, "valueOf", "(Z)Ljava/lang/Boolean;")) ||
        !(booleanvalue_id = env->GetMethodID(boolean_class, "booleanValue", "()Z")) ||
        !(boolean_false = env->NewGlobalRef(env->GetStaticObjectField(boolean_class, env->GetStaticFieldID(boolean_class, "FALSE", "Ljava/lang/Boolean;"))))) {
        return CCLJ_JNIVERSION;
    }    

    if(!(string_class = get_class_global_ref(env, "java/lang/String"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(bytearray_class = get_class_global_ref(env, "[B"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(map_class = get_class_global_ref(env, "java/util/Map")) ||
        !(map_containskey_id = env->GetMethodID(map_class, "containsKey", "(Ljava/lang/Object;)Z")) ||
        !(map_get_id = env->GetMethodID(map_class, "get", "(Ljava/lang/Object;)Ljava/lang/Object;")) ||
        !(map_put_id = env->GetMethodID(map_class, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) ||
        !(map_entryset_id = env->GetMethodID(map_class, "entrySet", "()Ljava/util/Set;"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(map_entry_class = get_class_global_ref(env, "java/util/Map$Entry")) ||
        !(map_entry_getkey_id = env->GetMethodID(map_entry_class, "getKey", "()Ljava/lang/Object;")) ||
        !(map_entry_getvalue_id = env->GetMethodID(map_entry_class, "getValue", "()Ljava/lang/Object;"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(hashmap_class = get_class_global_ref(env, "java/util/HashMap")) ||
        !(hashmap_init_id = env->GetMethodID(hashmap_class, "<init>", "()V"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(set_class = get_class_global_ref(env, "java/util/Set")) ||
        !(set_iterator_id = env->GetMethodID(set_class, "iterator", "()Ljava/util/Iterator;"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(iterator_class = get_class_global_ref(env, "java/util/Iterator")) ||
        !(iterator_hasnext_id = env->GetMethodID(iterator_class, "hasNext", "()Z")) ||
        !(iterator_next_id = env->GetMethodID(iterator_class, "next", "()Ljava/lang/Object;"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(identityhashmap_class = get_class_global_ref(env, "java/util/IdentityHashMap")) ||
        !(identityhashmap_init_id = env->GetMethodID(identityhashmap_class, "<init>", "()V"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(interruptedexception_class = get_class_global_ref(env, "java/lang/InterruptedException")) ||
        !(interruptedexception_init_id = env->GetMethodID(interruptedexception_class, "<init>", "(Ljava/lang/String;)V"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(machine_class = get_class_global_ref(env, "com/sci/cclj/computer/LuaJITMachine")) ||
        !(lua_state_id = env->GetFieldID(machine_class, "luaState", "J")) ||
        !(main_routine_id = env->GetFieldID(machine_class, "mainRoutine", "J")) ||
        !(soft_abort_message_id = env->GetFieldID(machine_class, "softAbortMessage", "Ljava/lang/String;")) ||
        !(hard_abort_message_id = env->GetFieldID(machine_class, "hardAbortMessage", "Ljava/lang/String;")) ||
        !(yield_requested_id = env->GetFieldID(machine_class, "yieldRequested", "Z"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(luacontext_class = get_class_global_ref(env, "com/sci/cclj/computer/LuaContext")) ||
        !(luacontext_init_id = env->GetMethodID(luacontext_class, "<init>", "(Lcom/sci/cclj/computer/LuaJITMachine;)V"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(iluaapi_class = get_class_global_ref(env, "dan200/computercraft/core/apis/ILuaAPI")) ||
        !(get_names_id = env->GetMethodID(iluaapi_class, "getNames", "()[Ljava/lang/String;"))) {
        return CCLJ_JNIVERSION;
    }

    if(!(iluaobject_class = get_class_global_ref(env, "dan200/computercraft/api/lua/ILuaObject")) ||
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

    if(object_class)                    env->DeleteGlobalRef(object_class);
    if(number_class)                    env->DeleteGlobalRef(number_class);
    if(integer_class)                   env->DeleteGlobalRef(integer_class);
    if(double_class)                    env->DeleteGlobalRef(double_class);
    if(boolean_class)                   env->DeleteGlobalRef(boolean_class);
    if(boolean_false)                   env->DeleteGlobalRef(boolean_false);
    if(string_class)                    env->DeleteGlobalRef(string_class);
    if(bytearray_class)                 env->DeleteGlobalRef(bytearray_class);
    if(map_class)                       env->DeleteGlobalRef(map_class);
    if(hashmap_class)                   env->DeleteGlobalRef(hashmap_class);
    if(set_class)                       env->DeleteGlobalRef(set_class);
    if(iterator_class)                  env->DeleteGlobalRef(iterator_class);
    if(identityhashmap_class)           env->DeleteGlobalRef(identityhashmap_class);
    if(interruptedexception_class)      env->DeleteGlobalRef(interruptedexception_class);
    if(machine_class)                   env->DeleteGlobalRef(machine_class);
    if(luacontext_class)                env->DeleteGlobalRef(luacontext_class);
    if(iluaapi_class)                   env->DeleteGlobalRef(iluaapi_class);
    if(iluaobject_class)                env->DeleteGlobalRef(iluaobject_class); 
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

    lua_pushlightuserdata(L, (void*)&REGISTRY_KEY_MACHINE);
    new_jobject_ref(env, L, obj);
    lua_rawset(L, LUA_REGISTRYINDEX);

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

    int table = wrap_lua_object(env, L, api, obj);    

    jobjectArray names = (jobjectArray) env->CallObjectMethod(api, get_names_id);
    jsize len = env->GetArrayLength(names);
    for(jsize i = 0; i < len; i++) {
        jstring name = (jstring) env->GetObjectArrayElement(names, i);
        const char *namec = env->GetStringUTFChars(name, JNI_FALSE);
        lua_pushvalue(L, table);
        lua_setglobal(L, namec);
        env->ReleaseStringUTFChars(name, namec);
        env->DeleteLocalRef(name);
    }

    lua_remove(L, table);

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

    int before = lua_gettop(L);
    to_lua_values(env, L, args, obj);
    int stat = lua_resume(L, env->GetArrayLength(args));
    int after = lua_gettop(L);

    jobjectArray results;
    if(stat == LUA_YIELD || stat == 0) {
        int nresults = after - before;
        if(nresults > 0) {
            results = to_java_values(env, L, nresults);
        } else {
            results = env->NewObjectArray(0, object_class, 0);
        }
    } else {
        results = env->NewObjectArray(2, object_class, 0);
        env->SetObjectArrayElement(results, 0, boolean_false);
        env->SetObjectArrayElement(results, 1, to_java_value(env, L));
    }

    return results;
}

CCLJ_JNIEXPORT(void, abort) {
    lua_State *L = get_main_routine(env, obj);
    lua_sethook(L, thread_interrupt_hook, LUA_MASKCOUNT, 1);
}

#ifdef __cplusplus
}
#endif