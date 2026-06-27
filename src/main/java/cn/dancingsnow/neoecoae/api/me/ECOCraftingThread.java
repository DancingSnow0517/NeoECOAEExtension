package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.me.service.CraftingService;
import appeng.menu.AutoCraftingMenu;
import cn.dancingsnow.neoecoae.api.NEFakePlayer;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOBatchCraftingHelper;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOBatchCraftingRequest;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOBatchCraftingWork;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathResult;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.NeoECOAE;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECOCraftingThread implements INBTSerializable<CompoundTag> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    public static final int MAX_PROGRESS = 100;

    private enum RecoveryState {
        ACTIVE,
        RECOVERING_INPUTS,
        RECOVERING_OUTPUTS,
        RECOVERED_TO_NETWORK,
        DROPPED_TO_WORLD,
        CLEARED
    }

    private final ECOCraftingWorkerBlockEntity worker;
    private final IActionSource actionSource;

    @Getter
    private boolean isBusy = false;

    private boolean reboot = true;

    private final List<GenericStack> outputStacks = new ArrayList<>();
    private final List<GenericStack> inputStacks = new ArrayList<>();
    private final List<GenericStack> remainingStacks = new ArrayList<>();

    @Nullable
    private UUID craftingJobId = null;

    private int progress = 0;
    private int occupiedThreadSlots = 1;
    private boolean outputsReady = false;
    private RecoveryState recoveryState = RecoveryState.CLEARED;

    private final TransientCraftingContainer craftingInv;

    public ECOCraftingThread(ECOCraftingWorkerBlockEntity worker) {
        this.worker = worker;
        this.actionSource = IActionSource.ofMachine(worker);
        this.craftingInv = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
    }

    public TickRateModulation tick(int overlockTimes, int powerMultiply, int ticksSinceLastCall) {
        if (!isBusy) {
            progress = 0;
            setChanged();
            return TickRateModulation.SLEEP;
        }
        if (this.reboot) {
            ticksSinceLastCall = 1;
        }

        this.reboot = false;
        if (isRecoveringToNetwork()) {
            if (retryRecoveryToNetwork()) {
                setChanged();
            }
            return TickRateModulation.URGENT;
        }

        if (outputsReady) {
            if (ejectOutputs()) {
                setChanged();
            }
            return TickRateModulation.URGENT;
        }

        int bonusValue = Math.min(10 + overlockTimes * 10, 100);
        progress += userPower(ticksSinceLastCall, bonusValue, powerMultiply);

        if (this.progress >= MAX_PROGRESS) {
            outputsReady = true;
            if (ejectOutputs()) {
                setChanged();
            }
            return TickRateModulation.URGENT;
        }
        setChanged();
        return TickRateModulation.URGENT;
    }

    public boolean isFree() {
        return !isBusy;
    }

    public int getProgress() {
        return progress;
    }

    public ItemStack getOutputItem() {
        return firstOutputItem().copy();
    }

    public List<ItemStack> getRemainingItems() {
        return genericStacksToItemStacks(remainingStacks);
    }

    public Snapshot createSnapshot() {
        return new Snapshot(
            isBusy, progress, MAX_PROGRESS, getOccupiedThreadSlots(), getOutputItem(), getRemainingItems(), outputsReady
        );
    }

    public boolean pushPattern(
        IMolecularAssemblerSupportedPattern pattern,
        KeyCounter[] table,
        ECOCraftingSystemBlockEntity controller
    ) {
        return pushPattern(pattern, table, controller, null);
    }

    public boolean pushPattern(
        IMolecularAssemblerSupportedPattern pattern,
        KeyCounter[] table,
        ECOCraftingSystemBlockEntity controller,
        @Nullable UUID craftingJobId
    ) {
        return pushPattern(ECOExtractedPatternExecution.slow(pattern, table), controller, craftingJobId);
    }

    public boolean pushPattern(
        ECOExtractedPatternExecution execution,
        ECOCraftingSystemBlockEntity controller,
        @Nullable UUID craftingJobId
    ) {
        if (isBusy) {
            return false;
        }

        return acceptPattern(execution, controller, craftingJobId);
    }

    public boolean pushBatch(ECOBatchCraftingRequest request, ECOCraftingSystemBlockEntity controller) {
        if (isBusy) {
            return false;
        }
        var outputTotal = ECOBatchCraftingHelper.multiply(request.outputsPerCraft(), request.batchSize());
        var inputTotal = ECOBatchCraftingHelper.multiply(request.inputsPerCraft(), request.batchSize());
        var remainingTotal = ECOBatchCraftingHelper.multiply(request.remainingPerCraft(), request.batchSize());
        var work = new ECOBatchCraftingWork(
            request.batchSize(),
            inputTotal,
            outputTotal,
            remainingTotal,
            request.craftingJobId(),
            0,
            request.batchSize()
        );
        return acceptBatch(work, controller);
    }

    private boolean acceptBatch(ECOBatchCraftingWork work, ECOCraftingSystemBlockEntity controller) {
        if (!consumeCraftingCoolant(controller, work.batchSize())) {
            worker.getFastPathCache().recordCoolantReject();
            return false;
        }
        startWork(work.outputTotal(), work.inputTotal(), work.remainingTotal(), work.craftingJobId(), work.occupiedThreadSlots());
        worker.getFastPathCache().recordFastPathAccepted();
        return true;
    }

    private boolean acceptPattern(
        ECOExtractedPatternExecution execution,
        ECOCraftingSystemBlockEntity controller,
        @Nullable UUID craftingJobId
    ) {
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        long tick = appeng.hooks.ticking.TickHandler.instance().getCurrentTick();
        if (!canUseFastPath(execution)) {
            cache.recordDisabled();
            return calcPatternSlow(execution, controller, craftingJobId, false, tick);
        }
        ECOFastPathKey key = execution.key();
        if (key == null) {
            cache.recordDisabled();
            return calcPatternSlow(execution, controller, craftingJobId, false, tick);
        }

        ECOFastPathResult cached = cache.get(key, tick);
        if (cached != null) {
            if (cached.isNegative()) {
                cache.recordFallbackSlowPath();
                return calcPatternSlow(execution, controller, craftingJobId, false, tick);
            }
            FastPathWork fastPathWork = createFastPathWork(cached, execution);
            if (fastPathWork == null) {
                cache.putNegative(key, tick);
                cache.recordFallbackSlowPath();
                return calcPatternSlow(execution, controller, craftingJobId, false, tick);
            }
            if (!consumeCraftingCoolant(controller, 1)) {
                cache.recordCoolantReject();
                return false;
            }
            startWork(fastPathWork.outputs(), fastPathWork.inputs(), fastPathWork.remaining(), craftingJobId, 1);
            cache.recordFastPathAccepted();
            cache.maybeLogStats(worker.getBlockPos().toShortString(), tick);
            return true;
        }

        return calcPatternSlow(execution, controller, craftingJobId, true, tick);
    }

    private boolean canUseFastPath(ECOExtractedPatternExecution execution) {
        return execution.fastPathEligible()
            && NEConfig.ecoAe2FastPathEnabled
            && !NEConfig.postCraftingEvent;
    }

    @Nullable
    private FastPathWork createFastPathWork(ECOFastPathResult cached, ECOExtractedPatternExecution execution) {
        if (!cached.matchesExecution(execution)) {
            return null;
        }
        return new FastPathWork(cached.outputEntries(), cached.inputEntries(), cached.remainingEntries());
    }

    private boolean calcPatternSlow(
        ECOExtractedPatternExecution execution,
        ECOCraftingSystemBlockEntity controller,
        @Nullable UUID craftingJobId,
        boolean verifyFastPath,
        long tick
    ) {
        IMolecularAssemblerSupportedPattern pattern = execution.molecularPattern();
        if (pattern == null) {
            return false;
        }
        KeyCounter[] table = execution.craftingContainer();
        craftingInv.clearContent();
        pattern.fillCraftingGrid(table, craftingInv::setItem);
        ItemStack outputItem = pattern.assemble(craftingInv.asCraftInput(), worker.getLevel());
        if (outputItem.isEmpty()) {
            craftingInv.clearContent();
            return false;
        }
        if (!consumeCraftingCoolant(controller, 1)) {
            craftingInv.clearContent();
            return false;
        }

        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : pattern.getRemainingItems(craftingInv.asCraftInput())) {
            if (!item.isEmpty()) {
                list.add(item.copy());
            }
        }

        var outputStacks = ECOFastPathStacks.fromItemStack(outputItem);
        var remainingStacks = ECOFastPathStacks.fromItemStacks(list);
        if (outputStacks.isEmpty() || remainingStacks.isEmpty()) {
            craftingInv.clearContent();
            return false;
        }
        if (verifyFastPath) {
            verifyAndCacheFastPath(execution, outputStacks.get(), remainingStacks.get(), tick);
        }
        startWork(outputStacks.get(), execution.inputItems(), remainingStacks.get(), craftingJobId, 1);
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        cache.recordSlowPathAccepted();
        cache.maybeLogStats(worker.getBlockPos().toShortString(), tick);
        return true;
    }

    private void verifyAndCacheFastPath(
        ECOExtractedPatternExecution execution,
        List<GenericStack> outputEntries,
        List<GenericStack> remainingEntries,
        long tick
    ) {
        ECOFastPathKey key = execution.key();
        if (key == null) {
            return;
        }
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        if (!outputEntries.equals(execution.expectedOutputs())
            || !remainingEntries.equals(execution.expectedContainerItems())) {
            cache.putNegative(key, tick);
            return;
        }
        cache.putPositive(key, outputEntries, remainingEntries, execution.inputItems(), tick);
    }

    private boolean consumeCraftingCoolant(ECOCraftingSystemBlockEntity controller, int craftCount) {
        return !controller.isActiveCooling()
            || controller.tryConsumeCoolant(5 * Math.max(1, craftCount), controller.getEffectiveOverclockTimes());
    }

    private void startWork(
        List<GenericStack> outputs,
        List<GenericStack> inputs,
        List<GenericStack> remaining,
        @Nullable UUID craftingJobId,
        int occupiedThreadSlots
    ) {
        outputStacks.clear();
        outputStacks.addAll(ECOFastPathStacks.copyGenericStacks(outputs));
        this.craftingJobId = craftingJobId;
        this.occupiedThreadSlots = Math.max(1, occupiedThreadSlots);
        this.outputsReady = false;
        inputStacks.clear();
        inputStacks.addAll(ECOFastPathStacks.copyGenericStacks(inputs));
        remainingStacks.clear();
        remainingStacks.addAll(ECOFastPathStacks.copyGenericStacks(remaining));
        worker.onThreadWork(this.occupiedThreadSlots);
        isBusy = true;
        recoveryState = RecoveryState.ACTIVE;
        reboot = true;
        setChanged();
    }

    private int userPower(int ticksPassed, int bonusValue, double acceleratorTax) {
        var grid = this.worker.getMainNode().getGrid();
        if (grid != null) {
            double slotScaledTax = acceleratorTax * Math.max(1, occupiedThreadSlots);
            var safePower = Math.min(ticksPassed * bonusValue * slotScaledTax, 500000);
            return (int) (grid.getEnergyService().extractAEPower(safePower, Actionable.MODULATE, PowerMultiplier.CONFIG)
                / slotScaledTax);
        } else {
            return 0;
        }
    }

    private boolean ejectOutputs() {
        IGrid grid = worker.getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        CraftingService craftingService = (CraftingService) grid.getCraftingService();
        MEStorage storage = grid.getStorageService().getInventory();

        List<GenericStack> remainder = ejectAllAndCollectRemainder(
            craftingService, storage, outputStacks, remainingStacks
        );
        if (!remainder.isEmpty()) {
            retainRemainderForRetry(remainder, RecoveryState.ACTIVE);
            return false;
        }

        if (NEConfig.postCraftingEvent) {
            postCraftingEventSafely();
        }
        worker.onThreadStop(occupiedThreadSlots);
        clearWork();
        return true;
    }

    private boolean canInsertAll(MEStorage storage, List<GenericStack> stacks) {
        for (GenericStack stack : stacks) {
            long inserted = storage.insert(stack.what(), stack.amount(), Actionable.SIMULATE, actionSource);
            if (inserted != stack.amount()) {
                return false;
            }
        }
        return true;
    }

    private boolean canInsertAll(MEStorage storage, List<GenericStack> first, List<GenericStack> second) {
        return canInsertAll(storage, first) && canInsertAll(storage, second);
    }

    private List<GenericStack> ejectAllAndCollectRemainder(
        CraftingService craftingService,
        MEStorage storage,
        List<GenericStack> first,
        List<GenericStack> second
    ) {
        List<GenericStack> remainder = new ArrayList<>(first.size() + second.size());
        ejectAllIntoRemainder(craftingService, storage, first, remainder);
        ejectAllIntoRemainder(craftingService, storage, second, remainder);
        return List.copyOf(remainder);
    }

    private void ejectAllIntoRemainder(
        CraftingService craftingService,
        MEStorage storage,
        List<GenericStack> stacks,
        List<GenericStack> remainder
    ) {
        for (GenericStack stack : stacks) {
            long remaining = stack.amount();
            long insertedIntoCpus = craftingService.insertIntoCpus(stack.what(), remaining, Actionable.MODULATE);
            remaining -= insertedIntoCpus;

            if (remaining > 0) {
                long insertedIntoStorage = storage.insert(stack.what(), remaining, Actionable.MODULATE, actionSource);
                remaining -= insertedIntoStorage;
            }

            if (remaining > 0) {
                remainder.add(new GenericStack(stack.what(), remaining));
            }
        }
    }

    private List<GenericStack> insertAllAndCollectRemainder(MEStorage storage, List<GenericStack> stacks) {
        List<GenericStack> remainder = new ArrayList<>(stacks.size());
        insertAllIntoRemainder(storage, stacks, remainder);
        return List.copyOf(remainder);
    }

    private List<GenericStack> insertAllAndCollectRemainder(
        MEStorage storage,
        List<GenericStack> first,
        List<GenericStack> second
    ) {
        List<GenericStack> remainder = new ArrayList<>(first.size() + second.size());
        insertAllIntoRemainder(storage, first, remainder);
        insertAllIntoRemainder(storage, second, remainder);
        return List.copyOf(remainder);
    }

    private void insertAllIntoRemainder(MEStorage storage, List<GenericStack> stacks, List<GenericStack> remainder) {
        for (GenericStack stack : stacks) {
            long remaining = stack.amount();
            long inserted = storage.insert(stack.what(), remaining, Actionable.MODULATE, actionSource);
            remaining -= inserted;
            if (remaining > 0) {
                remainder.add(new GenericStack(stack.what(), remaining));
            }
        }
    }

    public boolean belongsToJob(UUID jobId) {
        return this.isBusy && jobId.equals(this.craftingJobId);
    }

    public boolean recoverInputsToNetwork(MEStorage storage) {
        if (!isRecoverableState()) {
            return true;
        }
        return recoverItemsToNetwork(storage, shouldRecoverOutputs());
    }

    private boolean retryRecoveryToNetwork() {
        IGrid grid = worker.getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        return recoverItemsToNetwork(grid.getStorageService().getInventory(), shouldRecoverOutputs());
    }

    private boolean recoverItemsToNetwork(MEStorage storage, boolean recoverOutputs) {
        if (recoverOutputs ? outputStacks.isEmpty() && remainingStacks.isEmpty() : inputStacks.isEmpty()) {
            recoveryState = RecoveryState.RECOVERED_TO_NETWORK;
            worker.onThreadStop(occupiedThreadSlots);
            clearWork();
            setChanged();
            return true;
        }
        boolean canInsert = recoverOutputs
            ? canInsertAll(storage, outputStacks, remainingStacks)
            : canInsertAll(storage, inputStacks);
        if (!canInsert) {
            markRecoveryPending(recoverOutputs);
            return false;
        }
        List<GenericStack> remainder = recoverOutputs
            ? insertAllAndCollectRemainder(storage, outputStacks, remainingStacks)
            : insertAllAndCollectRemainder(storage, inputStacks);
        if (!remainder.isEmpty()) {
            if (recoverOutputs) {
                retainRemainderForRetry(remainder, RecoveryState.RECOVERING_OUTPUTS);
            } else {
                retainInputRemainderForRetry(remainder);
            }
            return false;
        }
        recoveryState = RecoveryState.RECOVERED_TO_NETWORK;
        worker.onThreadStop(occupiedThreadSlots);
        clearWork();
        setChanged();
        return true;
    }

    public void dropRecoverablesAndClear(List<ItemStack> drops) {
        if (!isRecoverableState()) {
            return;
        }
        if (shouldRecoverOutputs()) {
            dropRecoverableStacks(drops, outputStacks);
            dropRecoverableStacks(drops, remainingStacks);
        } else {
            dropRecoverableStacks(drops, inputStacks);
        }
        recoveryState = RecoveryState.DROPPED_TO_WORLD;
        worker.onThreadStop(occupiedThreadSlots);
        clearWork();
        setChanged();
    }

    private void dropRecoverableStacks(List<ItemStack> drops, List<GenericStack> recoverable) {
        for (GenericStack stack : recoverable) {
            var itemStack = ECOFastPathStacks.toItemStack(stack);
            if (itemStack.isPresent()) {
                drops.add(itemStack.get());
            } else {
                LOGGER.error(
                    "ECO crafting thread cannot drop non-item recoverable stack: worker={} key={} amount={}",
                    worker.getBlockPos(),
                    stack.what(),
                    stack.amount()
                );
            }
        }
    }

    private boolean isRecoveringToNetwork() {
        return recoveryState == RecoveryState.RECOVERING_INPUTS
            || recoveryState == RecoveryState.RECOVERING_OUTPUTS;
    }

    private boolean isRecoverableState() {
        return isBusy
            && (recoveryState == RecoveryState.ACTIVE
                || recoveryState == RecoveryState.RECOVERING_INPUTS
                || recoveryState == RecoveryState.RECOVERING_OUTPUTS);
    }

    private boolean shouldRecoverOutputs() {
        return outputsReady || recoveryState == RecoveryState.RECOVERING_OUTPUTS;
    }

    private void markRecoveryPending(boolean recoverOutputs) {
        isBusy = true;
        reboot = true;
        if (recoverOutputs) {
            inputStacks.clear();
            outputsReady = true;
            recoveryState = RecoveryState.RECOVERING_OUTPUTS;
        } else {
            outputStacks.clear();
            remainingStacks.clear();
            outputsReady = false;
            recoveryState = RecoveryState.RECOVERING_INPUTS;
        }
        setChanged();
    }

    private void clearWork() {
        outputStacks.clear();
        inputStacks.clear();
        remainingStacks.clear();
        craftingInv.clearContent();
        craftingJobId = null;
        isBusy = false;
        reboot = true;
        progress = 0;
        occupiedThreadSlots = 1;
        outputsReady = false;
        recoveryState = RecoveryState.CLEARED;
    }

    private void retainRemainderForRetry(List<GenericStack> remainder, RecoveryState nextState) {
        outputStacks.clear();
        outputStacks.addAll(ECOFastPathStacks.copyGenericStacks(remainder));
        remainingStacks.clear();
        inputStacks.clear();
        isBusy = true;
        outputsReady = true;
        recoveryState = nextState;
        setChanged();
    }

    private void retainInputRemainderForRetry(List<GenericStack> remainder) {
        inputStacks.clear();
        inputStacks.addAll(ECOFastPathStacks.copyGenericStacks(remainder));
        outputStacks.clear();
        remainingStacks.clear();
        isBusy = true;
        outputsReady = false;
        recoveryState = RecoveryState.RECOVERING_INPUTS;
        setChanged();
    }

    private void postCraftingEventSafely() {
        try {
            NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(
                NEFakePlayer.getFakePlayer((ServerLevel) worker.getLevel()), firstOutputItem(), craftingInv
            ));
        } catch (RuntimeException | Error e) {
            LOGGER.warn("ECO crafting post-crafting event failed: worker={}", worker.getBlockPos(), e);
        }
    }

    private ItemStack firstOutputItem() {
        if (outputStacks.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ECOFastPathStacks.toItemStack(outputStacks.get(0)).orElse(ItemStack.EMPTY);
    }

    private static List<ItemStack> genericStacksToItemStacks(List<GenericStack> stacks) {
        List<ItemStack> items = new ArrayList<>();
        for (GenericStack stack : stacks) {
            ECOFastPathStacks.toItemStack(stack).ifPresent(items::add);
        }
        return List.copyOf(items);
    }

    public int getOccupiedThreadSlots() {
        return isBusy ? Math.max(1, occupiedThreadSlots) : 0;
    }

    private void setChanged() {
        worker.setChanged();
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isBusy", isBusy);
        tag.putBoolean("reboot", reboot);
        tag.putInt("progress", progress);
        tag.putInt("neoecoae_version", 3);
        tag.putInt("occupiedThreadSlots", occupiedThreadSlots);
        tag.putBoolean("outputsReady", outputsReady);
        tag.putString("recoveryState", recoveryState.name());
        if (craftingJobId != null) {
            tag.putUUID("craftingJobId", craftingJobId);
        }

        tag.put("outputStacks", serializeGenericStacks(provider, outputStacks));
        tag.put("inputStacks", serializeGenericStacks(provider, inputStacks));
        tag.put("remainingStacks", serializeGenericStacks(provider, remainingStacks));
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        this.isBusy = nbt.getBoolean("isBusy");
        this.reboot = nbt.getBoolean("reboot");
        this.progress = nbt.getInt("progress");
        this.occupiedThreadSlots = Math.max(
            1, nbt.contains("occupiedThreadSlots") ? nbt.getInt("occupiedThreadSlots") : 1
        );
        this.outputsReady = nbt.getBoolean("outputsReady");
        this.craftingJobId = nbt.hasUUID("craftingJobId") ? nbt.getUUID("craftingJobId") : null;
        this.recoveryState = readRecoveryState(nbt);

        outputStacks.clear();
        inputStacks.clear();
        remainingStacks.clear();

        if (nbt.contains("outputStacks", Tag.TAG_LIST)) {
            outputStacks.addAll(deserializeGenericStacks(provider, nbt.getList("outputStacks", Tag.TAG_COMPOUND)));
            inputStacks.addAll(deserializeGenericStacks(provider, nbt.getList("inputStacks", Tag.TAG_COMPOUND)));
            remainingStacks.addAll(deserializeGenericStacks(provider, nbt.getList("remainingStacks", Tag.TAG_COMPOUND)));
        } else {
            outputStacks.addAll(deserializeLegacyItemStacks(provider, nbt, "outputItems", "outputItem"));
            inputStacks.addAll(deserializeLegacyItemStacks(provider, nbt, "inputItems", null));
            remainingStacks.addAll(deserializeLegacyItemStacks(provider, nbt, "remainingItems", null));
        }
    }

    private static ListTag serializeGenericStacks(HolderLookup.Provider provider, List<GenericStack> stacks) {
        ListTag tag = new ListTag();
        for (GenericStack stack : stacks) {
            tag.add(GenericStack.writeTag(provider, stack));
        }
        return tag;
    }

    private static List<GenericStack> deserializeGenericStacks(HolderLookup.Provider provider, ListTag tag) {
        List<GenericStack> stacks = new ArrayList<>();
        for (int i = 0; i < tag.size(); i++) {
            GenericStack stack = GenericStack.readTag(provider, tag.getCompound(i));
            if (stack != null && stack.amount() > 0) {
                stacks.add(stack);
            }
        }
        return List.copyOf(stacks);
    }

    private static List<GenericStack> deserializeLegacyItemStacks(
        HolderLookup.Provider provider,
        CompoundTag nbt,
        String listKey,
        @Nullable String singleKey
    ) {
        List<ItemStack> itemStacks = new ArrayList<>();
        ListTag items = nbt.getList(listKey, Tag.TAG_COMPOUND);
        if (!items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                ItemStack item = ItemStack.parseOptional(provider, items.getCompound(i));
                if (!item.isEmpty()) {
                    itemStacks.add(item);
                }
            }
        } else if (singleKey != null) {
            ItemStack item = ItemStack.parseOptional(provider, nbt.getCompound(singleKey));
            if (!item.isEmpty()) {
                itemStacks.add(item);
            }
        }
        return ECOFastPathStacks.fromItemStacks(itemStacks).orElse(List.of());
    }

    private RecoveryState readRecoveryState(CompoundTag nbt) {
        if (nbt.contains("recoveryState", Tag.TAG_STRING)) {
            try {
                return RecoveryState.valueOf(nbt.getString("recoveryState"));
            } catch (IllegalArgumentException ignored) {
                return this.isBusy ? RecoveryState.ACTIVE : RecoveryState.CLEARED;
            }
        }
        return this.isBusy ? RecoveryState.ACTIVE : RecoveryState.CLEARED;
    }

    private record FastPathWork(
        List<GenericStack> outputs,
        List<GenericStack> inputs,
        List<GenericStack> remaining
    ) {}

    public record Snapshot(
        boolean busy,
        int progress,
        int maxProgress,
        int occupiedThreadSlots,
        ItemStack outputItem,
        List<ItemStack> remainingItems,
        boolean outputsReady
    ) {}
}
