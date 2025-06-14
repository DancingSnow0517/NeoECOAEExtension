package cn.dancingsnow.neoecoae.data.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.data.worldgen.NEBiomeModifiers;
import cn.dancingsnow.neoecoae.data.worldgen.NEConfiguredFeatures;
import cn.dancingsnow.neoecoae.data.worldgen.NEPlacedFeatures;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class NERegistryProvider extends DatapackBuiltinEntriesProvider {
    private static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
        .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, NEBiomeModifiers::bootstrap)
        .add(Registries.CONFIGURED_FEATURE, NEConfiguredFeatures::bootstrap)
        .add(Registries.PLACED_FEATURE, NEPlacedFeatures::bootstrap);

    public NERegistryProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, BUILDER, Set.of(NeoECOAE.MOD_ID));
    }
}
