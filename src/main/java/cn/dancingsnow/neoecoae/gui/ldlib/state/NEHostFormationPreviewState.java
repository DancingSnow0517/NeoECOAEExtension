package cn.dancingsnow.neoecoae.gui.ldlib.state;

import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlanContext;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockRotation;
import cn.dancingsnow.neoecoae.multiblock.placement.PlannedBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public record NEHostFormationPreviewState(
        int length,
        int minLength,
        int maxLength,
        boolean mirrored,
        int selectedLayer,
        int previewMaterialScroll,
        int totalBlocks,
        int matchedBlocks,
        int missingBlocks,
        int conflictBlocks,
        List<BlockEntry> blocks,
        List<ItemStack> requiredItems) {
    public static NEHostFormationPreviewState empty() {
        return new NEHostFormationPreviewState(1, 1, 1, false, -1, 0, 0, 0, 0, 0, List.of(), List.of());
    }

    public static NEHostFormationPreviewState fromHost(
            INEMultiblockBuildHost host, boolean mirrored, int selectedLayer, int previewMaterialScroll) {
        MultiBlockDefinition definition = host.getBuildDefinition();
        Level level = host.getHostLevel();
        if (definition == null || !(level instanceof ServerLevel serverLevel)) {
            return empty();
        }

        int minLength = host.getMinBuildLength();
        int maxLength = host.getMaxBuildLength();
        int length = Math.max(minLength, Math.min(maxLength, host.getSelectedBuildLength()));
        Direction facing = host.getHostBlockState()
                .getOptionalValue(BlockStateProperties.HORIZONTAL_FACING)
                .orElse(Direction.NORTH);

        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
                serverLevel, host.getHostPos(), host.getHostBlockState(), definition, length, mirrored);
        MultiBlockPlanContext context = new MultiBlockPlanContext(length);
        definition.createLevel(context);

        List<BlockEntry> entries = new ArrayList<>();
        int total = 0;
        int matched = 0;
        int missing = 0;
        int conflicts = 0;

        for (PlannedBlock plannedBlock : context.getPlannedBlocks()) {
            BlockPos sourceRelative = plannedBlock.relativePos();
            boolean controller = sourceRelative.equals(MultiBlockRotation.CONTROLLER_ANCHOR);
            BlockPos relative = MultiBlockRotation.transformLocalPos(sourceRelative, mirrored);
            if (controller) {
                entries.add(new BlockEntry(relative, BlockStatus.MATCHED));
                continue;
            }

            total++;
            BlockPos worldPos = MultiBlockRotation.localToWorld(sourceRelative, host.getHostPos(), facing, mirrored);
            BlockState targetState = MultiBlockRotation.rotateState(plannedBlock.targetState(), facing, mirrored);
            BlockState existingState = serverLevel.getBlockState(worldPos);
            BlockStatus status;
            if (existingState.equals(targetState)) {
                status = BlockStatus.MATCHED;
                matched++;
            } else if (existingState.isAir() || existingState.canBeReplaced()) {
                status = BlockStatus.MISSING;
                missing++;
            } else {
                status = BlockStatus.CONFLICT;
                conflicts++;
            }
            entries.add(new BlockEntry(relative, status));
        }

        return new NEHostFormationPreviewState(
                length,
                minLength,
                maxLength,
                mirrored,
                selectedLayer,
                previewMaterialScroll,
                total,
                matched,
                missing,
                conflicts,
                List.copyOf(entries),
                plan.getRequiredItems());
    }

    public List<ItemStack> copyRequiredItems() {
        List<ItemStack> copy = new ArrayList<>(requiredItems.size());
        for (ItemStack stack : requiredItems) {
            copy.add(stack.copy());
        }
        return List.copyOf(copy);
    }

    public record BlockEntry(BlockPos relativePos, BlockStatus status) {}

    public enum BlockStatus {
        MATCHED,
        MISSING,
        CONFLICT
    }
}
