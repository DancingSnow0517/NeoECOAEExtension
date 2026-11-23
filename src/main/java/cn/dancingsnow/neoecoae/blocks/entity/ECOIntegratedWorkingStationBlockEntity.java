package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.integration.ae2.keyreuse.ECOKeyFactory;

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
import appeng.util.Icon;
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
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
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

    /** Fluid capability (26.1 Transfer API): slot 0 = input tank (insert only), slot 1 = output tank (extract only). */
    @Getter
    private final ResourceHandler<FluidResource> fluidCombined = new CombinedResourceHandler<>(
        FluidTankResourceHandler.insertOnly(this::getInputTank),
        FluidTankResourceHandler.extractOnly(this::getOutputTank));

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

    private Identifier standaloneRecipeId;
    private int standaloneSteps;
    private int standaloneEnergy;
    private double standalonePaid;

    public int getMaxProcessingTime() {
        if (standaloneRecipeId != null) return standaloneSteps;
        var task = getTask();
        return task == null ? MAX_PROCESSING_STEPS : processingSteps(task);
    }

    private static int processingSteps(IntegratedWorkingStationRecipe task) {
        return (int) Math.min(Integer.MAX_VALUE, (long) task.processingTicks() * 2);
    }

    @Override
    protected void saveVisualState(ValueOutput data) {
        super.saveVisualState(data);

        data.putBoolean("working", isWorking());
    }

    @Override
    protected void loadVisualState(ValueInput data) {
        super.loadVisualState(data);

        setWorking(data.getBooleanOr("working", false));
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);
        data.readChild("inputTank", inputTank);
        data.readChild("outputTank", outputTank);
        this.upgrades.readFromNBT(data, "upgrades");
        this.configManager.readFromNBT(data);
        shouldAutoExport = configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
        resetStandaloneProcessing();
        data.child("standaloneJob").ifPresent(job -> {
            standaloneRecipeId = job.read("recipe", Identifier.CODEC).orElse(null);
            standaloneSteps = job.getIntOr("steps", 0);
            standaloneEnergy = job.getIntOr("energy", 0);
            standalonePaid = job.getDoubleOr("paid", 0);
            processingTime = job.getIntOr("progress", 0);
            if (standaloneRecipeId == null || standaloneSteps <= 0 || processingTime < 0 || processingTime > standaloneSteps
                || !(standalonePaid >= 0 && standalonePaid <= standaloneEnergy)) resetStandaloneProcessing();
        });
    }

    @Override
    public void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        data.putChild("inputTank", inputTank);
        data.putChild("outputTank", outputTank);
        upgrades.writeToNBT(data, "upgrades");
        configManager.writeToNBT(data);
        if (standaloneRecipeId != null && (processingTime > 0 || standalonePaid > 0)) {
            var job = data.child("standaloneJob");
            job.store("recipe", Identifier.CODEC, standaloneRecipeId);
            job.putInt("steps", standaloneSteps);
            job.putInt("energy", standaloneEnergy);
            job.putDouble("paid", standalonePaid);
            job.putInt("progress", processingTime);
        }
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.inv;
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(Identifier id) {
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

    private boolean hasCraftWork(@Nullable IntegratedWorkingStationRecipe task) {
        if (task == null || (!task.hasItemOutput() && !task.hasFluidOutput())) return false;
        if (task.hasItemOutput() && !outputInv.insertItem(0, task.itemOutput(), true).isEmpty()) return false;
        if (!task.hasFluidOutput()) return true;

        var fluidOutput = task.fluidOutput();
        return getOutputTank().fill(fluidOutput, IFluidHandler.FluidAction.SIMULATE) == fluidOutput.getAmount();
    }

    @Nullable
    public IntegratedWorkingStationRecipe getTask() {
        if (this.cachedTask == null && level != null) {
            var holder = findRecipe(level);
            this.cachedTask = holder == null ? null : holder.value();
        }
        return this.cachedTask;
    }

    private @Nullable net.minecraft.world.item.crafting.RecipeHolder<IntegratedWorkingStationRecipe> findRecipe(Level level) {
        List<ItemStack> inputs = new ArrayList<>();
        for (var x = 0; x < this.inputInv.size(); x++) {
            inputs.add(this.inputInv.getStackInSlot(x));
        }
        return appeng.crafting.RecipeAccess.getRecipeFor(level, NERecipeTypes.INTEGRATED_WORKING_STATION.get(),
            new IntegratedWorkingStationRecipe.Input(inputs, getInputTank().getFluid()));
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
        var recipeId = holder == null ? null : holder.id().identifier();
        if (!java.util.Objects.equals(recipeId, standaloneRecipeId)) {
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
        return new TickingRequest(1, 20, standaloneRecipeId == null && !hasAutoExportWork() && !hasCraftWork(getTask()));
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode iGridNode, int ticksSinceLastCall) {
        if (level != null && (dirty || cachedTask == null || standaloneRecipeId == null)) refreshStandaloneTask(level);
        if (this.dirty) {
            this.markForUpdate();
            this.dirty = false;
        }

        var task = cachedTask;
        if (hasCraftWork(task)) {
            this.setWorking(true);
            getMainNode().ifPresent(grid -> advanceStandaloneProcessing(grid.getEnergyService()));

            if (processingTime == standaloneSteps && !dirty && hasCraftWork(task)) {
                var itemOutput = task.itemOutput();
                var fluidOutput = task.fluidOutput();
                if ((itemOutput.isEmpty() || outputInv.insertItem(0, itemOutput, false).isEmpty())
                    && (fluidOutput.isEmpty() || getOutputTank().fill(fluidOutput, IFluidHandler.FluidAction.EXECUTE)
                        == fluidOutput.getAmount())) {
                    finishStandaloneRecipe(task);
                }
            }
        } else if (isWorking()) {
            setWorking(false);
        }

        if (this.pushOutResult()) {
            return TickRateModulation.URGENT;
        }

        return hasCraftWork(getTask()) ? TickRateModulation.URGENT : hasAutoExportWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    private void finishStandaloneRecipe(IntegratedWorkingStationRecipe task) {
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
        task.inputFluid().ifPresent(ingredient -> getInputTank().drain(ingredient.amount(), IFluidHandler.FluidAction.EXECUTE));
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
                    var extracted = this.getOutputTank().drain(outFluid, IFluidHandler.FluidAction.EXECUTE).getAmount();
                    var inserted = target.insert(fluid.what(), extracted, Actionable.MODULATE, source);
                    this.getOutputTank().fill(outFluid.copyWithAmount((int) (extracted - inserted)), IFluidHandler.FluidAction.EXECUTE);

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
            getInputTank(), getOutputTank(), InternalInventoryItemHandler.of(getInternalInventory()),
            InternalInventoryItemHandler.of(upgrades), () -> (int) ((long) processingTime * MAX_PROCESSING_STEPS / Math.max(1, getMaxProcessingTime())), MAX_PROCESSING_STEPS,
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
        if (level != null && level.isClientSide()) {
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
