package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
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
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.slf4j.Logger;

public class ECOCraftingThread implements INBTSerializable<CompoundTag> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MAX_PROGRESS = 100;

    private final ECOCraftingWorkerBlockEntity worker;
    private final IActionSource actionSource;

    @Getter
    private boolean isBusy = false;

    private boolean reboot = true;

    private final List<GenericStack> outputStacks = new ArrayList<>();

    private final List<GenericStack> inputStacks = new ArrayList<>();

    private final List<GenericStack> remainingStacks = new ArrayList<>();

    @Nullable private UUID craftingJobId = null;

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

    /**
     * 宸ヤ綔 tick 鏂规硶
     *
     * @param overlockTimes 瓒呴娆℃暟锛堟瘡娆¤秴棰戝噺灏?tick鏃堕棿锛?
     * @param powerMultiply 鑳介噺鍊嶇巼锛堢敤浜庤秴棰戞棤鍐峰嵈鐨勬儏鍐碉級
     * @param ticksSinceLastCall 璺濈涓婁紶璋冪敤澶氬皯 tick
     */
    public TickRateModulation tick(int overlockTimes, int powerMultiply, int ticksSinceLastCall) {
        if (!isBusy) {
            return TickRateModulation.SLEEP;
        }
        if (isRecoveringToNetwork()) {
            retryRecoveryToNetwork();
            return TickRateModulation.URGENT;
        }
        if (outputsReady) {
            return TickRateModulation.URGENT;
        }
        ticksSinceLastCall = consumeEffectiveTicks(ticksSinceLastCall);
        int bonusValue = Math.min(10 + overlockTimes * 10, 100);
        progress += userPower(ticksSinceLastCall, bonusValue, powerMultiply);

        return markOutputsReadyIfComplete();
    }

    /**
     * Compute the AE power this thread would request from the grid
     * <em>without</em> actually extracting it.
     * Used by the worker to aggregate power extraction across all threads.
     *
     * @return the {@code safePower} value this thread would pass to
     *         {@code extractAEPower}, or 0 if idle / output-ready.
     */
    public int computePowerNeed(int ticksSinceLastCall, int bonusValue, double acceleratorTax) {
        if (!isBusy || outputsReady || isRecoveringToNetwork()) {
            return 0;
        }
        int effectiveTicks = this.reboot ? 1 : ticksSinceLastCall;
        double slotScaledTax = acceleratorTax * Math.max(1, occupiedThreadSlots);
        return (int) Math.min(effectiveTicks * bonusValue * slotScaledTax, 500_000);
    }

    /**
     * Tick variant that uses a pre-extracted power budget instead of
     * calling {@code grid.getEnergyService().extractAEPower()} individually.
     *
     * @param extractedPower the AE power already extracted from the grid
     *                       on behalf of this thread (after proportional scaling).
     */
    public TickRateModulation tickAggregated(
            int overlockTimes, int powerMultiply, int ticksSinceLastCall, double extractedPower) {
        if (!isBusy) {
            return TickRateModulation.SLEEP;
        }
        if (isRecoveringToNetwork()) {
            retryRecoveryToNetwork();
            return TickRateModulation.URGENT;
        }
        if (outputsReady) {
            return TickRateModulation.URGENT;
        }
        ticksSinceLastCall = consumeEffectiveTicks(ticksSinceLastCall);
        double slotScaledTax = powerMultiply * Math.max(1, occupiedThreadSlots);
        progress += (int) (extractedPower / slotScaledTax);

        return markOutputsReadyIfComplete();
    }

    private int consumeEffectiveTicks(int ticksSinceLastCall) {
        if (this.reboot) {
            ticksSinceLastCall = 1;
        }
        this.reboot = false;
        return ticksSinceLastCall;
    }

    private TickRateModulation markOutputsReadyIfComplete() {
        if (this.progress >= MAX_PROGRESS) {
            outputsReady = true;
            setChanged();
        }
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
                isBusy,
                progress,
                MAX_PROGRESS,
                getOccupiedThreadSlots(),
                getOutputItem(),
                getRemainingItems(),
                craftingJobId);
    }

    /**
     * 鎻愪氦鏍锋澘
     *
     * @param pattern    瑕佹彁浜ょ殑鏍锋澘
     * @param controller
     * @return 鏄惁鎴愬姛
     */
    public boolean pushPattern(
            IMolecularAssemblerSupportedPattern pattern, KeyCounter[] table, ECOCraftingSystemBlockEntity controller) {
        return pushPattern(pattern, table, controller, null);
    }

    public boolean pushPattern(
            IMolecularAssemblerSupportedPattern pattern,
            KeyCounter[] table,
            ECOCraftingSystemBlockEntity controller,
            @Nullable UUID craftingJobId) {
        return pushPattern(ECOExtractedPatternExecution.slow(pattern, table), controller, craftingJobId);
    }

    public boolean pushPattern(
            ECOExtractedPatternExecution execution,
            ECOCraftingSystemBlockEntity controller,
            @Nullable UUID craftingJobId) {
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
                request.batchSize());
        return acceptBatch(work, controller);
    }

    private boolean acceptBatch(ECOBatchCraftingWork work, ECOCraftingSystemBlockEntity controller) {
        if (!consumeCraftingCoolant(controller, work.batchSize())) {
            worker.getFastPathCache().recordCoolantReject();
            return false;
        }
        startWork(
                work.outputTotal(),
                work.inputTotal(),
                work.remainingTotal(),
                work.craftingJobId(),
                work.occupiedThreadSlots());
        worker.getFastPathCache().recordFastPathAccepted();
        return true;
    }

    private boolean acceptPattern(
            ECOExtractedPatternExecution execution,
            ECOCraftingSystemBlockEntity controller,
            @Nullable UUID craftingJobId) {
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        long tick = appeng.hooks.ticking.TickHandler.instance().getCurrentTick();
        ECOFastPathKey key = execution.key();
        if (!ECOFastPathEligibility.canUse(execution, key)) {
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

    @Nullable private FastPathWork createFastPathWork(ECOFastPathResult cached, ECOExtractedPatternExecution execution) {
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
            long tick) {
        IMolecularAssemblerSupportedPattern pattern = execution.molecularPattern();
        if (pattern == null) {
            return false;
        }
        KeyCounter[] table = execution.craftingContainer();
        craftingInv.clearContent();
        pattern.fillCraftingGrid(table, craftingInv::setItem);
        ItemStack outputItem = pattern.assemble(craftingInv, worker.getLevel());
        if (outputItem.isEmpty()) {
            craftingInv.clearContent();
            return false;
        }
        if (!consumeCraftingCoolant(controller, 1)) {
            craftingInv.clearContent();
            return false;
        }

        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : pattern.getRemainingItems(craftingInv)) {
            if (!item.isEmpty()) {
                list.add(item.copy());
            }
        }

        var outputEntries = ECOFastPathStacks.fromItemStack(outputItem);
        var remainingEntries = ECOFastPathStacks.fromItemStacks(list);
        if (outputEntries.isEmpty() || remainingEntries.isEmpty()) {
            craftingInv.clearContent();
            return false;
        }
        if (verifyFastPath) {
            verifyAndCacheFastPath(execution, outputEntries.get(), remainingEntries.get(), tick);
        }
        startWork(outputEntries.get(), execution.inputItems(), remainingEntries.get(), craftingJobId, 1);
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        cache.recordSlowPathAccepted();
        cache.maybeLogStats(worker.getBlockPos().toShortString(), tick);
        return true;
    }

    private void verifyAndCacheFastPath(
            ECOExtractedPatternExecution execution,
            List<GenericStack> outputEntries,
            List<GenericStack> remainingEntries,
            long tick) {
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
            int occupiedThreadSlots) {
        outputStacks.clear();
        outputStacks.addAll(copyGenericStacks(outputs));
        this.craftingJobId = craftingJobId;
        this.occupiedThreadSlots = Math.max(1, occupiedThreadSlots);
        this.outputsReady = false;
        inputStacks.clear();
        inputStacks.addAll(copyGenericStacks(inputs));
        remainingStacks.clear();
        remainingStacks.addAll(copyGenericStacks(remaining));
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

    public boolean isOutputReady() {
        return isBusy && outputsReady;
    }

    public void applyOutputFlush(KeyCounter acceptedOutputs) {
        if (!isOutputReady()) {
            return;
        }

        KeyCounter remainder = new KeyCounter();
        for (Object2LongMap.Entry<AEKey> entry : collectOutputItems()) {
            long accepted = Math.min(entry.getLongValue(), acceptedOutputs.get(entry.getKey()));
            if (accepted > 0) {
                acceptedOutputs.remove(entry.getKey(), accepted);
            }
            long remaining = entry.getLongValue() - accepted;
            if (remaining > 0) {
                remainder.add(entry.getKey(), remaining);
            }
        }

        if (!remainder.isEmpty()) {
            retainRemainderForRetry(remainder);
            return;
        }

        if (NEConfig.postCraftingEvent) {
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(
                    NEFakePlayer.getFakePlayer((ServerLevel) worker.getLevel()), firstOutputItem(), craftingInv));
        }

        int slotsToRelease = occupiedThreadSlots;
        clearWork();
        worker.onThreadStop(slotsToRelease);
        setChanged();
    }

    public KeyCounter collectOutputItems() {
        KeyCounter outputs = new KeyCounter();
        for (GenericStack outputStack : outputStacks) {
            addStack(outputs, outputStack);
        }
        for (GenericStack remainingStack : remainingStacks) {
            addStack(outputs, remainingStack);
        }
        return outputs;
    }

    private static void addStack(KeyCounter counter, GenericStack stack) {
        if (stack != null && stack.amount() > 0) {
            counter.add(stack.what(), stack.amount());
        }
    }

    private void retainRemainderForRetry(KeyCounter remainder) {
        List<GenericStack> retained = ECOFastPathStacks.copyCounterUnsorted(remainder);
        boolean changed = !outputsReady || !outputStacks.equals(retained) || !remainingStacks.isEmpty();
        outputStacks.clear();
        outputStacks.addAll(retained);
        remainingStacks.clear();
        inputStacks.clear();
        outputsReady = true;
        isBusy = true;
        recoveryState = RecoveryState.ACTIVE;
        if (changed) {
            setChanged();
        }
    }

    private List<GenericStack> insertAllAndCollectRemainder(MEStorage storage, List<GenericStack> stacks) {
        List<GenericStack> remainder = new ArrayList<>();
        for (GenericStack stack : stacks) {
            long remaining = stack.amount();
            long inserted = storage.insert(stack.what(), remaining, Actionable.MODULATE, actionSource);
            remaining -= inserted;
            if (remaining > 0) {
                remainder.add(new GenericStack(stack.what(), remaining));
            }
        }
        return List.copyOf(remainder);
    }

    private List<GenericStack> insertAllAndCollectRemainder(
            MEStorage storage, List<GenericStack> first, List<GenericStack> second) {
        List<GenericStack> remainder = new ArrayList<>(first.size() + second.size());
        remainder.addAll(insertAllAndCollectRemainder(storage, first));
        remainder.addAll(insertAllAndCollectRemainder(storage, second));
        return List.copyOf(remainder);
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
        var grid = this.worker.getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        return recoverItemsToNetwork(grid.getStorageService().getInventory(), shouldRecoverOutputs());
    }

    private boolean recoverItemsToNetwork(MEStorage storage, boolean recoverOutputs) {
        List<GenericStack> remainder = recoverOutputs
                ? insertAllAndCollectRemainder(storage, outputStacks, remainingStacks)
                : insertAllAndCollectRemainder(storage, inputStacks);
        if (!remainder.isEmpty()) {
            if (recoverOutputs) {
                retainRemainderForRecovery(remainder);
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
            ECOFastPathStacks.toItemStack(stack)
                    .ifPresentOrElse(
                            drops::add,
                            () -> LOGGER.error(
                                    "ECO crafting thread cannot drop non-item recoverable stack: worker={} key={} amount={}",
                                    worker.getBlockPos(),
                                    stack.what(),
                                    stack.amount()));
        }
    }

    private boolean isRecoveringToNetwork() {
        return recoveryState == RecoveryState.RECOVERING_INPUTS || recoveryState == RecoveryState.RECOVERING_OUTPUTS;
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

    private void retainRemainderForRecovery(List<GenericStack> remainder) {
        outputStacks.clear();
        outputStacks.addAll(copyGenericStacks(remainder));
        remainingStacks.clear();
        inputStacks.clear();
        outputsReady = true;
        isBusy = true;
        recoveryState = RecoveryState.RECOVERING_OUTPUTS;
        setChanged();
    }

    private void retainInputRemainderForRetry(List<GenericStack> remainder) {
        inputStacks.clear();
        inputStacks.addAll(copyGenericStacks(remainder));
        outputStacks.clear();
        remainingStacks.clear();
        outputsReady = false;
        isBusy = true;
        recoveryState = RecoveryState.RECOVERING_INPUTS;
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

    private ItemStack firstOutputItem() {
        if (outputStacks.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ECOFastPathStacks.toItemStack(outputStacks.get(0)).orElse(ItemStack.EMPTY);
    }

    public int getOccupiedThreadSlots() {
        return isBusy ? Math.max(1, occupiedThreadSlots) : 0;
    }

    private void setChanged() {
        worker.setChanged();
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT() {
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
        tag.put("outputItem", firstOutputItem().save(new CompoundTag()));
        tag.put("outputStacks", ECOFastPathStacks.writeGenericStacks(outputStacks));
        tag.put("inputStacks", ECOFastPathStacks.writeGenericStacks(inputStacks));
        tag.put("remainingStacks", ECOFastPathStacks.writeGenericStacks(remainingStacks));
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.isBusy = nbt.getBoolean("isBusy");
        this.reboot = nbt.getBoolean("reboot");
        this.progress = nbt.getInt("progress");
        this.occupiedThreadSlots =
                Math.max(1, nbt.contains("occupiedThreadSlots") ? nbt.getInt("occupiedThreadSlots") : 1);
        this.outputsReady = nbt.getBoolean("outputsReady");
        this.recoveryState = readRecoveryState(nbt);
        outputStacks.clear();
        inputStacks.clear();
        remainingStacks.clear();
        if (nbt.contains("outputStacks", Tag.TAG_LIST)) {
            outputStacks.addAll(ECOFastPathStacks.readGenericStacks(nbt.getList("outputStacks", Tag.TAG_COMPOUND)));
            inputStacks.addAll(ECOFastPathStacks.readGenericStacks(nbt.getList("inputStacks", Tag.TAG_COMPOUND)));
            remainingStacks.addAll(ECOFastPathStacks.readGenericStacks(nbt.getList("remainingStacks", Tag.TAG_COMPOUND)));
        } else {
            outputStacks.addAll(deserializeLegacyItemStacks(nbt, "outputItems", "outputItem"));
            inputStacks.addAll(deserializeLegacyItemStacks(nbt, "inputItems", null));
            remainingStacks.addAll(deserializeLegacyItemStacks(nbt, "remainingItems", null));
        }
        this.craftingJobId = nbt.hasUUID("craftingJobId") ? nbt.getUUID("craftingJobId") : null;
    }

    private static List<GenericStack> deserializeLegacyItemStacks(
            CompoundTag nbt, String listKey, @Nullable String singleKey) {
        List<ItemStack> itemStacks = new ArrayList<>();
        ListTag items = nbt.getList(listKey, Tag.TAG_COMPOUND);
        if (!items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                ItemStack item = ItemStack.of(items.getCompound(i));
                if (!item.isEmpty()) {
                    itemStacks.add(item);
                }
            }
        } else if (singleKey != null) {
            ItemStack item = ItemStack.of(nbt.getCompound(singleKey));
            if (!item.isEmpty()) {
                itemStacks.add(item);
            }
        }
        return ECOFastPathStacks.fromItemStacks(itemStacks).orElse(List.of());
    }

    private RecoveryState readRecoveryState(CompoundTag nbt) {
        if (!nbt.contains("recoveryState")) {
            return this.isBusy ? RecoveryState.ACTIVE : RecoveryState.CLEARED;
        }
        try {
            return RecoveryState.valueOf(nbt.getString("recoveryState"));
        } catch (IllegalArgumentException e) {
            return this.isBusy ? RecoveryState.ACTIVE : RecoveryState.CLEARED;
        }
    }

    private enum RecoveryState {
        ACTIVE,
        RECOVERING_INPUTS,
        RECOVERING_OUTPUTS,
        RECOVERED_TO_NETWORK,
        DROPPED_TO_WORLD,
        CLEARED
    }

    private static List<GenericStack> copyGenericStacks(List<GenericStack> source) {
        List<GenericStack> copy = new ArrayList<>();
        for (GenericStack stack : source) {
            if (stack != null && stack.amount() > 0) {
                copy.add(new GenericStack(stack.what(), stack.amount()));
            }
        }
        return List.copyOf(copy);
    }

    private static List<ItemStack> genericStacksToItemStacks(List<GenericStack> stacks) {
        List<ItemStack> items = new ArrayList<>();
        for (GenericStack stack : stacks) {
            ECOFastPathStacks.toItemStack(stack).ifPresent(items::add);
        }
        return List.copyOf(items);
    }

    private record FastPathWork(List<GenericStack> outputs, List<GenericStack> inputs, List<GenericStack> remaining) {}

    public record Snapshot(
            boolean busy,
            int progress,
            int maxProgress,
            int occupiedThreadSlots,
            ItemStack outputItem,
            List<ItemStack> remainingItems,
            @Nullable UUID craftingJobId) {}
}
