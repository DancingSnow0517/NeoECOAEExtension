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
import appeng.client.gui.Icon;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.fastpath.EcoFastpathHost;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOStatefulBatchCalculator;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathLookup;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECORecipeClassifier;
import cn.dancingsnow.neoecoae.impl.crafting.planner.growth.NetGrowthPatternValidationRegistry;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedFastPathExecution;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedFastPathRecipe;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOVerifiedVirtualExecution;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.util.ServerTaskUtil;
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
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.TickTask;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

public class ECOCraftingPatternBusBlockEntity extends cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster, ECOCraftingPatternBusBlockEntity>
    implements ISyncPersistRPCBlockEntity, InternalInventoryHost, ICraftingProvider, PatternContainer, IECOPatternStorage,
    ECOFastPathDispatchProvider, EcoFastpathHost {

    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

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
    public static final int SLOTS_PER_PAGE = ROW_SIZE * COL_SIZE;

    @Persisted
    private final AppEngInternalInventory inventory;
    private final InternalInventory effectiveInventory = new EffectivePatternInventory();
    private final IItemHandlerModifiable pageItemHandler = new PagedPatternItemHandler();
    private final ECOCraftingPatternBusCatalog catalog;
    private final ECOCraftingPatternBusDispatcher dispatcher;
    private static final int EXTERNAL_NONCE_HISTORY = 1024;
    private final Map<UUID, FastpathSubmission> externalFastpathSubmissions =
        new LinkedHashMap<>(64, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, FastpathSubmission> eldest) {
                return size() > EXTERNAL_NONCE_HISTORY;
            }
        };
    private final ECOCraftingPatternBusPublisher publisher;
    public final IItemHandlerModifiable itemHandler;
    @Persisted
    @DescSynced
    private int activePages = NEConfig.getCraftingPatternBusPages();
    @DescSynced
    private int currentPage;
    @DescSynced
    private int patternContentRevision;

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return catalog.availablePatterns();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return pushPattern(patternDetails, inputHolder, null);
    }

    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, @Nullable UUID craftingJobId) {
        return dispatcher.pushPattern(patternDetails, inputHolder, craftingJobId);
    }

    /** Ordinary single-craft fallback without another FastPath lookup. */
    public boolean pushPatternSlow(IPatternDetails pattern, KeyCounter[] inputs, @Nullable UUID craftingJobId) {
        return dispatcher.pushPatternSlow(pattern, inputs, craftingJobId);
    }

    public boolean pushPattern(ECOExtractedPatternExecution execution, @Nullable UUID craftingJobId) {
        return dispatcher.pushPattern(execution, craftingJobId);
    }

    @Override
    public @Nullable Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        return dispatcher.prepareFastPath(context);
    }

    @Override
    public FastpathCapability inspect(FastpathRequest request) {
        var rejection = validateExternalRequest(request);
        if (rejection != RejectionReason.NONE) return rejectedCapability(rejection);
        var pattern = findExternalPattern(request);
        if (pattern == null) return rejectedCapability(RejectionReason.UNSUPPORTED_PROCESSING);
        long accepted = Math.min(request.requestedAmount(), Math.max(0, getAvailableThreadSlots()));
        if (accepted <= 0L) return rejectedCapability(RejectionReason.BUSY);
        var tier = externalHostTier();
        return new FastpathCapability(API_VERSION, CAPABILITY_ID, tier, accepted,
            tier == HostTier.F9 ? DurationClass.IMMEDIATE : DurationClass.SHORT, RejectionReason.NONE);
    }

    @Override
    public FastpathSubmission submit(FastpathRequest request) {
        var previous = externalFastpathSubmissions.get(request.nonce());
        if (previous != null) return previous;
        var capability = inspect(request);
        if (capability.acceptedAmount() <= 0L) {
            return rememberExternal(request.nonce(), rejectedSubmission(request, capability.rejectionReason()));
        }
        var pattern = findExternalPattern(request);
        if (pattern == null || getLevel() == null) {
            return rememberExternal(request.nonce(), rejectedSubmission(request, RejectionReason.UNSUPPORTED_PROCESSING));
        }
        try {
            var inputs = request.inputsPerCraft().stream().map(slot -> {
                var counter = new KeyCounter();
                for (var stack : slot) counter.add(stack.what(), stack.amount());
                return counter;
            }).toArray(KeyCounter[]::new);
            var batch = ECOFastPathFacade.prepareAllocated(this, pattern, inputs,
                capability.acceptedAmount(), getLevel(), null);
            boolean accepted = batch != null && batch.submit(amount -> new ECOFastPathFacade.Reservation() {
                @Override public void commit() {}
                @Override public void refund() {}
            });
            long amount = accepted ? batch.craftCount() : 0L;
            long unaccepted = request.requestedAmount() - amount;
            var status = amount == 0L ? Status.REJECTED : unaccepted == 0L ? Status.ACCEPTED : Status.PARTIAL;
            var results = amount == 0L ? List.<appeng.api.stacks.GenericStack>of() : batch.outputs();
            return rememberExternal(request.nonce(), new FastpathSubmission(status, amount, results,
                unaccepted, amount == 0L, amount == 0L ? RejectionReason.NO_CAPACITY : RejectionReason.NONE));
        } catch (RuntimeException failure) {
            return rememberExternal(request.nonce(), new FastpathSubmission(Status.REJECTED, 0L, List.of(),
                request.requestedAmount(), false, RejectionReason.SUBMISSION_FAILED));
        }
    }

    private RejectionReason validateExternalRequest(FastpathRequest request) {
        if (request.apiVersion() != API_VERSION) return RejectionReason.VERSION_MISMATCH;
        if (!CAPABILITY_ID.equals(request.targetCapabilityId())) return RejectionReason.CAPABILITY_MISMATCH;
        if (!(getLevel() instanceof ServerLevel serverLevel)
            || !serverLevel.getServer().isSameThread()) return RejectionReason.NOT_SERVER_THREAD;
        return RejectionReason.NONE;
    }

    private @Nullable IPatternDetails findExternalPattern(FastpathRequest request) {
        for (var pattern : catalog.availablePatterns()) {
            if (pattern.getDefinition().equals(request.processingId())) return pattern;
        }
        return null;
    }

    private static FastpathCapability rejectedCapability(RejectionReason reason) {
        return new FastpathCapability(API_VERSION, CAPABILITY_ID, HostTier.FASTPATH, 0L,
            DurationClass.NORMAL, reason);
    }

    private HostTier externalHostTier() {
        var controller = getCraftingController();
        if (controller == null) return HostTier.FASTPATH;
        return switch (controller.getTier().getTier()) {
            case 4 -> HostTier.F4;
            case 6 -> HostTier.F6;
            case 9 -> HostTier.F9;
            default -> HostTier.FASTPATH;
        };
    }

    private static FastpathSubmission rejectedSubmission(FastpathRequest request, RejectionReason reason) {
        return new FastpathSubmission(Status.REJECTED, 0L, List.of(), request.requestedAmount(),
            reason == RejectionReason.BUSY || reason == RejectionReason.NO_CAPACITY, reason);
    }

    private FastpathSubmission rememberExternal(UUID nonce, FastpathSubmission submission) {
        externalFastpathSubmissions.put(nonce, submission);
        return submission;
    }

    public boolean acceptVerifiedBatch(ECOVerifiedFastPathExecution verified, @Nullable BatchFastPathOffer offer) {
        return dispatcher.acceptVerifiedBatch(verified, offer);
    }

    /** 已更换：仅保留给旧版 Crafting Tracker 注入的兼容空函数，不再执行批量合成。 */
    @Deprecated(forRemoval = false)
    public boolean pushBatch(ECOBatchCraftingRequest request, @Nullable BatchFastPathOffer offer) {
        return false;
    }

    public boolean pushVirtualBatch(ECOVerifiedVirtualExecution verified, @Nullable VirtualFastPathOffer offer) {
        return dispatcher.pushVirtualBatch(verified, offer);
    }

    @Nullable
    public VirtualFastPathOffer findVirtualFastPathOffer(ECOExtractedPatternExecution execution) {
        return dispatcher.findVirtualFastPathOffer(execution);
    }

    @Nullable
    public BatchFastPathOffer findBatchFastPathOffer(ECOExtractedPatternExecution execution, int requestedBatchSize) {
        return dispatcher.findBatchFastPathOffer(execution, requestedBatchSize);
    }

    public boolean recoverJobToNetwork(UUID craftingJobId, appeng.api.storage.MEStorage storage) {
        return dispatcher.recoverJobToNetwork(craftingJobId, storage);
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

    /**
     * Applies the recipe's own arithmetic/state limit after the live host-capacity calculation. A durability
     * recipe may have fewer remaining uses than one F-series machine batch can hold, and that final partial batch
     * must be offered at its actual safe size so the verified execution can accept it.
     */
    static int calculateBatchOfferSize(
            int requestedBatchSize,
            int workerAvailableSlots,
            int hostAvailableSlots,
            long recipeBatchLimit
    ) {
        long boundedRecipeLimit = Math.max(0L, Math.min((long) Integer.MAX_VALUE, recipeBatchLimit));
        return (int) Math.min(
            (long) calculateBatchOfferSize(requestedBatchSize, workerAvailableSlots, hostAvailableSlots),
            boundedRecipeLimit
        );
    }

    @Override
    public boolean isBusy() {
        return dispatcher.isBusy();
    }

    public int getAvailableThreadSlots() {
        return dispatcher.availableThreadSlots();
    }

    @Nullable
    public ECOCraftingSystemBlockEntity getCraftingController() {
        if (cluster != null) {
            return cluster.getController();
        }
        return null;
    }

    cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster getCraftingCluster() {
        return cluster;
    }

    boolean isServerStoppingForPublisher() {
        return isServerStopping();
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
        return catalog.insertPreparedPattern(prepared);
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

    class AEEncodedPatternFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return catalog.allowInsert(slot, stack);
        }
    }

    public ECOCraftingPatternBusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator::new);
        this.inventory = new AppEngInternalInventory(this, NEConfig.getMaxCraftingPatternBusSlotCount());
        this.catalog = new ECOCraftingPatternBusCatalog(this, this.inventory);
        this.dispatcher = new ECOCraftingPatternBusDispatcher(this);
        this.publisher = new ECOCraftingPatternBusPublisher(this);
        this.inventory.setFilter(new AEEncodedPatternFilter());
        this.itemHandler = (IItemHandlerModifiable) effectiveInventory.toItemHandler();
        this.getMainNode().addService(ICraftingProvider.class, this)
            .addService(IECOPatternStorage.class, this);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        if (catalog.isBatchActive()) {
            return;
        }
        this.saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        catalog.onChangeInventory(slot);
    }

    /** Starts a server-thread mutation batch. Nested callers share the outer commit. */
    public void beginPatternBatch() {
        catalog.beginBatch();
    }

    /** Writes one physical slot while retaining the exact key/space delta for the catalog commit. */
    public void setPatternDirect(int slot, ItemStack stack) {
        catalog.setPatternDirect(slot, stack);
    }

    public void endPatternBatch() {
        catalog.endBatch();
    }

    void notifyPatternCatalog(int previousRevision, int[] changedSlots) {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        IECOPatternStorageService storageService = grid.getService(IECOPatternStorageService.class);
        if (storageService != null) {
            storageService.onPatternSlotsChanged(this, previousRevision, changedSlots);
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        catalog.onReady();
    }

    @Override
    public void onChunkUnloaded() {
        IGrid previousGrid = getMainNode().getGrid();
        PatternBusUpdateScheduler.remove(this);
        super.onChunkUnloaded();
        notifyPatternInterfaceTopologyChanged(previousGrid);
    }

    @Override
    public void setRemoved() {
        IGrid previousGrid = getMainNode().getGrid();
        PatternBusUpdateScheduler.remove(this);
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
        catalog.updatePatternDetails();
    }

    private void updatePatternDetailsNow() {
        catalog.updatePatternDetailsNow();
    }

    /**
     * Compatibility entry point for integrations whose asynchronously supplied pattern set has changed.
     * Repeated requests for this bus share the normal two-tick quiet window and produce one provider refresh.
     */
    public void requestPatternDetailsRefresh() {
        catalog.requestPatternDetailsRefresh();
    }

    boolean shouldValidateNetGrowthPatterns() {
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(getGrid());
        return settings != null && settings.neoecoae$isCyclePlanningEnabled();
    }

    public int getPatternContentRevision() {
        return catalog.patternContentRevision();
    }

    int patternContentRevisionValue() {
        return patternContentRevision;
    }

    void incrementPatternContentRevision() {
        patternContentRevision = patternContentRevision == Integer.MAX_VALUE
            ? 1
            : patternContentRevision + 1;
    }

    /** Precomputed after each pattern-detail refresh so opening the network browser never has to decode this slot. */
    public String getPatternSearchKeywords(int slot) {
        return catalog.patternSearchKeywords(slot);
    }

    /** Makes the bus-owned decode cache current before catalog planning reads it. */
    public void refreshPatternDetailsForCatalog() {
        catalog.refreshPatternDetailsForCatalog();
    }

    @Nullable
    public IPatternDetails getDecodedPatternDetails(int slot) {
        return catalog.decodedPatternDetails(slot);
    }

    void notifyPatternInterfaceHosts(int slot) {
        publisher.notifyPatternInterfaceHosts(slot);
    }

    void notifyPatternInterfaceHosts(int[] slots) {
        publisher.notifyPatternInterfaceHosts(slots);
    }

    private void notifyPatternInterfaceTopologyChanged() {
        publisher.notifyPatternInterfaceTopologyChanged();
    }

    private void notifyPatternInterfaceTopologyChanged(@Nullable IGrid grid) {
        publisher.notifyPatternInterfaceTopologyChanged(grid);
    }

    /** Re-mount the provider after AE2 has completed a power or pathing transition. */
    private void queueCraftingProviderRefresh() {
        publisher.queueCraftingProviderRefresh();
    }

    public void flushScheduledPatternDetails() {
        catalog.flushScheduledPatternDetails();
    }

    @Override
    public void notifyPersistence() {
        if (level instanceof ServerLevel serverLevel) {
            ServerTaskUtil.executeIfServerRunning(serverLevel, () -> {
                setChanged();
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
                UIElement slot = new PatternItemSlot(new ItemHandlerSlot(pageItemHandler, slotIndex))
                    .slotStyle(style -> style.slotOverlay(AETextures.icon(Icon.BACKGROUND_BLANK_PATTERN)))
                    .addClass("eco-pattern-slot");
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
        TextElement pageNumber = new TextElement()
            .setText(Component.literal((currentPage + 1) + "/" + getPageCount()));
        controls.addChild(pageNumber.layout(layout -> {
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

    public int getPageCount() {
        activePages = clampPages(Math.max(
            NEConfig.getCraftingPatternBusPages(),
            catalog.highestOccupiedSlot() < 0 ? 1 : catalog.highestOccupiedSlot() / SLOTS_PER_PAGE + 1
        ));
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
            if (actualSlot < 0) {
                return;
            }
            ItemStack next = stack == null ? ItemStack.EMPTY : stack;
            if (!next.isEmpty() && !inventory.isItemValid(actualSlot, next)) {
                return;
            }
            ItemStack previous = inventory.getStackInSlot(actualSlot);
            if (ItemStack.matches(previous, next)) {
                return;
            }
            inventory.setItemDirect(actualSlot, next);
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

}
