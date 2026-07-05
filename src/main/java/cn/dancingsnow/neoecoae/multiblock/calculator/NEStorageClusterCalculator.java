package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageVentBlock;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import com.mojang.serialization.DataResult;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

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
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        return verifyMirroredStructure(level, min, max, this::verifyInternalStructure);
    }

    private boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max, boolean mirrored) {
        var controllerCandidate = findSoleController(level, min, max, ECOStorageSystemBlockEntity.class);
        if (controllerCandidate.isEmpty()) {
            return false;
        }
        ECOStorageSystemBlockEntity controller = controllerCandidate.get().blockEntity();
        BlockPos controllerPos = controllerCandidate.get().pos();
        IECOTier tier = controller.getTier();
        ControllerOrientation orientation = controllerOrientation(controller.getBlockState(), mirrored);
        Direction back = orientation.back();
        Direction front = orientation.front();
        Direction top = orientation.top();
        Direction down = orientation.down();
        Direction left = orientation.left();
        Direction right = orientation.right();

        if (!validateCasing(level, controllerPos, top, down, left)) {
            return false;
        }
        if (!validateCasing(level, controllerPos, top, down, back)) {
            return false;
        }
        if (!validateInterface(level, controllerPos.relative(left).relative(back), top, down)) {
            return false;
        }
        if (!validateBlock(level, controllerPos.relative(top), BlockState::is, NEBlocks.STORAGE_CASING.get())) {
            return false;
        }
        if (!validateBlock(level, controllerPos.relative(down), BlockState::is, NEBlocks.STORAGE_CASING.get())) {
            return false;
        }
        BlockPos storageBlocksStart = controllerPos.relative(right).relative(top);
        BlockPos storageBlocksEnd = expandTowards(
                level,
                right,
                controllerPos.relative(right).relative(down),
                ((state, pos) -> state.is(NEBlocks.ECO_DRIVE.get())
                        && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == front));
        if (!validateBlocks(
                level,
                storageBlocksStart,
                storageBlocksEnd,
                state -> state.is(NEBlocks.ECO_DRIVE.get())
                        && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == front)) {
            return false;
        }
        BlockPos ventStart = controllerPos.relative(right).relative(back);
        DataResult<BlockPos> ventEndResult = validateBlockLine(
                level,
                right,
                ventStart,
                (it, pos) -> it.is(NEBlocks.STORAGE_VENT.get()) && it.getValue(ECOStorageVentBlock.FACING) == back);
        if (ventEndResult.error().isPresent()) {
            return false;
        }
        BlockPos ventEnd = ventEndResult.getOrThrow(false, ignored -> {});

        BlockPos upperEnergyCellStart =
                controllerPos.relative(back).relative(top).relative(right);
        DataResult<BlockPos> upperEnergyCellResult = validateBlockLine(
                level,
                right,
                upperEnergyCellStart,
                (state, pos) -> state.getBlock() instanceof ECOEnergyCellBlock cell
                        && tier.supportsComponentTier(
                                cell.getBlockEntity(level, pos).getTier())
                        && state.getValue(ECOEnergyCellBlock.FACING) == back);
        if (upperEnergyCellResult.error().isPresent()) {
            return false;
        }
        BlockPos upperEnergyCellEnd = upperEnergyCellResult.getOrThrow(false, ignored -> {});
        BlockPos lowerEnergyCellStart =
                controllerPos.relative(back).relative(down).relative(right);
        DataResult<BlockPos> lowerEnergyCellResult = validateBlockLine(
                level,
                right,
                lowerEnergyCellStart,
                (state, pos) -> state.getBlock() instanceof ECOEnergyCellBlock cell
                        && tier.supportsComponentTier(
                                cell.getBlockEntity(level, pos).getTier())
                        && state.getValue(ECOEnergyCellBlock.FACING) == back);
        if (lowerEnergyCellResult.error().isPresent()) {
            return false;
        }
        BlockPos lowerEnergyCellEnd = lowerEnergyCellResult.getOrThrow(false, ignored -> {});

        BlockPos.MutableBlockPos tailCasing =
                storageBlocksEnd.mutable().move(right).move(top);
        List<BlockPos> tailCasingPoses = List.of(
                upperEnergyCellEnd.relative(right),
                lowerEnergyCellEnd.relative(right),
                ventEnd.relative(right),
                tailCasing.immutable(),
                tailCasing.relative(top),
                tailCasing.relative(down));
        if (!ensureSameSurface(tailCasingPoses)) {
            return false;
        }
        for (BlockPos tailCasingPos : tailCasingPoses) {
            if (!validateBlock(level, tailCasingPos, BlockState::is, NEBlocks.STORAGE_CASING.get())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isValidBlockEntity(BlockEntity te) {
        return (te instanceof NEBlockEntity<?, ?> neBlockEntity
                && neBlockEntity.getCalculator() instanceof NEStorageClusterCalculator);
    }

    private boolean validateCasing(
            ServerLevel level, BlockPos controllerPos, Direction top, Direction down, Direction direction) {
        return validateCasing(level, controllerPos.relative(direction), top, down);
    }

    private boolean validateCasing(ServerLevel level, BlockPos centerPos, Direction top, Direction down) {
        return validateCasing(level, centerPos, top, down, NEBlocks.STORAGE_CASING);
    }

    private boolean validateInterface(ServerLevel level, BlockPos interfacePos, Direction top, Direction down) {
        return validateInterface(level, interfacePos, top, down, NEBlocks.STORAGE_INTERFACE, NEBlocks.STORAGE_CASING);
    }
}
