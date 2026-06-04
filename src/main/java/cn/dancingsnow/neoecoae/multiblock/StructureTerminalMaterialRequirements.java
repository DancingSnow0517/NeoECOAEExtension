package cn.dancingsnow.neoecoae.multiblock;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlanContext;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockRotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public final class StructureTerminalMaterialRequirements {
    private StructureTerminalMaterialRequirements() {}

    public static List<NEStructureTerminalUiState.BuildMaterialEntry> collect(
            Player player, StructureTerminalHostType hostType, int tier, int buildLength) {
        MultiBlockDefinition definition = hostType.definitionForTier(tier);
        return collect(player, definition, buildLength);
    }

    public static List<NEStructureTerminalUiState.BuildMaterialEntry> collect(
            Player player, MultiBlockDefinition definition, int buildLength) {
        List<ItemStack> requiredItems = collectRequiredItems(definition, buildLength);
        List<NEStructureTerminalUiState.BuildMaterialEntry> entries = new ArrayList<>(requiredItems.size());
        for (ItemStack required : requiredItems) {
            int requiredCount = required.getCount();
            int availableCount = clampInt(countMatchingItems(player, required));
            entries.add(new NEStructureTerminalUiState.BuildMaterialEntry(
                    required.copyWithCount(1), requiredCount, availableCount));
        }
        entries.sort(Comparator.comparingInt(NEStructureTerminalUiState.BuildMaterialEntry::missing)
                .reversed()
                .thenComparing(entry -> entry.item().getHoverName().getString()));
        return entries;
    }

    public static List<ItemStack> collectRequiredItems(MultiBlockDefinition definition, int buildLength) {
        if (definition == null) {
            return List.of();
        }
        int repeats = net.minecraft.util.Mth.clamp(buildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlanContext context = new MultiBlockPlanContext(repeats);
        definition.createLevel(context);

        List<ItemStack> requiredItems = new ArrayList<>();
        for (var plannedBlock : context.getPlannedBlocks()) {
            if (plannedBlock.relativePos().equals(MultiBlockRotation.CONTROLLER_ANCHOR)) {
                continue;
            }
            mergeItem(requiredItems, plannedBlock.requiredItem());
        }
        return requiredItems;
    }

    private static void mergeItem(List<ItemStack> requiredItems, ItemStack toAdd) {
        if (toAdd.isEmpty()) {
            return;
        }
        for (ItemStack requiredItem : requiredItems) {
            if (ItemStack.isSameItemSameTags(requiredItem, toAdd)) {
                requiredItem.grow(toAdd.getCount());
                return;
            }
        }
        requiredItems.add(toAdd.copy());
    }

    private static long countMatchingItems(Player player, ItemStack target) {
        Set<IItemHandler> visitedHandlers = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        return countMatchingItems(player.getInventory().items, target, visitedHandlers)
                + countMatchingItems(player.getInventory().offhand, target, visitedHandlers);
    }

    private static long countMatchingItems(
            List<ItemStack> stacks, ItemStack target, Set<IItemHandler> visitedHandlers) {
        long count = 0;
        for (ItemStack stack : stacks) {
            count += countMatchingItems(stack, target, visitedHandlers);
        }
        return count;
    }

    private static long countMatchingItems(ItemStack stack, ItemStack target, Set<IItemHandler> visitedHandlers) {
        if (stack.isEmpty()) {
            return 0;
        }

        long count = ItemStack.isSameItemSameTags(stack, target) ? stack.getCount() : 0;
        IItemHandler itemHandler =
                stack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (itemHandler == null || !visitedHandlers.add(itemHandler)) {
            return count;
        }

        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            count += countMatchingItems(itemHandler.getStackInSlot(slot), target, visitedHandlers);
        }
        return count;
    }

    private static int clampInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, (int) value);
    }
}
