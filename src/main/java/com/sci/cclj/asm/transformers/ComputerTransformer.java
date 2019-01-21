package com.sci.cclj.asm.transformers;

import com.sci.cclj.asm.ITransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Arrays;
import java.util.Optional;

import static com.sci.cclj.asm.Constants.*;

public final class ComputerTransformer implements ITransformer {
    @Override
    public String clazz() {
        return COMPUTER_CLASS;
    }

    @Override
    public boolean transform(final ClassNode cn) {
        this.transformInitLua(cn);
        this.transformQueueEvent(cn);

        return true;
    }

    private void transformInitLua(final ClassNode cn) {
        final Optional<MethodNode> mno = cn.methods
                .stream()
                .filter(m -> m.name.equals("initLua"))
                .findFirst();

        if(!mno.isPresent()) {
            throw new RuntimeException("initLua not found in " + COMPUTER_CLASS);
        }

        final MethodNode mn = mno.get();

        boolean replacedNew = false;
        boolean replacedInvokeSpecial = false;

        for(final AbstractInsnNode insn : mn.instructions.toArray()) {
            if(insn.getOpcode() == Opcodes.NEW) {
                final TypeInsnNode tinsn = (TypeInsnNode) insn;
                if(tinsn.desc.equals(LUAJ_MACHINE_DESC)) {
                    ((TypeInsnNode) insn).desc = CCLJ_MACHINE_DESC;
                    replacedNew = true;
                }
            } else if(insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                final MethodInsnNode minsn = (MethodInsnNode) insn;
                if(minsn.owner.equals(LUAJ_MACHINE_DESC) && minsn.name.equals("<init>") && minsn.desc.equals(String.format("(L%s;)V", COMPUTER_DESC)) && !minsn.itf) {
                    minsn.owner = CCLJ_MACHINE_DESC;
                    minsn.desc = String.format("(L%s;)V", COMPUTER_DESC);
                    replacedInvokeSpecial = true;
                }
            }
        }

        if(!(replacedNew && replacedInvokeSpecial)) {
            throw new RuntimeException("Failed to transform " + COMPUTER_CLASS);
        }
    }

    private void transformQueueEvent(final ClassNode cn) {
        final Optional<MethodNode> mno = cn.methods
                .stream()
                .filter(m -> m.name.equals("queueEvent"))
                .findFirst();

        if(!mno.isPresent()) {
            throw new RuntimeException("queueEvent not found in " + COMPUTER_CLASS);
        }

        final MethodNode mn = mno.get();

        final Optional<MethodInsnNode> insno = Arrays.stream(mn.instructions.toArray())
                .filter(i -> i.getOpcode() == Opcodes.INVOKESTATIC)
                .map(i -> (MethodInsnNode) i)
                .filter(i -> i.name.equals("queueTask"))
                .filter(i -> i.owner.equals(COMPUTERTHREAD_DESC))
                .filter(i -> i.desc.equals(COMPUTERTHREAD_QUEUETASK_DESC))
                .findFirst();

        if(!insno.isPresent()) {
            throw new RuntimeException("Call to queueTask not found!");
        }

        final MethodInsnNode insn = insno.get();

        final InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PULLEVENTSCANNER_DESC, "isSpecialEvent", "(Ljava/lang/String;)Z", false));
        final LabelNode label = new LabelNode();
        list.add(new JumpInsnNode(Opcodes.IFEQ, label));
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new FieldInsnNode(Opcodes.GETFIELD, COMPUTER_DESC, "m_machine", String.format("L%s;", ILUAMACHINE_DESC)));
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new VarInsnNode(Opcodes.ALOAD, 2));
        list.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, ILUAMACHINE_DESC, "handleEvent", ILUAMACHINE_HANDLEEVENT_DESC, true));
        list.add(new InsnNode(Opcodes.RETURN));
        list.add(label);

        mn.instructions.insertBefore(mn.instructions.get(mn.instructions.indexOf(insn) - 2), list);
    }
}