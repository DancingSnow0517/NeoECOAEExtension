package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECORecipeClassifier;
import cn.dancingsnow.neoecoae.crafting.planner.growth.NetGrowthPatternValidationRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;

/** Owns decoded pattern data, capacity indexing and the quiet-window catalog refresh policy. */
final class ECOCraftingPatternBusCatalog {
    private static final int QUIET_TICKS = 2;

    private final ECOCraftingPatternBusBlockEntity host;
    private final AppEngInternalInventory inventory;
    private final List<IPatternDetails> patternDetails = new ArrayList<>();
    private final IPatternDetails[] decodedPatternDetails;
    private final BitSet dirtyPatternSlots;
    private final String[] patternSearchKeywords;
    private final BitSet emptyPatternSlots;

    private boolean patternDetailsUpdateQueued;
    private boolean immediatePatternDetailsUpdate;
    private boolean patternDetailsUpdateInProgress;
    private boolean rebuildAllPatternDetails = true;
    private int highestOccupiedSlot = -1;
    private int patternDetailsUpdateTick;
    private int patternCapacitySlotCount;
    private int patternCapacityGeneration;
    private boolean patternCapacityIndexInitialized;
    private int patternBatchDepth;
    private final BitSet patternBatchChangedSlots = new BitSet();

    @Nullable
    private ECOPreparedPattern activePreparedPattern;

    ECOCraftingPatternBusCatalog(ECOCraftingPatternBusBlockEntity host, AppEngInternalInventory inventory) {
        this.host = host;
        this.inventory = inventory;
        int slotCount = inventory.size();
        this.decodedPatternDetails = new IPatternDetails[slotCount];
        this.dirtyPatternSlots = new BitSet(slotCount);
        this.patternSearchKeywords = new String[slotCount];
        this.emptyPatternSlots = new BitSet(slotCount);
    }

    List<IPatternDetails> availablePatterns() {
        return patternDetails;
    }

    @Nullable
    IPatternDetails decodedPatternDetails(int slot) {
        return slot >= 0 && slot < decodedPatternDetails.length ? decodedPatternDetails[slot] : null;
    }

    String patternSearchKeywords(int slot) {
        return slot >= 0 && slot < patternSearchKeywords.length ? patternSearchKeywords[slot] : "";
    }

    int patternContentRevision() {
        return host.patternContentRevisionValue();
    }

    boolean isBatchActive() {
        return patternBatchDepth > 0;
    }

    int highestOccupiedSlot() {
        return highestOccupiedSlot;
    }

    boolean allowInsert(int slot, ItemStack stack) {
        return slot >= 0
            && slot < host.getPatternSlotCount()
            && (activePreparedPattern != null
                ? activePreparedPattern.matches(stack)
                : PatternDetailsHelper.decodePattern(stack, host.getLevel())
                    instanceof IMolecularAssemblerSupportedPattern);
    }

    ECOPatternInsertionResult insertPreparedPattern(ECOPreparedPattern prepared) {
        ItemStack remaining = addPatternItems(prepared);
        return remaining.isEmpty()
            ? ECOPatternInsertionResult.INSERTED
            : ECOPatternInsertionResult.NO_SPACE;
    }

    private ItemStack addPatternItems(ECOPreparedPattern prepared) {
        ensurePatternCapacityIndex();
        ItemStack remaining = prepared.stack().copy();
        activePreparedPattern = prepared;
        try {
            while (!remaining.isEmpty()) {
                int slot = emptyPatternSlots.nextSetBit(0);
                if (slot < 0 || slot >= patternCapacitySlotCount) {
                    break;
                }
                ItemStack next = inventory.insertItem(slot, remaining, false);
                if (next.getCount() >= remaining.getCount()) {
                    break;
                }
                remaining = next;
            }
            return remaining;
        } finally {
            activePreparedPattern = null;
        }
    }

    void beginBatch() {
        patternBatchDepth++;
    }

    void setPatternDirect(int slot, ItemStack stack) {
        if (slot < 0 || slot >= host.getPatternSlotCount()) {
            return;
        }
        ItemStack next = stack == null ? ItemStack.EMPTY : stack;
        ItemStack previous = inventory.getStackInSlot(slot);
        if (ItemStack.matches(previous, next)) {
            return;
        }
        if (patternBatchDepth > 0) {
            patternBatchChangedSlots.set(slot);
        }
        inventory.setItemDirect(slot, next);
    }

