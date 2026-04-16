package ru.V5Minecraft.NothiriumLittleTilesPatch.LittleTilesPatches;

import java.util.concurrent.locks.ReentrantLock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.math.BlockPos;

import meldexun.nothirium.mc.renderer.ChunkRenderManager;

public class NothiriumRenderChunkWrapper extends RenderChunk {

    private final int sectionX;
    private final int sectionY;
    private final int sectionZ;
    private final ReentrantLock dummyLock = new ReentrantLock();

    public NothiriumRenderChunkWrapper(BlockPos pos) {
        super(null, null, 0);
        setPosition(pos.getX() & ~15, pos.getY() & ~15, pos.getZ() & ~15);
        this.sectionX = pos.getX() >> 4;
        this.sectionY = pos.getY() >> 4;
        this.sectionZ = pos.getZ() >> 4;
    }

    @Override
    public void setNeedsUpdate(boolean immediate) {
        try {
            if (ChunkRenderManager.getProvider() == null) return;
            ChunkRenderManager.getProvider().setDirty(sectionX, sectionY, sectionZ);

            int baseX = sectionX << 4;
            int baseY = sectionY << 4;
            int baseZ = sectionZ << 4;
            if (Minecraft.getMinecraft().renderGlobal != null) {
                Minecraft.getMinecraft().renderGlobal.markBlockRangeForRenderUpdate(baseX - 1, baseY - 1, baseZ - 1,
                        baseX + 16, baseY + 16, baseZ + 16);
            }
        } catch (Exception e) {}
    }

    @Override
    public boolean needsUpdate() {
        return false;
    }

    @Override
    public ReentrantLock getLockCompileTask() {
        return dummyLock;
    }
}
