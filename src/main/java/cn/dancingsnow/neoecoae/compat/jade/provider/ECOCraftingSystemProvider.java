package cn.dancingsnow.neoecoae.compat.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
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

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        tooltip.add(tooltip.getElementHelper().progress(
            getProgressRatio(data),
            Component.literal(data.getInt("currentTicks") + " / " + data.getInt("totalTicks") + " t"),
            tooltip.getElementHelper().progressStyle().color(0xFFE53935, 0xFF3F3F3F).textColor(0xFFFFFFFF),
            BoxStyle.DEFAULT,
            true
        ));
        tooltip.add(JadeText.energyLine(data.getLong("energyPerTick")));
        tooltip.add(JadeText.energyMultiplierLine(data.getDouble("energyMultiplier")));
        tooltip.add(JadeText.timeMultiplierLine(data.getDouble("timeMultiplier")));
        tooltip.add(JadeText.overclockLine(data.getInt("effectiveOverclock"), data.getInt("theoreticalOverclock")));
        tooltip.add(JadeText.parallelRecipesLine(data.getLong("parallelRecipes")));
        tooltip.add(JadeText.structureLine(data.getBoolean("formed")));
        tooltip.add(JadeText.onlineLine(data.getBoolean("online")));
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ECOCraftingSystemBlockEntity system) {
            ECOCraftingWorkerBlockEntity.ThreadProgressSummary progress = system.getThreadProgressSummary();
            int totalTicks = Math.max(1, system.getTheoreticalCraftTicks());
            int progressPerTick = Math.max(1, system.getProgressPerTick());
            boolean running = progress.busyThreadCount() > 0 || system.isRunning();
            int currentTicks = progress.maxProgress() <= 0 ? 0 : (int) Math.ceil(progress.maxProgress() / (double) progressPerTick);
            currentTicks = Math.min(totalTicks, currentTicks);
            if (running && totalTicks <= 1) {
                currentTicks = 1;
            }

            tag.putBoolean("formed", system.isFormed());
            tag.putBoolean("online", system.isFormed() && system.getMainNode().isActive());
            tag.putBoolean("running", running);
            tag.putInt("currentTicks", currentTicks);
            tag.putInt("totalTicks", totalTicks);
            tag.putLong("energyPerTick", system.getCurrentEnergyPerTick());
            tag.putDouble("energyMultiplier", system.getEnergyMultiplier());
            tag.putDouble("timeMultiplier", system.getTimeMultiplier());
            tag.putInt("theoreticalOverclock", system.getOverlockTimes());
            tag.putInt("effectiveOverclock", system.getEffectiveOverclockTimes());
            tag.putLong("parallelRecipes", system.getThreadCount());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_crafting_system");
    }

    private static float getProgressRatio(CompoundTag data) {
        int totalTicks = Math.max(1, data.getInt("totalTicks"));
        int currentTicks = Math.max(0, data.getInt("currentTicks"));
        float ratio = (float) (currentTicks / Math.max(1.0D, (double) totalTicks));
        ratio = Math.min(1.0F, Math.max(0.0F, ratio));
        return currentTicks > 0 ? Math.max(0.01F, ratio) : ratio;
    }
}
