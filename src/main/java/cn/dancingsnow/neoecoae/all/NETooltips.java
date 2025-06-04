package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingParallelCore;
import cn.dancingsnow.neoecoae.config.NEConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.Set;

public class NETooltips {
    private static final Component HOLD_SHIFT = Component.translatable("tooltip.neoecoae.holdshift").withStyle(ChatFormatting.GRAY);

    private static final Set<Item> STORAGE_SYSTEMS = Set.of(
        NEBlocks.STORAGE_SYSTEM_L4.asItem(),
        NEBlocks.STORAGE_SYSTEM_L6.asItem(),
        NEBlocks.STORAGE_SYSTEM_L9.asItem()
    );

    private static final Set<Item> CRAFTING_SYSTEMS = Set.of(
        NEBlocks.CRAFTING_SYSTEM_L4.asItem(),
        NEBlocks.CRAFTING_SYSTEM_L6.asItem(),
        NEBlocks.CRAFTING_SYSTEM_L9.asItem()
    );

    private static List<Component> tooltip;
    private static TooltipFlag flags;

    public static void register(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        tooltip = event.getToolTip();
        flags = event.getFlags();
        if (STORAGE_SYSTEMS.contains(stack.getItem())) {
            addTooltips(
                Component.translatable("tooltip.neoecoae.storage_system"),
                Component.translatable("tooltip.neoecoae.max_lenth", NEConfig.storageSystemMaxLength)
            );
        }
        if (stack.is(NEBlocks.ECO_DRIVE.asItem())) {
            addTooltips(
                Component.translatable("tooltip.neoecoae.storage_dirve.0"),
                Component.translatable("tooltip.neoecoae.storage_dirve.1")
            );
        }
        if (CRAFTING_SYSTEMS.contains(stack.getItem())) {
            addTooltips(
                Component.translatable("tooltip.neoecoae.crafting_system"),
                Component.translatable("tooltip.neoecoae.max_lenth", NEConfig.craftingSystemMaxLength)
            );
        }
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ECOCraftingParallelCore parallelCore) {
            IECOTier tier = parallelCore.getTier();
            addTooltips(
                Component.translatable("tooltip.neoecoae.crafting_parallels"),
                Component.translatable("tooltip.neoecoae.max_parallel_count", tier.getCrafterParallel()),
                Component.translatable("tooltip.neoecoae.overclocked"),
                Component.literal("  ").append(
                    Component.translatable("tooltip.neoecoae.max_parallel_count", tier.getOverclockedCrafterParallel())
                ),
                Component.translatable("tooltip.neoecoae.active_cooling"),
                Component.literal("  ").append(
                    Component.translatable("tooltip.neoecoae.clear_negative_effect")
                )
            );
        }

        if (stack.is(NEBlocks.CRAFTING_WORKER.asItem())) {
            addTooltips(
                Component.translatable("tooltip.neoecoae.crafting_worker.0"),
                Component.translatable("tooltip.neoecoae.crafting_worker.1"),
                Component.translatable("tooltip.neoecoae.overclocked"),
                Component.literal("  ").append(
                    Component.translatable("tooltip.neoecoae.crafting_jobs_l4", ECOTier.L4.getOverclockedCrafterQueueMultiply())
                ),
                Component.literal("  ").append(
                    Component.translatable("tooltip.neoecoae.crafting_jobs_l6", ECOTier.L6.getOverclockedCrafterQueueMultiply())
                ),
                Component.literal("  ").append(
                    Component.translatable("tooltip.neoecoae.crafting_jobs_l9", ECOTier.L9.getOverclockedCrafterQueueMultiply())
                ),
                Component.literal("  ").append(
                    Component.translatable("tooltip.neoecoae.power_multiply_l4", ECOTier.L4.getOverclockedCrafterPowerMultiply())
                ),
                Component.literal("  ").append(
                    Component.translatable("tooltip.neoecoae.power_multiply_l6", ECOTier.L6.getOverclockedCrafterPowerMultiply())
                ),
                Component.literal("  ").append(
                    Component.translatable("tooltip.neoecoae.power_multiply_l9", ECOTier.L9.getOverclockedCrafterPowerMultiply())
                ),
                Component.translatable("tooltip.neoecoae.active_cooling"),
                Component.literal("  ").append(
                    Component.translatable("tooltip.neoecoae.clear_negative_effect")
                )
            );
        }
        if (stack.is(NEBlocks.CRAFTING_PATTERN_BUS.asItem())) {
            addTooltips(
                Component.translatable("tooltip.neoecoae.crafting_pattern_bus.0"),
                Component.translatable("tooltip.neoecoae.crafting_pattern_bus.1"),
                Component.translatable("tooltip.neoecoae.crafting_pattern_bus.2")
            );
        }
    }

    private static void addTooltips(Component... tooltips) {
        if (flags.hasShiftDown()) {
            tooltip.addAll(List.of(tooltips));
        } else {
            tooltip.add(HOLD_SHIFT);
        }
    }
}
