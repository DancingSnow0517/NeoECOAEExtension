package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.blockentity.grid.AENetworkPowerBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.me.storage.CompositeStorage;
import appeng.parts.automation.StackWorldBehaviors;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.AEItemFilters;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.api.components.AutoExportSides;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.gui.workstation.IntegratedWorkstationUI;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import cn.dancingsnow.neoecoae.platform.sync.NEManagedBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class ECOIntegratedWorkingStationBlockEntity extends AENetworkPowerBlockEntity
    implements NEManagedBlockEntity, IGridTickable, IUpgradeableObject, IConfigurableObject {

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        cn.dancingsnow.neoecoae.platform.ForgeBlockCapabilities.revive(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.put(getSyncTag(), cn.dancingsnow.neoecoae.platform.sync.ManagedInitialSync.write(this));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        if (tag.contains(getSyncTag(), 10)) {
            new com.lowdragmc.lowdraglib.networking.s2c.SPacketManagedPayload(tag.getCompound(getSyncTag()))
                .processPacket(this);
            CompoundTag aeTag = tag.copy();
            aeTag.remove(getSyncTag());
            super.handleUpdateTag(aeTag);
        } else {
            super.handleUpdateTag(tag);
        }
    }

    protected static final int MAX_INPUT_SLOTS = 9;
    protected static final int MAX_PROCESSING_STEPS = 200;
    // Holds one full high-energy network-switch recipe.
    protected static final int MAX_POWER_STORAGE = 16_000_000;
    protected static final int MAX_TANK_CAPACITY = 64_000;

    private final IUpgradeInventory upgrades;
    private final IConfigManager configManager;

    protected final AppEngInternalInventory inputInv = new AppEngInternalInventory(this, MAX_INPUT_SLOTS, 64);
    protected final AppEngInternalInventory outputInv = new AppEngInternalInventory(this, 1, 64);
    private final InternalInventory inv = new CombinedInternalInventory(this.inputInv, this.outputInv);

    private final FilteredInternalInventory inputExposed = new FilteredInternalInventory(this.inputInv, AEItemFilters.INSERT_ONLY);
    private final FilteredInternalInventory outputExposed = new FilteredInternalInventory(this.outputInv, AEItemFilters.EXTRACT_ONLY);
    private final InternalInventory invExposed = new CombinedInternalInventory(this.inputExposed, this.outputExposed);

    protected final FluidTank inputTank = new FluidTank(MAX_TANK_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            markForUpdate();
            setChanged();
            onChangeTank();
        }
    };
    protected final FluidTank outputTank = new FluidTank(MAX_TANK_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            markForUpdate();
            setChanged();
            onChangeTank();
        }
    };

    /**
     * Fluid sources and sinks can be supplied by a formed multiblock controller.
     * The standalone workstation keeps using its own tanks.
     */
    protected FluidTank getInputTank() {
        return inputTank;
    }

    protected FluidTank getOutputTank() {
        return outputTank;
    }

    @DescSynced
    boolean shouldAutoExport;

    @Getter
    private final IFluidHandler fluidCombined = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? getInputTank().getFluid() : getOutputTank().getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return MAX_TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 ? getInputTank().isFluidValid(stack) : getOutputTank().isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return getInputTank().fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return getOutputTank().drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return getOutputTank().drain(maxDrain, action);
        }
    };

    @Getter
    @DescSynced
    private boolean working = false;

    @Setter
    @Getter
    private int processingTime = 0;

    @Persisted
    @DescSynced
    private final EnumSet<RelativeSide> allowOutputs = EnumSet.allOf(RelativeSide.class);

    private boolean dirty = false;

    protected @Nullable IntegratedWorkingStationRecipe cachedTask = null;

    @SuppressWarnings("UnstableApiUsage")
    private final HashMap<Direction, Map<AEKeyType, ExternalStorageStrategy>> exportStrategies = new HashMap<>();
    private final net.minecraftforge.energy.IEnergyStorage externalEnergy=new appeng.helpers.ForgeEnergyAdapter(this);
    public @Nullable net.minecraftforge.energy.IEnergyStorage getEnergyStorage(@Nullable Direction side){
        return side==null||getPowerSides().contains(side)?externalEnergy:null;
    }

    public ECOIntegratedWorkingStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);

        this.getMainNode().setIdlePowerUsage(0).addService(IGridTickable.class, this);
        this.setInternalMaxPower(MAX_POWER_STORAGE);

        this.upgrades = UpgradeInventories.forMachine(NEBlocks.INTEGRATED_WORKING_STATION, 4, this::saveChanges);
        this.configManager = new appeng.util.ConfigManager(this::onConfigChanged);
        this.configManager.registerSetting(Settings.AUTO_EXPORT, YesNo.NO);

        this.setPowerSides(getGridConnectableSides(getOrientation()));
    }

    /**
     * Keep the workstation discoverable by integrations such as JDTE from every
     * side. The node service is registered independently of whether the node is
     * currently connected to an ME network.
     */
    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public void onReady() {
        super.onReady();
        // The node is created by the parent during onReady; apply the explicit
        // side set again so power and grid exposure stay in sync after loading.
        this.setPowerSides(getGridConnectableSides(getOrientation()));
        this.onGridConnectableSidesChanged();
    }

    public void setWorking(boolean working) {
        if (working != this.working) {
            updateBlockState(working);
            this.markForUpdate();
        }
        this.working = working;
    }

    private void updateBlockState(boolean working) {
        if (this.level == null || this.notLoaded() || this.isRemoved()) {
            return;
        }

        final BlockState current = this.level.getBlockState(this.worldPosition);
        if (current.getBlock() instanceof ECOIntegratedWorkingStation) {
            final BlockState newState = current.setValue(ECOIntegratedWorkingStation.WORKING, working);

            if (current != newState) {
                this.level.setBlock(this.worldPosition, newState, Block.UPDATE_CLIENTS);
            }
        }
    }

    public int getMaxProcessingTime() {
        var task = getTask();
        return task == null ? MAX_PROCESSING_STEPS : (int) Math.min(Integer.MAX_VALUE, (long) task.processingTicks() * 2);
    }

    @Override
    protected void saveVisualState(CompoundTag data) {
        super.saveVisualState(data);

        data.putBoolean("working", isWorking());
    }

    @Override
    protected void loadVisualState(CompoundTag data) {
        super.loadVisualState(data);

        setWorking(data.getBoolean("working"));
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        this.inputTank.readFromNBT(data.getCompound("inputTank"));
        this.outputTank.readFromNBT(data.getCompound("outputTank"));
        this.upgrades.readFromNBT(data, "upgrades");
        this.configManager.readFromNBT(data);
        shouldAutoExport = configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.put("inputTank", this.inputTank.writeToNBT(new CompoundTag()));
        data.put("outputTank", this.outputTank.writeToNBT(new CompoundTag()));
        upgrades.writeToNBT(data, "upgrades");
        configManager.writeToNBT(data);
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.inv;
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(ISegmentedInventory.STORAGE)) {
            return this.getInternalInventory();
        } else if (id.equals(ISegmentedInventory.UPGRADES)) {
            return this.upgrades;
        }

        return super.getSubInventory(id);
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(Direction facing) {
        return this.invExposed;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    private void onChangeInventory() {
        this.dirty = true;

        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    @Override
    public void onChangeInventory(appeng.api.inventories.InternalInventory inv, int slot) {
        onChangeInventory();
    }

    public void onChangeTank() {
        onChangeInventory();
    }

    private boolean hasAutoExportWork() {
        return configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES && (!this.outputInv.getStackInSlot(0).isEmpty() || !this.getOutputTank().getFluid().isEmpty());
    }

    private boolean hasCraftWork() {
        var task = this.getTask();
        if (task != null) {
            if (task.hasItemOutput() && outputInv.insertItem(0, task.itemOutput(), true).isEmpty()) {
                return true;
            }
            if (task.hasFluidOutput()) {
                FluidStack fluidOutput = task.fluidOutput();
                if (getOutputTank().fill(fluidOutput, IFluidHandler.FluidAction.SIMULATE) == fluidOutput.getAmount()) {
                    return true;
                }

            }
        }

        this.setProcessingTime(0);
        return this.isWorking();
    }

    @Nullable
    public IntegratedWorkingStationRecipe getTask() {
        if (this.cachedTask == null && level != null) {

            this.cachedTask = findRecipe(level);
        }
        return this.cachedTask;
    }

    private @Nullable IntegratedWorkingStationRecipe findRecipe(Level level) {
        List<ItemStack> inputs = new ArrayList<>();
        for (var x = 0; x < this.inputInv.size(); x++) {
            inputs.add(this.inputInv.getStackInSlot(x));
        }
        return level.getRecipeManager().getRecipeFor(
            NERecipeTypes.INTEGRATED_WORKING_STATION.get(),
            new IntegratedWorkingStationRecipe.Input(inputs, this.getInputTank().getFluid()),
            level
        ).orElse(null);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode iGridNode) {
        return new TickingRequest(1, 20, !hasAutoExportWork() && !this.hasCraftWork(), true);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode iGridNode, int ticksSinceLastCall) {
        if (this.dirty) {
            // Check if running recipe is still valid
            if (level != null) {
                var recipe = findRecipe(level);
                if (recipe == null) {
                    this.setProcessingTime(0);
                    this.setWorking(false);
                    this.cachedTask = null;
                }
            }
            this.markForUpdate();
            this.dirty = false;
        }

        if (this.hasCraftWork()) {
            this.setWorking(true);
            getMainNode().ifPresent(grid -> {
                IEnergyService eg = grid.getEnergyService();
                IEnergySource src = this;

                final int speedFactor =
                    switch (this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD)) {
                        case 0 -> 2; // 100 ticks
                        case 1 -> 3; // 66 ticks
                        case 2 -> 5; // 40 ticks
                        case 3 -> 10; // 20 ticks
                        case 4 -> 50; // 4 ticks
                        default -> 2; // 100 ticks
                    };

                final int progressReq = getMaxProcessingTime() - this.getProcessingTime();
                final float powerRatio = progressReq < speedFactor ? (float) progressReq / speedFactor : 1;
                final int requiredTicks = Mth.ceil((float) getMaxProcessingTime() / speedFactor);
                final int powerConsumption = Mth.floor(((float) getTask().energy() / requiredTicks) * powerRatio);
                final double powerThreshold = powerConsumption - 0.01;

                double powerReq = this.extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);

                if (powerReq <= powerThreshold) {
                    src = eg;
                    var oldPowerReq = powerReq;
                    powerReq = eg.extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                    if (oldPowerReq > powerReq) {
                        src = this;
                        powerReq = oldPowerReq;
                    }
                }

                if (powerReq > powerThreshold) {
                    src.extractAEPower(powerConsumption, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    this.setProcessingTime(this.getProcessingTime() + speedFactor);
                } else if (powerReq != 0) {
                    var progressRatio = src == this
                        ? powerReq / powerConsumption
                        : (powerReq - 10 * eg.getIdlePowerUsage()) / powerConsumption;
                    var factor = Mth.floor(progressRatio * speedFactor);

                    if (factor > 1) {
                        var extracted = src.extractAEPower(
                            (double) (powerConsumption * factor) / speedFactor,
                            Actionable.MODULATE,
                            PowerMultiplier.CONFIG);
                        var actualFactor = (int) Math.floor(extracted / powerConsumption * speedFactor);
                        this.setProcessingTime(this.getProcessingTime() + actualFactor);
                    }
                }
            });

            if (this.getProcessingTime() >= this.getMaxProcessingTime()) {
                final IntegratedWorkingStationRecipe out = this.getTask();
                if (out != null) {
                    final ItemStack itemOut = out.itemOutput();
                    final FluidStack fluidOut = out.fluidOutput();

                    boolean itemCanInsert = true;
                    boolean fluidCanInsert = true;

                    if (!itemOut.isEmpty()) {
                        itemCanInsert = this.outputInv.insertItem(0, itemOut, true).isEmpty();
                    }

                    if (!fluidOut.isEmpty()) {
                        fluidCanInsert = this.getOutputTank().fill(fluidOut, IFluidHandler.FluidAction.SIMULATE) >= fluidOut.getAmount() - 0.01;
                    }

                    // Only execute if both outputs can be placed; otherwise keep progress to retry later
                    if (itemCanInsert && fluidCanInsert) {
                        // perform actual insertion
                        boolean itemInserted = true;
                        boolean fluidInserted = true;

                        if (!itemOut.isEmpty()) {
                            itemInserted = this.outputInv.insertItem(0, itemOut, false).isEmpty();
                        }

                        if (!fluidOut.isEmpty()) {
                            int added = this.getOutputTank().fill(fluidOut, IFluidHandler.FluidAction.EXECUTE);
                            fluidInserted = added >= fluidOut.getAmount() - 0.01;
                        }

                        if (itemInserted && fluidInserted) {
                            // consume inputs
                            for (SizedIngredient itemInput : out.inputItems()) {
                                int remaining = itemInput.count();
                                for (int x = 0; x < this.inputInv.size(); x++) {
                                    var stack = this.inputInv.getStackInSlot(x);
                                    if (itemInput.ingredient().test(stack)) {
                                        if (stack.getCount() > remaining) {
                                            stack.shrink(remaining);
                                            remaining = 0;
                                        } else {
                                            remaining -= stack.getCount();
                                            stack.setCount(0);
                                        }
                                        this.inputInv.setItemDirect(x, stack);
                                    }

                                    if (remaining <= 0) {
                                        break;
                                    }
                                }
                            }

                            FluidStack fluidStack = this.getInputTank().getFluid();
                            if (out.inputFluid().test(fluidStack)) {
                                getInputTank().drain(cn.dancingsnow.neoecoae.platform.nbt.NEStacks.withAmount(fluidStack, out.inputFluid().amount()), IFluidHandler.FluidAction.EXECUTE);
                            }

                            this.setProcessingTime(0);
                            this.saveChanges();
                            this.cachedTask = null;
                            this.setWorking(false);
                        }
                    }
                }
            }
        } else {
        }

        if (this.pushOutResult()) {
            return TickRateModulation.URGENT;
        }

        return this.hasCraftWork() ? TickRateModulation.URGENT : this.hasAutoExportWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    private boolean pushOutResult() {
        if (!this.hasAutoExportWork()) {
            return false;
        }

        for (var side : allowOutputs) {
            var target = getTarget(getOrientation().getSide(side));

            if (target != null) {
                var source = IActionSource.ofMachine(this);
                var movedStacks = false;
                var genStack = GenericStack.fromItemStack(this.outputInv.getStackInSlot(0));
                if (genStack != null && genStack.what() != null) {
                    var extractedStack = this.outputInv.extractItem(0, 64, false);
                    var inserted = target.insert(genStack.what(), extractedStack.getCount(), Actionable.MODULATE, source);
                    extractedStack.setCount(extractedStack.getCount() - (int) inserted);
                    this.outputInv.insertItem(0, extractedStack, false);
                    movedStacks |= inserted > 0;
                }

                FluidStack outFluid = this.getOutputTank().getFluid();
                GenericStack fluid = GenericStack.fromFluidStack(outFluid);
                if (fluid != null && fluid.what() != null) {
                    var extracted = this.getOutputTank().drain(outFluid, IFluidHandler.FluidAction.EXECUTE).getAmount();
                    var inserted = target.insert(fluid.what(), extracted, Actionable.MODULATE, source);
                    this.getOutputTank().fill(cn.dancingsnow.neoecoae.platform.nbt.NEStacks.withAmount(outFluid, (int) (extracted - inserted)), IFluidHandler.FluidAction.EXECUTE);

                    if (this.getOutputTank().getFluidAmount() == 0) clearFluidOut();

                    movedStacks |= inserted > 0;
                }

                if (movedStacks) {
                    return true;
                }
            }
        }

        return false;
    }

    @SuppressWarnings("UnstableApiUsage")
    private @Nullable CompositeStorage getTarget(Direction dir) {
        if (this.exportStrategies.get(dir) == null) {
            var be = this.getBlockEntity();
            this.exportStrategies.put(dir, StackWorldBehaviors.createExternalStorageStrategies((ServerLevel) be.getLevel(), be.getBlockPos().relative(dir), dir.getOpposite()));
        }

        var externalStorages = new IdentityHashMap<AEKeyType, MEStorage>(2);
        for (var entry : exportStrategies.get(dir).entrySet()) {
            var wrapper = entry.getValue().createWrapper(false, () -> {
            });
            if (wrapper != null) {
                externalStorages.put(entry.getKey(), wrapper);
            }
        }

        if (!externalStorages.isEmpty()) {
            return new CompositeStorage(externalStorages);
        }
        return null;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.configManager;
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    private void onConfigChanged(IConfigManager manager, Setting<?> setting) {
        if (setting == Settings.AUTO_EXPORT) {
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
            shouldAutoExport = manager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
        }

        saveChanges();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var upgrade : upgrades) {
            drops.add(upgrade);
        }
    }

    @Override
    public void exportSettings(SettingsFrom mode, CompoundTag builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == SettingsFrom.MEMORY_CARD) {
            new AutoExportSides(allowOutputs).write(builder);
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, CompoundTag input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode == SettingsFrom.MEMORY_CARD) {
            AutoExportSides autoExportSides = AutoExportSides.read(input);
            if (autoExportSides != null) {
                this.allowOutputs.clear();
                this.allowOutputs.addAll(autoExportSides.sides());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.getInputTank().setFluid(FluidStack.EMPTY);
        this.getOutputTank().setFluid(FluidStack.EMPTY);
        this.upgrades.clear();
    }

    public void clearFluid() {
        this.getInputTank().setFluid(FluidStack.EMPTY);
    }

    public void clearFluidOut() {
        this.getOutputTank().setFluid(FluidStack.EMPTY);
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return IntegratedWorkstationUI.create(new IntegratedWorkstationUI.Config(
            getInputTank(), getOutputTank(), (IItemHandlerModifiable) getInternalInventory().toItemHandler(),
            (IItemHandlerModifiable) upgrades.toItemHandler(), () -> (int) ((long) processingTime * MAX_PROCESSING_STEPS / Math.max(1, getMaxProcessingTime())), MAX_PROCESSING_STEPS,
            this::clearFluid, this::clearFluidOut, () -> shouldAutoExport,
            enabled -> {
                shouldAutoExport = enabled;
                configManager.putSetting(Settings.AUTO_EXPORT, enabled ? YesNo.YES : YesNo.NO);
            },
            enabled -> shouldAutoExport = enabled,
            allowOutputs::contains, this::toggleOutputSide, this::syncOutputSide, this::getOutputNeighborItem,
            List::of), holder.player);
    }

    private void toggleOutputSide(RelativeSide side) {
        if (!allowOutputs.remove(side)) allowOutputs.add(side);
        try {
            exportStrategies.remove(getOrientation().getSide(side));
        } catch (RuntimeException failure) {
            log.warn("Failed to clear the workstation export strategy for {}", side, failure);
        }
        saveChanges();
        markForUpdate();
    }

    private void syncOutputSide(RelativeSide side, boolean enabled) {
        if (level != null && level.isClientSide) {
            if (enabled) allowOutputs.add(side);
            else allowOutputs.remove(side);
        }
    }

    private ItemStack getOutputNeighborItem(RelativeSide side) {
        if (level == null) return ItemStack.EMPTY;
        Direction direction;
        try {
            direction = getOrientation().getSide(side);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
        BlockPos target = worldPosition.relative(direction);
        if (!level.hasChunkAt(target)) return ItemStack.EMPTY;
        return new ItemStack(level.getBlockState(target).getBlock().asItem());
    }
}
