package cn.dancingsnow.neoecoae.data.worldgen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.List;

public class NEPlacedFeatures {

    public static final ResourceKey<PlacedFeature>
        ORE_ALUMINUM = key("ore_aluminum"),
        ORE_ALUMINUM_SMALL = key("ore_aluminum_small"),
        ORE_TUNGSTEN = key("ore_tungsten"),
        ORE_TUNGSTEN_SMALL = key("ore_tungsten_small");

    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> lookup = context.lookup(Registries.CONFIGURED_FEATURE);
        PlacementUtils.register(
            context,
            ORE_ALUMINUM,
            lookup.getOrThrow(NEConfiguredFeatures.ORE_ALUMINUM),
            commonOrePlacement(60, HeightRangePlacement.triangle(VerticalAnchor.absolute(60), VerticalAnchor.absolute(256)))
        );
        PlacementUtils.register(
            context,
            ORE_ALUMINUM_SMALL,
            lookup.getOrThrow(NEConfiguredFeatures.ORE_ALUMINUM_SMALL),
            commonOrePlacement(10, HeightRangePlacement.triangle(VerticalAnchor.absolute(10), VerticalAnchor.absolute(70)))
        );
        PlacementUtils.register(
            context,
            ORE_TUNGSTEN,
            lookup.getOrThrow(NEConfiguredFeatures.ORE_TUNGSTEN),
            commonOrePlacement(60, HeightRangePlacement.triangle(VerticalAnchor.absolute(60), VerticalAnchor.absolute(256)))
        );
        PlacementUtils.register(
            context,
            ORE_TUNGSTEN_SMALL,
            lookup.getOrThrow(NEConfiguredFeatures.ORE_TUNGSTEN_SMALL),
            commonOrePlacement(10, HeightRangePlacement.triangle(VerticalAnchor.absolute(10), VerticalAnchor.absolute(70)))
        );
    }

    private static ResourceKey<PlacedFeature> key(String id) {
        return ResourceKey.create(Registries.PLACED_FEATURE, NeoECOAE.id(id));
    }

    private static List<PlacementModifier> orePlacement(PlacementModifier countPlacement, PlacementModifier heightRange) {
        return List.of(countPlacement, InSquarePlacement.spread(), heightRange, BiomeFilter.biome());
    }

    private static List<PlacementModifier> commonOrePlacement(int count, PlacementModifier heightRange) {
        return orePlacement(CountPlacement.of(count), heightRange);
    }
}
