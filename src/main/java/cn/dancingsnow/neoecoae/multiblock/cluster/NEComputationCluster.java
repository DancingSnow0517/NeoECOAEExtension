package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.api.networking.security.IActionSource;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class NEComputationCluster extends NECluster<NEComputationCluster> {

    @Getter
    private List<ECOComputationDriveBlockEntity> upperDrives = new ArrayList<>();
    @Getter
    private List<ECOComputationDriveBlockEntity> lowerDrives = new ArrayList<>();
    @Getter
    private List<ECOComputationThreadingCoreBlockEntity> threadingCores = new ArrayList<>();
    @Getter
    @Nullable
    private ECOComputationSystemBlockEntity controller;
    @Getter
    @Nullable
    private IActionSource actionSource;

    public NEComputationCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    @Override
    public void addBlockEntity(NEBlockEntity<NEComputationCluster, ?> blockEntity) {
        super.addBlockEntity(blockEntity);
        if (blockEntity instanceof ECOComputationDriveBlockEntity driveBlockEntity) {
            Level level = driveBlockEntity.getLevel();
            BlockState bottomBlock = level.getBlockState(driveBlockEntity.getBlockPos().relative(Direction.DOWN));
            if (bottomBlock.is(NEBlocks.COMPUTATION_TRANSMITTER)) {
                upperDrives.add(driveBlockEntity);
            } else {
                driveBlockEntity.setLowerDrive(true);
                lowerDrives.add(driveBlockEntity);
            }
        }
        if (blockEntity instanceof ECOComputationThreadingCoreBlockEntity threadingCore) {
            threadingCores.add(threadingCore);
        }
        if (blockEntity instanceof ECOComputationSystemBlockEntity system) {
            controller = system;
            actionSource = IActionSource.ofMachine(system);
        }
    }
}
