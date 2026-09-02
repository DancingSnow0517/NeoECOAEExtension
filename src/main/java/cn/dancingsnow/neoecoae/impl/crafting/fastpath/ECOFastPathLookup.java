package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import org.jetbrains.annotations.Nullable;

/**
 * Outcome of one fast-path cache lookup. The status distinguishes the three non-usable cases the caller has
 * to treat differently (cold miss must verify, negative entry must not re-verify, stale positive entry must be
 * demoted to negative) without forcing a second map lookup or a second value comparison.
 *
 * <p>Miss and mismatch outcomes are shared singletons. Negative outcomes that carry the cached rejection
 * reason allocate a small result so the caller can expose the actual diagnostic.
 */
public final class ECOFastPathLookup {
    public enum Status {
        /** No entry for this key: the caller must run the real assembler and verify. */
        MISS,
        /** A live negative entry exists: the caller must run the slow path without re-verifying. */
        NEGATIVE,
        /** A positive entry exists but no longer matches this execution: it must be demoted to negative. */
        MISMATCH,
        /** A positive entry matched; {@link #recipe()} is a usable credential. */
        VERIFIED
    }

    private static final ECOFastPathLookup MISS = new ECOFastPathLookup(Status.MISS, null, "CACHE_MISS");
    private static final ECOFastPathLookup NEGATIVE = new ECOFastPathLookup(Status.NEGATIVE, null, null);
    private static final ECOFastPathLookup MISMATCH = new ECOFastPathLookup(Status.MISMATCH, null, "CACHE_RESULT_MISMATCH");

    private final Status status;
    private final String reason;

    @Nullable
    private final ECOVerifiedFastPathRecipe recipe;

    private ECOFastPathLookup(Status status, @Nullable ECOVerifiedFastPathRecipe recipe, @Nullable String reason) {
        this.status = status;
        this.recipe = recipe;
        this.reason = reason;
    }

    static ECOFastPathLookup miss() {
        return MISS;
    }

    static ECOFastPathLookup negative() {
        return NEGATIVE;
    }

    static ECOFastPathLookup negative(String reason) {
        return new ECOFastPathLookup(Status.NEGATIVE, null, reason);
    }

    static ECOFastPathLookup mismatch() {
        return MISMATCH;
    }

    static ECOFastPathLookup verified(ECOVerifiedFastPathRecipe recipe) {
        return new ECOFastPathLookup(Status.VERIFIED, recipe, null);
    }

    public Status status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public boolean isVerified() {
        return status == Status.VERIFIED;
    }

    @Nullable
    public ECOVerifiedFastPathRecipe recipe() {
        return recipe;
    }
}
