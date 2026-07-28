package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

public enum ECOCraftingWorkerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final int MAX_TASK_PREVIEWS = 5;
    private static final String TASKS_TAG = "tasks";
    private static final String TASK_COUNT_TAG = "taskCount";

    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        CompoundTag data = blockAccessor.getServerData();
        if (data.contains("running") && data.contains("max")) {
            int max = data.getInt("max");
            int running = data.getInt("running");
            iTooltip.add(Component.translatable("jade.neoecoae.worker_threads", running, max));
        }
        if (data.contains(TASKS_TAG, Tag.TAG_LIST)) {
            ListTag tasks = data.getList(TASKS_TAG, Tag.TAG_COMPOUND);
            int totalTasks = Math.max(tasks.size(), data.getInt(TASK_COUNT_TAG));
            if (!tasks.isEmpty()) {
                iTooltip.add(Component.translatable("jade.neoecoae.worker_tasks", totalTasks));
            }
            for (int i = 0; i < tasks.size(); i++) {
                CompoundTag task = tasks.getCompound(i);
                ItemStack output = ItemStack.parseOptional(
                    blockAccessor.getLevel().registryAccess(), task.getCompound("output")
                );
                Component outputName = output.isEmpty()
                    ? Component.translatable("jade.neoecoae.worker_task.unknown")
                    : output.getHoverName();
                Component status = task.getBoolean("waitingOutput")
                    ? Component.translatable("jade.neoecoae.worker_task.waiting_output")
                    : Component.translatable("jade.neoecoae.worker_task.progress", task.getInt("progress"));
                iTooltip.add(Component.translatable(
                    "jade.neoecoae.worker_task",
                    outputName,
                    Long.toString(Math.max(0L, task.getLong("amount"))),
                    status
                ));
            }
            if (totalTasks > tasks.size()) {
                iTooltip.add(Component.translatable(
                    "jade.neoecoae.worker_tasks.more", totalTasks - tasks.size()
                ));
            }
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
            List<ECOCraftingThread.Snapshot> snapshots = worker.getThreadSnapshots();
            compoundTag.putInt(TASK_COUNT_TAG, snapshots.size());
            ListTag tasks = new ListTag();
            for (int i = 0; i < Math.min(MAX_TASK_PREVIEWS, snapshots.size()); i++) {
                ECOCraftingThread.Snapshot snapshot = snapshots.get(i);
                CompoundTag task = new CompoundTag();
                ItemStack output = snapshot.outputItem().copyWithCount(1);
                task.put("output", output.saveOptional(blockAccessor.getLevel().registryAccess()));
                task.putLong("amount", snapshot.outputAmount());
                int maxProgress = Math.max(1, snapshot.maxProgress());
                task.putInt("progress", Math.clamp((int)(snapshot.progress() * 100L / maxProgress), 0, 100));
                task.putBoolean("waitingOutput", snapshot.outputsReady());
                tasks.add(task);
            }
            compoundTag.put(TASKS_TAG, tasks);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_crafting_worker");
    }
}
