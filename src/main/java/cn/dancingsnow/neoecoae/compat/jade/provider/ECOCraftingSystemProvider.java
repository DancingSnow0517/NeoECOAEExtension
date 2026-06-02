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

public enum ECOCraftingSystemProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        tooltip.add(Component.translatable("jade.neoecoae.formed", yesNo(data.getBoolean("formed"))));
        tooltip.add(Component.translatable("jade.neoecoae.running", yesNo(data.getBoolean("running"))));
        tooltip.add(Component.translatable("jade.neoecoae.crafting.worker_count", data.getInt("workerCount")));
        tooltip.add(Component.translatable(
            "jade.neoecoae.crafting.thread_usage",
            data.getInt("runningThreadCount"),
            data.getInt("threadCount")
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

        if (data.getBoolean("overclocked")) {
            tooltip.add(Component.translatable("jade.neoecoae.overclocked"));
        }
        if (data.getBoolean("activeCooling")) {
            tooltip.add(Component.translatable("jade.neoecoae.activeCooling"));
        }
        tooltip.add(Component.translatable("jade.neoecoae.coolant", data.getInt("coolant")));
        int coolingMaxOverclock = data.getInt("coolingMaxOverclock");
        tooltip.add(coolingMaxOverclock >= 0
            ? Component.translatable("jade.neoecoae.coolant_max_overclock", coolingMaxOverclock)
            : Component.translatable("jade.neoecoae.coolant_max_overclock.none"));
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ECOCraftingSystemBlockEntity system) {
            ECOCraftingWorkerBlockEntity.ThreadProgressSummary progress = system.getThreadProgressSummary();
            tag.putBoolean("formed", system.isFormed());
            tag.putBoolean("running", system.isRunning());
            tag.putInt("workerCount", system.getWorkerCount());
            tag.putInt("runningThreadCount", system.getRunningThreadCount());
            tag.putInt("threadCount", system.getThreadCount());
            tag.putInt("threadCountPerWorker", system.getThreadCountPerWorker());
            tag.putInt("batchSlots", system.getCurrentBatchSlots());
            tag.putBoolean("overclocked", system.isOverclocked());
            tag.putBoolean("activeCooling", system.isActiveCooling());
            tag.putInt("coolant", system.getCoolant());
            tag.putInt("theoreticalOverclock", system.getOverlockTimes());
            tag.putInt("effectiveOverclock", system.getEffectiveOverclockTimes());
            tag.putInt("coolingMaxOverclock", system.getDisplayedCoolingMaxOverclock());
            tag.putInt("progressPerTick", system.getProgressPerTick());
            tag.putInt("theoreticalCraftTicks", system.getTheoreticalCraftTicks());
            tag.putInt("busyThreadCount", progress.busyThreadCount());
            tag.putInt("occupiedSlots", progress.occupiedSlots());
            tag.putInt("nextProgress", progress.maxProgress());
            tag.putInt("averageProgress", progress.averageProgress());
            tag.putInt("maxProgress", ECOCraftingThread.MAX_PROGRESS);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_crafting_system");
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(value ? "jade.neoecoae.yes" : "jade.neoecoae.no");
    }
}
