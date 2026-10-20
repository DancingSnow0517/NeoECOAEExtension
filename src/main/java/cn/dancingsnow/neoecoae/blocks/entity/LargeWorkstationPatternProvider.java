package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.inventories.BaseInternalInventory;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import appeng.util.inv.filter.IAEItemFilter;
import appeng.util.inv.AppEngInternalInventory;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.compat.extendedaeplus.ECOExtendedAEPlusBlocking;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import cn.dancingsnow.neoecoae.integration.ae2pattern.PatternDiskSupport;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The large workstation's pattern provider with capacity-card upgrades and scrollable pattern storage.
 *
 * <p>The normal provider implementation is reused for pattern storage, return
 * inventory, settings and persistence. Only dispatch is specialized: a
 * workstation pattern is handed to the formed workstation controller instead
 * of being pushed into an adjacent inventory.</p>
 */
public final class LargeWorkstationPatternProvider extends PatternProviderLogic
    implements ECOParallelCraftingProvider, IUpgradeableObject {
    public static final int PATTERN_COLUMNS = 9;
    public static final int BASE_PATTERN_ROWS = 4;
    public static final int MAX_CAPACITY_CARDS = 4;
    public static final int UPGRADE_SLOTS = MAX_CAPACITY_CARDS + 1;
    public static final int MAX_PATTERN_SLOTS = (BASE_PATTERN_ROWS + MAX_CAPACITY_CARDS) * PATTERN_COLUMNS;

    private final ECOLargeIntegratedWorkingStationInterfaceBlockEntity host;
    private final IUpgradeInventory capacityUpgrades;
    private final InternalInventory visiblePatternInventory = new EffectivePatternInventory();
    private List<IPatternDetails> compatiblePatterns = List.of();
    private Set<AEItemKey> advertisedKeys = Set.of();
    private int advertisementRevision;
    private boolean compatiblePatternsDirty = true;
    private Object lastRecipes;
    @Nullable
    private ECOLargeIntegratedWorkingStationBlockEntity lastController;
    /** Disk contents the cached list was built from; a change invalidates it the way a recipe change does. */
    private long lastDiskRevision = Long.MIN_VALUE;

    public LargeWorkstationPatternProvider(ECOLargeIntegratedWorkingStationInterfaceBlockEntity host) {
        super(host.getMainNode(), host, MAX_PATTERN_SLOTS);
        this.host = host;
        capacityUpgrades = UpgradeInventories.forMachine(host.getBlockState().getBlock(), UPGRADE_SLOTS,
            this::onCapacityUpgradesChanged);
        if (capacityUpgrades instanceof AppEngInternalInventory inventory) {
            inventory.setFilter(new IAEItemFilter() {
                @Override
                public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                    return capacityUpgrades.getInstalledUpgrades(stack.getItem())
                        < capacityUpgrades.getMaxInstalled(stack.getItem());
                }

                @Override
                public boolean allowExtract(InternalInventory inv, int slot, int amount) {
                    return !AEItems.CAPACITY_CARD.is(inv.getStackInSlot(slot)) || canRemoveCapacityCard();
                }
            });
        }
    }

    @Override
    public InternalInventory getPatternInv() {
        return visiblePatternInventory;
    }

    public InternalInventory getMenuPatternInventory() {
        return super.getPatternInv();
    }

    public IUpgradeInventory getCapacityUpgrades() {
        return capacityUpgrades;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return capacityUpgrades;
    }

    public int getUnlockedPatternRows() {
        return BASE_PATTERN_ROWS + Math.clamp(capacityUpgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD),
            0, MAX_CAPACITY_CARDS);
    }

    public boolean canInsertPattern(int slot) {
        return slot >= 0 && slot < getUnlockedPatternRows() * PATTERN_COLUMNS;
    }

    public boolean canRemoveCapacityCard() {
        int rowsAfterRemoval = Math.max(BASE_PATTERN_ROWS, getUnlockedPatternRows() - 1);
        for (int slot = rowsAfterRemoval * PATTERN_COLUMNS; slot < MAX_PATTERN_SLOTS; slot++) {
            if (!super.getPatternInv().getStackInSlot(slot).isEmpty()) return false;
        }
        return true;
    }

    private void onCapacityUpgradesChanged() {
        host.saveChanges();
        host.onPatternSlotsChanged();
        host.markForUpdate();
        updatePatterns();
    }

    int getRequiredPatternRows() {
        InternalInventory inventory = super.getPatternInv();
        for (int slot = inventory.size() - 1; slot >= 0; slot--) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return Math.max(getUnlockedPatternRows(), slot / PATTERN_COLUMNS + 1);
            }
        }
        return getUnlockedPatternRows();
    }

    private final class EffectivePatternInventory extends BaseInternalInventory {
        @Override
        public int size() {
            return getRequiredPatternRows() * PATTERN_COLUMNS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return getMenuPatternInventory().getStackInSlot(slot);
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            getMenuPatternInventory().setItemDirect(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return getMenuPatternInventory().getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canInsertPattern(slot) && getMenuPatternInventory().isItemValid(slot, stack);
        }

        @Override
        public void sendChangeNotification(int slot) {
            getMenuPatternInventory().sendChangeNotification(slot);
        }
    }

    /**
     * @return the interface these slots belong to. Its menu needs it to decide what a pattern slot takes: the
     *         host is what knows whether a stack is one of its pattern disks.
     */
    public ECOLargeIntegratedWorkingStationInterfaceBlockEntity getWorkstationInterface() {
        return host;
    }

    public int getAdvertisementRevision() {
        getAvailablePatterns();
        return advertisementRevision;
    }

    public boolean advertises(net.minecraft.world.item.ItemStack stack) {
        getAvailablePatterns();
        AEItemKey key = AEItemKey.of(stack);
        return key != null && advertisedKeys.contains(key);
    }

    public boolean accepts(net.minecraft.world.item.ItemStack stack) {
        var controller = controller();
        if (controller == null || host.getLevel() == null) {
            return false;
        }
        IPatternDetails pattern = PatternDetailsHelper.decodePattern(stack, host.getLevel());
        return pattern != null && controller.acceptPattern(pattern, null, false);
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
        capacityUpgrades.readFromNBT(tag, "workstationCapacityUpgrades", registries);
        disableSmartDoubling();
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        capacityUpgrades.writeToNBT(tag, "workstationCapacityUpgrades", registries);
    }

    @Nullable
    private ECOLargeIntegratedWorkingStationBlockEntity controller() {
        return host.getCluster() instanceof NEIntegratedWorkingStationCluster cluster ? cluster.getController() : null;
    }

    @Override
    public void updateRedstoneState() {
        LockCraftingMode before = getCraftingLockedReason();
        super.updateRedstoneState();
        if (before != getCraftingLockedReason()) {
            requestProviderUpdate();
        }
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        super.onChangeInventory(inv, slot);
        // The host is the storage the grid indexes, but its patterns live in this logic's inventory. Without
        // this the catalog's revision for the host never moves, so a slot cleared here stays counted forever.
        host.onPatternSlotsChanged();
    }

    @Override
    public void updatePatterns() {
        disableSmartDoubling();
        compatiblePatternsDirty = true;
        // PatternProviderLogic.requestUpdate() refreshes the AE2 provider synchronously. Invalidate the
        // workstation-specific compatibility cache before delegating, otherwise the refresh can remount the
        // previous filtered list and leave the newly decoded patterns invisible until the network is replugged.
        super.updatePatterns();
    }

    private void disableSmartDoubling() {
        var manager = getConfigManager();
        for (var setting : manager.getSettings()) {
            if ("smart_doubling".equals(setting.getName())) {
                @SuppressWarnings("unchecked")
                var toggle = (appeng.api.config.Setting<appeng.api.config.YesNo>) setting;
                if (manager.getSetting(toggle) != appeng.api.config.YesNo.NO)
                    manager.putSetting(toggle, appeng.api.config.YesNo.NO);
            }
        }
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        var controller = controller();
        if (host.getLevel() == null || controller == null) {
            if (lastController != null || !compatiblePatterns.isEmpty()) {
                advertisementRevision++;
            }
            lastController = null;
            compatiblePatterns = List.of();
            advertisedKeys = Set.of();
            return List.of();
        }
        Object recipes = LargeWorkstationRecipes.getAvailable(host.getLevel(), controller.getInstalledModule());
        long diskRevision = host.diskRevision();
        if (!compatiblePatternsDirty && lastController == controller && lastRecipes == recipes
            && lastDiskRevision == diskRevision) {
            return compatiblePatterns;
        }
        List<IPatternDetails> result = new ArrayList<>();
        for (var pattern : super.getAvailablePatterns()) {
            if (controller.acceptPattern(pattern, null, false)) result.add(pattern);
        }
        // Patterns on a disk go through the same compatibility gate as the ones in slots: a recipe this
        // workstation would refuse should not be advertised just because it arrived on a disk.
        for (var pattern : PatternDiskSupport.decodeAuxiliary(host.getWorkstationProvider().getPatternInv(),
            host.getLevel())) {
            if (controller.acceptPattern(pattern, null, false)) result.add(pattern);
        }
        boolean sourceChanged = lastController != controller || lastRecipes != recipes
            || lastDiskRevision != diskRevision;
        compatiblePatterns = result;
        Set<AEItemKey> keys = new HashSet<>();
        for (IPatternDetails pattern : result) {
            keys.add(pattern.getDefinition());
        }
        advertisedKeys = keys;
        if (sourceChanged) {
            advertisementRevision++;
        }
        compatiblePatternsDirty = false;
        lastController = controller;
        lastRecipes = recipes;
        lastDiskRevision = diskRevision;
        return compatiblePatterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        var controller = controller();
        if (!canDispatch(controller, pattern)) {
            return false;
        }
        boolean accepted = controller.acceptPattern(pattern, inputs, true);
        if (accepted) {
            onPatternAccepted(pattern);
        }
        return accepted;
    }

    /** One ordinary dispatch transfers one batch up to the current coolant-supported limit. */
    @Override
    public int eco$getAvailableParallelSlots() {
        var controller = controller();
        return controller == null || !host.getMainNode().isActive() || !controller.canAcceptPattern()
                || isCraftingLocked()
                || getAvailablePatterns().isEmpty()
            ? 0 : controller.getMaxBatchParallelism();
    }

    @Override
    public boolean eco$pushPatternBatch(
        IPatternDetails pattern,
        KeyCounter[] inputTotal,
        long craftCount,
        @Nullable UUID craftingJobId
    ) {
        var controller = controller();
        if (!canDispatch(controller, pattern)) {
            return false;
        }
        boolean accepted = controller.acceptPatternBatch(pattern, inputTotal, craftCount, craftingJobId);
        if (accepted) {
            onPatternAccepted(pattern);
        }
        return accepted;
    }

    @Override
    public boolean isBusy() {
        var controller = controller();
        return controller == null || !controller.canAcceptPattern() || isCraftingLocked();
    }

    /** Called by the controller after this provider's primary result reached its final destination. */
    public void onPatternResult(@Nullable GenericStack result) {
        if (result == null) {
            return;
        }
        GenericStack unlockStack = getUnlockStack();
        if (unlockStack == null || !unlockStack.what().equals(result.what())
                || result.amount() < unlockStack.amount()) {
            return;
        }
        resetCraftingLock();
        requestProviderUpdate();
    }

    /** Releases a result lock when the owning CPU job was cancelled before producing a result. */
    public void onPatternAborted(@Nullable GenericStack expectedResult) {
        if (expectedResult == null) {
            return;
        }
        GenericStack unlockStack = getUnlockStack();
        if (unlockStack != null && unlockStack.what().equals(expectedResult.what())
                && unlockStack.amount() == expectedResult.amount()) {
            resetCraftingLock();
            requestProviderUpdate();
        }
    }

    private boolean canDispatch(
        @Nullable ECOLargeIntegratedWorkingStationBlockEntity controller,
        IPatternDetails pattern
    ) {
        if (controller == null || !host.getMainNode().isActive() || !getAvailablePatterns().contains(pattern)
                || isCraftingLocked()) {
            return false;
        }
        if (!isBlocking()) {
            return true;
        }
        // Dispatch bypasses EAEP's injection into the base pushPattern method. Apply the same
        // input-presence rule to the controller's pending ledger for both single and batch pushes.
        if (ECOExtendedAEPlusBlocking.isEnabled(getConfigManager())
                && ECOExtendedAEPlusBlocking.matchesPendingInputs(pattern, controller::containsPendingPatternInput)) {
            return true;
        }
        return !controller.containsPendingPatternInput(patternInputKeys(pattern));
    }

    private boolean isCraftingLocked() {
        return getCraftingLockedReason() != LockCraftingMode.NONE;
    }

    private static Set<AEKey> patternInputKeys(IPatternDetails pattern) {
        Set<AEKey> result = new HashSet<>();
        IPatternDetails.IInput[] inputs = pattern.getInputs();
        if (inputs == null) {
            return result;
        }
        for (IPatternDetails.IInput input : inputs) {
            if (input == null || input.getPossibleInputs() == null) {
                continue;
            }
            for (GenericStack candidate : input.getPossibleInputs()) {
                if (candidate != null && candidate.what() != null) {
                    result.add(candidate.what().dropSecondary());
                }
            }
        }
        return result;
    }

    private void onPatternAccepted(IPatternDetails pattern) {
        // The specialized provider bypasses PatternProviderLogic.pushPattern, so explicitly retain AE2's
        // lock-until-pulse/result state transition after the controller accepted the batch.
        ((PatternProviderLogicAccessor) (Object) this).neoecoae$onPushPatternSuccess(pattern);
        // Do not refresh the crafting-provider registry from inside pushPattern. AE2 may be iterating the
        // provider list while dispatching this pattern; refreshing synchronously removes and re-adds this
        // provider and invalidates that iterator when another provider advertises the same pattern.
    }

    private void requestProviderUpdate() {
        ICraftingProvider.requestUpdate(host.getMainNode());
    }
}
