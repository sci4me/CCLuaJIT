extern "C" {
    static int __inext(lua_State *L) {
        lua_Number n = lua_tonumber(L, 2) + 1;
        lua_pushnumber(L, n);
        lua_pushnumber(L, n);
        lua_gettable(L, 1);
        return lua_isnil(L, -1) ? 0 : 2;
    }

    static void thread_abort_hook(lua_State *L, lua_Debug *ar) {
        lua_sethook(L, 0, 0, 0);
        luaL_where(L, 0);
        lua_pushfstring(L, "%saborted!", lua_tostring(L, -1));
        lua_error(L);
    }
}


struct LuaJITMachine {
    jobject obj;
    lua_State *L;
    lua_State *main_routine;

    bool init(JNIEnv *env, jobject obj, jstring cclj_version, jstring cc_version, jstring mc_version, jstring host, jstring default_settings, jlong random_seed) {
        this->obj = env->NewGlobalRef(obj);

        L = luaL_newstate();
        if(!L) return false;

        luaopen_base(L);
        luaopen_math(L);
        luaopen_string(L);
        luaopen_table(L);
        luaopen_bit(L);

        lua_getglobal(L, "bit");
        lua_setglobal(L, "bit32");

        lua_pushcfunction(L, __inext);
        lua_setglobal(L, "__inext");

        #define SET_GLOBAL_STRING(key, value) { const char *s = env->GetStringUTFChars(value, JNI_FALSE); lua_pushstring(L, s); lua_setglobal(L, key); env->ReleaseStringUTFChars(value, s); }
            SET_GLOBAL_STRING("_CCLJ_VERSION", cclj_version)
            SET_GLOBAL_STRING("_CC_VERSION", cc_version)
            SET_GLOBAL_STRING("_MC_VERSION", mc_version)
            SET_GLOBAL_STRING("_HOST", host)
            SET_GLOBAL_STRING("_CC_DEFAULT_SETTINGS", default_settings)
        #undef SET_GLOBAL_STRING

        lua_pushstring(L, "Lua 5.1");
        lua_setglobal(L, "_VERSION");

        lua_pushnil(L);
        lua_setglobal(L, "collectgarbage");
        lua_pushnil(L);
        lua_setglobal(L, "gcinfo");
        lua_pushnil(L);
        lua_setglobal(L, "newproxy");

        lua_getglobal(L, "math");
        lua_pushstring(L, "randomseed");
        lua_gettable(L, -2);
        lua_pushnumber(L, random_seed);
        lua_call(L, 1, 0);

        lua_pop(L, lua_gettop(L));

        return true;
    }

    void deinit(JNIEnv *env) {
        env->DeleteGlobalRef(obj);
        lua_close(L);
        memset(this, 0, sizeof(LuaJITMachine));
    }

    bool register_api(JNIEnv *env, jobject api) {
        int table = wrap_lua_object(env, L, api, obj);

        jobjectArray names = (jobjectArray) env->CallObjectMethod(api, get_names_id);
        jsize len = env->GetArrayLength(names);
        for(jsize i = 0; i < len; i++) {
            jstring name = (jstring) env->GetObjectArrayElement(names, i);
            char const* namec = env->GetStringUTFChars(name, JNI_FALSE);
            lua_pushvalue(L, table);
            lua_setglobal(L, namec);
            env->ReleaseStringUTFChars(name, namec);
            env->DeleteLocalRef(name);
        }

        lua_remove(L, table);

        return true;
    }

    bool load_bios(JNIEnv *env, jstring bios) {
        main_routine = lua_newthread(L);
        if(!main_routine) return false;

        const char *biosc = env->GetStringUTFChars(bios, JNI_FALSE);
        int err = luaL_loadbuffer(main_routine, biosc, strlen(biosc), "@bios.lua");
        env->ReleaseStringUTFChars(bios, biosc);
        if(err) return 0;

        return lua_isthread(L, -1);
    }

    jobjectArray handle_event(JNIEnv *env, jobjectArray args) {
        auto L = main_routine;

        int before = lua_gettop(L);
        to_lua_values(env, L, args, obj);
        int stat = lua_resume(L, env->GetArrayLength(args));
        int after = lua_gettop(L);

        jobjectArray results;
        if(stat == LUA_YIELD || stat == LUA_OK) {
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

    void abort() {
        lua_sethook(L, thread_abort_hook, LUA_MASKCALL | LUA_MASKRET | LUA_MASKCOUNT, 1);
    }
};