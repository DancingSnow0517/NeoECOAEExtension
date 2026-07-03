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
import appeng.util.ConfigManager;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.AEItemFilters;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.gui.ldlib.NELDLibUis;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEIntegratedWorkingStationUiState;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.util.AutoExportSidesNbt;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEBlockEntityUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public class ECOIntegratedWorkingStationBlockEntity extends AENetworkPowerBlockEntity
        implements IGridTickable, IUpgradeableObject, IConfigurableObject, NEBlockEntityUIHolder {
    private static final int MAX_INPUT_SLOTS = 9;
    private static final int MAX_PROCESSING_STEPS = 200;
    private static final int MAX_POWER_STORAGE = 500000;
    private static final int MAX_TANK_CAPACITY = 16000;

    private final IUpgradeInventory upgrades;
    private final IConfigManager configManager;

    private final AppEngInternalInventory inputInv = new AppEngInternalInventory(this, MAX_INPUT_SLOTS, 64);
    private final AppEngInternalInventory outputInv = new AppEngInternalInventory(this, 1, 64);
    private final InternalInventory inv = new CombinedInternalInventory(this.inputInv, this.outputInv);

    private final FilteredInternalInventory inputExposed =
            new FilteredInternalInventory(this.inputInv, AEItemFilters.INSERT_ONLY);
    private final FilteredInternalInventory outputExposed =
            new FilteredInternalInventory(this.outputInv, AEItemFilters.EXTRACT_ONLY);
    private final InternalInventory invExposed = new CombinedInternalInventory(this.inputExposed, this.outputExposed);
    private final IItemHandler exposedItemHandler = (IItemHandler) this.invExposed.toItemHandler();

    private final FluidTank inputTank = new FluidTank(MAX_TANK_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            onChangeTank();
        }

        @Override
        public FluidTank readFromNBT(CompoundTag nbt) {
            super.readFromNBT(nbt);
            // Manually trigger onContentsChanged after NBT restore since readFromNBT doesn't auto-notify
            // This ensures UI refreshes when tank data is synced from server
            onContentsChanged();
            return this;
        }
    };
    private final FluidTank outputTank = new FluidTank(MAX_TANK_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            onChangeTank();
        }

        @Override
        public FluidTank readFromNBT(CompoundTag nbt) {
            super.readFromNBT(nbt);
            // Manually trigger onContentsChanged after NBT restore
            onContentsChanged();
            return this;
        }
    };

    boolean shouldAutoExport;

    public boolean isShouldAutoExport() {
        return shouldAutoExport;
    }

    @Getter
    private final IFluidHandler fluidCombined = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? inputTank.getFluid() : outputTank.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return MAX_TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 ? inputTank.isFluidValid(stack) : outputTank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return inputTank.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return outputTank.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return outputTank.drain(maxDrain, action);
        }
    };

    private LazyOptional<IItemHandler> itemHandlerCap = LazyOptional.of(() -> exposedItemHandler);
    private LazyOptional<IFluidHandler> fluidHandlerCap = LazyOptional.of(() -> fluidCombined);

    @Getter
    private boolean working = false;

    @Setter
    @Getter
    private int processingTime = 0;

    private final EnumSet<RelativeSide> allowOutputs = EnumSet.allOf(RelativeSide.class);

    /** Set to true when inventory/fluid changes invalidate the cached recipe. */
    private boolean recipeCacheDirty = false;

    private boolean recipeCacheValid = false;

    private @Nullable IntegratedWorkingStationRecipe cachedTask = null;

    @SuppressWarnings("UnstableApiUsage")
    private final HashMap<Direction, Map<AEKeyType, ExternalStorageStrategy>> exportStrategies = new HashMap<>();

    @Getter
    @Setter
    private boolean showWarning = false;

    public ECOIntegratedWorkingStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);

        this.getMainNode().setIdlePowerUsage(0).addService(IGridTickable.class, this);
        this.setInternalMaxPower(MAX_POWER_STORAGE);

        this.upgrades =
                UpgradeInventories.forMachine(NEBlocks.INTEGRATED_WORKING_STATION, 4, this::onUpgradeInventoryChanged);
        this.configManager = new ConfigManager(this::onConfigChanged);
        this.configManager.registerSetting(Settings.AUTO_EXPORT, YesNo.NO);

        this.setPowerSides(getGridConnectableSides(getOrientation()));
    }

    public IItemHandler getInputItemHandler() {
        return (IItemHandler) inputExposed.toItemHandler();
    }

    public IItemHandler getOutputItemHandler() {
        return (IItemHandler) outputExposed.toItemHandler();
    }

    /** Returns the AE2 upgrade inventory as an IItemHandler for slot display. */
    public IItemHandler getUpgradeItemHandler() {
        return upgrades.toItemHandler();
    }

    public NEIntegratedWorkingStationUiState createIntegratedWorkingStationUiState() {
        IntegratedWorkingStationRecipe task = getTask();
        return new NEIntegratedWorkingStationUiState(
                (long) getInternalCurrentPower(),
                (long) getInternalMaxPower(),
                processingTime,
                MAX_PROCESSING_STEPS,
                task == null ? 0 : task.energy(),
                working,
                isAutoExportEnabled());
    }

    @Override
    public ModularUI createUI(Player player) {
        return NELDLibUis.createIntegratedWorkingStation(this, player);
    }

    // ── State ──

    public void setWorking(boolean working) {
        if (working == this.working) {
            return;
        }
        this.working = working;
        updateBlockState(working);
        setChanged();
    }

    private void updateBlockState(boolean working) {
        if (this.level == null || this.notLoaded() || this.isRemoved()) {
            return;
        }

        final BlockState current = this.level.getBlockState(this.worldPosition);
        if (current.getBlock() instanceof ECOIntegratedWorkingStation) {
            final BlockState newState = current.setValue(ECOIntegratedWorkingStation.WORKING, working);

            if (current != newState) {
                this.level.setBlock(this.worldPosition, newState, Block.UPDATE_ALL_IMMEDIATE);
            }
        }
    }

    public int getMaxProcessingTime() {
        return MAX_PROCESSING_STEPS;
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
        this.processingTime = data.getInt("processingTime");
        if (AutoExportSidesNbt.hasSavedSides(data)) {
            this.allowOutputs.clear();
            this.allowOutputs.addAll(AutoExportSidesNbt.loadFromTag(data));
        }
        shouldAutoExport = configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.put("inputTank", this.inputTank.writeToNBT(new CompoundTag()));
        data.put("outputTank", this.outputTank.writeToNBT(new CompoundTag()));
        upgrades.writeToNBT(data, "upgrades");
        configManager.writeToNBT(data);
        data.putInt("processingTime", this.processingTime);
        AutoExportSidesNbt.saveToTag(data, allowOutputs);
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.inv;
    }

    public InternalInventory getInput() {
        return this.inputInv;
    }

    public InternalInventory getOutput() {
        return this.outputInv;
    }

    public FluidTank getInputTank() {
        return inputTank;
    }

    public FluidTank getOutputTank() {
        return outputTank;
    }

    public boolean isAutoExportEnabled() {
        return configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
    }

    public void toggleAutoExport() {
        shouldAutoExport = !isAutoExportEnabled();
        configManager.putSetting(Settings.AUTO_EXPORT, shouldAutoExport ? YesNo.YES : YesNo.NO);
    }

    @Nullable @Override
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
        invalidateRecipeCache();
    }

    private void invalidateRecipeCache() {
        this.recipeCacheValid = false;
        this.recipeCacheDirty = true;
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    private void onUpgradeInventoryChanged() {
        invalidateRecipeCache();
        saveChanges();
        markContentsChanged();
    }

    /** Called by LDLib inventory bridges when the UI modifies the inventory. */
    public void onGuiInventoryChanged() {
        if (level != null && level.isClientSide) {
            return;
        }
        markInventoryChanged();
    }

    public void onGuiStateChanged() {
        markContentsChanged();
    }

    @Override
    public void onChangeInventory(InternalInventory inv, int slot) {
        markInventoryChanged();
    }

    public void onChangeTank() {
        if (level != null && level.isClientSide) {
            // On client side, notify UI state change for proper rendering update
            onGuiStateChanged();
            return;
        }
        markInventoryChanged();
    }

    private void markInventoryChanged() {
        onChangeInventory();
        markContentsChanged();
    }

    private void markContentsChanged() {
        setChanged();
        markForUpdate();
    }

    private boolean hasAutoExportWork() {
        return configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES
                && (!this.outputInv.getStackInSlot(0).isEmpty()
                        || !this.outputTank.getFluid().isEmpty());
    }

    private boolean hasCraftWork() {
        var task = this.getTask();
        if (task != null) {
            if (task.hasItemOutput()
                    && outputInv.insertItem(0, task.itemOutput(), true).isEmpty()) {
                return true;
            }
            if (task.hasFluidOutput()) {
                FluidStack fluidOutput = task.fluidOutput();
                if (outputTank.fill(fluidOutput, IFluidHandler.FluidAction.SIMULATE) == fluidOutput.getAmount()) {
                    return true;
                }
            }
        }

        this.setProcessingTime(0);
        return this.isWorking();
    }

    @Nullable public IntegratedWorkingStationRecipe getTask() {
        ensureRecipeCached();
        return this.cachedTask;
    }

    private void ensureRecipeCached() {
        if (!recipeCacheValid) {
            refreshRecipeCache();
        }
    }

    private void refreshRecipeCache() {
        IntegratedWorkingStationRecipe newTask = level == null ? null : findRecipe(level);
        if (!Objects.equals(cachedTask == null ? null : cachedTask.getId(), newTask == null ? null : newTask.getId())) {
            this.setProcessingTime(0);
        }
        this.cachedTask = newTask;
        this.recipeCacheValid = true;
        this.recipeCacheDirty = false;
        if (this.cachedTask == null) {
            this.setWorking(false);
        }
    }

    private @Nullable IntegratedWorkingStationRecipe findRecipe(Level level) {
        List<ItemStack> inputs = new ArrayList<>();
        for (var x = 0; x < this.inputInv.size(); x++) {
            inputs.add(this.inputInv.getStackInSlot(x));
        }
        return level.getRecipeManager()
                .getRecipeFor(
                        NERecipeTypes.INTEGRATED_WORKING_STATION.get(),
                        new IntegratedWorkingStationRecipe.Input(inputs, this.inputTank.getFluid()),
                        level)
                .orElse(null);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode iGridNode) {
        return new TickingRequest(1, 20, !hasAutoExportWork() && !this.hasCraftWork(), false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode iGridNode, int ticksSinceLastCall) {
        if (this.recipeCacheDirty) {
            refreshRecipeCache();
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

                final int progressReq = MAX_PROCESSING_STEPS - this.getProcessingTime();
                final float powerRatio = progressReq < speedFactor ? (float) progressReq / speedFactor : 1;
                final int requiredTicks = Mth.ceil((float) MAX_PROCESSING_STEPS / speedFactor);
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
                    setShowWarning(false);
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
                    // Add warning
                    setShowWarning(true);
                }
            });

            if (this.getProcessingTime() >= this.getMaxProcessingTime()) {
                final IntegratedWorkingStationRecipe out = this.getTask();
                if (out != null && tryCompleteRecipe(out)) {
                    this.setProcessingTime(0);
                    this.saveChanges();
                    this.invalidateRecipeCache();
                    this.setWorking(false);
                }
            }
        } else {
            setShowWarning(false);
        }

        if (this.pushOutResult()) {
            return TickRateModulation.URGENT;
        }

        return this.hasCraftWork()
                ? TickRateModulation.URGENT
                : this.hasAutoExportWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    private boolean tryCompleteRecipe(IntegratedWorkingStationRecipe recipe) {
        int[] itemConsumption = createItemConsumptionPlan(recipe);
        if (itemConsumption == null || !hasRequiredInputFluid(recipe)) {
            invalidateRecipeCache();
            return false;
        }

        ItemStack itemOutput = recipe.itemOutput().copy();
        FluidStack fluidOutput = recipe.fluidOutput().copy();
        if ((!itemOutput.isEmpty() && !outputInv.insertItem(0, itemOutput, true).isEmpty())
                || (!fluidOutput.isEmpty()
                        && outputTank.fill(fluidOutput, IFluidHandler.FluidAction.SIMULATE)
                                != fluidOutput.getAmount())) {
            return false;
        }

        for (int slot = 0; slot < itemConsumption.length; slot++) {
            int amount = itemConsumption[slot];
            if (amount > 0) {
                inputInv.extractItem(slot, amount, false);
            }
        }
        int fluidAmount = recipe.inputFluid().amount();
        if (fluidAmount > 0) {
            inputTank.drain(fluidAmount, IFluidHandler.FluidAction.EXECUTE);
        }
        if (!itemOutput.isEmpty()) {
            outputInv.insertItem(0, itemOutput, false);
        }
        if (!fluidOutput.isEmpty()) {
            outputTank.fill(fluidOutput, IFluidHandler.FluidAction.EXECUTE);
        }
        markContentsChanged();
        return true;
    }

    private @Nullable int[] createItemConsumptionPlan(IntegratedWorkingStationRecipe recipe) {
        return ECOIntegratedWorkingStationRecipeHelper.createItemConsumptionPlan(inputInv, recipe.inputItems());
    }

    private boolean hasRequiredInputFluid(IntegratedWorkingStationRecipe recipe) {
        return recipe.inputFluid().ingredient().isEmpty() || recipe.inputFluid().test(inputTank.getFluid());
    }

    private boolean pushOutResult() {
        if (!this.hasAutoExportWork()) {
            return false;
        }

        for (var side : allowOutputs) {
            var target = getTarget(getOrientation().getSide(side));

            if (target != null) {
                var source = IActionSource.ofMachine(this);
                var movedStacks = pushOutItemResult(target, source);
                movedStacks |= pushOutFluidResult(target, source);

                if (movedStacks) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean pushOutItemResult(MEStorage target, IActionSource source) {
        ItemStack outputStack = this.outputInv.getStackInSlot(0);
        if (outputStack.isEmpty()) {
            return false;
        }

        GenericStack genStack = GenericStack.fromItemStack(outputStack);
        if (genStack == null || genStack.what() == null) {
            return false;
        }

        long accepted = target.insert(genStack.what(), outputStack.getCount(), Actionable.SIMULATE, source);
        if (accepted <= 0) {
            return false;
        }

        int moveAmount = (int) Math.min(outputStack.getCount(), accepted);
        ItemStack extractedStack = this.outputInv.extractItem(0, moveAmount, false);
        if (extractedStack.isEmpty()) {
            return false;
        }

        long inserted = target.insert(genStack.what(), extractedStack.getCount(), Actionable.MODULATE, source);
        int insertedAmount = (int) Math.min(inserted, extractedStack.getCount());
        if (insertedAmount < extractedStack.getCount()) {
            ItemStack remainder = extractedStack.copy();
            remainder.setCount(extractedStack.getCount() - insertedAmount);
            this.outputInv.insertItem(0, remainder, false);
        }
        if (insertedAmount > 0) {
            markContentsChanged();
        }
        return insertedAmount > 0;
    }

    private boolean pushOutFluidResult(MEStorage target, IActionSource source) {
        FluidStack outFluid = this.outputTank.getFluid();
        if (outFluid.isEmpty()) {
            return false;
        }

        GenericStack fluid = GenericStack.fromFluidStack(outFluid);
        if (fluid == null || fluid.what() == null) {
            return false;
        }

        long accepted = target.insert(fluid.what(), outFluid.getAmount(), Actionable.SIMULATE, source);
        if (accepted <= 0) {
            return false;
        }

        int moveAmount = (int) Math.min(outFluid.getAmount(), accepted);
        FluidStack drainRequest = outFluid.copy();
        drainRequest.setAmount(moveAmount);
        FluidStack drained = this.outputTank.drain(drainRequest, IFluidHandler.FluidAction.EXECUTE);
        int drainedAmount = drained.getAmount();
        if (drainedAmount <= 0) {
            return false;
        }

        long inserted = target.insert(fluid.what(), drainedAmount, Actionable.MODULATE, source);
        int insertedAmount = (int) Math.min(inserted, drainedAmount);
        if (insertedAmount < drainedAmount) {
            drained.setAmount(drainedAmount - insertedAmount);
            this.outputTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        }

        if (this.outputTank.getFluidAmount() == 0) clearFluidOut();
        markContentsChanged();
        return insertedAmount > 0;
    }

    @SuppressWarnings("UnstableApiUsage")
    private @Nullable CompositeStorage getTarget(Direction dir) {
        if (this.exportStrategies.get(dir) == null) {
            var be = this.getBlockEntity();
            this.exportStrategies.put(
                    dir,
                    StackWorldBehaviors.createExternalStorageStrategies(
                            (ServerLevel) be.getLevel(), be.getBlockPos().relative(dir), dir.getOpposite()));
        }

        var externalStorages = new IdentityHashMap<AEKeyType, MEStorage>(2);
        for (var entry : exportStrategies.get(dir).entrySet()) {
            var wrapper = entry.getValue().createWrapper(false, () -> {});
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
        invalidateRecipeCache();

        saveChanges();
        markContentsChanged();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var upgrade : upgrades) {
            drops.add(upgrade);
        }
    }

    @Override
    public void exportSettings(SettingsFrom mode, CompoundTag data, @Nullable Player player) {
        super.exportSettings(mode, data, player);
        if (mode == SettingsFrom.MEMORY_CARD) {
            AutoExportSidesNbt.saveToTag(data, allowOutputs);
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, CompoundTag data, @Nullable Player player) {
        super.importSettings(mode, data, player);
        if (mode == SettingsFrom.MEMORY_CARD && AutoExportSidesNbt.hasSavedSides(data)) {
            this.allowOutputs.clear();
            this.allowOutputs.addAll(AutoExportSidesNbt.loadFromTag(data));
            this.exportStrategies.clear();
            markContentsChanged();
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.inputTank.setFluid(FluidStack.EMPTY);
        this.outputTank.setFluid(FluidStack.EMPTY);
        this.upgrades.clear();
    }

    public void clearFluid() {
        if (!this.inputTank.getFluid().isEmpty()) {
            this.inputTank.setFluid(FluidStack.EMPTY);
        }
    }

    public void clearFluidOut() {
        if (!this.outputTank.getFluid().isEmpty()) {
            this.outputTank.setFluid(FluidStack.EMPTY);
        }
    }

    /**
     * Handles left-click on the input fluid tank with a held container item.
     * Tries to empty a filled container into the input tank first;
     * if that fails, tries to fill an empty container from the input tank.
     */
    public void handleInputTankContainerClick(ServerPlayer player) {
        if (level == null || level.isClientSide) return;
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) return;

        int BUCKET_VOLUME = 1000;
        // Try to empty held container into input tank
        FluidActionResult result = FluidUtil.tryEmptyContainer(carried, inputTank, BUCKET_VOLUME, player, true);
        if (result.isSuccess()) {
            player.containerMenu.setCarried(result.getResult());
            return;
        }

        // Try to fill held empty container from input tank
        result = FluidUtil.tryFillContainer(carried, inputTank, BUCKET_VOLUME, player, true);
        if (result.isSuccess()) {
            player.containerMenu.setCarried(result.getResult());
        }
    }

    // ── Client sync (fluid tanks + processing state) ──

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandlerCap.cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidHandlerCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandlerCap.invalidate();
        fluidHandlerCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHandlerCap = LazyOptional.of(() -> exposedItemHandler);
        fluidHandlerCap = LazyOptional.of(() -> fluidCombined);
    }
}
