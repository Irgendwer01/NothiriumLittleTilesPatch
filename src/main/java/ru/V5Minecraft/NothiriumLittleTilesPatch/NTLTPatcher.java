package ru.V5Minecraft.NothiriumLittleTilesPatch;

import java.util.Collections;
import java.util.List;

import net.minecraftforge.fml.common.Mod;

import zone.rong.mixinbooter.ILateMixinLoader;

@Mod(name = Tags.MODNAME, modid = Tags.VERSION, version = Tags.VERSION)
public class NTLTPatcher implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("nothiriumlittletilespatch.mixins.json");
    }
}
