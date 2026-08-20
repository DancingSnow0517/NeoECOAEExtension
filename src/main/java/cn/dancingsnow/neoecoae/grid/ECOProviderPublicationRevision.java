package cn.dancingsnow.neoecoae.grid;

/** Local monotonic publication counter used by PatternStorage compatibility callers. */
public final class ECOProviderPublicationRevision {
    private long revision;

    public synchronized Value advance() {
        revision = revision == Long.MAX_VALUE ? 1L : revision + 1L;
        return new Value(revision);
    }

    public synchronized long value() {
        return revision;
    }

    public record Value(long value) {
    }
}
