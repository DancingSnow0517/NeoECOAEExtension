package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.gui.common.FluidHatchUI;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class ECOFluidHatchBlockEntity<C extends NECluster<C>> extends NEBlockEntity<C, ECOFluidHatchBlockEntity<C>> {

    public record Spec(boolean input, int capacity, boolean coolantOnly, String titleKey) {
    }

    public static final Spec CRAFTING_INPUT = new Spec(true, 16000, false, "block.neoecoae.input_hatch");
    public static final Spec CRAFTING_OUTPUT = new Spec(false, 16000, false, "block.neoecoae.output_hatch");
    public static final Spec LWS_INPUT = new Spec(
        true, 1_024_000, true, "block.neoecoae.large_integrated_working_station_input_hatch");
    public static final Spec LWS_OUTPUT = new Spec(
        false, 1_024_000, false, "block.neoecoae.large_integrated_working_station_output_hatch");

    @Getter
    private final Spec spec;

    public FluidTank tank;

    public ECOFluidHatchBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        NEClusterCalculator.Factory<C> calculator,
        Spec spec
    ) {
        super(type, pos, blockState, calculator);
        this.spec = spec;
        this.tank = new FluidTank(spec.capacity(), spec.coolantOnly() ? this::isCoolant : fluid -> true) {
            @Override
            protected void onContentsChanged() {
                persistAndSync();
                C cluster = getCluster();
                if (cluster != null) {
                    cluster.onFluidHatchChanged();
                }
            }
        };
    }

    private boolean isCoolant(FluidStack fluid) {
        if (level == null || fluid.isEmpty()) return false;
        for (var holder : level.getRecipeManager().getAllRecipesFor(NERecipeTypes.COOLING.get())) {
            if (holder.value().input().ingredient().test(fluid)) return true;
        }
        return false;
    }

    public boolean isInput() {
        return spec.input();
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        var cluster = getCluster();
        for (Direction face : Direction.values()) {
            BlockPos neighborPos = pos.relative(face);
            if (cluster != null && cluster.containsBlockEntity(level.getBlockEntity(neighborPos))) {
                continue;
            }
            IFluidHandler handler = level.getCapability(
                Capabilities.FluidHandler.BLOCK, neighborPos, face.getOpposite());
            if (handler == null) {
                continue;
            }
            FluidStack moved = isInput()
                ? FluidUtil.tryFluidTransfer(tank, handler, tank.getCapacity(), true)
                : FluidUtil.tryFluidTransfer(handler, tank, tank.getFluidAmount(), true);
            if (!moved.isEmpty()) {
                return;
            }
        }
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return FluidHatchUI.createFluidHatchUI(holder, tank, spec.titleKey(), spec.input(), true);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        tank.writeToNBT(registries, data);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        tank.readFromNBT(registries, data);
    }
}
