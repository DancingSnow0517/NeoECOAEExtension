package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.StorageHelper;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEFluids;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.gui.common.FluidHatchUI;
import cn.dancingsnow.neoecoae.gui.workstation.SmartCoolingModuleUI;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEIntegratedWorkingStationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
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

public class ECOLargeIntegratedWorkingStationInputHatchBlockEntity
    extends NEBlockEntity<NEIntegratedWorkingStationCluster, ECOLargeIntegratedWorkingStationInputHatchBlockEntity> {
    public static final int CAPACITY = 1_024_000;
    private int targetTemperature = 75;

    public boolean isSmartCoolingModule() {
        return getBlockState().is(NEBlocks.LARGE_INTEGRATED_WORKING_STATION_SMART_COOLING_MODULE);
    }

    public int getTargetTemperature() { return targetTemperature; }

    public void setTargetTemperature(int target) {
        int bounded = Math.clamp(target, 0, 99);
        if (targetTemperature != bounded) {
            targetTemperature = bounded;
            setChanged();
            markForUpdate();
            notifyController();
        }
    }

    public final FluidTank tank = new FluidTank(CAPACITY, this::isCoolant) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
            notifyController();
        }
    };

    public ECOLargeIntegratedWorkingStationInputHatchBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        super(type, pos, blockState, NEIntegratedWorkingStationClusterCalculator::new);
    }

    private boolean isCoolant(FluidStack fluid) {
        if (level == null || fluid.isEmpty()) return false;
        for (var holder : level.getRecipeManager().getAllRecipesFor(NERecipeTypes.COOLING.get())) {
            if (holder.value().input().ingredient().test(fluid)) return true;
        }
        return false;
    }

    private void notifyController() {
        if (getCluster() != null && getCluster().getController() != null) {
            getCluster().getController().onChangeTankFromHatch();
        }
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;
        if (isSmartCoolingModule()) {
            if (level.getGameTime() % 20 == 0) pullNetworkCoolant();
            return;
        }
        var cluster = getCluster();
        for (Direction face : Direction.values()) {
            BlockPos neighborPos = pos.relative(face);
            if (cluster != null && cluster.containsBlockEntity(level.getBlockEntity(neighborPos))) {
                continue;
            }
            IFluidHandler source = level.getCapability(
                Capabilities.FluidHandler.BLOCK, neighborPos, face.getOpposite());
            if (source != null
                && !FluidUtil.tryFluidTransfer(tank, source, tank.getCapacity(), true).isEmpty()) {
                return;
            }
        }
    }

    private void pullNetworkCoolant() {
        var cluster = getCluster();
        if (cluster == null || cluster.getController() == null || !getMainNode().isActive()) return;
        var controller = cluster.getController();
        // A small buffer covers one second of consumption without draining the network into an idle module.
        if (!controller.isActiveCooling()) return;
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        var inventory = grid.getStorageService().getInventory();
        if (controller.isOverheated() && !tank.isEmpty()
            && tank.getFluid().getFluid() != NEFluids.CRYOTHEUM_SOLUTION.getSource()) {
            // Recovery requires cryotheum. Preserve any weaker coolant the network cannot take back.
            long returned = inventory.insert(AEFluidKey.of(tank.getFluid()), tank.getFluidAmount(),
                Actionable.MODULATE, IActionSource.ofMachine(this));
            if (returned > 0) tank.drain((int) returned, IFluidHandler.FluidAction.EXECUTE);
            if (!tank.isEmpty()) return;
        }
        int bufferAmount = (int) Math.min(tank.getCapacity(),
            (long) controller.getCoolantPerTick() * LargeWorkstationThermal.TICKS_PER_SECOND);
        if (tank.getFluidAmount() >= bufferAmount) return;
        AEFluidKey selected = tank.isEmpty() ? null : AEFluidKey.of(tank.getFluid());
        if (selected == null) {
            int bestTier = 0;
            for (var entry : inventory.getAvailableStacks()) {
                if (!(entry.getKey() instanceof AEFluidKey fluid) || entry.getLongValue() <= 0) continue;
                FluidStack candidate = fluid.toStack(1);
                if (controller.isOverheated() && candidate.getFluid() != NEFluids.CRYOTHEUM_SOLUTION.getSource()) continue;
                for (var recipe : level.getRecipeManager().getAllRecipesFor(NERecipeTypes.COOLING.get())) {
                    int tier = recipe.value().maxOverclock();
                    if (LargeWorkstationOverclock.forCoolingTier(tier) != null
                        && tier > bestTier && recipe.value().input().ingredient().test(candidate)) {
                        selected = fluid;
                        bestTier = tier;
                    }
                }
            }
        }
        if (selected == null) return;
        int space = tank.fill(selected.toStack(bufferAmount - tank.getFluidAmount()), IFluidHandler.FluidAction.SIMULATE);
        if (space <= 0) return;
        long extracted = StorageHelper.poweredExtraction(grid.getEnergyService(), inventory, selected,
            space, IActionSource.ofMachine(this));
        // The local tank cannot change during this server-thread operation; only commit the amount actually extracted.
        if (extracted > 0) tank.fill(selected.toStack((int) extracted), IFluidHandler.FluidAction.EXECUTE);
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        if (isSmartCoolingModule()) return SmartCoolingModuleUI.create(holder, this);
        return FluidHatchUI.createFluidHatchUI(
            holder, tank, "block.neoecoae.large_integrated_working_station_input_hatch", true, true);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        tank.writeToNBT(registries, data);
        data.putInt("targetTemperature", targetTemperature);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        tank.readFromNBT(registries, data);
        targetTemperature = data.contains("targetTemperature") ? Math.clamp(data.getInt("targetTemperature"), 0, 99) : 75;
    }
}
