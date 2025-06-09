package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
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
            iTooltip.add(Component.translatable("jade.neoecoae.worker_threads", running, max));
        }
    }

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof ECOCraftingWorkerBlockEntity worker) {
            if (worker.getCluster() != null && worker.getCluster().getController() != null) {
                int max = worker.getCluster().getController().getThreadCountPerWorker();
                int running = worker.getRunningThreads();
                compoundTag.putInt("running", running);
                compoundTag.putInt("max", max);
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_crafting_worker");
    }
}
