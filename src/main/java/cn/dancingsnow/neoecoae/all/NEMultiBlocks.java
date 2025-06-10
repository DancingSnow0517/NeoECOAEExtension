package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageVentBlock;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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

    private static BlockPos pos(int x, int y, int z) {
        return new BlockPos(x, y, z);
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
            .create();
    }
}
