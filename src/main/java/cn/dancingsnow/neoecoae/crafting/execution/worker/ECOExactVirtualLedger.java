package cn.dancingsnow.neoecoae.crafting.execution.worker;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.ToLongBiFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Durable exact-amount custody for one full-virtual worker lane. */
public final class ECOExactVirtualLedger {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final BigInteger crafts;
    private final Map<AEKey, BigInteger> inputs = new LinkedHashMap<>();
    private final Map<AEKey, BigInteger> outputs = new LinkedHashMap<>();

    public ECOExactVirtualLedger(BigInteger crafts, List<GenericStack> inputs,
            List<GenericStack> outputs, List<GenericStack> remaining) {
        this.crafts = positive(crafts, "crafts");
        addTotals(this.inputs, inputs, crafts);
        addTotals(this.outputs, outputs, crafts);
        addTotals(this.outputs, remaining, crafts);
    }

    /** The CPU has already extracted these exact totals before handing the batch to the worker. */
    public static ECOExactVirtualLedger fromOwnedTotals(long crafts, List<GenericStack> inputs,
            List<GenericStack> outputs, List<GenericStack> remaining) {
        ECOExactVirtualLedger ledger = new ECOExactVirtualLedger(BigInteger.valueOf(crafts));
        addTotals(ledger.inputs, inputs, BigInteger.ONE);
        addTotals(ledger.outputs, outputs, BigInteger.ONE);
        addTotals(ledger.outputs, remaining, BigInteger.ONE);
        return ledger;
    }

    private ECOExactVirtualLedger(BigInteger crafts) {
        this.crafts = positive(crafts, "crafts");
    }

    public BigInteger crafts() {
        return crafts;
    }

    public Map<AEKey, BigInteger> snapshot(boolean output) {
        return Map.copyOf(output ? outputs : inputs);
    }

    public boolean drain(boolean output, ToLongBiFunction<AEKey, Long> insert, Runnable changed) {
        return drainExact(output, (key, amount) -> {
            long offered = amount.min(LONG_MAX).longValueExact();
            return BigInteger.valueOf(insert.applyAsLong(key, offered));
        }, changed);
    }

    /** Debit accepted amounts before returning so a retry cannot replay completed delivery. */
    public boolean drainExact(boolean output, BiFunction<AEKey, BigInteger, BigInteger> insert, Runnable changed) {
        Map<AEKey, BigInteger> pending = output ? outputs : inputs;
        for (var entry : List.copyOf(pending.entrySet())) {
            BigInteger offered = pending.get(entry.getKey());
            if (offered == null) continue;
            BigInteger accepted = insert.apply(entry.getKey(), offered);
            if (accepted == null || accepted.signum() < 0 || accepted.compareTo(offered) > 0) {
                throw new IllegalStateException("Invalid exact delivery amount");
            }
            if (accepted.signum() > 0) {
                BigInteger left = offered.subtract(accepted);
                if (left.signum() == 0) pending.remove(entry.getKey());
                else pending.put(entry.getKey(), left);
                changed.run();
            }
        }
        return pending.isEmpty();
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putString("crafts", crafts.toString());
        tag.put("inputs", writeAmounts(inputs));
        tag.put("outputs", writeAmounts(outputs));
        return tag;
    }

    public static ECOExactVirtualLedger read(CompoundTag tag) {
        ECOExactVirtualLedger ledger = new ECOExactVirtualLedger(new BigInteger(tag.getString("crafts")));
        readAmounts(tag.getList("inputs", Tag.TAG_COMPOUND), ledger.inputs);
        readAmounts(tag.getList("outputs", Tag.TAG_COMPOUND), ledger.outputs);
        return ledger;
    }

    private static void addTotals(Map<AEKey, BigInteger> destination, List<GenericStack> stacks,
            BigInteger copies) {
        for (GenericStack stack : stacks) {
            if (stack == null || stack.what() == null || stack.amount() <= 0L) {
                throw new IllegalArgumentException("Invalid exact virtual stack");
            }
            destination.merge(stack.what(), BigInteger.valueOf(stack.amount()).multiply(copies), BigInteger::add);
        }
    }

    private static ListTag writeAmounts(Map<AEKey, BigInteger> amounts) {
        ListTag result = new ListTag();
        amounts.forEach((key, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("key", key.toTagGeneric());
            entry.putString("amount", amount.toString());
            result.add(entry);
        });
        return result;
    }

    private static void readAmounts(ListTag entries, Map<AEKey, BigInteger> destination) {
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            AEKey key = AEKey.fromTagGeneric(entry.getCompound("key"));
            if (key == null) throw new IllegalArgumentException("Unknown exact virtual key");
            BigInteger amount = positive(new BigInteger(entry.getString("amount")), "amount");
            destination.merge(key, amount, BigInteger::add);
        }
    }

    private static BigInteger positive(BigInteger value, String name) {
        if (Objects.requireNonNull(value, name).signum() <= 0) {
            throw new IllegalArgumentException("Invalid exact virtual " + name);
        }
        return value;
    }
}
