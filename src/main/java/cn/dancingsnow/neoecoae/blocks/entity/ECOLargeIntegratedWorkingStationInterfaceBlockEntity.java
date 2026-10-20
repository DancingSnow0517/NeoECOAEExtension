package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.AuxiliaryPatternHolder;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.PatternStorageHost;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import cn.dancingsnow.neoecoae.util.PatternSearchKeywords;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** The only machine-interface variant that exposes AE2 pattern-provider behavior. */
public final class ECOLargeIntegratedWorkingStationInterfaceBlockEntity
    extends ECOMachineInterfaceBlockEntity<NEIntegratedWorkingStationCluster>
    implements PatternProviderLogicHost, AuxiliaryPatternHolder, PatternStorageHost, IECOPatternStorage,
    IUpgradeableObject {

    private final LargeWorkstationPatternProvider workstationProvider;
    private transient boolean workstationProviderRefreshQueued;

    public ECOLargeIntegratedWorkingStationInterfaceBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        NEClusterCalculator.Factory<NEIntegratedWorkingStationCluster> calculator
    ) {
        super(type, pos, blockState, calculator);
        workstationProvider = new LargeWorkstationPatternProvider(this);
        // PatternProviderLogic configures the shared node for a normal provider.
        // Restore the multiblock flag required by this interface's cluster.
        getMainNode().setFlags(GridFlags.MULTIBLOCK, GridFlags.REQUIRE_CHANNEL)
            .addService(IECOPatternStorage.class, this);
    }

    public LargeWorkstationPatternProvider getWorkstationProvider() {
        return workstationProvider;
    }

    public int getWorkstationPatternRows() {
        return workstationProvider.getRequiredPatternRows();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return workstationProvider.getCapacityUpgrades();
    }

    @Override
    public boolean insertPattern(ItemStack itemStack) {
        return insertPatternWithResult(itemStack) == ECOPatternInsertionResult.INSERTED;
    }

    @Override
    public ECOPatternInsertionResult insertPatternWithResult(ItemStack itemStack) {
        // The provider owns the actual slots and enforces workstation compatibility.
        return ECOPatternInsertionResult.NO_TARGET;
    }

    private InternalInventory patternInventory() {
        return workstationProvider.getPatternInv();
    }

    // ---- the slot half of PatternStorageHost, forwarded to the provider that owns the slots ---------------

    @Override
    public InternalInventory getPatternSlotInventory() {
        return patternInventory();
    }

    @Override
    public int getPatternSlotCount() {
        return patternInventory().size();
    }

    @Override
    public void refreshAdvertisedPatterns() {
        // updatePatterns() invalidates the compatibility cache and re-requests the provider from AE2, which is
        // what makes a disk-only change visible to autocrafting.
        workstationProvider.updatePatterns();
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return workstationProvider.getAvailablePatterns();
    }

    /**
     * Decoded on demand: this host has no catalog-side cache to refresh, unlike the bus, whose decode is fed
     * by its own index.
     */
    @Override
    public void refreshPatternDetailsForCatalog() {
    }

    @Override
    @Nullable
    public IPatternDetails getDecodedPatternDetails(int slot) {
        var inventory = patternInventory();
        if (slot < 0 || slot >= inventory.size()) {
            return null;
        }
        ItemStack stack = inventory.getStackInSlot(slot);
        return workstationProvider.advertises(stack)
            ? PatternDetailsHelper.decodePattern(stack, getLevel())
            : null;
    }

    @Override
    public String getPatternSearchKeywords(int slot) {
        // No keyword index for this host yet; the pattern still indexes, it just will not match a search.
        return "";
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        workstationProvider.writeToNBT(data, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        workstationProvider.readFromNBT(data, registries);
        readLegacyWorkstationPatterns(data, registries);
    }

    private void readLegacyWorkstationPatterns(CompoundTag data, HolderLookup.Provider registries) {
        if (data.contains(PatternProviderLogic.NBT_MEMORY_CARD_PATTERNS, Tag.TAG_LIST)
            || !data.contains("workstationPatterns", Tag.TAG_LIST)
            || !workstationProvider.getPatternInv().isEmpty()) {
            return;
        }

        var legacy = new AppEngInternalInventory(this, LargeWorkstationPatternProvider.MAX_PATTERN_SLOTS, 1);
        legacy.readFromNBT(data, "workstationPatterns", registries);
        for (int slot = 0; slot < legacy.size(); slot++) {
            workstationProvider.getPatternInv().setItemDirect(slot, legacy.getStackInSlot(slot));
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        workstationProvider.addDrops(drops);
        for (ItemStack upgrade : getUpgrades()) {
            if (!upgrade.isEmpty()) drops.add(upgrade.copy());
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        refreshWorkstationProviderLifecycle();
    }

    @Override
    public void tick() {
        super.tick();
        if (level instanceof ServerLevel) {
            workstationProvider.updateRedstoneState();
        }
    }

    /**
     * Re-advertises once the disks' contents move.
     *
     * <p>AE2's provider list is pushed, not polled: a disk changed by something other than this host - a
     * player taking a pattern back out through a terminal, or another host writing to a disk in a shared slot
     * - would otherwise stay invisible until the network is replugged. Polling the revision is what the bus
     * does for the same reason.</p>
     */
    @Override
    protected void onInterfaceTopologyChanged() {
        refreshWorkstationProviderLifecycle();
    }

    @Override
    protected boolean handleSpecializedMainNodeStateChanged(IGridNodeListener.State reason) {
        if (workstationProvider == null) {
            return true;
        }
        workstationProvider.onMainNodeStateChanged();
        if (reason == IGridNodeListener.State.POWER || reason == IGridNodeListener.State.GRID_BOOT) {
            refreshWorkstationProviderLifecycle();
        }
        return true;
    }

    /**
     * Rebuilds the dynamic workstation provider view after the node and the multiblock have had a chance to settle.
     * The immediate refresh covers normal placement, while the deferred refresh covers world-load ordering where the
     * provider can initially be mounted before the controller or its final AE2 grid is available.
     */
    private void refreshWorkstationProviderLifecycle() {
        LargeWorkstationPatternProvider provider = workstationProvider;
        if (provider == null || !(level instanceof ServerLevel serverLevel)
            || isServerStopping() || !getMainNode().isReady()) {
            return;
        }

        provider.updatePatterns();
        if (workstationProviderRefreshQueued) {
            return;
        }

        workstationProviderRefreshQueued = true;
        serverLevel.getServer().executeIfPossible(() -> {
            workstationProviderRefreshQueued = false;
            if (!isServerStopping() && !isRemoved() && level == serverLevel
                && getMainNode().isReady() && workstationProvider == provider) {
                provider.updatePatterns();
            }
        });
    }

    /**
     * The disk-expanded view, rebuilt when the disks move.
     *
     * <p>AE2's own answer here is {@code getLogic().getPatternInv()} - the raw slots - which would show a
     * player an undecodable disk item and let them pull it out mid-craft. The disk-aware view replaces it with
     * the patterns the disks hold, and charges the network a blank pattern for each one taken back.</p>
     *
     * <p>Ordinary slot patterns remain visible ahead of the disk rows. Occupied disk slots are hidden so the
     * terminal cannot take a disk as though it were an encoded pattern.</p>
     */
    @Override
    public InternalInventory getTerminalPatternInventory() {
        return patternInventory();
    }

    /** Slot contents revision, bumped by the provider so the grid's catalog notices its patterns moved. */
    private int patternSlotRevision;

    /** Called by {@link LargeWorkstationPatternProvider} when its pattern slots change. */
    void onPatternSlotsChanged() {
        patternSlotRevision++;
    }

    /**
     * The provider's slots, not this block's own sync state.
     *
     * <p>The base implementation's revision is bumped from the pattern-bus callbacks, which never fire for a
     * workstation - leaving it constant freezes the catalog's index for this host and keeps deleted patterns
     * counted, which then reads as ALREADY_PRESENT for a pattern the network no longer holds.</p>
     */
    @Override
    public int getPatternContentRevision() {
        return patternSlotRevision + workstationProvider.getAdvertisementRevision();
    }

    @Override
    public boolean shouldIndexPattern(ItemStack pattern) {
        return workstationProvider.advertises(pattern);
    }

    @Override
    public PatternProviderLogic getLogic() {
        return workstationProvider;
    }

    @Override
    public ECOLargeIntegratedWorkingStationInterfaceBlockEntity getBlockEntity() {
        return this;
    }

    @Override
    public EnumSet<Direction> getTargets() {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public void saveChanges() {
        setChanged();
        markForUpdate();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INTERFACE.asItem());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INTERFACE.asStack();
    }

    @Override
    public void openMenu(Player player, MenuLocator locator) {
        MenuOpener.open(LargeIntegratedWorkingStationPatternProviderMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(LargeIntegratedWorkingStationPatternProviderMenu.TYPE, player, subMenu.getLocator());
    }
}
