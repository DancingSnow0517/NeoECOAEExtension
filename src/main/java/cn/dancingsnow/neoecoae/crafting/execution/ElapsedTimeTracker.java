/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package cn.dancingsnow.neoecoae.crafting.execution;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressSnapshot;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

public class ElapsedTimeTracker {
    private static final String NBT_ELAPSED_TIME = "elapsedTime";
    private static final String NBT_STARTED_WORK = "startedWork";
    private static final String NBT_COMPLETED_WORK = "completedWork";

    private long lastTime = System.nanoTime();
    private long elapsedTime = 0;
    private boolean exact;
    private final java.util.Map<AEKeyType, PlannerAmount> exactStarted = new LinkedHashMap<>();
    private final java.util.Map<AEKeyType, PlannerAmount> exactCompleted = new LinkedHashMap<>();

    private final Reference2LongMap<AEKeyType> startedWorkByType = new Reference2LongOpenHashMap<>(
            AEKeyTypes.getAll().size());
    private final Reference2LongMap<AEKeyType> completedWorkByType = new Reference2LongOpenHashMap<>(
            AEKeyTypes.getAll().size());

    public ElapsedTimeTracker() {
    }

    public ElapsedTimeTracker(CompoundTag data) {
        this.elapsedTime = data.getLong(NBT_ELAPSED_TIME);
        readLongByTypeMap(data.getCompound(NBT_STARTED_WORK), startedWorkByType);
        readLongByTypeMap(data.getCompound(NBT_COMPLETED_WORK), completedWorkByType);
        exact = data.getBoolean("exactWork");
        if (exact) {
            var started = data.getCompound("exactStarted");
            var completed = data.getCompound("exactCompleted");
            for (var type : AEKeyTypes.getAll()) {
                String id = type.getId().toString();
                // A key type added after the save has no historical work. Existing malformed values still fail.
                exactStarted.put(type, started.contains(id) ? PlannerAmount.parse(started.getString(id)) : PlannerAmount.ZERO);
                exactCompleted.put(type, completed.contains(id) ? PlannerAmount.parse(completed.getString(id)) : PlannerAmount.ZERO);
            }
        }
    }

    public CompoundTag writeToNBT() {
        CompoundTag data = new CompoundTag();
        data.putLong(NBT_ELAPSED_TIME, elapsedTime);
        data.put(NBT_STARTED_WORK, writeLongByTypeMap(startedWorkByType));
        data.put(NBT_COMPLETED_WORK, writeLongByTypeMap(completedWorkByType));
        if (exact) {
            data.putBoolean("exactWork", true);
            var started = new CompoundTag();
            var completed = new CompoundTag();
            for (var type : AEKeyTypes.getAll()) {
                started.putString(type.getId().toString(), getExactStartedWork(type).toString());
                completed.putString(type.getId().toString(), getExactCompletedWork(type).toString());
            }
            data.put("exactStarted", started);
            data.put("exactCompleted", completed);
        }
        return data;
    }

    private static void readLongByTypeMap(CompoundTag tag, Reference2LongMap<AEKeyType> output) {
        for (var keyType : AEKeyTypes.getAll()) {
            output.put(keyType, tag.getLong(keyType.getId().toString()));
        }
    }

    private static CompoundTag writeLongByTypeMap(Reference2LongMap<AEKeyType> input) {
        CompoundTag result = new CompoundTag();
        for (var entry : input.reference2LongEntrySet()) {
            result.putLong(entry.getKey().getId().toString(), entry.getLongValue());
        }
        return result;
    }

    private void updateTime() {
        long currentTime = System.nanoTime();
        this.elapsedTime = this.elapsedTime + (currentTime - this.lastTime);
        this.lastTime = currentTime;
    }

    void decrementItems(long itemDiff, AEKeyType keyType) {
        updateTime();
        if (exact) exactCompleted.merge(keyType, PlannerAmount.of(itemDiff), PlannerAmount::add);
        completedWorkByType.merge(keyType, itemDiff, NEMath::saturatingAdd);
    }

    void addMaxItems(long itemDiff, AEKeyType keyType) {
        updateTime();
        if (exact) exactStarted.merge(keyType, PlannerAmount.of(itemDiff), PlannerAmount::add);
        startedWorkByType.merge(keyType, itemDiff, NEMath::saturatingAdd);
    }

    /** Rebase the denominator after a suffix replacement without losing completed work or elapsed time. */
    void replaceRemainingWork(ElapsedTimeTracker remaining) {
        updateTime();
        var completed = new LinkedHashMap<AEKeyType, java.math.BigInteger>();
        var totals = new LinkedHashMap<AEKeyType, java.math.BigInteger>();
        boolean needsExact = exact;
        for (var type : AEKeyTypes.getAll()) {
            var done = getExactCompletedWork(type);
            var total = done.add(remaining.getExactStartedWork(type));
            completed.put(type, done);
            totals.put(type, total);
            if (total.bitLength() > 63) needsExact = true;
        }
        exact = needsExact;
        for (var type : AEKeyTypes.getAll()) {
            var done = completed.get(type);
            var total = totals.get(type);
            if (exact) {
                exactCompleted.put(type, PlannerAmount.of(done));
                exactStarted.put(type, PlannerAmount.of(total));
            }
            startedWorkByType.put(type,
                    cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan.bounded(total));
            completedWorkByType.put(type,
                    cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan.bounded(done));
        }
    }

