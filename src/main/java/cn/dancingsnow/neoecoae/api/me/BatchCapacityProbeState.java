package cn.dancingsnow.neoecoae.api.me;

/** Lightweight long-lived capacity history plus a short-lived downward-search cursor. */
public final class BatchCapacityProbeState {
    private long historicalKnownGood;
    private long knownBadExclusive = Long.MAX_VALUE;
    private long nextUpperBound;

    public long historicalKnownGood() {
        return historicalKnownGood;
    }

    public long knownBadExclusive() {
        return knownBadExclusive;
    }

    public long nextUpperBound() {
        return nextUpperBound;
    }

    long startingUpperBound(long legalUpperBound) {
        long legal = Math.max(0L, legalUpperBound);
        if (nextUpperBound > 0L) {
            return Math.min(nextUpperBound, legal);
        }
        if (historicalKnownGood > 0L) {
            return Math.min(historicalKnownGood, legal);
        }
        return legal;
    }

    void recordFailedCandidate(long candidate) {
        if (candidate > historicalKnownGood) {
            knownBadExclusive = Math.min(knownBadExclusive, candidate);
        }
    }

    void recordSuccess(long candidate) {
        historicalKnownGood = Math.max(historicalKnownGood, candidate);
        nextUpperBound = 0L;
    }

    void continueBelow(long lastCandidate) {
        nextUpperBound = Math.max(0L, lastCandidate / 2L);
    }
}
