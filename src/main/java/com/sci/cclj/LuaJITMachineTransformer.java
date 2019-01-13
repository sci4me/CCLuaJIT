package com.sci.cclj;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Optional;

public final class LuaJITMachineTransformer implements IClassTransformer {
    private static final String COMPUTER_CLASS = "dan200.computercraft.core.computer.Computer";
    private static final String COMPUTER_DESC = "dan200/computercraft/core/computer/Computer";
    private static final String LUAJ_MACHINE_DESC = "dan200/computercraft/core/lua/LuaJLuaMachine";

    private static final String ICOMPUTER_DESC = "com/sci/cclj/IComputer";
    private static final String CCLJ_MACHINE_DESC = "com/sci/cclj/LuaJITMachine";

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
        } else {
            return basicClass;
        }
    }

    private void transformComputer(final ClassNode cn) {
        this.transformComputerInterfaces(cn);
        this.transformComputerInitLua(cn);
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
}