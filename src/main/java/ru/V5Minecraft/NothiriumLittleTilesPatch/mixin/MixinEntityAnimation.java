package ru.V5Minecraft.NothiriumLittleTilesPatch.mixin;

import com.creativemd.littletiles.client.render.world.LittleRenderChunkSuppilier;
import com.creativemd.littletiles.common.entity.EntityAnimation;
import com.creativemd.creativecore.common.world.CreativeWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = EntityAnimation.class, remap = false)
public abstract class MixinEntityAnimation {

    @Shadow
    public CreativeWorld fakeWorld;

    @Overwrite
    public LittleRenderChunkSuppilier getRenderChunkSuppilier() {
        if (fakeWorld == null)
            return null;

        if (fakeWorld.renderChunkSupplier == null && fakeWorld.isRemote) {
            fakeWorld.renderChunkSupplier = new LittleRenderChunkSuppilier();
        }

        return (LittleRenderChunkSuppilier) fakeWorld.renderChunkSupplier;
    }
}
