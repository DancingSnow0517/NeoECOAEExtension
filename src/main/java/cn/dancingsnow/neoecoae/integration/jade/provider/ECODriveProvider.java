package cn.dancingsnow.neoecoae.integration.jade.provider;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.items.cell.ECOStorageCell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECODriveProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        CompoundTag serverData = blockAccessor.getServerData();
        if (serverData.contains("usedBytes") && serverData.contains("totalBytes")) {
            iTooltip.add(Tooltips.bytesUsed(serverData.getLong("usedBytes"),serverData.getLong("totalBytes")));
        }
        if (serverData.contains("storedItemTypes") && serverData.contains("totalItemTypes")) {
            iTooltip.add(Tooltips.typesUsed(serverData.getLong("storedItemTypes"), serverData.getLong("totalItemTypes")));
        }
    }

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof ECODriveBlockEntity be) {
            ECOStorageCell cellInventory = be.getCellInventory();
            if (cellInventory != null) {
                compoundTag.putLong("usedBytes", cellInventory.getUsedBytes());
                compoundTag.putLong("totalBytes", cellInventory.getTotalBytes());
                compoundTag.putLong("storedItemTypes", cellInventory.getStoredItemTypes());
                compoundTag.putLong("totalItemTypes", cellInventory.getTotalItemTypes());
            }
        }
    }



    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_drive");
    }
}
