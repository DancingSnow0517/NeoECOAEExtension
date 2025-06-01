package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.api.IECOTier;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ECOComputationThreadingCoreBlockEntity extends AbstractComputationBlockEntity<ECOComputationThreadingCoreBlockEntity> {
    @Getter
    private final IECOTier tier;

    public ECOComputationThreadingCoreBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
        this.tier = tier;
    }

}
