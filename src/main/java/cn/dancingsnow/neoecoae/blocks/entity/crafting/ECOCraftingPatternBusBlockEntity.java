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
import cn.dancingsnow.neoecoae.api.AuxiliaryPatternStore;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.fastpath.EcoFastpathHost;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOThunderboltBatchBridge;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOStatefulBatchCalculator;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathLookup;
import cn.dancingsnow.neoecoae.crafting.planner.growth.NetGrowthPatternValidationRegistry;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedFastPathRecipe;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOVerifiedVirtualExecution;
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

    // ---- auxiliary pattern store (pattern disks and the like) ----------------------------------

    @Nullable
    private static AuxiliaryPatternStore auxiliaryPatternStore;

    /** @see TerminalInventoryHook */
    @Nullable
    private static TerminalInventoryHook terminalInventoryHook;

    /**
     * Registered once by an integration mod. Left {@code null}, the bus behaves exactly as before: it
     * stores patterns in slots and knows nothing about auxiliary containers.
     */
    public static void setAuxiliaryPatternStore(@Nullable AuxiliaryPatternStore store) {
        auxiliaryPatternStore = store;
    }

    @Nullable
    public static AuxiliaryPatternStore getAuxiliaryPatternStore() {
        return auxiliaryPatternStore;
    }

    /**
     * Supplies the pattern access terminal's view of this bus.
     *
     * <p>Registered once by an integration mod, like {@link #setAuxiliaryPatternStore}. Left {@code null}, the
     * bus builds its own view: the pattern slots with the auxiliary containers' recipes appended as display-only
     * rows. An integration that owns those containers can hand over a view of its own instead, and that is what
     * makes their contents interactive rather than display-only - the terminal reads and writes through this
     * inventory, so whoever supplies it owns what a row means, when a removal is paid for, and when the result
     * lands back in the container.</p>
     *
     * <p>Nothing inside the bus reads it: the slots, the index and the container scan all go through
     * {@link #getPatternSlotInventory()}, so a view supplied here cannot make a container look like an empty
     * slot to the write paths.</p>
     *
     * <p>What the view owes in return, since these are contracts the bus otherwise keeps itself:</p>
     *
     * <ul>
     *   <li><b>A stable row count within one session.</b> The bus hands out a fresh view per revision for
     *       exactly this reason: a pattern access terminal sizes its mirror from the container's size when the
     *       session opens and keeps indexing it against the container's current size afterwards, so a view that
     *       grows or shrinks mid-session walks off the end of that mirror. Return the same instance while the
     *       session lasts, or accept that the next session is when a change shows up.</li>
     *   <li><b>A declaration of which leading rows are the bus's own slots.</b> The terminal's move-all reads
     *       {@code WritablePrefix.writableSlotCount()} to keep display-only rows from being copied into a
     *       player's inventory. Returning a view that does not implement it is therefore not a compile error but
     *       a duplication bug, so either implement it or see {@link TerminalInventoryHook#writableRows}.</li>
     *   <li><b>Exceptions stay inside.</b> This is called from the terminal's container query, on the path where
     *       items move, so a throw is neither caught here nor cheap to attribute afterwards.</li>
     * </ul>
     *
     * <p>Registered once per process, not per bus: {@code view} is asked which bus it is looking at.</p>
     */
    @FunctionalInterface
    public interface TerminalInventoryHook {

        /** @return the view to hand the terminal, or {@code null} to keep the bus's own */
        @Nullable
        InternalInventory view(ECOCraftingPatternBusBlockEntity bus);

        /**
         * How many leading rows of that view are the bus's own writable slots.
         *
         * <p>Exists for an integration that stays clear of this mod's types, so that an older build lacking them
         * still loads: implementing the prefix interface directly would turn a missing one into a load-time
         * failure, whereas a count can simply go unasked. Return the count and the bus wraps the view in its own
         * prefix; return {@code -1} to say the view already keeps its movable rows to itself, which amounts to
         * declaring every row movable.</p>
         *
         * <p>Zero, the default, means nothing is movable. It is the safe direction: a terminal that cannot move
         * anything shows the same rows and loses a shortcut, whereas over-reporting copies display rows out.</p>
         *
         * <p>This governs moving rows in bulk only. Whether one row can be taken out on its own stays with
         * {@code view}: the bus has no way to know which rows are display-only, so it cannot stand in for that
         * judgement.</p>
         */
        default int writableRows(ECOCraftingPatternBusBlockEntity bus) {
            return 0;
        }
    }

    public static void setTerminalInventoryHook(@Nullable TerminalInventoryHook hook) {
        terminalInventoryHook = hook;
    }

    /**
     * Change token for the auxiliary store's contents.
     *
     * <p>Kept separate from {@link #getPatternContentRevision()} on purpose. That value drives the
     * catalog's per-slot delta, and a disk rewriting its contents does not change which item occupies
     * the slot. Folding one into the other would let a slot delta stamp a revision that already covers
     * patterns the catalog has not indexed yet, and the index would then never catch up.</p>
     */
    public long getAuxiliaryRevision() {
        AuxiliaryPatternStore store = auxiliaryPatternStore;
        return store == null ? 0L : store.revision(this);
    }

    /** Whether the bus can still take a pattern without spending a slot. */
    public boolean hasAuxiliaryRoom() {
        AuxiliaryPatternStore store = auxiliaryPatternStore;
        return store != null && store.hasRoom(this);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This is what lets the catalog prefer a disk somewhere on the grid over a free slot on some
     * other bus.</p>
     */
    @Override
    public boolean canAcceptIntoAuxiliary(ItemStack pattern) {
        AuxiliaryPatternStore store = auxiliaryPatternStore;
        return store != null && store.canAccept(this, pattern);
    }

    @Override
    public ECOPatternInsertionResult insertIntoAuxiliary(ItemStack pattern, @Nullable ECOPreparedPattern prepared) {
        AuxiliaryPatternStore store = auxiliaryPatternStore;
        if (store == null) {
            return ECOPatternInsertionResult.NO_TARGET;
        }
        // The acceptance probe is deliberately not repeated here: callers only reach this after
        // canAcceptIntoAuxiliary said yes for the same pattern, and probing again would re-decode every
        // pattern on every disk a second time. A store that changes its mind reports it through the
        // result below, which the caller already treats as "keep looking".
        ECOPreparedPattern toStore = prepared != null ? prepared : preparePattern(pattern);
        if (toStore == null) {
            return ECOPatternInsertionResult.INCOMPATIBLE;
        }
        return store.insert(this, toStore);
    }

    /** Whether an auxiliary store recognises {@code stack} as one of its own containers. */
    public boolean ownsAuxiliary(ItemStack stack) {
        AuxiliaryPatternStore store = auxiliaryPatternStore;
        return store != null && store.owns(this, stack);
    }

    /** Store revision the cached auxiliary lists were derived from. */
    private long auxiliaryDecodeRevision = Long.MIN_VALUE;

    private List<IPatternDetails> auxiliaryPatternDetails = List.of();

    private List<ItemStack> auxiliaryEncodedPatterns = List.of();

    /**
     * Derives the patterns the auxiliary store contributes, cached against the store's revision.
     *
     * <p>A disk holds whatever the encoding terminal wrote to it, processing patterns included, but an
     * ECO worker only runs molecular-assembler patterns. Advertising the rest would aim a crafting job at
     * a route {@code pushPattern} then refuses. The filter also keeps the catalog's counts and the bus's
     * advertised list over exactly the same set, so a pattern can never be counted as present yet be
     * unusable.</p>
     */
    private void refreshAuxiliaryPatterns() {
        AuxiliaryPatternStore store = auxiliaryPatternStore;
        if (store == null) {
            auxiliaryPatternDetails = List.of();
            auxiliaryEncodedPatterns = List.of();
            return;
        }
        long revision = store.revision(this);
        if (revision == auxiliaryDecodeRevision) {
            return;
        }
        // One decode pass feeds both views; the store owns the filter so the advertisement, the network
        // index and any caller asking what a disk publishes all see the same set.
        AuxiliaryPatternStore.ExposedPatterns exposed =
                AuxiliaryPatternStore.exposedPatterns(store, this, level);
        auxiliaryPatternDetails = exposed.details();
        auxiliaryEncodedPatterns = exposed.encoded();
        // Stamped last on purpose: a decoder that throws would otherwise leave the revision marked as
        // already decoded, freezing the cache on the previous contents until the store's revision
        // happens to move again.
        auxiliaryDecodeRevision = revision;
    }

    /** Encoded patterns the auxiliary store contributes to the network. */
    public List<ItemStack> getAuxiliaryEncodedPatterns() {
        refreshAuxiliaryPatterns();
        return auxiliaryEncodedPatterns;
    }

    /**
     * Search keywords for the auxiliary patterns, index-aligned with {@link #getAuxiliaryEncodedPatterns()}.
     *
     * <p>A terminal only finds a recipe through its keywords, so a pattern that reaches the list without them is
     * visible under the unfiltered view and invisible to every search. The slot index keeps these per slot; the
     * auxiliary list needs the same pairing, which is why the decode pass that produces both lists also feeds
     * this one.</p>
     */
    public List<String> getAuxiliarySearchKeywords() {
        refreshAuxiliaryPatterns();
        List<String> keywords = new ArrayList<>(auxiliaryEncodedPatterns.size());
        for (int index = 0; index < auxiliaryEncodedPatterns.size(); index++) {
            IPatternDetails details = index < auxiliaryPatternDetails.size()
                    ? auxiliaryPatternDetails.get(index)
                    : null;
            keywords.add(ECOCraftingPatternBusCatalog.buildPatternSearchKeywords(
                    auxiliaryEncodedPatterns.get(index), details));
        }
        return keywords;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        refreshAuxiliaryPatterns();
        if (auxiliaryPatternDetails.isEmpty()) {
            return catalog.availablePatterns();
        }
        List<IPatternDetails> merged = new ArrayList<>(
            catalog.availablePatterns().size() + auxiliaryPatternDetails.size());
        merged.addAll(catalog.availablePatterns());
        merged.addAll(auxiliaryPatternDetails);
        return merged;
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

    /**
     * The bus's real pattern slots, pattern disks included.
     *
     * <p>Separate from {@link #getTerminalPatternInventory()} on purpose. That one is the pattern access
     * terminal's contract and hides the disks, whereas everything that has to see the slots as they are - the
     * auxiliary store's disk scan, the catalog's index, this bus's own slot accessors - reads this one. Both
     * used to be one method, which made the terminal's display semantics leak into the write paths: a disk
     * slot then looked empty to them and got overwritten.</p>
     */
    public InternalInventory getPatternSlotInventory() {
        return effectiveInventory;
    }

    /**
     * Takes one pattern back out of an auxiliary container, settling whatever the store says it costs.
     *
     * <p>For the management screens: a container's recipes are listed beside the bus's own, and a pattern
     * that went into the network's index came out of a blank, so taking one back out has to pay for it. Only
     * the store can do that, which is why this hands the whole decision over rather than clearing the slot
     * here and leaving the cost to whoever asked.</p>
     *
     * @return whether the pattern was taken; {@code false} leaves the container untouched
     * @see AuxiliaryPatternStore#remove
     */
    public boolean removeAuxiliaryPattern(int containerSlot, ItemStack encodedPattern) {
        AuxiliaryPatternStore store = auxiliaryPatternStore;
        return store != null && !encodedPattern.isEmpty()
                && store.remove(this, containerSlot, encodedPattern);
    }

    /**
     * The rows the terminal view appends after the real slots.
     *
     * <p>A function rather than a call spelled out in the view itself, so a bus variant can widen or narrow what
     * the terminal shows without touching how the view is sized or frozen. The default is what the auxiliary
     * store publishes, which is the containers' recipes.</p>
     */
    protected List<ItemStack> terminalAppendedRows() {
        return getAuxiliaryEncodedPatterns();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        TerminalInventoryHook hook = terminalInventoryHook;
        if (hook != null) {
            // Cached against the same revision the bus's own view uses, and for the same end: the terminal must
            // keep seeing one view for the length of a session. Holding it here rather than inside the hook is
            // what ties the view's lifetime to this bus - a hook that cached per bus would keep every bus it was
            // ever asked about alive, and the bus it belongs to is the only thing that knows when to let go.
            long revision = getAuxiliaryRevision();
            if (hookedView == null || hookedViewRevision != revision) {
                hookedViewRevision = revision;
                InternalInventory supplied = hook.view(this);
                hookedView = supplied == null ? null : withWritableRows(supplied, hook.writableRows(this));
            }
            if (hookedView != null) {
                return hookedView;
            }
        }
        AuxiliaryPatternStore store = auxiliaryPatternStore;
        long revision = store == null ? 0L : store.revision(this);
        if (revision != terminalViewRevision) {
            // A fresh view per revision, the same trade the disk-backed provider makes: a session already open
            // keeps the instance it was handed and its row count stays frozen against it, while the next
            // session gets one built for the disks as they are now. Handing out one long-lived view instead
            // would freeze the row count for the whole life of the block entity, so a recipe written to a disk
            // would not show up until the chunk was reloaded.
            terminalViewRevision = revision;
            terminalPatternInventory = new TerminalPatternInventory();
        }
        return terminalPatternInventory;
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
        // An auxiliary store (pattern disks) takes precedence over slot storage: the pattern is already
        // being carried by the bus, and spending a slot on it would consume capacity for nothing.
        AuxiliaryPatternStore store = auxiliaryPatternStore;
        if (store != null && store.canAccept(this, itemStack)) {
            ECOPatternInsertionResult stored = store.insert(this, prepared);
            if (stored == ECOPatternInsertionResult.INSERTED) {
                // No revision bump here: a store writes through the bus's own inventory, which notifies
                // its host, and the catalog picks the disk up from getAuxiliaryRevision() either way.
                return stored;
            }
            if (stored == ECOPatternInsertionResult.ALREADY_PRESENT) {
                return stored;
            }
            // Any other outcome means the store did not take it after all; the slot inventory stays
            // the fallback, exactly as if the store had not been consulted.
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
        for (ItemStack storedPattern : getAuxiliaryEncodedPatterns()) {
            if (ItemStack.isSameItemSameComponents(storedPattern, pattern)) {
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
        controls.addChild(pageButton("<", () -> changePage(visiblePage() - 1)).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(PAGE_BUTTON_SIZE);
            layout.height(PAGE_BUTTON_SIZE);
        }));
        TextElement pageNumber = new TextElement()
            .setText(Component.literal((visiblePage() + 1) + "/" + getPageCount()));
        controls.addChild(pageNumber.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(PAGE_BUTTON_SIZE + PAGE_CONTROL_GAP);
            layout.top(0);
            layout.width(PAGE_LABEL_WIDTH);
            layout.height(PAGE_BUTTON_SIZE);
        }));
        controls.addChild(pageButton(">", () -> changePage(visiblePage() + 1)).layout(layout -> {
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
        int pages = clampPages(Math.max(
            NEConfig.getCraftingPatternBusPages(),
            catalog.highestOccupiedSlot() < 0 ? 1 : catalog.highestOccupiedSlot() / SLOTS_PER_PAGE + 1
        ));
        // 只在变化时写：本方法被每一次槽位查找调用，读一次就写一次会让这个同步字段来回改。
        if (activePages != pages) {
            activePages = pages;
        }
        currentPage = Math.clamp(currentPage, 0, activePages - 1);
        return activePages;
    }

    /**
     * The page the UI is on, clamped to the pages that currently exist.
     *
     * <p>Slot lookups and the paging buttons both go through this, so a visible index always resolves to the
     * same physical slot on either side.</p>
     */
    private int visiblePage() {
        return Math.clamp(currentPage, 0, getPageCount() - 1);
    }

    public int getPatternSlotCount() {
        return getPageCount() * SLOTS_PER_PAGE;
    }

    private void changePage(int targetPage) {
        int pageCount = getPageCount();
        int clamped = Math.clamp(targetPage, 0, pageCount - 1);
        if (clamped == visiblePage()) {
            return;
        }
        currentPage = clamped;
        setChanged();
        markForUpdate();
    }

    private static int clampPages(int pages) {
        return Math.clamp(pages, NEConfig.PATTERN_BUS_MIN_PAGES, NEConfig.PATTERN_BUS_MAX_PAGES);
    }

    /**
     * The view the pattern access terminal lists.
     *
     * <p>A disk is not a pattern: listed as one it is a stack the terminal cannot decode, and the recipes
     * inside it - the reason it is there at all - appear nowhere. So the disk's slot renders empty and the
     * recipes it publishes are appended after the slots. Appending rather than compacting keeps every index
     * mapping straight onto the real inventory.</p>
     *
     * <p>This is a display contract only. Nothing that touches the slots may read it - see
     * {@link #getPatternSlotInventory()}.</p>
     */
    /** Store revision the current terminal view was built from. */
    /**
     * Gives a supplied view the writable prefix the terminal reads, when it does not carry one itself.
     *
     * <p>An integration that reaches this class by reflection has no way to implement the interface that
     * declares that prefix, so the count is taken from the hook and wrapped on here. A view that declares it
     * itself is returned untouched, and so is one claiming no movable rows: a terminal that cannot move anything
     * loses a shortcut, whereas one that assumes every row is movable copies the display rows into a player's
     * inventory.</p>
     */
    private InternalInventory withWritableRows(InternalInventory view, int writableRows) {
        if (view instanceof cn.dancingsnow.neoecoae.util.WritablePrefix || writableRows < 0) {
            return view;
        }
        int rows = Math.max(0, Math.min(writableRows, view.size()));
        // One wrapper per supplied view, reused: the base class promises that the platform adapter it hands out
        // keeps its identity over time, and a fresh wrapper on every query would break that for a terminal that
        // asks repeatedly.
        if (view == wrappedViewSource && wrappedView instanceof WritableRowsView cached && cached.writableRows == rows) {
            return cached;
        }
        WritableRowsView wrapped = new WritableRowsView(view, rows);
        wrappedViewSource = view;
        wrappedView = wrapped;
        return wrapped;
    }

    @Nullable
    private transient InternalInventory wrappedViewSource;

    @Nullable
    private transient InternalInventory wrappedView;

    /** The view an integration supplied, kept until the containers' revision moves. */
    @Nullable
    private transient InternalInventory hookedView;

    private long hookedViewRevision = Long.MIN_VALUE;

    /** Delegates everything and narrows only the rows the terminal is allowed to move. */
    private static final class WritableRowsView extends BaseInternalInventory
            implements cn.dancingsnow.neoecoae.util.WritablePrefix {

        private final InternalInventory delegate;
        private final int writableRows;

        private WritableRowsView(InternalInventory delegate, int writableRows) {
            this.delegate = delegate;
            this.writableRows = writableRows;
        }

        @Override
        public int writableSlotCount() {
            return writableRows;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            delegate.setItemDirect(slot, stack);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return delegate.isItemValid(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            // Delegated rather than left to the default: a bus slot takes one pattern, and the default would let
            // a caller stack a full 64 into it before the view ever sees them.
            return delegate.getSlotLimit(slot);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return delegate.extractItem(slot, amount, simulate);
        }
    }

    protected long terminalViewRevision = Long.MIN_VALUE;

    private TerminalPatternInventory terminalPatternInventory = new TerminalPatternInventory();

    protected final class TerminalPatternInventory extends BaseInternalInventory
            implements cn.dancingsnow.neoecoae.util.WritablePrefix {

        /**
         * The slot count as it was when this view was built.
         *
         * <p>Fixed for the same reason the appended rows are: a pattern access terminal sizes its mirror from
         * whatever the container reported when the session opened and then keeps indexing it against the
         * container's current size, so a bus that grows a page mid-session walks off the end of that mirror. The
         * page shows up from the next session on.</p>
         */
        private final int slotCount = getPatternSlotCount();

        /** Only the real slots can be written to; the appended disk recipes are display-only. */
        @Override
        public int writableSlotCount() {
            return slotCount;
        }

        /** Display-only rows, frozen when this view was first asked for them. */
        private List<ItemStack> appendedRows;

        private List<ItemStack> rows() {
            if (appendedRows == null) {
                // A subclass may return null to mean "append nothing", which is a reasonable thing to want and
                // would otherwise fail here while a screen is being drawn.
                List<ItemStack> fromSubclass = terminalAppendedRows();
                appendedRows = fromSubclass == null ? List.of() : List.copyOf(fromSubclass);
            }
            return appendedRows;
        }

        @Override
        public int size() {
            return slotCount + rows().size();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0) {
                return ItemStack.EMPTY;
            }
            if (slot < slotCount) {
                ItemStack stack = inventory.getStackInSlot(slot);
                return ownsAuxiliary(stack) ? ItemStack.EMPTY : stack;
            }
            List<ItemStack> exposed = rows();
            int index = slot - slotCount;
            // A copy: the frozen list outlives this call, and handing out the live stack lets a caller writing to
            // it corrupt every later read.
            return index < exposed.size() ? exposed.get(index).copy() : ItemStack.EMPTY;
        }

        /**
         * Whether a row is one the terminal shows but must not act on: a disk's slot, or an appended disk
         * recipe. A hidden row reads as empty, so a write aimed at it replaces whatever is really there -
         * clicking the blank row where a disk sits would delete the disk and every recipe on it.
         */
        private boolean hidden(int slot) {
            return slot < 0 || slot >= slotCount || ownsAuxiliary(inventory.getStackInSlot(slot));
        }

        /**
         * Extraction reaches the terminal through the slot view while display goes through
         * {@link #getStackInSlot}. Nothing here can be taken out: a disk's own contents would have to draw a
         * blank pattern and delete the entry from the disk, and taking a disk out stays a bus-GUI action.
         */
        @Override
        public InternalInventory getSlotInv(int slot) {
            return hidden(slot) ? InternalInventory.empty() : super.getSlotInv(slot);
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            if (!hidden(slot)) {
                inventory.setItemDirect(slot, stack);
            }
        }

        /**
         * The default extraction reads through {@link #getStackInSlot} and clears through
         * {@link #setItemDirect} - on a hidden row that hands the stack out and then fails to remove it, i.e. it
         * duplicates. Nothing here is extractable, so the row answers before the default can run.
         */
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return hidden(slot) ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !hidden(slot) && inventory.isItemValid(slot, stack);
        }
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
                // Refusing silently would drop the write while the caller believes it succeeded, which
                // is how a pattern disk can end up duplicated into the player inventory while staying in
                // the bus. Out-of-range visible slots are a caller bug, so say so instead of swallowing it.
                LOGGER.warn("[PatternBus] at {} refused a write to out-of-range pattern slot {}", worldPosition, slot);
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
            int page = visiblePage();
            int actualSlot = page * SLOTS_PER_PAGE + visibleSlot;
            return actualSlot < getPatternSlotCount() ? actualSlot : -1;
        }
    }

}
