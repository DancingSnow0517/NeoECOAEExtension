package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.impl.storage.ECOInfiniteResourceCell;
import cn.dancingsnow.neoecoae.items.CellTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
        if (serverData.getBoolean("tierMismatch")) {
            iTooltip.add(Component.translatable("jade.neoecoae.storage_matrix_tier_too_high")
                .withStyle(ChatFormatting.RED));
        }
        if (serverData.getBoolean("infiniteMember")) {
            iTooltip.add(CellTooltips.infiniteMemberHint());
            return;
        }
        if (serverData.getBoolean("infiniteResource")) {
            if (serverData.getBoolean("mounted")) {
                iTooltip.add(Component.translatable("jade.neoecoae.drive_mounted").withStyle(ChatFormatting.GREEN));
            }
            return;
        }
        if (serverData.contains("mounted")) {
            boolean mounted = serverData.getBoolean("mounted");
            if (mounted) {
                iTooltip.add(Component.translatable("jade.neoecoae.drive_mounted").withStyle(ChatFormatting.GREEN));
            } else {
                iTooltip.add(Component.translatable("jade.neoecoae.drive_unmounted").withStyle(ChatFormatting.RED));
                return;
            }
        }
        if (serverData.contains("usedBytes") && serverData.contains("totalBytes")) {
            iTooltip.add(CellTooltips.bytesUsed(serverData.getLong("usedBytes"), serverData.getLong("totalBytes")));
        }
        if (serverData.contains("storedItemTypes") && serverData.contains("totalItemTypes")) {
            iTooltip.add(CellTooltips.typesUsed(
                serverData.getLong("storedItemTypes"),
                serverData.getLong("totalItemTypes"),
                serverData.getBoolean("infiniteTypeCapacity")));
        }
    }

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof ECODriveBlockEntity be) {
            compoundTag.putBoolean("infiniteMember", ECOInfiniteStorageMember.isMember(be.getCellStack()));
            compoundTag.putBoolean("infiniteResource", be.getCellInventory() instanceof ECOInfiniteResourceCell);
            compoundTag.putBoolean("mounted", be.isMounted());
            IECOStorageCell cellInventory = be.getCellInventory();
            if (cellInventory != null) {
                var controller = be.getStorageController();
                compoundTag.putBoolean("tierMismatch", controller != null
                    && !controller.getTier().supportsComponentTier(cellInventory.getTier()));
                compoundTag.putLong("usedBytes", cellInventory.getUsedBytes());
                compoundTag.putLong("totalBytes", cellInventory.getTotalBytes());
                compoundTag.putLong("storedItemTypes", cellInventory.getStoredItemTypes());
                compoundTag.putLong("totalItemTypes", cellInventory.getTotalItemTypes());
                compoundTag.putBoolean("infiniteTypeCapacity", cellInventory.hasInfiniteTypeCapacity());
            }
        }
    }



    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_drive");
    }
}
