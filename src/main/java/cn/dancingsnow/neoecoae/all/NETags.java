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
        private static TagKey<Item> mod(String path) {
            return ItemTags.create(NeoECOAE.id(path));
        }

        private static TagKey<Item> common(String path) {
            return ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", path));
        }
    }

    public static final class Blocks {
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
