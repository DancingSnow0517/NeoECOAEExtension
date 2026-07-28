package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.BuddingEnergizedCrystalBlock;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationParallelCore;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationThreadingCore;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingParallelCore;
import cn.dancingsnow.neoecoae.config.NEConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.Set;

public class NETooltips {
    private static final Component HOLD_SHIFT = Component.translatable("tooltip.neoecoae.holdshift")
        .withStyle(ChatFormatting.DARK_GRAY);

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

    private static final Set<Item> COMPUTATION_SYSTEMS = Set.of(
        NEBlocks.COMPUTATION_SYSTEM_L4.asItem(),
        NEBlocks.COMPUTATION_SYSTEM_L6.asItem(),
        NEBlocks.COMPUTATION_SYSTEM_L9.asItem()
    );

    public static void register(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> tooltip = event.getToolTip();
        TooltipFlag flags = event.getFlags();
        if (STORAGE_SYSTEMS.contains(stack.getItem())) {
            addTooltips(tooltip, flags,
                description("tooltip.neoecoae.storage_system"),
                stat("tooltip.neoecoae.max_lenth", NEConfig.storageSystemMaxLength)
            );
        }
        if (stack.is(NEBlocks.ECO_DRIVE.asItem())) {
            addTooltips(tooltip, flags,
                description("tooltip.neoecoae.storage_dirve.0"),
                detail("tooltip.neoecoae.storage_dirve.1")
            );
        }
        if (CRAFTING_SYSTEMS.contains(stack.getItem())) {
            addTooltips(tooltip, flags,
                description("tooltip.neoecoae.crafting_system"),
                stat("tooltip.neoecoae.max_lenth", NEConfig.craftingSystemMaxLength)
            );
        }
        if (stack.is(NEBlocks.CRAFTING_NETWORK_SWITCH.asItem())) {
            addNetworkSwitchTooltips(
                tooltip, flags, "tooltip.neoecoae.crafting_network_switch", 2,
                "tooltip.neoecoae.network_switch.crafting_cooling"
            );
        }
        if (stack.is(NEBlocks.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH.asItem())) {
            addNetworkSwitchTooltips(
                tooltip, flags, "tooltip.neoecoae.crafting_network_switch", 8,
                "tooltip.neoecoae.network_switch.crafting_high_energy_cooling"
            );
        }
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ECOCraftingParallelCore parallelCore) {
            IECOTier tier = parallelCore.getTier();
            addTooltips(tooltip, flags,
                description("tooltip.neoecoae.crafting_parallels"),
                stat("tooltip.neoecoae.max_parallel_count", tier.getCrafterParallel()),
                heading("tooltip.neoecoae.overclocked"),
                indented(ChatFormatting.AQUA, "tooltip.neoecoae.max_parallel_count", tier.getOverclockedCrafterParallel()),
                heading("tooltip.neoecoae.active_cooling"),
                indented(ChatFormatting.GREEN, "tooltip.neoecoae.clear_negative_effect")
            );
        }

        if (stack.is(NEBlocks.CRAFTING_WORKER.asItem())) {
            addTooltips(tooltip, flags,
                description("tooltip.neoecoae.crafting_worker.0"),
                detail("tooltip.neoecoae.crafting_worker.1"),
                heading("tooltip.neoecoae.overclocked"),
                indented(ChatFormatting.AQUA, "tooltip.neoecoae.crafting_jobs_l4", ECOTier.L4.getOverclockedCrafterQueueMultiply()),
                indented(ChatFormatting.AQUA, "tooltip.neoecoae.crafting_jobs_l6", ECOTier.L6.getOverclockedCrafterQueueMultiply()),
                indented(ChatFormatting.AQUA, "tooltip.neoecoae.crafting_jobs_l9", ECOTier.L9.getOverclockedCrafterQueueMultiply()),
                indented(ChatFormatting.RED, "tooltip.neoecoae.power_multiply_l4", ECOTier.L4.getOverclockedCrafterPowerMultiply()),
                indented(ChatFormatting.RED, "tooltip.neoecoae.power_multiply_l6", ECOTier.L6.getOverclockedCrafterPowerMultiply()),
                indented(ChatFormatting.RED, "tooltip.neoecoae.power_multiply_l9", ECOTier.L9.getOverclockedCrafterPowerMultiply()),
                heading("tooltip.neoecoae.active_cooling"),
                indented(ChatFormatting.GREEN, "tooltip.neoecoae.clear_negative_effect")
            );
        }
        if (stack.is(NEBlocks.CRAFTING_PATTERN_BUS.asItem())) {
            addTooltips(tooltip, flags,
                description("tooltip.neoecoae.crafting_pattern_bus.0"),
                stat("tooltip.neoecoae.crafting_pattern_bus.1", NEConfig.getCraftingPatternBusSlotCount()),
                detail("tooltip.neoecoae.crafting_pattern_bus.2")
            );
        }

