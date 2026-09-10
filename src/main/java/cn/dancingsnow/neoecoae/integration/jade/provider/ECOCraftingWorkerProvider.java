package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.api.me.CraftingCapabilitySnapshot;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECOCraftingWorkerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        CompoundTag data = blockAccessor.getServerData();
        if (data.contains("max")) {
            long max = data.getLong("max");
            boolean infinite = data.getBoolean("infinite");
            int normalHosts = data.getInt("normalSwitchHosts");
            int highEnergyHosts = data.getInt("highEnergySwitchHosts");
            Component normalSwitches = Component.translatable("jade.neoecoae.normal_switch")
                .withStyle(ChatFormatting.GREEN);
            Component highEnergySwitches = Component.translatable("jade.neoecoae.high_energy_switch")
                .withStyle(ChatFormatting.GOLD);
            iTooltip.add(Component.translatable("jade.neoecoae.network_composition",
                normalHosts, normalSwitches, highEnergyHosts, highEnergySwitches));
            Component batchCapacity = infinite
                ? Component.translatable("gui.neoecoae.storage.infinite_value").withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.literal(String.format(Locale.ROOT, "%,d", max))
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
            iTooltip.add(Component.translatable("jade.neoecoae.crafting_capacity", batchCapacity));
            if ((normalHosts > 0 || highEnergyHosts > 0) && blockAccessor.getPlayer().isShiftKeyDown()) {
                long base = data.getLong("baseCapacity");
                String terms = normalHosts > 0 && highEnergyHosts > 0
                    ? normalHosts + " x 2 + " + highEnergyHosts + " x 8"
                    : normalHosts > 0 ? normalHosts + " x 2" : highEnergyHosts + " x 8";
                String formula = base + " x (" + terms + ") = " + (infinite ? "∞" : max);
                iTooltip.add(Component.translatable("jade.neoecoae.worker_capacity_formula", formula)
                    .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof ECOCraftingWorkerBlockEntity worker) {
            if (worker.getCluster() != null && worker.getCluster().getController() != null) {
                var controller = worker.getCluster().getController();
                CraftingCapabilitySnapshot state = controller.getCapabilitySnapshot();
                long max = state.batchPerFx().finiteValue();
                compoundTag.putLong("max", max);
                compoundTag.putLong("baseCapacity", CraftingCapabilitySnapshot.F9_OVERCLOCKED_BATCH_PER_FX);
                compoundTag.putBoolean("infinite", state.batchPerFx().unlimited());
                compoundTag.putInt("normalSwitchHosts", state.normalSwitchHosts());
                compoundTag.putInt("highEnergySwitchHosts", state.highEnergySwitchHosts());
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_crafting_worker");
    }
}
