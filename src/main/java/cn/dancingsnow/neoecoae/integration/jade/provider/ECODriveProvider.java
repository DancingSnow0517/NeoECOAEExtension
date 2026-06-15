package cn.dancingsnow.neoecoae.integration.jade.provider;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECODriveProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof ECODriveBlockEntity be) {
            compoundTag.putBoolean("mounted", be.isMounted());
            IECOStorageCell cellInventory = be.getCellInventory();
            if (cellInventory != null) {
                compoundTag.putLong("usedBytes", cellInventory.getUsedBytes());
                compoundTag.putLong("totalBytes", cellInventory.getTotalBytes());
                compoundTag.putLong("storedItemTypes", cellInventory.getStoredItemTypes());
                compoundTag.putLong("totalItemTypes", cellInventory.getTotalItemTypes());
            }
        }
    }


    @Override
    public Identifier getUid() {
        return NeoECOAE.id("eco_drive");
    }

    public enum Client implements IBlockComponentProvider {
        INSTANCE;


        @Override
        public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
            CompoundTag serverData = blockAccessor.getServerData();
            var mounted = serverData.getBoolean("mounted");
            if (mounted.isPresent()) {
                if (mounted.get()) {
                    iTooltip.add(Component.translatable("jade.neoecoae.drive_mounted").withStyle(ChatFormatting.GREEN));
                } else {
                    iTooltip.add(Component.translatable("jade.neoecoae.drive_unmounted").withStyle(ChatFormatting.RED));
                    return;
                }
            }
            var usedBytes = serverData.getLong("usedBytes");
            var totalBytes = serverData.getLong("totalBytes");
            if (usedBytes.isPresent() && totalBytes.isPresent()) {
                iTooltip.add(Tooltips.bytesUsed(usedBytes.get(), totalBytes.get()));
            }

            var storedItemTypes = serverData.getLong("storedItemTypes");
            var totalItemTypes = serverData.getLong("totalItemTypes");
            if (storedItemTypes.isPresent() && totalItemTypes.isPresent()) {
                iTooltip.add(Tooltips.typesUsed(storedItemTypes.get(), totalItemTypes.get()));
            }
        }

        @Override
        public Identifier getUid() {
            return NeoECOAE.id("eco_drive");
        }
    }
}
