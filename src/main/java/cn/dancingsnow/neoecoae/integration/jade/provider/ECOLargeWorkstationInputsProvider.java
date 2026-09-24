package cn.dancingsnow.neoecoae.integration.jade.provider;

import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;

public enum ECOLargeWorkstationInputsProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String TAG_INPUTS = "workstationCurrentInputs";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity host)) {
            return;
        }
        ListTag encoded = new ListTag();
        NEIntegratedWorkingStationCluster cluster = host.getCluster();
        if (cluster != null && cluster.getController() != null) {
            for (GenericStack input : cluster.getController().getCurrentBatchInputs()) {
                if (input != null && input.amount() > 0) {
                    encoded.add(GenericStack.writeTag(input));
                }
            }
        }
        data.put(TAG_INPUTS, encoded);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(TAG_INPUTS, Tag.TAG_LIST)) return;

        tooltip.add(Component.translatable("jade.neoecoae.large_workstation.current_inputs")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        ListTag inputs = data.getList(TAG_INPUTS, Tag.TAG_COMPOUND);
        if (inputs.isEmpty()) {
            tooltip.add(Component.translatable("jade.neoecoae.large_workstation.current_inputs.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        for (int i = 0; i < inputs.size(); i++) {
            GenericStack stack = GenericStack.readTag(inputs.getCompound(i));
            if (stack == null || stack.amount() <= 0) continue;
            tooltip.add(stack.what()
                    .getDisplayName()
                    .copy()
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" x " + stack.what().formatAmount(stack.amount(), AmountFormat.SLOT))
                            .withStyle(ChatFormatting.YELLOW)));
        }
    }

    @Override
    public int getDefaultPriority() {
        return TooltipPosition.TAIL;
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("large_workstation_current_inputs");
    }
}
