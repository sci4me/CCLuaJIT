function initializeCoreMod() {
    var COBALT_MACHINE_DESC         = "dan200/computercraft/core/lua/CobaltLuaMachine";
    var COMPUTER_DESC               = "dan200/computercraft/core/computer/Computer";
    var TIMEOUT_STATE_DESC          = "dan200/computercraft/core/computer/TimeoutState";
    var ILUAMACHINE_DESC            = "dan200/computercraft/core/lua/ILuaMachine";
    var COMPUTER_EXECUTOR_DESC      = "dan200/computercraft/core/computer/ComputerExecutor";
    var COMPUTER_EXECUTOR_NAME      = COMPUTER_EXECUTOR_DESC.replace("/", ".");

    var CCLJ_MACHINE_DESC           = "com/sci/cclj/computer/LuaJITMachine";

    var ASM                         = Java.type("net.minecraftforge.coremod.api.ASMAPI");
    var Opcodes                     = Java.type("org.objectweb.asm.Opcodes");
    var InsnList                    = Java.type("org.objectweb.asm.tree.InsnList");
    var InsnNode                    = Java.type("org.objectweb.asm.tree.InsnNode");
    var LabelNode                   = Java.type("org.objectweb.asm.tree.LabelNode");
    var VarInsnNode                 = Java.type("org.objectweb.asm.tree.VarInsnNode");
    var JumpInsnNode                = Java.type("org.objectweb.asm.tree.JumpInsnNode");
    var MethodInsnNode              = Java.type("org.objectweb.asm.tree.MethodInsnNode");
    var FieldInsnNode               = Java.type("org.objectweb.asm.tree.FieldInsnNode");
    var TypeInsnNode                = Java.type("org.objectweb.asm.tree.TypeInsnNode");

    return {
        "ComputerExecutor::createLuaMachine": {
            "target": {
                "type": "METHOD",
                "class": COMPUTER_EXECUTOR_NAME,
                "methodName": "createLuaMachine",
                "methodDesc": "()L" + ILUAMACHINE_DESC + ";"
            },
            "transformer": function(mn) {
                var replacedNew = false;
                var replacedInvokeSpecial = false;

                var insns = mn.instructions;
                for(var i = 0; i < insns.size(); i++) {
                    var insn = insns.get(i);
                    var op = insn.getOpcode();

                    if(op == Opcode.NEW && insn.desc.equals(COBALT_MACHINE_DESC)) {
                        insn.desc = CCLJ_MACHINE_DESC;
                        replacedNew = true;
                        continue;
                    } else if(op == Opcode.INVOKESPECIAL) {
                        var expectedDesc = "(L" + COMPUTER_DESC + ";L" + TIMEOUT_STATE_DESC + ";)V";
                        if(insn.owner.equals(COBALT_MACHINE_DESC) &&
                                insn.name.equals("<init>") &&
                                insn.desc.equals(expectedDesc) &&
                                !insn.itf) {
                            insn.owner = CCLJ_MACHINE_DESC;
                            replacedInvokeSpecial = true;
                        }
                    }

                    if(replacedNew && replacedInvokeSpecial) break;
                }

                if(!(replacedNew && replacedInvokeSpecial)) {
                    throw "Failed to replace instantiation of CobaltLuaMachine";
                }

                return mn;
            }
        },
        "ComputerExecutor::queueEvent": {
            "target": {
                "type": "METHOD",
                "class": COMPUTER_EXECUTOR_NAME,
                "methodName": "queueEvent",
                "methodDesc": "(Ljava/lang/String;[Ljava/lang/Object;)V"
            },
            "transformer": function(mn) {
                var list = new InsnList();
                list.add(new VarInsnNode(Opcodes.ALOAD, 1));
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CCLJ_MACHINE_DESC, "isSpecialEvent", "(Ljava/lang/String;)Z", false));
                var label = new LabelNode();
                list.add(new JumpInsnNode(Opcodes.IFEQ, label));
                list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                list.add(new FieldInsnNode(Opcodes.GETFIELD, COMPUTER_EXECUTOR_DESC, "machine", "L" + ILUAMACHINE_DESC + ";"));
                list.add(new VarInsnNode(Opcodes.ALOAD, 1));
                list.add(new VarInsnNode(Opcodes.ALOAD, 2));
                var desc = "(Ljava/lang/String;[Ljava/lang/Object;)Ldan200/computercraft/core/lua/MachineResult;";
                list.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, ILUAMACHINE_DESC, "handleEvent", desc, true));
                list.add(new InsnNode(Opcodes.RETURN));
                list.add(label);
                mn.instructions.insertBefore(mn.instructions.get(0), list);
                return mn;
            }
        },
        "ComputerExecutor::abort": {
            "target": {
                "type": "METHOD",
                "class": COMPUTER_EXECUTOR_NAME,
                "methodName": "abort",
                "methodDesc": "()V"
            },
            "transformer": function(mn) {
                var mi = null;

                var insns = mn.instructions;
                for(var i = 0; i < insns.size(); i++) {
                    var insn = insns.get(i);
                    if(insn.getOpcode() == Opcodes.INVOKEINTERFACE &&
                            insn.owner.equals(ILUAMACHINE_DESC) &&
                            insn.name.equals("close") &&
                            insn.desc.equals("()V") &&
                            insn.itf) {
                        mi = insn;
                        break;
                    }
                }

                if(mi == null) throw "Failed to replace call to ILuaMachine::close";

                var rep = new InsnList();
                rep.add(new TypeInsnNode(Opcodes.CHECKCAST, CCLJ_MACHINE_DESC));
                rep.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CCLJ_MACHINE_DESC, "abort", "()V", false));

                insns.insert(mi, rep);
                insns.remove(mi);

                return mn;
            }
        }
    };
}