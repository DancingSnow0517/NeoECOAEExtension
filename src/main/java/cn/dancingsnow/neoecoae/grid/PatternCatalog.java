package cn.dancingsnow.neoecoae.grid;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.helpers.patternprovider.PatternContainer;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPatternSourceSlot;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.Comparator;

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
    private final Map<ECOCraftingPatternBusBlockEntity, Map<AEItemKey, Integer>> busPatternKeys =
            new IdentityHashMap<>();
    private final Map<ECOCraftingPatternBusBlockEntity, Integer> busPatternRevisions = new IdentityHashMap<>();
    private final Map<ECOCraftingPatternBusBlockEntity, Integer> busEmptySlotCounts = new IdentityHashMap<>();
    private final Map<ECOCraftingPatternBusBlockEntity, Map<Integer, PatternRecord>> busPatternRecords =
            new IdentityHashMap<>();
    private final Map<AEItemKey, Set<PatternLocation>> patternLocationsByKey = new HashMap<>();
    private final Map<AEItemKey, Integer> networkPatternCounts = new HashMap<>();
    private List<IECOPatternStorage> writablePatternStorages = List.of();
    private boolean writablePatternStorageCacheInitialized;
    private long patternCapacityGeneration;
    @Nullable
    private IECOPatternStorage preferredStorage;
    @Nullable
    private IGrid externalPatternIndexGrid;
    private final Map<PatternContainer, BitSet> externalPatternSlots = new IdentityHashMap<>();
    private final Map<PatternContainer, Map<Integer, PatternRecord>> externalPatternRecords =
            new IdentityHashMap<>();
    private List<PatternContainer> externalPatternSources = List.of();
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
    private final Map<ECOPatternSourceSlot, UUID> externalPatternClaims = new HashMap<>();
    private final Map<UUID, Set<ECOPatternSourceSlot>> externalPatternClaimsByOwner = new HashMap<>();
    private final Map<UUID, Long> externalPatternClaimTicks = new HashMap<>();
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

    private ECOPatternInsertionResult tryInsertPatternInternal(ItemStack patternItem,
                                                                 @Nullable ECOPreparedPattern prepared) {
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
        for (Map.Entry<ECOCraftingPatternBusBlockEntity, Map<Integer, PatternRecord>> entry
                : busPatternRecords.entrySet()) {
            ECOCraftingPatternBusBlockEntity bus = entry.getKey();
            bus.refreshPatternDetailsForCatalog();
            for (int slot : entry.getValue().keySet()) {
                ItemStack stack = bus.getTerminalPatternInventory().getStackInSlot(slot);
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
        Map<Integer, PatternRecord> records = busPatternRecords.get(bus);
        if (records == null || !records.containsKey(physicalSlot)) {
            return null;
        }
        ItemStack stack = bus.getTerminalPatternInventory().getStackInSlot(physicalSlot);
        return stack.isEmpty() ? null : createRecord(bus, physicalSlot, stack);
    }

    public void refresh() {
        refreshPatternIndexes();
    }

    public Set<PatternLocation> locationsForKey(AEItemKey key) {
        Set<PatternLocation> locations = patternLocationsByKey.get(key);
        return locations == null ? Set.of() : Set.copyOf(locations);
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
        if (externalPatternIndexGrid != grid) {
            externalPatternIndexGrid = grid;
            invalidateExternalPatternIndex();
        }
        if (externalPatternIndexDirty && !externalPatternIndexBuilding) {
            beginExternalPatternIndexBuild(grid);
        }
        return externalPatternIndexState();
    }

    @Override
    public ExternalPatternClaim claimExternalPatternCandidates(IGrid grid, UUID owner, int maxCandidates) {
        if (owner == null || maxCandidates <= 0) {
            return new ExternalPatternClaim(false, externalPatternScannedSlots, externalPatternTotalSlots, List.of(),
                    externalPatternLastScanNanos, EXTERNAL_PATTERN_INDEX_NANOS_PER_TICK,
                    externalPatternScanBudgetHits, externalPatternScanNanos);
        }
        ExternalPatternIndexState state = getExternalPatternIndex(grid);
        Set<ECOPatternSourceSlot> owned = externalPatternClaimsByOwner.computeIfAbsent(owner,
                ignored -> new HashSet<>());
        externalPatternClaimTicks.put(owner, externalPatternTick);
        List<ECOPatternSourceSlot> claimed = new ArrayList<>(Math.min(maxCandidates, 64));
        outer:
        for (Map.Entry<PatternContainer, BitSet> entry : externalPatternSlots.entrySet()) {
            BitSet slots = entry.getValue();
            for (int slot = slots.nextSetBit(0); slot >= 0; slot = slots.nextSetBit(slot + 1)) {
                ECOPatternSourceSlot candidate = new ECOPatternSourceSlot(entry.getKey(), slot);
                UUID currentOwner = externalPatternClaims.get(candidate);
                if (currentOwner != null) {
                    continue;
                }
                externalPatternClaims.put(candidate, owner);
                owned.add(candidate);
                claimed.add(candidate);
                if (claimed.size() >= maxCandidates) {
                    break outer;
                }
            }
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
        externalPatternClaimTicks.remove(owner);
        if (owned == null) {
            return;
        }
        for (ECOPatternSourceSlot candidate : owned) {
            if (owner.equals(externalPatternClaims.get(candidate))) {
                externalPatternClaims.remove(candidate);
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
        Set<ECOPatternSourceSlot> owned = externalPatternClaimsByOwner.get(owner);
        if (owned != null) {
            owned.remove(slot);
            if (owned.isEmpty()) {
                externalPatternClaimsByOwner.remove(owner);
                externalPatternClaimTicks.remove(owner);
            }
        }
    }

    @Override
    public void removeExternalPatternCandidate(ECOPatternSourceSlot slot) {
        BitSet slots = externalPatternSlots.get(slot.source());
        if (slots != null) {
            slots.clear(slot.slot());
        }
        Map<Integer, PatternRecord> records = externalPatternRecords.get(slot.source());
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
                    externalPatternClaimTicks.remove(owner);
                }
            }
        }
    }

    @Override
    public void onServerEndTick() {
        externalPatternTick++;
        if (!externalPatternClaimTicks.isEmpty()) {
            List<UUID> expired = new ArrayList<>();
            for (Map.Entry<UUID, Long> entry : externalPatternClaimTicks.entrySet()) {
                if (externalPatternTick - entry.getValue() >= EXTERNAL_PATTERN_CLAIM_TIMEOUT_TICKS) {
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
        clearExternalPatternRecords();
        externalPatternSources = List.of();
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
        externalPatternSlots.clear();
        clearExternalPatternRecords();
        externalPatternSources = List.copyOf(sources);
        externalPatternSourceIndex = 0;
        externalPatternSlotIndex = 0;
        externalPatternScannedSlots = 0;
        externalPatternTotalSlots = sources.stream()
                .mapToInt(source -> source.getTerminalPatternInventory().size())
                .sum();
        externalPatternIndexAge = 0;
        externalPatternIndexDirty = false;
        externalPatternIndexBuilding = !sources.isEmpty();
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
            var inventory = source.getTerminalPatternInventory();
            if (source.getGrid() != externalPatternIndexGrid || externalPatternSlotIndex >= inventory.size()) {
                externalPatternSourceIndex++;
                externalPatternSlotIndex = 0;
                continue;
            }
            int slot = externalPatternSlotIndex++;
            externalPatternScannedSlots++;
            budget--;
            var stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack)) {
                externalPatternSlots.computeIfAbsent(source, ignored -> new BitSet()).set(slot);
                AEItemKey key = AEItemKey.of(stack);
                PatternRecord record = new PatternRecord(
                                new PatternLocation(source, slot),
                                key,
                                stack.copy(),
                                null,
                                PatternType.EXTERNAL_ENCODED,
                                false,
                                "");
                externalPatternRecords.computeIfAbsent(source, ignored -> new HashMap<>()).put(slot, record);
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

    private ExternalPatternIndexState externalPatternIndexState() {
        if (externalPatternIndexBuilding) {
            return new ExternalPatternIndexState(false, externalPatternScannedSlots, externalPatternTotalSlots, List.of(),
                    externalPatternLastScanNanos, EXTERNAL_PATTERN_INDEX_NANOS_PER_TICK,
                    externalPatternScanBudgetHits, externalPatternScanNanos);
        }
        List<ECOPatternSourceSlot> candidates = new ArrayList<>();
        externalPatternSlots.forEach((source, slots) -> slots.stream()
                .forEach(slot -> {
                    ECOPatternSourceSlot candidate = new ECOPatternSourceSlot(source, slot);
                    if (!externalPatternClaims.containsKey(candidate)) {
                        candidates.add(candidate);
                    }
                }));
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
            Integer revision = busPatternRevisions.get(bus);
            if (revision == null || revision != bus.getPatternContentRevision()) {
                rebuildBusPatternIndex(bus);
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
        Integer indexedRevision = busPatternRevisions.get(bus);
        var inventory = bus.getTerminalPatternInventory();
        boolean unknownRange = Arrays.stream(changedSlots)
                .anyMatch(slot -> slot < 0 || slot >= inventory.size());
        if (indexedRevision == null || indexedRevision != previousRevision || unknownRange) {
            rebuildBusPatternIndex(bus);
            rebuildWritablePatternStorageCache();
            return;
        }
        Map<AEItemKey, Integer> busCounts = busPatternKeys.computeIfAbsent(bus, ignored -> new HashMap<>());
        Map<Integer, PatternRecord> records = busPatternRecords.computeIfAbsent(bus, ignored -> new HashMap<>());
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
            if (!current.isEmpty()) {
                AEItemKey key = AEItemKey.of(current);
                if (key != null) {
                    busCounts.merge(key, 1, Integer::sum);
                    networkPatternCounts.merge(key, 1, Integer::sum);
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
        int previousEmpty = busEmptySlotCounts.getOrDefault(bus, 0);
        int nextEmpty = Math.max(0, previousEmpty + emptyDelta);
        busEmptySlotCounts.put(bus, nextEmpty);
        if ((previousEmpty == 0) != (nextEmpty == 0)) {
            rebuildWritablePatternStorageCache();
        }
    }

    private static void decrementCount(Map<AEItemKey, Integer> target, AEItemKey key) {
        target.computeIfPresent(key, (ignored, current) -> current <= 1 ? null : current - 1);
    }

    private void rebuildBusPatternIndex(ECOCraftingPatternBusBlockEntity bus) {
        removeBusPatternIndex(bus);
        Map<AEItemKey, Integer> counts = new HashMap<>();
        Map<Integer, PatternRecord> records = new HashMap<>();
        int emptySlots = 0;
        var inventory = bus.getTerminalPatternInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                emptySlots++;
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key != null) {
                counts.merge(key, 1, Integer::sum);
                networkPatternCounts.merge(key, 1, Integer::sum);
                PatternRecord record = createRecord(bus, slot, stack);
                records.put(slot, record);
                addLocation(record);
            }
        }
        busPatternKeys.put(bus, counts);
        busPatternRecords.put(bus, records);
        busPatternRevisions.put(bus, bus.getPatternContentRevision());
        busEmptySlotCounts.put(bus, emptySlots);
    }

    private void removeBusPatternIndex(ECOCraftingPatternBusBlockEntity bus) {
        Map<Integer, PatternRecord> records = busPatternRecords.remove(bus);
        if (records != null) {
            records.values().forEach(this::removeLocation);
        }
        Map<AEItemKey, Integer> counts = busPatternKeys.remove(bus);
        if (counts != null) {
            for (Map.Entry<AEItemKey, Integer> entry : counts.entrySet()) {
                networkPatternCounts.computeIfPresent(entry.getKey(), (ignored, count) -> {
                    int remaining = count - entry.getValue();
                    return remaining <= 0 ? null : remaining;
                });
            }
        }
        busPatternRevisions.remove(bus);
        busEmptySlotCounts.remove(bus);
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
                if (busEmptySlotCounts.getOrDefault(bus, 0) > 0) {
                    next.add(storage);
                }
            }
        }
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
            if (busEmptySlotCounts.getOrDefault(bus, 0) > 0) {
                busEmptySlotCounts.put(bus, 0);
                rebuildWritablePatternStorageCache();
            }
        }
    }

}
