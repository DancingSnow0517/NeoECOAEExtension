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
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.client.gui.Icon;
import appeng.core.definitions.AEItems;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.me.storage.CompositeStorage;
import appeng.parts.automation.StackWorldBehaviors;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.AEItemFilters;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.gui.AETextures;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.NETextures;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.LayoutStyle;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
import org.appliedenergistics.yoga.YogaAlign;
import org.appliedenergistics.yoga.YogaEdge;
import org.appliedenergistics.yoga.YogaFlexDirection;
import org.appliedenergistics.yoga.YogaGutter;
import org.appliedenergistics.yoga.YogaJustify;
import org.appliedenergistics.yoga.YogaPositionType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ECOIntegratedWorkingStationBlockEntity extends AENetworkedPoweredBlockEntity
    implements ISyncPersistRPCBlockEntity, IGridTickable, IUpgradeableObject, IConfigurableObject {
    private static final IGuiTexture AUTO_EXPORT_OFF = AETextures.icon(Icon.AUTO_EXPORT_OFF);
    private static final IGuiTexture AUTO_EXPORT_ON = AETextures.icon(Icon.AUTO_EXPORT_ON);

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    private static final int MAX_INPUT_SLOTS = 9;
    private static final int MAX_PROCESSING_STEPS = 200;
    private static final int MAX_POWER_STORAGE = 500000;
    private static final int MAX_TANK_CAPACITY = 16000;

    private final IUpgradeInventory upgrades;
    private final IConfigManager configManager;

    private final AppEngInternalInventory inputInv = new AppEngInternalInventory(this, MAX_INPUT_SLOTS, 64);
    private final AppEngInternalInventory outputInv = new AppEngInternalInventory(this, 1, 64);
    private final InternalInventory inv = new CombinedInternalInventory(this.inputInv, this.outputInv);

    private final FilteredInternalInventory inputExposed = new FilteredInternalInventory(this.inputInv, AEItemFilters.INSERT_ONLY);
    private final FilteredInternalInventory outputExposed = new FilteredInternalInventory(this.outputInv, AEItemFilters.EXTRACT_ONLY);
    private final InternalInventory invExposed = new CombinedInternalInventory(this.inputExposed, this.outputExposed);

    private final FluidTank inputTank = new FluidTank(MAX_TANK_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            markForUpdate();
            setChanged();
            onChangeTank();
        }
    };
    private final FluidTank outputTank = new FluidTank(MAX_TANK_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            markForUpdate();
            setChanged();
            onChangeTank();
        }
    };

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


    @Getter
    @DescSynced
    private boolean working = false;

    @Setter
    @Getter
    @DescSynced
    private int processingTime = 0;

    private boolean dirty = false;

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

        this.upgrades = UpgradeInventories.forMachine(NEBlocks.INTEGRATED_WORKING_STATION, 4, this::saveChanges);
        this.configManager = IConfigManager.builder(this::onConfigChanged)
            .registerSetting(Settings.AUTO_EXPORT, YesNo.NO)
            .build();

        this.setPowerSides(getGridConnectableSides(getOrientation()));
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
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.inputTank.readFromNBT(registries, data.getCompound("inputTank"));
        this.outputTank.readFromNBT(registries, data.getCompound("outputTank"));
        this.upgrades.readFromNBT(data, "upgrades", registries);
        this.configManager.readFromNBT(data, registries);
        shouldAutoExport = configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.put("inputTank", this.inputTank.writeToNBT(registries, new CompoundTag()));
        data.put("outputTank", this.outputTank.writeToNBT(registries, new CompoundTag()));
        upgrades.writeToNBT(data, "upgrades", registries);
        configManager.writeToNBT(data, registries);
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
        return configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES && (!this.outputInv.getStackInSlot(0).isEmpty() || !this.outputTank.getFluid().isEmpty());
    }

    private boolean hasCraftWork() {
        var task = this.getTask();
        if (task != null) {
            if (task.hasItemOutput() && outputInv.insertItem(0, task.itemOutput(), true).isEmpty()) {
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
            new IntegratedWorkingStationRecipe.Input(inputs, this.inputTank.getFluid()),
            level
        ).map(RecipeHolder::value).orElse(null);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode iGridNode) {
        return new TickingRequest(1, 20, !hasAutoExportWork() && !this.hasCraftWork());
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
                if (out != null) {
                    final ItemStack itemOut = out.itemOutput();
                    final FluidStack fluidOut = out.fluidOutput();

                    boolean itemCanInsert = true;
                    boolean fluidCanInsert = true;

                    if (!itemOut.isEmpty()) {
                        itemCanInsert = this.outputInv.insertItem(0, itemOut, true).isEmpty();
                    }

                    if (!fluidOut.isEmpty()) {
                        fluidCanInsert = this.outputTank.fill(fluidOut, IFluidHandler.FluidAction.SIMULATE) >= fluidOut.getAmount() - 0.01;
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
                            int added = this.outputTank.fill(fluidOut, IFluidHandler.FluidAction.EXECUTE);
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

                            FluidStack fluidStack = this.inputTank.getFluid();
                            if (out.inputFluid().test(fluidStack)) {
                                inputTank.drain(fluidStack.copyWithAmount(out.inputFluid().amount()), IFluidHandler.FluidAction.EXECUTE);
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
            setShowWarning(false);
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

        for (var side : Direction.values()) {
            var target = getTarget(side);

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

                FluidStack outFluid = this.outputTank.getFluid();
                GenericStack fluid = GenericStack.fromFluidStack(outFluid);
                if (fluid != null && fluid.what() != null) {
                    var extracted = this.outputTank.drain(outFluid, IFluidHandler.FluidAction.EXECUTE).getAmount();
                    var inserted = target.insert(fluid.what(), extracted, Actionable.MODULATE, source);
                    this.outputTank.fill(outFluid.copyWithAmount((int) (extracted - inserted)), IFluidHandler.FluidAction.EXECUTE);

                    if (this.outputTank.getFluidAmount() == 0) clearFluidOut();

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
    public void clearContent() {
        super.clearContent();
        this.inputTank.setFluid(FluidStack.EMPTY);
        this.outputTank.setFluid(FluidStack.EMPTY);
        this.upgrades.clear();
    }

    public void clearFluid() {
        this.inputTank.setFluid(FluidStack.EMPTY);
    }

    public void clearFluidOut() {
        this.outputTank.setFluid(FluidStack.EMPTY);
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement root = new UIElement().layout(layout -> layout
            .setPadding(YogaEdge.ALL, 4)
            .setGap(YogaGutter.ALL, 2)
            .setJustifyContent(YogaJustify.CENTER)
        ).addClass("panel_bg");

        root.addChild(new TextElement()
            .setText("block.neoecoae.integrated_working_station", true)
            .textStyle(textStyle -> textStyle.textWrap(TextWrap.HOVER_ROLL).adaptiveHeight(true).textShadow(false).textColor(0x403e53)));

        UIElement inputArea = new UIElement().layout(layout -> layout.flexDirection(YogaFlexDirection.ROW).setMargin(YogaEdge.BOTTOM, 5));
        // Input Fluid
        UIElement inputFluid = new UIElement().addClass("panel_border");
        inputFluid.addChild(new FluidSlot()
            .bind(inputTank, 0)
            .slotStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP))
            .setAllowClickDrained(true)
            .setAllowClickDrained(true)
            .layout(LayoutStyle::setHeightMaxContent));
        inputArea.addChild(inputFluid);
        // Clear button to the right of fluid slot, bottom-aligned with the fluid slot
        inputArea.addChild(new Button()
            .noText()
            .addPostIcon(AETextures.icon(Icon.CLEAR))
            .setOnServerClick(e -> clearFluid())
            .layout(layout -> layout.setWidth(8).setHeight(8).setAlignSelf(YogaAlign.FLEX_END).paddingAll(1)));


        // Input slots
        UIElement inputSlots = new UIElement().addClass("panel_border").layout(layout -> layout.setMargin(YogaEdge.LEFT, 10).setMargin(YogaEdge.RIGHT, 10));
        for (int x = 0; x < 3; x++) {
            UIElement row = new UIElement().layout(layout -> layout.setFlexDirection(YogaFlexDirection.ROW));
            for (int y = 0; y < 3; y++) {
                int i = x + y * 3;
                row.addChild(new ItemSlot(new ItemHandlerSlot((IItemHandlerModifiable) getExposedItemHandler(null), i)));
            }
            inputSlots.addChild(row);
        }
        inputArea.addChild(inputSlots);

        // output slot
        UIElement outputSlots = new UIElement().layout(layout -> {
            layout.setMargin(YogaEdge.LEFT, 5);
            layout.setFlexDirection(YogaFlexDirection.COLUMN);
            layout.setJustifyContent(YogaJustify.CENTER);
        });
        UIElement outputSlot = new UIElement().addClass("panel_border").layout(layout -> layout.setJustifyContent(YogaJustify.CENTER));
        outputSlots.addChild(outputSlot);
        outputSlot.addChild(new ItemSlot(new ItemHandlerSlot((IItemHandlerModifiable) getExposedItemHandler(null), 9)));
        inputArea.addChild(outputSlots);

        // progress bar
        UIElement progressBar = new UIElement().layout(layout -> {
            layout.setMargin(YogaEdge.LEFT, 2);
            layout.setMargin(YogaEdge.RIGHT, 5);
            layout.setFlexDirection(YogaFlexDirection.COLUMN);
            layout.setJustifyContent(YogaJustify.CENTER);
        }).addChildren(
            new ProgressBar()
                .bindDataSource(SupplierDataSource.of(() -> (float) processingTime))
                .setMaxValue(MAX_PROCESSING_STEPS)
                .progressBarStyle(style -> style.fillDirection(FillDirection.UP_TO_DOWN).interpolate(false))
                .barContainer(element -> element.layout(layout -> layout.paddingAll(1)))
                .label(label -> label.setText(""))
                .layout(layout -> layout.setHeight(18).setWidth(6))
                .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                    event.hoverTooltips = new HoverTooltips(
                        List.of(Component.literal("%.0f%%".formatted((float) processingTime / MAX_PROCESSING_STEPS * 100))),
                        null,
                        null,
                        null
                    );
                })
        );

        inputArea.addChild(progressBar);

        // clear button on the left of output slot, bottom-aligned
        inputArea.addChild(new Button()
            .noText()
            .addPostIcon(AETextures.icon(Icon.CLEAR))
            .setOnServerClick(e -> clearFluidOut())
            .layout(layout -> layout.setWidth(8).setHeight(8).setAlignSelf(YogaAlign.FLEX_END).paddingAll(1)));
        // output fluid
        UIElement outputFluid = new UIElement().addClass("panel_border");
        outputFluid.addChild(new FluidSlot()
            .bind(outputTank, 0)
            .slotStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP))
            .setAllowClickFilled(true)
            .setAllowClickDrained(false)
            .layout(LayoutStyle::setHeightMaxContent));
        inputArea.addChild(outputFluid);


        // add main input area and upgrades panel side-by-side
        root.addChild(inputArea);

        // Upgrades panel on the right (凸出式)
        UIElement upgradesPanel = new UIElement().layout(layout -> {
            layout.positionType(YogaPositionType.ABSOLUTE);
            layout.setPosition(YogaEdge.RIGHT, -22);
            layout.setPosition(YogaEdge.TOP, 0);
            layout.setPadding(YogaEdge.ALL, 2);
            layout.setPadding(YogaEdge.BOTTOM, 4);
        }).style(style -> style.background(NETextures.BACKGROUND));
        // add four upgrade slots vertically
        for (int i = 0; i < 4; i++) {
            upgradesPanel.addChild(new ItemSlot(new ItemHandlerSlot((IItemHandlerModifiable) this.upgrades.toItemHandler(), i))
                .slotStyle(style -> style.slotOverlay(AETextures.icon(Icon.BACKGROUND_UPGRADE)))
                .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                    List<Component> tooltips = new ArrayList<>();
                    tooltips.add(GuiText.CompatibleUpgrades.text());
                    tooltips.addAll(Upgrades.getTooltipLinesForMachine(NEBlocks.INTEGRATED_WORKING_STATION));
                    event.hoverTooltips = new HoverTooltips(tooltips, null, null, null);
                }));
        }

        root.addChild(upgradesPanel);

        UIElement settingsPanel = new UIElement().layout(layout -> {
            layout.positionType(YogaPositionType.ABSOLUTE);
            layout.setPosition(YogaEdge.LEFT, -22);
            layout.setPosition(YogaEdge.TOP, 0);
            layout.setPadding(YogaEdge.ALL, 2);
            layout.setPadding(YogaEdge.BOTTOM, 4);
        }).style(style -> style.background(NETextures.BACKGROUND));

        settingsPanel.addChild(new Button()
            .noText()
            .addPostIcon(AETextures.icon(Icon.HELP))
            .setOnServerClick(e -> {
            })
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                event.hoverTooltips = new HoverTooltips(
                    List.of(ButtonToolTips.OpenGuide.text().withColor(-1), ButtonToolTips.OpenGuideDetail.text().withStyle(ChatFormatting.GRAY)),
                    null,
                    null,
                    null
                );
            })
            .layout(style -> style.setHeight(20).setWidth(18)));

        settingsPanel.addChild(new Toggle()
            .noText()
            .toggleStyle(style -> style.markTexture(AUTO_EXPORT_ON).unmarkTexture(AUTO_EXPORT_OFF))
            .toggleButton(button -> button.setOnServerClick(e -> {
                shouldAutoExport = !shouldAutoExport;
                configManager.putSetting(Settings.AUTO_EXPORT, shouldAutoExport ? YesNo.YES : YesNo.NO);
            }).layout(layout -> layout.setHeight(20).setWidth(18)))
            .bindDataSource(SupplierDataSource.of(() -> shouldAutoExport))
            .addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
                event.hoverTooltips = new HoverTooltips(
                    List.of(
                        ButtonToolTips.AutoExport.text().withColor(-1),
                        (shouldAutoExport ? ButtonToolTips.AutoExportOn : ButtonToolTips.AutoExportOff).text().withStyle(ChatFormatting.GRAY)
                    ),
                    null,
                    null,
                    null
                );
            })
            .layout(layout -> layout.setWidth(18).setHeight(22).paddingAll(0)));

        root.addChild(settingsPanel);

        root.addChild(new TextElement()
            .setText("container.inventory", true)
            .textStyle(textStyle -> textStyle.textWrap(TextWrap.HOVER_ROLL).adaptiveHeight(true).textShadow(false).textColor(0x403e53)));

        root.addChild(new InventorySlots().layout(layout -> layout.setMargin(YogaEdge.TOP, 2)));
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }
}