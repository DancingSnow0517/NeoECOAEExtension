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
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.annotations.Nullable;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ECOCraftingThread implements INBTSerializable<CompoundTag> {
    public static final int MAX_PROGRESS = 100;

    private final ECOCraftingWorkerBlockEntity worker;
    private final IActionSource actionSource;

    @Getter
    private boolean isBusy = false;

    private boolean reboot = true;

    private ItemStack outputItem = ItemStack.EMPTY;

    private final List<ItemStack> inputItems = new ArrayList<>();

    private final List<ItemStack> remainingItems = new ArrayList<>();

    @Nullable
    private UUID craftingJobId = null;

    private int progress = 0;

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
            if (ejectOutputs()) {
                worker.onThreadStop();
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
        if (isBusy) {
            return false;
        }

        return calcPattern(pattern, table, controller, craftingJobId);
    }

    private boolean calcPattern(
        IMolecularAssemblerSupportedPattern pattern,
        KeyCounter[] table,
        ECOCraftingSystemBlockEntity controller,
        @Nullable UUID craftingJobId
    ) {
        PatternCacheKey cacheKey = PatternCacheKey.of(pattern, table, worker.getLevel());
        PatternCacheEntry cached = cacheKey == null || NEConfig.postCraftingEvent ? null : worker.getCachedPattern(cacheKey);
        if (cached != null) {
            if (!consumeCraftingCoolant(controller)) {
                return false;
            }
            startWork(cached.copyOutput(), cached.copyInputs(), cached.copyRemaining(), craftingJobId);
            return true;
        }

        craftingInv.clearContent();
        pattern.fillCraftingGrid(table, craftingInv::setItem);
        ItemStack outputItem = pattern.assemble(craftingInv, worker.getLevel());
        if (outputItem.isEmpty()) {
            craftingInv.clearContent();
            return false;
        }
        if (!consumeCraftingCoolant(controller)) {
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
        if (cacheKey != null && !NEConfig.postCraftingEvent && isCacheSafe(inputs, outputItem, list)) {
            worker.cachePattern(cacheKey, new PatternCacheEntry(outputItem, inputs, list));
        }
        startWork(outputItem.copy(), inputs, list, craftingJobId);
        return true;
    }

    private boolean consumeCraftingCoolant(ECOCraftingSystemBlockEntity controller) {
        return !controller.isActiveCooling() || controller.tryConsumeCoolant(5, controller.getEffectiveOverclockTimes());
    }

    private void startWork(
        ItemStack outputItem,
        List<ItemStack> inputs,
        List<ItemStack> remaining,
        @Nullable UUID craftingJobId
    ) {
        this.outputItem = outputItem;
        this.craftingJobId = craftingJobId;
        inputItems.clear();
        copyStacks(inputs, inputItems);
        remainingItems.clear();
        copyStacks(remaining, remainingItems);
        worker.onThreadWork();
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

    private static boolean isCacheSafe(List<ItemStack> inputs, ItemStack output, List<ItemStack> remaining) {
        if (!isCacheSafeStack(output, false)) {
            return false;
        }
        for (ItemStack input : inputs) {
            if (!isCacheSafeStack(input, true)) {
                return false;
            }
        }
        for (ItemStack remainder : remaining) {
            if (!isCacheSafeStack(remainder, false)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCacheSafeStack(ItemStack stack, boolean input) {
        if (stack.isEmpty()) {
            return true;
        }
        if (stack.isDamageableItem()) {
            return false;
        }
        if (stack.hasTag()) {
            return false;
        }
        return !input || !stack.getItem().hasCraftingRemainingItem(stack);
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
            var safePower = Math.min(ticksPassed * bonusValue * acceleratorTax, 500000);
            return (int) (grid.getEnergyService().extractAEPower(safePower, Actionable.MODULATE, PowerMultiplier.CONFIG) / acceleratorTax);
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
                    outputItem,
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
        addStack(outputs, outputItem);
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
        if (inputItems.isEmpty()) {
            return false;
        }
        KeyCounter inputs = collectStacks(inputItems);
        if (!canInsertAll(storage, inputs)) {
            return false;
        }
        insertAll(storage, inputs);
        worker.onThreadStop();
        clearWork();
        setChanged();
        return true;
    }

    public void addRecoverableDrops(List<ItemStack> drops) {
        if (!isBusy) {
            return;
        }
        List<ItemStack> recoverable = inputItems.isEmpty() ? outputAndRemainingItems() : inputItems;
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
        if (!outputItem.isEmpty()) {
            stacks.add(outputItem);
        }
        stacks.addAll(remainingItems);
        return stacks;
    }

    private void clearWork() {
        outputItem = ItemStack.EMPTY;
        inputItems.clear();
        remainingItems.clear();
        craftingJobId = null;
        isBusy = false;
        reboot = true;
        progress = 0;
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
        tag.putInt("neoecoae_version", 1);
        if (craftingJobId != null) {
            tag.putUUID("craftingJobId", craftingJobId);
        }
        tag.put("outputItem", outputItem.save(new CompoundTag()));
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
        this.outputItem = ItemStack.of(nbt.getCompound("outputItem"));
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

    public static final class PatternCacheKey {
        private final IMolecularAssemblerSupportedPattern pattern;
        private final ResourceLocation dimension;
        private final List<SlotSignature> slots;
        private final int hash;

        private PatternCacheKey(
            IMolecularAssemblerSupportedPattern pattern,
            ResourceLocation dimension,
            List<SlotSignature> slots
        ) {
            this.pattern = pattern;
            this.dimension = dimension;
            this.slots = slots;
            this.hash = computeHash();
        }

        @Nullable
        public static PatternCacheKey of(
            IMolecularAssemblerSupportedPattern pattern,
            KeyCounter[] table,
            @Nullable Level level
        ) {
            if (pattern == null || table == null) {
                return null;
            }
            ResourceLocation dimension = level == null ? null : level.dimension().location();
            List<SlotSignature> slots = new ArrayList<>(table.length);
            for (KeyCounter counter : table) {
                List<CounterSignature> entries = new ArrayList<>();
                if (counter != null) {
                    for (Object2LongMap.Entry<AEKey> entry : counter) {
                        entries.add(new CounterSignature(entry.getKey(), entry.getLongValue()));
                    }
                }
                slots.add(new SlotSignature(entries));
            }
            return new PatternCacheKey(pattern, dimension, List.copyOf(slots));
        }

        private int computeHash() {
            int result = System.identityHashCode(pattern);
            result = 31 * result + Objects.hashCode(dimension);
            result = 31 * result + slots.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PatternCacheKey other)) {
                return false;
            }
            return this.pattern == other.pattern
                && Objects.equals(this.dimension, other.dimension)
                && this.slots.equals(other.slots);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private record SlotSignature(List<CounterSignature> entries) {
        private SlotSignature {
            entries = List.copyOf(entries);
        }
    }

    private record CounterSignature(AEKey key, long amount) {
    }

    public static final class PatternCacheEntry {
        private final ItemStack output;
        private final List<ItemStack> inputs;
        private final List<ItemStack> remaining;

        public PatternCacheEntry(ItemStack output, List<ItemStack> inputs, List<ItemStack> remaining) {
            this.output = output.copy();
            this.inputs = copyImmutable(inputs);
            this.remaining = copyImmutable(remaining);
        }

        public ItemStack copyOutput() {
            return output.copy();
        }

        public List<ItemStack> copyInputs() {
            return copyImmutable(inputs);
        }

        public List<ItemStack> copyRemaining() {
            return copyImmutable(remaining);
        }

        private static List<ItemStack> copyImmutable(List<ItemStack> stacks) {
            List<ItemStack> copy = new ArrayList<>(stacks.size());
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty()) {
                    copy.add(stack.copy());
                }
            }
            return List.copyOf(copy);
        }
    }
}
