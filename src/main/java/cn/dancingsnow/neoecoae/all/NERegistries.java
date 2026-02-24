package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegistryBuilder;

public class NERegistries {
    public static final Registry<ECOCellType> CELL_TYPE = new RegistryBuilder<>(Keys.CELL_TYPE)
        .sync(true)
        .maxId(256)
        .create();

    public static final Registry<IECOTier> ECO_TIER = new RegistryBuilder<>(Keys.ECO_TIER)
        .sync(true)
        .maxId(256)
        .create();

    public static class Keys {
        public static final ResourceKey<Registry<ECOCellType>> CELL_TYPE = ResourceKey.createRegistryKey(NeoECOAE.id("cell_type"));
        public static final ResourceKey<Registry<IECOTier>> ECO_TIER = ResourceKey.createRegistryKey(NeoECOAE.id("eco_tier"));
    }
}

