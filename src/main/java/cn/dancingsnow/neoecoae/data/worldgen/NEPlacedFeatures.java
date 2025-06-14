package cn.dancingsnow.neoecoae.data.worldgen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.RarityFilter;

import java.util.List;

public class NEPlacedFeatures {
    public static final ResourceKey<PlacedFeature> GEODE = key("geode");

    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> lookup = context.lookup(Registries.CONFIGURED_FEATURE);

        context.register(
            GEODE,
            new PlacedFeature(
                lookup.getOrThrow(NEConfiguredFeatures.GEODE),
                List.of(
                    RarityFilter.onAverageOnceEvery(24),
                    InSquarePlacement.spread(),
                    HeightRangePlacement.uniform(
                        VerticalAnchor.aboveBottom(6),
                        VerticalAnchor.absolute(30)
                    ),
                    BiomeFilter.biome()
                )
            )
        );
    }

    private static ResourceKey<PlacedFeature> key(String id) {
        return ResourceKey.create(Registries.PLACED_FEATURE, NeoECOAE.id(id));
    }
}
