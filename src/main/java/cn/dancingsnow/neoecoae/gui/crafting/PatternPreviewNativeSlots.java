package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.api.crafting.PatternDetailsHelper;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;

/** A bounded, per-menu view of real inventories, synchronized exclusively by vanilla container packets. */
final class PatternPreviewNativeSlots implements IItemTransfer {
    record Target(long bus, int slot, int recipe, boolean visible) {}
    private final ECOMachineInterfaceBlockEntity<?> host;
    private final Player player;
    private final CraftingPatternPreviewState state;
    private final Target[] targets = new Target[CraftingInterfaceUI.PREVIEW_BUFFER_SLOTS];
    private final ItemStack[] clientStacks = new ItemStack[targets.length];
    private final Map<Target, ItemStack> lastReadyStacks = new HashMap<>();
    private final SimpleContainer nativeContainer = new SimpleContainer(0);
    private boolean ready;
    private boolean viewDirty;
    private long generation;

    PatternPreviewNativeSlots(ECOMachineInterfaceBlockEntity<?> host, Player player, CraftingPatternPreviewState state) {
        this.host = host;
        this.player = player;
        this.state = state;
        Arrays.fill(clientStacks, ItemStack.EMPTY);
    }

    Slot slot(int visual) {
        // Use a native Slot directly: LDLib's inner slot compares HOVER_SLOT by identity, while the retained
        // adapter owns the actual registered wrapper. Its max-size probe would also temporarily mutate a bus.
        return new Slot(nativeContainer, visual, 0, 0) {
            @Override public ItemStack getItem() { return getStackInSlot(visual); }
            @Override public void set(ItemStack stack) { setStackInSlot(visual, stack); setChanged(); }
            @Override public ItemStack remove(int amount) { return extractItem(visual, amount, false); }
            @Override public boolean mayPlace(ItemStack stack) { return isItemValid(visual, stack); }
            @Override public boolean mayPickup(Player actor) { return !extractItem(visual, 1, true).isEmpty(); }
            @Override public int getMaxStackSize() { return 1; }
            @Override public int getMaxStackSize(ItemStack stack) { return 1; }
            @Override public boolean isActive() { return ready && visible(visual); }
            @Override public void setChanged() {
                if (!player.level().isClientSide && writable(visual)) {
                    Target target = targets[visual];
                    host.nativePatternPreviewChanged(player, target.bus(), target.slot());
                }
            }
        };
    }
    boolean ready() { return ready; }

    void view(Target[] next) {
        if (!player.level().isClientSide || Arrays.equals(targets, next)) return;
        if (ready) {
            lastReadyStacks.clear();
            for (int index = 0; index < targets.length; index++) {
                if (targets[index] != null && targets[index].slot() >= 0)
                    lastReadyStacks.put(targets[index], clientStacks[index].copy());
            }
        }
        System.arraycopy(next, 0, targets, 0, targets.length);
        ready = false;
        viewDirty = true;
        generation++;
    }

    boolean needsView() { return viewDirty; }

    /** Rendering can use the catalogue while a new mapping is in flight; native prediction stays untouched. */
    ItemStack displayStack(int index, ItemStack fallback) {
        if (!player.level().isClientSide || ready) return getStackInSlot(index);
        return lastReadyStacks.getOrDefault(targets[index], fallback);
    }
    void writeView(FriendlyByteBuf buffer) {
        buffer.writeLong(generation);
        for (Target target : targets) {
            buffer.writeBoolean(target != null);
            if (target != null) {
                buffer.writeLong(target.bus());
                buffer.writeVarInt(target.slot());
                buffer.writeInt(target.recipe());
                buffer.writeBoolean(target.visible());
            }
        }
        viewDirty = false;
    }

    long readView(FriendlyByteBuf buffer) {
        long nextGeneration = buffer.readLong();
        Target[] next = new Target[targets.length];
        for (int index = 0; index < next.length; index++) {
            if (!buffer.readBoolean()) continue;
            Target target = new Target(buffer.readLong(), buffer.readVarInt(), buffer.readInt(), buffer.readBoolean());
            if (target.slot() < -1 || target.slot() >= PatternPreviewCodec.MAX_SLOTS
                    || target.recipe() < -1 || target.recipe() >= PatternPreviewCodec.MAX_SLOTS)
                throw new IllegalArgumentException("Invalid native pattern viewport");
            next[index] = target;
        }
        System.arraycopy(next, 0, targets, 0, targets.length);
        ready = true;
        state.updateNativeVisibility();
        return nextGeneration;
    }

