package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.api.me.CraftingCapabilitySnapshot;
import appeng.core.localization.Tooltips;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECOCraftingSystemProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        CompoundTag data = blockAccessor.getServerData();
        if (data.contains("overclocked") && data.getBoolean("overclocked")) {
            iTooltip.add(Component.translatable("jade.neoecoae.overclocked"));
            iTooltip.add(Component.translatable(
                "jade.neoecoae.overclock_status",
                data.getInt("theoreticalOverclock"),
                data.getInt("effectiveOverclock")
            ));
        }
        if (data.contains("activeCooling") && data.getBoolean("activeCooling")) {
            iTooltip.add(Component.translatable("jade.neoecoae.activeCooling"));
        }
        if (data.contains("coolant")) {
            iTooltip.add(Component.translatable("jade.neoecoae.coolant", data.getInt("coolant")));
        }
        if (data.contains("coolingMaxOverclock")) {
            int coolingMaxOverclock = data.getInt("coolingMaxOverclock");
            if (coolingMaxOverclock >= 0) {
                iTooltip.add(Component.translatable("jade.neoecoae.coolant_max_overclock", coolingMaxOverclock));
            } else {
                iTooltip.add(Component.translatable("jade.neoecoae.coolant_max_overclock.none"));
            }
        }
        if (data.contains("physicalFxCount")) {
            boolean virtual = data.getBoolean("virtualMode");
            iTooltip.add(Component.translatable("gui.neoecoae.crafting.capability.fx",
                data.getInt("activeFxCount"), data.getInt("physicalFxCount")));
            iTooltip.add(Component.translatable("gui.neoecoae.crafting.capability.network_composition",
                data.getInt("normalSwitchHosts"), data.getInt("highEnergySwitchHosts")));
            iTooltip.add(Component.translatable("gui.neoecoae.crafting.capability.network_multiplier",
                data.getInt("networkMultiplier")));
            Component batch = virtual ? Component.translatable("gui.neoecoae.storage.infinite_value")
                : Tooltips.ofNumber(data.getLong("batchPerFx"));
            Component total = virtual ? Component.translatable("gui.neoecoae.storage.infinite_value")
                : Tooltips.ofNumber(data.getLong("totalBatchCapacity"));
            iTooltip.add(Component.translatable("gui.neoecoae.crafting.capability.batch_per_fx", batch));
            iTooltip.add(Component.translatable("gui.neoecoae.crafting.capability.total", total));
            iTooltip.add(Component.translatable("gui.neoecoae.crafting.capability.ft_parallel",
                Tooltips.ofNumber(data.getLong("ftParallelCapacity"))));
        }
    }

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof ECOCraftingSystemBlockEntity system) {
            CraftingCapabilitySnapshot state = system.getCapabilitySnapshot();
            compoundTag.putBoolean("overclocked", system.isOverclocked());
            compoundTag.putBoolean("activeCooling", state.coolantState().activeCooling());
            compoundTag.putInt("coolant", (int) Math.min(Integer.MAX_VALUE, state.coolantState().amount()));
            compoundTag.putInt("theoreticalOverclock", state.theoreticalOverclock());
            compoundTag.putInt("effectiveOverclock", state.effectiveOverclock());
            compoundTag.putInt("coolingMaxOverclock", state.coolantState().maxSupportedOverclock());
            compoundTag.putInt("physicalFxCount", state.physicalFxCount());
            compoundTag.putInt("activeFxCount", state.activeFxCount());
            compoundTag.putInt("normalSwitchHosts", state.normalSwitchHosts());
            compoundTag.putInt("highEnergySwitchHosts", state.highEnergySwitchHosts());
            compoundTag.putInt("networkMultiplier", state.networkMultiplier());
            compoundTag.putLong("batchPerFx", state.batchPerFx().finiteValue());
            compoundTag.putLong("totalBatchCapacity", state.totalBatchCapacity().finiteValue());
            compoundTag.putLong("ftParallelCapacity", state.ftParallelCapacity());
            compoundTag.putBoolean("virtualMode", state.virtualMode());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_crafting_system");
    }
}
