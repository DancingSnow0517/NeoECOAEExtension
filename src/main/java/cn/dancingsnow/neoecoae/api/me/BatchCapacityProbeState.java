package cn.dancingsnow.neoecoae.api.me;

/** Lightweight long-lived capacity history plus a short-lived downward-search cursor. */
public final class BatchCapacityProbeState {
    private long historicalKnownGood;
    private long knownBadExclusive = Long.MAX_VALUE;
    /** Non-zero only after a complete N/N2/N4 window failed. Never a learned capacity ceiling. */
    private long continuationUpperBound;

    public long historicalKnownGood() {
        return historicalKnownGood;
    }

    public long knownBadExclusive() {
        return knownBadExclusive;
    }

    public long nextUpperBound() {
        return continuationUpperBound;
    }

    public long continuationUpperBound() {
        return continuationUpperBound;
    }

    long startingUpperBound(long legalUpperBound) {
        long legal = Math.max(0L, legalUpperBound);
        return continuationUpperBound > 0L ? Math.min(continuationUpperBound, legal) : legal;
    }

    void recordFailedCandidate(long candidate) {
        if (candidate > historicalKnownGood) {
            knownBadExclusive = Math.min(knownBadExclusive, candidate);
        }
    }

    void recordSuccess(long candidate) {
        historicalKnownGood = Math.max(historicalKnownGood, candidate);
        continuationUpperBound = 0L;
    }

    void continueBelow(long lastCandidate) {
        continuationUpperBound = Math.max(0L, lastCandidate / 2L);
    }
}
