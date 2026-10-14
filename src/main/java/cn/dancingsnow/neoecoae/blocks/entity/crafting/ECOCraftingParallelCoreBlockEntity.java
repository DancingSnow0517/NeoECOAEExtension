package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.ECOParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Binary entry point for addons whose shadow cores extend the original crafting core. */
@Deprecated(forRemoval = false)
public class ECOCraftingParallelCoreBlockEntity extends ECOParallelCoreBlockEntity<NECraftingCluster> {
    public ECOCraftingParallelCoreBlockEntity(
        BlockEntityType<?> type, BlockPos pos, BlockState state, IECOTier tier
    ) {
        super(type, pos, state, tier, NECraftingClusterCalculator::new, 64);
    }
}
