package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Field-resolution bridge for addons compiled before 21.2.11, not an addon extension point.
 * JVM field lookup includes the descriptor and searches superclasses, so this field can coexist
 * with the hatch's 21.2.11 {@code LongFluidTank} field without changing either binary signature.
 */
public abstract class ECOFluidInputHatchLegacyFields
    extends NEBlockEntity<NECraftingCluster, ECOFluidInputHatchBlockEntity> {

    /**
     * The same buffer as the hatch's long-typed field, assigned by its constructor.
     * @deprecated Use {@link ECOFluidInputHatchBlockEntity#getFluidTankHandler()}.
     * Read and operate on the buffer; replacing either public field reference is unsupported.
     */
    @Deprecated
    public FluidTank tank;

    protected ECOFluidInputHatchLegacyFields(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, NECraftingClusterCalculator::new);
    }
}
