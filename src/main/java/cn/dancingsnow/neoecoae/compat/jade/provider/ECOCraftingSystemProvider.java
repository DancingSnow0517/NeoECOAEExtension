package cn.dancingsnow.neoecoae.compat.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
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

public enum ECOCraftingSystemProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String KEY_FORMED = "formed";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_CURRENT_TICKS = "currentTicks";
    private static final String KEY_TOTAL_TICKS = "totalTicks";
    private static final String KEY_ENERGY_PER_TICK = "energyPerTick";
    private static final String KEY_TIME_MULTIPLIER = "timeMultiplier";
    private static final String KEY_THEORETICAL_OVERCLOCK = "theoreticalOverclock";
    private static final String KEY_EFFECTIVE_OVERCLOCK = "effectiveOverclock";

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();

        boolean running = data.getBoolean(KEY_RUNNING);
        int currentTicks = data.getInt(KEY_CURRENT_TICKS);
        int totalTicks = data.getInt(KEY_TOTAL_TICKS);

        if (running && currentTicks > 0 && totalTicks > 0) {
            tooltip.add(tooltip.getElementHelper()
                    .progress(
                            getProgressRatio(data),
                            Component.literal(currentTicks + " / " + totalTicks + " t"),
                            tooltip.getElementHelper()
                                    .progressStyle()
                                    .color(0xFF4FC3F7)
                                    .textColor(0xFFFFFFFF),
                            BoxStyle.DEFAULT,
                            true));
        }

        tooltip.add(JadeText.energyLine(data.getLong(KEY_ENERGY_PER_TICK)));
        tooltip.add(JadeText.timeMultiplierLine(data.getDouble(KEY_TIME_MULTIPLIER)));
        tooltip.add(
                JadeText.overclockLine(data.getInt(KEY_EFFECTIVE_OVERCLOCK), data.getInt(KEY_THEORETICAL_OVERCLOCK)));

        // TODO: 显示"工作合成：xxx 个配方"。
        // 当前 ECOCraftingSystemBlockEntity 无法直接读取 ECOCraftingCPULogic.job.waitingFor。
        // 需要在 ECOCraftingSystemBlockEntity 中暴露 getWorkingCrafts()，
        // 返回 job.waitingFor 中各 key 的总计数，与 AE2 合成状态界面的"工作合成"对齐。
        // 暴露后使用 JadeText.workingCraftsLine(workingCrafts) 替换此注释。

        // 让原有通用 provider / Jade 默认逻辑去显示，避免重复出现两行“设备在线”。
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ECOCraftingSystemBlockEntity system) {
            ECOCraftingWorkerBlockEntity.ThreadProgressSummary progress = system.getThreadProgressSummary();

            int totalTicks = Math.max(1, system.getTheoreticalCraftTicks());
            int progressPerTick = Math.max(1, system.getProgressPerTick());

            boolean running = progress.busyThreadCount() > 0 || system.isRunning();

            int currentTicks = 0;
            if (running) {
                if (totalTicks <= 1) {
                    currentTicks = 1;
                } else if (progress.maxProgress() > 0) {
                    currentTicks = (int) Math.ceil(progress.maxProgress() / (double) progressPerTick);
                    currentTicks = Math.min(totalTicks, Math.max(1, currentTicks));
                }
            }

            tag.putBoolean(KEY_FORMED, system.isFormed());
            tag.putBoolean(KEY_RUNNING, running);
            tag.putInt(KEY_CURRENT_TICKS, currentTicks);
            tag.putInt(KEY_TOTAL_TICKS, totalTicks);
            tag.putLong(KEY_ENERGY_PER_TICK, Math.max(0L, system.getCurrentEnergyPerTick()));
            tag.putDouble(KEY_TIME_MULTIPLIER, system.getTimeMultiplier());
            tag.putInt(KEY_THEORETICAL_OVERCLOCK, system.getOverlockTimes());
            tag.putInt(KEY_EFFECTIVE_OVERCLOCK, system.getEffectiveOverclockTimes());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_crafting_system");
    }

    private static float getProgressRatio(CompoundTag data) {
        int totalTicks = Math.max(1, data.getInt(KEY_TOTAL_TICKS));
        int currentTicks = Math.max(0, data.getInt(KEY_CURRENT_TICKS));

        float ratio = (float) (currentTicks / Math.max(1.0D, (double) totalTicks));
        ratio = Math.min(1.0F, Math.max(0.0F, ratio));

        return currentTicks > 0 ? Math.max(0.01F, ratio) : 0.0F;
    }
}
