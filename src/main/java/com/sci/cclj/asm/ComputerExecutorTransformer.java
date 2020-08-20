package com.sci.cclj.asm;

import com.google.common.collect.Sets;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import static com.sci.cclj.asm.Constants.*;

public final class ComputerExecutorTransformer implements ITransformer<MethodNode> {
    private static final String CLASS_NAME = "dan200.computercraft.core.computer.ComputerExecutor";
    private static final String[] LABELS = new String[]{"CCLuaJIT_ComputerExecutorTransformer"};

    @Nonnull
    @Override
    public MethodNode transform(final MethodNode n, final ITransformerVotingContext ctx) {
        switch (n.name) {
            case "createLuaMachine":
                this.transformCreateLuaMachine(n);
                break;
            case "queueEvent":
                this.transformQueueEvent(n);
                break;
            case "abort":
                this.transformAbort(n);
                break;
        }
        return n;
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
        list.add(new FieldInsnNode(Opcodes.GETFIELD, CLASS_NAME.replace('.', '/'), "machine", String.format("L%s;", ILUAMACHINE_DESC)));
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

    @Nonnull
    @Override
    public TransformerVoteResult castVote(final ITransformerVotingContext ctx) {
        return TransformerVoteResult.YES;
    }

    @Nonnull
    @Override
    public Set<Target> targets() {
        return Sets.newHashSet(
                Target.targetMethod(
                        CLASS_NAME,
                        "createLuaMachine",
                        "()Ldan200/computercraft/core/lua/ILuaMachine;"
                ),
                Target.targetMethod(
                        CLASS_NAME,
                        "queueEvent",
                        "(Ljava/lang/String;[Ljava/lang/Object;)V"
                ),
                Target.targetMethod(
                        CLASS_NAME,
                        "abort",
                        "()V"
                )
        );
    }

    @Override
    public String[] labels() {
        return LABELS;
    }
}