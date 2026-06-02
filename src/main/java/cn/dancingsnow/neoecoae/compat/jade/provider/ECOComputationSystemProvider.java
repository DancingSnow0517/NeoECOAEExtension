package cn.dancingsnow.neoecoae.compat.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
        tooltip.add(Component.translatable("jade.neoecoae.formed", yesNo(data.getBoolean("formed"))));
        tooltip.add(Component.translatable("jade.neoecoae.running", yesNo(data.getBoolean("running"))));
        tooltip.add(Component.translatable("jade.neoecoae.computation.accelerators", data.getInt("acceleratorCount")));
        tooltip.add(Component.translatable("jade.neoecoae.computation.dispatch_limit", data.getInt("dispatchLimit")));
        tooltip.add(Component.translatable(
            "jade.neoecoae.computation.thread_usage",
            data.getInt("usedThread"),
            data.getInt("totalThread")
        ));
        tooltip.add(Component.translatable(
            "jade.neoecoae.computation.storage_usage",
            data.getLong("usedStorage"),
            data.getLong("totalStorage")
        ));
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ECOComputationSystemBlockEntity system) {
            long totalStorage = system.getTotalBytes();
            long availableStorage = system.getAvailableBytes();
            long usedStorage = Math.max(0L, totalStorage - availableStorage);
            int acceleratorCount = system.getAcceleratorCount();
            tag.putBoolean("formed", system.isFormed());
            tag.putBoolean("running", system.isRunning());
            tag.putInt("acceleratorCount", acceleratorCount);
            tag.putInt("dispatchLimit", acceleratorCount + 1);
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

    private static Component yesNo(boolean value) {
        return Component.translatable(value ? "jade.neoecoae.yes" : "jade.neoecoae.no");
    }
}
