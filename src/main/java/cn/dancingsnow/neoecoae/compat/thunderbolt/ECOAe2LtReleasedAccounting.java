package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadClaimResult;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadConsumerCredit;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadCpuInsertSupport;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadCpuStateManager;
import com.moakiee.ae2lt.overload.runtime.cpu.OverloadPatternReference;
import com.moakiee.ae2lt.overload.runtime.cpu.PendingOverloadClaim;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Loaded only when AE2LT is present; uses public contracts shipped in 2.1.2-beta3. */
final class ECOAe2LtReleasedAccounting {
    private static final OverloadCpuStateManager STATES = OverloadCpuStateManager.INSTANCE;
    private static final ECOOverloadCpuAccountingBridge.Registration ORDINARY = registration(true, null);
    private static final ECOOverloadCpuAccountingBridge.Registration BLOCKED = registration(false, null);

    ECOOverloadCpuAccountingBridge.Registration prepare(Object owner, IPatternDetails pattern, UUID job,
            AEKey finalOutput, KeyCounter waiting) {
        var delegate = CraftingPatternDelegates.forProviderLookup(pattern);
        if (!(delegate instanceof OverloadedProviderOnlyPatternDetails overload)) {
            return owner != null && waiting != null
                && OverloadCpuInsertSupport.hasPendingCollisionWithOrdinaryPattern(owner, pattern) ? BLOCKED : ORDINARY;
        }
        if (owner == null || job == null) return BLOCKED;
        if (waiting != null && OverloadCpuInsertSupport.hasStrictCollisionWithOverloadPattern(owner, pattern, overload, waiting)) {
            return BLOCKED;
        }
        var details = overload.overloadPatternDetailsView();
        var reference = new OverloadPatternReference(overload.overloadPatternIdentity(), details.sourcePattern());
        if (STATES.hasAmbiguousOutputRegistration(owner, reference, details)) return BLOCKED;
        return registration(true, amount -> STATES.registerExpectedOutputs(owner, job, reference, details,
            java.util.Arrays.asList(pattern.getOutputs()), finalOutput, amount));
    }

    long strictMatch(Object owner, AEKey actual, long rawMatch, long exactWaiting) {
        return STATES.hasExactPending(owner, actual)
            ? OverloadCpuInsertSupport.nativeStrictMatch(owner, actual, rawMatch, exactWaiting) : rawMatch;
    }

    long claim(Object owner, UUID job, AEKey actual, long amount, Actionable mode,
            BiFunction<GenericStack, Actionable, Long> receiver) {
        var preview = STATES.claim(owner, actual, amount, Actionable.SIMULATE);
        long accepted = 0;
        for (var claim : preview.claims()) {
            if (!job.equals(claim.key().craftingId())) continue;
            long limit = receiver.apply(new GenericStack(claim.exactExpectedKey(), claim.claimedAmount()), Actionable.SIMULATE);
            if (limit < 0 || limit > claim.claimedAmount()) throw new IllegalStateException("Invalid AE2LT claim preview");
            if (limit == 0) continue;
            if (mode == Actionable.SIMULATE) {
                accepted = Math.addExact(accepted, limit);
                continue;
            }
            var credits = OverloadConsumerCredit.limit(claim.consumerCredits(), limit);
            var limited = new PendingOverloadClaim(claim.key(), limit, claim.routesToRequester(),
                Math.min(claim.requesterAmount(), limit - OverloadConsumerCredit.total(credits)),
                claim.exactExpectedKey(), credits, claim.sharedReusableSeedPool());
            var committed = STATES.commitPreview(owner, new OverloadClaimResult(limit, List.of(limited)));
            if (!committed.claimedAnything()) continue;
            long received = receiver.apply(new GenericStack(claim.exactExpectedKey(), committed.claimedAmount()), mode);
            if (received != committed.claimedAmount()) throw new IllegalStateException("AE2LT claim changed after commitment");
            accepted = Math.addExact(accepted, received);
        }
        return accepted;
    }

    CompoundTag save(Object owner, UUID job) {
        for (var pending : STATES.snapshotPending(owner)) {
            if (!job.equals(pending.key().craftingId())) throw new IllegalStateException("AE2LT state belongs to another job");
        }
        var result = new CompoundTag();
        result.putUUID("craftingId", job);
        var state = STATES.writeToTag(owner);
        if (state != null) result.put("state", state);
        return result;
    }

    void load(Object owner, UUID job, CompoundTag data) {
        if (data.hasUUID("craftingId") && !job.equals(data.getUUID("craftingId"))) {
            throw new IllegalArgumentException("AE2LT checkpoint belongs to another job");
        }
        STATES.readFromTag(owner, job, data.getCompound("state"));
    }

    void clear(Object owner) { STATES.clear(owner); }

    private static ECOOverloadCpuAccountingBridge.Registration registration(boolean allowed,
            java.util.function.LongConsumer accepted) {
        return new ECOOverloadCpuAccountingBridge.Registration() {
            public boolean canDispatch() { return allowed; }
            public void registerAccepted(long amount) {
                if (!allowed || amount <= 0) throw new IllegalStateException("Invalid AE2LT accepted amount");
                if (accepted != null) accepted.accept(amount);
            }
        };
    }
}
