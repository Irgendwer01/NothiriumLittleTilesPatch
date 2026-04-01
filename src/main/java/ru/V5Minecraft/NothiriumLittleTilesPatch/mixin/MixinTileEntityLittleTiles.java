package ru.V5Minecraft.NothiriumLittleTilesPatch.mixin;

import com.creativemd.littletiles.client.render.world.TileEntityRenderManager;
import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileEntityLittleTiles.class, remap = false)
public abstract class MixinTileEntityLittleTiles {
    @Shadow
    public TileEntityRenderManager render;

    @Inject(method = "onLoad", at = @At("TAIL"), remap = false)
    private void injectScheduleRenderUpdate(CallbackInfo ci) {
        TileEntity self = (TileEntity) (Object) this;
        World world = self.getWorld();
        if (world != null && world.isRemote) {
            nothirium$scheduleRenderUpdate();
        }
    }

    @Unique
    @SideOnly(Side.CLIENT)
    private void nothirium$scheduleRenderUpdate() {
        if (render == null) return;
        try {
            TileEntity self = (TileEntity) (Object) this;
            BlockPos pos = self.getPos();
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.renderGlobal != null && pos != null) {
                mc.renderGlobal.markBlockRangeForRenderUpdate(
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX(), pos.getY(), pos.getZ());
            }
        } catch (Exception e) {
        }
    }
}
