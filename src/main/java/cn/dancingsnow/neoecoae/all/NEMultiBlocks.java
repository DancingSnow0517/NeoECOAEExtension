package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.ECOMachineCasing;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.NENetworkSwitchBlock;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingParallelCore;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem;
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
import java.util.Map;

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
        NEBlocks.COMPUTATION_COOLING_CONTROLLER_L4,
        false
    );

    public static final MultiBlockDefinition COMPUTATION_SYSTEM_L6 = createComputationSystem(
        NEBlocks.COMPUTATION_SYSTEM_L6,
        NEBlocks.COMPUTATION_THREADING_CORE_L6,
        NEBlocks.COMPUTATION_PARALLEL_CORE_L6,
        NEBlocks.COMPUTATION_COOLING_CONTROLLER_L6,
        false
    );

    public static final MultiBlockDefinition COMPUTATION_SYSTEM_L9 = createComputationSystem(
        NEBlocks.COMPUTATION_SYSTEM_L9,
        NEBlocks.COMPUTATION_THREADING_CORE_L9,
        NEBlocks.COMPUTATION_PARALLEL_CORE_L9,
        NEBlocks.COMPUTATION_COOLING_CONTROLLER_L9,
        true
    );

    public static final MultiBlockDefinition CRAFTING_SYSTEM_L4 = createCraftingSystem(
        NEBlocks.CRAFTING_SYSTEM_L4,
        NEBlocks.CRAFTING_PARALLEL_CORE_L4,
        false
    );

    public static final MultiBlockDefinition CRAFTING_SYSTEM_L6 = createCraftingSystem(
        NEBlocks.CRAFTING_SYSTEM_L6,
        NEBlocks.CRAFTING_PARALLEL_CORE_L6,
        false
    );

    public static final MultiBlockDefinition CRAFTING_SYSTEM_L9 = createCraftingSystem(
        NEBlocks.CRAFTING_SYSTEM_L9,
        NEBlocks.CRAFTING_PARALLEL_CORE_L9,
        true
    );

    private static MultiBlockDefinition createCraftingSystem(
        BlockEntry<ECOCraftingSystem> main,
        BlockEntry<ECOCraftingParallelCore> parallelCore,
        boolean networkPreviewVariants
    ) {
        BlockState casing = NEBlocks.CRAFTING_CASING.getDefaultState();
        MultiBlockDefinition.Builder builder = MultiBlockDefinition.builder(main)
            .setBlock(pos(1, 1, 0), main.getDefaultState())
            .setBlock(pos(1, 0, 0), casing)
            .setBlock(pos(2, 0, 0), casing)
            .setBlock(pos(2, 1, 0), casing)
            .setBlock(pos(1, 2, 0), casing)
            .setBlock(pos(2, 2, 0), casing)
            .setBlock(pos(1, 0, 1), casing)
            .setBlock(pos(2, 0, 1), NEBlocks.OUTPUT_HATCH.getDefaultState())
            .setBlock(pos(2, 1, 1), NEBlocks.CRAFTING_INTERFACE.getDefaultState())
            .setBlock(pos(1, 1, 1), casing)
            .setBlock(pos(1, 2, 1), casing)
            .setBlock(pos(2, 2, 1), NEBlocks.INPUT_HATCH.getDefaultState())
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
                BlockState newState = state;
                if (state.hasProperty(NEBlock.FORMED)) {
                    newState = newState.setValue(NEBlock.FORMED, true);
                }
                if (newState.hasProperty(ECOMachineCasing.INVISIBLE)) {
                    Vec3 myPos = pos.getCenter();
                    Vec3 controllerPos = new Vec3(1.5, 1.5, 0.5);
                    newState = newState.setValue(ECOMachineCasing.INVISIBLE, myPos.distanceToSqr(controllerPos) <= 3);
                }
                if (newState != state) {
                    level.setBlockAndUpdate(pos, newState);
                }
            });
        if (networkPreviewVariants) {
            builder
                .previewVariant(craftingNetworkPreviewVariant(NEBlocks.CRAFTING_NETWORK_SWITCH.getDefaultState(), false))
                .previewVariant(craftingNetworkPreviewVariant(NEBlocks.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH.getDefaultState(), true));
        }
        return builder.create(DEFINITIONS::add);
    }

    private static MultiBlockDefinition createComputationSystem(
        BlockEntry<ECOComputationSystem> main,
        BlockEntry<ECOComputationThreadingCore> threadingCore,
        BlockEntry<ECOComputationParallelCore> parallelCore,
        BlockEntry<ECOComputationCoolingController> cooler,
        boolean networkPreviewVariants
    ) {
        BlockState casing = NEBlocks.COMPUTATION_CASING.getDefaultState();
        MultiBlockDefinition.Builder builder = MultiBlockDefinition.builder(main)
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
//            .setBlockEntityRepeatable(pos(-1, 2, 0), Direction.WEST, (pos,state) -> {
//                ECOComputationDriveBlockEntity be = NEBlockEntities.COMPUTATION_DRIVE.create(pos, state);
//                be.setLowerDrive(false);
//                be.setTier(threadingCore.get().getTier());
//                return be;
//            })
            .setBlockRepeatable(pos(-1, 0, 0), Direction.WEST, NEBlocks.COMPUTATION_DRIVE.getDefaultState())
//            .setBlockEntityRepeatable(pos(-1, 0, 0), Direction.WEST, (pos,state) -> {
//                ECOComputationDriveBlockEntity be = NEBlockEntities.COMPUTATION_DRIVE.create(pos, state);
//                be.setLowerDrive(true);
//                be.setTier(threadingCore.get().getTier());
//                return be;
//            })
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
                BlockState newState = state;
                if (state.hasProperty(NEBlock.FORMED)) {
                    newState = newState.setValue(NEBlock.FORMED, true);
                }
                if (newState.hasProperty(ECOMachineCasing.INVISIBLE)) {
                    newState = newState.setValue(ECOMachineCasing.INVISIBLE, true);
                }
                if (newState != state) {
                    BlockEntity be = level.getBlockEntity(pos);
                    level.setBlockAndUpdate(pos, newState);
                    if (be != null) level.setBlockEntity(be);
                }
            });
        if (networkPreviewVariants) {
            builder
                .previewVariant(computationNetworkPreviewVariant(NEBlocks.COMPUTATION_NETWORK_SWITCH.getDefaultState(), false))
                .previewVariant(computationNetworkPreviewVariant(NEBlocks.COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH.getDefaultState(), true));
        }
        return builder.create(DEFINITIONS::add);
    }

    private static MultiBlockDefinition.PreviewVariant craftingNetworkPreviewVariant(
        BlockState switchState,
        boolean highEnergy
    ) {
        return new MultiBlockDefinition.PreviewVariant(
            switchState.getBlock().getName(),
            highEnergy ? "x8" : "x2",
            Map.of(pos(2, 1, 0), switchState),
            (level, formed) -> {
                BlockPos switchPos = pos(2, 1, 0);
                BlockState currentSwitch = level.getBlockState(switchPos);
                if (currentSwitch.hasProperty(NENetworkSwitchBlock.FORMED)) {
                    level.setBlockAndUpdate(
                        switchPos,
                        currentSwitch.setValue(NENetworkSwitchBlock.FORMED, formed)
                    );
                }
                BlockPos controllerPos = pos(1, 1, 0);
                BlockState controller = level.getBlockState(controllerPos);
                if (controller.hasProperty(ECOCraftingSystem.NETWORK_SWITCH)
                    && controller.hasProperty(ECOCraftingSystem.HIGH_ENERGY_NETWORK_SWITCH)) {
                    level.setBlockAndUpdate(
                        controllerPos,
                        controller
                            .setValue(ECOCraftingSystem.NETWORK_SWITCH, formed && !highEnergy)
                            .setValue(ECOCraftingSystem.HIGH_ENERGY_NETWORK_SWITCH, formed && highEnergy)
                    );
                }
            }
        );
    }

    private static MultiBlockDefinition.PreviewVariant computationNetworkPreviewVariant(
        BlockState switchState,
        boolean highEnergy
    ) {
        return new MultiBlockDefinition.PreviewVariant(
            switchState.getBlock().getName(),
            highEnergy ? "x8" : "x2",
            Map.of(pos(2, 1, 0), switchState),
            (level, formed) -> {
                BlockPos switchPos = pos(2, 1, 0);
                BlockState currentSwitch = level.getBlockState(switchPos);
                if (currentSwitch.hasProperty(NENetworkSwitchBlock.FORMED)) {
                    level.setBlockAndUpdate(
                        switchPos,
                        currentSwitch.setValue(NENetworkSwitchBlock.FORMED, formed)
                    );
                }
                BlockPos controllerPos = pos(1, 1, 0);
                BlockState controller = level.getBlockState(controllerPos);
                if (controller.hasProperty(ECOComputationSystem.NETWORK_SWITCH)
                    && controller.hasProperty(ECOComputationSystem.HIGH_ENERGY_NETWORK_SWITCH)) {
                    level.setBlockAndUpdate(
                        controllerPos,
                        controller
                            .setValue(ECOComputationSystem.NETWORK_SWITCH, formed && !highEnergy)
                            .setValue(ECOComputationSystem.HIGH_ENERGY_NETWORK_SWITCH, formed && highEnergy)
                    );
                }
            }
        );
    }

    private static MultiBlockDefinition storageSystem(Holder<Block> owner, BlockState system, BlockState energyCell) {
        MultiBlockDefinition.Builder builder = MultiBlockDefinition.builder(owner)
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
            .setBlock(pos(2, 2, 1), NEBlocks.STORAGE_CASING.getDefaultState());

        BlockState casing = NEBlocks.STORAGE_CASING.getDefaultState();
        for (int y = 0; y < 3; y++) {
            builder.setBlock(pos(0, y, 0), casing);
            builder.setBlock(pos(0, y, 1), casing);
        }

        return builder
            .setBlockRepeatable(pos(-1, 0, 0), Direction.WEST, NEBlocks.ECO_DRIVE.getDefaultState())
            .setBlockRepeatable(pos(-1, 1, 0), Direction.WEST, NEBlocks.ECO_DRIVE.getDefaultState())
            .setBlockRepeatable(pos(-1, 2, 0), Direction.WEST, NEBlocks.ECO_DRIVE.getDefaultState())
            .setBlockRepeatable(pos(-1, 0, 1), Direction.WEST, energyCell)
            .setBlockRepeatable(pos(-1, 1, 1), Direction.WEST, NEBlocks.STORAGE_VENT.getDefaultState().setValue(ECOStorageVentBlock.FACING, Direction.SOUTH))
            .setBlockRepeatable(pos(-1, 2, 1), Direction.WEST, energyCell)
            .setBlockWithRepeatShifted(pos(0, 0, 0), Direction.WEST, 1, casing)
            .setBlockWithRepeatShifted(pos(0, 0, 1), Direction.WEST, 1, casing)
            .setBlockWithRepeatShifted(pos(0, 1, 0), Direction.WEST, 1, casing)
            .setBlockWithRepeatShifted(pos(0, 1, 1), Direction.WEST, 1, casing)
            .setBlockWithRepeatShifted(pos(0, 2, 0), Direction.WEST, 1, casing)
            .setBlockWithRepeatShifted(pos(0, 2, 1), Direction.WEST, 1, casing)
            .expandMin(1)
            .expandMax(NEConfig.storageSystemMaxLength - 4)
            .onFormed((pos, level) -> {
                BlockState state = level.getBlockState(pos);
                BlockState newState = state;
                if (state.hasProperty(NEBlock.FORMED)) {
                    newState = newState.setValue(NEBlock.FORMED, true);
                }
                if (newState.hasProperty(ECOMachineCasing.INVISIBLE)) {
                    Vec3 myPos = pos.getCenter();
                    Vec3 controllerPos = new Vec3(1.5, 1.5, 0.5);
                    newState = newState.setValue(
                        ECOMachineCasing.INVISIBLE,
                        myPos.distanceToSqr(controllerPos) <= 3
                    );
                }
                if (newState != state) {
                    level.setBlockAndUpdate(pos, newState);
                }
            })
            .create();
    }

    private static BlockPos pos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    public static MultiBlockDefinition getComputationSystemDefinition(IECOTier tier) {
        return switch (tier.getTier()) {
            case 1 -> COMPUTATION_SYSTEM_L4;
            case 2 -> COMPUTATION_SYSTEM_L6;
            case 3 -> COMPUTATION_SYSTEM_L9;
            default -> null;
        };
    }

    public static MultiBlockDefinition getStorageSystemDefinition(IECOTier tier) {
        return switch (tier.getTier()) {
            case 1 -> STORAGE_SYSTEM_L4;
            case 2 -> STORAGE_SYSTEM_L6;
            case 3 -> STORAGE_SYSTEM_L9;
            default -> null;
        };
    }

    public static MultiBlockDefinition getCraftingSystemDefinition(IECOTier tier) {
        return switch (tier.getTier()) {
            case 1 -> CRAFTING_SYSTEM_L4;
            case 2 -> CRAFTING_SYSTEM_L6;
            case 3 -> CRAFTING_SYSTEM_L9;
            default -> null;
        };
    }

}
