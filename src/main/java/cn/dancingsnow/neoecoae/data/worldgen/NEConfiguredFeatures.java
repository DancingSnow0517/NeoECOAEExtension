package cn.dancingsnow.neoecoae.data.worldgen;

import appeng.core.definitions.AEBlocks;
import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GeodeBlockSettings;
import net.minecraft.world.level.levelgen.GeodeCrackSettings;
import net.minecraft.world.level.levelgen.GeodeLayerSettings;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.GeodeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;

import java.util.List;

public class NEConfiguredFeatures {

    public static final ResourceKey<ConfiguredFeature<?, ?>> GEODE = key("geode");

    public static void bootstrap(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        context.register(
            GEODE,
            new ConfiguredFeature<>(
                Feature.GEODE,
                new GeodeConfiguration(
                    new GeodeBlockSettings(
                        BlockStateProvider.simple(Blocks.AIR),
                        BlockStateProvider.simple(AEBlocks.QUARTZ_BLOCK.block()),
                        new WeightedStateProvider(SimpleWeightedRandomList.<BlockState>builder()
                            .add(AEBlocks.FLAWLESS_BUDDING_QUARTZ.block().defaultBlockState(), 1)
                            .add(AEBlocks.FLAWED_BUDDING_QUARTZ.block().defaultBlockState(), 4)
                            .add(AEBlocks.CHIPPED_BUDDING_QUARTZ.block().defaultBlockState(), 16)
                            .add(AEBlocks.DAMAGED_BUDDING_QUARTZ.block().defaultBlockState(), 32)
                            .build()),
                        BlockStateProvider.simple(Blocks.CALCITE),
                        BlockStateProvider.simple(AEBlocks.SKY_STONE_BLOCK.block()),
                        List.of(
                            AEBlocks.SMALL_QUARTZ_BUD.block().defaultBlockState(),
                            AEBlocks.MEDIUM_QUARTZ_BUD.block().defaultBlockState(),
                            AEBlocks.LARGE_QUARTZ_BUD.block().defaultBlockState(),
                            AEBlocks.QUARTZ_CLUSTER.block().defaultBlockState()
                        ),
                        BlockTags.FEATURES_CANNOT_REPLACE,
                        BlockTags.GEODE_INVALID_BLOCKS
                    ),
                    new GeodeLayerSettings(1.7, 2.2, 3.2, 4.2),
                    new GeodeCrackSettings(0.95, 2.0, 2),
                    0.35,
                    0.09,
                    true,
                    UniformInt.of(4, 6),
                    UniformInt.of(3, 4),
                    UniformInt.of(1, 2),
                    -16,
                    16,
                    0.05,
                    1
                )
            )
        );
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> key(String id) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, NeoECOAE.id(id));
    }
}
