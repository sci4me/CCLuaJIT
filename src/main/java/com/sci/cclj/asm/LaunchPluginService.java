package com.sci.cclj.asm;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

import static com.sci.cclj.asm.Constants.*;
import static com.sci.cclj.asm.Constants.CCLJ_MACHINE_DESC;

public final class LaunchPluginService implements ILaunchPluginService {
    private static final EnumSet<Phase> YAY = EnumSet.of(Phase.BEFORE);
    private static final EnumSet<Phase> NAY = EnumSet.noneOf(Phase.class);

    @Override
    public String name() {
        return "CCLuaJIT_LaunchPluginService";
    }

    @Override
    public EnumSet<Phase> handlesClass(final Type clazz, boolean empty) {
        return empty ? NAY : YAY;
    }

    @Override
    public boolean processClass(final Phase phase, final ClassNode cn, final Type clazz) {
        // TODO: de-uglify this
        if(cn.name.equals(COMPUTER_EXECUTOR_DESC)) {
            int c = 0;
            for(final MethodNode mn : cn.methods) {
                switch (mn.name) {
                    case "createLuaMachine":
                        this.transformCreateLuaMachine(mn);
                        c++;
                        break;
                    case "queueEvent":
                        this.transformQueueEvent(mn);
                        c++;
                        break;
                    case "abort":
                        this.transformAbort(mn);
                        c++;
                        break;
                }
            }
            if(c != 3) throw new RuntimeException("CCLuaJIT class transformation failed!");
            return true;
        }
        return false;
    }

    private void transformCreateLuaMachine(final MethodNode n) {
        boolean replacedNew = false;
        boolean replacedInvokeSpecial = false;

        for(final AbstractInsnNode insn : n.instructions.toArray()) {
            if(insn.getOpcode() == Opcodes.NEW) {
                final TypeInsnNode tinsn = (TypeInsnNode) insn;
                if(tinsn.desc.equals(COBALT_MACHINE_DESC)) {
                    ((TypeInsnNode) insn).desc = CCLJ_MACHINE_DESC;
                    replacedNew = true;
                }
            } else if(insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                final MethodInsnNode minsn = (MethodInsnNode) insn;
                if(minsn.owner.equals(COBALT_MACHINE_DESC) && minsn.name.equals("<init>") && minsn.desc.equals(String.format("(L%s;L%s;)V", COMPUTER_DESC, TIMEOUT_STATE_DESC)) && !minsn.itf) {
                    minsn.owner = CCLJ_MACHINE_DESC;
                    replacedInvokeSpecial = true;
                }
            }

            if(replacedNew && replacedInvokeSpecial) break;
        }

        if(!(replacedNew && replacedInvokeSpecial)) {
            throw new RuntimeException("Failed to replace CobaltLuaMachine instantiation!");
        }
    }

    private void transformQueueEvent(final MethodNode n) {
        final InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CCLJ_MACHINE_DESC, "isSpecialEvent", "(Ljava/lang/String;)Z", false));
        final LabelNode label = new LabelNode();
        list.add(new JumpInsnNode(Opcodes.IFEQ, label));
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new FieldInsnNode(Opcodes.GETFIELD, COMPUTER_EXECUTOR_DESC, "machine", String.format("L%s;", ILUAMACHINE_DESC)));
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new VarInsnNode(Opcodes.ALOAD, 2));
        list.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, ILUAMACHINE_DESC, "handleEvent", "(Ljava/lang/String;[Ljava/lang/Object;)Ldan200/computercraft/core/lua/MachineResult;", true));
        list.add(new InsnNode(Opcodes.RETURN));
        list.add(label);

        n.instructions.insertBefore(n.instructions.get(0), list);
    }

    private void transformAbort(final MethodNode n) {
        final Optional<AbstractInsnNode> mi = Arrays.stream(n.instructions.toArray())
                .filter(insn -> {
                    if (insn.getOpcode() == Opcodes.INVOKEINTERFACE) {
                        final MethodInsnNode minsn = (MethodInsnNode) insn;
                        return minsn.owner.equals(ILUAMACHINE_DESC) && minsn.name.equals("close") && minsn.desc.equals("()V");
                    }
                    return false;
                })
                .findFirst();

        if(!mi.isPresent()) throw new RuntimeException("Failed to replace call to ILuaMachine::close");

        final InsnList rep = new InsnList();
        rep.add(new TypeInsnNode(Opcodes.CHECKCAST, CCLJ_MACHINE_DESC));
        rep.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CCLJ_MACHINE_DESC, "abort", "()V", false));

        final AbstractInsnNode original = mi.get();
        n.instructions.insert(original, rep);
        n.instructions.remove(original);
    }
}