    void acknowledge(long acknowledged) {
        if (acknowledged == generation) {
            ready = true;
            state.updateNativeVisibility();
        }
    }

    boolean visible(int index) { return targets[index] != null && targets[index].visible(); }
    private boolean writable(int index) {
        Target target = targets[index];
        return ready && target != null && target.slot() >= 0 && target.recipe() < 0;
    }

    /** Special insertion rules still run inside the normal native menu click, including its prediction. */
    ItemStack click(int index, int button, ClickType type, Player actor) {
        if (!ready) return ItemStack.EMPTY;
        ItemStack carried = actor.containerMenu.getCarried();
        if (type == ClickType.PICKUP && button == 0 && PatternDetailsHelper.isEncodedPattern(carried)) {
            actor.containerMenu.setCarried(insertAnywhere(carried));
            return ItemStack.EMPTY;
        }
        return writable(index) ? null : ItemStack.EMPTY;
    }

    ItemStack quickMoveFromPlayer(Slot source, ClickType type) {
        if (type != ClickType.QUICK_MOVE || !PatternDetailsHelper.isEncodedPattern(source.getItem())) return null;
        if (!ready || !player.containerMenu.getCarried().isEmpty()) return ItemStack.EMPTY;
        ItemStack remainder = insertAnywhere(source.getItem());
        source.set(remainder);
        source.setChanged();
        return ItemStack.EMPTY;
    }

    private ItemStack insertAnywhere(ItemStack stack) {
        if (!player.level().isClientSide) return host.insertNativePatternPreview(player, stack);
        // Predict only the cursor/backpack. Slot contents arrive through the same native menu sync as 20.4.2.
        if (!state.hasEmptyPhysicalSlot()) return stack;
        return stack.copyWithCount(Math.max(0, stack.getCount() - 1));
    }

    @Override public int getSlots() { return targets.length; }
    @Override public ItemStack getStackInSlot(int index) {
        if (player.level().isClientSide) return clientStacks[index];
        Target target = targets[index];
        return target == null || target.slot() < 0 ? ItemStack.EMPTY
            : host.getNativePatternPreviewStack(player, target.bus(), target.slot(), target.recipe());
    }
    @Override public void setStackInSlot(int index, ItemStack stack) {
        if (player.level().isClientSide) { clientStacks[index] = stack.copy(); return; }
        if (writable(index)) {
            Target target = targets[index];
            host.setNativePatternPreviewStack(player, target.bus(), target.slot(), stack);
        }
    }
    @Override public ItemStack insertItem(int index, ItemStack stack, boolean simulate, boolean notify) {
        if (!isItemValid(index, stack)) return stack;
        ItemStack current = getStackInSlot(index);
        if (!current.isEmpty()) return stack;
        int accepted = Math.min(1, stack.getCount());
        if (!simulate) setStackInSlot(index, stack.copyWithCount(accepted));
        return stack.copyWithCount(stack.getCount() - accepted);
    }
    @Override public ItemStack extractItem(int index, int amount, boolean simulate, boolean notify) {
        if (!writable(index) || amount <= 0) return ItemStack.EMPTY;
        if (!player.level().isClientSide) {
            Target target = targets[index];
            return host.extractNativePatternPreview(player, target.bus(), target.slot(), amount, simulate);
        }
        ItemStack current = clientStacks[index];
        ItemStack result = current.copyWithCount(Math.min(amount, current.getCount()));
        if (!simulate) clientStacks[index] = current.copyWithCount(current.getCount() - result.getCount());
        return result;
    }
    @Override public int getSlotLimit(int index) { return 1; }
    @Override public boolean isItemValid(int index, ItemStack stack) {
        if (!writable(index) || !PatternDetailsHelper.isEncodedPattern(stack)) return false;
        if (player.level().isClientSide) return true;
        Target target = targets[index];
        return host.canInsertNativePatternPreview(player, target.bus(), target.slot(), stack);
    }
    @Override public Object createSnapshot() {
        ItemStack[] contents = new ItemStack[targets.length];
        for (int index = 0; index < contents.length; index++) contents[index] = getStackInSlot(index).copy();
        return new Snapshot(targets.clone(), contents);
    }
    @Override public void restoreFromSnapshot(Object value) {
        if (!(value instanceof Snapshot snapshot) || !Arrays.equals(targets, snapshot.targets())) return;
        for (int index = 0; index < targets.length; index++) setStackInSlot(index, snapshot.contents()[index]);
    }
    private record Snapshot(Target[] targets, ItemStack[] contents) {}
}
