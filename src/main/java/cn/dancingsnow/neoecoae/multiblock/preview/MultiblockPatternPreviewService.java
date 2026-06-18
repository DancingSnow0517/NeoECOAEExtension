package cn.dancingsnow.neoecoae.multiblock.preview;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlanContext;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockRotation;
import cn.dancingsnow.neoecoae.multiblock.placement.PlannedBlock;
import cn.dancingsnow.neoecoae.util.ItemStacks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class MultiblockPatternPreviewService {
    private MultiblockPatternPreviewService() {}

    public static MultiblockPatternSnapshot create(MultiBlockDefinition definition, int repeats, boolean mirrored) {
        int clampedRepeats = Mth.clamp(repeats, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlanContext context = new MultiBlockPlanContext(clampedRepeats);
        definition.createLevel(context);

        Map<BlockPos, PatternBlockEntry> entriesByPosition = new LinkedHashMap<>();
        for (PlannedBlock plannedBlock : context.getPlannedBlocks()) {
            BlockPos relativePos = MultiBlockRotation.transformLocalPos(plannedBlock.relativePos(), mirrored);
            BlockState state = MultiBlockRotation.rotateState(
                    plannedBlock.targetState(), net.minecraft.core.Direction.NORTH, mirrored);
            boolean controller = plannedBlock.relativePos().equals(MultiBlockRotation.CONTROLLER_ANCHOR);
            entriesByPosition.put(
                    relativePos,
                    new PatternBlockEntry(
                            relativePos, state, plannedBlock.requiredItem(), controller, relativePos.getY()));
        }

        List<PatternBlockEntry> blocks = new ArrayList<>(entriesByPosition.values());
        blocks.sort(Comparator.comparingInt(
                        (PatternBlockEntry entry) -> entry.relativePos().getY())
                .thenComparingInt(entry -> entry.relativePos().getZ())
                .thenComparingInt(entry -> entry.relativePos().getX()));

        Map<Integer, List<PatternBlockEntry>> blocksByLayer = new TreeMap<>();
        List<ItemStack> materials = new ArrayList<>();
        BlockPos min = BlockPos.ZERO;
        BlockPos max = BlockPos.ZERO;
        boolean hasBounds = false;
        for (PatternBlockEntry entry : blocks) {
            blocksByLayer
                    .computeIfAbsent(entry.layerY(), ignored -> new ArrayList<>())
                    .add(entry);
            if (!entry.controller()) {
                ItemStacks.merge(materials, entry.requiredItem());
            }
            BlockPos pos = entry.relativePos();
            if (!hasBounds) {
                min = pos;
                max = pos;
                hasBounds = true;
            } else {
                min = new BlockPos(
                        Math.min(min.getX(), pos.getX()),
                        Math.min(min.getY(), pos.getY()),
                        Math.min(min.getZ(), pos.getZ()));
                max = new BlockPos(
                        Math.max(max.getX(), pos.getX()),
                        Math.max(max.getY(), pos.getY()),
                        Math.max(max.getZ(), pos.getZ()));
            }
        }

        List<PatternLayer> layers = new ArrayList<>(blocksByLayer.size());
        for (Map.Entry<Integer, List<PatternBlockEntry>> entry : blocksByLayer.entrySet()) {
            layers.add(new PatternLayer(entry.getKey(), entry.getValue()));
        }
        materials.sort(Comparator.comparing(stack -> stack.getHoverName().getString()));
        return new MultiblockPatternSnapshot(definition, clampedRepeats, mirrored, blocks, layers, materials, min, max);
    }

    public static List<ItemStack> summarizeMaterials(List<PatternBlockEntry> blocks) {
        List<ItemStack> materials = new ArrayList<>();
        for (PatternBlockEntry entry : blocks) {
            if (!entry.controller()) {
                ItemStacks.merge(materials, entry.requiredItem());
            }
        }
        return materials;
    }
}
