package cn.dancingsnow.neoecoae.all;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ToolMaterial;

public final class NEToolTier {
    public static final ToolMaterial ALUMINUM = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 250, 6.0F, 2.0F, 14, NETags.Items.ALUMINUM_INGOT);
    public static final ToolMaterial ALUMINUM_ALLOY = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 500, 6.0F, 2.0F, 14, NETags.Items.ALUMINUM_ALLOY_INGOT);
    public static final ToolMaterial TUNGSTEN = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1700, 8.0F, 3.0F, 10, NETags.Items.TUNGSTEN_INGOT);
    public static final ToolMaterial BLACK_TUNGSTEN_ALLOY = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2500, 9.9f, 4.0f, 15, NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT);

    private NEToolTier() {
    }
}
