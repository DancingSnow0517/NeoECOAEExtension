package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public class NERegistries {
    public static final Registry<ECOCellType> CELL_TYPE = new MappedRegistry<>(
        Keys.CELL_TYPE,
        Lifecycle.stable()
    );

    public static final Registry<IECOTier> ECO_TIER = new MappedRegistry<>(
        Keys.ECO_TIER,
        Lifecycle.stable()
    );

    public static class Keys {
        public static final ResourceKey<Registry<ECOCellType>> CELL_TYPE = ResourceKey.createRegistryKey(NeoECOAE.id("cell_type"));
        public static final ResourceKey<Registry<IECOTier>> ECO_TIER = ResourceKey.createRegistryKey(NeoECOAE.id("eco_tier"));
    }
}

