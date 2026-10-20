package cn.dancingsnow.neoecoae.data.worldgen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class NEBiomeModifiers {
    public static final ResourceKey<BiomeModifier> ORE_END = key("ore_end");

    public static void bootstrap(BootstrapContext<BiomeModifier> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
        HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        BiomeModifiers.AddFeaturesBiomeModifier oreBiomeModifier = new BiomeModifiers.AddFeaturesBiomeModifier(
            HolderSet.direct(
                biomes.getOrThrow(Biomes.THE_END),
                biomes.getOrThrow(Biomes.END_HIGHLANDS),
                biomes.getOrThrow(Biomes.END_MIDLANDS),
                biomes.getOrThrow(Biomes.SMALL_END_ISLANDS),
                biomes.getOrThrow(Biomes.END_BARRENS)
            ),
            HolderSet.direct(
                placedFeatures.getOrThrow(NEPlacedFeatures.ORE_ALUMINUM),
                placedFeatures.getOrThrow(NEPlacedFeatures.ORE_ALUMINUM_SMALL),
                placedFeatures.getOrThrow(NEPlacedFeatures.ORE_TUNGSTEN),
                placedFeatures.getOrThrow(NEPlacedFeatures.ORE_TUNGSTEN_SMALL)
            ),
            GenerationStep.Decoration.UNDERGROUND_ORES
        );
        context.register(ORE_END, oreBiomeModifier);
    }

    private static ResourceKey<BiomeModifier> key(String id) {
        return ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, NeoECOAE.id(id));
    }
}
