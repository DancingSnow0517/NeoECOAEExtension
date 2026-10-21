package cn.dancingsnow.neoecoae.multiblock.preview;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public final class MultiblockPatternSnapshot {
    private final MultiBlockDefinition definition;
    private final int repeats;
    private final boolean mirrored;
    private final List<PatternBlockEntry> blocks;
    private final List<PatternLayer> layers;
    private final List<ItemStack> materialSummary;
    private final BlockPos min;
    private final BlockPos max;

    public MultiblockPatternSnapshot(
            MultiBlockDefinition definition,
            int repeats,
            boolean mirrored,
            List<PatternBlockEntry> blocks,
            List<PatternLayer> layers,
            List<ItemStack> materialSummary,
            BlockPos min,
            BlockPos max) {
        this.definition = definition;
        this.repeats = repeats;
        this.mirrored = mirrored;
        this.blocks = List.copyOf(blocks);
        this.layers = List.copyOf(layers);
        this.materialSummary = copyItems(materialSummary);
        this.min = min.immutable();
        this.max = max.immutable();
    }

    public MultiBlockDefinition definition() {
        return definition;
    }

    public int repeats() {
        return repeats;
    }

    public boolean mirrored() {
        return mirrored;
    }

    public List<PatternBlockEntry> blocks() {
        return blocks;
    }

    public List<PatternLayer> layers() {
        return layers;
    }

    public List<ItemStack> materialSummary() {
        return copyItems(materialSummary);
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    public int sizeX() {
        return blocks.isEmpty() ? 0 : max.getX() - min.getX() + 1;
    }

    public int sizeY() {
        return blocks.isEmpty() ? 0 : max.getY() - min.getY() + 1;
    }

    public int sizeZ() {
        return blocks.isEmpty() ? 0 : max.getZ() - min.getZ() + 1;
    }

    public int minLayerY() {
        return layers.isEmpty() ? 0 : layers.get(0).y();
    }

    public int maxLayerY() {
        return layers.isEmpty() ? 0 : layers.get(layers.size() - 1).y();
    }

    public List<PatternBlockEntry> blocksForLayer(int y) {
        for (PatternLayer layer : layers) {
            if (layer.y() == y) {
                return layer.blocks();
            }
        }
        return List.of();
    }

    private static List<ItemStack> copyItems(List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copy.add(stack.copy());
        }
        return Collections.unmodifiableList(copy);
    }
}
