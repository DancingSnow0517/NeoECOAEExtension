package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

public class NETags {
    public static final class Items {
        public static final TagKey<Item> ORE_ALUMINUM = common("ores/aluminum");
        public static final TagKey<Item> RAW_ALUMINUM = common("raw_materials/aluminum");
        public static final TagKey<Item> INGOT_ALUMINUM = common("ingots/aluminum");
        public static final TagKey<Item> DUST_ALUMINUM = common("dusts/aluminum");

        public static final TagKey<Item> ORE_TUNGSTEN = common("ores/tungsten");
        public static final TagKey<Item> RAW_TUNGSTEN = common("raw_materials/tungsten");
        public static final TagKey<Item> INGOT_TUNGSTEN = common("ingots/tungsten");
        public static final TagKey<Item> DUST_TUNGSTEN = common("dusts/tungsten");

        private static TagKey<Item> mod(String path) {
            return ItemTags.create(NeoECOAE.id(path));
        }

        private static TagKey<Item> common(String path) {
            return ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", path));
        }
    }

    public static final class Blocks {
        public static final TagKey<Block> ORE_ALUMINUM = common("ores/aluminum");

        public static final TagKey<Block> ORE_TUNGSTEN = common("ores/tungsten");

        private static TagKey<Block> mod(String path) {
            return BlockTags.create(NeoECOAE.id(path));
        }

        private static TagKey<Block> common(String path) {
            return BlockTags.create(ResourceLocation.fromNamespaceAndPath("c", path));
        }
    }

    public static final class Fluids {
        public static final TagKey<Fluid> STEAM = common("steam");

        private static TagKey<Fluid> mod(String path) {
            return FluidTags.create(NeoECOAE.id(path));
        }

        private static TagKey<Fluid> common(String path) {
            return FluidTags.create(ResourceLocation.fromNamespaceAndPath("c", path));
        }
    }
}
