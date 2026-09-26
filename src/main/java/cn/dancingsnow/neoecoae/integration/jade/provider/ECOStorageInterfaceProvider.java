package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECOStorageInterfaceProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("storageMode")) return;
        ECOStorageInterfaceMode mode = ECOStorageInterfaceMode.values()[Math.max(0,
            Math.min(ECOStorageInterfaceMode.values().length - 1, data.getInt("storageMode")))];
        if (mode == ECOStorageInterfaceMode.INPUT) {
            tooltip.add(Component.translatable("jade.neoecoae.storage_interface.input")
                .withStyle(ChatFormatting.BLUE));
        } else if (mode == ECOStorageInterfaceMode.OUTPUT) {
            tooltip.add(Component.translatable("jade.neoecoae.storage_interface.output")
                .withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ECOMachineInterfaceBlockEntity<?> machine
            && machine.getCluster() instanceof NEStorageCluster) {
            ECOStorageInterfaceMode mode = machine.getStorageInterfaceMode();
            data.putInt("storageMode", (mode == null ? ECOStorageInterfaceMode.STORAGE : mode).ordinal());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_storage_interface");
    }
}
