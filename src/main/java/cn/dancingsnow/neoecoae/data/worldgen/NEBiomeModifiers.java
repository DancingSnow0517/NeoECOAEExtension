package cn.dancingsnow.neoecoae.data.worldgen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Biome modifiers for adding ore generation to the End dimension
 */
public class NEBiomeModifiers {
    public static final ResourceKey<BiomeModifier> ADD_END_TUNGSTEN_ORE = createKey("add_end_tungsten_ore");
    public static final ResourceKey<BiomeModifier> ADD_END_ALUMINUM_ORE = createKey("add_end_aluminum_ore");

    private static ResourceKey<BiomeModifier> createKey(String name) {
        return ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, NeoECOAE.id(name));
    }

    public static void bootstrap(BootstrapContext<BiomeModifier> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
        HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);

        // Add tungsten ore to the End
        context.register(ADD_END_TUNGSTEN_ORE, new BiomeModifiers.AddFeaturesBiomeModifier(
            biomes.getOrThrow(BiomeTags.IS_END),
            HolderSet.direct(placedFeatures.getOrThrow(NEWorldGenProvider.END_TUNGSTEN_ORE_PLACED)),
            GenerationStep.Decoration.UNDERGROUND_ORES
        ));

        // Add aluminum ore to the End
        context.register(ADD_END_ALUMINUM_ORE, new BiomeModifiers.AddFeaturesBiomeModifier(
            biomes.getOrThrow(BiomeTags.IS_END),
            HolderSet.direct(placedFeatures.getOrThrow(NEWorldGenProvider.END_ALUMINUM_ORE_PLACED)),
            GenerationStep.Decoration.UNDERGROUND_ORES
        ));
    }
}