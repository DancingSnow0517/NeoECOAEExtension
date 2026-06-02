package cn.dancingsnow.neoecoae.compat.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECOCraftingSystemProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        tooltip.add(JadeText.threadLine(data.getInt("runningThreadCount"), data.getInt("threadCount")));
        tooltip.add(JadeText.overclockLine(data.getInt("effectiveOverclock"), data.getInt("theoreticalOverclock")));
        tooltip.add(JadeText.progressLine(data.getInt("craftTicks")));
        tooltip.add(JadeText.coolantLine(data.getLong("coolant"), data.getInt("theoreticalOverclock")));
        tooltip.add(JadeText.onlineLine(data.getBoolean("formed")));
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ECOCraftingSystemBlockEntity system) {
            tag.putBoolean("formed", system.isFormed());
            tag.putInt("runningThreadCount", system.getRunningThreadCount());
            tag.putInt("threadCount", system.getThreadCount());
            tag.putInt("coolant", system.getCoolant());
            tag.putInt("theoreticalOverclock", system.getOverlockTimes());
            tag.putInt("effectiveOverclock", system.getEffectiveOverclockTimes());
            tag.putInt("craftTicks", system.getTheoreticalCraftTicks());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_crafting_system");
    }
}
