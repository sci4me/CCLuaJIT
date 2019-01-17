package com.sci.cclj.asm;

public final class Constants {
    public static final String COMPUTER_CLASS = "dan200.computercraft.core.computer.Computer";
    public static final String COMPUTER_DESC = COMPUTER_CLASS.replace('.', '/');
    public static final String COMPUTERTHREAD_CLASS = "dan200.computercraft.core.computer.ComputerThread";
    public static final String COMPUTERTHREAD_DESC = COMPUTERTHREAD_CLASS.replace('.', '/');
    public static final String ILUAMACHINE_DESC = "dan200/computercraft/core/lua/ILuaMachine";
    public static final String LUAJ_MACHINE_DESC = "dan200/computercraft/core/lua/LuaJLuaMachine";

    public static final String LUACONTEXT_CLASS = "com.sci.cclj.computer.LuaContext";
    public static final String CCLJ_MACHINE_DESC = "com/sci/cclj/computer/LuaJITMachine";
    public static final String TASKSCHEDULER_DESC = "com/sci/cclj/computer/TaskScheduler";

    public static final String COMPUTERTHREAD_QUEUETASK_DESC = "(Ldan200/computercraft/core/computer/ITask;Ldan200/computercraft/core/computer/Computer;)V";
    public static final String HANDLEEVENT_DESC = "(Ljava/lang/String;[Ljava/lang/Object;)V";

    public static final String ILUACONTEXT_DESC = "dan200/computercraft/api/lua/ILuaContext";
    public static final String PULLEVENT_DESC = "(Ljava/lang/String;)[Ljava/lang/Object;";

    private Constants() {
    }
}