    void endBatch() {
        if (patternBatchDepth <= 0) {
            throw new IllegalStateException("Pattern batch is not active");
        }
        if (--patternBatchDepth > 0) {
            return;
        }
        if (patternBatchChangedSlots.isEmpty()) {
            clearPatternBatchState();
            return;
        }

        int previousRevision = patternContentRevision();
        host.saveChanges();
        host.incrementPatternContentRevision();
        applyPatternCapacityBatch(patternBatchChangedSlots);
        dirtyPatternSlots.or(patternBatchChangedSlots);
        host.notifyPatternCatalog(previousRevision, patternBatchChangedSlots.stream().toArray());
        updatePatternDetailsNow();
        patternDetailsUpdateQueued = false;
        clearPatternBatchState();
    }

    void onChangeInventory(int slot) {
        if (host.getLevel() == null) {
            return;
        }
        // The highest occupied slot has to stay in step on both sides: the page count is derived from it, and the
        // paging decides which physical slot a visible index maps to. The server applies a write to the slot the
        // *client* clicked, so a client that counted fewer pages would write somewhere else entirely. That is why
        // this runs before the client-side exit below.
        trackHighestOccupiedSlot(slot);
        if (host.getLevel().isClientSide) {
            return;
        }
        if (patternBatchDepth > 0) {
            if (slot >= 0 && slot < inventory.size()) {
                patternBatchChangedSlots.set(slot);
            } else {
                patternCapacityIndexInitialized = false;
                rebuildAllPatternDetails = true;
            }
            return;
        }

        int previousRevision = patternContentRevision();
        host.saveChanges();
        host.incrementPatternContentRevision();
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
        refreshAfterInventoryChange(slot);
        host.notifyPatternCatalog(previousRevision, new int[] { slot });
        host.notifyPatternInterfaceHosts(slot);
    }

    /**
     * Keeps the highest occupied slot in step with the inventory on both sides.
     *
     * @param slot the slot that changed, or a negative value for "the whole inventory changed"
     */
    private void trackHighestOccupiedSlot(int slot) {
        if (slot < 0 || slot >= inventory.size()) {
            // A negative slot means "the whole inventory changed"; anything else is out of range. Either way the
            // tracked maximum can no longer be trusted, and the page count is derived from it — slots past a
            // stale maximum would stop being decoded and advertised.
            rescanHighestOccupiedSlot();
            return;
        }
        if (!inventory.getStackInSlot(slot).isEmpty()) {
            highestOccupiedSlot = Math.max(highestOccupiedSlot, slot);
            return;
        }
        if (slot != highestOccupiedSlot) {
            return;
        }
        highestOccupiedSlot = slot - 1;
        while (highestOccupiedSlot >= 0 && inventory.getStackInSlot(highestOccupiedSlot).isEmpty()) {
            highestOccupiedSlot--;
        }
    }

    private void rescanHighestOccupiedSlot() {
        highestOccupiedSlot = inventory.size() - 1;
        while (highestOccupiedSlot >= 0 && inventory.getStackInSlot(highestOccupiedSlot).isEmpty()) {
            highestOccupiedSlot--;
        }
    }

