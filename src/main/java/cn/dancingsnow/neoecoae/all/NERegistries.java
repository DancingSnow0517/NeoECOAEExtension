package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;

public class NERegistries {

    /**
     * Returns the Forge-managed cell type registry (populated via
     * {@code NewRegistryEvent}), not a local empty {@code MappedRegistry}.
     */
    @SuppressWarnings("unchecked")
    public static Registry<ECOCellType> cellTypeRegistry() {
        return (Registry<ECOCellType>) BuiltInRegistries.REGISTRY.get(Keys.CELL_TYPE.location());
    }

    /**
     * @deprecated Kept only for LDLib accessor compatibility. This is not the
     *             Forge-managed registry created during {@code NewRegistryEvent}.
     */
    @Deprecated
    public static final Registry<IECOTier> ECO_TIER = new MappedRegistry<>(Keys.ECO_TIER, Lifecycle.stable());

    public static class Keys {
        public static final ResourceKey<Registry<ECOCellType>> CELL_TYPE =
                ResourceKey.createRegistryKey(NeoECOAE.id("cell_type"));
        public static final ResourceKey<Registry<IECOTier>> ECO_TIER =
                ResourceKey.createRegistryKey(NeoECOAE.id("eco_tier"));
    }
}
