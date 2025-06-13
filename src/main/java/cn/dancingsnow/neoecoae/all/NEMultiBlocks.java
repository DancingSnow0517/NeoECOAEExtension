package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.blocks.ECOMachineCasing;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationDrive;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingParallelCore;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageVentBlock;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationCoolingController;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationParallelCore;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationThreadingCore;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class NEMultiBlocks {
    public static final List<MultiBlockDefinition> DEFINITIONS = new ArrayList<>();

    public static final MultiBlockDefinition STORAGE_SYSTEM_L4 = storageSystem(
        NEBlocks.STORAGE_SYSTEM_L4,
        NEBlocks.STORAGE_SYSTEM_L4.getDefaultState(),
        NEBlocks.ENERGY_CELL_L4.getDefaultState().setValue(ECOEnergyCellBlock.FACING, Direction.SOUTH)
    );
    public static final MultiBlockDefinition STORAGE_SYSTEM_L6 = storageSystem(
        NEBlocks.STORAGE_SYSTEM_L6,
        NEBlocks.STORAGE_SYSTEM_L6.getDefaultState(),
        NEBlocks.ENERGY_CELL_L6.getDefaultState().setValue(ECOEnergyCellBlock.FACING, Direction.SOUTH)
    );
    public static final MultiBlockDefinition STORAGE_SYSTEM_L9 = storageSystem(
        NEBlocks.STORAGE_SYSTEM_L9,
        NEBlocks.STORAGE_SYSTEM_L9.getDefaultState(),
        NEBlocks.ENERGY_CELL_L9.getDefaultState().setValue(ECOEnergyCellBlock.FACING, Direction.SOUTH)
    );

    public static final MultiBlockDefinition COMPUTATION_SYSTEM_L4 = createComputationSystem(
        NEBlocks.COMPUTATION_SYSTEM_L4,
        NEBlocks.COMPUTATION_THREADING_CORE_L4,
        NEBlocks.COMPUTATION_PARALLEL_CORE_L4,
        NEBlocks.COMPUTATION_COOLING_CONTROLLER_L4
    );

    public static final MultiBlockDefinition COMPUTATION_SYSTEM_L6 = createComputationSystem(
        NEBlocks.COMPUTATION_SYSTEM_L6,
        NEBlocks.COMPUTATION_THREADING_CORE_L6,
        NEBlocks.COMPUTATION_PARALLEL_CORE_L6,
        NEBlocks.COMPUTATION_COOLING_CONTROLLER_L6
    );

    public static final MultiBlockDefinition COMPUTATION_SYSTEM_L9 = createComputationSystem(
        NEBlocks.COMPUTATION_SYSTEM_L9,
        NEBlocks.COMPUTATION_THREADING_CORE_L9,
        NEBlocks.COMPUTATION_PARALLEL_CORE_L9,
        NEBlocks.COMPUTATION_COOLING_CONTROLLER_L9
    );

    public static final MultiBlockDefinition CRAFTING_SYSTEM_L4 = createCraftingSystem(
        NEBlocks.CRAFTING_SYSTEM_L4,
        NEBlocks.CRAFTING_PARALLEL_CORE_L4
    );

    public static final MultiBlockDefinition CRAFTING_SYSTEM_L6 = createCraftingSystem(
        NEBlocks.CRAFTING_SYSTEM_L6,
        NEBlocks.CRAFTING_PARALLEL_CORE_L6
    );

    public static final MultiBlockDefinition CRAFTING_SYSTEM_L9 = createCraftingSystem(
        NEBlocks.CRAFTING_SYSTEM_L9,
        NEBlocks.CRAFTING_PARALLEL_CORE_L9
    );

    private static MultiBlockDefinition createCraftingSystem(
        BlockEntry<ECOCraftingSystem> main,
        BlockEntry<ECOCraftingParallelCore> parallelCore
    ) {
        BlockState casing = NEBlocks.CRAFTING_CASING.getDefaultState();
        return MultiBlockDefinition.builder(main)
            .setBlock(pos(1, 1, 0), main.getDefaultState())
            .setBlock(pos(1, 0, 0), casing)
            .setBlock(pos(2, 0, 0), casing)
            .setBlock(pos(2, 1, 0), casing)
            .setBlock(pos(1, 2, 0), casing)
            .setBlock(pos(2, 2, 0), casing)
            .setBlock(pos(1, 0, 1), casing)
            .setBlock(pos(2, 0, 1), casing)
            .setBlock(pos(2, 1, 1), NEBlocks.CRAFTING_INTERFACE.getDefaultState())
            .setBlock(pos(1, 1, 1), casing)
            .setBlock(pos(1, 2, 1), casing)
            .setBlock(pos(2, 2, 1), casing)
            .setBlock(pos(0, 0, 0), casing)
            .setBlock(pos(0, 1, 0), casing)
            .setBlock(pos(0, 2, 0), casing)
            .setBlock(pos(0, 0, 1), casing)
            .setBlock(pos(0, 1, 1), casing)
            .setBlock(pos(0, 2, 1), casing)
            .setBlockRepeatable(pos(-1, 1, 0), Direction.WEST, NEBlocks.CRAFTING_WORKER.getDefaultState())
            .setBlockRepeatable(pos(-1, 2, 0), Direction.WEST, parallelCore.getDefaultState())
            .setBlockRepeatable(pos(-1, 0, 0), Direction.WEST, parallelCore.getDefaultState())
            .setBlockRepeatable(pos(-1, 0, 1), Direction.WEST, NEBlocks.CRAFTING_PATTERN_BUS.getDefaultState().setValue(ECOComputationParallelCore.FACING, Direction.SOUTH))
            .setBlockRepeatable(pos(-1, 1, 1), Direction.WEST, NEBlocks.CRAFTING_VENT.getDefaultState().setValue(ECOComputationThreadingCore.FACING, Direction.SOUTH))
            .setBlockRepeatable(pos(-1, 2, 1), Direction.WEST, NEBlocks.CRAFTING_PATTERN_BUS.getDefaultState().setValue(ECOComputationParallelCore.FACING, Direction.SOUTH))
            .setBlockWithRepeatShifted(pos(-1, 1, 0), Direction.WEST, 0, casing)
            .setBlockWithRepeatShifted(pos(-1, 2, 0), Direction.WEST, 0, casing)
            .setBlockWithRepeatShifted(pos(-1, 0, 0), Direction.WEST, 0, casing)
            .setBlockWithRepeatShifted(pos(-1, 0, 1), Direction.WEST, 0, casing)
            .setBlockWithRepeatShifted(pos(-1, 1, 1), Direction.WEST, 0, casing)
            .setBlockWithRepeatShifted(pos(-1, 2, 1), Direction.WEST, 0, casing)
            .expandMin(1)
            .expandMax(NEConfig.craftingSystemMaxLength - 4)
            .onFormed((pos, level) -> {
                BlockState state = level.getBlockState(pos);
                if (state.hasProperty(NEBlock.FORMED)) {
                    state = state.setValue(NEBlock.FORMED, true);
                }
                if (state.hasProperty(ECOMachineCasing.INVISIBLE)) {
                    Vec3 myPos = pos.getCenter();
                    Vec3 controllerPos = new Vec3(1.5, 1.5, 0.5);
                    state = state.setValue(ECOMachineCasing.INVISIBLE, myPos.distanceToSqr(controllerPos) <= 3);
                }
                level.setBlockAndUpdate(pos, state);
            })
            .create(DEFINITIONS::add);
    }

    private static MultiBlockDefinition createComputationSystem(
        BlockEntry<ECOComputationSystem> main,
        BlockEntry<ECOComputationThreadingCore> threadingCore,
        BlockEntry<ECOComputationParallelCore> parallelCore,
        BlockEntry<ECOComputationCoolingController> cooler
    ) {
        BlockState casing = NEBlocks.COMPUTATION_CASING.getDefaultState();
        return MultiBlockDefinition.builder(main)
            .setBlock(pos(1, 1, 0), main.getDefaultState())
            .setBlock(pos(1, 0, 0), casing)
            .setBlock(pos(2, 0, 0), casing)
            .setBlock(pos(2, 1, 0), casing)
            .setBlock(pos(1, 2, 0), casing)
            .setBlock(pos(2, 2, 0), casing)
            .setBlock(pos(1, 0, 1), casing)
            .setBlock(pos(2, 0, 1), casing)
            .setBlock(pos(2, 1, 1), NEBlocks.COMPUTATION_INTERFACE.getDefaultState())
            .setBlock(pos(1, 1, 1), casing)
            .setBlock(pos(1, 2, 1), casing)
            .setBlock(pos(2, 2, 1), casing)
            .setBlock(pos(0, 0, 0), casing)
            .setBlock(pos(0, 1, 0), casing)
            .setBlock(pos(0, 2, 0), casing)
            .setBlock(pos(0, 0, 1), casing)
            .setBlock(pos(0, 1, 1), casing)
            .setBlock(pos(0, 2, 1), casing)
            .setBlockRepeatable(pos(-1, 1, 0), Direction.WEST, NEBlocks.COMPUTATION_TRANSMITTER.getDefaultState())
            .setBlockRepeatable(pos(-1, 2, 0), Direction.WEST, NEBlocks.COMPUTATION_DRIVE.getDefaultState())
            .setBlockEntityRepeatable(pos(-1, 2, 0), Direction.WEST, (pos,state) -> {
                ECOComputationDriveBlockEntity be = NEBlockEntities.COMPUTATION_DRIVE.create(pos, state);
                be.setLowerDrive(false);
                be.setTier(threadingCore.get().getTier());
                return be;
            })
            .setBlockRepeatable(pos(-1, 0, 0), Direction.WEST, NEBlocks.COMPUTATION_DRIVE.getDefaultState())
            .setBlockEntityRepeatable(pos(-1, 0, 0), Direction.WEST, (pos,state) -> {
                ECOComputationDriveBlockEntity be = NEBlockEntities.COMPUTATION_DRIVE.create(pos, state);
                be.setLowerDrive(true);
                be.setTier(threadingCore.get().getTier());
                return be;
            })
            .setBlockRepeatable(pos(-1, 0, 1), Direction.WEST, parallelCore.getDefaultState().setValue(ECOComputationParallelCore.FACING, Direction.SOUTH))
            .setBlockRepeatable(pos(-1, 1, 1), Direction.WEST, threadingCore.getDefaultState().setValue(ECOComputationThreadingCore.FACING, Direction.SOUTH))
            .setBlockRepeatable(pos(-1, 2, 1), Direction.WEST, parallelCore.getDefaultState().setValue(ECOComputationParallelCore.FACING, Direction.SOUTH))
            .setBlockWithRepeatShifted(pos(-1, 1, 0), Direction.WEST, 0, cooler.getDefaultState().setValue(ECOComputationCoolingController.FACING, Direction.WEST))
            .setBlockWithRepeatShifted(pos(-1, 2, 0), Direction.WEST, 0, casing)
            .setBlockWithRepeatShifted(pos(-1, 0, 0), Direction.WEST, 0, casing)
            .setBlockWithRepeatShifted(pos(-1, 0, 1), Direction.WEST, 0, casing)
            .setBlockWithRepeatShifted(pos(-1, 1, 1), Direction.WEST, 0, casing)
            .setBlockWithRepeatShifted(pos(-1, 2, 1), Direction.WEST, 0, casing)
            .expandMin(1)
            .expandMax(NEConfig.computationSystemMaxLength - 4)
            .onFormed((pos, level) -> {
                BlockState state = level.getBlockState(pos);
                if (state.hasProperty(NEBlock.FORMED)) {
                    state = state.setValue(NEBlock.FORMED, true);
                }
                if (state.hasProperty(ECOMachineCasing.INVISIBLE)) {
                    state = state.setValue(ECOMachineCasing.INVISIBLE, true);
                }
                BlockEntity be = level.getBlockEntity(pos);
                level.setBlockAndUpdate(pos, state);
                if (be != null)level.setBlockEntity(be);
            })
            .create(DEFINITIONS::add);
    }

    private static MultiBlockDefinition storageSystem(Holder<Block> owner, BlockState system, BlockState energyCell) {
        return MultiBlockDefinition.builder(owner)
            .setBlock(pos(1, 1, 0), system)
            .setBlock(pos(1, 0, 0), NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlock(pos(2, 0, 0), NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlock(pos(2, 1, 0), NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlock(pos(1, 2, 0), NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlock(pos(2, 2, 0), NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlock(pos(1, 0, 1), NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlock(pos(2, 0, 1), NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlock(pos(2, 1, 1), NEBlocks.STORAGE_INTERFACE.getDefaultState())
            .setBlock(pos(1, 1, 1), NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlock(pos(1, 2, 1), NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlock(pos(2, 2, 1), NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlockRepeatable(pos(0, 0, 0), Direction.WEST, NEBlocks.ECO_DRIVE.getDefaultState())
            .setBlockRepeatable(pos(0, 1, 0), Direction.WEST, NEBlocks.ECO_DRIVE.getDefaultState())
            .setBlockRepeatable(pos(0, 2, 0), Direction.WEST, NEBlocks.ECO_DRIVE.getDefaultState())
            .setBlockRepeatable(pos(0, 0, 1), Direction.WEST, energyCell)
            .setBlockRepeatable(pos(0, 1, 1), Direction.WEST, NEBlocks.STORAGE_VENT.getDefaultState().setValue(ECOStorageVentBlock.FACING, Direction.SOUTH))
            .setBlockRepeatable(pos(0, 2, 1), Direction.WEST, energyCell)
            .setBlockWithRepeatShifted(pos(0, 0, 0), Direction.WEST, 0, NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlockWithRepeatShifted(pos(0, 0, 1), Direction.WEST, 0, NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlockWithRepeatShifted(pos(0, 1, 0), Direction.WEST, 0, NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlockWithRepeatShifted(pos(0, 1, 1), Direction.WEST, 0, NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlockWithRepeatShifted(pos(0, 2, 0), Direction.WEST, 0, NEBlocks.STORAGE_CASING.getDefaultState())
            .setBlockWithRepeatShifted(pos(0, 2, 1), Direction.WEST, 0, NEBlocks.STORAGE_CASING.getDefaultState())
            .expandMin(1)
            .expandMax(NEConfig.storageSystemMaxLength - 3)
            .onFormed((pos, level) -> {
                BlockState state = level.getBlockState(pos);
                if (state.hasProperty(NEBlock.FORMED)) {
                    state = state.setValue(NEBlock.FORMED, true);
                }
                if (state.hasProperty(ECOMachineCasing.INVISIBLE)) {
                    Vec3 myPos = pos.getCenter();
                    Vec3 controllerPos = new Vec3(1.5, 1.5, 0.5);
                    state = state.setValue(ECOMachineCasing.INVISIBLE, myPos.distanceToSqr(controllerPos) <= 3);
                }
                level.setBlockAndUpdate(pos, state);
            })
            .create();
    }

    private static BlockPos pos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

}
