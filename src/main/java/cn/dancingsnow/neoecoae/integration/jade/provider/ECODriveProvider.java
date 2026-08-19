package cn.dancingsnow.neoecoae.integration.jade.provider;

import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
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

public enum ECODriveProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        CompoundTag serverData = blockAccessor.getServerData();
        if (serverData.getBoolean("infiniteMember")) {
            iTooltip.add(Component.translatable("tooltip.neoecoae.storage.infinite_member")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        if (serverData.contains("storageInterfaceMode")) {
            int mode = serverData.getInt("storageInterfaceMode");
            if (mode == ECOStorageInterfaceMode.INPUT.ordinal()) {
                iTooltip.add(Component.translatable("jade.neoecoae.drive_input_mode")
                    .withStyle(ChatFormatting.GREEN));
                return;
            }
            if (mode == ECOStorageInterfaceMode.OUTPUT.ordinal()) {
                iTooltip.add(Component.translatable("jade.neoecoae.drive_output_mode")
                    .withStyle(ChatFormatting.GREEN));
                return;
            }
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
            iTooltip.add(Tooltips.bytesUsed(serverData.getLong("usedBytes"),serverData.getLong("totalBytes")));
        }
        if (serverData.contains("storedItemTypes") && serverData.contains("totalItemTypes")) {
            long storedItemTypes = serverData.getLong("storedItemTypes");
            if (serverData.getBoolean("infiniteTypeCapacity")) {
                iTooltip.add(Tooltips.of(
                    Tooltips.ofUnformattedNumberWithRatioColor(storedItemTypes, 0, false),
                    Tooltips.of(" "),
                    Tooltips.of(GuiText.Of),
                    Tooltips.of(" "),
                    Component.literal("∞").withStyle(Tooltips.NUMBER_TEXT),
                    Tooltips.of(" "),
                    Tooltips.of(GuiText.Types)
                ));
            } else {
                iTooltip.add(Tooltips.typesUsed(storedItemTypes, serverData.getLong("totalItemTypes")));
            }
        }
    }

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof ECODriveBlockEntity be) {
            compoundTag.putBoolean("infiniteMember", ECOInfiniteStorageMember.isMember(be.getCellStack()));
            compoundTag.putBoolean("mounted", be.isMounted());
            if (be.getCluster() instanceof NEStorageCluster storageCluster) {
                ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = storageCluster.getTheInterface();
                if (storageInterface != null && storageInterface.isStorageTransferMode()) {
                    compoundTag.putInt("storageInterfaceMode", storageInterface.getStorageInterfaceMode().ordinal());
                }
            }
            IECOStorageCell cellInventory = be.getCellInventory();
            if (cellInventory != null) {
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
