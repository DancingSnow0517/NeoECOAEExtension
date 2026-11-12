package cn.dancingsnow.neoecoae.compat.ae2.keyreuse;

/** One immutable key per registered resource. Reads allocate nothing and do not acquire a lock. */
public final class KeyReuseSlot {
    private static final java.util.concurrent.atomic.AtomicReferenceFieldUpdater<KeyReuseSlot, Entry> ENTRY =
        java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater(KeyReuseSlot.class, Entry.class, "entry");
    private volatile Entry entry;

    public KeyReuseSlot() {
        KeyReuse.register(this);
    }

    public Object get(Object epoch) {
        Entry current = entry;
        return epoch != null && current != null && current.epoch == epoch ? current.key : null;
    }

    /** Component prototypes are independent from the resource registration and reload generation. */
    public Object get(Object epoch, Object context) {
        Entry current = entry;
        return epoch != null && current != null && current.epoch == epoch && current.context == context
                ? current.key : null;
    }

    /** Call while synchronized on this slot, after completely initializing the representative key. */
    public void set(Object epoch, Object key) {
        set(epoch, null, key);
    }

    public void set(Object epoch, Object context, Object key) {
        // A concurrent reload must never republish a key from the preceding generation.
        if (epoch != null && epoch == KeyReuse.epoch()) {
            Entry published = new Entry(epoch, context, key);
            entry = published;
            if (epoch != KeyReuse.epoch()) ENTRY.compareAndSet(this, published, null);
        }
    }

    // Invalidation never takes an owner lock: a mod callback during creation may register another resource.
    void clear() {
        entry = null;
    }

    private static final class Entry {
        final Object epoch;
        final Object key;
        final Object context;

        Entry(Object epoch, Object context, Object key) {
            this.epoch = epoch;
            this.context = context;
            this.key = key;
        }
    }
}
