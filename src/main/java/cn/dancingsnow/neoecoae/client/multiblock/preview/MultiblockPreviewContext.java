package cn.dancingsnow.neoecoae.client.multiblock.preview;

import cn.dancingsnow.neoecoae.blocks.ECOMachineCasing;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiFunction;

public final class MultiblockPreviewContext extends MultiBlockContext {
    private static final Vec3 CONTROLLER_CENTER = new Vec3(1.5, 1.5, 0.5);

    private final MultiBlockDefinition definition;
    private final boolean formed;
    private final LinkedHashMap<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    private final List<BlockPos> posList = new ArrayList<>();
    private final List<ItemStack> requiredItems = new ArrayList<>();

    private int yMax = 0;
    private int minX = 0;
    private int minY = 0;
    private int minZ = 0;
    private int maxX = 0;
    private int maxY = 0;
    private int maxZ = 0;
    private boolean hasBounds = false;

    public MultiblockPreviewContext(int repeats) {
        this(null, repeats, false);
    }

    public MultiblockPreviewContext(int repeats, boolean formed) {
        this(null, repeats, formed);
    }

    public MultiblockPreviewContext(@Nullable MultiBlockDefinition definition, int repeats, boolean formed) {
        this.definition = definition;
        this.repeats = repeats;
        this.formed = formed;
    }

    public static MultiblockPreviewScene createScene(MultiBlockDefinition definition, int expand) {
        return createScene(definition, expand, false);
    }

    public static MultiblockPreviewScene createScene(MultiBlockDefinition definition, int expand, boolean formed) {
        MultiblockPreviewContext context = new MultiblockPreviewContext(definition, expand, formed);
        definition.createLevel(context);
        return context.toScene(definition, expand);
    }

    @Override
    public void setBlock(BlockPos pos, BlockState blockState) {
        if (blockState == null || blockState.isAir()) {
            return;
        }

        ItemStack item = blockState.getBlock().asItem().getDefaultInstance();
        addRequiredItem(item);

        if (pos.getY() < 0) {
            return;
        }

        BlockPos immutable = pos.immutable();
        BlockState previewState = formed ? applyFormedPreviewState(immutable, blockState) : blockState;
        if (!blocks.containsKey(immutable)) {
            posList.add(immutable);
        }
        blocks.put(immutable, previewState);
        yMax = Math.max(yMax, immutable.getY());
        updateBounds(immutable);
    }

    @Override
    public void setBlockEntity(BlockPos pos, BiFunction<BlockPos, BlockState, BlockEntity> sup) {
        // Preview collection is intentionally pure-data. Formed previews that need
        // real block entities remain a later feature.
    }

    @Override
    public @Nullable Level getLevel() {
        return null;
    }

    @Override
    public List<BlockPos> allBlocks() {
        return posList;
    }

    @Override
    public boolean isFormed() {
        // Preview rendering has no real Level. Returning false prevents
        // MultiBlockDefinition.onFormed from mutating a null/dummy world; the
        // visible formed block-state changes are applied locally above.
        return false;
    }

    public MultiblockPreviewScene toScene(MultiBlockDefinition definition, int expand) {
        return new MultiblockPreviewScene(
                definition,
                expand,
                formed,
                blocks,
                posList,
                requiredItems,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                yMax);
    }

    private void addRequiredItem(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return;
        }
        for (ItemStack stack : requiredItems) {
            if (ItemStack.isSameItemSameTags(itemStack, stack)) {
                stack.grow(itemStack.getCount());
                return;
            }
        }
        requiredItems.add(itemStack.copy());
    }

    private void updateBounds(BlockPos pos) {
        if (!hasBounds) {
            minX = maxX = pos.getX();
            minY = maxY = pos.getY();
            minZ = maxZ = pos.getZ();
            hasBounds = true;
            return;
        }

        minX = Math.min(minX, pos.getX());
        minY = Math.min(minY, pos.getY());
        minZ = Math.min(minZ, pos.getZ());
        maxX = Math.max(maxX, pos.getX());
        maxY = Math.max(maxY, pos.getY());
        maxZ = Math.max(maxZ, pos.getZ());
    }

    private BlockState applyFormedPreviewState(BlockPos pos, BlockState state) {
        if (state.hasProperty(NEBlock.FORMED)) {
            state = state.setValue(NEBlock.FORMED, true);
        }
        if (state.hasProperty(ECOMachineCasing.INVISIBLE)) {
            boolean invisible = isComputationSystem()
                    || pos.getCenter().distanceToSqr(CONTROLLER_CENTER) <= 3.0D;
            state = state.setValue(ECOMachineCasing.INVISIBLE, invisible);
        }
        return state;
    }

    private boolean isComputationSystem() {
        return definition != null && definition.getOwner().value() instanceof ECOComputationSystem;
    }
}
