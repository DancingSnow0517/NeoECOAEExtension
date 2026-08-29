package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
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
        if (data.contains("running") && data.contains("max")) {
            int max = data.getInt("max");
            int running = data.getInt("running");
            boolean infinite = data.getBoolean("infinite");
            int normalHosts = data.getInt("normalSwitchHosts");
            int highEnergyHosts = data.getInt("highEnergySwitchHosts");
            if (normalHosts > 0) {
                iTooltip.add(Component.translatable("jade.neoecoae.worker_network_x2", normalHosts)
                    .withStyle(ChatFormatting.GREEN));
            }
            if (highEnergyHosts > 0) {
                iTooltip.add(Component.translatable("jade.neoecoae.worker_network_x8", highEnergyHosts)
                    .withStyle(ChatFormatting.GREEN));
            }
            iTooltip.add(Component.translatable("jade.neoecoae.worker_threads", running, infinite ? "∞" : max));
            if ((normalHosts > 0 || highEnergyHosts > 0) && blockAccessor.getPlayer().isShiftKeyDown()) {
                int base = data.getInt("baseCapacity");
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
                int max = controller.getThreadCountForWorker(worker);
                int running = worker.getRunningThreads();
                compoundTag.putInt("running", running);
                compoundTag.putInt("max", max);
                compoundTag.putInt("baseCapacity", controller.getLocalThreadCountForWorker(worker));
                compoundTag.putBoolean("infinite", controller.isFullVirtualCraftingMode());
                NECraftingNetworkCluster network = worker.getCluster().getNetworkCluster();
                if (network != null) {
                    compoundTag.putInt("normalSwitchHosts", network.getNormalSwitchHostCount());
                    compoundTag.putInt("highEnergySwitchHosts", network.getHighEnergySwitchHostCount());
                }
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_crafting_worker");
    }
}
