package cn.dancingsnow.neoecoae.data.worldgen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.FeatureUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.neoforged.neoforge.common.Tags;

public class NEConfiguredFeatures {
    public static final ResourceKey<ConfiguredFeature<?, ?>>
        ORE_ALUMINUM = key("ore_aluminum"),
        ORE_ALUMINUM_SMALL = key("ore_aluminum_small"),
        ORE_TUNGSTEN = key("ore_tungsten"),
        ORE_TUNGSTEN_SMALL = key("ore_tungsten_small");

    public static void bootstrap(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        FeatureUtils.register(
            context,
            ORE_ALUMINUM,
            Feature.ORE,
            new OreConfiguration(new TagMatchTest(Tags.Blocks.END_STONES), NEBlocks.ALUMINUM_ORE.getDefaultState(), 9)
        );
        FeatureUtils.register(
            context,
            ORE_ALUMINUM_SMALL,
            Feature.ORE,
            new OreConfiguration(new TagMatchTest(Tags.Blocks.END_STONES), NEBlocks.ALUMINUM_ORE.getDefaultState(), 4)
        );
        FeatureUtils.register(
            context,
            ORE_TUNGSTEN,
            Feature.ORE,
            new OreConfiguration(new TagMatchTest(Tags.Blocks.END_STONES), NEBlocks.TUNGSTEN_ORE.getDefaultState(), 9)
        );
        FeatureUtils.register(
            context,
            ORE_TUNGSTEN_SMALL,
            Feature.ORE,
            new OreConfiguration(new TagMatchTest(Tags.Blocks.END_STONES), NEBlocks.TUNGSTEN_ORE.getDefaultState(), 4)
        );
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> key(String id) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, NeoECOAE.id(id));
    }
}