        if (COMPUTATION_SYSTEMS.contains(stack.getItem())) {
            addTooltips(tooltip, flags,
                description("tooltip.neoecoae.computation_system"),
                stat("tooltip.neoecoae.max_lenth", NEConfig.computationSystemMaxLength),
                heading("tooltip.neoecoae.computation_system_desc.0"),
                detail("tooltip.neoecoae.computation_system_desc.1"),
                detail("tooltip.neoecoae.computation_system_desc.2"),
                detail("tooltip.neoecoae.computation_system_desc.3"),
                detail("tooltip.neoecoae.computation_system_desc.4")
            );
        }
        if (stack.is(NEBlocks.COMPUTATION_NETWORK_SWITCH.asItem())) {
            addNetworkSwitchTooltips(
                tooltip, flags, "tooltip.neoecoae.computation_network_switch", 2,
                "tooltip.neoecoae.network_switch.computation_cooling"
            );
        }
        if (stack.is(NEBlocks.COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH.asItem())) {
            addNetworkSwitchTooltips(
                tooltip, flags, "tooltip.neoecoae.computation_network_switch", 8,
                "tooltip.neoecoae.network_switch.computation_high_energy_cooling"
            );
            addTooltips(tooltip, flags,
                styled(ChatFormatting.LIGHT_PURPLE, "tooltip.neoecoae.network_switch.computation_ultimate")
            );
        }
        if (stack.is(NEBlocks.COMPUTATION_DRIVE.asItem())) {
            addTooltips(tooltip, flags,
                description("tooltip.neoecoae.computation_drive.0"),
                detail("tooltip.neoecoae.computation_drive.1")
            );
        }
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ECOComputationThreadingCore threadingCore) {
            addTooltips(tooltip, flags,
                description("tooltip.neoecoae.computation_threading_core.0"),
                detail("tooltip.neoecoae.computation_threading_core.1"),
                detail("tooltip.neoecoae.computation_threading_core.2"),
                stat("tooltip.neoecoae.max_thread_count", threadingCore.getTier().getCPUThreads())
            );
        }
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ECOComputationParallelCore parallelCore) {
            addTooltips(tooltip, flags,
                description("tooltip.neoecoae.computation_parallel_core.0"),
                detail("tooltip.neoecoae.computation_parallel_core.1"),
                stat("tooltip.neoecoae.max_parallel_count", parallelCore.getTier().getCPUAccelerators())
            );
        }
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof BuddingEnergizedCrystalBlock) {
            addTooltips(tooltip, flags,
                description("tooltip.neoecoae.budding_energized_crystal_block")
            );
        }
    }

    private static void addTooltips(List<Component> tooltip, TooltipFlag flags, Component... tooltips) {
        if (flags.hasShiftDown()) {
            tooltip.addAll(List.of(tooltips));
        } else if (!tooltip.contains(HOLD_SHIFT)) {
            tooltip.add(HOLD_SHIFT);
        }
    }

    private static void addNetworkSwitchTooltips(
        List<Component> tooltip,
        TooltipFlag flags,
        String descriptionKey,
        int multiplier,
        String coolingRequirementKey
    ) {
        addTooltips(tooltip, flags,
            description(descriptionKey),
            stat("tooltip.neoecoae.network_switch.multiplier", multiplier),
            styled(ChatFormatting.RED, "tooltip.neoecoae.network_switch.power_multiplier", multiplier >= 8 ? 16 : 4),
            styled(ChatFormatting.YELLOW, "tooltip.neoecoae.network_switch.requirement"),
            styled(ChatFormatting.BLUE, coolingRequirementKey)
        );
    }

    private static MutableComponent description(String key, Object... args) {
        return styled(ChatFormatting.GRAY, key, args);
    }

    private static MutableComponent detail(String key, Object... args) {
        return styled(ChatFormatting.DARK_GRAY, key, args);
    }

    private static MutableComponent stat(String key, Object... args) {
        return styled(ChatFormatting.AQUA, key, args);
    }

    private static MutableComponent heading(String key, Object... args) {
        return styled(ChatFormatting.GOLD, key, args);
    }

    private static MutableComponent indented(ChatFormatting color, String key, Object... args) {
        return Component.literal("  ").append(styled(color, key, args));
    }

    private static MutableComponent styled(ChatFormatting color, String key, Object... args) {
        return Component.translatable(key, args).withStyle(color);
    }
}
