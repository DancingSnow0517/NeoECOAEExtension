package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECOComputationDriveProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getServerData().getBoolean("tierMismatch")) {
            tooltip.add(Component.translatable("jade.neoecoae.computation_cell_tier_too_high")
                .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ECOComputationDriveBlockEntity drive
            && drive.getTier() != null
            && drive.getCellStack() != null
            && drive.getCellStack().getItem() instanceof ECOComputationCellItem cell) {
            data.putBoolean("tierMismatch", !drive.getTier().supportsComponentTier(cell.getTier()));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_computation_drive");
    }
}
