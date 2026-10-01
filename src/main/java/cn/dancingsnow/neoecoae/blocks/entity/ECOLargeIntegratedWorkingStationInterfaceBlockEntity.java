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
import appeng.menu.locator.MenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.AuxiliaryPatternHolder;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.PatternStorageHost;
import cn.dancingsnow.neoecoae.integration.ae2pattern.PatternDiskSupport;
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
    implements PatternProviderLogicHost, AuxiliaryPatternHolder, PatternStorageHost, IECOPatternStorage {

    private final LargeWorkstationPatternProvider workstationProvider;
    private transient boolean workstationProviderRefreshQueued;
    /** The disk revision last handed to AE2; {@link #refreshDiskAdvertisement} polls against it. */
    private long advertisedDiskRevision = Long.MIN_VALUE;

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

    // ---- pattern disks (AE2 Pattern Disk, optional) -------------------------------------------------

    /**
     * The disk holder over this host's pattern slots, built on first use.
     *
     * <p>Not a field initialiser: the slots it reads belong to {@code workstationProvider}, which the
     * constructor is what assigns. Asking before that would hold a holder over nothing.</p>
     *
     * <p>Returned methods all go through {@link PatternDiskSupport}, which answers "no disks" when the mod
     * that provides them is absent or has not installed its backend yet.</p>
     */
    private AuxiliaryPatternHolder diskHolder;

    private AuxiliaryPatternHolder diskHolder() {
        AuxiliaryPatternHolder current = diskHolder;
        if (current == null && PatternDiskSupport.available()) {
            current = PatternDiskSupport.holderFor(
                workstationProvider.getPatternInv(),
                () -> getMainNode().getGrid(),
                this::getLevel);
            diskHolder = current;
        }
        return current;
    }

    @Override
    public boolean ownsAuxiliary(ItemStack stack) {
        AuxiliaryPatternHolder holder = diskHolder();
        return holder != null && holder.ownsAuxiliary(stack);
    }

    @Override
    public boolean hasAuxiliaryRoom() {
        AuxiliaryPatternHolder holder = diskHolder();
        return holder != null && holder.hasAuxiliaryRoom();
    }

    @Override
    public List<ItemStack> getAuxiliaryEncodedPatterns() {
        AuxiliaryPatternHolder holder = diskHolder();
        if (holder == null) {
            return List.of();
        }
        List<ItemStack> advertised = new ArrayList<>();
        for (ItemStack pattern : holder.getAuxiliaryEncodedPatterns()) {
            if (workstationProvider.advertises(pattern)) {
                advertised.add(pattern);
            }
        }
        return advertised;
    }

    @Override
    public long getAuxiliaryRevision() {
        long diskRevision = diskRevision();
        int advertisementRevision = workstationProvider.getAdvertisementRevision();
        if (lastAuxiliaryDiskRevision != diskRevision || lastAuxiliaryAdvertisementRevision != advertisementRevision) {
            lastAuxiliaryDiskRevision = diskRevision;
            lastAuxiliaryAdvertisementRevision = advertisementRevision;
            auxiliaryRevision++;
        }
        return auxiliaryRevision;
    }

    private long lastAuxiliaryDiskRevision = Long.MIN_VALUE;
    private int lastAuxiliaryAdvertisementRevision = Integer.MIN_VALUE;
    private long auxiliaryRevision;

    long diskRevision() {
        AuxiliaryPatternHolder holder = diskHolder();
        return holder == null ? 0L : holder.getAuxiliaryRevision();
    }

    @Override
    public List<String> getAuxiliarySearchKeywords() {
        AuxiliaryPatternHolder holder = diskHolder();
        if (holder == null) {
            return List.of();
        }
        // Answered for contract completeness: this machine has no pattern preview rows, so nothing reads this
        // list today. The rows it would serve belong to the crafting interface, which is not what this
        // working-station interface is - its disks are reached through the disk terminals instead, and those
        // find a recipe by the terminal's own filter rather than by these keywords. Decoded on demand rather
        // than cached, because a cache here would need its own revision for no current caller.
        List<ItemStack> patterns = getAuxiliaryEncodedPatterns();
        List<String> keywords = new ArrayList<>(patterns.size());
        for (ItemStack pattern : patterns) {
            keywords.add(PatternSearchKeywords.build(pattern, PatternDetailsHelper.decodePattern(pattern, getLevel())));
        }
        return keywords;
    }

    // ---- routing patterns into disks (IECOPatternStorage) ------------------------------------------

    @Override
    public boolean canAcceptIntoAuxiliary(ItemStack pattern) {
        return workstationProvider.accepts(pattern)
            && PatternDiskSupport.canAcceptAuxiliary(patternInventory(), pattern, getLevel());
    }

    @Override
    public ECOPatternInsertionResult insertIntoAuxiliary(ItemStack pattern, @Nullable ECOPreparedPattern prepared) {
        if (!workstationProvider.accepts(pattern)) {
            return ECOPatternInsertionResult.INCOMPATIBLE;
        }
        boolean inserted = PatternDiskSupport.insertAuxiliary(patternInventory(), pattern, getLevel());
        return inserted ? ECOPatternInsertionResult.INSERTED : ECOPatternInsertionResult.NO_TARGET;
    }

    @Override
    public boolean insertPattern(ItemStack itemStack) {
        return insertPatternWithResult(itemStack) == ECOPatternInsertionResult.INSERTED;
    }

    @Override
    public ECOPatternInsertionResult insertPatternWithResult(ItemStack itemStack) {
        // A disk is the only place this host stores patterns itself; its slots belong to the provider, which
        // the catalog falls back to on its own. NO_TARGET is what sends the pattern there.
        //
        // That split is also why this host reports slot capacity it never writes to: it implements
        // IECOPatternStorage, so the catalog counts it as a slot storage, yet a pattern handed to it only ever
        // lands on a disk. Accepted rather than tightened - refusing the pattern here would make a machine
        // with empty slots look full, and writing the slots from here would bypass the provider's own filter.
        return insertIntoAuxiliary(itemStack, null);
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
        return LargeWorkstationPatternProvider.PATTERN_SLOT_COUNT;
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

        var legacy = new AppEngInternalInventory(this, workstationProvider.getPatternInv().size(), 1);
        legacy.readFromNBT(data, "workstationPatterns", registries);
        for (int slot = 0; slot < legacy.size(); slot++) {
            workstationProvider.getPatternInv().setItemDirect(slot, legacy.getStackInSlot(slot));
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        workstationProvider.addDrops(drops);
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
            refreshDiskAdvertisement();
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
    private void refreshDiskAdvertisement() {
        long revision = diskRevision();
        if (advertisedDiskRevision == revision) {
            return;
        }
        advertisedDiskRevision = revision;
        refreshWorkstationProviderLifecycle();
    }

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
    @Nullable
    private PatternDiskSupport.TerminalView diskTerminalView;
    private long diskTerminalViewRevision = Long.MIN_VALUE;
    private InternalInventory terminalPatternInventory;
    private long terminalPatternInventoryRevision = Long.MIN_VALUE;

    @Override
    public InternalInventory getTerminalPatternInventory() {
        PatternDiskSupport.TerminalView view = diskTerminalView();
        if (view == null) {
            return PatternProviderLogicHost.super.getTerminalPatternInventory();
        }
        long revision = diskRevision();
        if (terminalPatternInventory == null || terminalPatternInventoryRevision != revision) {
            terminalPatternInventory = view.withHostRows(patternInventory());
            terminalPatternInventoryRevision = revision;
        }
        return terminalPatternInventory;
    }

    /**
     * @return the disk-aware view, or {@code null} on the client and when AEPD is absent - in both cases the
     *         host shows AE2's plain slots, which is what it did before this integration existed
     */
    @Nullable
    private PatternDiskSupport.TerminalView diskTerminalView() {
        if (!(level instanceof ServerLevel)) {
            // A grid is a server concept, and the view charges it; the client has nothing to attach to.
            return null;
        }
        long revision = diskRevision();
        PatternDiskSupport.TerminalView current = diskTerminalView;
        if (current != null) {
            if (diskTerminalViewRevision == revision) {
                return current;
            }
            // The layout is cached on the view's side, so a change has to be pushed into it.
            current.invalidate();
            diskTerminalViewRevision = revision;
            return current;
        }
        current = PatternDiskSupport.terminalView(
            patternInventory(),
            () -> getMainNode().getGrid(),
            this,
            this::saveChanges,
            this::getLevel);
        if (current != null) {
            diskTerminalView = current;
            diskTerminalViewRevision = revision;
        }
        return current;
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
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(LargeIntegratedWorkingStationPatternProviderMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(LargeIntegratedWorkingStationPatternProviderMenu.TYPE, player, subMenu.getLocator());
    }
}
