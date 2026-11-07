package cn.dancingsnow.neoecoae.crafting.execution.worker;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import java.math.BigInteger;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongBiFunction;
import java.util.function.BiFunction;
import net.minecraft.nbt.CompoundTag;

/** Durable custody for one exact virtual lane. External long APIs only see bounded windows. */
public final class ECOExactVirtualLedger {
    private final PlannerAmount crafts;
    private final ECOExactInventory inputs = inventory();
    private final ECOExactInventory outputs = inventory();

    private static ECOExactInventory inventory() {
        var inventory = new ECOExactInventory(ignored -> {});
        inventory.setEnabled(true);
        return inventory;
    }

    public ECOExactVirtualLedger(BigInteger crafts, List<GenericStack> inputs,
            List<GenericStack> outputs, List<GenericStack> remaining) {
        this(PlannerAmount.of(crafts), inputs, outputs, remaining);
    }

    public ECOExactVirtualLedger(PlannerAmount crafts, List<GenericStack> inputs,
            List<GenericStack> outputs, List<GenericStack> remaining) {
        if (crafts.signum() <= 0) throw new IllegalArgumentException("Invalid exact virtual count");
        this.crafts = crafts;
        this.inputs.restoreQuantities(ECOExactInventory.totalsQuantities(inputs, crafts));
        this.outputs.restoreQuantities(ECOExactInventory.totalsQuantities(outputs, crafts));
        this.outputs.restoreQuantities(ECOExactInventory.totalsQuantities(remaining, crafts));
    }

    private ECOExactVirtualLedger(PlannerAmount crafts) {
        if (crafts.signum() <= 0) throw new IllegalArgumentException("Invalid saved exact virtual count");
        this.crafts = crafts;
    }

    public BigInteger crafts() { return crafts.toBigInteger(); }
    public PlannerAmount craftQuantity() { return crafts; }
    public PlannerAmount quantity(boolean output, AEKey key) { return (output ? outputs : inputs).quantity(key); }
    public PlannerAmount totalQuantity(boolean output) { return (output ? outputs : inputs).totalQuantity(); }
    public Map<AEKey, BigInteger> snapshot(boolean output) {
        return (output ? outputs : inputs).snapshot();
    }

    /** Update each accepted key before the next external call; partial failure cannot replay a transfer. */
    public boolean drain(boolean output, ToLongBiFunction<AEKey, Long> insert, Runnable changed) {
        var inventory = output ? outputs : inputs;
        for (var entry : inventory.snapshotQuantities().entrySet()) {
            long offered = entry.getValue().longValueSaturated();
            long accepted = insert.applyAsLong(entry.getKey(), offered);
            if (accepted < 0 || accepted > offered) throw new IllegalStateException("Invalid exact delivery");
            if (accepted > 0) {
                inventory.debitQuantities(Map.of(entry.getKey(), PlannerAmount.of(accepted)));
                changed.run();
            }
        }
        return inventory.list.isEmpty();
    }

    /** Exact delivery path used when the destination can accept more than AE2's long-sized windows. */
    public boolean drainExact(boolean output, BiFunction<AEKey, BigInteger, BigInteger> insert, Runnable changed) {
        var inventory = output ? outputs : inputs;
        for (var entry : inventory.snapshotQuantities().entrySet()) {
            BigInteger receipt = insert.apply(entry.getKey(), entry.getValue().toBigInteger());
            if (receipt == null) throw new IllegalStateException("Invalid exact delivery amount");
            PlannerAmount accepted = PlannerAmount.of(receipt);
            if (accepted.signum() < 0 || accepted.compareTo(entry.getValue()) > 0) {
                throw new IllegalStateException("Invalid exact delivery amount");
            }
            if (accepted.signum() > 0) {
                inventory.debitQuantities(Map.of(entry.getKey(), accepted));
                changed.run();
            }
        }
        return inventory.list.isEmpty();
    }

    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putString("crafts", crafts.toString());
        tag.put("inputs", inputs.writeToNBT());
        tag.put("outputs", outputs.writeToNBT());
        return tag;
    }

    public static ECOExactVirtualLedger read(CompoundTag tag) {
        var ledger = new ECOExactVirtualLedger(PlannerAmount.parse(tag.getString("crafts")));
        ledger.inputs.readFromNBT(tag.getList("inputs", 10));
        ledger.outputs.readFromNBT(tag.getList("outputs", 10));
        return ledger;
    }
}
