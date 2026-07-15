package cn.dancingsnow.neoecoae.integration.jade.provider;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
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
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data.getBoolean("infiniteMember")) {
            tooltip.add(Component.translatable("tooltip.neoecoae.storage.infinite_member")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        if (data.contains("mounted")) {
            boolean mounted = data.getBoolean("mounted");
            if (mounted) {
                tooltip.add(
                        Component.translatable("jade.neoecoae.drive_mounted").withStyle(ChatFormatting.GREEN));
            } else if (data.contains("storageInterfaceMode")) {
                ECOStorageInterfaceMode mode = ECOStorageInterfaceMode.byName(data.getString("storageInterfaceMode"));
                if (mode == ECOStorageInterfaceMode.INPUT) {
                    tooltip.add(Component.translatable("jade.neoecoae.drive_input_mode")
                            .withStyle(ChatFormatting.BLUE));
                } else if (mode == ECOStorageInterfaceMode.OUTPUT) {
                    tooltip.add(Component.translatable("jade.neoecoae.drive_output_mode")
                            .withStyle(ChatFormatting.BLUE));
                } else {
                    tooltip.add(Component.translatable("jade.neoecoae.drive_unmounted")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
            } else {
                tooltip.add(
                        Component.translatable("jade.neoecoae.drive_unmounted").withStyle(ChatFormatting.RED));
                return;
            }
        }
        if (data.contains("usedBytes") && data.contains("totalBytes")) {
            tooltip.add(Tooltips.bytesUsed(data.getLong("usedBytes"), data.getLong("totalBytes")));
        }
        if (data.contains("storedItemTypes") && data.contains("totalItemTypes")) {
            tooltip.add(Tooltips.typesUsed(data.getLong("storedItemTypes"), data.getLong("totalItemTypes")));
        }
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ECODriveBlockEntity drive) {
            tag.putBoolean("infiniteMember", ECOInfiniteStorageMember.isMember(drive.getCellStack()));
            tag.putBoolean("mounted", drive.isMounted());
            tag.putString(
                    "storageInterfaceMode", drive.getStorageInterfaceMode().name());
            IECOStorageCell cellInventory = drive.getCellInventory();
            if (cellInventory != null) {
                tag.putLong("usedBytes", cellInventory.getUsedBytes());
                tag.putLong("totalBytes", cellInventory.getTotalBytes());
                tag.putLong("storedItemTypes", cellInventory.getStoredItemTypes());
                tag.putLong("totalItemTypes", cellInventory.getTotalItemTypes());
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_drive");
    }
}