    void onReady() {
        rebuildAllPatternDetails = true;
        rebuildPatternCapacityIndex();
        highestOccupiedSlot = -1;
        for (int slot = inventory.size() - 1; slot >= 0; slot--) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                highestOccupiedSlot = slot;
                break;
            }
        }
        updatePatternDetailsNow();
    }

    void refreshPatternDetailsForCatalog() {
        if (rebuildAllPatternDetails || !dirtyPatternSlots.isEmpty()) {
            updatePatternDetailsNow();
            patternDetailsUpdateQueued = false;
        }
    }

    void requestPatternDetailsRefresh() {
        queuePatternDetailsUpdate();
    }

    void flushScheduledPatternDetails() {
        if (!host.isRemoved()) {
            patternDetailsUpdateQueued = false;
            updatePatternDetailsNow();
        }
    }

    private void refreshAfterInventoryChange(int slot) {
        if (slot < 0 || slot >= inventory.size() || inventory.getStackInSlot(slot).isEmpty()
            || decodedPatternDetails[slot] != null) {
            PatternBusUpdateScheduler.remove(host);
            patternDetailsUpdateQueued = false;
            updatePatternDetailsNow();
            return;
        }
        queuePatternDetailsUpdate();
    }

    void updatePatternDetails() {
        if (host.getLevel() == null || host.getLevel().isClientSide) {
            return;
        }
        if (patternDetailsUpdateInProgress) {
            queuePatternDetailsUpdate();
            return;
        }
        if (host.getLevel() instanceof ServerLevel && !immediatePatternDetailsUpdate) {
            queuePatternDetailsUpdate();
            return;
        }

        patternDetailsUpdateInProgress = true;
        try {
            int slotCount = host.getPatternSlotCount();
            boolean refreshedAll = rebuildAllPatternDetails;
            int[] refreshedSlots = refreshedAll ? new int[0] : dirtyPatternSlots.stream().toArray();
            if (rebuildAllPatternDetails) {
                Arrays.fill(decodedPatternDetails, null);
                for (int slot = 0; slot < slotCount; slot++) {
                    decodedPatternDetails[slot] = PatternDetailsHelper.decodePattern(
                        inventory.getStackInSlot(slot), host.getLevel());
                }
            } else {
                for (int slot = dirtyPatternSlots.nextSetBit(0);
                     slot >= 0;
                     slot = dirtyPatternSlots.nextSetBit(slot + 1)) {
                    decodedPatternDetails[slot] = PatternDetailsHelper.decodePattern(
                        inventory.getStackInSlot(slot), host.getLevel());
                }
            }
            rebuildAllPatternDetails = false;
            dirtyPatternSlots.clear();

            patternDetails.clear();
            for (int slot = 0; slot < slotCount; slot++) {
                IPatternDetails details = decodedPatternDetails[slot];
                patternSearchKeywords[slot] = buildPatternSearchKeywords(inventory.getStackInSlot(slot), details);
                if (details instanceof IMolecularAssemblerSupportedPattern) {
                    ECORecipeClassifier.classify(details);
                    if (host.shouldValidateNetGrowthPatterns()) {
                        NetGrowthPatternValidationRegistry.validateAndRegisterFromSmartPatternBus(details);
                    }
                    patternDetails.add(details);
                }
            }
            ICraftingProvider.requestUpdate(host.getMainNode());
            if (refreshedAll) {
                host.notifyPatternInterfaceHosts(-1);
            } else if (refreshedSlots.length > 0) {
                host.notifyPatternInterfaceHosts(refreshedSlots);
            }
        } finally {
            patternDetailsUpdateInProgress = false;
        }
    }

    void updatePatternDetailsNow() {
        boolean previous = immediatePatternDetailsUpdate;
        immediatePatternDetailsUpdate = true;
        try {
            updatePatternDetails();
        } finally {
            immediatePatternDetailsUpdate = previous;
        }
    }

    private void queuePatternDetailsUpdate() {
        if (!(host.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        patternDetailsUpdateTick = serverLevel.getServer().getTickCount() + QUIET_TICKS;
        patternDetailsUpdateQueued = true;
        PatternBusUpdateScheduler.mark(host, patternDetailsUpdateTick);
    }

    private void ensurePatternCapacityIndex() {
        int slotCount = Math.min(host.getPatternSlotCount(), inventory.size());
        if (!patternCapacityIndexInitialized || patternCapacitySlotCount != slotCount) {
            rebuildPatternCapacityIndex(slotCount);
        }
    }

    private void rebuildPatternCapacityIndex() {
        rebuildPatternCapacityIndex(Math.min(host.getPatternSlotCount(), inventory.size()));
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
        int slotCount = Math.min(host.getPatternSlotCount(), inventory.size());
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
        emptyPatternSlots.set(slot, shouldBeEmpty);
        patternCapacityGeneration = patternCapacityGeneration == Integer.MAX_VALUE
            ? 1
            : patternCapacityGeneration + 1;
    }

    private void applyPatternCapacityBatch(BitSet changedSlots) {
        int slotCount = Math.min(host.getPatternSlotCount(), inventory.size());
        if (!patternCapacityIndexInitialized || patternCapacitySlotCount != slotCount) {
            rebuildPatternCapacityIndex(slotCount);
        } else {
            for (int slot = changedSlots.nextSetBit(0);
                 slot >= 0 && slot < slotCount;
                 slot = changedSlots.nextSetBit(slot + 1)) {
                emptyPatternSlots.set(slot, inventory.getStackInSlot(slot).isEmpty());
            }
            patternCapacityGeneration = patternCapacityGeneration == Integer.MAX_VALUE
                ? 1
                : patternCapacityGeneration + 1;
        }
        highestOccupiedSlot = -1;
        for (int slot = slotCount - 1; slot >= 0; slot--) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                highestOccupiedSlot = slot;
                break;
            }
        }
    }

    private void clearPatternBatchState() {
        patternBatchChangedSlots.clear();
    }

    /**
     * Search keywords for one pattern: its own name plus everything it is built from and produces.
     *
     * <p>Package-private because the bus's auxiliary keyword list pairs a container's encoded patterns with the
     * same decode, and a terminal only finds a recipe through these keywords.</p>
     */
    static String buildPatternSearchKeywords(
        ItemStack stack,
        @Nullable IPatternDetails details
    ) {
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
}
