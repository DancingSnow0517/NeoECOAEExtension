package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECOCraftingWorkerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;
    private static final int MAX_DISPLAYED_CRAFTS = 4;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        tooltip.add(JadeText.runningLine(data.getBoolean("running")));
        tooltip.add(JadeText.recipesPerOperationLine(data.getInt("recipesPerOperation")));
        ListTag crafts = data.getList("activeCrafts", 10);
        for (int i = 0; i < crafts.size(); i++) {
            CompoundTag craft = crafts.getCompound(i);
            ItemStack output = ItemStack.of(craft.getCompound("output"));
            if (!output.isEmpty()) {
                tooltip.add(JadeText.activeCraftLine(
                        output, craft.getInt("slots"), craft.getInt("progress"), craft.getInt("maxProgress")));
            }
        }
        int hiddenCrafts = data.getInt("hiddenCrafts");
        if (hiddenCrafts > 0) {
            tooltip.add(JadeText.moreActiveCraftsLine(hiddenCrafts));
        }
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
            List<ECOCraftingThread.Snapshot> snapshots = worker.getThreadSnapshots();
            ListTag crafts = new ListTag();
            for (int i = 0; i < Math.min(MAX_DISPLAYED_CRAFTS, snapshots.size()); i++) {
                ECOCraftingThread.Snapshot snapshot = snapshots.get(i);
                ItemStack output = snapshot.outputItem();
                if (output.isEmpty()) {
                    continue;
                }
                CompoundTag craft = new CompoundTag();
                craft.put("output", output.save(new CompoundTag()));
                craft.putInt("slots", snapshot.occupiedThreadSlots());
                craft.putInt("progress", snapshot.progress());
                craft.putInt("maxProgress", snapshot.maxProgress());
                crafts.add(craft);
            }
            tag.put("activeCrafts", crafts);
            tag.putInt("hiddenCrafts", Math.max(0, snapshots.size() - MAX_DISPLAYED_CRAFTS));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_crafting_worker");
    }
}
