package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.StorageHelper;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import java.util.EnumSet;
import java.util.Set;
import cn.dancingsnow.neoecoae.blocks.ECOLargeIntegratedWorkingStationSmartCoolingModule;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public class ECOLargeIntegratedWorkingStationInputHatchBlockEntity
    extends NEBlockEntity<NEIntegratedWorkingStationCluster, ECOLargeIntegratedWorkingStationInputHatchBlockEntity> {
    public static final int CAPACITY = 1_024_000;
    public static final int SMART_COOLING_CAPACITY = 16_384_000;
    public static final int SMART_COOLING_PULL_INTERVAL = 200;
    private int targetTemperature = 75;
    private FluidStack markedCoolant = FluidStack.EMPTY;
    private boolean returnUnmarkedCoolant;
    private long nextCoolantPullTick;

    public boolean isSmartCoolingModule() {
        return getBlockState().getBlock() instanceof ECOLargeIntegratedWorkingStationSmartCoolingModule;
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

    public FluidStack getMarkedCoolant() { return markedCoolant.copy(); }

    boolean acceptsManualCoolant(FluidStack fluid) {
        return !returnUnmarkedCoolant && (markedCoolant.isEmpty()
            || FluidStack.isSameFluidSameComponents(fluid, markedCoolant));
    }

    public void setMarkedCoolant(FluidStack fluid) {
        if (!fluid.isEmpty() && !isCoolant(fluid)) return;
        FluidStack selected = fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(1);
        if (FluidStack.matches(markedCoolant, selected)) return;
        markedCoolant = selected;
        returnUnmarkedCoolant = selected.isEmpty() && !tank.isEmpty();
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

    private final FluidTankResourceHandler fluidResourceHandler = new FluidTankResourceHandler(tank);

    /** 26.1 fluid capability ({@code Capabilities.Fluid.BLOCK}) view of {@link #tank}. */
    public ResourceHandler<FluidResource> getFluidResourceHandler() { return fluidResourceHandler; }

    public ECOLargeIntegratedWorkingStationInputHatchBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        super(type, pos, blockState, NEIntegratedWorkingStationClusterCalculator::new);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return formed && cluster != null ? cluster.getInternalConnections(this) : EnumSet.noneOf(Direction.class);
    }

    @Override
    public AECableType getCableConnectionType(Direction side) {
        return AECableType.NONE;
    }

    private boolean isCoolant(FluidStack fluid) {
        if (level == null || fluid.isEmpty()) return false;
        for (var holder : appeng.crafting.RecipeAccess.byType(level, NERecipeTypes.COOLING.get())) {
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
        if (level.isClientSide()) return;
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
            ResourceHandler<FluidResource> source = level.getCapability(
                Capabilities.Fluid.BLOCK, neighborPos, face.getOpposite());
            if (source != null) {
                try (Transaction transaction = Transaction.open(null)) {
                    if (ResourceHandlerUtil.move(source, fluidResourceHandler, fluid -> true, tank.getCapacity(), transaction) > 0) {
                        transaction.commit();
                        return;
                    }
                }
            }
        }
    }

    private void pullNetworkCoolant() {
        if (returnUnmarkedCoolant && tank.isEmpty()) {
            returnUnmarkedCoolant = false;
            setChanged();
        }
        if (markedCoolant.isEmpty() && !returnUnmarkedCoolant) return;
        var cluster = getCluster();
        if (cluster == null || cluster.getController() == null || !getMainNode().isActive()) return;
        var controller = cluster.getController();
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        var inventory = grid.getStorageService().getInventory();
        if (!tank.isEmpty() && (returnUnmarkedCoolant
            || !FluidStack.isSameFluidSameComponents(tank.getFluid(), markedCoolant))) {
            // Return old stock before changing type, including when the mark was cleared.
            // A partial or rejected return must remain in the tank for the next attempt.
            long returned = inventory.insert(ECOKeyFactory.fluid(tank.getFluid()), tank.getFluidAmount(),
                Actionable.MODULATE, IActionSource.ofMachine(this));
            if (returned > 0) tank.drain((int) returned, IFluidHandler.FluidAction.EXECUTE);
            if (!tank.isEmpty()) return;
        }
        if (returnUnmarkedCoolant) {
            returnUnmarkedCoolant = false;
            setChanged();
        }
        if (markedCoolant.isEmpty() || !controller.isActiveCooling() || tank.getSpace() <= 0) return;
        int threshold = Math.clamp(cn.dancingsnow.neoecoae.config.NEConfig.smartCoolingRefillThresholdPercent, 1, 100);
        if ((long) tank.getFluidAmount() * 100 >= (long) tank.getCapacity() * threshold) return;
        AEFluidKey selected = ECOKeyFactory.fluid(markedCoolant);
        int space = tank.fill(selected.toStack(tank.getSpace()), IFluidHandler.FluidAction.SIMULATE);
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
    public void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        tank.serialize(data);
        data.putInt("targetTemperature", targetTemperature);
        cn.dancingsnow.neoecoae.util.LegacyNbt.write(data, tag -> tag.put("markedCoolant",
            cn.dancingsnow.neoecoae.util.LegacyNbt.saveFluid(cn.dancingsnow.neoecoae.util.LegacyNbt.registries(this), markedCoolant)));
        data.putBoolean("returnUnmarkedCoolant", returnUnmarkedCoolant);
    }

    @Override
    public void loadTag(ValueInput input) {
        super.loadTag(input);
        var data = cn.dancingsnow.neoecoae.util.LegacyNbt.read(input);
        var registries = input.lookup();
        tank.deserialize(input);
        targetTemperature = data.contains("targetTemperature") ? Math.clamp(data.getIntOr("targetTemperature", 0), 0, 99) : 75;
        // Old saves keep their real buffer but require an explicit mark before any new extraction.
        markedCoolant = cn.dancingsnow.neoecoae.util.LegacyNbt.parseFluid(registries, data.getCompoundOrEmpty("markedCoolant"));
        if (!markedCoolant.isEmpty()) markedCoolant.setAmount(1);
        returnUnmarkedCoolant = data.contains("returnUnmarkedCoolant") ? data.getBooleanOr("returnUnmarkedCoolant", false)
            : markedCoolant.isEmpty() && !tank.isEmpty();
        nextCoolantPullTick = 0;
    }
}
