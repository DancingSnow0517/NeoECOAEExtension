package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.api.me.network.CraftingCapabilitySnapshot;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECOCraftingSystemProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof ECOCraftingSystemBlockEntity system) {
            CraftingCapabilitySnapshot state = system.getCapabilitySnapshot();
            compoundTag.putBoolean("overclocked", system.isOverclocked());
            compoundTag.putBoolean("activeCooling", state.coolantState().activeCooling());
            compoundTag.putInt("coolant", (int) Math.min(Integer.MAX_VALUE, state.coolantState().amount()));
            compoundTag.putInt("coolingMaxOverclock", state.coolantState().maxSupportedOverclock());
            compoundTag.putInt("normalSwitchHosts", state.normalSwitchHosts());
            compoundTag.putInt("highEnergySwitchHosts", state.highEnergySwitchHosts());
            compoundTag.putLong("totalBatchCapacity", state.totalBatchCapacity().finiteValue());
            compoundTag.putBoolean("virtualMode", state.virtualMode());
        }
    }

    @Override
    public Identifier getUid() {
        return NeoECOAE.id("eco_crafting_system");
    }

    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
            CompoundTag data = blockAccessor.getServerData();
            if (data.contains("overclocked")) {
                boolean overclocked = data.getBooleanOr("overclocked", false);
                iTooltip.add(Component.translatable(overclocked
                        ? "jade.neoecoae.overclocked"
                        : "jade.neoecoae.overclock_disabled")
                    .withStyle(overclocked ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            }
            if (data.contains("activeCooling")) {
                boolean activeCooling = data.getBooleanOr("activeCooling", false);
                iTooltip.add(Component.translatable(activeCooling
                        ? "jade.neoecoae.activeCooling"
                        : "jade.neoecoae.active_cooling_disabled")
                    .withStyle(activeCooling ? ChatFormatting.AQUA : ChatFormatting.GRAY));
            }
            if (data.contains("coolant")) {
                iTooltip.add(Component.translatable("jade.neoecoae.coolant",
                    Component.literal(data.getIntOr("coolant", 0) + " mB").withStyle(ChatFormatting.BLUE)));
            }
            if (data.contains("coolingMaxOverclock")) {
                int coolingMaxOverclock = data.getIntOr("coolingMaxOverclock", 0);
                if (coolingMaxOverclock >= 0) {
                    iTooltip.add(Component.translatable("jade.neoecoae.coolant_max_overclock", coolingMaxOverclock));
                } else {
                    iTooltip.add(Component.translatable("jade.neoecoae.coolant_max_overclock.none"));
                }
            }
            if (data.contains("normalSwitchHosts")) {
                boolean virtual = data.getBooleanOr("virtualMode", false);
                Component normalSwitches = Component.translatable("jade.neoecoae.normal_switch")
                    .withStyle(ChatFormatting.GREEN);
                Component highEnergySwitches = Component.translatable("jade.neoecoae.high_energy_switch")
                    .withStyle(ChatFormatting.GOLD);
                iTooltip.add(Component.translatable("jade.neoecoae.network_composition",
                    data.getIntOr("normalSwitchHosts", 0), normalSwitches,
                    data.getIntOr("highEnergySwitchHosts", 0), highEnergySwitches));
                Component total = virtual ? Component.translatable("gui.neoecoae.storage.infinite_value")
                    : Component.literal(String.format(Locale.ROOT, "%,d", data.getLongOr("totalBatchCapacity", 0L)))
                        .withStyle(ChatFormatting.LIGHT_PURPLE);
                iTooltip.add(Component.translatable("jade.neoecoae.total_crafting_capacity", total));
            }
        }

        @Override
        public Identifier getUid() {
            return NeoECOAE.id("eco_crafting_system");
        }
    }
}
