package cn.dancingsnow.neoecoae.gui.ldlib.support;

import cn.dancingsnow.neoecoae.items.StructureTerminalItem;
import cn.dancingsnow.neoecoae.multiblock.BuildPreviewState;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMaterialRequirements;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public record NEStructureTerminalConfigState(
        int length,
        int minLength,
        int maxLength,
        int tier,
        StructureTerminalHostType hostType,
        StructureTerminalMode operationMode,
        boolean operationModePending,
        boolean previewMirrored,
        boolean previewFormed,
        int previewLayer,
        int previewMaterialScroll,
        boolean linkedHost,
        boolean formed,
        boolean buildInProgress,
        int previewMissingBlocks,
        int previewConflictBlocks,
        int previewReusedBlocks,
        int previewRequiredItems,
        String previewStatusKey,
        int previewStatusArg1,
        int previewStatusArg2,
        List<NEStructureTerminalUiState.BuildMaterialEntry> materials) {
    public static NEStructureTerminalConfigState empty() {
        return new NEStructureTerminalConfigState(
                StructureTerminalItem.DEFAULT_BUILD_LENGTH,
                StructureTerminalItem.MIN_BUILD_LENGTH,
                StructureTerminalItem.MIN_BUILD_LENGTH,
                StructureTerminalHostType.DEFAULT_TIER,
                StructureTerminalHostType.DEFAULT,
                StructureTerminalMode.BUILD,
                false,
                false,
                false,
                -1,
                0,
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                BuildPreviewState.DEFAULT_STATUS_KEY,
                0,
                0,
                List.of());
    }

    public static NEStructureTerminalConfigState fromStack(Player player, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof StructureTerminalItem)) {
            return empty();
        }
        int min = StructureTerminalItem.MIN_BUILD_LENGTH;
        int max = StructureTerminalItem.getMaxBuildLength(stack);
        int length = StructureTerminalItem.getBuildLength(stack, max);
        int tier = StructureTerminalItem.getHostTier(stack);
        StructureTerminalHostType hostType = StructureTerminalItem.getHostType(stack);
        StructureTerminalMode mode = StructureTerminalItem.getOperationMode(stack);
        boolean modePending = StructureTerminalItem.hasOperationMode(stack);
        INEMultiblockBuildHost host = StructureTerminalItem.findLinkedHost(player, stack);
        BuildPreviewState preview = host == null ? null : host.getBuildPreview();
        return new NEStructureTerminalConfigState(
                length,
                min,
                max,
                tier,
                hostType,
                mode,
                modePending,
                StructureTerminalItem.isPreviewMirrored(stack),
                StructureTerminalItem.isPreviewFormed(stack),
                StructureTerminalItem.getPreviewLayer(stack),
                StructureTerminalItem.getPreviewMaterialScroll(stack),
                host != null,
                host != null && host.isFormed(),
                host != null && host.isBuildInProgress(),
                preview == null ? 0 : preview.previewMissingBlocks,
                preview == null ? 0 : preview.previewConflictBlocks,
                preview == null ? 0 : preview.previewReusedBlocks,
                preview == null ? 0 : preview.previewRequiredItems,
                preview == null ? BuildPreviewState.DEFAULT_STATUS_KEY : preview.previewStatusKey,
                preview == null ? 0 : preview.previewStatusArg1,
                preview == null ? 0 : preview.previewStatusArg2,
                StructureTerminalMaterialRequirements.collect(player, hostType, tier, length));
    }
}
