package cn.dancingsnow.neoecoae.multiblock.placement;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class MultiBlockPlanContext extends MultiBlockContext {
    @Getter
    private final List<PlannedBlock> plannedBlocks = new ArrayList<>();
    private final TrackedDummyWorld dummyWorld = new TrackedDummyWorld();

    public MultiBlockPlanContext(int repeats) {
        this.repeats = repeats;
    }

    @Override
    public void setBlock(BlockPos pos, BlockState blockState) {
        plannedBlocks.add(new PlannedBlock(
            pos.immutable(),
            blockState,
            blockState.getBlock().asItem().getDefaultInstance().copy()
        ));
        dummyWorld.addBlock(pos, new BlockInfo(blockState));
    }

    @Override
    public void setBlockEntity(BlockPos pos, BiFunction<BlockPos, BlockState, BlockEntity> sup) {
    }

    @Override
    public Level getLevel() {
        return dummyWorld;
    }

    @Override
    public List<BlockPos> allBlocks() {
        return plannedBlocks.stream().map(PlannedBlock::relativePos).toList();
    }

    @Override
    public boolean isFormed() {
        return false;
    }
}