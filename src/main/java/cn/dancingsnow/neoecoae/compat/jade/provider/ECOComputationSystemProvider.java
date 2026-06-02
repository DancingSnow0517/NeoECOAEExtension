package cn.dancingsnow.neoecoae.compat.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECOComputationSystemProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        tooltip.add(JadeText.threadLine(data.getInt("usedThread"), data.getInt("totalThread")));
        tooltip.add(JadeText.storageLine(data.getLong("usedStorage"), data.getLong("totalStorage")));
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ECOComputationSystemBlockEntity system) {
            long totalStorage = system.getTotalBytes();
            long availableStorage = system.getAvailableBytes();
            long usedStorage = Math.max(0L, totalStorage - availableStorage);
            tag.putBoolean("formed", system.isFormed());
            tag.putBoolean("online", system.isFormed() && system.getMainNode().isActive());
            tag.putInt("usedThread", system.getUsedThread());
            tag.putInt("totalThread", system.getTotalThread());
            tag.putLong("usedStorage", usedStorage);
            tag.putLong("totalStorage", totalStorage);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_computation_system");
    }
}
