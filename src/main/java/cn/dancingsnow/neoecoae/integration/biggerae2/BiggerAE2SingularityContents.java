package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.platform.nbt.NEItemData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Immutable exact contents in native 1.20 item NBT, including stack copies and menu packets. */
record BiggerAE2SingularityContents(List<Entry> entries) {
    private static final String CONTENTS = "neoecoae_bigger_ae2_singularity_contents";
    static final BiggerAE2SingularityContents EMPTY = new BiggerAE2SingularityContents(List.of());

    BiggerAE2SingularityContents {
        entries = List.copyOf(entries);
        if (entries.size() > 5 || entries.stream().map(Entry::key).distinct().count() != entries.size()) {
            throw new IllegalArgumentException("Invalid singularity contents");
        }
    }

    static BiggerAE2SingularityContents read(ItemStack stack) {
        CompoundTag root = NEItemData.read(stack);
        if (!root.contains(CONTENTS)) return EMPTY;
        if (!root.contains(CONTENTS, Tag.TAG_COMPOUND)) throw new IllegalStateException("Malformed singularity contents");
        CompoundTag data = root.getCompound(CONTENTS);
        if (data.getInt("version") != 1 || !data.contains("entries", Tag.TAG_LIST)) {
            throw new IllegalStateException("Unsupported singularity contents; original NBT retained");
        }
        ListTag raw = (ListTag) data.get("entries");
        if (raw.size() > 5 || !raw.isEmpty() && raw.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalStateException("Invalid singularity entries; original NBT retained");
        }
        List<Entry> entries = new ArrayList<>(raw.size());
        for (int index = 0; index < raw.size(); index++) {
            CompoundTag entry = raw.getCompound(index);
            AEKey key = AEKey.fromTagGeneric(entry.getCompound("key"));
            entries.add(new Entry(key, new BigInteger(entry.getString("amount"))));
        }
        return new BiggerAE2SingularityContents(entries);
    }

    static void write(ItemStack stack, BiggerAE2SingularityContents contents) {
        if (contents.entries.isEmpty()) {
            NEItemData.remove(stack, CONTENTS);
            return;
        }
        ListTag entries = new ListTag();
        for (Entry value : contents.entries) {
            CompoundTag entry = new CompoundTag();
            entry.put("key", value.key.toTagGeneric());
            entry.putString("amount", value.amount.toString());
            entries.add(entry);
        }
        CompoundTag data = new CompoundTag();
        data.putInt("version", 1);
        data.put("entries", entries);
        NEItemData.update(stack, root -> root.put(CONTENTS, data));
    }

    record Entry(AEKey key, BigInteger amount) {
        Entry {
            java.util.Objects.requireNonNull(key, "Unknown singularity key; original NBT retained");
            if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Nonpositive cell amount");
        }
    }
}
