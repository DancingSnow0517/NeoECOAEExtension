package cn.dancingsnow.neoecoae.integration.jade.provider;

import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;

/** Shows the concrete materials accepted for the large workstation's current batch. */
public enum ECOLargeWorkstationInputsProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String TAG_INPUTS = "workstationCurrentInputs";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity host)
                || host.getLevel() == null) {
            return;
        }

        List<GenericStack> inputs = List.of();
        if (host.getCluster() instanceof NEIntegratedWorkingStationCluster cluster
                && cluster.getController() != null) {
            inputs = cluster.getController().getCurrentBatchInputs();
        }

        HolderLookup.Provider registries = host.getLevel().registryAccess();
        ListTag encodedInputs = new ListTag();
        for (GenericStack input : inputs) {
            if (input != null && input.amount() > 0) {
                encodedInputs.add(GenericStack.writeTag(registries, input));
            }
        }
        data.put(TAG_INPUTS, encodedInputs);
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

        for (int index = 0; index < inputs.size(); index++) {
            GenericStack stack = GenericStack.readTag(accessor.getLevel().registryAccess(), inputs.getCompound(index));
            if (stack == null || stack.amount() <= 0) continue;
            MutableComponent line = stack.what().getDisplayName().copy().withStyle(ChatFormatting.WHITE);
            line.append(Component.literal(" × " + stack.what().formatAmount(stack.amount(), AmountFormat.SLOT))
                    .withStyle(ChatFormatting.YELLOW));
            tooltip.add(line);
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
