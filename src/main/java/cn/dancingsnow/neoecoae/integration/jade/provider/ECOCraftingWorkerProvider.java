package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECOCraftingWorkerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        tooltip.add(JadeText.runningLine(data.getBoolean("running")));
        tooltip.add(JadeText.recipesPerOperationLine(data.getInt("recipesPerOperation")));
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ECOCraftingWorkerBlockEntity worker) {
            ECOCraftingSystemBlockEntity controller =
                    worker.getCluster() == null ? null : worker.getCluster().getController();
            int threadCountPerWorker = controller == null ? 0 : controller.getThreadCountPerWorker();
            tag.putBoolean(
                    "online",
                    worker.getCluster() != null && worker.getMainNode().isActive());
            tag.putBoolean("running", worker.isWorking());
            tag.putInt("recipesPerOperation", threadCountPerWorker);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_crafting_worker");
    }
}
