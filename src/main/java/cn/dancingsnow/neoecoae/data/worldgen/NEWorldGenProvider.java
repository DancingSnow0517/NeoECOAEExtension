package cn.dancingsnow.neoecoae.data.worldgen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * World generation provider for NeoECOAE ore generation
 */
public class NEWorldGenProvider {

    private static final int TUNGSTEN_VEIN_SIZE = 6;
    private static final int TUNGSTEN_VEINS_PER_CHUNK = 4;
    private static final int TUNGSTEN_MIN_HEIGHT = 0;
    private static final int TUNGSTEN_MAX_HEIGHT = 80;

    private static final int ALUMINUM_VEIN_SIZE = 8;
    private static final int ALUMINUM_VEINS_PER_CHUNK = 6;
    private static final int ALUMINUM_MIN_HEIGHT = 10;
    private static final int ALUMINUM_MAX_HEIGHT = 120;

    // Configured Features
    public static final ResourceKey<ConfiguredFeature<?, ?>> END_TUNGSTEN_ORE = createConfiguredFeatureKey("end_tungsten_ore");
    public static final ResourceKey<ConfiguredFeature<?, ?>> END_ALUMINUM_ORE = createConfiguredFeatureKey("end_aluminum_ore");

    // Placed Features
    public static final ResourceKey<PlacedFeature> END_TUNGSTEN_ORE_PLACED = createPlacedFeatureKey("end_tungsten_ore");
    public static final ResourceKey<PlacedFeature> END_ALUMINUM_ORE_PLACED = createPlacedFeatureKey("end_aluminum_ore");

    private static ResourceKey<ConfiguredFeature<?, ?>> createConfiguredFeatureKey(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, NeoECOAE.id(name));
    }

    private static ResourceKey<PlacedFeature> createPlacedFeatureKey(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, NeoECOAE.id(name));
    }

    public static void bootstrapConfiguredFeatures(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        RuleTest endStoneReplaceable = new BlockMatchTest(Blocks.END_STONE);

        // Tungsten ore configuration - larger veins, less common
        context.register(END_TUNGSTEN_ORE, new ConfiguredFeature<>(
            Feature.ORE,
            new OreConfiguration(
                endStoneReplaceable,
                NEBlocks.TUNGSTEN_ORE.getDefaultState(),
                TUNGSTEN_VEIN_SIZE
            )
        ));

        // Aluminum ore configuration - smaller veins, more common
        context.register(END_ALUMINUM_ORE, new ConfiguredFeature<>(
            Feature.ORE,
            new OreConfiguration(
                endStoneReplaceable,
                NEBlocks.ALUMINUM_ORE.getDefaultState(),
                ALUMINUM_VEIN_SIZE
            )
        ));
    }

    public static void bootstrapPlacedFeatures(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);

        // Tungsten ore placement - rare, deep in the End
        context.register(END_TUNGSTEN_ORE_PLACED, new PlacedFeature(
            configuredFeatures.getOrThrow(END_TUNGSTEN_ORE),
            List.of(
                CountPlacement.of(TUNGSTEN_VEINS_PER_CHUNK),
                InSquarePlacement.spread(),
                HeightRangePlacement.uniform(
                    VerticalAnchor.absolute(TUNGSTEN_MIN_HEIGHT),
                    VerticalAnchor.absolute(TUNGSTEN_MAX_HEIGHT)
                ),
                BiomeFilter.biome()
            )
        ));

        // Aluminum ore placement - more common, wider range
        context.register(END_ALUMINUM_ORE_PLACED, new PlacedFeature(
            configuredFeatures.getOrThrow(END_ALUMINUM_ORE),
            List.of(
                CountPlacement.of(ALUMINUM_VEINS_PER_CHUNK),
                InSquarePlacement.spread(),
                HeightRangePlacement.uniform(
                    VerticalAnchor.absolute(ALUMINUM_MIN_HEIGHT),
                    VerticalAnchor.absolute(ALUMINUM_MAX_HEIGHT)
                ),
                BiomeFilter.biome()
            )
        ));
    }
}