package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

final class ECOCellContents {
    private static final String CONTENTS_TAG = "eco_cell_contents";

    private final ItemStack cellStack;
    private final int expectedTypes;
    private int storedItemTypes;
    private long storedItemCount;

    @Nullable private Object2LongMap<AEKey> storedAmounts;

    private boolean persisted = true;

    ECOCellContents(ItemStack cellStack, int expectedTypes) {
        this.cellStack = cellStack;
        this.expectedTypes = expectedTypes;

        var storedStacks = readStoredStacks();
        this.storedItemTypes = storedStacks.size();
        this.storedItemCount =
                storedStacks.stream().mapToLong(GenericStack::amount).reduce(0L, LongMath::saturatedAdd);
    }

    int storedItemTypes() {
        return storedItemTypes;
    }

    long storedItemCount() {
        return storedItemCount;
    }

    boolean isPersisted() {
        return persisted;
    }

    boolean isEmpty() {
        return items().isEmpty();
    }

    boolean contains(AEKey what) {
        return items().containsKey(what);
    }

    long getAmount(AEKey what) {
        return items().getLong(what);
    }

    Object2LongMap<AEKey> items() {
        if (storedAmounts == null) {
            storedAmounts = new Object2LongOpenHashMap<>(expectedTypes);
            loadCellItems();
        }
        return storedAmounts;
    }

    void add(AEKey what, long amount) {
        var items = items();
        long currentAmount = items.getLong(what);
        items.put(what, LongMath.saturatedAdd(currentAmount, amount));
        if (currentAmount <= 0) {
            storedItemTypes++;
        }
        storedItemCount = LongMath.saturatedAdd(storedItemCount, amount);
    }

    void remove(AEKey what, long amount) {
        items().removeLong(what);
        storedItemTypes = Math.max(0, storedItemTypes - 1);
        storedItemCount = Math.max(0, storedItemCount - amount);
    }

    void subtract(AEKey what, long amount) {
        long currentAmount = items().getLong(what);
        items().put(what, currentAmount - amount);
        storedItemCount = Math.max(0, storedItemCount - amount);
    }

    void addTo(KeyCounter out) {
        for (var entry : Object2LongMaps.fastIterable(items())) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    void markDirty() {
        persisted = false;
    }

    int persist() {
        if (persisted) {
            return storedItemTypes;
        }

        long itemCount = 0L;
        var stacks = new ArrayList<GenericStack>(items().size());
        for (var entry : items().object2LongEntrySet()) {
            long amount = entry.getLongValue();
            itemCount = LongMath.saturatedAdd(itemCount, amount);
            if (amount > 0) {
                stacks.add(new GenericStack(entry.getKey(), amount));
            }
        }

        ListTag list = new ListTag();
        for (GenericStack stack : stacks) {
            list.add(GenericStack.writeTag(stack));
        }
        cellStack.getOrCreateTag().put(CONTENTS_TAG, list);

        storedItemTypes = stacks.size();
        storedItemCount = itemCount;
        persisted = true;
        return storedItemTypes;
    }

    private List<GenericStack> readStoredStacks() {
        if (!cellStack.hasTag() || !cellStack.getTag().contains(CONTENTS_TAG, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag list = cellStack.getTag().getList(CONTENTS_TAG, Tag.TAG_COMPOUND);
        List<GenericStack> stacks = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            GenericStack stack = GenericStack.readTag(list.getCompound(i));
            if (stack != null && stack.amount() > 0) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private void loadCellItems() {
        var amounts = storedAmounts;
        if (amounts == null) {
            return;
        }
        for (var stack : readStoredStacks()) {
            amounts.put(stack.what(), stack.amount());
        }
    }
}
