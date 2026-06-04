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
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ForgeBiomeModifiers;
import net.minecraftforge.registries.ForgeRegistries;

public class NEBiomeModifiers {
    public static final ResourceKey<BiomeModifier> ORE_END = key("ore_end");

    // WARNING: The generated ore_end.json must include a "conditions" block to disable ore
    // generation when GTCEu (modid: gtceu) is loaded. Since ForgeBiomeModifiers.AddFeaturesBiomeModifier
    // does not expose conditions via datagen, the conditions are manually maintained in
    // src/generated/resources/data/neoecoae/forge/biome_modifier/ore_end.json.
    // After re-running datagen, re-add the conditions block manually:
    //   "conditions": [{ "type": "forge:not", "value": { "type": "forge:mod_loaded", "modid": "gtceu" } }],
    public static void bootstrap(BootstrapContext<BiomeModifier> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
        HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        ForgeBiomeModifiers.AddFeaturesBiomeModifier oreBiomeModifier =
                new ForgeBiomeModifiers.AddFeaturesBiomeModifier(
                        HolderSet.direct(
                                biomes.getOrThrow(Biomes.THE_END),
                                biomes.getOrThrow(Biomes.END_HIGHLANDS),
                                biomes.getOrThrow(Biomes.END_MIDLANDS),
                                biomes.getOrThrow(Biomes.SMALL_END_ISLANDS),
                                biomes.getOrThrow(Biomes.END_BARRENS)),
                        HolderSet.direct(
                                placedFeatures.getOrThrow(NEPlacedFeatures.ORE_ALUMINUM),
                                placedFeatures.getOrThrow(NEPlacedFeatures.ORE_ALUMINUM_SMALL),
                                placedFeatures.getOrThrow(NEPlacedFeatures.ORE_TUNGSTEN),
                                placedFeatures.getOrThrow(NEPlacedFeatures.ORE_TUNGSTEN_SMALL)),
                        GenerationStep.Decoration.UNDERGROUND_ORES);
        context.register(ORE_END, oreBiomeModifier);
    }

    private static ResourceKey<BiomeModifier> key(String id) {
        return ResourceKey.create(ForgeRegistries.Keys.BIOME_MODIFIERS, NeoECOAE.id(id));
    }
}
