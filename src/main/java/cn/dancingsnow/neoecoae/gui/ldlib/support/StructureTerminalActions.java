package cn.dancingsnow.neoecoae.gui.ldlib.support;

import cn.dancingsnow.neoecoae.items.StructureTerminalItem;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.preview.MultiblockPatternPreviewService;
import cn.dancingsnow.neoecoae.multiblock.preview.MultiblockPatternSnapshot;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class StructureTerminalActions {
    public static void apply(HeldItemUIFactory.HeldItemHolder holder, Enum<?> action, FriendlyByteBuf buffer) {
        ItemStack stack = holder.getHeld();
        if (stack.isEmpty() || !(stack.getItem() instanceof StructureTerminalItem)) {
            return;
        }

        int current = StructureTerminalItem.getBuildLength(stack);
        switch (action.name()) {
            case "SELECT_CRAFTING" -> StructureTerminalItem.setHostType(stack, StructureTerminalHostType.CRAFTING);
            case "SELECT_STORAGE" -> StructureTerminalItem.setHostType(stack, StructureTerminalHostType.STORAGE);
            case "SELECT_COMPUTATION" -> StructureTerminalItem.setHostType(stack, StructureTerminalHostType.COMPUTATION);
            case "SELECT_TIER_1" -> StructureTerminalItem.setHostTarget(
                    stack, StructureTerminalItem.getHostType(stack), 1);
            case "SELECT_TIER_2" -> StructureTerminalItem.setHostTarget(
                    stack, StructureTerminalItem.getHostType(stack), 2);
            case "SELECT_TIER_3" -> StructureTerminalItem.setHostTarget(
                    stack, StructureTerminalItem.getHostType(stack), 3);
            case "INCREASE" -> StructureTerminalItem.setBuildLength(stack, current + 1);
            case "DECREASE" -> StructureTerminalItem.setBuildLength(stack, current - 1);
            case "TOGGLE_PREVIEW_MIRRORED" -> StructureTerminalItem.setPreviewMirrored(
                    stack, !StructureTerminalItem.isPreviewMirrored(stack));
            case "TOGGLE_PREVIEW_FORMED" -> StructureTerminalItem.setPreviewFormed(
                    stack, !StructureTerminalItem.isPreviewFormed(stack));
            case "PREVIOUS_LAYER" -> StructureTerminalItem.setPreviewLayer(stack, previousLayer(stack));
            case "NEXT_LAYER" -> StructureTerminalItem.setPreviewLayer(stack, nextLayer(stack));
            case "SET_PATTERN_MATERIAL_SCROLL" -> StructureTerminalItem.setPreviewMaterialScroll(
                    stack, buffer.readVarInt());
            case "BUILD_LINKED" -> StructureTerminalItem.setOperationMode(stack, StructureTerminalMode.BUILD);
            case "BUILD_MIRRORED_LINKED" -> StructureTerminalItem.setOperationMode(
                    stack, StructureTerminalMode.MIRRORED_BUILD);
            case "DISMANTLE_LINKED" -> StructureTerminalItem.setOperationMode(stack, StructureTerminalMode.DISMANTLE);
            default -> throw new IllegalArgumentException("Unsupported structure terminal action: " + action.name());
        }
        holder.markAsDirty();
    }

    private static int previousLayer(ItemStack stack) {
        MultiblockPatternSnapshot snapshot = snapshotForStack(stack);
        int selectedLayer = StructureTerminalItem.getPreviewLayer(stack);
        if (snapshot == null || snapshot.layers().isEmpty()) {
            return -1;
        }
        if (selectedLayer < 0 || snapshot.blocksForLayer(selectedLayer).isEmpty()) {
            return snapshot.maxLayerY();
        }
        int previous = -1;
        for (var layer : snapshot.layers()) {
            if (layer.y() >= selectedLayer) {
                break;
            }
            previous = layer.y();
        }
        return previous;
    }

    private static int nextLayer(ItemStack stack) {
        MultiblockPatternSnapshot snapshot = snapshotForStack(stack);
        int selectedLayer = StructureTerminalItem.getPreviewLayer(stack);
        if (snapshot == null || snapshot.layers().isEmpty()) {
            return -1;
        }
        if (selectedLayer < 0 || snapshot.blocksForLayer(selectedLayer).isEmpty()) {
            return snapshot.minLayerY();
        }
        for (var layer : snapshot.layers()) {
            if (layer.y() > selectedLayer) {
                return layer.y();
            }
        }
        return -1;
    }

    @Nullable private static MultiblockPatternSnapshot snapshotForStack(ItemStack stack) {
        StructureTerminalHostType hostType = StructureTerminalItem.getHostType(stack);
        MultiBlockDefinition definition = hostType.definitionForTier(StructureTerminalItem.getHostTier(stack));
        if (definition == null) {
            return null;
        }
        return MultiblockPatternPreviewService.create(
                definition,
                StructureTerminalItem.getBuildLength(stack, StructureTerminalItem.getMaxBuildLength(stack)),
                StructureTerminalItem.isPreviewMirrored(stack));
    }

    private StructureTerminalActions() {}
}
