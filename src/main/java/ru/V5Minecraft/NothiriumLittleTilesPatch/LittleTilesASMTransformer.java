package ru.V5Minecraft.NothiriumLittleTilesPatch;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class LittleTilesASMTransformer implements IClassTransformer {
    private static final String TASK_COMPILE = "meldexun/nothirium/mc/renderer/chunk/RenderChunkTaskCompile";
    private static final String ABSTRACT_TASK = "meldexun/nothirium/renderer/chunk/AbstractRenderChunkTask";
    private static final String ABSTRACT_CHUNK = "meldexun/nothirium/renderer/chunk/AbstractRenderChunk";
    private static final String SECTION_POS = "meldexun/nothirium/util/SectionPos";
    private static final String INTEGRATION = "ru/V5Minecraft/NothiriumLittleTilesPatch/NothiriumPatches/LittleTilesIntegration";
    private static final String VISIBILITY_GRAPH = "meldexun/nothirium/util/VisibilityGraph";
    private static final String IBLOCK_STATE = "net/minecraft/block/state/IBlockState";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if ("meldexun.nothirium.mc.renderer.chunk.RenderChunkTaskCompile".equals(transformedName)) {
            try {
                byte[] result = patchRenderChunkTaskCompile(basicClass);
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return basicClass;
            }
        }

        return basicClass;
    }

    private byte[] patchRenderChunkTaskCompile(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        for (MethodNode method : classNode.methods) {
            if (method.name.equals("compileSection") && method.desc.equals("(Lnet/minecraft/client/renderer/RegionRenderCacheBuilder;)Lmeldexun/nothirium/api/renderer/chunk/RenderChunkTaskResult;")) {
                patchCompileSection(method);
            }

            if (method.name.equals("renderBlockState") && method.desc.contains("IBlockState")) {
                patchRenderBlockState(method);
            }
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private void patchCompileSection(MethodNode method) {
        AbstractInsnNode target = null;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                MethodInsnNode min = (MethodInsnNode) insn;
                if (min.owner.equals(VISIBILITY_GRAPH) && min.name.equals("compute")) {
                    target = insn;
                    break;
                }
            }
        }
        if (target == null) {
            return;
        }

        InsnList patch = new InsnList();
        patch.add(new VarInsnNode(Opcodes.ALOAD, 0));
        patch.add(new FieldInsnNode(Opcodes.GETFIELD, TASK_COMPILE, "chunkCache", "Lnet/minecraft/world/IBlockAccess;"));
        patch.add(new VarInsnNode(Opcodes.ALOAD, 0));
        patch.add(new FieldInsnNode(Opcodes.GETFIELD, ABSTRACT_TASK, "renderChunk", "L" + ABSTRACT_CHUNK + ";"));
        patch.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ABSTRACT_CHUNK, "getPos", "()L" + SECTION_POS + ";", false));
        patch.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SECTION_POS, "getBlockX", "()I", false));
        patch.add(new VarInsnNode(Opcodes.ALOAD, 0));
        patch.add(new FieldInsnNode(Opcodes.GETFIELD, ABSTRACT_TASK, "renderChunk", "L" + ABSTRACT_CHUNK + ";"));
        patch.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ABSTRACT_CHUNK, "getPos", "()L" + SECTION_POS + ";", false));
        patch.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SECTION_POS, "getBlockY", "()I", false));
        patch.add(new VarInsnNode(Opcodes.ALOAD, 0));
        patch.add(new FieldInsnNode(Opcodes.GETFIELD, ABSTRACT_TASK, "renderChunk", "L" + ABSTRACT_CHUNK + ";"));
        patch.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ABSTRACT_CHUNK, "getPos", "()L" + SECTION_POS + ";", false));
        patch.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SECTION_POS, "getBlockZ", "()I", false));
        patch.add(new VarInsnNode(Opcodes.ALOAD, 1));
        patch.add(new MethodInsnNode(Opcodes.INVOKESTATIC, INTEGRATION, "appendLittleTilesData", "(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraft/client/renderer/RegionRenderCacheBuilder;)V", false));

        method.instructions.insertBefore(target, patch);
    }

    private void patchRenderBlockState(MethodNode method) {
        method.instructions.insert(method.instructions.getFirst(), new InsnList() {
            {
                add(new VarInsnNode(Opcodes.ALOAD, 1));
                add(new MethodInsnNode(Opcodes.INVOKESTATIC, INTEGRATION, "beginRenderBlock", "(L" + IBLOCK_STATE + ";)V", false));
            }
        });

        AbstractInsnNode returnInsn = null;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.RETURN) {
                returnInsn = insn;
            }
        }
        if (returnInsn == null) return;

        LabelNode returnLabel = new LabelNode();
        InsnList endPatch = new InsnList();
        endPatch.add(returnLabel);
        endPatch.add(new MethodInsnNode(Opcodes.INVOKESTATIC, INTEGRATION, "endRenderBlock", "()V", false));
        method.instructions.insertBefore(returnInsn, endPatch);

        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
            MethodInsnNode min = (MethodInsnNode) insn;
            if (!min.owner.equals(VISIBILITY_GRAPH) || !min.name.equals("setOpaque")) continue;
            method.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, INTEGRATION, "setOpaque", "(L" + VISIBILITY_GRAPH + ";IIILmeldexun/nothirium/util/Direction;)V", false));
        }

        for (AbstractInsnNode insn : method.instructions.toArray()) {
            int op = insn.getOpcode();
            if (op != Opcodes.INVOKEVIRTUAL && op != Opcodes.INVOKEINTERFACE) continue;
            MethodInsnNode min = (MethodInsnNode) insn;
            if (!min.name.equals("doesSideBlockRendering") && !min.name.equals("func_176225_a")) continue;
            method.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, INTEGRATION, "doesSideBlockRendering", "(L" + IBLOCK_STATE + ";Lnet/minecraft/world/IBlockAccess;" + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;)Z", false));
        }

        AbstractInsnNode canRenderInLayer = null;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL && insn.getOpcode() != Opcodes.INVOKEINTERFACE) continue;
            MethodInsnNode min = (MethodInsnNode) insn;
            if (min.name.equals("canRenderInLayer") || min.name.equals("func_193383_a")) {
                canRenderInLayer = insn;
                break;
            }
        }
        if (canRenderInLayer == null) return;

        AbstractInsnNode loopLabel = canRenderInLayer.getPrevious();
        while (loopLabel != null && !(loopLabel instanceof LabelNode)) {
            loopLabel = loopLabel.getPrevious();
        }
        if (loopLabel == null) return;

        InsnList skipPatch = new InsnList();
        skipPatch.add(new VarInsnNode(Opcodes.ALOAD, 1));
        skipPatch.add(new MethodInsnNode(Opcodes.INVOKESTATIC, INTEGRATION, "shouldSkipBlockRender", "(L" + IBLOCK_STATE + ";)Z", false));
        skipPatch.add(new JumpInsnNode(Opcodes.IFNE, returnLabel));
        method.instructions.insertBefore(loopLabel, skipPatch);
    }
}
