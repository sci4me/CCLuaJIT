package com.sci.cclj.asm.transformers;

import com.sci.cclj.asm.ITransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

import static com.sci.cclj.asm.Constants.COMPUTERTHREAD_CLASS;
import static com.sci.cclj.asm.Constants.TASKSCHEDULER_DESC;

public final class ComputerThreadTransformer implements ITransformer {
    @Override
    public String clazz() {
        return COMPUTERTHREAD_CLASS;
    }

    @Override
    public boolean transform(final ClassNode cn) {
        this.transformFields(cn);
        this.transformMethods(cn);
        return true;
    }

    private void transformFields(final ClassNode cn) {
        cn.fields.clear();
    }

    private void transformMethods(final ClassNode cn) {
        cn.innerClasses.clear();

        final List<MethodNode> toRemove = new ArrayList<>();

        for(final MethodNode mn : cn.methods) {
            switch(mn.name) {
                case "<init>":
                    break;
                case "queueTask":
                case "start":
                case "stop":
                    mn.maxLocals = 0;
                    mn.maxStack = 1;

                    mn.instructions.clear();
                    mn.localVariables.clear();
                    mn.tryCatchBlocks.clear();

                    mn.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, TASKSCHEDULER_DESC, "INSTANCE", String.format("L%s;", TASKSCHEDULER_DESC)));

                    if(mn.name.equals("queueTask")) {
                        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));

                        mn.maxStack = 3;
                    }

                    mn.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TASKSCHEDULER_DESC, mn.name, mn.desc, false));
                    mn.instructions.add(new InsnNode(Opcodes.RETURN));
                    break;
                default:
                    toRemove.add(mn);
                    break;
            }
        }

        cn.methods.removeAll(toRemove);
    }
}