package com.sci.cclj.asm;

import com.sci.cclj.computer.LuaJITMachine;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

// @TODO: Modify OSAPI queueEvent to prepend a sentinel object to arguments in case event filter is a special event
// @TODO: Once we're transforming >1 class, use a Set<String> to check if class 'name' is being transformed

public final class CCLJClassTransformer implements IClassTransformer {
    private static final Set<String> ANALYSIS_EXCLUSIONS;

    static {
        final Set<String> exclusions = new HashSet<>();
        exclusions.add("com.sci.cclj.computer.LuaContext");
        ANALYSIS_EXCLUSIONS = exclusions;
    }

    private static final String COMPUTER_CLASS = "dan200.computercraft.core.computer.Computer";
    private static final String TERMINAL_CLASS = "dan200.computercraft.core.terminal.Terminal";
    private static final String COMPUTER_DESC = "dan200/computercraft/core/computer/Computer";
    private static final String LUAJ_MACHINE_DESC = "dan200/computercraft/core/lua/LuaJLuaMachine";
    private static final String ILUACONTEXT_DESC = "dan200/computercraft/api/lua/ILuaContext";
    private static final String COMPUTERTHREAD_DESC = "dan200/computercraft/core/computer/ComputerThread";
    private static final String ILUAMACHINE_DESC = "dan200/computercraft/core/lua/ILuaMachine";

    private static final String PULLEVENT_DESC = "(Ljava/lang/String;)[Ljava/lang/Object;";
    private static final String COMPUTERTHREAD_QUEUETASK_DESC = "(Ldan200/computercraft/core/computer/ITask;Ldan200/computercraft/core/computer/Computer;)V";
    private static final String HANDLEEVENT_DESC = "(Ljava/lang/String;[Ljava/lang/Object;)V";

    private static final String ICOMPUTER_DESC = "com/sci/cclj/computer/IComputer";
    private static final String CCLJ_MACHINE_DESC = "com/sci/cclj/computer/LuaJITMachine";

    @Override
    public byte[] transform(final String name, final String transformedName, final byte[] basicClass) {
        if(name.equals(COMPUTER_CLASS)) {
            final ClassNode cn = new ClassNode();
            final ClassReader cr = new ClassReader(basicClass);
            cr.accept(cn, 0);

            this.transformComputer(cn);

            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            return cw.toByteArray();
        } else if(name.equals(TERMINAL_CLASS)) {
            final ClassNode cn = new ClassNode();
            final ClassReader cr = new ClassReader(basicClass);
            cr.accept(cn, 0);

            this.transformTerminal(cn);

            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            return cw.toByteArray();
        } else {
            if(!CCLJClassTransformer.ANALYSIS_EXCLUSIONS.contains(name)) {
                final ClassNode cn = new ClassNode();
                final ClassReader cr = new ClassReader(basicClass);
                cr.accept(cn, 0);

                this.scanForPullEvents(cn);
            }

            return basicClass;
        }
    }

    private void transformTerminal(final ClassNode cn) {
        final Optional<MethodNode> mno = cn.methods
                .stream()
                .filter(m -> m.name.equals("write"))
                .findFirst();

        if(!mno.isPresent()) {
            throw new RuntimeException("write not found in " + TERMINAL_CLASS);
        }

        final MethodNode mn = mno.get();

        final InsnList list = new InsnList();
        list.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V"));

        mn.instructions.insertBefore(mn.instructions.get(0), list);
    }

    private void scanForPullEvents(final ClassNode cn) {
        for(final MethodNode mn : cn.methods) {
            for(final AbstractInsnNode insn : mn.instructions.toArray()) {
                if(insn instanceof MethodInsnNode) {
                    final MethodInsnNode minsn = (MethodInsnNode) insn;
                    if(minsn.owner.equals(ILUACONTEXT_DESC) &&
                            (minsn.name.equals("pullEvent") || minsn.name.equals("pullEventRaw")) &&
                            minsn.desc.equals(PULLEVENT_DESC)) {
                        final AbstractInsnNode prev = mn.instructions.get(mn.instructions.indexOf(minsn) - 1);
                        if(prev.getOpcode() == Opcodes.LDC) {
                            LuaJITMachine.registerSpecialEvent((String) ((LdcInsnNode) prev).cst);
                        } else {
                            throw new RuntimeException("Found call to pullEvent(raw?) but could not determine its event filter!");
                        }
                    }
                }
            }
        }
    }

    private void transformComputer(final ClassNode cn) {
        this.transformComputerInterfaces(cn);
        this.transformComputerInitLua(cn);
        this.transformComputerQueueEvent(cn);
    }

    private void transformComputerInterfaces(final ClassNode cn) {
        cn.interfaces.add(ICOMPUTER_DESC);
    }

    private void transformComputerInitLua(final ClassNode cn) {
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
                    minsn.desc = String.format("(L%s;)V", ICOMPUTER_DESC);
                    replacedInvokeSpecial = true;
                }
            }
        }

        if(!(replacedNew && replacedInvokeSpecial)) {
            throw new RuntimeException("Failed to transformComputer " + COMPUTER_CLASS);
        }
    }

    private void transformComputerQueueEvent(final ClassNode cn) {
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
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CCLJ_MACHINE_DESC, "isSpecialEvent", "(Ljava/lang/String;)Z", false));
        final LabelNode label = new LabelNode();
        list.add(new JumpInsnNode(Opcodes.IFEQ, label));
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new FieldInsnNode(Opcodes.GETFIELD, COMPUTER_DESC, "m_machine", String.format("L%s;", ILUAMACHINE_DESC)));
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new VarInsnNode(Opcodes.ALOAD, 2));
        list.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, ILUAMACHINE_DESC, "handleEvent", HANDLEEVENT_DESC));
        list.add(label);

        mn.instructions.insertBefore(mn.instructions.get(mn.instructions.indexOf(insn) - 2), list);
    }
}