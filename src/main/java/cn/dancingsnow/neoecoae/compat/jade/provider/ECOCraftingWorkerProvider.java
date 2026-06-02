package cn.dancingsnow.neoecoae.compat.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingThread;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;

import java.util.Locale;

public enum ECOCraftingWorkerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        tooltip.add(Component.translatable("jade.neoecoae.formed", yesNo(data.getBoolean("formed"))));
        tooltip.add(Component.translatable("jade.neoecoae.running", yesNo(data.getBoolean("running"))));
        tooltip.add(Component.translatable(
            "jade.neoecoae.crafting.thread_usage",
            data.getInt("runningThreadCount"),
            data.getInt("threadCountPerWorker")
        ));
        tooltip.add(Component.translatable("jade.neoecoae.crafting.queue_per_worker", data.getInt("threadCountPerWorker")));
        tooltip.add(Component.translatable("jade.neoecoae.crafting.batch_slots", data.getInt("batchSlots")));
        tooltip.add(Component.translatable(
            "jade.neoecoae.overclock_status",
            data.getInt("theoreticalOverclock"),
            data.getInt("effectiveOverclock")
        ));
        tooltip.add(Component.translatable("jade.neoecoae.crafting.speed", data.getInt("progressPerTick")));
        tooltip.add(Component.translatable(
            "jade.neoecoae.crafting.duration",
            data.getInt("theoreticalCraftTicks"),
            String.format(Locale.ROOT, "%.2f", data.getInt("theoreticalCraftTicks") / 20.0)
        ));

        int maxProgress = data.getInt("maxProgress");
        int nextProgress = data.getInt("nextProgress");
        if (maxProgress > 0 && data.getInt("busyThreadCount") > 0) {
            float ratio = Math.min(1.0f, Math.max(0.0f, nextProgress / (float) maxProgress));
            tooltip.add(tooltip.getElementHelper().progress(
                ratio,
                Component.translatable("jade.neoecoae.crafting.progress", nextProgress, maxProgress),
                tooltip.getElementHelper().progressStyle().color(0xFF55AAFF, 0xFF243242).textColor(0xFFFFFFFF),
                BoxStyle.DEFAULT,
                true
            ));
            tooltip.add(Component.translatable("jade.neoecoae.crafting.progress_value", nextProgress));
            tooltip.add(Component.translatable("jade.neoecoae.crafting.avg_progress", data.getInt("averageProgress")));
        }
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ECOCraftingWorkerBlockEntity worker) {
            ECOCraftingWorkerBlockEntity.ThreadProgressSummary progress = worker.getThreadProgressSummary();
            ECOCraftingSystemBlockEntity controller = worker.getCluster() == null ? null : worker.getCluster().getController();
            int threadCountPerWorker = controller == null ? 0 : controller.getThreadCountPerWorker();
            tag.putBoolean("formed", worker.getCluster() != null);
            tag.putBoolean("running", worker.isWorking());
            tag.putInt("runningThreadCount", worker.getRunningThreads());
            tag.putInt("threadCountPerWorker", threadCountPerWorker);
            tag.putInt("batchSlots", controller == null ? 0 : controller.getCurrentBatchSlots());
            tag.putInt("theoreticalOverclock", controller == null ? 0 : controller.getOverlockTimes());
            tag.putInt("effectiveOverclock", controller == null ? 0 : controller.getEffectiveOverclockTimes());
            tag.putInt("progressPerTick", controller == null ? 10 : controller.getProgressPerTick());
            tag.putInt("theoreticalCraftTicks", controller == null ? 10 : controller.getTheoreticalCraftTicks());
            tag.putInt("busyThreadCount", progress.busyThreadCount());
            tag.putInt("occupiedSlots", progress.occupiedSlots());
            tag.putInt("nextProgress", progress.maxProgress());
            tag.putInt("averageProgress", progress.averageProgress());
            tag.putInt("maxProgress", ECOCraftingThread.MAX_PROGRESS);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_crafting_worker");
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(value ? "jade.neoecoae.yes" : "jade.neoecoae.no");
    }
}
