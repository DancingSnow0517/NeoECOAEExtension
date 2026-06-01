package cn.dancingsnow.neoecoae.client.multiblock.preview;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MultiblockPreviewScene {
    private final MultiBlockDefinition definition;
    private final int expand;
    private final boolean formed;
    private final LinkedHashMap<BlockPos, BlockState> blocks;
    private final Map<BlockPos, BlockState> blocksView;
    private final List<BlockPos> orderedPositions;
    private final List<ItemStack> requiredItems;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final int yMax;

    public MultiblockPreviewScene(
            MultiBlockDefinition definition,
            int expand,
            boolean formed,
            LinkedHashMap<BlockPos, BlockState> blocks,
            List<BlockPos> orderedPositions,
            List<ItemStack> requiredItems,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            int yMax) {
        this.definition = definition;
        this.expand = expand;
        this.formed = formed;
        this.blocks = new LinkedHashMap<>(blocks);
        this.blocksView = Collections.unmodifiableMap(this.blocks);
        this.orderedPositions = List.copyOf(orderedPositions);
        this.requiredItems = copyItems(requiredItems);
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.yMax = yMax;
    }

    private static List<ItemStack> copyItems(List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copy.add(stack.copy());
        }
        return Collections.unmodifiableList(copy);
    }

    public MultiBlockDefinition definition() {
        return definition;
    }

    public int expand() {
        return expand;
    }

    public boolean formed() {
        return formed;
    }

    public Map<BlockPos, BlockState> blocks() {
        return blocksView;
    }

    public List<BlockPos> orderedPositions() {
        return orderedPositions;
    }

    public List<ItemStack> requiredItems() {
        return requiredItems;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public int yMax() {
        return yMax;
    }

    public int sizeX() {
        return isEmpty() ? 0 : maxX - minX + 1;
    }

    public int sizeY() {
        return isEmpty() ? 0 : maxY - minY + 1;
    }

    public int sizeZ() {
        return isEmpty() ? 0 : maxZ - minZ + 1;
    }

    public int maxDimension() {
        return Math.max(sizeX(), Math.max(sizeY(), sizeZ()));
    }

    public float centerX() {
        return isEmpty() ? 0.0F : (minX + maxX + 1.0F) * 0.5F;
    }

    public float centerY() {
        return isEmpty() ? 0.0F : (minY + maxY + 1.0F) * 0.5F;
    }

    public float centerZ() {
        return isEmpty() ? 0.0F : (minZ + maxZ + 1.0F) * 0.5F;
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public List<BlockPos> positionsForLayer(int layer) {
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : orderedPositions) {
            if (pos.getY() == layer) {
                positions.add(pos);
            }
        }
        return positions;
    }
}
