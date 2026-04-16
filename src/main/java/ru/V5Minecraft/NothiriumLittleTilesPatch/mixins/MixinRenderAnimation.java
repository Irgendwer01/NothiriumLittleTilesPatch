package ru.V5Minecraft.NothiriumLittleTilesPatch.mixins;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.creativemd.littletiles.client.render.entity.RenderAnimation;
import com.creativemd.littletiles.client.render.world.LittleRenderChunkSuppilier;
import com.creativemd.littletiles.common.entity.EntityAnimation;
import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;

@SideOnly(Side.CLIENT)
@Mixin(value = RenderAnimation.class, remap = false)
public abstract class MixinRenderAnimation {

    @Inject(method = "doRender", at = @At("HEAD"), remap = false)
    private void forcePrepareAnimationRender(EntityAnimation entity, double x, double y, double z, float entityYaw,
                                             float partialTicks, CallbackInfo ci) {
        if (entity.fakeWorld == null || !entity.world.isRemote)
            return;

        if (entity.fakeWorld.renderChunkSupplier == null) {
            entity.fakeWorld.renderChunkSupplier = new LittleRenderChunkSuppilier();
        }

        LittleRenderChunkSuppilier supplier = entity.getRenderChunkSuppilier();
        if (supplier == null)
            return;

        try {
            for (Object obj : entity.fakeWorld.loadedTileEntityList) {
                if (obj instanceof TileEntityLittleTiles) {
                    TileEntityLittleTiles te = (TileEntityLittleTiles) obj;

                    BlockPos pos = te.getPos();
                    if (pos != null) {
                        supplier.getRenderChunk(entity.fakeWorld, pos);

                        if (te.render != null) {
                            te.render.tilesChanged();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        int sx = (int) entity.posX >> 4;
        int sy = (int) entity.posY >> 4;
        int sz = (int) entity.posZ >> 4;

        try {
            Class<?> mgrClass = Class.forName("meldexun.nothirium.mc.renderer.ChunkRenderManager");
            Object provider = mgrClass.getMethod("getProvider").invoke(null);
            if (provider != null) {
                provider.getClass().getMethod("setDirty", int.class, int.class, int.class)
                        .invoke(provider, sx, sy, sz);
            }
        } catch (Exception ignored) {}
    }

    @Inject(method = "doRender",
            at = @At(value = "INVOKE",
                     target = "Lcom/creativemd/littletiles/common/entity/EntityAnimation;getRenderChunkSuppilier()Lcom/creativemd/littletiles/client/render/world/LittleRenderChunkSuppilier;",
                     shift = At.Shift.AFTER,
                     remap = false),
            remap = false)
    private void safeSupplierCheck(EntityAnimation entity, double x, double y, double z, float entityYaw,
                                   float partialTicks, CallbackInfo ci) {}
}
