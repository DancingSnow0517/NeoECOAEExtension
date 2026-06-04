package cn.dancingsnow.neoecoae.util;

import appeng.items.tools.quartz.QuartzWrenchItem;
import cn.dancingsnow.neoecoae.items.StructureTerminalItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Utility methods for mod interaction handling.
 * No LDLib dependency.
 */
public final class NEInteractionUtil {

    private static final TagKey<Item> WRENCH_TAG = ItemTags.create(new ResourceLocation("forge", "tools/wrench"));

    private NEInteractionUtil() {}

    public static boolean isHoldingStructureTerminal(Player player, InteractionHand hand) {
        return player.getItemInHand(hand).getItem() instanceof StructureTerminalItem;
    }

    public static boolean isHoldingWrench(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Item item = stack.getItem();
        // Check AE2 wrench class directly
        if (item instanceof QuartzWrenchItem) {
            return true;
        }
        // Also check the Forge wrench tag
        return stack.is(WRENCH_TAG);
    }

    /**
     * Returns {@code true} when the player is holding a tool that should
     * take priority over the block's default use action (e.g. Shift+right-click
     * with Structure Terminal for auto-build, or right-click with a wrench
     * for dismantle).
     */
    public static boolean shouldPassBlockUseToHeldTool(Player player, InteractionHand hand) {
        if (isHoldingStructureTerminal(player, hand) && player.isShiftKeyDown()) {
            return true;
        }
        return isHoldingWrench(player, hand);
    }
}
