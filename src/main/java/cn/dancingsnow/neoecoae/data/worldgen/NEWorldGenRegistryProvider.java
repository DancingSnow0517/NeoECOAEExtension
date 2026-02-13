package cn.dancingsnow.neoecoae.data.worldgen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Data provider for NeoECOAE worldgen registries
 */
public class NEWorldGenRegistryProvider extends DatapackBuiltinEntriesProvider {
    private static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
        .add(Registries.CONFIGURED_FEATURE, NEWorldGenProvider::bootstrapConfiguredFeatures)
        .add(Registries.PLACED_FEATURE, NEWorldGenProvider::bootstrapPlacedFeatures)
        .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, NEBiomeModifiers::bootstrap);

    public NEWorldGenRegistryProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, BUILDER, Set.of(NeoECOAE.MOD_ID));
    }
}