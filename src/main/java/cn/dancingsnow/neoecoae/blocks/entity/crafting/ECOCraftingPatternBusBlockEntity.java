package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.BaseInternalInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathLookup;
import cn.dancingsnow.neoecoae.impl.crafting.planner.growth.NetGrowthPatternValidationRegistry;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedFastPathExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedFastPathRecipe;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedVirtualExecution;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.util.ServerTaskUtil;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.IntStream;

public class ECOCraftingPatternBusBlockEntity extends cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster, ECOCraftingPatternBusBlockEntity>
    implements ISyncPersistRPCBlockEntity, InternalInventoryHost, ICraftingProvider, PatternContainer, IECOPatternStorage {

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    public static final int ROW_SIZE = 9;
    public static final int COL_SIZE = 7;
    private static final int PAGE_BUTTON_SIZE = 16;
    private static final int PAGE_CONTROL_GAP = 4;
    private static final int HEADER_HEIGHT = 36;
    private static final int SINGLE_PAGE_HEADER_HEIGHT = 16;
    private static final int HEADER_TITLE_TOP = 2;
    private static final int PAGE_TOP_MARGIN = 19;
    private static final int PAGE_RIGHT_MARGIN = 2;
    private static final int PAGE_CONTROLS_OFFSET_X = 1;
    private static final int PAGE_LABEL_WIDTH = 16;
    private static final int UI_CONTENT_WIDTH = ROW_SIZE * 18;
    private static final int PAGE_CONTROLS_WIDTH = PAGE_BUTTON_SIZE * 2 + PAGE_CONTROL_GAP * 2 + PAGE_LABEL_WIDTH;
    private static final int PATTERN_UPDATE_QUIET_TICKS = 2;
    public static final int SLOTS_PER_PAGE = ROW_SIZE * COL_SIZE;

    @Persisted
    @DescSynced
    private final AppEngInternalInventory inventory;
    private final InternalInventory effectiveInventory = new EffectivePatternInventory();
    private final IItemHandlerModifiable pageItemHandler = new PagedPatternItemHandler();
    private final List<IPatternDetails> patternDetails = new ArrayList<>();
    private final IPatternDetails[] decodedPatternDetails =
        new IPatternDetails[NEConfig.getMaxCraftingPatternBusSlotCount()];
    private final BitSet dirtyPatternSlots = new BitSet(NEConfig.getMaxCraftingPatternBusSlotCount());
    public final IItemHandlerModifiable itemHandler;
    @Persisted
    @DescSynced
    private int activePages = NEConfig.getCraftingPatternBusPages();
    @DescSynced
    private int currentPage;
    private boolean patternDetailsUpdateQueued;
    private boolean rebuildAllPatternDetails = true;
    private int patternDetailsUpdateTick;
    private final String[] patternSearchKeywords = new String[NEConfig.getMaxCraftingPatternBusSlotCount()];
    private final BitSet emptyPatternSlots = new BitSet(NEConfig.getMaxCraftingPatternBusSlotCount());
    private int patternCapacitySlotCount;
    private int patternCapacityGeneration;
    private boolean patternCapacityIndexInitialized;
    @DescSynced
    private int patternContentRevision;
    private transient boolean craftingProviderRefreshQueued;
    /** The prepared pattern currently being inserted; used to avoid decoding it again in the slot filter. */
    private transient ECOPreparedPattern activePreparedPattern;

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return patternDetails;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return pushPattern(patternDetails, inputHolder, null);
    }

    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, @Nullable UUID craftingJobId) {
        return pushPattern(ECOExtractedPatternExecution.slow(patternDetails, inputHolder), craftingJobId);
    }

    public boolean pushPattern(ECOExtractedPatternExecution execution, @Nullable UUID craftingJobId) {
        if (execution.molecularPattern() == null || cluster == null) {
            return false;
        }
        // Deterministic, concentrating order over every worker this host can reach - the whole Network Switch
        // group when one is formed. No rotation and no randomness, so the same network state always picks the
        // same worker.
        for (RankedWorker ranked : rankDispatchCandidates()) {
            if (ranked.worker().pushPattern(execution, craftingJobId)) {
                return true;
            }
        }
        return false;
    }

    public boolean pushBatch(ECOVerifiedFastPathExecution verified, @Nullable BatchFastPathOffer offer) {
        if (offer == null || cluster == null) {
            return false;
        }
        // The credential must be the one minted for this offer. That single reference check replaces the value
        // comparison of three per-craft stack lists, and it also pins the batch size the offer was sized for.
        if (verified.recipe() != offer.recipe()) {
            return false;
        }
        int batchSize = verified.batchSize();
        ECOCraftingWorkerBlockEntity worker = offer.worker();
        if (offer.maxBatchSize() < batchSize
            || !cluster.isDispatchCandidate(worker)
            || worker.getAvailableThreadSlots() < batchSize
            || getAvailableThreadSlots() < batchSize) {
            return false;
        }
        return worker.pushBatch(verified);
    }

    public boolean pushVirtualBatch(ECOVerifiedVirtualExecution verified, @Nullable VirtualFastPathOffer offer) {
        if (offer == null || cluster == null || verified.recipe() != offer.recipe()) {
            return false;
        }
        ECOCraftingWorkerBlockEntity worker = offer.worker();
        return cluster.isDispatchCandidate(worker)
            && worker.getAvailableBatchCapacity() > 0
            && worker.pushVirtualBatch(verified);
    }

    @Nullable
    public VirtualFastPathOffer findVirtualFastPathOffer(ECOExtractedPatternExecution execution) {
        ECOCraftingSystemBlockEntity controller = getCraftingController();
        if (cluster == null || controller == null || !controller.getCapabilitySnapshot().virtualMode()) {
            return null;
        }
        ECOFastPathLookup lookup = cluster.getFastPathCache().lookup(
            execution,
            appeng.hooks.ticking.TickHandler.instance().getCurrentTick(),
            AE2PatternIntrospection.reloadGeneration()
        );
        if (!lookup.isVerified()) {
            return null;
        }
        List<RankedWorker> ranked = rankDispatchCandidates();
        return ranked.isEmpty() ? null : new VirtualFastPathOffer(ranked.getFirst().worker(), lookup.recipe());
    }

    @Nullable
    public BatchFastPathOffer findBatchFastPathOffer(ECOExtractedPatternExecution execution, int requestedBatchSize) {
        if (cluster == null || requestedBatchSize <= 0) {
            return null;
        }
        int globalAvailableSlots = getAvailableThreadSlots();
        if (globalAvailableSlots <= 0) {
            return null;
        }
        // Recipe-level verification is shared knowledge, so it is resolved once for the whole search instead of
        // once per candidate worker.
        ECOFastPathLookup lookup = cluster.getFastPathCache().lookup(
            execution,
            appeng.hooks.ticking.TickHandler.instance().getCurrentTick(),
            AE2PatternIntrospection.reloadGeneration()
        );
        if (!lookup.isVerified()) {
            return null;
        }
        List<RankedWorker> ranked = rankDispatchCandidates();
        if (ranked.isEmpty()) {
            return null;
        }
        // calculateBatchOfferSize is monotone in the worker's free slots, so the highest-ranked candidate also
        // has the largest offer. Taking it keeps a batch concentrated on one worker instead of splitting it.
        RankedWorker best = ranked.getFirst();
        int maxBatchSize = calculateBatchOfferSize(requestedBatchSize, best.availableSlots(), globalAvailableSlots);
        if (maxBatchSize <= 0) {
            return null;
        }
        return new BatchFastPathOffer(best.worker(), lookup.recipe(), maxBatchSize);
    }

    /**
     * Live snapshot of every reachable worker with free capacity, ordered by free thread slots descending and
     * then by block position ascending.
     *
     * <p>The topology is re-collected here and the capacity is re-measured here: candidate membership may be
     * reused within one dispatch, but "is this worker available right now" must never be cached, and no
     * reference to a removed or rebuilt worker is ever retained.
     */
    private List<RankedWorker> rankDispatchCandidates() {
        List<ECOCraftingWorkerBlockEntity> candidates = cluster.collectDispatchCandidateWorkers();
        List<RankedWorker> ranked = new ArrayList<>(candidates.size());
        for (ECOCraftingWorkerBlockEntity worker : candidates) {
            int availableSlots = worker.getAvailableThreadSlots();
            if (availableSlots > 0) {
                ranked.add(new RankedWorker(worker, availableSlots));
            }
        }
        ranked.sort(DISPATCH_ORDER);
        return ranked;
    }

    private record RankedWorker(ECOCraftingWorkerBlockEntity worker, int availableSlots) {}

    private static final java.util.Comparator<RankedWorker> DISPATCH_ORDER = (left, right) -> {
        int bySlots = Integer.compare(right.availableSlots(), left.availableSlots());
        return bySlots != 0 ? bySlots : comparePositions(left.worker().getBlockPos(), right.worker().getBlockPos());
    };

    /** Stable, predictable tie-break: no randomness, no rotation, no dependence on structure scan order. */
    static int comparePositions(BlockPos left, BlockPos right) {
        int byX = Integer.compare(left.getX(), right.getX());
        if (byX != 0) {
            return byX;
        }
        int byY = Integer.compare(left.getY(), right.getY());
        return byY != 0 ? byY : Integer.compare(left.getZ(), right.getZ());
    }

    public boolean recoverJobToNetwork(UUID craftingJobId, appeng.api.storage.MEStorage storage) {
        if (cluster == null) {
            return false;
        }
        // Dispatch may have crossed a Network Switch, so recovery must cover the same reachable set. Recovery is
        // idempotent per thread, so overlapping attempts from several buses are harmless.
        boolean recoveredAll = true;
        for (ECOCraftingWorkerBlockEntity worker : cluster.collectDispatchCandidateWorkers()) {
            if (!worker.recoverJobToNetwork(craftingJobId, storage)) {
                recoveredAll = false;
            }
        }
        return recoveredAll;
    }

    public record BatchFastPathOffer(
        ECOCraftingWorkerBlockEntity worker,
        ECOVerifiedFastPathRecipe recipe,
        int maxBatchSize
    ) {}

    public record VirtualFastPathOffer(
        ECOCraftingWorkerBlockEntity worker,
        ECOVerifiedFastPathRecipe recipe
    ) {}

    /**
     * The batch size this host is willing to accept. It is bounded purely by live capability - the selected
     * worker's free thread slots and the host's own remaining parallelism - so an F9 host wired into a
     * high-energy logical network may accept far larger batches than a lone F4 host. There is deliberately
     * no fixed ceiling.
     */
    static int calculateBatchOfferSize(int requestedBatchSize, int workerAvailableSlots, int hostAvailableSlots) {
        return Math.max(0, Math.min(requestedBatchSize, Math.min(workerAvailableSlots, hostAvailableSlots)));
    }

    @Override
    public boolean isBusy() {
        if (cluster == null || getAvailableThreadSlots() <= 0) {
            return true;
        }
        // Busy reporting spans the same reachable worker set the dispatch does, so a host whose own workers are
        // saturated still advertises capacity while a Network Switch peer is idle.
        return !cluster.hasAvailableDispatchCandidate();
    }

    public int getAvailableThreadSlots() {
        if (cluster == null || getCraftingController() == null) {
            return 0;
        }
        long available = 0L;
        for (ECOCraftingWorkerBlockEntity worker : cluster.collectDispatchCandidateWorkers()) {
            available = cn.dancingsnow.neoecoae.util.NEMath.saturatingAdd(
                available, worker.getAvailableBatchCapacity());
        }
        return (int) Math.min(Integer.MAX_VALUE, available);
    }

    @Nullable
    public ECOCraftingSystemBlockEntity getCraftingController() {
        if (cluster != null) {
            return cluster.getController();
        }
        return null;
    }

    @Override
    public @Nullable IGrid getGrid() {
        return getGridNode().getGrid();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return effectiveInventory;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        if (cluster != null && cluster.getController() != null) {
            var block = cluster.getController().getBlockState().getBlock();
            if (block != Blocks.AIR) {
                return new PatternContainerGroup(
                    AEItemKey.of(block.asItem()),
                    block.getName(),
                    List.of()
                );
            }
        }
        return new PatternContainerGroup(
            AEItemKey.of(NEBlocks.CRAFTING_PATTERN_BUS.asStack()),
            NEBlocks.CRAFTING_PATTERN_BUS.get().getName(),
            List.of()
        );
    }

    @Override
    public boolean insertPattern(ItemStack itemStack) {
        return insertPatternWithResult(itemStack) == ECOPatternInsertionResult.INSERTED;
    }

    @Override
    public ECOPatternInsertionResult insertPatternWithResult(ItemStack itemStack) {
        // ECO Workers only execute molecular-assembler crafting patterns. Reject processing patterns before they can
        // be advertised to a crafting CPU, which would otherwise extract and later reinject their inputs.
        ECOPreparedPattern prepared = preparePattern(itemStack);
        if (prepared == null) {
            return ECOPatternInsertionResult.INCOMPATIBLE;
        }
        return insertPreparedPattern(prepared);
    }

    @Override
    public ECOPatternInsertionResult insertPatternKnownUnique(ItemStack itemStack) {
        // PatternStorage has already checked the complete logical network for duplicates.
        // Avoid repeating containsPatternInCluster for every bus when the first target is full.
        ECOPreparedPattern prepared = preparePattern(itemStack);
        if (prepared == null) {
            return ECOPatternInsertionResult.INCOMPATIBLE;
        }
        return insertPreparedPatternKnownUnique(prepared);
    }

    /** Decodes and validates an incoming pattern once for reuse across destination buses. */
    @Nullable
    public ECOPreparedPattern preparePattern(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return null;
        }
        IPatternDetails details = PatternDetailsHelper.decodePattern(itemStack, level);
        if (!(details instanceof IMolecularAssemblerSupportedPattern)) return null;
        if (shouldValidateNetGrowthPatterns()) {
            NetGrowthPatternValidationRegistry.validateAndRegisterFromSmartPatternBus(details);
        }
        return new ECOPreparedPattern(itemStack, details, AEItemKey.of(itemStack));
    }

    @Override
    public ECOPatternInsertionResult insertPreparedPattern(ECOPreparedPattern prepared) {
        return insertPreparedPatternInternal(prepared, false);
    }

    @Override
    public ECOPatternInsertionResult insertPreparedPatternKnownUnique(ECOPreparedPattern prepared) {
        return insertPreparedPatternInternal(prepared, true);
    }

    /** Inserts a previously decoded pattern while preserving normal logical-domain duplicate checks. */
    private ECOPatternInsertionResult insertPreparedPatternInternal(ECOPreparedPattern prepared, boolean knownUnique) {
        if (!isValidPreparedPattern(prepared)) {
            return ECOPatternInsertionResult.INCOMPATIBLE;
        }
        ItemStack itemStack = prepared.stack();
        if (!knownUnique && containsPatternInCluster(itemStack)) {
            return ECOPatternInsertionResult.ALREADY_PRESENT;
        }
        return insertPreparedStack(prepared);
    }

    private ECOPatternInsertionResult insertPreparedStack(ECOPreparedPattern prepared) {
        ItemStack result;
        activePreparedPattern = prepared;
        try {
            result = addPatternItems(prepared.stack());
        } finally {
            activePreparedPattern = null;
        }
        return result.isEmpty()
            ? ECOPatternInsertionResult.INSERTED
            : ECOPatternInsertionResult.NO_SPACE;
    }

    private boolean isValidPreparedPattern(@Nullable ECOPreparedPattern prepared) {
        return prepared != null
            && prepared.details() instanceof IMolecularAssemblerSupportedPattern
            && !prepared.stack().isEmpty()
            && prepared.matches(prepared.stack());
    }

    /**
     * Purely a function of the recipe encoded in the pattern - never cached and never written back onto the
     * pattern's ItemStack, so it can't go stale and can't affect pattern identity (dedup, AEItemKey, ...).
     */
    private static boolean isDurabilityPattern(IPatternDetails details) {
        for (var input : details.getInputs()) {
            if (input == null) continue;
            for (var possible : input.getPossibleInputs()) {
                if (possible != null && possible.what() instanceof AEItemKey itemKey
                    && itemKey.toStack(1).isDamageableItem()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Places the stack through known empty slots, preserving normal remainder semantics. */
    private ItemStack addPatternItems(ItemStack stack) {
        ensurePatternCapacityIndex();
        ItemStack remaining = stack.copy();
        while (!remaining.isEmpty()) {
            int slot = emptyPatternSlots.nextSetBit(0);
            if (slot < 0 || slot >= patternCapacitySlotCount) {
                break;
            }
            ItemStack next = effectiveInventory.insertItem(slot, remaining, false);
            if (next.getCount() >= remaining.getCount()) {
                break;
            }
            remaining = next;
        }
        return remaining;
    }

    @Override
    public boolean checksLogicalDomainForDuplicates() {
        return true;
    }

    private boolean containsPatternInCluster(ItemStack pattern) {
        if (cluster == null) {
            return containsPattern(pattern);
        }
        for (ECOCraftingPatternBusBlockEntity patternBus : cluster.getPatternBuses()) {
            if (patternBus.containsPattern(pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPattern(ItemStack pattern) {
        for (ItemStack storedPattern : effectiveInventory) {
            if (ItemStack.isSameItemSameComponents(storedPattern, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExecutablePattern(ItemStack stack) {
        return PatternDetailsHelper.decodePattern(stack, level) instanceof IMolecularAssemblerSupportedPattern;
    }

    private void ensurePatternCapacityIndex() {
        int slotCount = Math.min(getPatternSlotCount(), inventory.size());
        if (!patternCapacityIndexInitialized || patternCapacitySlotCount != slotCount) {
            rebuildPatternCapacityIndex(slotCount);
        }
    }

    private void rebuildPatternCapacityIndex() {
        rebuildPatternCapacityIndex(Math.min(getPatternSlotCount(), inventory.size()));
    }

    private void rebuildPatternCapacityIndex(int slotCount) {
        emptyPatternSlots.clear();
        for (int slot = 0; slot < slotCount; slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                emptyPatternSlots.set(slot);
            }
        }
        patternCapacitySlotCount = slotCount;
        patternCapacityIndexInitialized = true;
        patternCapacityGeneration = patternCapacityGeneration == Integer.MAX_VALUE
            ? 1
            : patternCapacityGeneration + 1;
    }

    private void updatePatternCapacitySlot(int slot) {
        int slotCount = Math.min(getPatternSlotCount(), inventory.size());
        if (!patternCapacityIndexInitialized || patternCapacitySlotCount != slotCount) {
            rebuildPatternCapacityIndex(slotCount);
            return;
        }
        if (slot < 0 || slot >= slotCount) {
            return;
        }
        boolean shouldBeEmpty = inventory.getStackInSlot(slot).isEmpty();
        boolean wasEmpty = emptyPatternSlots.get(slot);
        if (shouldBeEmpty == wasEmpty) {
            return;
        }
        if (shouldBeEmpty) {
            emptyPatternSlots.set(slot);
        } else {
            emptyPatternSlots.clear(slot);
        }
        patternCapacityGeneration = patternCapacityGeneration == Integer.MAX_VALUE
            ? 1
            : patternCapacityGeneration + 1;
    }

    class AEEncodedPatternFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return slot >= 0
                && slot < getPatternSlotCount()
                && (activePreparedPattern != null
                    ? activePreparedPattern.matches(stack)
                    : isExecutablePattern(stack));
        }
    }

    public ECOCraftingPatternBusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator::new);
        this.inventory = new AppEngInternalInventory(this, NEConfig.getMaxCraftingPatternBusSlotCount());
        this.inventory.setFilter(new AEEncodedPatternFilter());
        this.itemHandler = (IItemHandlerModifiable) effectiveInventory.toItemHandler();
        this.getMainNode().addService(ICraftingProvider.class, this)
            .addService(IECOPatternStorage.class, this);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        this.saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.saveChanges();
        incrementPatternContentRevision();
        if (slot < 0 || slot >= inventory.size()) {
            patternCapacityIndexInitialized = false;
            rebuildPatternCapacityIndex();
        } else {
            updatePatternCapacitySlot(slot);
        }
        if (slot >= 0 && slot < decodedPatternDetails.length) {
            dirtyPatternSlots.set(slot);
        } else {
            rebuildAllPatternDetails = true;
        }
        queuePatternDetailsUpdate();
        notifyPatternInterfaceHosts();
    }

    @Override
    public void onReady() {
        super.onReady();
        rebuildAllPatternDetails = true;
        rebuildPatternCapacityIndex();
        updatePatternDetails();
    }

    @Override
    public void onChunkUnloaded() {
        IGrid previousGrid = getMainNode().getGrid();
        super.onChunkUnloaded();
        notifyPatternInterfaceTopologyChanged(previousGrid);
    }

    @Override
    public void setRemoved() {
        IGrid previousGrid = getMainNode().getGrid();
        super.setRemoved();
        notifyPatternInterfaceTopologyChanged(previousGrid);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (isServerStopping()) {
            return;
        }
        super.onMainNodeStateChanged(reason);
        if (reason == IGridNodeListener.State.POWER || reason == IGridNodeListener.State.GRID_BOOT) {
            queueCraftingProviderRefresh();
            notifyPatternInterfaceTopologyChanged();
        }
    }

    private void updatePatternDetails() {
        int slotCount = getPatternSlotCount();
        if (rebuildAllPatternDetails) {
            Arrays.fill(decodedPatternDetails, null);
            for (int slot = 0; slot < slotCount; slot++) {
                decodedPatternDetails[slot] = PatternDetailsHelper.decodePattern(
                    inventory.getStackInSlot(slot), level
                );
            }
        } else {
            for (int slot = dirtyPatternSlots.nextSetBit(0);
                 slot >= 0;
                 slot = dirtyPatternSlots.nextSetBit(slot + 1)) {
                decodedPatternDetails[slot] = PatternDetailsHelper.decodePattern(
                    inventory.getStackInSlot(slot), level
                );
            }
        }
        rebuildAllPatternDetails = false;
        dirtyPatternSlots.clear();

        patternDetails.clear();
        for (int slot = 0; slot < slotCount; slot++) {
            IPatternDetails details = decodedPatternDetails[slot];
            patternSearchKeywords[slot] = buildPatternSearchKeywords(inventory.getStackInSlot(slot), details);
            // Old saves and external inventory APIs may bypass the slot filter. Never publish such processing
            // patterns as executable providers, even if their encoded item remains stored for manual removal.
            if (details instanceof IMolecularAssemblerSupportedPattern) {
                if (shouldValidateNetGrowthPatterns()) {
                    NetGrowthPatternValidationRegistry.validateAndRegisterFromSmartPatternBus(details);
                }
                patternDetails.add(details);
            }
        }
        ICraftingProvider.requestUpdate(this.getMainNode());
        notifyPatternInterfaceHosts();
    }

    private boolean shouldValidateNetGrowthPatterns() {
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(getGrid());
        return settings != null && settings.neoecoae$isCyclePlanningEnabled();
    }

    private static String buildPatternSearchKeywords(ItemStack stack, @Nullable IPatternDetails details) {
        if (stack.isEmpty()) {
            return "";
        }
        StringBuilder keywords = new StringBuilder(stack.getHoverName().getString());
        if (details != null) {
            for (var output : details.getOutputs()) {
                if (output != null) {
                    keywords.append('\n').append(output.what().getDisplayName().getString());
                }
            }
            for (var input : details.getInputs()) {
                if (input == null) {
                    continue;
                }
                for (var possible : input.getPossibleInputs()) {
                    if (possible != null) {
                        keywords.append('\n').append(possible.what().getDisplayName().getString());
                    }
                }
            }
        }
        return keywords.toString().toLowerCase(Locale.ROOT);
    }

    public int getPatternContentRevision() {
        return patternContentRevision;
    }

    /** Precomputed after each pattern-detail refresh so opening the network browser never has to decode this slot. */
    public String getPatternSearchKeywords(int slot) {
        return slot >= 0 && slot < patternSearchKeywords.length ? patternSearchKeywords[slot] : "";
    }

    private void incrementPatternContentRevision() {
        patternContentRevision = patternContentRevision == Integer.MAX_VALUE
            ? 1
            : patternContentRevision + 1;
    }

    private void notifyPatternInterfaceHosts() {
        if (level == null || level.isClientSide || getMainNode().getGrid() == null) {
            return;
        }
        for (var machineInterface : getMainNode().getGrid()
                .getActiveMachines(cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity.class)) {
            machineInterface.onPatternBusInventoryChanged(this);
        }
    }

    private void notifyPatternInterfaceTopologyChanged() {
        notifyPatternInterfaceTopologyChanged(getMainNode().getGrid());
    }

    private void notifyPatternInterfaceTopologyChanged(@Nullable IGrid grid) {
        if (level == null || level.isClientSide || grid == null) {
            return;
        }
        for (var machineInterface : grid
                .getActiveMachines(cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity.class)) {
            machineInterface.onPatternBusTopologyChanged(this);
        }
    }

    /** Re-mount the provider after AE2 has completed a power or pathing transition. */
    private void queueCraftingProviderRefresh() {
        if (!(level instanceof ServerLevel serverLevel)
            || craftingProviderRefreshQueued
            || isServerStopping()) {
            return;
        }

        craftingProviderRefreshQueued = true;
        var server = serverLevel.getServer();
        int targetTick = server.getTickCount() + 1;

        server.tell(new TickTask(targetTick, () -> {
            craftingProviderRefreshQueued = false;

            if (!isServerStopping()
                && !isRemoved()
                && level == serverLevel
                && getMainNode().isOnline()) {
                ICraftingProvider.requestUpdate(getMainNode());
            }
        }));
    }

    private void queuePatternDetailsUpdate() {
        if (!(level instanceof ServerLevel serverLevel)) {
            updatePatternDetails();
            return;
        }
        patternDetailsUpdateTick = serverLevel.getServer().getTickCount() + PATTERN_UPDATE_QUIET_TICKS;
        if (patternDetailsUpdateQueued) {
            return;
        }
        patternDetailsUpdateQueued = true;
        schedulePatternDetailsUpdate(serverLevel, patternDetailsUpdateTick);
    }

    private void schedulePatternDetailsUpdate(ServerLevel serverLevel, int tick) {
        serverLevel.getServer().tell(new TickTask(tick, () -> {
            if (isRemoved() || level != serverLevel) {
                patternDetailsUpdateQueued = false;
                return;
            }
            if (serverLevel.getServer().getTickCount() < patternDetailsUpdateTick) {
                schedulePatternDetailsUpdate(serverLevel, patternDetailsUpdateTick);
                return;
            }
            patternDetailsUpdateQueued = false;
            updatePatternDetails();
        }));
    }

    @Override
    public void notifyPersistence() {
        if (level instanceof ServerLevel serverLevel) {
            ServerTaskUtil.executeIfServerRunning(serverLevel, () -> {
                setChanged();
                markForUpdate();
            });
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        IntStream.range(0, inventory.size())
            .mapToObj(inventory::getStackInSlot)
            .filter(s -> !s.isEmpty())
            .forEach(drops::add);
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement root = new UIElement().layout(layout -> layout
            .paddingAll(4)
            .paddingBottom(6)
            .gapAll(2)
            .width(UI_CONTENT_WIDTH + 8)
            .justifyContent(AlignContent.CENTER)
        ).addClass("panel_bg");

        root.addChild(headerRow());

        UIElement patternInv = new UIElement().addClass("panel_border");
        for (int row = 0; row < COL_SIZE; row++) {
            UIElement rowInv = new UIElement().layout(layout -> layout.flexDirection(FlexDirection.ROW));
            for (int col = 0; col < ROW_SIZE; col++) {
                int slotIndex = row * ROW_SIZE + col;
                UIElement slot = new VerifiedPatternItemSlot(new ItemHandlerSlot(pageItemHandler, slotIndex))
                    .slotStyle(slotStyle -> slotStyle.slotOverlay(NETextures.PATTERN_OVERLAY));
                rowInv.addChild(slot);
            }
            patternInv.addChild(rowInv);
        }
        root.addChild(patternInv);
        root.addChild(new InventorySlots().layout(layout -> layout.marginTop(5)));
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private UIElement headerRow() {
        boolean showPageControls = getPageCount() > 1;
        UIElement row = new UIElement().layout(layout -> {
            layout.width(UI_CONTENT_WIDTH);
            layout.height(showPageControls ? HEADER_HEIGHT : SINGLE_PAGE_HEADER_HEIGHT);
        });
        row.addChild(new TextElement()
            .setText(Component.translatable("block.neoecoae.crafting_pattern_bus"))
            .textStyle(textStyle -> textStyle
                .textWrap(TextWrap.HOVER_ROLL)
                .adaptiveHeight(true))
            .layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0);
                layout.top(HEADER_TITLE_TOP);
                layout.width(UI_CONTENT_WIDTH);
                layout.height(12);
            }));
        if (showPageControls) {
            row.addChild(pageControls());
        }
        return row;
    }

    private UIElement pageControls() {
        UIElement controls = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(UI_CONTENT_WIDTH - PAGE_RIGHT_MARGIN - PAGE_CONTROLS_WIDTH + PAGE_CONTROLS_OFFSET_X);
            layout.top(PAGE_TOP_MARGIN);
            layout.width(PAGE_CONTROLS_WIDTH);
            layout.height(PAGE_BUTTON_SIZE);
        });
        controls.addChild(pageButton("<", () -> changePage(currentPage - 1)).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(PAGE_BUTTON_SIZE);
            layout.height(PAGE_BUTTON_SIZE);
        }));
        controls.addChild(new PageNumberElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(PAGE_BUTTON_SIZE + PAGE_CONTROL_GAP);
            layout.top(0);
            layout.width(PAGE_LABEL_WIDTH);
            layout.height(PAGE_BUTTON_SIZE);
        }));
        controls.addChild(pageButton(">", () -> changePage(currentPage + 1)).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(PAGE_BUTTON_SIZE + PAGE_CONTROL_GAP + PAGE_LABEL_WIDTH + PAGE_CONTROL_GAP);
            layout.top(0);
            layout.width(PAGE_BUTTON_SIZE);
            layout.height(PAGE_BUTTON_SIZE);
        }));
        return controls;
    }

    private Button pageButton(String text, Runnable action) {
        Button button = new Button().setText(text);
        button.setOnServerClick(event -> action.run());
        return button;
    }

    private final class PageNumberElement extends UIElement implements IBindable<Integer> {
        private int syncedPageCount = getPageCount();

        private PageNumberElement() {
            bind(DataBindingBuilder.intValS2C(ECOCraftingPatternBusBlockEntity.this::getPageCount).build());
        }

        @Override
        public IDataSource<Integer> setValue(@Nullable Integer value) {
            syncedPageCount = value == null ? getPageCount() : Math.max(1, value);
            return this;
        }

        @Override
        public Integer getValue() {
            return syncedPageCount;
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            Font font = Minecraft.getInstance().font;
            Component text = Component.literal((currentPage + 1) + "/" + syncedPageCount);
            int x = (int)getPositionX() + Math.round((getSizeWidth() - font.width(text)) / 2.0F);
            int y = (int)getPositionY() + Math.round((getSizeHeight() - font.lineHeight) / 2.0F);
            guiContext.graphics.drawString(font, text, x, y, 0x3F3D52, false);
        }
    }

    public int getPageCount() {
        activePages = clampPages(Math.max(NEConfig.getCraftingPatternBusPages(), getHighestOccupiedPage()));
        currentPage = Math.clamp(currentPage, 0, activePages - 1);
        return activePages;
    }

    public int getPatternSlotCount() {
        return getPageCount() * SLOTS_PER_PAGE;
    }

    private void changePage(int targetPage) {
        int pageCount = getPageCount();
        int clamped = Math.clamp(targetPage, 0, pageCount - 1);
        if (clamped == currentPage) {
            return;
        }
        currentPage = clamped;
        setChanged();
        markForUpdate();
    }

    private int getHighestOccupiedPage() {
        for (int slot = inventory.size() - 1; slot >= 0; slot--) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return slot / SLOTS_PER_PAGE + 1;
            }
        }
        return 1;
    }

    private static int clampPages(int pages) {
        return Math.clamp(pages, NEConfig.PATTERN_BUS_MIN_PAGES, NEConfig.PATTERN_BUS_MAX_PAGES);
    }

    private final class EffectivePatternInventory extends BaseInternalInventory {
        @Override
        public int size() {
            return getPatternSlotCount();
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < size() ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            if (slot >= 0 && slot < size()) {
                inventory.setItemDirect(slot, stack);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < size() && inventory.isItemValid(slot, stack);
        }
    }

    private final class PagedPatternItemHandler implements IItemHandlerModifiable {
        @Override
        public int getSlots() {
            return SLOTS_PER_PAGE;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            int actualSlot = toActualSlot(slot);
            return actualSlot >= 0 ? inventory.getStackInSlot(actualSlot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int actualSlot = toActualSlot(slot);
            return actualSlot >= 0 ? itemHandler.insertItem(actualSlot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int actualSlot = toActualSlot(slot);
            return actualSlot >= 0 ? itemHandler.extractItem(actualSlot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            int actualSlot = toActualSlot(slot);
            return actualSlot >= 0 ? itemHandler.getSlotLimit(actualSlot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            int actualSlot = toActualSlot(slot);
            return actualSlot >= 0 && itemHandler.isItemValid(actualSlot, stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            int actualSlot = toActualSlot(slot);
            if (actualSlot >= 0) {
                itemHandler.setStackInSlot(actualSlot, stack);
            }
        }

        private int toActualSlot(int visibleSlot) {
            if (visibleSlot < 0 || visibleSlot >= SLOTS_PER_PAGE) {
                return -1;
            }
            int page = Math.clamp(currentPage, 0, getPageCount() - 1);
            int actualSlot = page * SLOTS_PER_PAGE + visibleSlot;
            return actualSlot < getPatternSlotCount() ? actualSlot : -1;
        }
    }

    /**
     * Appends the pattern's verification classification to its tooltip. Computed fresh from the slot's own
     * ItemStack on every hover - not cached, not written onto the pattern - so it can never go stale and never
     * affects pattern identity.
     */
    private static final class VerifiedPatternItemSlot extends PatternItemSlot {
        private VerifiedPatternItemSlot(Slot slot) {
            super(slot);
            getStyle().backgroundTexture(NETextures.ITEM_SLOT);
        }

        @Override
        public List<Component> getFullTooltipTexts() {
            List<Component> tooltip = new ArrayList<>(super.getFullTooltipTexts());
            ItemStack stack = getValue();
            Level clientLevel = Minecraft.getInstance().level;
            if (!stack.isEmpty() && clientLevel != null) {
                IPatternDetails details = PatternDetailsHelper.decodePattern(stack, clientLevel);
                if (details instanceof IMolecularAssemblerSupportedPattern) {
                    tooltip.add(Component.translatable(
                        NetGrowthPatternValidationRegistry.isSelfGrowingPattern(details)
                            ? "tooltip.neoecoae.pattern.verified_self_growing"
                            : isDurabilityPattern(details)
                            ? "tooltip.neoecoae.pattern.verified_durability"
                            : "tooltip.neoecoae.pattern.verified_normal"
                    ).withStyle(style -> style.withColor(0xFFAA00)));
                }
            }
            return tooltip;
        }
    }
}
