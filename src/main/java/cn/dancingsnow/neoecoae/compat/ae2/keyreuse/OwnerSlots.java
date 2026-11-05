package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

import java.util.concurrent.atomic.AtomicReferenceArray;

/** ECO-owned identity table. Hot reads allocate nothing; only first use of a resource takes a lock. */
final class OwnerSlots {
    private final AtomicReferenceArray<Entry> buckets = new AtomicReferenceArray<>(4096);
    private final int limit;
    private int size;

    OwnerSlots() { this(65_536); }
    OwnerSlots(int limit) { this.limit = limit; }

    KeyReuseSlot get(Object owner) {
        int bucket = System.identityHashCode(owner) & (buckets.length() - 1);
        for (Entry entry = buckets.get(bucket); entry != null; entry = entry.next) {
            if (entry.owner == owner) return entry.slot;
        }
        return create(owner, bucket);
    }

    private synchronized KeyReuseSlot create(Object owner, int bucket) {
        Entry head = buckets.get(bucket);
        for (Entry entry = head; entry != null; entry = entry.next) {
            if (entry.owner == owner) return entry.slot;
        }
        // Unknown/ephemeral owners cannot grow an ECO generation without bound.
        if (size >= limit) return null;
        KeyReuseSlot slot = new KeyReuseSlot();
        size++;
        buckets.set(bucket, new Entry(owner, slot, head));
        return slot;
    }

    private static final class Entry {
        final Object owner;
        final KeyReuseSlot slot;
        final Entry next;
        Entry(Object owner, KeyReuseSlot slot, Entry next) {
            this.owner = owner;
            this.slot = slot;
            this.next = next;
        }
    }
}
