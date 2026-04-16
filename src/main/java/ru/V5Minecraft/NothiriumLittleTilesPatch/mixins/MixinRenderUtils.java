package ru.V5Minecraft.NothiriumLittleTilesPatch.mixins;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.creativemd.littletiles.client.render.world.RenderUtils;

import ru.V5Minecraft.NothiriumLittleTilesPatch.LittleTilesPatches.NothiriumRenderChunkWrapper;

@SideOnly(Side.CLIENT)
@Mixin(value = RenderUtils.class, remap = false)
public abstract class MixinRenderUtils {

    @Shadow
    private static Method getRenderChunk;

    @Shadow
    private static Minecraft mc;

    private static final ConcurrentHashMap<Long, NothiriumRenderChunkWrapper> nothirium$wrapperCache = new ConcurrentHashMap<>();

    private static long nothirium$packSectionKey(BlockPos pos) {
        int sx = pos.getX() >> 4;
        int sy = pos.getY() >> 4;
        int sz = pos.getZ() >> 4;
        return ((long) (sx & 0x3FFFFF) << 34) | ((long) (sy & 0xFFF) << 22) | (long) (sz & 0x3FFFFF);
    }

    @Overwrite
    public static RenderChunk getRenderChunk(ViewFrustum frustum, BlockPos pos) {
        if (frustum == null) {
            long key = nothirium$packSectionKey(pos);

            NothiriumRenderChunkWrapper wrapper = nothirium$wrapperCache.get(key);
            if (wrapper != null)
                return wrapper;

            if (!mc.isCallingFromMinecraftThread()) {
                final BlockPos finalPos = pos;
                mc.addScheduledTask(() -> {
                    long k = nothirium$packSectionKey(finalPos);
                    if (!nothirium$wrapperCache.containsKey(k)) {
                        NothiriumRenderChunkWrapper w = new NothiriumRenderChunkWrapper(finalPos);
                        nothirium$wrapperCache.putIfAbsent(k, w);
                    }

                    try {
                        Class<?> cls = Class.forName("meldexun.nothirium.mc.renderer.ChunkRenderManager");
                        Object provider = cls.getMethod("getProvider").invoke(null);
                        if (provider != null) {
                            int sx = finalPos.getX() >> 4;
                            int sy = finalPos.getY() >> 4;
                            int sz = finalPos.getZ() >> 4;
                            provider.getClass().getMethod("setDirty", int.class, int.class, int.class)
                                    .invoke(provider, sx, sy, sz);
                        }
                    } catch (Exception ignored) {}
                });
                return null;
            }

            wrapper = new NothiriumRenderChunkWrapper(pos);
            NothiriumRenderChunkWrapper existing = nothirium$wrapperCache.putIfAbsent(key, wrapper);
            return existing != null ? existing : wrapper;
        }

        try {
            return (RenderChunk) getRenderChunk.invoke(frustum, pos);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
