package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.StorageHelper;
import cn.dancingsnow.neoecoae.blocks.ECOLargeIntegratedWorkingStationSmartCoolingModule;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.gui.common.FluidHatchUI;
import cn.dancingsnow.neoecoae.gui.workstation.SmartCoolingModuleUI;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEIntegratedWorkingStationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.platform.ui.NEBlockEntityUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public class ECOLargeIntegratedWorkingStationInputHatchBlockEntity
    extends NEBlockEntity<NEIntegratedWorkingStationCluster, ECOLargeIntegratedWorkingStationInputHatchBlockEntity> implements NEBlockEntityUI {
    public static final int CAPACITY = 1_024_000;
    public static final int SMART_COOLING_CAPACITY = 16_384_000;
    public static final int SMART_COOLING_PULL_INTERVAL = 200;
    private int targetTemperature = 75;
    private FluidStack markedCoolant = FluidStack.EMPTY;
    private long nextCoolantPullTick;

    public boolean isSmartCoolingModule() {
        return getBlockState().getBlock() instanceof cn.dancingsnow.neoecoae.blocks.ECOLargeIntegratedWorkingStationSmartCoolingModule;
    }

    public int getTargetTemperature() { return targetTemperature; }

    public void setTargetTemperature(int target) {
        int bounded = cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(target, 0, 99);
        if (targetTemperature != bounded) {
            targetTemperature = bounded;
            setChanged();
            markForUpdate();
            notifyController();
        }
    }

    public FluidStack getMarkedCoolant() { return markedCoolant.copy(); }

    public void setMarkedCoolant(FluidStack fluid) {
        if (!fluid.isEmpty() && !isCoolant(fluid)) return;
        FluidStack selected = fluid.isEmpty() ? FluidStack.EMPTY : new FluidStack(fluid, 1);
        if (markedCoolant.isFluidStackIdentical(selected)) return;
        markedCoolant = selected;
        setChanged();
        markForUpdate();
    }

    public final FluidTank tank = new FluidTank(isSmartCoolingModule() ? SMART_COOLING_CAPACITY : CAPACITY, this::isCoolant) {
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
            if (holder.input().ingredient().test(fluid)) return true;
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
            long now = level.getGameTime();
            if (now >= nextCoolantPullTick) {
                nextCoolantPullTick = now + SMART_COOLING_PULL_INTERVAL;
                pullNetworkCoolant();
            }
            return;
        }
        var cluster = getCluster();
        for (Direction face : Direction.values()) {
            BlockPos neighborPos = pos.relative(face);
            if (cluster != null && cluster.containsBlockEntity(level.getBlockEntity(neighborPos))) {
                continue;
            }
            IFluidHandler source = cn.dancingsnow.neoecoae.platform.ForgeBlockCapabilities.find(level, neighborPos, ForgeCapabilities.FLUID_HANDLER, face.getOpposite());
            if (source != null
                && !FluidUtil.tryFluidTransfer(tank, source, tank.getCapacity(), true).isEmpty()) {
                return;
            }
        }
    }

    private void pullNetworkCoolant() {
        if (markedCoolant.isEmpty() && tank.isEmpty()) return;
        var cluster = getCluster();
        if (cluster == null || cluster.getController() == null || !getMainNode().isActive()) return;
        var controller = cluster.getController();
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        var inventory = grid.getStorageService().getInventory();
        if (!tank.isEmpty() && (markedCoolant.isEmpty()
            || !tank.getFluid().isFluidEqual(markedCoolant))) {
            // Return old stock before changing type, including when the mark was cleared.
            // A partial or rejected return must remain in the tank for the next attempt.
            long returned = inventory.insert(ECOKeyFactory.fluid(tank.getFluid()), tank.getFluidAmount(),
                Actionable.MODULATE, IActionSource.ofMachine(this));
            if (returned > 0) tank.drain((int) returned, IFluidHandler.FluidAction.EXECUTE);
            if (!tank.isEmpty()) return;
        }
        if (markedCoolant.isEmpty() || !controller.isActiveCooling() || tank.getSpace() <= 0) return;
        AEFluidKey selected = ECOKeyFactory.fluid(markedCoolant);
        int space = tank.fill(selected.toStack(tank.getSpace()), IFluidHandler.FluidAction.SIMULATE);
        if (space <= 0) return;
        long extracted = StorageHelper.poweredExtraction(grid.getEnergyService(), inventory, selected,
            space, IActionSource.ofMachine(this));
        // The local tank cannot change during this server-thread operation; only commit the amount actually extracted.
        if (extracted > 0) tank.fill(selected.toStack((int) extracted), IFluidHandler.FluidAction.EXECUTE);
    }

    /** The smart cooling module uses the host UI runtime; ordinary hatches keep the LDLib fluid hatch UI. */
    public cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI createSmartCoolingUI(
        cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType.BlockUIHolder holder) {
        return SmartCoolingModuleUI.create(holder, this);
    }

    public ModularUI createUI(Player player) {
        return FluidHatchUI.createFluidHatchUI(
            this, player, tank, "block.neoecoae.large_integrated_working_station_input_hatch", true, true);
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        tank.writeToNBT(data);
        data.putInt("targetTemperature", targetTemperature);
        data.put("markedCoolant", markedCoolant.writeToNBT(new CompoundTag()));
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        tank.readFromNBT(data);
        targetTemperature = data.contains("targetTemperature") ? cn.dancingsnow.neoecoae.platform.JdkCompat.clamp(data.getInt("targetTemperature"), 0, 99) : 75;
        // Old saves keep their real buffer but require an explicit mark before any new extraction.
        markedCoolant = FluidStack.loadFluidStackFromNBT(data.getCompound("markedCoolant"));
        if (!markedCoolant.isEmpty()) markedCoolant.setAmount(1);
        nextCoolantPullTick = 0;
    }
}
