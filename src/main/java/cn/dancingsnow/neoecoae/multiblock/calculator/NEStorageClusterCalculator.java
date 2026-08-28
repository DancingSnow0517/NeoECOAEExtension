package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageVentBlock;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public class NEStorageClusterCalculator extends NEClusterCalculator<NEStorageCluster> {
    public NEStorageClusterCalculator(NEBlockEntity<NEStorageCluster, ?> t) {
        super(t);
    }

    @Override
    public NEStorageCluster createCluster(ServerLevel level, BlockPos min, BlockPos max) {
        return new NEStorageCluster(min, max);
    }

    @Override
    protected int maxLength() {
        return NEConfig.storageSystemMaxLength;
    }

    @Override
    protected Holder<Block> casing() {
        return NEBlocks.STORAGE_CASING;
    }

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        Optional<ControllerContext<ECOStorageSystemBlockEntity>> contextResult = findUniqueController(
            level, min, max, ECOStorageSystemBlockEntity.class
        );
        if (contextResult.isEmpty()) return false;
        ControllerContext<ECOStorageSystemBlockEntity> context = contextResult.orElseThrow();
        ECOStorageSystemBlockEntity controller = context.controller();
        BlockPos controllerPos = context.position();
        IECOTier tier = controller.getTier();
        Direction front = context.front();
        Direction back = context.back();
        Direction top = context.top();
        Direction down = context.down();
        Direction interfaceSide = context.right();
        Direction expandSide = interfaceSide.getOpposite();

        if (verifyStructure(level, controllerPos, tier, front, back, top, down, interfaceSide, expandSide)) {
            controller.setMirrored(false);
            return true;
        }
        if (verifyStructure(level, controllerPos, tier, front, back, top, down, expandSide, interfaceSide)) {
            controller.setMirrored(true);
            return true;
        }
        controller.setMirrored(false);
        return false;
    }

    private boolean verifyStructure(
        ServerLevel level,
        BlockPos controllerPos,
        IECOTier tier,
        Direction front,
        Direction back,
        Direction top,
        Direction down,
        Direction staticSide,
        Direction expandSide
    ) {
        if (!validateCasing(level, controllerPos, top, down, staticSide)) return false;
        if (!validateCasing(level, controllerPos, top, down, back)) return false;
        if (!validateInterface(level, controllerPos.relative(staticSide).relative(back), top, down)) return false;
        if (!validateBlock(level, controllerPos.relative(top), BlockState::is, NEBlocks.STORAGE_CASING)) {
            return false;
        }
        if (!validateBlock(level, controllerPos.relative(down), BlockState::is, NEBlocks.STORAGE_CASING)) {
            return false;
        }
        BlockPos transitionCenter = controllerPos.relative(expandSide);
        if (!validateCasing(level, transitionCenter, top, down)) return false;
        if (!validateCasing(level, transitionCenter.relative(back), top, down)) return false;

        BlockPos firstStorageColumn = transitionCenter.relative(expandSide);
        BlockPos storageBlocksStart = firstStorageColumn.relative(top);
        BlockPos storageBlocksEnd = expandTowards(
            level,
            expandSide,
            firstStorageColumn.relative(down),
            matchingStateFacing(NEBlocks.ECO_DRIVE, front)
        );
        if (!validateBlocks(
            level,
            storageBlocksStart,
            storageBlocksEnd,
            state -> state.is(NEBlocks.ECO_DRIVE)
                && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == front
        )) {
            return false;
        }
        BlockPos ventStart = firstStorageColumn.relative(back);
        Optional<BlockPos> ventEndResult = validateBlockLine(
            level,
            expandSide,
            ventStart,
            matchingStateFacing(NEBlocks.STORAGE_VENT, back)
        );
        if (ventEndResult.isEmpty()) {
            return false;
        }
        BlockPos ventEnd = ventEndResult.orElseThrow();

        BlockPos upperEnergyCellStart = firstStorageColumn.relative(back).relative(top);
        Optional<BlockPos> upperEnergyCellResult = validateBlockLine(
            level,
            expandSide,
            upperEnergyCellStart,
            (state, pos) -> state.getBlock() instanceof ECOEnergyCellBlock cell
                && tier.supportsComponentTier(cell.getBlockEntity(level, pos).getTier())
                && state.getValue(ECOEnergyCellBlock.FACING) == back
        );
        if (upperEnergyCellResult.isEmpty()) {
            return false;
        }
        BlockPos upperEnergyCellEnd = upperEnergyCellResult.orElseThrow();
        if (upperEnergyCellEnd.equals(upperEnergyCellStart)
            && !validateBlock(
                level,
                upperEnergyCellStart,
                state -> state.getBlock() instanceof ECOEnergyCellBlock cell
                    && tier.supportsComponentTier(cell.getBlockEntity(level, upperEnergyCellEnd).getTier())
                    && state.getValue(ECOEnergyCellBlock.FACING) == back
            )) {
            return false;
        }
        BlockPos lowerEnergyCellStart = firstStorageColumn.relative(back).relative(down);
        Optional<BlockPos> lowerEnergyCellResult = validateBlockLine(
            level,
            expandSide,
            lowerEnergyCellStart,
            (state, pos) -> state.getBlock() instanceof ECOEnergyCellBlock cell
                && tier.supportsComponentTier(cell.getBlockEntity(level, pos).getTier())
                && state.getValue(ECOEnergyCellBlock.FACING) == back
        );
        if (lowerEnergyCellResult.isEmpty()) {
            return false;
        }
        BlockPos lowerEnergyCellEnd = lowerEnergyCellResult.orElseThrow();

        BlockPos.MutableBlockPos tailCasing = storageBlocksEnd.mutable().move(expandSide).move(top);
        List<BlockPos> tailCasingPoses = List.of(
            upperEnergyCellEnd.relative(expandSide),
            lowerEnergyCellEnd.relative(expandSide),
            ventEnd.relative(expandSide),
            tailCasing.immutable(),
            tailCasing.relative(top),
            tailCasing.relative(down)
        );
        if (!ensureSameSurface(tailCasingPoses)) {
            return false;
        }
        return validateBlocks(level, tailCasingPoses, BlockState::is, NEBlocks.STORAGE_CASING);
    }

    @Override
    public boolean isValidBlockEntity(BlockEntity te) {
        return (te instanceof NEBlockEntity<?,?> neBlockEntity && neBlockEntity.getCalculator() instanceof NEStorageClusterCalculator);
    }

    private boolean validateInterface(ServerLevel level, BlockPos interfacePos, Direction top, Direction down) {
        return validateInterface(level, interfacePos, top, down, NEBlocks.STORAGE_INTERFACE, NEBlocks.STORAGE_CASING);
    }
}
