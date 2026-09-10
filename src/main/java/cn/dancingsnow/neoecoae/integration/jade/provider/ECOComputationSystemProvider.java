package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECOComputationSystemProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig pluginConfig) {
        CompoundTag data = accessor.getServerData();
        if (data.contains("fastPlannerEnabled")) {
            boolean enabled = data.getBoolean("fastPlannerEnabled");
            tooltip.add(0, Component.translatable(enabled
                    ? "jade.neoecoae.fast_planner.enabled"
                    : "jade.neoecoae.fast_planner.disabled")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (data.contains("cyclePlanningEnabled")) {
            boolean enabled = data.getBoolean("cyclePlanningEnabled");
            tooltip.add(1, Component.translatable(enabled
                    ? "jade.neoecoae.cycle_planning.enabled"
                    : "jade.neoecoae.cycle_planning.disabled")
                .withStyle(enabled ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ECOComputationSystemBlockEntity system) {
            data.putBoolean("fastPlannerEnabled", system.isFastCraftingPlannerEnabled());
            data.putBoolean("cyclePlanningEnabled", system.isCyclePlanningEnabled());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_computation_system");
    }
}
