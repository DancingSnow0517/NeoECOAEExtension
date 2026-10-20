package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.registries.RegistryBuilder;

public class NERegistries {
    public static Registry<ECOCellType> CELL_TYPE;
    public static Registry<IECOTier> ECO_TIER;

    @SuppressWarnings("unchecked")
    public static void create(NewRegistryEvent event) {
        // Forge's wrappers preserve vanilla Registry IDs/codecs for the imported business code.
        // Forge synchronizes custom registries by default; hasTags also installs the vanilla wrapper.
        event.create(new RegistryBuilder<ECOCellType>().setName(Keys.CELL_TYPE.location())
            .setMaxID(256).hasTags(), registry ->
                CELL_TYPE = (Registry<ECOCellType>) BuiltInRegistries.REGISTRY.get(Keys.CELL_TYPE.location()));
        event.create(new RegistryBuilder<IECOTier>().setName(Keys.ECO_TIER.location())
            .setMaxID(256).hasTags(), registry ->
                ECO_TIER = (Registry<IECOTier>) BuiltInRegistries.REGISTRY.get(Keys.ECO_TIER.location()));
    }

    public static class Keys {
        public static final ResourceKey<Registry<ECOCellType>> CELL_TYPE = ResourceKey.createRegistryKey(NeoECOAE.id("cell_type"));
        public static final ResourceKey<Registry<IECOTier>> ECO_TIER = ResourceKey.createRegistryKey(NeoECOAE.id("eco_tier"));
    }
}

