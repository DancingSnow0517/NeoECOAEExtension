package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.api.IECOTier;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ECOCraftingParallelCoreBlockEntity extends AbstractCraftingBlockEntity<ECOCraftingParallelCoreBlockEntity> {

    @Getter
    private final IECOTier tier;

    public ECOCraftingParallelCoreBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
        this.tier = tier;
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setIdlePowerUsage(64);
    }
}

