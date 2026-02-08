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
        public static final TagKey<Item> IRON_DUST = common("dusts/iron");

        public static final TagKey<Item> ALUMINUM_ORE = common("ores/aluminum");
        public static final TagKey<Item> RAW_ALUMINUM_STORAGE_BLOCK = common("storage_blocks/raw_aluminum");
        public static final TagKey<Item> ALUMINUM_STORAGE_BLOCK = common("storage_blocks/aluminum");
        public static final TagKey<Item> ALUMINUM_RAW = common("raw_materials/aluminum");
        public static final TagKey<Item> ALUMINUM_INGOT = common("ingots/aluminum");
        public static final TagKey<Item> ALUMINUM_DUST = common("dusts/aluminum");

        public static final TagKey<Item> TUNGSTEN_ORE = common("ores/tungsten");
        public static final TagKey<Item> RAW_TUNGSTEN_STORAGE_BLOCK = common("storage_blocks/raw_tungsten");
        public static final TagKey<Item> TUNGSTEN_STORAGE_BLOCK = common("storage_blocks/tungsten");
        public static final TagKey<Item> TUNGSTEN_RAW = common("raw_materials/tungsten");
        public static final TagKey<Item> TUNGSTEN_INGOT = common("ingots/tungsten");
        public static final TagKey<Item> TUNGSTEN_DUST = common("dusts/tungsten");

        public static final TagKey<Item> ALUMINUM_ALLOY_STORAGE_BLOCK = common("storage_blocks/aluminum_alloy");
        public static final TagKey<Item> ALUMINUM_ALLOY_INGOT = common("ingots/aluminum_alloy");
        public static final TagKey<Item> ALUMINUM_ALLOY_DUST = common("dusts/aluminum_alloy");

        private static TagKey<Item> mod(String path) {
            return ItemTags.create(NeoECOAE.id(path));
        }

        private static TagKey<Item> common(String path) {
            return ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", path));
        }
    }

    public static final class Blocks {
        public static final TagKey<Block> ALUMINUM_ORE = common("ores/aluminum");
        public static final TagKey<Block> RAW_ALUMINUM_STORAGE_BLOCK = common("storage_blocks/raw_aluminum");
        public static final TagKey<Block> ALUMINUM_STORAGE_BLOCK = common("storage_blocks/aluminum");

        public static final TagKey<Block> TUNGSTEN_ORE = common("ores/tungsten");
        public static final TagKey<Block> RAW_TUNGSTEN_STORAGE_BLOCK = common("storage_blocks/raw_tungsten");
        public static final TagKey<Block> TUNGSTEN_STORAGE_BLOCK = common("storage_blocks/tungsten");

        public static final TagKey<Block> ALUMINUM_ALLOY_STORAGE_BLOCK = common("storage_blocks/aluminum_alloy");

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
