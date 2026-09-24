package cn.dancingsnow.neoecoae.crafting.execution.worker;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongBiFunction;
import java.util.function.BiFunction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Durable custody for one exact virtual lane. External long APIs only see bounded windows. */
public final class ECOExactVirtualLedger {
    private final BigInteger crafts;
    private final ECOExactInventory inputs = inventory();
    private final ECOExactInventory outputs = inventory();

    private static ECOExactInventory inventory() {
        var inventory = new ECOExactInventory(ignored -> {});
        inventory.setEnabled(true);
        return inventory;
    }

    public ECOExactVirtualLedger(BigInteger crafts, List<GenericStack> inputs,
            List<GenericStack> outputs, List<GenericStack> remaining) {
        if (crafts.signum() <= 0) throw new IllegalArgumentException("Invalid exact virtual count");
        this.crafts = crafts;
        this.inputs.restore(ECOExactInventory.totals(inputs, crafts));
        this.outputs.restore(ECOExactInventory.totals(outputs, crafts));
        this.outputs.restore(ECOExactInventory.totals(remaining, crafts));
    }

    private ECOExactVirtualLedger(BigInteger crafts) {
        if (crafts.signum() <= 0) throw new IllegalArgumentException("Invalid saved exact virtual count");
        this.crafts = crafts;
    }

    public BigInteger crafts() { return crafts; }
    public Map<AEKey, BigInteger> snapshot(boolean output) {
        return (output ? outputs : inputs).snapshot();
    }

    /** Update each accepted key before the next external call; partial failure cannot replay a transfer. */
    public boolean drain(boolean output, ToLongBiFunction<AEKey, Long> insert, Runnable changed) {
        var inventory = output ? outputs : inputs;
        for (var entry : inventory.snapshot().entrySet()) {
            long offered = entry.getValue().min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
            long accepted = insert.applyAsLong(entry.getKey(), offered);
            if (accepted < 0 || accepted > offered) throw new IllegalStateException("Invalid exact delivery");
            if (accepted > 0) {
                inventory.debit(Map.of(entry.getKey(), BigInteger.valueOf(accepted)));
                changed.run();
            }
        }
        return inventory.snapshot().isEmpty();
    }

    /** Exact delivery path used when the destination can accept more than AE2's long-sized windows. */
    public boolean drainExact(boolean output, BiFunction<AEKey, BigInteger, BigInteger> insert, Runnable changed) {
        var inventory = output ? outputs : inputs;
        for (var entry : inventory.snapshot().entrySet()) {
            BigInteger accepted = insert.apply(entry.getKey(), entry.getValue());
            if (accepted == null || accepted.signum() < 0 || accepted.compareTo(entry.getValue()) > 0) {
                throw new IllegalStateException("Invalid exact delivery amount");
            }
            if (accepted.signum() > 0) {
                inventory.debit(Map.of(entry.getKey(), accepted));
                changed.run();
            }
        }
        return inventory.snapshot().isEmpty();
    }

    public CompoundTag write(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putString("crafts", crafts.toString());
        tag.put("inputs", inputs.writeToNBT(registries));
        tag.put("outputs", outputs.writeToNBT(registries));
        return tag;
    }

    public static ECOExactVirtualLedger read(CompoundTag tag, HolderLookup.Provider registries) {
        var ledger = new ECOExactVirtualLedger(new BigInteger(tag.getString("crafts")));
        ledger.inputs.readFromNBT(tag.getList("inputs", 10), registries);
        ledger.outputs.readFromNBT(tag.getList("outputs", 10), registries);
        return ledger;
    }
}
