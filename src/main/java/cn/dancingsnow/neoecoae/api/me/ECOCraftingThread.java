package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
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
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.annotations.Nullable;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ECOCraftingThread implements INBTSerializable<CompoundTag> {
    public static final int MAX_PROGRESS = 100;

    private final ECOCraftingWorkerBlockEntity worker;
    private final IActionSource actionSource;

    @Getter
    private boolean isBusy = false;

    private boolean reboot = true;

    private final List<ItemStack> outputItems = new ArrayList<>();

    private final List<ItemStack> inputItems = new ArrayList<>();

    private final List<ItemStack> remainingItems = new ArrayList<>();

    @Nullable
    private UUID craftingJobId = null;

    private int progress = 0;
    private int occupiedThreadSlots = 1;
    private boolean outputsReady = false;

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
            progress = 0;
            setChanged();
            return TickRateModulation.SLEEP;
        }
        if (this.reboot) {
            ticksSinceLastCall = 1;
        }

        this.reboot = false;
        int bonusValue = Math.min(10 + overlockTimes * 10, 100);
        progress += userPower(ticksSinceLastCall, bonusValue, powerMultiply);

        if (this.progress >= MAX_PROGRESS) {
            outputsReady = true;
            int slotsToRelease = occupiedThreadSlots;
            if (ejectOutputs()) {
                worker.onThreadStop(slotsToRelease);
                isBusy = false;
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
        return copyStacks(remainingItems);
    }

    public Snapshot createSnapshot() {
        return new Snapshot(
            isBusy,
            progress,
            MAX_PROGRESS,
            getOccupiedThreadSlots(),
            getOutputItem(),
            getRemainingItems()
        );
    }

    /**
     * 鎻愪氦鏍锋澘
     *
     * @param pattern    瑕佹彁浜ょ殑鏍锋澘
     * @param controller
     * @return 鏄惁鎴愬姛
     */
    public boolean pushPattern(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] table, ECOCraftingSystemBlockEntity controller) {
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
        var outputs = ECOFastPathStacks.toItemStacks(work.outputTotal());
        var inputs = ECOFastPathStacks.toItemStacks(work.inputTotal());
        var remaining = ECOFastPathStacks.toItemStacks(work.remainingTotal());
        if (outputs.isEmpty() || inputs.isEmpty() || remaining.isEmpty()) {
            worker.getFastPathCache().recordNonItemKey();
            return false;
        }
        if (!consumeCraftingCoolant(controller, work.batchSize())) {
            worker.getFastPathCache().recordCoolantReject();
            return false;
        }
        startWork(outputs.get(), inputs.get(), remaining.get(), work.craftingJobId(), work.occupiedThreadSlots());
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
        ECOFastPathKey key = execution.key();
        if (!canUseFastPath(execution, key)) {
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
            startWork(List.of(fastPathWork.output()), fastPathWork.inputs(), fastPathWork.remaining(), craftingJobId, 1);
            cache.recordFastPathAccepted();
            cache.maybeLogStats(worker.getBlockPos().toShortString(), tick);
            return true;
        }

        return calcPatternSlow(execution, controller, craftingJobId, true, tick);
    }

    private boolean canUseFastPath(ECOExtractedPatternExecution execution, @Nullable ECOFastPathKey key) {
        return key != null
            && execution.fastPathEligible()
            && NEConfig.isEcoAe2FastPathEnabled()
            && !NEConfig.postCraftingEvent;
    }

    @Nullable
    private FastPathWork createFastPathWork(ECOFastPathResult cached, ECOExtractedPatternExecution execution) {
        if (!cached.matchesExecution(execution)) {
            return null;
        }
        var output = ECOFastPathStacks.toSingleItemStack(cached.outputEntries());
        var inputs = ECOFastPathStacks.toItemStacks(cached.inputEntries());
        var remaining = ECOFastPathStacks.toItemStacks(cached.remainingEntries());
        if (output.isEmpty() || inputs.isEmpty() || remaining.isEmpty()) {
            return null;
        }
        return new FastPathWork(output.get(), inputs.get(), remaining.get());
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

        List<ItemStack> inputs = snapshotCraftingInputs();
        if (verifyFastPath) {
            verifyAndCacheFastPath(execution, outputItem, inputs, list, tick);
        }
        startWork(List.of(outputItem.copy()), inputs, list, craftingJobId, 1);
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        cache.recordSlowPathAccepted();
        cache.maybeLogStats(worker.getBlockPos().toShortString(), tick);
        return true;
    }

    private void verifyAndCacheFastPath(
        ECOExtractedPatternExecution execution,
        ItemStack outputItem,
        List<ItemStack> inputs,
        List<ItemStack> remaining,
        long tick
    ) {
        ECOFastPathKey key = execution.key();
        if (key == null) {
            return;
        }
        ECOCraftingFastPathCache cache = worker.getFastPathCache();
        var outputEntries = ECOFastPathStacks.fromItemStack(outputItem);
        var inputEntries = ECOFastPathStacks.fromItemStacks(inputs);
        var remainingEntries = ECOFastPathStacks.fromItemStacks(remaining);
        if (outputEntries.isEmpty() || inputEntries.isEmpty() || remainingEntries.isEmpty()) {
            cache.putNegative(key, tick);
            return;
        }
        if (!outputEntries.get().equals(execution.expectedOutputs())
            || !remainingEntries.get().equals(execution.expectedContainerItems())
            || !inputEntries.get().equals(execution.inputItems())) {
            cache.putNegative(key, tick);
            return;
        }
        cache.putPositive(key, outputEntries.get(), remainingEntries.get(), inputEntries.get(), tick);
    }

    private boolean consumeCraftingCoolant(ECOCraftingSystemBlockEntity controller, int craftCount) {
        return !controller.isActiveCooling()
            || controller.tryConsumeCoolant(5 * Math.max(1, craftCount), controller.getEffectiveOverclockTimes());
    }

    private void startWork(
        List<ItemStack> outputs,
        List<ItemStack> inputs,
        List<ItemStack> remaining,
        @Nullable UUID craftingJobId,
        int occupiedThreadSlots
    ) {
        outputItems.clear();
        copyStacks(outputs, outputItems);
        this.craftingJobId = craftingJobId;
        this.occupiedThreadSlots = Math.max(1, occupiedThreadSlots);
        this.outputsReady = false;
        inputItems.clear();
        copyStacks(inputs, inputItems);
        remainingItems.clear();
        copyStacks(remaining, remainingItems);
        worker.onThreadWork(this.occupiedThreadSlots);
        isBusy = true;
        reboot = true;
        setChanged();
    }

    private static void copyStacks(List<ItemStack> source, List<ItemStack> target) {
        for (ItemStack stack : source) {
            if (!stack.isEmpty()) {
                target.add(stack.copy());
            }
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> source) {
        List<ItemStack> copy = new ArrayList<>();
        copyStacks(source, copy);
        return List.copyOf(copy);
    }

    private List<ItemStack> snapshotCraftingInputs() {
        List<ItemStack> inputs = new ArrayList<>();
        for (int slot = 0; slot < craftingInv.getContainerSize(); slot++) {
            ItemStack stack = craftingInv.getItem(slot);
            if (!stack.isEmpty()) {
                inputs.add(stack.copy());
            }
        }
        return inputs;
    }

    private int userPower(int ticksPassed, int bonusValue, double acceleratorTax) {
        var grid = this.worker.getMainNode().getGrid();
        if (grid != null) {
            double slotScaledTax = acceleratorTax * Math.max(1, occupiedThreadSlots);
            var safePower = Math.min(ticksPassed * bonusValue * slotScaledTax, 500000);
            return (int) (grid.getEnergyService().extractAEPower(safePower, Actionable.MODULATE, PowerMultiplier.CONFIG) / slotScaledTax);
        } else {
            return 0;
        }
    }

    private boolean ejectOutputs() {
        IGrid grid = worker.getMainNode().getGrid();
        if (grid != null) {
            MEStorage storage = grid.getStorageService().getInventory();
            KeyCounter outputs = collectOutputItems();
            if (!canInsertAll(storage, outputs)) {
                return false;
            }
            insertAll(storage, outputs);
            if (NEConfig.postCraftingEvent) {
                MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(
                    NEFakePlayer.getFakePlayer((ServerLevel) worker.getLevel()),
                    firstOutputItem(),
                    craftingInv
                ));
            }
            clearWork();
            return true;
        } else {
            return false;
        }
    }

    private KeyCounter collectOutputItems() {
        KeyCounter outputs = new KeyCounter();
        for (ItemStack outputItem : outputItems) {
            addStack(outputs, outputItem);
        }
        for (ItemStack remainingItem : remainingItems) {
            addStack(outputs, remainingItem);
        }
        return outputs;
    }

    private static void addStack(KeyCounter counter, ItemStack stack) {
        if (!stack.isEmpty()) {
            counter.add(AEItemKey.of(stack), stack.getCount());
        }
    }

    private boolean canInsertAll(MEStorage storage, KeyCounter stacks) {
        for (Object2LongMap.Entry<AEKey> entry : stacks) {
            long inserted = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE, actionSource);
            if (inserted != entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }

    private void insertAll(MEStorage storage, KeyCounter stacks) {
        for (Object2LongMap.Entry<AEKey> entry : stacks) {
            storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, actionSource);
        }
    }

    public boolean belongsToJob(UUID jobId) {
        return this.isBusy && jobId.equals(this.craftingJobId);
    }

    public boolean recoverInputsToNetwork(MEStorage storage) {
        if (!isBusy) {
            return true;
        }
        List<ItemStack> recoverable = outputsReady ? outputAndRemainingItems() : inputItems;
        if (recoverable.isEmpty()) {
            return false;
        }
        KeyCounter stacks = collectStacks(recoverable);
        if (!canInsertAll(storage, stacks)) {
            return false;
        }
        insertAll(storage, stacks);
        worker.onThreadStop(occupiedThreadSlots);
        clearWork();
        setChanged();
        return true;
    }

    public void addRecoverableDrops(List<ItemStack> drops) {
        if (!isBusy) {
            return;
        }
        List<ItemStack> recoverable = outputsReady ? outputAndRemainingItems() : inputItems;
        for (ItemStack stack : recoverable) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    private static KeyCounter collectStacks(List<ItemStack> stacks) {
        KeyCounter counter = new KeyCounter();
        for (ItemStack stack : stacks) {
            addStack(counter, stack);
        }
        return counter;
    }

    private List<ItemStack> outputAndRemainingItems() {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.addAll(outputItems);
        stacks.addAll(remainingItems);
        return stacks;
    }

    private void clearWork() {
        outputItems.clear();
        inputItems.clear();
        remainingItems.clear();
        craftingJobId = null;
        isBusy = false;
        reboot = true;
        progress = 0;
        occupiedThreadSlots = 1;
        outputsReady = false;
    }

    private ItemStack firstOutputItem() {
        return outputItems.isEmpty() ? ItemStack.EMPTY : outputItems.get(0);
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
        tag.putInt("neoecoae_version", 2);
        tag.putInt("occupiedThreadSlots", occupiedThreadSlots);
        tag.putBoolean("outputsReady", outputsReady);
        if (craftingJobId != null) {
            tag.putUUID("craftingJobId", craftingJobId);
        }
        tag.put("outputItem", firstOutputItem().save(new CompoundTag()));
        ListTag outputs = new ListTag();
        for (ItemStack outputItem : outputItems) {
            outputs.add(outputItem.save(new CompoundTag()));
        }
        tag.put("outputItems", outputs);
        ListTag inputs = new ListTag();
        for (ItemStack inputItem : inputItems) {
            inputs.add(inputItem.save(new CompoundTag()));
        }
        tag.put("inputItems", inputs);
        ListTag remaining = new ListTag();
        for (ItemStack remainingItem : remainingItems) {
            remaining.add(remainingItem.save(new CompoundTag()));
        }
        tag.put("remainingItems", remaining);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.isBusy = nbt.getBoolean("isBusy");
        this.reboot = nbt.getBoolean("reboot");
        this.progress = nbt.getInt("progress");
        this.occupiedThreadSlots = Math.max(1, nbt.contains("occupiedThreadSlots") ? nbt.getInt("occupiedThreadSlots") : 1);
        this.outputsReady = nbt.getBoolean("outputsReady");
        outputItems.clear();
        ListTag outputs = nbt.getList("outputItems", Tag.TAG_COMPOUND);
        if (!outputs.isEmpty()) {
            for (int i = 0; i < outputs.size(); i++) {
                ItemStack output = ItemStack.of(outputs.getCompound(i));
                if (!output.isEmpty()) {
                    outputItems.add(output);
                }
            }
        } else {
            ItemStack output = ItemStack.of(nbt.getCompound("outputItem"));
            if (!output.isEmpty()) {
                outputItems.add(output);
            }
        }
        this.craftingJobId = nbt.hasUUID("craftingJobId") ? nbt.getUUID("craftingJobId") : null;
        ListTag inputs = nbt.getList("inputItems", Tag.TAG_COMPOUND);
        inputItems.clear();
        for (int i = 0; i < inputs.size(); i++) {
            inputItems.add(ItemStack.of(inputs.getCompound(i)));
        }
        ListTag remaining = nbt.getList("remainingItems", Tag.TAG_COMPOUND);
        remainingItems.clear();
        for (int i = 0; i < remaining.size(); i++) {
            remainingItems.add(ItemStack.of(remaining.getCompound(i)));
        }
    }

    private record FastPathWork(ItemStack output, List<ItemStack> inputs, List<ItemStack> remaining) {
    }

    public record Snapshot(
        boolean busy,
        int progress,
        int maxProgress,
        int occupiedThreadSlots,
        ItemStack outputItem,
        List<ItemStack> remainingItems
    ) {
    }
}
