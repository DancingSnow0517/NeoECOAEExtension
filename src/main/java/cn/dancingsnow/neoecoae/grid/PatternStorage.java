package cn.dancingsnow.neoecoae.grid;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import appeng.helpers.patternprovider.PatternContainer;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPatternSourceSlot;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PatternStorage implements IECOPatternStorageService, IGridServiceProvider {

    private static final int EXTERNAL_PATTERN_INDEX_SLOTS_PER_TICK = 96;
    private static final int EXTERNAL_PATTERN_INDEX_MAX_AGE_TICKS = 100;

    private final Map<IGridNode, IECOPatternStorage> patternStorages = new IdentityHashMap<>();
    private final IECOPatternStorage combinedStorage = this::tryInsertPattern;
    @Nullable
    private IECOPatternStorage preferredStorage;
    @Nullable
    private IGrid externalPatternIndexGrid;
    private final Map<PatternContainer, BitSet> externalPatternSlots = new IdentityHashMap<>();
    private List<PatternContainer> externalPatternSources = List.of();
    private int externalPatternSourceIndex;
    private int externalPatternSlotIndex;
    private int externalPatternScannedSlots;
    private int externalPatternTotalSlots;
    private int externalPatternIndexAge;
    private boolean externalPatternIndexBuilding;
    private boolean externalPatternIndexDirty = true;

    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        IGridServiceProvider.super.addNode(gridNode, savedData);
        invalidateExternalPatternIndex();
        IECOPatternStorage service = gridNode.getService(IECOPatternStorage.class);
        if (service == null) return;
        patternStorages.put(gridNode, service);
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        IGridServiceProvider.super.removeNode(gridNode);
        IECOPatternStorage removed = patternStorages.remove(gridNode);
        if (removed == preferredStorage) {
            preferredStorage = null;
        }
        invalidateExternalPatternIndex();
    }

    public ECOPatternInsertionResult tryInsertPattern(ItemStack patternItem) {
        if (patternItem.isEmpty()) {
            return ECOPatternInsertionResult.INCOMPATIBLE;
        }

        boolean noSpace = false;
        boolean uniquenessChecked = false;
        if (preferredStorage != null) {
            ECOPatternInsertionResult result = preferredStorage.insertPattern(patternItem);
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
                }
                default -> {
                }
            }
        }
        for (IECOPatternStorage value : patternStorages.values()) {
            if (value == preferredStorage) {
                continue;
            }
            ECOPatternInsertionResult result = uniquenessChecked
                ? value.insertPatternKnownUnique(patternItem)
                : value.insertPattern(patternItem);
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

    @Override
    public IECOPatternStorage getPatternStorage() {
        return combinedStorage;
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
    public void removeExternalPatternCandidate(ECOPatternSourceSlot slot) {
        BitSet slots = externalPatternSlots.get(slot.source());
        if (slots != null) {
            slots.clear(slot.slot());
        }
    }

    @Override
    public void onServerEndTick() {
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
        externalPatternSources = List.of();
        externalPatternSourceIndex = 0;
        externalPatternSlotIndex = 0;
        externalPatternScannedSlots = 0;
        externalPatternTotalSlots = 0;
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
        int budget = EXTERNAL_PATTERN_INDEX_SLOTS_PER_TICK;
        while (budget > 0 && externalPatternSourceIndex < externalPatternSources.size()) {
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
            }
        }
        if (externalPatternSourceIndex >= externalPatternSources.size()) {
            externalPatternIndexBuilding = false;
            externalPatternIndexAge = 0;
        }
    }

    private ExternalPatternIndexState externalPatternIndexState() {
        if (externalPatternIndexBuilding) {
            return new ExternalPatternIndexState(false, externalPatternScannedSlots, externalPatternTotalSlots, List.of());
        }
        List<ECOPatternSourceSlot> candidates = new ArrayList<>();
        externalPatternSlots.forEach((source, slots) -> slots.stream()
                .forEach(slot -> candidates.add(new ECOPatternSourceSlot(source, slot))));
        return new ExternalPatternIndexState(true, externalPatternScannedSlots, externalPatternTotalSlots, List.copyOf(candidates));
    }
}
