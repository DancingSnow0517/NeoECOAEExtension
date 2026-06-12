package cn.dancingsnow.neoecoae.client.multiblock.preview;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.ECOMachineCasing;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockItemFormResolver;
import cn.dancingsnow.neoecoae.multiblock.preview.MultiblockPatternPreviewService;
import cn.dancingsnow.neoecoae.multiblock.preview.MultiblockPatternSnapshot;
import cn.dancingsnow.neoecoae.multiblock.preview.PatternBlockEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class MultiblockPreviewContext extends MultiBlockContext {
    private static final Vec3 CONTROLLER_CENTER = new Vec3(1.5, 1.5, 0.5);

    private final MultiBlockDefinition definition;
    private final boolean mirrored;
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
        this(null, repeats, false, false);
    }

    public MultiblockPreviewContext(int repeats, boolean formed) {
        this(null, repeats, false, formed);
    }

    public MultiblockPreviewContext(@Nullable MultiBlockDefinition definition, int repeats, boolean formed) {
        this(definition, repeats, false, formed);
    }

    public MultiblockPreviewContext(
            @Nullable MultiBlockDefinition definition, int repeats, boolean mirrored, boolean formed) {
        this.definition = definition;
        this.repeats = repeats;
        this.mirrored = mirrored;
        this.formed = formed;
    }

    public static MultiblockPreviewScene createScene(MultiBlockDefinition definition, int expand) {
        return createScene(definition, expand, false);
    }

    public static MultiblockPreviewScene createScene(MultiBlockDefinition definition, int expand, boolean formed) {
        return createScene(MultiblockPatternPreviewService.create(definition, expand, false), formed, -1);
    }

    public static MultiblockPreviewScene createScene(
            MultiblockPatternSnapshot snapshot, boolean formed, int selectedLayer) {
        MultiblockPreviewContext context =
                new MultiblockPreviewContext(snapshot.definition(), snapshot.repeats(), snapshot.mirrored(), formed);
        for (PatternBlockEntry entry : snapshot.blocks()) {
            if (selectedLayer < 0 || entry.layerY() == selectedLayer) {
                context.addPreviewBlock(entry.relativePos(), entry.blockState());
            }
        }
        return context.toScene(snapshot.definition(), snapshot.repeats(), snapshot.materialSummary());
    }

    @Override
    public void setBlock(BlockPos pos, BlockState blockState) {
        if (blockState == null || blockState.isAir()) {
            return;
        }

        ItemStack item = MultiBlockItemFormResolver.requiredItem(blockState);
        addRequiredItem(item);
        addPreviewBlock(pos, blockState);
    }

    private void addPreviewBlock(BlockPos pos, BlockState blockState) {
        if (pos.getY() < 0) {
            return;
        }

        BlockPos immutable = pos.immutable();
        BlockState previewState = applyPreviewState(immutable, blockState);
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
        return toScene(definition, expand, requiredItems);
    }

    private MultiblockPreviewScene toScene(
            MultiBlockDefinition definition, int expand, List<ItemStack> sceneRequiredItems) {
        return new MultiblockPreviewScene(
                definition,
                expand,
                formed,
                blocks,
                posList,
                sceneRequiredItems,
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

    private BlockState applyPreviewState(BlockPos pos, BlockState state) {
        if (state.is(NEBlocks.COMPUTATION_TRANSMITTER.get()) && state.hasProperty(NEBlock.FORMED)) {
            state = state.setValue(NEBlock.FORMED, true);
        }
        if (!formed) {
            return state;
        }
        if (state.hasProperty(NEBlock.FORMED)) {
            state = state.setValue(NEBlock.FORMED, true);
        }
        if (state.hasProperty(NEBlock.MIRRORED)) {
            state = state.setValue(NEBlock.MIRRORED, mirrored);
        }
        if (state.hasProperty(ECOMachineCasing.INVISIBLE)) {
            boolean invisible =
                    isComputationSystem() || pos.getCenter().distanceToSqr(controllerCenter()) <= 3.0D;
            state = state.setValue(ECOMachineCasing.INVISIBLE, invisible);
        }
        return state;
    }

    private Vec3 controllerCenter() {
        if (!mirrored) {
            return CONTROLLER_CENTER;
        }
        BlockPos controllerPos =
                cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockRotation.transformLocalPos(
                        cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockRotation.CONTROLLER_ANCHOR, true);
        return controllerPos.getCenter();
    }

    private boolean isComputationSystem() {
        return definition != null && definition.getOwner().value() instanceof ECOComputationSystem;
    }
}