    public long getElapsedTime() {
        boolean allDone = true;
        for (var keyType : AEKeyTypes.getAll()) {
            if (getExactCompletedWork(keyType).compareTo(getExactStartedWork(keyType)) < 0) {
                allDone = false;
                break;
            }
        }

        if (!allDone) {
            return this.elapsedTime + (System.nanoTime() - this.lastTime);
        } else {
            return this.elapsedTime;
        }
    }

    // TODO: 1.21.4 Change the network packet and screen to use this rather than the counts below
    public float getProgress() {
        if (exact) {
            PlannerAmount started = PlannerAmount.ZERO, completed = PlannerAmount.ZERO, denominator = PlannerAmount.ONE;
            for (var type : AEKeyTypes.getAll()) {
                PlannerAmount unit = PlannerAmount.of(type.getAmountPerUnit());
                PlannerAmount gcd = denominator.gcd(unit);
                PlannerAmount left = unit.divide(gcd), right = denominator.divide(gcd);
                started = started.multiply(left).add(exactStarted.getOrDefault(type, PlannerAmount.ZERO).multiply(right));
                completed = completed.multiply(left).add(exactCompleted.getOrDefault(type, PlannerAmount.ZERO).multiply(right));
                denominator = denominator.multiply(left);
            }
            if (started.signum() <= 0 || completed.signum() <= 0) return 0;
            if (completed.compareTo(started) >= 0) return 1;
            // Only the UI ratio is approximate. Scale both exact integers together before the
            // double boundary, so even a BigInteger fallback cannot produce Infinity / Infinity.
            int shift = Math.max(0, started.bitLength() - 1022);
            double ratio = completed.shiftRight(shift).doubleValue() / started.shiftRight(shift).doubleValue();
            return Math.clamp((float) ratio, 0, 1);
        }
        double startedUnits = 0;
        double completedUnits = 0;
        for (var keyType : AEKeyTypes.getAll()) {
            var startedForType = startedWorkByType.getLong(keyType);
            var completedForType = completedWorkByType.getLong(keyType);
            startedUnits += startedForType / (double) keyType.getAmountPerUnit();
            completedUnits += completedForType / (double) keyType.getAmountPerUnit();
        }

        // A newly-created tracker has no denominator yet. Returning zero keeps
        // progress a proper value for packet/UI consumers instead of propagating NaN.
        if (startedUnits <= 0.0) return 0.0F;
        return Math.clamp((float) (completedUnits / startedUnits), 0, 1);
    }

    /** Returns the started work for one key type without exposing the mutable backing map. */
    void startExactWork() {
        exact = true;
        exactStarted.clear();
        exactCompleted.clear();
        startedWorkByType.clear();
        completedWorkByType.clear();
    }

    void addMaxItems(java.math.BigInteger amount, AEKeyType type) {
        addMaxItems(PlannerAmount.of(amount), type);
    }

    void addMaxItems(PlannerAmount amount, AEKeyType type) {
        if (!exact) throw new IllegalStateException("Exact work is disabled");
        updateTime();
        exactStarted.merge(type, amount, PlannerAmount::add);
        startedWorkByType.put(type, exactStarted.get(type).longValueSaturated());
    }

    public java.math.BigInteger getExactStartedWork(AEKeyType type) {
        return exact ? exactStarted.getOrDefault(type, PlannerAmount.ZERO).toBigInteger() : java.math.BigInteger.valueOf(getStartedWork(type));
    }

    public java.math.BigInteger getExactCompletedWork(AEKeyType type) {
        return exact ? exactCompleted.getOrDefault(type, PlannerAmount.ZERO).toBigInteger() : java.math.BigInteger.valueOf(getCompletedWork(type));
    }

    public long getStartedWork(AEKeyType keyType) {
        return keyType == null ? 0L : Math.max(0L, startedWorkByType.getLong(keyType));
    }

    /** Returns the completed work for one key type without exposing the mutable backing map. */
    public long getCompletedWork(AEKeyType keyType) {
        return keyType == null ? 0L : Math.max(0L, completedWorkByType.getLong(keyType));
    }

    /** Creates an immutable view that is safe to retain after the job or tracker changes. */
    public ECOCraftingProgressSnapshot snapshot() {
        var started = new LinkedHashMap<AEKeyType, Long>();
        var completed = new LinkedHashMap<AEKeyType, Long>();
        for (var keyType : AEKeyTypes.getAll()) {
            started.put(keyType, getStartedWork(keyType));
            completed.put(keyType, getCompletedWork(keyType));
        }
        return new ECOCraftingProgressSnapshot(getProgress(), getElapsedTime(), started, completed);
    }

    @Deprecated(forRemoval = true)
    public long getRemainingItemCount() {
        return getSyntheticRemainingItemCount();
    }

    @Deprecated(forRemoval = true)
    public long getStartItemCount() {
        return getSyntheticStartItemCount();
    }

    public long getSyntheticStartItemCount() {
        return Integer.MAX_VALUE;
    }

    public long getSyntheticRemainingItemCount() {
        return Math.max(0L, Math.min(getSyntheticStartItemCount(), Math.round((1.0F - getProgress()) * getSyntheticStartItemCount())));
    }
}
