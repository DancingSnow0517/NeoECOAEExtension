package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.integration.ae2.keyreuse.ECOKeyFactory;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.BaseInternalInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.AuxiliaryPatternHolder;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.api.PatternStorageHost;
import cn.dancingsnow.neoecoae.api.fastpath.EcoFastpathHost;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider;
import cn.dancingsnow.neoecoae.integration.thunderbolt.ECOThunderboltBatchBridge;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathRecipe;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedVirtualExecution;
import cn.dancingsnow.neoecoae.crafting.planner.growth.NetGrowthPatternValidationRegistry;
import cn.dancingsnow.neoecoae.gui.crafting.CraftingPatternBusUI;
import cn.dancingsnow.neoecoae.gui.crafting.PatternMenuActions;
import cn.dancingsnow.neoecoae.util.PatternSearchKeywords;
import cn.dancingsnow.neoecoae.util.ServerTaskUtil;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import cn.dancingsnow.neoecoae.platform.sync.NEManagedBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.IntStream;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOCraftingPatternBusBlockEntity extends cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster, ECOCraftingPatternBusBlockEntity>
    implements NEManagedBlockEntity, InternalInventoryHost, ICraftingProvider, PatternContainer, IECOPatternStorage,
    AuxiliaryPatternHolder, PatternStorageHost, ECOFastPathDispatchProvider, EcoFastpathHost,
    ECOJobAwareCraftingProvider, ECOProcessingTargetProvider, BlockUIMenuType.MenuData {

    private Target processingTarget;

    @Override public Target neoecoae$getProcessingTarget() {
        return processingTarget;
    }

    boolean recordProcessingTarget(ECOCraftingWorkerBlockEntity worker, boolean accepted) {
        if (accepted && worker.getLevel() != null) processingTarget = new Target(
                worker.getLevel().dimension().location(), worker.getBlockPos());
        return accepted;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    public static final int ROW_SIZE = 9;
    public static final int COL_SIZE = 7;
    public static final int SLOTS_PER_PAGE = ROW_SIZE * COL_SIZE;

    @Persisted
    private final AppEngInternalInventory inventory;
    private final InternalInventory effectiveInventory = new EffectivePatternInventory();
    private final Map<Player, Integer> viewerPages = new WeakHashMap<>();
    private final ECOCraftingPatternBusCatalog catalog;
    private final ECOCraftingPatternBusDispatcher dispatcher;
    private static final int EXTERNAL_NONCE_HISTORY = 1024;
    private final Object2ObjectLinkedOpenHashMap<UUID, FastpathSubmission> externalFastpathSubmissions =
        new Object2ObjectLinkedOpenHashMap<>(64);
    private final ECOCraftingPatternBusPublisher publisher;
    public final IItemHandlerModifiable itemHandler;
    @Persisted
    @DescSynced
    private int activePages = NEConfig.getCraftingPatternBusPages();
    @Persisted
    @DescSynced
    private int importedLegacyPages;
    @Persisted
    private net.minecraft.nbt.CompoundTag legacyPatternData = new net.minecraft.nbt.CompoundTag();
    @DescSynced
    private int patternContentRevision;

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return legacyPatternData.isEmpty() ? catalog.availablePatterns() : List.of();
    }

    public List<PatternSearchKeywords.Directions> getAuxiliaryPatternSearchDirections() {
        return List.of();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return pushPattern(patternDetails, inputHolder, null);
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, @Nullable UUID craftingJobId) {
        return legacyPatternData.isEmpty() && dispatcher.pushPattern(patternDetails, inputHolder, craftingJobId);
    }

    /** Ordinary single-craft fallback without another FastPath lookup. */
    public boolean pushPatternSlow(IPatternDetails pattern, KeyCounter[] inputs, @Nullable UUID craftingJobId) {
        return legacyPatternData.isEmpty() && dispatcher.pushPatternSlow(pattern, inputs, craftingJobId);
    }

    public boolean pushPattern(ECOExtractedPatternExecution execution, @Nullable UUID craftingJobId) {
        return legacyPatternData.isEmpty() && dispatcher.pushPattern(execution, craftingJobId);
    }

    @Override
    public @Nullable ExactPreparation eco$prepareExactFastPath(ECOBatchDispatchContext context, java.math.BigInteger requested) {
        return dispatcher.prepareExactFastPath(context, requested);
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
        if (pattern == null
            || !ECOThunderboltBatchBridge.fastPathEligible(this, pattern)) {
            return rejectedCapability(RejectionReason.UNSUPPORTED_PROCESSING);
        }
        long accepted = Math.min(request.requestedAmount(), Math.max(0, getAvailableThreadSlots()));
        if (accepted <= 0L) return rejectedCapability(RejectionReason.BUSY);
        var tier = externalHostTier();
        return new FastpathCapability(API_VERSION, CAPABILITY_ID, tier, accepted,
            tier == HostTier.F9 ? DurationClass.IMMEDIATE : DurationClass.SHORT, RejectionReason.NONE);
    }

    @Override
    public FastpathSubmission submit(FastpathRequest request) {
        var previous = externalFastpathSubmissions.getAndMoveToLast(request.nonce());
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
            long leftover = ECOThunderboltBatchBridge
                .drainFastPath(this, pattern, inputs, capability.acceptedAmount(), getLevel(), null);
            if (leftover < 0L || leftover > capability.acceptedAmount()) leftover = capability.acceptedAmount();
            long amount = capability.acceptedAmount() - leftover;
            long unaccepted = request.requestedAmount() - amount;
            var status = amount == 0L ? Status.REJECTED : unaccepted == 0L ? Status.ACCEPTED : Status.PARTIAL;
            return rememberExternal(request.nonce(), new FastpathSubmission(status, amount, List.of(),
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
        externalFastpathSubmissions.putAndMoveToLast(nonce, submission);
        if (externalFastpathSubmissions.size() > EXTERNAL_NONCE_HISTORY) {
            externalFastpathSubmissions.removeFirst();
        }
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

    public cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster getCraftingCluster() {
        return cluster;
    }

    @Override
    public @Nullable IGrid getGrid() {
        var node = getGridNode();
        return node == null ? null : node.getGrid();
    }

    /**
     * The bus's real pattern slots, pattern disks included.
     *
     * <p>Separate from {@link #getTerminalPatternInventory()} on purpose. That one is the pattern access
     * terminal's contract and hides the disks, whereas everything that has to see the slots as they are - the
     * auxiliary containers' disk scan, the catalog's index, this bus's own slot accessors - reads this one. Both
     * used to be one method, which made the terminal's display semantics leak into the write paths: a disk
     * slot then looked empty to them and got overwritten.</p>
     */
    public InternalInventory getPatternSlotInventory() {
        return effectiveInventory;
    }

    /**
     * The rows the terminal view appends after the real slots.
     *
     * <p>A function rather than a call spelled out in the view itself, so a bus variant can widen or narrow what
     * the terminal shows without touching how the view is sized or frozen. The default is what the auxiliary
     * containers publish, which is the recipes on the disks.</p>
     */
    protected List<ItemStack> terminalAppendedRows() {
        return getAuxiliaryEncodedPatterns();
    }

    /**
     * The view the pattern access terminal lists.
     *
     * <p>A disk is not a pattern: listed as one it is a stack the terminal cannot decode, and the recipes
     * inside it - the reason it is there at all - appear nowhere. So the disk's slot renders empty and the
     * recipes it publishes are appended after the slots. Appending rather than compacting keeps every index
     * mapping straight onto the real inventory.</p>
     *
     * <p>The appended rows are the disk mod's own view when it is present - the same rows the workstation
     * interface serves - so a recipe can be taken back out of a disk here too: the mod draws a blank pattern
     * from the network and removes the recipe from its disk. Without the mod they fall back to a display-only
     * list.</p>
     *
     * <p>This is a terminal contract. Nothing that touches the slots may read it - see
     * {@link #getPatternSlotInventory()}.</p>
     */
    @Override
    public InternalInventory getTerminalPatternInventory() {
        return getPatternSlotInventory();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        if (cluster != null && cluster.getController() != null) {
            var block = cluster.getController().getBlockState().getBlock();
            if (block != Blocks.AIR) {
                return new PatternContainerGroup(
                    ECOKeyFactory.item(block.asItem()),
                    block.getName(),
                    List.of()
                );
            }
        }
        return new PatternContainerGroup(
            ECOKeyFactory.item(NEBlocks.CRAFTING_PATTERN_BUS.asStack()),
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
        if (!legacyPatternData.isEmpty()) return null;
        if (itemStack.isEmpty()) {
            return null;
        }
        IPatternDetails details = PatternDetailsHelper.decodePattern(itemStack, level);
        if (!(details instanceof IMolecularAssemblerSupportedPattern)) return null;
        if (shouldValidateNetGrowthPatterns()) {
            NetGrowthPatternValidationRegistry.validateAndRegisterFromSmartPatternBus(details);
        }
        return new ECOPreparedPattern(itemStack, details, ECOKeyFactory.item(itemStack));
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

    @Override
    public boolean checksLogicalDomainForDuplicates() {
        return true;
    }

    private boolean containsPatternInCluster(ItemStack pattern) {
        if (cluster == null) {
            return containsPattern(pattern);
        }
        for (ECOCraftingPatternBusBlockEntity patternBus : cluster.getPatternDomainBuses()) {
            if (patternBus.containsPattern(pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPattern(ItemStack pattern) {
        for (ItemStack storedPattern : effectiveInventory) {
            if (ItemStack.isSameItemSameTags(storedPattern, pattern)) {
                return true;
            }
        }
        for (ItemStack storedPattern : getAuxiliaryEncodedPatterns()) {
            if (ItemStack.isSameItemSameTags(storedPattern, pattern)) {
                return true;
            }
        }
        return false;
    }

    class AEEncodedPatternFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            if (catalog.allowInsert(slot, stack)) {
                return true;
            }
            // 样板磁盘这类「一格装多张样板」的容器过不了 AE2 的「一格一张可执行样板」判据，
            // 由辅助存储声明的 owns 放行（否则磁盘根本放不进总线）。
            return slot >= 0 && slot < getPatternSlotCount() && ownsAuxiliary(stack);
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

    public void saveChangedInventory(appeng.util.inv.AppEngInternalInventory inv) {
        if (catalog.isBatchActive()) {
            return;
        }
        this.saveChanges();
    }

    @Override
    public void onChangeInventory(appeng.api.inventories.InternalInventory inv, int slot) {
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

    /**
     * Republishes this provider's pattern list to AE2.
     *
     * <p>The auxiliary side is polled rather than notified, so a disk can change without the inventory ever
     * reporting it. AE2 caches the list until it is asked to read it again, which is why patterns of a disk
     * that is already gone keep being dispatched until some unrelated change to the grid forces a rescan.
     * Re-publishing whenever the polled revision moved turns that into a self-healing tick instead of
     * depending on luck.</p>
     */
    public void refreshAdvertisedPatterns() {
        if (level == null || level.isClientSide) {
            return;
        }
        updatePatternDetailsNow();
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

    private void notifyPatternInterfaceTopologyChanged(@Nullable IGrid grid) {
        publisher.notifyPatternInterfaceTopologyChanged(grid);
    }

    /** Flushes a coalesced pattern-content update. */
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
        if (!legacyPatternData.isEmpty()) return; // The dismantled block carries the undecodable original inventory.
        IntStream.range(0, inventory.size())
            .mapToObj(inventory::getStackInSlot)
            .filter(s -> !s.isEmpty())
            .forEach(drops::add);
    }

    @Override
    public void loadTag(net.minecraft.nbt.CompoundTag data) {
        super.loadTag(data);
        readLegacyPatterns(data);
    }

    private void readLegacyPatterns(net.minecraft.nbt.CompoundTag data) {
        if (legacyPatternData.isEmpty() && (data.contains("inventory")
            || !data.contains("patternInventory") && !data.contains("patternInventoryPages"))) return;
        var original = legacyPatternData.isEmpty() ? new net.minecraft.nbt.CompoundTag() : legacyPatternData.copy();
        if (legacyPatternData.isEmpty()) {
            if (data.contains("patternInventory")) original.put("patternInventory", data.get("patternInventory").copy());
            else original.put("patternInventory", new net.minecraft.nbt.ListTag());
            if (data.contains("patternInventoryPages")) original.put("patternInventoryPages", data.get("patternInventoryPages").copy());
        }
        try {
            if (!inventory.isEmpty()) throw new IllegalStateException("Conflicting current and 20.4.2 pattern inventories");
            var restored = cn.dancingsnow.neoecoae.platform.nbt.Legacy2042Inventory
                .read(original, "patternInventory", inventory.size(), 64);
            int pages = cn.dancingsnow.neoecoae.platform.nbt.Legacy2042Inventory
                .pages(original, restored, SLOTS_PER_PAGE, NEConfig.PATTERN_BUS_MAX_PAGES);
            importedLegacyPages = pages;
            legacyPatternData = new net.minecraft.nbt.CompoundTag();
            beginPatternBatch();
            try {
                for (int slot = 0; slot < restored.size(); slot++) if (!restored.get(slot).isEmpty())
                    setPatternDirect(slot, restored.get(slot));
            } finally { endPatternBatch(); }
            catalog.updatePatternDetails();
        } catch (RuntimeException failure) {
            legacyPatternData = original;
            LOGGER.error("Cannot import 20.4.2 patterns at {}; original inventory retained and bus locked", worldPosition, failure);
        }
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode, net.minecraft.nbt.CompoundTag input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode == appeng.util.SettingsFrom.DISMANTLE_ITEM && inventory.isEmpty()) {
            if (input.contains("legacyPatternData")) loadManagedPersistentData(input);
            readLegacyPatterns(input);
        }
    }

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode, net.minecraft.nbt.CompoundTag output, @Nullable Player player) {
        super.exportSettings(mode, output, player);
        if (mode == appeng.util.SettingsFrom.DISMANTLE_ITEM && !legacyPatternData.isEmpty())
            saveManagedPersistentData(output, false);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        beginPatternBatch();
        try { inventory.clear(); legacyPatternData = new net.minecraft.nbt.CompoundTag(); }
        finally { endPatternBatch(); }
    }

    @Override
    public CompoundTag createMenuData(Player player) {
        var data = new CompoundTag();
        int pages = getPageCount();
        data.putInt("pages", pages);
        data.putInt("page", JdkCompat.clamp(viewerPages.getOrDefault(player, 0), 0, pages - 1));
        data.putInt("slots", effectiveInventory.size());
        return data;
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        var data = holder.menuData();
        int page = data.getInt("page");
        int start = page * SLOTS_PER_PAGE;
        int limit = Math.min(start + SLOTS_PER_PAGE, data.getInt("slots"));
        return CraftingPatternBusUI.create(new CraftingPatternBusUI.Config(
            () -> cn.dancingsnow.neoecoae.gui.GuiTitleProvider.title(getBlockState(),
                Component.translatable("block.neoecoae.crafting_pattern_bus")),
            index -> new PatternBusMenuSlot(holder, start + index, limit),
            (stack, simulate) -> insertIntoPage(holder, start, limit, stack, simulate),
            data.getInt("pages"), page, target -> changePage(holder, page, target)), holder.player);
    }

    public int getPageCount() {
        // catalog 在构造器体里才赋值，而本方法会被构造期路径间接触发（字段初始化器算 getPatternSlotCount），
        // 那时只可能知道配置页数——构造期还没有磁盘可读，配置值就是当时唯一正确的答案。
        int highestOccupiedSlot = catalog == null ? -1 : catalog.highestOccupiedSlot();
        int pages = clampPages(Math.max(
            Math.max(NEConfig.getCraftingPatternBusPages(), importedLegacyPages),
            highestOccupiedSlot < 0 ? 1 : highestOccupiedSlot / SLOTS_PER_PAGE + 1
        ));
        // 只在变化时写：本方法被每一次槽位查找调用，读一次就写一次会让这个同步字段来回改。
        if (activePages != pages) {
            activePages = pages;
        }
        return activePages;
    }

    public int getPatternSlotCount() {
        return legacyPatternData.isEmpty() ? getPageCount() * SLOTS_PER_PAGE : 0;
    }

    private void changePage(BlockUIMenuType.BlockUIHolder holder, int page, int targetPage) {
        if (!(holder.player instanceof ServerPlayer player) || holder.isInvalid()) return;
        int target = JdkCompat.clamp(targetPage, 0, getPageCount() - 1);
        if (target == page) return;
        viewerPages.put(player, target);
        if (!PatternMenuActions.reopen(player, worldPosition)) viewerPages.put(player, page);
    }

    private ItemStack insertIntoPage(BlockUIMenuType.BlockUIHolder holder, int start, int limit,
            ItemStack stack, boolean simulate) {
        if (holder.isInvalid()) return stack;
        ItemStack remainder = stack.copy();
        for (int slot = start; slot < limit && !remainder.isEmpty(); slot++) {
            remainder = itemHandler.insertItem(slot, remainder, simulate);
        }
        return remainder;
    }

    private static int clampPages(int pages) {
        return JdkCompat.clamp(pages, NEConfig.PATTERN_BUS_MIN_PAGES, NEConfig.PATTERN_BUS_MAX_PAGES);
    }

    private final class EffectivePatternInventory extends BaseInternalInventory {
        @Override
        public int size() {
            // A subclass may expose a wider logical view than this bus's backing AppEng inventory.
            // Keep this physical slot view within the storage it actually delegates to.
            return Math.min(Math.max(0, getPatternSlotCount()), inventory.size());
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= 0 && slot < size() ? inventory.getSlotLimit(slot) : 0;
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

    private final class PatternBusMenuSlot extends Slot {
        private final BlockUIMenuType.BlockUIHolder holder;
        private final int physicalSlot;
        private final int limit;

        private PatternBusMenuSlot(BlockUIMenuType.BlockUIHolder holder, int physicalSlot, int limit) {
            super(new SimpleContainer(1), 0, 0, 0);
            this.holder = holder;
            this.physicalSlot = physicalSlot;
            this.limit = limit;
        }

        @Override public boolean isActive() {
            return physicalSlot < limit && (holder.isRemote() || physicalSlot < effectiveInventory.size());
        }

        @Override public ItemStack getItem() {
            if (holder.isRemote()) return super.getItem();
            return isActive() ? effectiveInventory.getStackInSlot(physicalSlot) : ItemStack.EMPTY;
        }

        @Override public boolean mayPlace(ItemStack stack) {
            return isActive() && (holder.isRemote() || effectiveInventory.isItemValid(physicalSlot, stack));
        }

        @Override public boolean mayPickup(Player player) {
            return isActive() && (holder.isRemote() || !itemHandler.extractItem(physicalSlot, 1, true).isEmpty());
        }

        @Override public void set(ItemStack stack) {
            if (holder.isRemote()) super.set(stack);
            else if (isActive() && (PatternMenuActions.isRemainder(getItem(), stack)
                    || mayPlace(stack) && stack.getCount() <= getMaxStackSize(stack))) {
                setPatternDirect(physicalSlot, stack.copy());
            }
        }

        @Override public void setChanged() {
            if (!holder.isRemote() && isActive()) onChangeInventory(effectiveInventory, physicalSlot);
        }

        @Override public ItemStack remove(int amount) {
            if (holder.isRemote()) return super.remove(amount);
            return isActive() ? itemHandler.extractItem(physicalSlot, amount, false) : ItemStack.EMPTY;
        }

        @Override public int getMaxStackSize() {
            return holder.isRemote() ? super.getMaxStackSize() : effectiveInventory.getSlotLimit(physicalSlot);
        }
    }

}
