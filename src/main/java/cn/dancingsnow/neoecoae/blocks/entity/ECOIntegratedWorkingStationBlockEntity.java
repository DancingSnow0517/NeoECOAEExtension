package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;

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
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.me.storage.CompositeStorage;
import appeng.parts.automation.StackWorldBehaviors;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.AEItemFilters;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEDataComponents;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.api.components.AutoExportSides;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.gui.workstation.IntegratedWorkstationUI;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

public class ECOIntegratedWorkingStationBlockEntity extends AENetworkedPoweredBlockEntity
    implements ISyncPersistRPCBlockEntity, IGridTickable, IUpgradeableObject, IConfigurableObject {

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

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

    private boolean dirty = true;

    private @Nullable ResourceLocation standaloneRecipeId;
    private int standaloneSteps;
    private int standaloneEnergy;
    private double standalonePaid;

    protected @Nullable IntegratedWorkingStationRecipe cachedTask = null;

    @SuppressWarnings("UnstableApiUsage")
    private final HashMap<Direction, Map<AEKeyType, ExternalStorageStrategy>> exportStrategies = new HashMap<>();

    public ECOIntegratedWorkingStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);

        this.getMainNode().setIdlePowerUsage(0).addService(IGridTickable.class, this);
        this.setInternalMaxPower(MAX_POWER_STORAGE);

        this.upgrades = UpgradeInventories.forMachine(NEBlocks.INTEGRATED_WORKING_STATION, 4, this::saveChanges);
        this.configManager = IConfigManager.builder(this::onConfigChanged)
            .registerSetting(Settings.AUTO_EXPORT, YesNo.NO)
            .build();

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
        if (standaloneRecipeId != null) return standaloneSteps;
        var task = getTask();
        return task == null ? MAX_PROCESSING_STEPS : processingSteps(task);
    }

    private static int processingSteps(IntegratedWorkingStationRecipe task) {
        return (int) Math.min(Integer.MAX_VALUE, (long) task.processingTicks() * 2);
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
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.inputTank.readFromNBT(registries, data.getCompound("inputTank"));
        this.outputTank.readFromNBT(registries, data.getCompound("outputTank"));
        this.upgrades.readFromNBT(data, "upgrades", registries);
        this.configManager.readFromNBT(data, registries);
        shouldAutoExport = configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
        resetStandaloneProcessing();
        if (data.contains("standaloneJob")) {
            var job = data.getCompound("standaloneJob");
            standaloneRecipeId = ResourceLocation.tryParse(job.getString("recipe"));
            standaloneSteps = job.getInt("steps");
            standaloneEnergy = job.getInt("energy");
            standalonePaid = job.getDouble("paid");
            processingTime = job.getInt("progress");
            if (standaloneRecipeId == null || standaloneSteps <= 0 || processingTime < 0 || processingTime > standaloneSteps
                || !(standalonePaid >= 0 && standalonePaid <= standaloneEnergy)) resetStandaloneProcessing();
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.put("inputTank", this.inputTank.writeToNBT(registries, new CompoundTag()));
        data.put("outputTank", this.outputTank.writeToNBT(registries, new CompoundTag()));
        upgrades.writeToNBT(data, "upgrades", registries);
        configManager.writeToNBT(data, registries);
        if (standaloneRecipeId != null && (processingTime > 0 || standalonePaid > 0)) {
            var job = new CompoundTag();
            job.putString("recipe", standaloneRecipeId.toString());
            job.putInt("steps", standaloneSteps);
            job.putInt("energy", standaloneEnergy);
            job.putDouble("paid", standalonePaid);
            job.putInt("progress", processingTime);
            data.put("standaloneJob", job);
        }
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
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        onChangeInventory();
    }

    public void onChangeTank() {
        onChangeInventory();
    }

    private boolean hasAutoExportWork() {
        return configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES && (!this.outputInv.getStackInSlot(0).isEmpty() || !this.getOutputTank().getFluid().isEmpty());
    }

    private boolean canPlaceOutputs(@Nullable IntegratedWorkingStationRecipe task) {
        return task != null && (task.hasItemOutput() || task.hasFluidOutput())
            && (!task.hasItemOutput() || outputInv.insertItem(0, task.itemOutput(), true).isEmpty())
            && (!task.hasFluidOutput() || getOutputTank().fill(task.fluidOutput(), IFluidHandler.FluidAction.SIMULATE)
                == task.fluidOutput().getAmount());
    }

    @Nullable
    public IntegratedWorkingStationRecipe getTask() {
        if (this.cachedTask == null && level != null) {
            var holder = findRecipe(level);
            this.cachedTask = holder == null ? null : holder.value();
        }
        return this.cachedTask;
    }

    private @Nullable RecipeHolder<IntegratedWorkingStationRecipe> findRecipe(Level level) {
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

    private void resetStandaloneProcessing() {
        standaloneRecipeId = null;
        standaloneSteps = 0;
        standaloneEnergy = 0;
        standalonePaid = 0;
        processingTime = 0;
        cachedTask = null;
        dirty = true;
    }

    private void refreshStandaloneTask(Level level) {
        var holder = findRecipe(level);
        var recipeId = holder == null ? null : holder.id();
        if (!Objects.equals(recipeId, standaloneRecipeId)) {
            resetStandaloneProcessing();
            standaloneRecipeId = recipeId;
            if (holder != null) {
                standaloneSteps = processingSteps(holder.value());
                standaloneEnergy = holder.value().energy();
            }
            setChanged();
        }
        cachedTask = holder == null ? null : holder.value();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode iGridNode) {
        return new TickingRequest(1, 20, standaloneRecipeId == null && !hasAutoExportWork() && !canPlaceOutputs(getTask()));
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode iGridNode, int ticksSinceLastCall) {
        if (level != null && (dirty || cachedTask == null || standaloneRecipeId == null)) refreshStandaloneTask(level);
        if (this.dirty) {
            this.markForUpdate();
            this.dirty = false;
        }

        var task = cachedTask;
        if (canPlaceOutputs(task)) {
            this.setWorking(true);
            getMainNode().ifPresent(grid -> advanceStandaloneProcessing(grid.getEnergyService()));

            if (processingTime == standaloneSteps && !dirty && canPlaceOutputs(task)) finishStandaloneRecipe(task);
        } else if (isWorking()) {
            setWorking(false);
        }

        if (this.pushOutResult()) {
            return TickRateModulation.URGENT;
        }

        return canPlaceOutputs(getTask()) ? TickRateModulation.URGENT : hasAutoExportWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    private void finishStandaloneRecipe(IntegratedWorkingStationRecipe task) {
        if (task.hasItemOutput() && !outputInv.insertItem(0, task.itemOutput(), false).isEmpty()) return;
        if (task.hasFluidOutput()
            && getOutputTank().fill(task.fluidOutput(), IFluidHandler.FluidAction.EXECUTE) != task.fluidOutput().getAmount()) return;

        for (SizedIngredient input : task.inputItems()) {
            int remaining = input.count();
            for (int slot = 0; slot < inputInv.size() && remaining > 0; slot++) {
                var stack = inputInv.getStackInSlot(slot);
                if (!stack.isEmpty() && input.ingredient().test(stack)) {
                    int taken = Math.min(remaining, stack.getCount());
                    stack.shrink(taken);
                    inputInv.setItemDirect(slot, stack);
                    remaining -= taken;
                }
            }
        }
        if (!task.inputFluid().ingredient().isEmpty()) {
            getInputTank().drain(task.inputFluid().amount(), IFluidHandler.FluidAction.EXECUTE);
        }
        resetStandaloneProcessing();
        saveChanges();
        setWorking(false);
    }

    private void advanceStandaloneProcessing(IEnergyService grid) {
        int speed = switch (upgrades.getInstalledUpgrades(AEItems.SPEED_CARD)) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 10;
            case 4 -> 50;
            default -> 2;
        };
        int target = processingTime + Math.min(speed, standaloneSteps - processingTime);
        long cost = Math.ceilDiv((long) standaloneEnergy * target, standaloneSteps);
        double paid = cost > standalonePaid ? drawStandalonePower(grid, cost - standalonePaid) : 0;
        if (paid > 0) standalonePaid = Math.min(cost, standalonePaid + paid);
        int funded = standalonePaid >= cost ? target : (int) ((long) standalonePaid * standaloneSteps / standaloneEnergy);
        if (processingTime != funded || paid > 0) {
            processingTime = funded;
            setChanged();
        }
    }

    private double drawStandalonePower(IEnergyService grid, double requested) {
        IEnergySource source = this;
        double available = extractAEPower(requested, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (available < requested) {
            double network = grid.extractAEPower(requested, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            if (network < requested - 0.01) network -= 10 * grid.getIdlePowerUsage();
            if (network > available) {
                source = grid;
                available = network;
            }
        }
        return available > 0 ? source.extractAEPower(Math.min(requested, available), Actionable.MODULATE, PowerMultiplier.CONFIG) : 0;
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
                var genStack = ECOKeyFactory.fromItemStack(this.outputInv.getStackInSlot(0));
                if (genStack != null && genStack.what() != null) {
                    var extractedStack = this.outputInv.extractItem(0, 64, false);
                    var inserted = target.insert(genStack.what(), extractedStack.getCount(), Actionable.MODULATE, source);
                    extractedStack.setCount(extractedStack.getCount() - (int) inserted);
                    this.outputInv.insertItem(0, extractedStack, false);
                    movedStacks |= inserted > 0;
                }

                FluidStack outFluid = this.getOutputTank().getFluid();
                GenericStack fluid = ECOKeyFactory.fromFluidStack(outFluid);
                if (fluid != null && fluid.what() != null) {
                    var extracted = this.getOutputTank().drain(outFluid, IFluidHandler.FluidAction.EXECUTE);
                    var inserted = target.insert(fluid.what(), extracted.getAmount(), Actionable.MODULATE, source);
                    if (inserted < extracted.getAmount()) {
                        this.getOutputTank().fill(extracted.copyWithAmount(extracted.getAmount() - (int) inserted), IFluidHandler.FluidAction.EXECUTE);
                    }

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
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == SettingsFrom.MEMORY_CARD) {
            builder.set(NEDataComponents.AUTO_EXPORT_SIDES, new AutoExportSides(allowOutputs));
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode == SettingsFrom.MEMORY_CARD) {
            AutoExportSides autoExportSides = input.get(NEDataComponents.AUTO_EXPORT_SIDES);
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
            getInputTank(), getOutputTank(), (IItemHandlerModifiable) getExposedItemHandler(null),
            (IItemHandlerModifiable) upgrades.toItemHandler(), () -> (int) ((long) processingTime * MAX_PROCESSING_STEPS / Math.max(1, getMaxProcessingTime())), MAX_PROCESSING_STEPS,
            this::clearFluid, this::clearFluidOut, () -> shouldAutoExport,
            enabled -> {
                shouldAutoExport = enabled;
                configManager.putSetting(Settings.AUTO_EXPORT, enabled ? YesNo.YES : YesNo.NO);
            },
            enabled -> shouldAutoExport = enabled,
            allowOutputs::contains, this::toggleOutputSide, this::syncOutputSide, this::getOutputNeighborItem,
            () -> this instanceof ECOLargeIntegratedWorkingStationBlockEntity workstation
                ? List.of(workstation.createAutoBuildButton(holder.player)) : List.of()), holder.player);
    }

    private void toggleOutputSide(RelativeSide side) {
        if (!allowOutputs.remove(side)) allowOutputs.add(side);
        exportStrategies.remove(getOrientation().getSide(side));
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
        BlockPos target = worldPosition.relative(getOrientation().getSide(side));
        if (!level.hasChunkAt(target)) return ItemStack.EMPTY;
        return new ItemStack(level.getBlockState(target).getBlock().asItem());
    }
}
