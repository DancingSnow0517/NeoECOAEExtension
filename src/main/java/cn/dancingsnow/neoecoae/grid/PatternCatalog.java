package cn.dancingsnow.neoecoae.grid;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.core.definitions.AEItems;
import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternContainer;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertion;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPatternSourceSlot;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.Comparator;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Grid-owned source of truth for ECO and external pattern locations, key counts and free capacity.
 * Migration, organization and preview consume this catalog instead of maintaining private scans.
 */
public class PatternCatalog implements IECOPatternStorageService, IGridServiceProvider {

    /** A guard against pathological inventories; elapsed time remains the real throttle. */
    private static final int EXTERNAL_PATTERN_INDEX_SAFETY_LIMIT = 1024;
    private static final long EXTERNAL_PATTERN_INDEX_NANOS_PER_TICK = 2_000_000L;
    private static final int EXTERNAL_PATTERN_INDEX_MAX_AGE_TICKS = 100;
    private static final long EXTERNAL_PATTERN_CLAIM_TIMEOUT_TICKS = 200L;

    private final Map<IGridNode, IECOPatternStorage> patternStorages = new IdentityHashMap<>();
    private final IECOPatternStorage combinedStorage = new IECOPatternStorage() {
        @Override
        public boolean insertPattern(ItemStack patternItem) {
            return tryInsertPattern(patternItem) == ECOPatternInsertionResult.INSERTED;
        }

        @Override
        public ECOPatternInsertionResult insertPatternWithResult(ItemStack patternItem) {
            return tryInsertPattern(patternItem);
        }

        @Override
        public ECOPatternInsertionResult insertPreparedPattern(ECOPreparedPattern prepared) {
            return PatternCatalog.this.insertPreparedPattern(prepared);
        }
    };
    private final Map<ECOCraftingPatternBusBlockEntity, Object2IntOpenHashMap<AEItemKey>> busPatternKeys =
            new IdentityHashMap<>();
    private final Reference2IntOpenHashMap<ECOCraftingPatternBusBlockEntity> busPatternRevisions =
            new Reference2IntOpenHashMap<>();
    private final Reference2IntOpenHashMap<ECOCraftingPatternBusBlockEntity> busEmptySlotCounts =
            new Reference2IntOpenHashMap<>();
    private final Map<ECOCraftingPatternBusBlockEntity, Int2ObjectMap<PatternRecord>> busPatternRecords =
            new IdentityHashMap<>();
    private final Map<AEItemKey, Set<PatternLocation>> patternLocationsByKey = new Object2ObjectOpenHashMap<>();
    private final Object2IntOpenHashMap<AEItemKey> networkPatternCounts = new Object2IntOpenHashMap<>();
    /**
     * Auxiliary (non-slot) pattern counts per bus, the counterpart of {@link #busPatternKeys}.
     *
     * <p>Kept apart because the slot deltas applied by {@link #onPatternSlotsChanged} assume one count
     * per physical slot; folding disk-held patterns into the same map would desync those deltas.</p>
     */
    private final Map<ECOCraftingPatternBusBlockEntity, Object2IntOpenHashMap<AEItemKey>> busAuxiliaryPatternKeys =
            new IdentityHashMap<>();
    /** Store revision each bus's auxiliary counts were built from. */
    private final Reference2LongOpenHashMap<ECOCraftingPatternBusBlockEntity> busAuxiliaryRevisions =
            new Reference2LongOpenHashMap<>();
    /**
     * Slots currently holding an auxiliary container.
     *
     * <p>Disks are deliberately absent from {@link #busPatternRecords}, so the per-slot delta's
     * "no record = was empty" shortcut does not hold for them; these slots let that delta notice a disk
     * changing hands and fall back to a full re-derivation.</p>
     */
    private final Map<ECOCraftingPatternBusBlockEntity, BitSet> busAuxiliarySlots = new IdentityHashMap<>();
    private List<IECOPatternStorage> writablePatternStorages = List.of();
    private boolean writablePatternStorageCacheInitialized;
    private long patternCapacityGeneration;
    @Nullable
    private IECOPatternStorage preferredStorage;
    @Nullable
    private IGrid externalPatternIndexGrid;
    private final Map<PatternContainer, BitSet> externalPatternSlots = new IdentityHashMap<>();
    private final Map<PatternContainer, Int2ObjectMap<PatternRecord>> externalPatternRecords =
            new IdentityHashMap<>();
    private List<PatternContainer> externalPatternSources = List.of();
    private final Map<PatternContainer, BitSet> externalCraftingHistory = new IdentityHashMap<>();
    private final Map<PatternContainer, BitSet> externalPreferredBySource = new IdentityHashMap<>();
    private final ArrayDeque<ECOPatternSourceSlot> externalAvailable = new ArrayDeque<>();
    private BitSet externalPreferredSlots = new BitSet();
    private int externalPreferredSlot;
    private boolean externalScanningPreferred = true;
    private boolean externalPreferredPass = true;
    private int externalPatternSourceIndex;
    private int externalPatternSlotIndex;
    private int externalPatternScannedSlots;
    private int externalPatternTotalSlots;
    private int externalPatternIndexAge;
    private boolean externalPatternIndexBuilding;
    private boolean externalPatternIndexDirty = true;
    private long externalPatternLastScanNanos;
    private long externalPatternScanNanos;
    private int externalPatternScanBudgetHits;
    private long externalPatternTick;
    private final Map<ECOPatternSourceSlot, UUID> externalPatternClaims = new Object2ObjectOpenHashMap<>();
    private final Map<UUID, Set<ECOPatternSourceSlot>> externalPatternClaimsByOwner = new Object2ObjectOpenHashMap<>();
    private final Object2LongOpenHashMap<UUID> externalPatternClaimTicks = new Object2LongOpenHashMap<>();
    private long providerPublicationRevision;

    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        IGridServiceProvider.super.addNode(gridNode, savedData);
        invalidateExternalPatternIndex();
        IECOPatternStorage service = gridNode.getService(IECOPatternStorage.class);
        if (service == null) return;
        patternStorages.put(gridNode, service);
        refreshPatternIndexes();
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        IGridServiceProvider.super.removeNode(gridNode);
        IECOPatternStorage removed = patternStorages.remove(gridNode);
        if (removed == preferredStorage) {
            preferredStorage = null;
        }
        if (removed instanceof ECOCraftingPatternBusBlockEntity bus) {
            removeBusPatternIndex(bus);
        }
        rebuildWritablePatternStorageCache();
        invalidateExternalPatternIndex();
    }

    public ECOPatternInsertionResult tryInsertPattern(ItemStack patternItem) {
        if (patternItem.isEmpty()) {
            return ECOPatternInsertionResult.INCOMPATIBLE;
        }

        return tryInsertPatternInternal(patternItem, null);
    }

    @Override
    public ECOPatternInsertionResult insertPreparedPattern(ECOPreparedPattern prepared) {
        if (prepared == null || prepared.stack().isEmpty()
                || !(prepared.details() instanceof IMolecularAssemblerSupportedPattern)) {
            return ECOPatternInsertionResult.INCOMPATIBLE;
        }
        return tryInsertPatternInternal(prepared.stack(), prepared);
    }

    /**
     * Like {@link #insertPreparedPattern}, but reports whether a container absorbed the recipe.
     *
     * <p>A caller that clears the source pattern after a successful insertion needs this: routing into a
     * slot and routing into a container are indistinguishable in the plain result, yet only the container
     * consumes the pattern's item.</p>
     */
    @Override
    public ECOPatternInsertion insertPreparedPatternReporting(ECOPreparedPattern prepared) {
        if (prepared == null || prepared.stack().isEmpty()
                || !(prepared.details() instanceof IMolecularAssemblerSupportedPattern)) {
            return ECOPatternInsertion.of(ECOPatternInsertionResult.INCOMPATIBLE);
        }
        ItemStack patternItem = prepared.stack();
        if (!writablePatternStorageCacheInitialized) {
            refreshPatternIndexes();
        }
        AEItemKey patternKey = AEItemKey.of(patternItem);
        if (patternKey != null && networkPatternCounts.containsKey(patternKey)) {
            return ECOPatternInsertion.of(ECOPatternInsertionResult.ALREADY_PRESENT);
        }
        // The container pass is the only one that consumes the pattern's item; a slot keeps it as a stack.
        ECOPatternInsertionResult auxiliary = tryAuxiliaryPass(patternItem, prepared);
        if (auxiliary == ECOPatternInsertionResult.INSERTED) {
            return new ECOPatternInsertion(ECOPatternInsertionResult.INSERTED, true,
                    blankPatternReplacementFor(patternItem));
        }
        if (auxiliary == ECOPatternInsertionResult.ALREADY_PRESENT) {
            return ECOPatternInsertion.of(ECOPatternInsertionResult.ALREADY_PRESENT);
        }
        // The container pass already ran and declined, so the slot path must not run it a second time: a
        // second write window could absorb the pattern while this method reports it as slot-held.
        return ECOPatternInsertion.of(tryInsertPatternInternal(patternItem, prepared, true));
    }

    /**
     * A blank pattern is what an encoded pattern becomes once a container took its recipe: the recipe is
     * stored, the item is not. Returning the same item the disk-encoding path returns keeps the two ways of
     * storing a pattern consistent.
     */
    @Override
    public ItemStack blankPatternReplacementFor(ItemStack pattern) {
        return PatternRefund.blankFor(pattern);
    }

    /**
     * Routes {@code patternItem} into a container - a pattern disk - before any slot gets a chance.
     *
     * <p>A pattern that belongs on a disk has to reach one first: the slot loop below hands it to whichever
     * bus exposes a free slot, which both spends that slot and leaves the disk empty - and once the pattern
     * sits in a slot, removing the disk can no longer take it back out.</p>
     *
     * @return the outcome, or {@code null} when no container took it and the slot path should run
     */
    /**
     * Offers a pattern to the storage buses' disks alone, never to a slot.
     *
     * <p>Kept apart from {@link #insertPreparedPatternReporting} because that one falls back to a slot, and a
     * caller that is about to clear its own source slot cannot have the pattern handed back into a slot - it
     * could be the very one being cleared, and the clear would then destroy what was just stored.</p>
     *
     * @return the outcome, or {@code null} when no disk took it and the caller should carry on without it
     */
    @Nullable
    public ECOPatternInsertionResult insertPatternIntoAuxiliaryOnly(ItemStack patternItem,
                                                                   @Nullable ECOPreparedPattern prepared) {
        return tryAuxiliaryPass(patternItem, prepared);
    }

    @Nullable
    private ECOPatternInsertionResult tryAuxiliaryPass(ItemStack patternItem, @Nullable ECOPreparedPattern prepared) {
        // Two rounds, so a disk already locked to this pattern's type takes it before an empty disk can. A disk
        // locked to some other type refuses the pattern outright, so a bus whose disks hold patterns is either
        // a match or not a candidate at all - which is what lets "already holds patterns" stand in for "its disk
        // is the right type" without asking the store to describe its disks.
        //
        // The first round needs to exist because the second one alone picks whichever bus the identity-ordered
        // candidate list happens to yield first: an empty disk would take the pattern and lock itself to it,
        // leaving a matching disk elsewhere unreachable.
        ECOPatternInsertionResult onHoldingBus = insertIntoCandidateBuses(patternItem, prepared, true);
        if (onHoldingBus != null) {
            return onHoldingBus;
        }
        return insertIntoCandidateBuses(patternItem, prepared, false);
    }

    /**
     * Offers {@code patternItem} to the storage buses, optionally restricted to those already holding patterns.
     *
     * @return the outcome, or {@code null} when no candidate took it and the slot path should run
     */
    @Nullable
    private ECOPatternInsertionResult insertIntoCandidateBuses(ItemStack patternItem,
                                                               @Nullable ECOPreparedPattern prepared,
                                                               boolean requireHeldPatterns) {
        for (IECOPatternStorage value : writablePatternStorages) {
            if (requireHeldPatterns && !holdsAuxiliaryPatterns(value)) {
                continue;
            }
            if (!value.canAcceptIntoAuxiliary(patternItem)) {
                continue;
            }
            switch (value.insertIntoAuxiliary(patternItem, prepared)) {
                case INSERTED -> {
                    preferredStorage = value;
                    return ECOPatternInsertionResult.INSERTED;
                }
                case ALREADY_PRESENT -> {
                    // Forward compatibility: no current implementation returns it from the auxiliary write.
                    return ECOPatternInsertionResult.ALREADY_PRESENT;
                }
                default -> {
                    // Either the store stopped accepting between the probe and the write, or this bus cannot
                    // execute the pattern at all (INCOMPATIBLE). Both leave it to the slot loop below.
                }
            }
        }
        return null;
    }

    /**
     * @return whether the bus's disks already hold patterns, which is what makes them worth preferring
     *
     * <p>Known limit: the judgement is per bus, not per disk. A bus holding a disk of another type next to an
     * empty one still counts as holding, so that bus's empty disk can take the pattern before a matching disk on
     * another bus is ever reached. The pattern still lands on a disk that accepts it - nothing is mismatched or
     * lost - but the preference across buses can be missed for that layout. Closing the gap needs the store to
     * answer at disk granularity rather than bus granularity.</p>
     */
    private boolean holdsAuxiliaryPatterns(IECOPatternStorage storage) {
        return storage instanceof ECOCraftingPatternBusBlockEntity bus
                && !bus.getAuxiliaryEncodedPatterns().isEmpty();
    }

    /**
     * Every recipe the network's pattern storage exposes right now.
     *
     * <p>Deliberately assembled from each bus's own advertised list instead of re-deriving one here: what a
     * bus hands to autocrafting <em>is</em> what the network exposes, container-held patterns included, so
     * going through it is the only way this answer cannot disagree with what autocrafting sees.</p>
     */
    @Override
    public List<IPatternDetails> getExposedPatterns() {
        refreshPatternIndexes();
        List<IPatternDetails> exposed = new ArrayList<>();
        for (IECOPatternStorage storage : patternStorages.values()) {
            if (storage instanceof ECOCraftingPatternBusBlockEntity bus) {
                exposed.addAll(bus.getAvailablePatterns());
            }
        }
        return List.copyOf(exposed);
    }

    private ECOPatternInsertionResult tryInsertPatternInternal(ItemStack patternItem,
                                                                 @Nullable ECOPreparedPattern prepared) {
        return tryInsertPatternInternal(patternItem, prepared, false);
    }

    private ECOPatternInsertionResult tryInsertPatternInternal(ItemStack patternItem,
                                                                 @Nullable ECOPreparedPattern prepared,
                                                                 boolean auxiliaryAlreadyTried) {
        if (prepared != null && !prepared.matches(patternItem)) {
            return ECOPatternInsertionResult.INCOMPATIBLE;
        }

        if (!writablePatternStorageCacheInitialized) {
            refreshPatternIndexes();
        }
        AEItemKey patternKey = AEItemKey.of(patternItem);
        if (patternKey != null && networkPatternCounts.containsKey(patternKey)) {
            return ECOPatternInsertionResult.ALREADY_PRESENT;
        }

        boolean noSpace = false;
        // The catalog is initialized once and then maintained by slot/batch deltas. A non-null key
        // absent from it is already proven unique, so no destination may rescan its cluster.
        boolean uniquenessChecked = patternKey != null;

        ECOPatternInsertionResult auxiliary = auxiliaryAlreadyTried
                ? null
                : tryAuxiliaryPass(patternItem, prepared);
        if (auxiliary != null) {
            return auxiliary;
        }

        if (preferredStorage instanceof ECOCraftingPatternBusBlockEntity) {
            ECOPatternInsertionResult result = insertIntoStorage(
                    preferredStorage, patternItem, prepared, uniquenessChecked);
            switch (result) {
                case INSERTED -> {
                    return ECOPatternInsertionResult.INSERTED;
                }
                case ALREADY_PRESENT -> {
                    return ECOPatternInsertionResult.ALREADY_PRESENT;
                }
                case NO_SPACE -> {
                    noSpace = true;
                    uniquenessChecked = preferredStorage.checksLogicalDomainForDuplicates();
                    markStorageFull(preferredStorage);
                }
                default -> {
                }
            }
        }
        // Pattern migration is specifically an ECO pattern-bus operation. Do not fall back to
        // arbitrary IECOPatternStorage implementations when all buses are full: those services
        // may be backed by a storage drive or another item inventory and must never receive an
        // encoded pattern as a side effect of organizing patterns.
        for (IECOPatternStorage value : writablePatternStorages) {
            if (value == preferredStorage) {
                continue;
            }
            ECOPatternInsertionResult result = insertIntoStorage(value, patternItem, prepared, uniquenessChecked);
            switch (result) {
                case INSERTED -> {
                    preferredStorage = value;
                    return ECOPatternInsertionResult.INSERTED;
                }
                case ALREADY_PRESENT -> {
                    return ECOPatternInsertionResult.ALREADY_PRESENT;
                }
                case NO_SPACE -> {
                    noSpace = true;
                    markStorageFull(value);
                    if (value.checksLogicalDomainForDuplicates()) {
                        // This target checked the entire logical domain before reporting NO_SPACE.
                        // All subsequent targets can therefore use the no-duplicate fast path.
                        uniquenessChecked = true;
                    }
                }
                default -> {
                }
            }
        }
        return noSpace ? ECOPatternInsertionResult.NO_SPACE : ECOPatternInsertionResult.NO_TARGET;
    }

    private static ECOPatternInsertionResult insertIntoStorage(IECOPatternStorage storage,
                                                                ItemStack pattern,
                                                                @Nullable ECOPreparedPattern prepared,
                                                                boolean knownUnique) {
        if (prepared != null) {
            return knownUnique
                    ? storage.insertPreparedPatternKnownUnique(prepared)
                    : storage.insertPreparedPattern(prepared);
        }
        return knownUnique
                ? storage.insertPatternKnownUnique(pattern)
                : storage.insertPatternWithResult(pattern);
    }

    @Override
    public IECOPatternStorage getPatternStorage() {
        return combinedStorage;
    }

    @Override
    public boolean containsPatternInNetwork(ItemStack pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        refreshPatternIndexes();
        AEItemKey key = AEItemKey.of(pattern);
        return key != null && networkPatternCounts.containsKey(key);
    }

    @Override
    public long getPatternCapacityGeneration() {
        refreshPatternIndexes();
        return patternCapacityGeneration;
    }

    public long currentProviderPublicationRevision() {
        return providerPublicationRevision;
    }

    /** Occupied ECO slots only, ordered exactly like the network browser's physical layout. */
    public List<PatternRecord> occupiedPatterns() {
        refreshPatternIndexes();
        List<PatternRecord> result = new ArrayList<>();
        for (Map.Entry<ECOCraftingPatternBusBlockEntity, Int2ObjectMap<PatternRecord>> entry
                : busPatternRecords.entrySet()) {
            ECOCraftingPatternBusBlockEntity bus = entry.getKey();
            bus.refreshPatternDetailsForCatalog();
            for (int slot : entry.getValue().keySet()) {
                ItemStack stack = bus.getPatternSlotInventory().getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    result.add(createRecord(bus, slot, stack));
                }
            }
        }
        result.sort(Comparator
                .comparingLong((PatternRecord record) -> java.util.Objects.requireNonNull(
                        record.location().bus()).getBlockPos().asLong())
                .thenComparingInt(record -> record.location().physicalSlot()));
        return List.copyOf(result);
    }

    @Nullable
    public PatternRecord getPatternRecord(ECOCraftingPatternBusBlockEntity bus, int physicalSlot) {
        Int2ObjectMap<PatternRecord> records = busPatternRecords.get(bus);
        if (records == null || !records.containsKey(physicalSlot)) {
            return null;
        }
        ItemStack stack = bus.getPatternSlotInventory().getStackInSlot(physicalSlot);
        return stack.isEmpty() ? null : createRecord(bus, physicalSlot, stack);
    }

    public void refresh() {
        refreshPatternIndexes();
    }

    public Set<PatternLocation> locationsForKey(AEItemKey key) {
        Set<PatternLocation> locations = patternLocationsByKey.get(key);
        return locations == null ? Set.of() : Set.copyOf(locations);
    }

    /**
     * Whether the key exists anywhere in the network except one physical bus slot.
     *
     * <p>The player quick-insert path needs both ordinary/external slot locations and auxiliary containers. The
     * latter contribute to {@link #networkPatternCounts} but deliberately do not have a physical
     * {@link PatternLocation}, so checking that location index alone can admit a duplicate of a disk pattern.</p>
     */
    public boolean containsPatternOtherThan(ECOCraftingPatternBusBlockEntity targetBus, int targetSlot,
                                             AEItemKey key) {
        refreshPatternIndexes();
        Set<PatternLocation> locations = patternLocationsByKey.get(key);
        if (locations != null) {
            for (PatternLocation location : locations) {
                if (location.bus() != targetBus || location.physicalSlot() != targetSlot) {
                    return true;
                }
            }
        }
        for (Object2IntOpenHashMap<AEItemKey> auxiliaryCounts : busAuxiliaryPatternKeys.values()) {
            if (auxiliaryCounts.getInt(key) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Auxiliary pattern keys, refreshed from their store revisions before returning. */
    public Set<AEItemKey> auxiliaryPatternKeys() {
        refreshPatternIndexes();
        Set<AEItemKey> keys = new HashSet<>();
        for (Object2IntOpenHashMap<AEItemKey> counts : busAuxiliaryPatternKeys.values()) {
            keys.addAll(counts.keySet());
        }
        return Set.copyOf(keys);
    }

    private static PatternRecord createRecord(ECOCraftingPatternBusBlockEntity bus,
                                               int slot,
                                               ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        IPatternDetails details = bus.getDecodedPatternDetails(slot);
        PatternType type = details instanceof IMolecularAssemblerSupportedPattern
                ? PatternType.CRAFTING
                : details == null ? PatternType.INVALID : PatternType.UNSUPPORTED;
        return new PatternRecord(
                new PatternLocation(bus, slot),
                key,
                stack.copy(),
                details,
                type,
                details instanceof IMolecularAssemblerSupportedPattern,
                bus.getPatternSearchKeywords(slot));
    }

    public enum PatternType {
        CRAFTING,
        UNSUPPORTED,
        INVALID,
        EXTERNAL_ENCODED
    }

    public record PatternLocation(PatternContainer container, int physicalSlot) {
        public boolean eco() {
            return container instanceof ECOCraftingPatternBusBlockEntity;
        }

        @Nullable
        public ECOCraftingPatternBusBlockEntity bus() {
            return container instanceof ECOCraftingPatternBusBlockEntity bus ? bus : null;
        }
    }

    public record PatternRecord(PatternLocation location,
                                @Nullable AEItemKey key,
                                ItemStack stack,
                                @Nullable IPatternDetails details,
                                PatternType type,
                                boolean supported,
                                String searchKeywords) {
    }

    public void captureProviderPublicationRevision(long capturedRevision) {
        if (capturedRevision > providerPublicationRevision) {
            providerPublicationRevision = capturedRevision;
        }
    }

    public void onPatternStorageMutation(@Nullable IECOPatternStorage source) {
        providerPublicationRevision = providerPublicationRevision == Long.MAX_VALUE
                ? 1L
                : providerPublicationRevision + 1L;
        invalidateExternalPatternIndex();
    }

    @Override
    public ExternalPatternIndexState getExternalPatternIndex(IGrid grid) {
        ensureExternalPatternIndex(grid);
        return externalPatternIndexState(true);
    }

    private void ensureExternalPatternIndex(IGrid grid) {
        if (externalPatternIndexGrid != grid) {
            externalPatternIndexGrid = grid;
            invalidateExternalPatternIndex();
        }
        if (externalPatternIndexDirty && !externalPatternIndexBuilding) {
            beginExternalPatternIndexBuild(grid);
        }
    }

    @Override
    public ExternalPatternClaim claimExternalPatternCandidates(IGrid grid, UUID owner, int maxCandidates) {
        if (owner == null || maxCandidates <= 0) {
            return new ExternalPatternClaim(false, externalPatternScannedSlots, externalPatternTotalSlots, List.of(),
                    externalPatternLastScanNanos, EXTERNAL_PATTERN_INDEX_NANOS_PER_TICK,
                    externalPatternScanBudgetHits, externalPatternScanNanos);
        }
        ensureExternalPatternIndex(grid);
        ExternalPatternIndexState state = externalPatternIndexState(false);
        Set<ECOPatternSourceSlot> owned = externalPatternClaimsByOwner.computeIfAbsent(owner,
                ignored -> new HashSet<>());
        externalPatternClaimTicks.put(owner, externalPatternTick);
        List<ECOPatternSourceSlot> claimed = new ArrayList<>(Math.min(maxCandidates, 64));
        while (claimed.size() < maxCandidates && !externalAvailable.isEmpty()) {
            ECOPatternSourceSlot candidate = externalAvailable.removeFirst();
            BitSet slots = externalPatternSlots.get(candidate.source());
            if (slots == null || !slots.get(candidate.slot()) || externalPatternClaims.containsKey(candidate)) continue;
            externalPatternClaims.put(candidate, owner);
            owned.add(candidate);
            claimed.add(candidate);
        }
        return new ExternalPatternClaim(state.ready(), state.scannedSlots(), state.totalSlots(), List.copyOf(claimed),
                state.lastScanNanos(), state.scanBudgetNanos(), state.scanBudgetHits(), state.totalScanNanos());
    }

    @Override
    public void releaseExternalPatternCandidates(UUID owner) {
        if (owner == null) {
            return;
        }
        Set<ECOPatternSourceSlot> owned = externalPatternClaimsByOwner.remove(owner);
        externalPatternClaimTicks.removeLong(owner);
        if (owned == null) {
            return;
        }
        for (ECOPatternSourceSlot candidate : owned) {
            if (owner.equals(externalPatternClaims.get(candidate))) {
                externalPatternClaims.remove(candidate);
                requeueExternalCandidate(candidate);
            }
        }
    }

    @Override
    public void releaseExternalPatternCandidate(ECOPatternSourceSlot slot) {
        if (slot == null) {
            return;
        }
        UUID owner = externalPatternClaims.remove(slot);
        if (owner == null) {
            return;
        }
        requeueExternalCandidate(slot);
        Set<ECOPatternSourceSlot> owned = externalPatternClaimsByOwner.get(owner);
        if (owned != null) {
            owned.remove(slot);
            if (owned.isEmpty()) {
                externalPatternClaimsByOwner.remove(owner);
                externalPatternClaimTicks.removeLong(owner);
            }
        }
    }

    @Override
    public void removeExternalPatternCandidate(ECOPatternSourceSlot slot) {
        BitSet slots = externalPatternSlots.get(slot.source());
        if (slots != null) {
            slots.clear(slot.slot());
        }
        Int2ObjectMap<PatternRecord> records = externalPatternRecords.get(slot.source());
        if (records != null) {
            removeLocation(records.remove(slot.slot()));
        }
        UUID owner = externalPatternClaims.remove(slot);
        if (owner != null) {
            Set<ECOPatternSourceSlot> owned = externalPatternClaimsByOwner.get(owner);
            if (owned != null) {
                owned.remove(slot);
                if (owned.isEmpty()) {
                    externalPatternClaimsByOwner.remove(owner);
                    externalPatternClaimTicks.removeLong(owner);
                }
            }
        }
    }

    @Override
    public void onServerEndTick() {
        externalPatternTick++;
        if (!externalPatternClaimTicks.isEmpty()) {
            List<UUID> expired = new ArrayList<>();
            for (var entry : externalPatternClaimTicks.object2LongEntrySet()) {
                if (externalPatternTick - entry.getLongValue() >= EXTERNAL_PATTERN_CLAIM_TIMEOUT_TICKS) {
                    expired.add(entry.getKey());
                }
            }
            for (UUID owner : expired) {
                releaseExternalPatternCandidates(owner);
            }
        }
        refreshPatternIndexes();
        if (externalPatternIndexBuilding) {
            scanExternalPatternIndex();
        } else if (!externalPatternIndexDirty && ++externalPatternIndexAge >= EXTERNAL_PATTERN_INDEX_MAX_AGE_TICKS) {
            // Third-party PatternContainers do not expose a common inventory-change callback. Periodic invalidation
            // keeps newly inserted patterns discoverable without putting a full network scan on each migration.
            externalPatternIndexDirty = true;
        }
    }

    private void invalidateExternalPatternIndex() {
        externalPatternIndexDirty = true;
        externalPatternIndexBuilding = false;
        externalPatternIndexAge = 0;
        externalPatternSlots.clear();
        externalAvailable.clear();
        clearExternalPatternRecords();
        externalPatternSources = List.of();
        externalPreferredBySource.clear();
        externalPatternSourceIndex = 0;
        externalPatternSlotIndex = 0;
        externalPatternScannedSlots = 0;
        externalPatternTotalSlots = 0;
        externalPatternLastScanNanos = 0L;
        externalPatternClaims.clear();
        externalPatternClaimsByOwner.clear();
        externalPatternClaimTicks.clear();
    }

    private void beginExternalPatternIndexBuild(IGrid grid) {
        Set<PatternContainer> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<PatternContainer> sources = new ArrayList<>();
        for (Class<?> machineClass : grid.getMachineClasses()) {
            if (!PatternContainer.class.isAssignableFrom(machineClass)) {
                continue;
            }
            Class<? extends PatternContainer> containerClass = machineClass.asSubclass(PatternContainer.class);
            for (PatternContainer container : grid.getActiveMachines(containerClass)) {
                if (visited.add(container) && !(container instanceof ECOCraftingPatternBusBlockEntity)) {
                    sources.add(container);
                }
            }
        }
        externalCraftingHistory.keySet().retainAll(visited);
        sources.sort(Comparator.comparingInt(this::externalSourcePriority));
        externalPatternSlots.clear();
        externalAvailable.clear();
        clearExternalPatternRecords();
        externalPatternSources = List.copyOf(sources);
        externalPreferredBySource.clear();
        for (PatternContainer source : sources) {
            BitSet history = externalCraftingHistory.get(source);
            if (history != null && !history.isEmpty()) externalPreferredBySource.put(source, (BitSet) history.clone());
        }
        externalPreferredPass = true;
        externalPatternSourceIndex = 0;
        externalPatternSlotIndex = 0;
        beginPreferredSlots();
        externalPatternScannedSlots = 0;
        externalPatternTotalSlots = sources.stream()
                .mapToInt(source -> sourceSlots(source).size())
                .sum();
        externalPatternIndexAge = 0;
        externalPatternIndexDirty = false;
        externalPatternIndexBuilding = !sources.isEmpty();
    }

    private void requeueExternalCandidate(ECOPatternSourceSlot candidate) {
        BitSet slots = externalPatternSlots.get(candidate.source());
        if (slots != null && slots.get(candidate.slot())) externalAvailable.addLast(candidate);
    }

    private int externalSourcePriority(PatternContainer source) {
        BitSet previous = externalCraftingHistory.get(source);
        return previous != null && !previous.isEmpty() ? 0 : sourceKindPriority(source.getClass());
    }

    // Hints only: unknown integrations are always scanned as well. No optional mod class is loaded here.
    static int sourceKindPriority(Class<?> type) {
        String name = type.getName().toLowerCase(Locale.ROOT);
        if (name.contains("matrix") || name.contains("patterncore") || name.contains("patternstorage")
                || name.contains("molecularassembler")) return 1;
        if (name.contains("patternprovider")) return 2;
        return 3;
    }

    private void beginPreferredSlots() {
        externalPreferredSlots = externalPatternSourceIndex < externalPatternSources.size()
                ? externalPreferredBySource.getOrDefault(
                        externalPatternSources.get(externalPatternSourceIndex), new BitSet())
                : new BitSet();
        externalPreferredSlot = 0;
        externalScanningPreferred = externalPreferredPass;
    }

    /**
     * The slots to index for {@code source}.
     *
     * <p>An FD bus answers AE2's contract with the terminal's view, and that view hides the disks and appends
     * their recipes. Indexing it would read those appended rows as slots and then count the same recipes again
     * through the auxiliary index, so the bus is asked for its real slots instead. Any other container only
     * has the one view, and that is what its slot indices refer to.</p>
     */
    public static InternalInventory sourceSlots(PatternContainer source) {
        return source instanceof ECOCraftingPatternBusBlockEntity bus
                ? bus.getPatternSlotInventory()
                : source.getTerminalPatternInventory();
    }

    private void scanExternalPatternIndex() {
        long started = System.nanoTime();
        int budget = EXTERNAL_PATTERN_INDEX_SAFETY_LIMIT;
        boolean hitTimeBudget = false;
        while (budget > 0 && externalPatternSourceIndex < externalPatternSources.size()) {
            if (System.nanoTime() - started >= EXTERNAL_PATTERN_INDEX_NANOS_PER_TICK) {
                hitTimeBudget = true;
                break;
            }
            PatternContainer source = externalPatternSources.get(externalPatternSourceIndex);
            var inventory = sourceSlots(source);
            int preferred = externalScanningPreferred ? externalPreferredSlots.nextSetBit(externalPreferredSlot) : -1;
            if (preferred < 0 || preferred >= inventory.size()) externalScanningPreferred = false;
            if (source.getGrid() != externalPatternIndexGrid
                    || (externalPreferredPass && !externalScanningPreferred)
                    || (!externalScanningPreferred && externalPatternSlotIndex >= inventory.size())) {
                externalPatternSourceIndex++;
                externalPatternSlotIndex = 0;
                if (externalPreferredPass && externalPatternSourceIndex >= externalPatternSources.size()) {
                    externalPreferredPass = false;
                    externalPatternSourceIndex = 0;
                }
                beginPreferredSlots();
                continue;
            }
            int slot;
            if (externalScanningPreferred) {
                slot = preferred;
                externalPreferredSlot = slot + 1;
            } else {
                slot = externalPatternSlotIndex++;
                if (externalPreferredSlots.get(slot)) continue;
            }
            externalPatternScannedSlots++;
            budget--;
            var stack = inventory.getStackInSlot(slot);
            BitSet history = externalCraftingHistory.computeIfAbsent(source, ignored -> new BitSet());
            history.set(slot, stack.has(appeng.api.ids.AEComponents.ENCODED_CRAFTING_PATTERN));
            if (!stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack)) {
                externalPatternSlots.computeIfAbsent(source, ignored -> new BitSet()).set(slot);
                ECOPatternSourceSlot candidate = new ECOPatternSourceSlot(source, slot);
                if (!externalPatternClaims.containsKey(candidate)) externalAvailable.addLast(candidate);
                AEItemKey key = AEItemKey.of(stack);
                PatternRecord record = new PatternRecord(
                                new PatternLocation(source, slot),
                                key,
                                stack.copy(),
                                null,
                                PatternType.EXTERNAL_ENCODED,
                                false,
                                "");
                externalPatternRecords.computeIfAbsent(source, ignored -> new Int2ObjectOpenHashMap<>()).put(slot, record);
                addLocation(record);
            }
        }
        if (externalPatternSourceIndex >= externalPatternSources.size()) {
            externalPatternIndexBuilding = false;
            externalPatternIndexAge = 0;
        }
        if (budget == 0 && externalPatternSourceIndex < externalPatternSources.size()) {
            hitTimeBudget = true;
        }
        externalPatternLastScanNanos = System.nanoTime() - started;
        externalPatternScanNanos += externalPatternLastScanNanos;
        if (hitTimeBudget) {
            externalPatternScanBudgetHits++;
        }
    }

    private ExternalPatternIndexState externalPatternIndexState(boolean includeCandidates) {
        if (externalPatternIndexBuilding || !includeCandidates) {
            return new ExternalPatternIndexState(!externalPatternIndexBuilding, externalPatternScannedSlots, externalPatternTotalSlots, List.of(),
                    externalPatternLastScanNanos, EXTERNAL_PATTERN_INDEX_NANOS_PER_TICK,
                    externalPatternScanBudgetHits, externalPatternScanNanos);
        }
        List<ECOPatternSourceSlot> candidates = new ArrayList<>();
        for (PatternContainer source : externalPatternSources) {
            BitSet slots = externalPatternSlots.get(source);
            if (slots == null) continue;
            slots.stream()
                .forEach(slot -> {
                    ECOPatternSourceSlot candidate = new ECOPatternSourceSlot(source, slot);
                    if (!externalPatternClaims.containsKey(candidate)) {
                        candidates.add(candidate);
                    }
                });
        }
        return new ExternalPatternIndexState(true, externalPatternScannedSlots, externalPatternTotalSlots, List.copyOf(candidates),
                externalPatternLastScanNanos, EXTERNAL_PATTERN_INDEX_NANOS_PER_TICK,
                externalPatternScanBudgetHits, externalPatternScanNanos);
    }

    private void refreshPatternIndexes() {
        Set<ECOCraftingPatternBusBlockEntity> current =
                Collections.newSetFromMap(new IdentityHashMap<>());
        boolean changed = false;
        for (IECOPatternStorage storage : patternStorages.values()) {
            if (!(storage instanceof ECOCraftingPatternBusBlockEntity bus)) {
                continue;
            }
            current.add(bus);
            if (!busPatternRevisions.containsKey(bus)
                    || busPatternRevisions.getInt(bus) != bus.getPatternContentRevision()) {
                rebuildBusPatternIndex(bus);
                changed = true;
                continue;
            }
            // A disk rewriting its contents leaves the slot layout untouched, so the slot revision above
            // does not move. Re-derive just the auxiliary side, or the network keeps counting patterns
            // that are gone and keeps missing ones that arrived.
            if (!busAuxiliaryRevisions.containsKey(bus)
                    || busAuxiliaryRevisions.getLong(bus) != bus.getAuxiliaryRevision()) {
                dropAuxiliaryCounts(bus);
                indexAuxiliaryPatterns(bus);
                // The disk changed without moving the slot layout, so nothing told AE2 to re-read this
                // provider. Without the nudge its crafting service keeps dispatching the old list until an
                // unrelated grid change forces a rescan.
                bus.refreshAdvertisedPatterns();
                changed = true;
            }
        }
        if (!busPatternRevisions.isEmpty()) {
            List<ECOCraftingPatternBusBlockEntity> stale = new ArrayList<>();
            for (ECOCraftingPatternBusBlockEntity bus : busPatternRevisions.keySet()) {
                if (!current.contains(bus)) {
                    stale.add(bus);
                }
            }
            for (ECOCraftingPatternBusBlockEntity bus : stale) {
                removeBusPatternIndex(bus);
                changed = true;
            }
        }
        if (changed || !writablePatternStorageCacheInitialized) {
            rebuildWritablePatternStorageCache();
        }
    }

    @Override
    public void onPatternSlotsChanged(ECOCraftingPatternBusBlockEntity bus,
                                      int previousRevision,
                                      int[] changedSlots) {
        boolean indexed = busPatternRevisions.containsKey(bus);
        int indexedRevision = busPatternRevisions.getInt(bus);
        var inventory = bus.getPatternSlotInventory();
        boolean unknownRange = Arrays.stream(changedSlots)
                .anyMatch(slot -> slot < 0 || slot >= inventory.size());
        if (!indexed || indexedRevision != previousRevision || unknownRange) {
            rebuildBusPatternIndex(bus);
            rebuildWritablePatternStorageCache();
            return;
        }
        // A slot that held, or now holds, an auxiliary container voids the "no record = was empty"
        // shortcut below, because disks never get a record. Moving one is rare, so re-derive the bus
        // instead of guessing the empty-slot delta: guessing clamps the count, and the bus silently
        // drops out of the writable set while it still has free slots.
        BitSet auxiliarySlots = busAuxiliarySlots.get(bus);
        for (int slot : changedSlots) {
            boolean holdsAuxiliary = slot >= 0 && slot < inventory.size()
                    && bus.ownsAuxiliary(inventory.getStackInSlot(slot));
            if ((auxiliarySlots != null && auxiliarySlots.get(slot)) || holdsAuxiliary) {
                rebuildBusPatternIndex(bus);
                rebuildWritablePatternStorageCache();
                return;
            }
        }
        Object2IntOpenHashMap<AEItemKey> busCounts = busPatternKeys.computeIfAbsent(bus, ignored -> new Object2IntOpenHashMap<>());
        Int2ObjectMap<PatternRecord> records = busPatternRecords.computeIfAbsent(bus, ignored -> new Int2ObjectOpenHashMap<>());
        int emptyDelta = 0;
        for (int slot : changedSlots) {
            PatternRecord previous = records.remove(slot);
            if (previous != null) {
                removeLocation(previous);
                decrementCount(busCounts, previous.key());
                decrementCount(networkPatternCounts, previous.key());
            }
            ItemStack current = slot >= 0 && slot < inventory.size()
                    ? inventory.getStackInSlot(slot)
                    : ItemStack.EMPTY;
            if (!current.isEmpty() && !bus.ownsAuxiliary(current)) {
                AEItemKey key = AEItemKey.of(current);
                if (key != null) {
                    busCounts.addTo(key, 1);
                    networkPatternCounts.addTo(key, 1);
                    PatternRecord record = createRecord(bus, slot, current);
                    records.put(slot, record);
                    addLocation(record);
                }
            }
            boolean wasEmpty = previous == null;
            boolean isEmpty = current.isEmpty();
            if (wasEmpty != isEmpty) {
                emptyDelta += isEmpty ? 1 : -1;
            }
        }
        busPatternRevisions.put(bus, bus.getPatternContentRevision());
        int previousEmpty = busEmptySlotCounts.getInt(bus);
        int nextEmpty = Math.max(0, previousEmpty + emptyDelta);
        busEmptySlotCounts.put(bus, nextEmpty);
        if ((previousEmpty == 0) != (nextEmpty == 0)) {
            rebuildWritablePatternStorageCache();
        }
    }

    private static void decrementCount(Object2IntOpenHashMap<AEItemKey> target, AEItemKey key) {
        dropCount(target, key, 1);
    }

    private void rebuildBusPatternIndex(ECOCraftingPatternBusBlockEntity bus) {
        removeBusPatternIndex(bus);
        Object2IntOpenHashMap<AEItemKey> counts = new Object2IntOpenHashMap<>();
        Int2ObjectMap<PatternRecord> records = new Int2ObjectOpenHashMap<>();
        int emptySlots = 0;
        BitSet auxiliarySlots = new BitSet();
        var inventory = bus.getPatternSlotInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                emptySlots++;
                continue;
            }
            // A container the auxiliary store serves from is not itself a pattern; its contents reach
            // networkPatternCounts through indexAuxiliaryPatterns instead. The slot is still remembered,
            // because the per-slot delta reads "no record" as "was empty".
            if (bus.ownsAuxiliary(stack)) {
                auxiliarySlots.set(slot);
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key != null) {
                counts.addTo(key, 1);
                networkPatternCounts.addTo(key, 1);
                PatternRecord record = createRecord(bus, slot, stack);
                records.put(slot, record);
                addLocation(record);
            }
        }
        busPatternKeys.put(bus, counts);
        busPatternRecords.put(bus, records);
        busPatternRevisions.put(bus, bus.getPatternContentRevision());
        busEmptySlotCounts.put(bus, emptySlots);
        busAuxiliarySlots.put(bus, auxiliarySlots);
        indexAuxiliaryPatterns(bus);
    }

    /**
     * Folds a bus's auxiliary patterns into the network-wide counts.
     *
     * <p>These are patterns the network can already craft with, so leaving them out would make
     * {@link #tryInsertPatternInternal}'s first gate treat them as absent and accept a duplicate.</p>
     */
    private void indexAuxiliaryPatterns(ECOCraftingPatternBusBlockEntity bus) {
        Object2IntOpenHashMap<AEItemKey> counts = new Object2IntOpenHashMap<>();
        for (ItemStack encoded : bus.getAuxiliaryEncodedPatterns()) {
            AEItemKey key = AEItemKey.of(encoded);
            if (key != null) {
                counts.addTo(key, 1);
                networkPatternCounts.addTo(key, 1);
            }
        }
        busAuxiliaryPatternKeys.put(bus, counts);
        busAuxiliaryRevisions.put(bus, bus.getAuxiliaryRevision());
    }

    /** Drops a bus's auxiliary counts, keeping {@link #networkPatternCounts} consistent. */
    private void dropAuxiliaryCounts(ECOCraftingPatternBusBlockEntity bus) {
        Object2IntOpenHashMap<AEItemKey> counts = busAuxiliaryPatternKeys.remove(bus);
        if (counts == null) {
            return;
        }
        for (var entry : counts.object2IntEntrySet()) {
            dropCount(networkPatternCounts, entry.getKey(), entry.getIntValue());
        }
    }

    private void removeBusPatternIndex(ECOCraftingPatternBusBlockEntity bus) {
        Int2ObjectMap<PatternRecord> records = busPatternRecords.remove(bus);
        if (records != null) {
            records.values().forEach(this::removeLocation);
        }
        Object2IntOpenHashMap<AEItemKey> counts = busPatternKeys.remove(bus);
        if (counts != null) {
            for (var entry : counts.object2IntEntrySet()) {
                dropCount(networkPatternCounts, entry.getKey(), entry.getIntValue());
            }
        }
        dropAuxiliaryCounts(bus);
        busAuxiliaryRevisions.removeLong(bus);
        busAuxiliarySlots.remove(bus);
        busPatternRevisions.removeInt(bus);
        busEmptySlotCounts.removeInt(bus);
    }

    private static void dropCount(Object2IntOpenHashMap<AEItemKey> target, AEItemKey key, int amount) {
        int remaining = target.getInt(key) - amount;
        if (remaining <= 0) target.removeInt(key);
        else target.put(key, remaining);
    }

    private void addLocation(@Nullable PatternRecord record) {
        if (record != null && record.key() != null) {
            patternLocationsByKey.computeIfAbsent(record.key(), ignored -> new HashSet<>())
                    .add(record.location());
        }
    }

    private void removeLocation(@Nullable PatternRecord record) {
        if (record == null || record.key() == null) {
            return;
        }
        Set<PatternLocation> locations = patternLocationsByKey.get(record.key());
        if (locations != null) {
            locations.remove(record.location());
            if (locations.isEmpty()) {
                patternLocationsByKey.remove(record.key());
            }
        }
    }

    private void clearExternalPatternRecords() {
        externalPatternRecords.values().forEach(records -> records.values().forEach(this::removeLocation));
        externalPatternRecords.clear();
    }

    private void rebuildWritablePatternStorageCache() {
        List<IECOPatternStorage> next = new ArrayList<>();
        for (IECOPatternStorage storage : patternStorages.values()) {
            if (storage instanceof ECOCraftingPatternBusBlockEntity bus) {
                // An auxiliary store keeps the bus writable even with every slot occupied: a bus full of
                // pattern disks is exactly the case where slot capacity says nothing about room.
                if (busEmptySlotCounts.getInt(bus) > 0 || bus.hasAuxiliaryRoom()) {
                    next.add(storage);
                }
            }
        }
        // Ordered by position, because this list decides which bus takes a pattern and the map behind it is an
        // identity map whose order is neither stable nor meaningful. Without this, which disk a pattern lands
        // on could differ between runs of the same world.
        next.sort(Comparator.comparingLong(storage -> ((ECOCraftingPatternBusBlockEntity) storage)
                .getBlockPos()
                .asLong()));
        if (!sameStorageList(writablePatternStorages, next)) {
            writablePatternStorages = List.copyOf(next);
            patternCapacityGeneration = patternCapacityGeneration == Long.MAX_VALUE
                    ? 1L
                    : patternCapacityGeneration + 1L;
            if (preferredStorage != null && !writablePatternStorages.contains(preferredStorage)) {
                preferredStorage = null;
            }
        }
        writablePatternStorageCacheInitialized = true;
    }

    private static boolean sameStorageList(List<IECOPatternStorage> first, List<IECOPatternStorage> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (first.get(index) != second.get(index)) {
                return false;
            }
        }
        return true;
    }

    private void markStorageFull(IECOPatternStorage storage) {
        if (storage instanceof ECOCraftingPatternBusBlockEntity bus) {
            if (busEmptySlotCounts.getInt(bus) > 0) {
                busEmptySlotCounts.put(bus, 0);
                rebuildWritablePatternStorageCache();
            }
        }
    }

}
