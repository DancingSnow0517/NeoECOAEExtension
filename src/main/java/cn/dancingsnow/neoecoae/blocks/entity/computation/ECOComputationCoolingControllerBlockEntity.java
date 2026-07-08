package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationCoolingController;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ECOComputationCoolingControllerBlockEntity extends AbstractComputationBlockEntity<ECOComputationCoolingControllerBlockEntity> {
    @Getter
    private final IECOTier tier;
    private boolean mirrored;

    public ECOComputationCoolingControllerBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
        this.tier = tier;
    }

    @Override
    public void updateState(boolean updateExposed) {
        if (isServerStopping()) {
            return;
        }
        super.updateState(updateExposed);
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(ECOComputationCoolingController.MIRRORED)) {
                BlockState newState = state.setValue(ECOComputationCoolingController.MIRRORED, formed && mirrored);
                if (newState != state) {
                    level.setBlock(
                        worldPosition,
                        newState,
                        Block.UPDATE_CLIENTS
                    );
                }
            }
        }
    }

    public void setMirrored(boolean mirrored) {
        this.mirrored = mirrored;
    }
}
